package app.zone2running

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
private fun Toggle(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) { Body(title); if (sub.isNotEmpty()) Small(sub) }
        Switch(on, onChange)
    }
}

@Composable
fun SetupScreen(vm: AppVM, e: Engine, rerunGuide: () -> Unit) {
    val p = LocalPal.current
    val s = vm.settings

    // Number fields are edited as text and applied together with Save.
    var target by remember(s) { mutableStateOf(fmtPace(s.targetPace.toDouble())) }
    var ref by remember(s) { mutableStateOf(if (s.refHr > 0) "${s.refHr}" else "") }
    var maxHr by remember(s) { mutableStateOf("${s.maxHr}") }
    var rest by remember(s) { mutableStateOf("${s.restHr}") }
    var cz by remember(s) { mutableStateOf(s.customZ.map { "$it" }) }
    var easy by remember(s) { mutableStateOf("${s.easyMin}") }
    var long by remember(s) { mutableStateOf("${s.longMin}") }
    var error by remember { mutableStateOf("") }

    Panel {
        Head("Target")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Zone 2 pace (min/km)", target, Modifier.weight(1f), "7:00", KeyboardType.Ascii) { target = it }
            Field("Reference HR", ref, Modifier.weight(1f), "mid Zone 2") { ref = it }
        }
        Small("Every steady run is converted to the pace you would hold at the reference HR. That number is what the app tracks toward your target.")
        Head("Heart rate")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Max HR (bpm)", maxHr, Modifier.weight(1f)) { maxHr = it }
            Field("Resting HR (bpm)", rest, Modifier.weight(1f)) { rest = it }
        }
        Small("Zone method")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("hrr" to "HR reserve", "max" to "% of max", "custom" to "From my watch").forEach { (k, v) ->
                Pick(v, s.zoneMethod == k, Modifier.weight(1f)) { vm.updateSettings { it.copy(zoneMethod = k, customZ = if (k == "custom" && s.zoneMethod != "custom") e.zb.drop(1) else it.customZ) } }
            }
        }
        if (s.zoneMethod == "custom") Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0..3) Field("Z${i + 2} from", cz[i], Modifier.weight(1f)) { v -> cz = cz.toMutableList().also { it[i] = v } }
        }
        ZoneStrip(e)
        Head("Session length")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Easy run, minutes", easy, Modifier.weight(1f)) { easy = it }
            Field("Long run, minutes", long, Modifier.weight(1f)) { long = it }
        }
        if (error.isNotEmpty()) NoteBox(Note("warn", error))
        Btn("Save changes") {
            val tp = parseDur(target); val mh = maxHr.toIntOrNull() ?: 0; val rh = rest.toIntOrNull() ?: 0
            val rf = ref.toIntOrNull() ?: 0; val z = cz.map { it.toIntOrNull() ?: 0 }
            val em = easy.toIntOrNull() ?: 0; val lm = long.toIntOrNull() ?: 0
            error = when {
                tp !in 180..900 -> "Enter the target pace like 7:00."
                mh !in 120..230 -> "Max HR should be between 120 and 230."
                rh !in 30..110 || mh - rh < 50 -> "Check resting HR. It should be 30–110 and well below max."
                ref.isNotBlank() && rf !in 80..200 -> "Reference HR should be 80–200, or leave it blank."
                s.zoneMethod == "custom" && !(z[0] > 60 && z.zipWithNext().all { (a, b) -> b > a } && z[3] < 230) -> "Custom zones must rise from Zone 2 to Zone 5."
                em !in 15..90 -> "Easy run should be 15–90 minutes."
                lm !in 30..180 -> "Long run should be 30–180 minutes."
                else -> ""
            }
            if (error.isEmpty()) {
                vm.updateSettings { it.copy(targetPace = tp, maxHr = mh, restHr = rh, refHr = rf, customZ = if (s.zoneMethod == "custom") z else it.customZ, easyMin = em, longMin = lm) }
                vm.toast = "Saved"
            }
        }
    }

    Panel {
        Head("Training week")
        Small("Running days")
        DayPicker(s.days) { d -> vm.updateSettings { it.copy(days = if (d in it.days) it.days - d else (it.days + d).sorted()) } }
        Small("Long run day")
        DayPicker(listOf(s.longDay)) { d -> vm.updateSettings { it.copy(longDay = d) } }
        Toggle("One harder session a week", "Needs 4 or more running days.", s.quality) { v -> vm.updateSettings { it.copy(quality = v) } }
        Small("Week 1 of your plan started ${fmtDay(e.planStart)} ${e.planStart.year}.")
        Btn("Restart the plan from this week", ghost = true, small = true) { vm.updateSettings { it.copy(planStart = mondayOf(LocalDate.now())) }; vm.toast = "Plan restarted" }
    }

    Panel {
        Head("Motivation")
        Toggle("Levels, streaks and badges", "Turn off for a calmer app: progress, plan and coaching only.", s.gameOn) { v -> vm.updateSettings { it.copy(gameOn = v) } }
    }

    Panel {
        Head("Health Connect")
        val ask = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { vm.refreshHealth() }
        when {
            !vm.health.available() -> Body("Health Connect isn't available on this phone, so runs are added by hand.", p.muted)
            !vm.healthGranted -> {
                Body("Import runs recorded by your watch app, Strava or any app that writes to Health Connect. Zone 2 Running only reads runs, their distance and heart rate. Nothing is written back and nothing leaves your phone.")
                Btn("Connect Health Connect") { ask.launch(vm.health.permissions) }
            }
            else -> {
                NoteBox(Note("good", "Connected. Zone 2 Running reads runs, distance and heart rate."))
                Toggle("Import new runs automatically", "Checks each time you open the app.", s.autoImport) { v -> vm.updateSettings { it.copy(autoImport = v) } }
                Btn(if (vm.importing) "Importing…" else "Import now", ghost = true) { vm.importHealth(manual = true) }
            }
        }
    }

    Panel {
        Head("Your data")
        Body("Everything is stored on this phone. There is no account and no server.")
        var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }
        val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::export) }
        val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { confirmRestore = it }
        val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(vm::setBackupFolder) }
        if (s.backupTree.isEmpty()) {
            Small("Pick a folder and Zone 2 Running writes a backup there once a day, keeping the last seven. Choose one that syncs or survives a reset, like Documents.")
            Btn("Choose a backup folder") { folder.launch(null) }
        } else {
            NoteBox(Note("good", "Daily backup is on." + if (s.lastBackup.isNotEmpty()) " Last written ${s.lastBackup}." else ""))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Change folder", ghost = true, small = true) { folder.launch(null) }
                Btn("Stop backups", ghost = true, small = true) { vm.stopBackups() }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Export a backup", ghost = true) { save.launch("zone2-running-${LocalDate.now()}.json") }
            Btn("Restore", ghost = true) { open.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
        }
        confirmRestore?.let { uri ->
            AlertDialog(onDismissRequest = { confirmRestore = null }, title = { Text("Restore this backup?") },
                text = { Text("It replaces every run and setting currently in the app.") },
                confirmButton = { TextButton({ vm.import(uri); confirmRestore = null }) { Text("Restore") } },
                dismissButton = { TextButton({ confirmRestore = null }) { Text("Cancel") } })
        }
    }

    Panel {
        Btn("Run the setup guide again", ghost = true, onClick = rerunGuide)
        Small("Zone 2 Running 0.1.0 · open source under the MIT licence · not medical advice. Check with a doctor before starting a new training plan if you have a health condition.")
    }
}

private val LEVELS = listOf(Triple("Just starting", "Under 10 km a week", Triple(25, 40, false)), Triple("Running regularly", "10–25 km a week", Triple(35, 60, true)), Triple("Experienced", "More than 25 km a week", Triple(45, 80, true)))

@Composable
fun Onboarding(vm: AppVM, canClose: Boolean, close: () -> Unit) {
    val p = LocalPal.current
    val s0 = vm.settings
    val today = LocalDate.now()
    val logged = remember { vm.engine().eligible.lastOrNull { it.first.id != "_base" }?.first }
    var step by remember { mutableIntStateOf(0) }
    var target by remember { mutableStateOf(fmtPace(s0.targetPace.toDouble())) }
    var cur by remember { mutableStateOf(if (s0.basePace > 0) fmtPace(s0.basePace.toDouble()) else "") }
    var hr by remember { mutableStateOf(if (s0.baseHr > 0) "${s0.baseHr}" else "") }
    var age by remember { mutableStateOf("") }
    var maxHr by remember { mutableStateOf(if (s0.onboarded) "${s0.maxHr}" else "") }
    var rest by remember { mutableStateOf("${s0.restHr}") }
    var level by remember { mutableIntStateOf(-1) }
    var days by remember { mutableStateOf(s0.days) }
    var longDay by remember { mutableIntStateOf(s0.longDay) }
    var easy by remember { mutableStateOf("${s0.easyMin}") }
    var long by remember { mutableStateOf("${s0.longMin}") }
    var quality by remember { mutableStateOf(s0.quality) }
    var error by remember { mutableStateOf("") }

    fun built(): Settings {
        val cp = parseDur(cur); val h = hr.toIntOrNull() ?: 0
        val base = logged == null && cp in 180..900 && h in 80..220
        return s0.copy(
            targetPace = parseDur(target).takeIf { it in 180..900 } ?: s0.targetPace,
            maxHr = maxHr.toIntOrNull()?.takeIf { it in 120..230 } ?: s0.maxHr, restHr = rest.toIntOrNull()?.takeIf { it in 30..110 } ?: s0.restHr,
            zoneMethod = if (s0.zoneMethod == "custom") "custom" else "hrr",
            basePace = if (base) cp else s0.basePace, baseHr = if (base) h else s0.baseHr, baseDate = if (base) (s0.baseDate ?: today) else s0.baseDate,
            days = days.sorted(), longDay = longDay, easyMin = easy.toIntOrNull()?.takeIf { it in 15..90 } ?: s0.easyMin,
            longMin = long.toIntOrNull()?.takeIf { it in 30..180 } ?: s0.longMin, quality = quality,
            planStart = s0.planStart ?: mondayOf(today), onboarded = true,
        )
    }
    fun check(): String = when (step) {
        0 -> if (parseDur(target) !in 180..900) "Enter a target pace like 7:00." else ""
        1 -> if (logged != null) "" else if (parseDur(cur) !in 180..900) "Enter your pace like 8:30." else if ((hr.toIntOrNull() ?: 0) !in 80..220) "Enter the heart rate you see at that pace." else ""
        2 -> { val m = maxHr.toIntOrNull() ?: 0; val r = rest.toIntOrNull() ?: 0
            if (m !in 120..230) "Enter your max heart rate, or your age to estimate it." else if (r !in 30..110) "Enter your resting heart rate. 60 is a fair guess." else if (m - r < 50) "Max and resting heart rate are too close. Check them." else "" }
        3 -> if (days.size < 2) "Pick at least 2 running days." else ""
        else -> ""
    }

    Text("ZONE 2 RUNNING", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = 1.sp, color = p.ink)
    Panel {
        Label("Step ${step + 1} of 5")
        when (step) {
            0 -> {
                Head("What easy pace are you chasing?")
                Body("Zone 2 is the effort where you can still talk in full sentences. The faster you run at that heart rate, the bigger your aerobic engine. Pick the pace you want to hold there.", p.muted)
                Field("Target Zone 2 pace (min/km)", target, placeholder = "7:00", keyboard = KeyboardType.Ascii) { target = it }
                Small("Not sure? A solid first target is 60–90 seconds per km faster than your easy pace today.")
            }
            1 -> {
                Head("Where are you now?")
                if (logged != null) {
                    Body("Your starting point comes from the run you already logged.", p.muted)
                    NoteBox(Note("plain", "${fmtDay(logged.date)}: ${fmtPace(paceOf(logged))} /km at ${logged.avgHr} bpm"))
                } else {
                    Body("Take any recent steady run, or your usual easy pace, and the heart rate you see at that pace. It does not need to be a Zone 2 run. The app converts it.", p.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Field("Pace (min/km)", cur, Modifier.weight(1f), "8:30", KeyboardType.Ascii) { cur = it }
                        Field("Heart rate", hr, Modifier.weight(1f), "150") { hr = it }
                    }
                }
            }
            2 -> {
                Head("Your heart-rate zones")
                Body("Enter your age to estimate max heart rate, or type the number if you know it. Resting heart rate is what your watch shows when you wake up.", p.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Age", age, Modifier.weight(1f), "32") { v -> age = v; v.toIntOrNull()?.takeIf { it in 10..90 }?.let { maxHr = "${(208 - .7 * it).roundToInt()}" } }
                    Field("Max HR", maxHr, Modifier.weight(1f), "188") { maxHr = it }
                    Field("Resting HR", rest, Modifier.weight(1f), "60") { rest = it }
                }
                if ((maxHr.toIntOrNull() ?: 0) in 120..230) ZoneStrip(Engine(built(), emptyList(), today))
                Small("You can copy exact zones from your watch later in Setup.")
            }
            3 -> {
                Head("Your training week")
                Small("How much do you run now?")
                LEVELS.forEachIndexed { i, (name, sub, v) ->
                    val shape = RoundedCornerShape(10.dp)
                    Column(Modifier.fillMaxWidth().clip(shape).background(p.surface2).border(if (level == i) 2.dp else 1.dp, if (level == i) p.accent else p.line, shape)
                        .clickable { level = i; easy = "${v.first}"; long = "${v.second}"; quality = v.third }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = p.ink, fontFamily = BodyFont); Small(sub)
                    }
                }
                Small("Days you can run")
                DayPicker(days) { d -> days = if (d in days) days - d else days + d }
                Small("Long run day")
                DayPicker(listOf(longDay)) { longDay = it }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("Easy run, minutes", easy, Modifier.weight(1f)) { easy = it }
                    Field("Long run, minutes", long, Modifier.weight(1f)) { long = it }
                }
                Toggle("One harder session a week", "Needs 4 or more running days.", quality) { quality = it }
            }
            else -> {
                val e = Engine(built(), vm.data.runs, today)
                val c = e.current; val tg = e.s.targetPace.toDouble(); val gap = (c ?: tg) - tg
                val plan = e.weekPlan(mondayOf(today))
                Head("Your plan is ready")
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column { Label("Zone 2 now"); Text(c?.let { fmtPace(it) } ?: "–", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 58.sp, lineHeight = 58.sp, color = p.ink) }
                    Text("to", Modifier.padding(bottom = 10.dp), fontFamily = Display, fontSize = 30.sp, color = p.muted)
                    Column { Label("Target"); Text(fmtPace(tg), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 58.sp, lineHeight = 58.sp, color = p.ink) }
                }
                Body(if (gap > 0) "That is ${fmtPace(gap)} per km to gain at ${e.refHr} bpm. Runners typically gain 10–20 s/km a month with consistent easy running, so plan for about ${max(1, (gap / 20).roundToInt())}–${max(2, (gap / 10).roundToInt())} months."
                else "You already hold this pace in Zone 2. Go back and pick a faster target, or keep it and use the plan to stay there.")
                Label("Week 1 · ${plan.total} minutes")
                plan.ses.entries.forEachIndexed { i, (d, ses) ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(DOW[d - 1], Modifier.width(44.dp), fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = p.ink)
                        Column { Text("${ses.title} · ${ses.min} min", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = p.ink, fontFamily = BodyFont); Small(ses.detail) }
                    }
                }
                Small("Your Zone 2 is ${e.zb[1]}–${e.zb[2] - 1} bpm. Each week builds by about 8%, every fourth week is lighter and ends with a re-test.")
            }
        }
        if (error.isNotEmpty()) NoteBox(Note("warn", error))
        Row {
            if (step > 0 || canClose) Btn(if (step == 0) "Close" else "Back", ghost = true) { error = ""; if (step == 0) close() else step-- }
            Spacer(Modifier.weight(1f))
            Btn(if (step == 4) "Start training" else "Next") {
                error = check()
                if (error.isEmpty()) { if (step < 4) step++ else { val b = built(); vm.updateSettings { b }; vm.refreshHealth(); close() } }
            }
        }
    }
}

@Composable
fun RewardScreen(c: Celebration, gameOn: Boolean, done: () -> Unit) {
    val p = LocalPal.current
    val clip = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Text("ZONE 2 RUNNING", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = 1.sp, color = p.ink)
    Panel {
        Label("Run logged")
        Head(c.title)
        Text("+${c.xp} XP", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 76.sp, lineHeight = 72.sp, color = p.accent)
        LevelBar(c.game)
        Body(c.message)
        NoteBox(Note("plain", c.summary + (c.estLine?.let { "\n$it" } ?: "")))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Flame(if (c.streakOn) p.hard else p.muted); Small(c.streakLine, p.ink) }
        if (c.newBadges.isNotEmpty()) c.newBadges.chunked(4).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { row.forEach { Medal(it, Modifier.weight(1f)) }; repeat(4 - row.size) { Spacer(Modifier.weight(1f)) } } }
        c.records.forEach { NoteBox(Note("good", it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("Done", onClick = done)
            Btn(if (copied) "Copied" else "Copy summary", ghost = true) { clip.setText(AnnotatedString(c.share)); copied = true }
        }
    }
}
