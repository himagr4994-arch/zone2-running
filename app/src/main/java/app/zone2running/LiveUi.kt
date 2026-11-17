package app.zone2running

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun clock(sec: Int) = "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"

/** The run itself: a briefing before the start, then heart rate, the current block, time, distance and pace. */
@Composable
fun LiveScreen(vm: AppVM, e: Engine, session: Session?, close: () -> Unit) {
    val snap = Live.snap
    Text("ZONE 2 RUNNING", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = 1.sp, color = LocalPal.current.ink)
    if (snap.active) Running(snap, e) else if (session != null) Briefing(vm, e, session, close)
}

@Composable
private fun Briefing(vm: AppVM, e: Engine, session: Session, close: () -> Unit) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    val steps = remember(session) { e.steps(session) }
    var note by remember { mutableStateOf("") }
    var finding by remember { mutableStateOf(false) }
    val found = remember { mutableStateListOf<Pair<String, String>>() }
    val scanner = remember { HeartSensor(ctx) }
    DisposableEffect(Unit) { onDispose { scanner.stopScan() } }

    fun begin() = Live.start(ctx, steps, e.zb, session.type, vm.settings.sensorAddr, vm.settings.voiceOn)
    val askStart = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { got ->
        if (got[Manifest.permission.ACCESS_FINE_LOCATION] == true) begin()
        else note = "Location is needed to measure distance during the run. Without it you can still log the run by hand afterwards."
    }
    val askScan = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { got ->
        if (got.values.all { it }) { finding = true; found.clear(); if (!scanner.scan { n, a -> if (found.none { it.second == a }) found.add(n to a) }) { finding = false; note = "Turn Bluetooth on to look for a sensor." } }
        else note = "Bluetooth permission is needed to find a heart-rate sensor."
    }

    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Dil("hello", Modifier.size(64.dp))
            Column { Label("Guided run"); Head("${session.title} · ${session.min} min") }
        }
        Body(session.detail)
        steps.take(8).forEachIndexed { i, st ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Dot(p.zones[(st.zHi - 1).coerceIn(0, 4)])
                Body(st.label, modifier = Modifier.weight(1f))
                Small(if (st.sec == 0) "open" else if (st.sec >= 60) "${st.sec / 60} min" else "${st.sec} s")
            }
        }
        if (steps.size > 8) Small("…and ${steps.size - 8} more blocks.")
        if (steps.any { it.kind == "hard" || it.kind == "race" }) Small(STOP_SIGNS)
    }
    Panel {
        Label("Heart rate sensor")
        if (vm.settings.sensorAddr.isNotEmpty()) {
            Body("${vm.settings.sensorName.ifBlank { "Saved sensor" }} will connect when you start.")
            Btn("Use a different sensor", ghost = true, small = true) { vm.updateSettings { it.copy(sensorAddr = "", sensorName = "") } }
        } else {
            Body("Optional. With a chest strap, arm band or a watch that broadcasts heart rate over Bluetooth, I can tell you the moment you leave Zone 2. Without one you still get time, distance, pace and the guided blocks.")
            if (!finding) Btn("Find a sensor", ghost = true, small = true) {
                askScan.launch(if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
            } else {
                Small(if (found.isEmpty()) "Looking… wear the sensor so it switches on." else "Tap yours:")
                found.forEach { (n, a) ->
                    Text(n, Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, p.line, RoundedCornerShape(10.dp)).clickable {
                        scanner.stopScan(); finding = false; vm.updateSettings { it.copy(sensorAddr = a, sensorName = n) }
                    }.padding(12.dp), fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = p.ink)
                }
            }
        }
    }
    if (note.isNotEmpty()) NoteBox(Note("warn", note))
    Small("Your phone measures distance with GPS while the run is on. The route itself is never stored, only distance and per-kilometre times.")
    Row {
        Btn("Back", ghost = true, onClick = close)
        Spacer(Modifier.weight(1f))
        Btn("Start run") {
            val want = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) want += Manifest.permission.POST_NOTIFICATIONS
            if (Build.VERSION.SDK_INT >= 31 && vm.settings.sensorAddr.isNotEmpty()) want += Manifest.permission.BLUETOOTH_CONNECT
            askStart.launch(want.toTypedArray())
        }
    }
}

@Composable
private fun Running(snap: LiveSnap, e: Engine) {
    val p = LocalPal.current
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    var confirm by remember { mutableStateOf(false) }
    val easyBlock = snap.stepKind in setOf("warm", "easy", "walk", "cool", "hold", "")
    val over = easyBlock && snap.hr >= e.zb[2]

    // Heart rate is the one number that matters mid-run, so it gets the loud panel.
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.hero).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Label(if (snap.hr > 0) "Heart rate · Zone ${snap.zone}" else "Heart rate", p.heroMuted)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if (snap.hr > 0) "${snap.hr}" else "–", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 96.sp, lineHeight = 88.sp, color = if (over) p.heroMark else p.heroInk)
                    Text(" bpm", Modifier.padding(bottom = 8.dp), fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = p.heroMuted)
                }
            }
            Dil(if (over || snap.zone >= 4) "puffed" else if (snap.paused) "sleepy" else "calm", Modifier.size(72.dp), color = p.easy.lighten(), bpm = if (snap.hr > 0) snap.hr else 0, running = !snap.paused)
        }
        Row(Modifier.clip(RoundedCornerShape(5.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (i in 0..4) Box(Modifier.weight(1f).height(if (snap.zone == i + 1) 14.dp else 8.dp).background(if (snap.zone == i + 1) p.zones[i].lighten() else p.heroTrack))
        }
        Text(when {
            snap.hr == 0 -> if (snap.sensor.isEmpty()) "No sensor connected. Use the talk test: full sentences mean Zone 2." else "Sensor: ${snap.sensor}"
            over -> "Above Zone 2. Ease off or walk until you're under ${e.zb[2]}."
            easyBlock -> "In the easy range. Cap is ${e.zb[2] - 1} bpm."
            else -> "Hard block. Aim for ${e.zb[3]}–${e.zb[4] - 1} bpm."
        }, color = if (over) p.heroMark else p.heroMuted, fontFamily = BodyFont, fontSize = 15.sp, lineHeight = 20.sp)
    }

    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Label(if (snap.paused) "Paused" else "Now"); Head(snap.stepLabel, 24) }
            if (snap.stepLeft > 0) Text(clock(snap.stepLeft), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 44.sp, color = p.ink)
        }
        if (snap.nextLabel.isNotEmpty()) Small("Next: ${snap.nextLabel}")
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        Row {
            LiveStat(fmtDur(snap.elapsed), "time", Modifier.weight(1f))
            LiveStat(String.format(java.util.Locale.US, "%.2f", U.k(snap.meters / 1000)), U.unit, Modifier.weight(1f))
            LiveStat(snap.pace?.let { fmtPace(it) } ?: "–", "pace ${U.per}", Modifier.weight(1f))
        }
        if (snap.lastCue.isNotEmpty()) NoteBox(Note("plain", snap.lastCue))
        Small("${snap.gps}${if (snap.sensor.isNotEmpty()) " · Sensor: ${snap.sensor}" else ""}${if (snap.avgHr > 0) " · Average ${snap.avgHr} bpm" else ""}")
    }

    if (snap.splits.isNotEmpty()) Panel { Label("Splits"); SplitBars(snap.splits.takeLast(5), snap.splits.size - snap.splits.takeLast(5).size) }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Btn(if (snap.paused) "Resume" else "Pause", Modifier.weight(1f), ghost = !snap.paused) { if (snap.paused) Live.resume() else Live.pause() }
        Btn(if (confirm) "Tap again to finish" else "Finish", Modifier.weight(1f), ghost = !confirm) { if (confirm) Live.finish() else confirm = true }
    }
    if (snap.paused) Btn("Discard this run", danger = true) { Live.discard() }
}

@Composable
private fun LiveStat(v: String, label: String, modifier: Modifier) {
    Column(modifier) { Text(v, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, color = LocalPal.current.ink); Small(label) }
}

/** One bar per kilometre: longer is faster, and the fastest is picked out. */
@Composable
fun SplitBars(splits: List<Split>, offset: Int = 0) {
    val p = LocalPal.current
    val fastest = splits.minOf { it.sec }; val slowest = splits.maxOf { it.sec }
    splits.forEachIndexed { i, sp ->
        val best = sp.sec == fastest && splits.size > 1
        val frac = if (slowest == fastest) .8f else .45f + .55f * (slowest - sp.sec) / (slowest - fastest).toFloat()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Km ${offset + i + 1}", Modifier.width(48.dp), fontSize = 13.5.sp, color = if (best) p.ink else p.muted, fontFamily = BodyFont, fontWeight = if (best) FontWeight.SemiBold else FontWeight.Normal)
            Box(Modifier.weight(1f)) { Box(Modifier.fillMaxWidth(frac).height(10.dp).clip(RoundedCornerShape(5.dp)).background(if (best) p.accent else p.easy.copy(alpha = .45f))) }
            Text(clock(sp.sec), Modifier.width(44.dp), fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = p.ink)
            Text(if (sp.hr > 0) "${sp.hr}" else "", Modifier.width(30.dp), fontSize = 12.5.sp, color = p.muted, fontFamily = BodyFont)
        }
    }
}
