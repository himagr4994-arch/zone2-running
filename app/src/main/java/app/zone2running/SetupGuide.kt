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
import androidx.compose.ui.graphics.graphicsLayer
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
fun SetupScreen(vm: AppVM, e: Engine, replayIntro: () -> Unit, rerunGuide: () -> Unit) {
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
            Field("Zone 2 pace (min${U.per})", target, Modifier.weight(1f), fmtPace(420.0), KeyboardType.Ascii) { target = it }
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
            val tp = U.paceToKm(parseDur(target)); val mh = maxHr.toIntOrNull() ?: 0; val rh = rest.toIntOrNull() ?: 0
            val rf = ref.toIntOrNull() ?: 0; val z = cz.map { it.toIntOrNull() ?: 0 }
            val em = easy.toIntOrNull() ?: 0; val lm = long.toIntOrNull() ?: 0
            error = when {
                tp !in 180..900 -> "Enter the target pace like ${fmtPace(420.0)}."
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
        Head("Units")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pick("Kilometres", !s.miles, Modifier.weight(1f)) { vm.updateSettings { it.copy(miles = false) } }
            Pick("Miles", s.miles, Modifier.weight(1f)) { vm.updateSettings { it.copy(miles = true) } }
        }
    }

    RacePanel(vm, e)

    Panel {
        Head("Live run")
        Toggle("Spoken cues", "Block changes, each kilometre, and a nudge when you leave Zone 2. Vibration stays on either way.", s.voiceOn) { v -> vm.updateSettings { it.copy(voiceOn = v) } }
        if (s.sensorAddr.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
            Body("Sensor: ${s.sensorName.ifBlank { "saved" }}", modifier = Modifier.weight(1f))
            Btn("Forget", ghost = true, small = true) { vm.updateSettings { it.copy(sensorAddr = "", sensorName = "") } }
        } else Small("Pick a heart-rate sensor on the screen before a run starts.")
    }

    Panel {
        Head("Weather")
        var city by remember(s.city) { mutableStateOf(s.city) }
        if (s.weatherOn) {
            NoteBox(Note("good", "On for ${s.city}. Hot, humid hours lower the expected pace, and a run in the heat no longer counts against your Zone 2 estimate."))
            Btn("Turn off", ghost = true, small = true) { vm.weatherOff() }
        } else {
            Body("Heat and humidity slow everyone down at the same heart rate. Switch this on and the app allows for it.")
            Field("Town or city", city, keyboard = KeyboardType.Text, placeholder = "Raipur") { city = it }
            Btn(if (vm.cityBusy) "Looking it up…" else "Turn on") { vm.setCity(city) }
        }
        Small("This is the one feature that uses the internet. The app sends only the town's coordinates, a few times a day, to Open-Meteo. No account, no key. Weather data by Open-Meteo.com (CC BY 4.0).")
    }

    Panel {
        Head("Motivation")
        Toggle("Levels, streaks, quests and badges", "Turn off for a calmer app: progress, plan and coaching only.", s.gameOn) { v -> vm.updateSettings { it.copy(gameOn = v) } }
        ReminderPicker(s.remindOn, s.remindMin) { on, m -> vm.setReminder(on, m) }
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
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val clip = androidx.compose.ui.platform.LocalClipboardManager.current
        var crash by remember { mutableStateOf(Diagnostics.lastCrash(ctx)) }
        Head("Help and diagnostics")
        Body(if (crash != null) "The app closed unexpectedly on ${crash!!.lineSequence().first().take(16).replace('T', ' ')}. Copy the report and send it with a line on what you were doing." else "If something goes wrong, copy this report into your message. It holds the app version, phone model and settings switches, and no runs, heart rates or locations.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Copy report", ghost = true, small = true) { clip.setText(androidx.compose.ui.text.AnnotatedString(Diagnostics.report(ctx, vm.data, vm.healthGranted))); vm.toast = "Report copied" }
            if (crash != null) Btn("Clear", ghost = true, small = true) { Diagnostics.clear(ctx); crash = null }
        }
        if (BuildConfig.UPDATE_REPO.isNotBlank()) Toggle("Check for new versions", "Asks GitHub once when you open the app. Off by default.", s.updateCheck) { v -> vm.updateSettings { it.copy(updateCheck = v) } }
    }

    Panel {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Setup guide", ghost = true, onClick = rerunGuide)
            Btn("Replay Dil's intro", ghost = true, onClick = replayIntro)
        }
        Small("Zone 2 Running ${BuildConfig.VERSION_NAME} · open source under the MIT licence · not medical advice. Check with a doctor before starting a new training plan if you have a health condition.")
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun RacePanel(vm: AppVM, e: Engine) {
    val p = LocalPal.current
    val s = vm.settings
    var name by remember(s.raceName) { mutableStateOf(s.raceName) }
    var km by remember(s.raceKm) { mutableStateOf(s.raceKm) }
    var date by remember(s.raceDate) { mutableStateOf(s.raceDate) }
    var picking by remember { mutableStateOf(false) }
    Panel {
        Head("Race goal")
        Body("Optional. Pick an event and the plan builds toward it, tapers over the last two weeks and puts the race on your calendar.", p.muted)
        Field("Event name", name, keyboard = KeyboardType.Text, placeholder = "City half marathon") { name = it }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("5K" to 5.0, "10K" to 10.0, "Half" to 21.1, "Marathon" to 42.2).forEach { (n, d) -> Pick(n, km == d, Modifier.weight(1f)) { km = d } }
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, p.line, RoundedCornerShape(10.dp)).clickable { picking = true }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Small("Race date"); Body(date?.let { "${DOW[it.dayOfWeek.value - 1]} ${fmtDay(it)} ${it.year}" } ?: "Not set") }
            Small("Choose", p.accent)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Save race") {
                val d = date
                if (km <= 0 || d == null) vm.toast = "Pick a distance and a date" else if (d.isBefore(LocalDate.now())) vm.toast = "That date has passed" else { vm.setRace(name, d, km); vm.toast = "Race saved" }
            }
            if (s.raceDate != null) Btn("Remove", ghost = true) { vm.setRace("", null, 0.0) }
        }
        e.raceDaysLeft?.let { Small("${it} day${if (it == 1) "" else "s"} to go." + (e.racePrediction()?.let { t -> " Projected finish about ${fmtDur(t)}, from your hardest recent run." } ?: ""), p.ink) }
    }
    if (picking) {
        val st = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = (date ?: LocalDate.now().plusWeeks(12)).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli())
        androidx.compose.material3.DatePickerDialog(onDismissRequest = { picking = false }, confirmButton = {
            TextButton({ st.selectedDateMillis?.let { date = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate() }; picking = false }) { Text("OK") }
        }) { androidx.compose.material3.DatePicker(st) }
    }
}

/** Daily reminder: the runner picks a time first, then Android asks for permission. */
@Composable
fun ReminderPicker(on: Boolean, minutes: Int, onChange: (Boolean, Int) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var denied by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> denied = !ok; onChange(ok, minutes) }
    Toggle("Daily reminder", "One nudge on run days, and when your streak is at risk. Nothing on days with nothing to say.", on) { v ->
        if (v && android.os.Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(ctx)) ask.launch(android.Manifest.permission.POST_NOTIFICATIONS) else onChange(v, minutes)
    }
    if (on) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(360, 390, 420, 1080, 1140, 1200).forEach { m ->
                val h = m / 60; val label = "${if (h > 12) h - 12 else h}${if (m % 60 == 30) ":30" else ""} ${if (h >= 12) "pm" else "am"}"
                Pick(label, minutes == m, Modifier.weight(1f)) { onChange(true, m) }
            }
        }
        if (minutes !in listOf(360, 390, 420, 1080, 1140, 1200)) Small("Set for ${fmtClock(minutes)}, an hour before you usually run.", LocalPal.current.ink)
        NoteBox(Note("plain", "Preview: “Today: Easy run · 40 min. Keep HR in Zone 2.”"))
    }
    if (denied) Small("Notifications are blocked for this app. You can allow them in Android settings.")
}

private val LEVELS = listOf(Triple("Just starting", "Under 10 km a week|Under 6 miles a week", Triple(25, 40, false)), Triple("Running regularly", "10–25 km a week|6–15 miles a week", Triple(35, 60, true)), Triple("Experienced", "More than 25 km a week|More than 15 miles a week", Triple(45, 80, true)))

/** A big tappable answer. */
@Composable
private fun Choice(title: String, sub: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(if (on) p.goodBg else p.surface).border(if (on) 2.dp else 1.dp, if (on) p.accent else p.line, shape).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(title, fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = p.ink)
        if (sub.isNotEmpty()) Small(sub)
    }
}

/** One number, set by dragging; the − and + buttons nudge it by a single step. */
@Composable
private fun Dial(value: Int, min: Int, max: Int, step: Int = 1, unit: String = "", show: (Int) -> String = { "$it" }, onChange: (Int) -> Unit) {
    val p = LocalPal.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(show(value), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 72.sp, lineHeight = 72.sp, color = p.ink)
            if (unit.isNotEmpty()) Text(" $unit", Modifier.padding(bottom = 10.dp), fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = p.muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("−", ghost = true) { onChange((value - step).coerceAtLeast(min)) }
            androidx.compose.material3.Slider(value.toFloat(), { onChange((Math.round(it / step) * step).coerceIn(min, max)) }, Modifier.weight(1f), valueRange = min.toFloat()..max.toFloat())
            Btn("+", ghost = true) { onChange((value + step).coerceAtMost(max)) }
        }
    }
}

/**
 * The setup guide: one question per screen, asked by Dil. Most answers are a tap or a drag,
 * and each one visibly changes the plan shown at the end.
 */
@Composable
fun Onboarding(vm: AppVM, canClose: Boolean, close: () -> Unit) {
    val p = LocalPal.current
    val s0 = vm.settings
    val today = LocalDate.now()
    val logged = remember { vm.engine().eligible.lastOrNull { it.first.id != "_base" }?.first }
    val steps = remember { listOfNotNull("age", "rest", if (logged == null) "now" else null, "target", "level", "days", "long", "commit", "remind", if (s0.disclaimerOk) null else "safe", "plan") }
    var at by remember { mutableIntStateOf(0) }
    val step = steps[at]

    var age by remember { mutableIntStateOf(30) }
    var knowMax by remember { mutableStateOf(s0.onboarded) }
    var maxHr by remember { mutableIntStateOf(if (s0.onboarded) s0.maxHr else 187) }
    var rest by remember { mutableIntStateOf(s0.restHr) }
    var knowNow by remember { mutableStateOf(s0.basePace > 0) }
    var cur by remember { mutableIntStateOf(if (s0.basePace > 0) s0.basePace else 480) }
    var hr by remember { mutableIntStateOf(if (s0.baseHr > 0) s0.baseHr else 150) }
    var target by remember { mutableIntStateOf(s0.targetPace) }
    var targetPick by remember { mutableIntStateOf(if (s0.onboarded) 3 else -1) }     // 0..2 = suggested, 3 = own
    var level by remember { mutableIntStateOf(-1) }
    var days by remember { mutableStateOf(s0.days) }
    var longDay by remember { mutableIntStateOf(s0.longDay) }
    var easy by remember { mutableIntStateOf(s0.easyMin) }
    var long by remember { mutableIntStateOf(s0.longMin) }
    var quality by remember { mutableStateOf(s0.quality) }
    var goalWeeks by remember { mutableIntStateOf(s0.goalWeeks) }
    var remind by remember { mutableStateOf(s0.remindOn) }
    var remindMin by remember { mutableIntStateOf(s0.remindMin) }
    var adjust by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun built(): Settings {
        val base = logged == null && knowNow
        return s0.copy(
            targetPace = target, maxHr = if (knowMax) maxHr else (208 - .7 * age).roundToInt(), restHr = rest,
            zoneMethod = if (s0.zoneMethod == "custom") "custom" else "hrr",
            basePace = if (base) cur else if (logged == null) 0 else s0.basePace, baseHr = if (base) hr else if (logged == null) 0 else s0.baseHr,
            baseDate = if (base) (s0.baseDate ?: today) else s0.baseDate,
            days = days.sorted(), longDay = if (longDay in days) longDay else days.maxOrNull() ?: 7, easyMin = easy, longMin = long, quality = quality,
            planStart = s0.planStart ?: mondayOf(today), onboarded = true, disclaimerOk = true, goalWeeks = goalWeeks, remindOn = remind, remindMin = remindMin,
        )
    }
    val preview = Engine(built(), vm.data.runs, today)
    val now = preview.current
    fun next() { error = ""; if (at < steps.lastIndex) at++ }
    val round5 = { v: Double -> (v / 5).roundToInt() * 5 }

    val ask = when (step) {
        "age" -> "First things first. How old are you?"
        "rest" -> "And when you're resting, how fast do I beat?"
        "now" -> "Have you got a recent run I can learn from?"
        "target" -> "What easy pace shall we chase?"
        "level" -> "How much are you running these days?"
        "days" -> "Which days can you run?"
        "long" -> "Which day suits your longest run?"
        "commit" -> "Easy running pays off over weeks. How long will you give it?"
        "remind" -> "Want a nudge on run days?"
        "safe" -> "One honest thing before we start."
        else -> "Here's your plan."
    }

    Text("ZONE 2 RUNNING", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = 1.sp, color = p.ink)
    Meter(animFloat((at + 1f) / steps.size, 350), Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Dil(if (step == "plan") "proud" else "hello", Modifier.size(72.dp))
        Box(Modifier.weight(1f).clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)).background(p.surface).padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(ask, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 25.sp, color = p.ink)
        }
    }

    key(step) {
        Column(Modifier.rise(0), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (step) {
                "age" -> {
                    if (!knowMax) {
                        Dial(age, 14, 85, unit = "years") { age = it }
                        NoteBox(Note("plain", "That puts my top speed at about ${(208 - .7 * age).roundToInt()} beats a minute. It's an estimate, and it sets your zones."))
                        Text("I know my max heart rate", Modifier.clickable { knowMax = true; maxHr = (208 - .7 * age).roundToInt() }.padding(vertical = 6.dp), color = p.accent, fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    } else {
                        Dial(maxHr, 140, 215, unit = "bpm max") { maxHr = it }
                        Small("The highest heart rate you've seen at the end of an all-out effort.")
                        Text("Estimate it from my age", Modifier.clickable { knowMax = false }.padding(vertical = 6.dp), color = p.accent, fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }
                "rest" -> {
                    Dial(rest, 38, 95, unit = "bpm") { rest = it }
                    Small("Your watch shows this as resting heart rate. Not sure? Leave it at 60.")
                    ZoneStrip(preview)
                    Small("These are your zones. Zone 2 is where we'll spend most of our time.")
                }
                "now" -> {
                    Choice("Yes, I know a pace and heart rate", "Any steady run works. It doesn't have to be easy.", knowNow) { knowNow = true }
                    Choice("Not yet", "I'll log or import a run first.", false) { knowNow = false; next() }
                    if (knowNow) {
                        Small("Average pace of that run")
                        Dial(cur, 210, 780, 5, "${U.per}", { fmtPace(it.toDouble()) }) { cur = it }
                        Small("Average heart rate on it")
                        Dial(hr, 95, 200, unit = "bpm") { hr = it }
                        now?.let { NoteBox(Note("good", "So at an easy ${preview.refHr} bpm you'd run about ${fmtPace(it)} per ${U.word} today. That's your starting line.")) }
                    }
                }
                "target" -> {
                    if (now != null) {
                        Small("Your Zone 2 pace today is about ${fmtPace(now)} ${U.per}.")
                        listOf(Triple(45, "A steady step", "3–5"), Triple(75, "Ambitious", "4–8"), Triple(105, "A big goal", "6–11")).forEachIndexed { i, (gain, name, months) ->
                            val t = round5(now - gain).coerceAtLeast(210)
                            Choice("${fmtPace(t.toDouble())} ${U.per} · $name", "${U.s(gain)} seconds faster. Usually about $months months.", targetPick == i) { targetPick = i; target = t }
                        }
                        Choice("My own number", "", targetPick == 3) { targetPick = 3 }
                    }
                    if (now == null || targetPick == 3) Dial(target, 210, 720, 5, "${U.per}", { fmtPace(it.toDouble()) }) { target = it; targetPick = 3 }
                    Small("For scale: the average marathon finisher races at about ${fmtPace(384.0)} ${U.per}, and their easy Zone 2 pace is roughly ${fmtPace(420.0)} to ${fmtPace(465.0)}.")
                }
                "level" -> LEVELS.forEachIndexed { i, (name, sub, v) ->
                    Choice(name, sub.split("|")[if (U.miles) 1 else 0], level == i) { level = i; easy = v.first; long = v.second; quality = v.third; next() }
                }
                "days" -> {
                    DayPicker(days) { d -> days = if (d in days) days - d else days + d }
                    Head("${days.size} run${if (days.size == 1) "" else "s"} a week", 20)
                    Small(when { days.size < 2 -> "Pick at least two."; days.size <= 3 -> "A solid start. Every run will be easy."; days.size <= 5 -> "A good rhythm, with room for rest."; else -> "That's a lot of running. Make sure you keep a rest day when you need one." })
                    if (days.size >= 4) Toggle("Include one harder session", "Short intervals or a tempo, once a week.", quality) { quality = it }
                }
                "long" -> {
                    Small("It starts at $long minutes and grows slowly.")
                    days.sorted().chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { d -> Pick(listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")[d - 1], longDay == d, Modifier.weight(1f)) { longDay = d; next() } }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                "commit" -> {
                    listOf(Triple(4, "A fair try", "Long enough to feel the difference."), Triple(8, "Real change", "Long enough to see your pace move."), Triple(12, "A new habit", "Long enough that it sticks.")).forEach { (w, t, sub) ->
                        Choice("$w weeks · $t", sub, goalWeeks == w) { goalWeeks = w; next() }
                    }
                    Small("A week counts when you finish about three-quarters of its runs. Miss one and a rest pass covers it.")
                }
                "remind" -> ReminderPicker(remind, remindMin) { on, m -> remind = on; remindMin = m }
                "safe" -> {
                    Body("I'm a training guide, not a doctor. Heart-rate zones and pace estimates here are rough, and they come from formulas, not from testing you.")
                    Body("Talk to a doctor before starting if you have a heart condition, chest pain, dizziness or fainting, are pregnant, or are coming back from illness or injury.")
                    Body("On any run: if something feels wrong, stop.")
                }
                else -> {
                    val tg = preview.s.targetPace.toDouble(); val gap = (now ?: tg) - tg
                    val plan = preview.weekPlan(mondayOf(today))
                    Panel {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column { Label("Zone 2 now"); Text(now?.let { fmtPace(it) } ?: "–", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 58.sp, lineHeight = 58.sp, color = p.ink) }
                            Text("to", Modifier.padding(bottom = 10.dp), fontFamily = Display, fontSize = 30.sp, color = p.muted)
                            Column { Label("Target"); Text(fmtPace(tg), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 58.sp, lineHeight = 58.sp, color = p.ink) }
                        }
                        Body(when {
                            now == null -> "Log or import your first run and I'll work out where you're starting from."
                            gap > 0 -> "That is ${fmtPace(gap)} per km to gain at ${preview.refHr} bpm. Runners typically gain ${U.s(10)}–${U.s(20)} s${U.per} a month with consistent easy running, so plan for about ${max(1, (gap / 20).roundToInt())}–${max(2, (gap / 10).roundToInt())} months."
                            else -> "You already hold this pace in Zone 2. Go back and pick a faster target, or keep it and use the plan to stay there."
                        })
                        Label("Week 1 · ${plan.total} minutes")
                        plan.ses.entries.forEachIndexed { i, (d, ses) ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(DOW[d - 1], Modifier.width(44.dp), fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = p.ink)
                                Column { Text("${ses.title} · ${ses.min} min", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = p.ink, fontFamily = BodyFont); Small(ses.detail) }
                            }
                        }
                        NoteBox(Note("plain", "Built from your answers: Zone 2 of ${preview.zb[1]}–${preview.zb[2] - 1} bpm from max ${preview.s.maxHr} and resting ${preview.s.restHr}. ${plan.ses.size} runs on the days you picked. A $goalWeeks-week streak to aim for."))
                        Text(if (adjust) "Hide run lengths" else "Adjust run lengths", Modifier.clickable { adjust = !adjust }.padding(vertical = 4.dp), color = p.accent, fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        if (adjust) {
                            Small("Easy run"); Dial(easy, 15, 90, 5, "min") { easy = it }
                            Small("Long run"); Dial(long, 30, 180, 5, "min") { long = it }
                        }
                    }
                }
            }
        }
    }
    if (error.isNotEmpty()) NoteBox(Note("warn", error))
    Row {
        if (at > 0 || canClose) Btn(if (at == 0) "Close" else "Back", ghost = true) { error = ""; if (at == 0) close() else at-- }
        Spacer(Modifier.weight(1f))
        val tapOnly = step == "level" && level < 0      // nothing to confirm until a card is tapped
        if (!tapOnly) Btn(when (step) { "plan" -> "Start training"; "remind" -> if (remind) "Next" else "Not now"; "safe" -> "I understand"; else -> "Next" }) {
            error = when {
                step == "days" && days.size < 2 -> "Pick at least two running days."
                step == "age" && knowMax && maxHr - rest < 50 -> "Max and resting heart rate are too close. Check them."
                step == "rest" && preview.s.maxHr - rest < 50 -> "Resting heart rate is too close to your max. Check it."
                step == "target" && now != null && targetPick < 0 -> "Pick a target to continue."
                else -> ""
            }
            if (error.isEmpty()) { if (step != "plan") next() else { val b = built(); vm.updateSettings { b }; vm.setReminder(b.remindOn, b.remindMin); vm.refreshHealth(); close() } }
        }
    }
}

@Composable
fun RewardScreen(c: Celebration, share: () -> Unit, done: () -> Unit) {
    val p = LocalPal.current
    val pop = enter(650, 100)
    Text("ZONE 2 RUNNING", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = 1.sp, color = p.ink)
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Dil(if (c.celebrate) "proud" else "calm", Modifier.size((56 + 24 * pop).dp))
            Column { Label("Run logged"); Head(if (c.streakMilestone > 0) "${c.streakMilestone}-week streak!" else c.title) }
        }
        Text("+${(c.xp * pop).toInt()} XP", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 76.sp, lineHeight = 72.sp, color = p.accent)
        LevelBar(c.game)
        Body(c.message)
        NoteBox(Note("plain", c.summary + (c.estLine?.let { "\n$it" } ?: "")))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Flame(if (c.streakOn) p.hard else p.muted); Small(c.streakLine, p.ink) }
        if (c.newBadges.isNotEmpty()) c.newBadges.chunked(4).forEach { row ->
            Row(Modifier.graphicsLayer { scaleX = .6f + .4f * pop; scaleY = .6f + .4f * pop; alpha = pop }, horizontalArrangement = Arrangement.spacedBy(6.dp)) { row.forEach { Medal(it, Modifier.weight(1f)) }; repeat(4 - row.size) { Spacer(Modifier.weight(1f)) } }
        }
        c.records.forEach { NoteBox(Note("good", it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("Done", onClick = done)
            Btn("Share", ghost = true, onClick = share)
        }
    }
}
