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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.drawText

private fun clock(sec: Int) = "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"

/** The run itself: a briefing before the start, then heart rate, the current block, time, distance and pace. */
@Composable
fun LiveScreen(vm: AppVM, e: Engine, session: Session?, close: () -> Unit) {
    val snap = Live.snap
    Text("ZONE 2 RUNNING", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = 1.sp, color = LocalPal.current.ink)
    if (!snap.active && session != null) Briefing(vm, e, session, close)
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

    fun begin() = Live.start(ctx, steps, e.zb, session.type, vm.settings.sensorAddr, vm.settings.voiceOn, session.kind)
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

/** What the screen is telling the runner right now: the colour of the whole screen, a headline and a line under it. */
private data class LiveState(val over: Boolean, val head: String, val sub: String)

private fun liveState(snap: LiveSnap, e: Engine): LiveState {
    val st = Live.steps.getOrNull(snap.stepIndex)
    val easyBlock = snap.stepKind in setOf("warm", "easy", "walk", "cool", "hold", "")
    val lo = st?.zLo ?: 1; val hi = st?.zHi ?: 2
    val over = easyBlock && snap.hr >= e.zb[2]
    return when {
        snap.paused -> LiveState(false, "Paused", "The clock and distance are stopped.")
        snap.hr == 0 -> LiveState(false, if (easyBlock) "Keep it easy" else "Go hard", if (easyBlock) "No heart-rate sensor. Full sentences mean Zone 2." else "No heart-rate sensor. Hard but repeatable: a few words at a time.")
        over -> LiveState(true, "Ease off", "Above Zone 2. Ease off or walk until you're under ${e.zb[2]}.")
        easyBlock -> LiveState(false, if (snap.zone == 2) "In Zone 2" else "Nice and easy", "In the easy range. Cap is ${e.zb[2] - 1} bpm.")
        snap.zone < lo -> LiveState(false, "Build up", "Hard block. Aim for ${e.zb[lo - 1]}–${e.zb[hi.coerceAtMost(4)] - 1} bpm. It takes a minute to get there.")
        snap.zone > hi -> LiveState(false, "Back off a touch", "Above the target for this block. Hard but repeatable.")
        else -> LiveState(false, "On target", "Hard block. Aim for ${e.zb[lo - 1]}–${e.zb[hi.coerceAtMost(4)] - 1} bpm.")
    }
}

/** The colour behind the whole run screen: calm green in range, burnt orange above the cap, grey when paused. */
@Composable
fun liveGround(e: Engine): Color {
    val snap = Live.snap
    val to = when { snap.paused -> Color(0xFF1C2523); liveState(snap, e).over -> Color(0xFF6B300A); else -> Color(0xFF0B3A34) }
    return androidx.compose.animation.animateColorAsState(to, androidx.compose.animation.core.tween(if (Motion.on) 600 else 0), label = "ground").value
}

private val Ink = Color(0xFFEAF6F3)
private val Dim = Color(0xB3EAF6F3)
private val Track = Color(0x26FFFFFF)

/**
 * The run in progress, filling the screen. One dial says where the heart rate sits against the zones,
 * the screen's colour says whether that is fine, and everything under it is glanceable at arm's length.
 */
@Composable
fun LiveRunning(e: Engine) {
    val snap = Live.snap
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    val state = liveState(snap, e)
    val st = Live.steps.getOrNull(snap.stepIndex)
    val zones = DarkPal.zones
    val mark = if (state.over) Color(0xFFFFD166) else Ink

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Who is listening: GPS and the sensor, as two quiet lights.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text((TYPE_NAMES[Live.type] ?: "Run").uppercase(), Modifier.weight(1f), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 1.sp, color = Ink)
            Light("GPS", snap.gps == "GPS locked"); Spacer(Modifier.width(12.dp)); Light("HR", snap.hr > 0)
        }

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val r = minOf(maxWidth / 2, maxHeight / 1.5f)
            ZoneDial(snap, e, st, zones, mark, Modifier.fillMaxSize())
            Column(Modifier.offset(y = r * .2f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (snap.hr > 0) {
                    Text("${snap.hr}", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = (r.value * .62f).sp, lineHeight = (r.value * .58f).sp, color = mark)
                    Text("BPM · ZONE ${snap.zone}", fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 1.2.sp, color = Dim)
                } else {
                    Text(if (snap.stepLeft > 0) clock(snap.stepLeft) else fmtDur(snap.elapsed), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = (r.value * .5f).sp, lineHeight = (r.value * .5f).sp, color = Ink)
                    Text(if (snap.stepLeft > 0) "LEFT IN THIS BLOCK" else "RUNNING", fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 1.2.sp, color = Dim)
                }
                Spacer(Modifier.height(8.dp))
                Text(state.head, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 36.sp, color = mark)
            }
        }
        Text(state.sub, Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontFamily = BodyFont, fontSize = 15.sp, lineHeight = 20.sp, color = Dim)

        Row {
            BigStat(fmtDur(snap.elapsed), "TIME", Modifier.weight(1f))
            BigStat(String.format(java.util.Locale.US, "%.2f", U.k(snap.meters / 1000)), U.unit.uppercase(), Modifier.weight(1f))
            BigStat(snap.pace?.let { fmtPace(it) } ?: "–", "PACE ${U.per.uppercase()}", Modifier.weight(1f))
        }

        // The session as a picture: every block, how hard it is, and where you are in it.
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x1FFFFFFF)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (snap.paused) "PAUSED" else "NOW", fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.2.sp, color = Dim)
                    Text(snap.stepLabel, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 24.sp, color = Ink, maxLines = 2)
                }
                if (snap.stepLeft > 0 && snap.hr > 0) Text(clock(snap.stepLeft), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 34.sp, color = Ink)
            }
            if (Live.steps.size > 1) SessionProfile(Live.steps, snap.stepIndex, st?.takeIf { it.sec > 0 }?.let { 1f - snap.stepLeft / it.sec.toFloat() } ?: 0f, zones, Modifier.fillMaxWidth().height(46.dp))
            if (snap.trace.size >= 6) HeartTrace(snap.trace, e, st, Modifier.fillMaxWidth().height(54.dp))
            val last = snap.splits.lastOrNull()
            Text(listOfNotNull(if (snap.nextLabel.isNotEmpty()) "Next: ${snap.nextLabel}" else null,
                last?.let { "${U.word.replaceFirstChar { c -> c.uppercase() }} ${snap.splits.size} in ${clock(it.sec)}" }, if (snap.avgHr > 0) "Avg ${snap.avgHr} bpm" else null).joinToString("  ·  ").ifEmpty { snap.gps },
                fontFamily = BodyFont, fontSize = 13.5.sp, color = Dim, maxLines = 1)
        }

        // Finish only shows once paused, so a stray thumb can't end the run.
        if (!snap.paused) LiveBtn("Pause", Ink, Color(0xFF0B3A34), Modifier.fillMaxWidth()) { Live.pause() }
        else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiveBtn("Resume", Ink, Color(0xFF1C2523), Modifier.weight(1f)) { Live.resume() }
                LiveBtn("Finish", Color(0xFFFFD166), Color(0xFF1C2523), Modifier.weight(1f)) { Live.finish() }
            }
            Text("Discard this run", Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { Live.discard() }.padding(6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFFEF9A9D))
        }
    }
}

@Composable
private fun Light(name: String, on: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (on) Color(0xFF5BE0C6) else Color(0x55FFFFFF)))
        Text(name, fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (on) Ink else Dim)
    }
}

@Composable
private fun BigStat(v: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(v, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 42.sp, color = Ink)
        Text(label, fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.2.sp, color = Dim)
    }
}

@Composable
private fun LiveBtn(text: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    val tick = haptic()
    Box(modifier.height(62.dp).clip(RoundedCornerShape(31.dp)).background(bg).clickable { tick(true); onClick() }, contentAlignment = Alignment.Center) {
        Text(text, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = .5.sp, color = fg)
    }
}

/**
 * The dial: five zones round an arc, the block's target zones drawn thick and bright, a marker where the
 * heart rate is now. Without a sensor it becomes a plain ring that fills as the block runs down.
 */
@Composable
private fun ZoneDial(snap: LiveSnap, e: Engine, st: Step?, zones: List<Color>, mark: Color, modifier: Modifier) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val lo = e.zb[0] - 12f; val hi = e.zb[4] + 14f
    val at = animFloat(((snap.hr - lo) / (hi - lo)).coerceIn(0f, 1f), 700)
    val prog = st?.takeIf { it.sec > 0 }?.let { 1f - snap.stepLeft / it.sec.toFloat() } ?: 0f
    val tLo = st?.zLo ?: 1; val tHi = st?.zHi ?: 2
    androidx.compose.foundation.Canvas(modifier) {
        val thick = 22.dp.toPx(); val thin = 10.dp.toPx()
        val r = minOf(size.width / 2, size.height / 1.5f) - thick
        val cx = size.width / 2; val cy = (size.height - 1.5f * r) / 2 + r
        val box = androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r)
        fun arc(c: Color, from: Float, sweep: Float, w: Float) = drawArc(c, 150f + from * 240f, sweep * 240f, false, box.topLeft, box.size,
            style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Butt))
        fun point(f: Float, rad: Float): androidx.compose.ui.geometry.Offset { val a = Math.toRadians(150.0 + f * 240.0); return androidx.compose.ui.geometry.Offset(cx + rad * kotlin.math.cos(a).toFloat(), cy + rad * kotlin.math.sin(a).toFloat()) }
        if (snap.hr <= 0) {
            arc(Track, 0f, 1f, thin); if (prog > 0) arc(Ink, 0f, prog, thin)
            return@Canvas
        }
        val gap = .006f
        for (i in 0..4) {
            val a = if (i == 0) 0f else (e.zb[i] - lo) / (hi - lo); val b = if (i == 4) 1f else (e.zb[i + 1] - lo) / (hi - lo)
            val target = (i + 1) in tLo..tHi
            arc(zones[i].copy(alpha = if (target) 1f else .38f), a + gap, b - a - 2 * gap, if (target) thick else thin)
        }
        // The two numbers that bound the target, written just outside the arc.
        listOf(e.zb[tLo - 1], e.zb[tHi.coerceAtMost(4)] - if (tHi <= 4) 0 else 0).distinct().forEach { bpm ->
            val f = (bpm - lo) / (hi - lo); val txt = measurer.measure("$bpm", androidx.compose.ui.text.TextStyle(color = Dim, fontSize = 13.sp, fontFamily = BodyFont, fontWeight = FontWeight.SemiBold))
            val o = point(f, r + thick * .5f + 14.dp.toPx())
            drawText(txt, topLeft = androidx.compose.ui.geometry.Offset(o.x - txt.size.width / 2f, o.y - txt.size.height / 2f))
        }
        val p = point(at, r)
        drawCircle(Color(0x33000000), 19.dp.toPx(), p)
        drawCircle(mark, 15.dp.toPx(), p)
        drawCircle(zones[(snap.zone - 1).coerceIn(0, 4)], 8.dp.toPx(), p)
    }
}

/** The whole session as a row of blocks: wider is longer, taller is harder, filled is done. */
@Composable
private fun SessionProfile(steps: List<Step>, index: Int, progress: Float, zones: List<Color>, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val gap = 3.dp.toPx(); val total = steps.sumOf { if (it.sec > 0) it.sec else 600 }.toFloat()
        val usable = size.width - gap * (steps.size - 1); var x = 0f
        steps.forEachIndexed { i, s ->
            val w = usable * (if (s.sec > 0) s.sec else 600) / total
            val h = size.height * listOf(.3f, .45f, .65f, .85f, 1f)[(s.zHi - 1).coerceIn(0, 4)]
            val c = zones[(s.zHi - 1).coerceIn(0, 4)]; val rad = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
            drawRoundRect(c.copy(alpha = if (i < index) 1f else .3f), androidx.compose.ui.geometry.Offset(x, size.height - h), androidx.compose.ui.geometry.Size(w, h), rad)
            if (i == index) {
                drawRoundRect(c, androidx.compose.ui.geometry.Offset(x, size.height - h), androidx.compose.ui.geometry.Size(w * progress.coerceIn(0f, 1f), h), rad)
                val px = x + w * progress.coerceIn(0f, 1f)
                drawLine(Ink, androidx.compose.ui.geometry.Offset(px, 0f), androidx.compose.ui.geometry.Offset(px, size.height), 2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
            x += w + gap
        }
    }
}

/** Heart rate over the last few minutes, with the block's target range as a band behind it. */
@Composable
private fun HeartTrace(trace: List<Int>, e: Engine, st: Step?, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val tLo = e.zb[(st?.zLo ?: 1) - 1].toFloat(); val tHi = e.zb[(st?.zHi ?: 2).coerceAtMost(4)].toFloat()
        val lo = minOf(trace.min().toFloat(), tLo) - 4; val hi = maxOf(trace.max().toFloat(), tHi) + 4
        fun y(v: Float) = size.height * (1 - (v - lo) / (hi - lo))
        drawRect(Color(0x2E5BE0C6), androidx.compose.ui.geometry.Offset(0f, y(tHi)), androidx.compose.ui.geometry.Size(size.width, y(tLo) - y(tHi)))
        val path = androidx.compose.ui.graphics.Path()
        trace.forEachIndexed { i, v -> val x = size.width * i / (trace.size - 1f); if (i == 0) path.moveTo(x, y(v.toFloat())) else path.lineTo(x, y(v.toFloat())) }
        drawPath(path, Ink, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawCircle(Ink, 4.dp.toPx(), androidx.compose.ui.geometry.Offset(size.width, y(trace.last().toFloat())))
    }
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
