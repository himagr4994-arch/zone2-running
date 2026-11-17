package app.zone2running

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun LogScreen(vm: AppVM, e: Engine, openRun: (Run) -> Unit, openForm: (FormSeed) -> Unit) {
    val p = LocalPal.current
    Row(Modifier.rise(1), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Head("${e.runs.size} run${if (e.runs.size == 1) "" else "s"}", modifier = Modifier.weight(1f))
        if (vm.healthGranted) Btn(if (vm.importing) "Importing…" else "Import", ghost = true, small = true) { vm.importHealth(manual = true) }
        Btn("Add run", small = true) { openForm(FormSeed()) }
    }
    if (e.runs.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Dil("hello", Modifier.size(72.dp))
        Body(if (vm.healthGranted) "No runs yet. Import them from Health Connect, or add one by hand." else "No runs yet. Add one with distance, time and average heart rate.", p.muted, Modifier.weight(1f))
    }
    Column(Modifier.rise(2)) {
        val tick = haptic()
        e.runs.asReversed().forEachIndexed { i, r ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
            val z = if (r.avgHr > 0) e.zoneOf(r.avgHr) else 0
            Row(Modifier.fillMaxWidth().clickable { tick(false); openRun(r) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SessionArt(when (r.type) { "long" -> "long"; "test" -> "test"; "tempo" -> "tempo"; "intervals" -> "intervals"; "race" -> "race"; "hard" -> "tempo"; else -> "easy" }, Modifier.size(40.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${DOW[r.date.dayOfWeek.value - 1]} ${fmtDay(r.date)} · ${TYPE_NAMES[r.type] ?: "Run"}", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = p.ink, fontFamily = BodyFont)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Small("${U.dist(r.distKm)} · ${fmtDur(r.durSec)}")
                        if (z > 0) { Dot(p.zones[z - 1]); Small("Z$z") }
                    }
                }
                Text(fmtPace(paceOf(r)), fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = p.ink)
            }
        }
    }
}

/** One run, shown rather than edited: the pace, where the heart rate sat, the splits, and what it says about Zone 2. */
@Composable
fun RunPage(e: Engine, r: Run, rate: (Int) -> Unit, edit: () -> Unit, close: () -> Unit) {
    val p = LocalPal.current
    val z = if (r.avgHr > 0) e.zoneOf(r.avgHr) else 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clip(androidx.compose.foundation.shape.CircleShape).clickable(onClick = close).padding(10.dp)) { Glyph("left", p.ink, Modifier.size(22.dp)) }
        Spacer(Modifier.weight(1f))
        TextLink("Edit", edit)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label("${DAY_NAMES[r.date.dayOfWeek.value - 1]} ${fmtDay(r.date)} ${r.date.year}" + if (r.startMin >= 0) " · ${fmtClock(r.startMin)}" else "")
        Text(SWAP_KINDS[r.kind]?.takeIf { r.kind in setOf("runwalk", "talk", "4x4", "6x3") } ?: TYPE_NAMES[r.type] ?: "Run", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 32.sp, color = p.ink)
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.hero).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(fmtPace(paceOf(r)), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 88.sp, lineHeight = 80.sp, color = p.heroInk)
            Text(" ${U.per}", Modifier.padding(bottom = 7.dp), fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = p.heroMuted)
        }
        Row {
            for ((v, l) in listOf(f2(U.k(r.distKm)) to U.unit, fmtDur(r.durSec) to "time", (if (r.avgHr > 0) "${r.avgHr}" else "–") to (if (z > 0) "bpm · Z$z" else "bpm")))
                Column(Modifier.weight(1f)) { Text(v, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, color = p.heroInk); Text(l, fontSize = 13.sp, color = p.heroMuted, fontFamily = BodyFont) }
        }
    }
    val d = remember(r, e) { e.debrief(r) }
    if (r.cadence > 0 || r.kcal > 0 || r.climb > 0) Small(listOfNotNull(if (r.cadence > 0) "${r.cadence} steps a minute" else null, if (r.kcal > 0) "${r.kcal} kcal" else null, if (r.climb > 0) "${r.climb} m climbed" else null).joinToString("  ·  "), p.ink)

    // The verdict in one line up top; the reasons and the advice come last, after the evidence.
    Text(d.head, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 30.sp, color = p.ink)

    if (r.trace.count { it > 0 } >= 30) RunCharts(e, r, d)

    if (d.blocks.size > 1) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("Block by block")
        BlockRows(e, d.blocks)
        if (d.planned) Small("Blocks are laid over from the day's plan, assuming you started the session when the run began.")
    }

    if (r.zoneSec.size == 5 && r.zoneSec.sum() > 0) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val tot = r.zoneSec.sum()
        Label("Time in zones")
        ZoneBar(r.zoneSec)
        ZoneRows(e, r.zoneSec)
        if (tot < r.durSec * .8) Small("Heart rate was recorded for ${tot / 60} of ${r.durSec / 60} minutes.")
    }
    if (r.splits.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Label("${U.word.replaceFirstChar { it.uppercase() }} splits"); SplitBars(r.splits) }

    if (d.facts.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("The numbers")
        d.facts.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { f -> FactTile(f, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    EffortRow(r.feel, rate)

    if (d.learned.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("What this run says")
        d.learned.forEach { NoteBox(it) }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.hero).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label("Next time", p.heroMuted)
        Text(d.next, fontFamily = BodyFont, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 23.sp, color = p.heroInk)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (r.heatPct >= 1.5) Small("Weather that hour slowed running by roughly ${f1(r.heatPct)}%. The Zone 2 reading allows for it.", p.ink)
        if (r.notes.isNotBlank()) Small(r.notes, p.ink)
        Small(when (r.source) { "health" -> "Imported from Health Connect."; "live" -> "Recorded with a live run."; else -> "Entered by hand." })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunForm(vm: AppVM, e: Engine, seed: FormSeed, close: () -> Unit) {
    val p = LocalPal.current
    val r0 = seed.run
    val isNew = r0 == null || seed.fresh
    var date by remember { mutableStateOf(r0?.date ?: seed.date) }
    var type by remember { mutableStateOf(r0?.type ?: seed.type) }
    var dist by remember { mutableStateOf(r0?.takeIf { it.distKm > 0 }?.let { f2(U.k(it.distKm)) } ?: "") }
    var time by remember { mutableStateOf(r0?.let { fmtDur(it.durSec) } ?: "") }
    var hr by remember { mutableStateOf(r0?.avgHr?.takeIf { it > 0 }?.toString() ?: "") }
    var maxHr by remember { mutableStateOf(r0?.maxHr?.takeIf { it > 0 }?.toString() ?: "") }
    var feel by remember { mutableIntStateOf(r0?.feel ?: 0) }
    var notes by remember { mutableStateOf(r0?.notes ?: "") }
    var startMin by remember { mutableIntStateOf(r0?.startMin ?: -1) }
    var picking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    fun draft() = Run(r0?.id ?: "run_${date.toString().replace("-", "")}_${System.currentTimeMillis().toString(36)}", date, type,
        (U.toKm(dist.replace(',', '.').toDoubleOrNull() ?: 0.0) * 100).roundToInt() / 100.0, parseDur(time), hr.toIntOrNull() ?: 0, maxHr.toIntOrNull() ?: 0,
        feel, notes.trim().take(500), r0?.source ?: "manual", r0?.zoneSec ?: emptyList(), startMin, r0?.splits ?: emptyList(), r0?.heatPct ?: 0.0)

    Panel {
        Head(if (seed.fresh) "Finish logging your run" else if (r0 == null) "Log a run" else "Edit run")
        if (seed.fresh) NoteBox(Note("warn", "GPS didn't record a distance. Add it and the run is saved with its time and heart rate."))
        if (r0 != null && r0.splits.isNotEmpty()) { Small("Kilometre splits and average heart rate"); SplitBars(r0.splits) }
        if (r0 != null && r0.heatPct >= 1.5) Small("Weather that hour slowed running by roughly ${f1(r0.heatPct)}%. The Zone 2 estimate allows for it.", p.ink)
        if (r0 != null && r0.zoneSec.size == 5 && r0.zoneSec.sum() > 0) {
            val tot = r0.zoneSec.sum().toFloat()
            Small("Time in each zone, from your heart-rate samples")
            Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                r0.zoneSec.forEachIndexed { i, sec -> if (sec > 0) Box(Modifier.weight(sec / tot).fillMaxHeight().background(p.zones[i])) }
            }
            Small(r0.zoneSec.mapIndexedNotNull { i, sec -> if (sec >= 60) "Z${i + 1} ${sec / 60} min" else null }.joinToString(" · "))
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, p.line, RoundedCornerShape(10.dp)).clickable { picking = true }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Small("Date"); Body("${DOW[date.dayOfWeek.value - 1]} ${fmtDay(date)} ${date.year}") }
            Small("Change", p.accent)
        }
        Small("Type")
        val types = TYPE_NAMES.entries.filter { it.key != "baseline" }
        types.chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { row.forEach { (k, v) -> Pick(v.removeSuffix(" run").replace("Long easy", "Long").replace("Steady / tempo", "Tempo").replace("Hard effort", "Hard"), type == k, Modifier.weight(1f)) { type = k } } } }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Distance (${U.unit})", dist, Modifier.weight(1f), if (U.miles) "6.2" else "10.26", KeyboardType.Decimal) { dist = it }
            Field("Time", time, Modifier.weight(1f), "1:11:37", KeyboardType.Ascii) { time = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Average HR", hr, Modifier.weight(1f), "142") { hr = it }
            Field("Highest HR", maxHr, Modifier.weight(1f), "optional") { maxHr = it }
        }
        if (r0?.source != "health") {
            Small("When did you run? Optional. It teaches the app your routine.")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Early" to 360, "Morning" to 510, "Midday" to 780, "Evening" to 1110, "Night" to 1260).forEach { (n, m) -> Pick(n, startMin == m, Modifier.weight(1f)) { startMin = if (startMin == m) -1 else m } }
            }
        }
        Small("How it felt: 1 very easy, 3 steady, 5 all out")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FEELS.forEachIndexed { i, n -> Pick("${i + 1}", feel == i + 1, Modifier.weight(1f)) { feel = if (feel == i + 1) 0 else i + 1 } }
        }
        Field("Notes (heat, route, sleep)", notes, keyboard = KeyboardType.Text, singleLine = false) { notes = it }
        val d = draft()
        val preview = if (d.distKm > 0 && d.durSec > 0) buildString {
            append("Pace ${fmtPace(paceOf(d))} ${U.per}")
            if (d.avgHr > 0) {
                append(" · Zone ${e.zoneOf(d.avgHr)}")
                e.est(d)?.let { append(" · Zone 2 estimate ${fmtPace(it)} ${U.per}") } ?: if (d.type == "intervals") append(" · intervals are left out of the estimate") else Unit
            }
        } else "Enter distance, time and average HR to see pace, zone and your Zone 2 estimate."
        NoteBox(Note("plain", preview))
        if (error.isNotEmpty()) NoteBox(Note("warn", error))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("Save run") {
                val r = draft()
                error = when {
                    r.distKm <= 0 -> "Enter the distance in ${U.unit}."
                    r.durSec <= 0 -> "Enter the time, like 45:30 or 1:11:37."
                    r.avgHr != 0 && r.avgHr !in 60..230 -> "Average HR looks off. Check it."
                    r.date.isAfter(LocalDate.now()) -> "That date is in the future."
                    else -> ""
                }
                if (error.isEmpty()) { vm.saveRun(r, isNew = isNew); close() }
            }
            Btn("Cancel", ghost = true, onClick = close)
            if (!isNew && r0 != null) Btn(if (confirmDelete) "Tap again to delete" else "Delete", danger = true) {
                if (confirmDelete) { vm.deleteRun(r0.id); close() } else confirmDelete = true
            }
        }
    }
    if (picking) {
        val st = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { picking = false }, confirmButton = {
            TextButton({ st.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }; picking = false }) { Text("OK") }
        }) { DatePicker(st) }
    }
}

@Composable
fun ProgressScreen(vm: AppVM, e: Engine, openRun: (Run) -> Unit, openForm: (FormSeed) -> Unit) {
    val p = LocalPal.current
    val g = e.game
    val gameOn = vm.settings.gameOn
    var seg by remember { mutableIntStateOf(0) }
    val segs = if (gameOn) listOf("Stats", "Runs", "Badges") else listOf("Stats", "Runs")
    Row(Modifier.rise(0).clip(RoundedCornerShape(12.dp)).background(p.surface2).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        segs.forEachIndexed { i, n ->
            Box(Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (seg == i) p.surface else Color.Transparent).clickable { seg = i }.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                Text(n, fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = if (seg == i) p.ink else p.muted)
            }
        }
    }
    if (seg == 1) { LogScreen(vm, e, openRun, openForm); return }
    if (seg == 2) Panel(Modifier.rise(1)) {
        var picked by remember { mutableStateOf<Badge?>(null) }
        Row(verticalAlignment = Alignment.Bottom) { Head("Badges", 18, modifier = Modifier.weight(1f)); Small("${g.badges.count { it.got }} of ${g.badges.size} earned") }
        g.badges.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { b -> Medal(b, Modifier.weight(1f)) { picked = b } }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        val b = picked
        Small(if (b == null) "Tap a badge to see how to earn it." else "${b.name}: ${b.desc}" + when {
            b.got -> " Earned."
            b.prog != null -> " ${f1(min(b.prog.have, b.prog.need))} of ${if (b.prog.need % 1.0 == 0.0) b.prog.need.toInt().toString() else f1(b.prog.need)} ${b.prog.unit}."
            else -> ""
        })
    }
    if (seg == 2) {
        Panel(Modifier.rise(3)) {
            val c = e.challenge()
            Row { Label("${MONTHS_FULL[e.today.monthValue - 1]} challenge"); Spacer(Modifier.weight(1f)); Small("${c.daysLeft} day${if (c.daysLeft == 1) "" else "s"} left") }
            Head("${c.goal} easy minutes", 18)
            Meter(c.done.toFloat() / c.goal * enter(900, 300))
            Small(if (c.done >= c.goal) "Done. ${c.done} easy minutes this month." else "${c.done} done, ${c.goal - c.done} to go. Only time in Zone 1–2 counts.")
            Small("Rest passes: ${g.passes} of 2. A pass covers a missed week so your streak survives. You earn one every three completed weeks.")
        }
        return
    }

    Column(Modifier.rise(1), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var scrub by remember { mutableStateOf<Int?>(null) }
        val tick = haptic()
        val pts = e.eligible
        Head("Zone 2 pace", 20)
        if (pts.isEmpty()) Body("Your trend appears once you log a run with average heart rate.", p.muted)
        else {
            val at = pts[scrub ?: pts.lastIndex]
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(fmtPace(at.second), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 44.sp, color = p.ink)
                Small("${U.per} · ${fmtDay(at.first.date)} · ${TYPE_NAMES[at.first.type] ?: "Run"}" + if (at.first.id != "_base") " · ran ${fmtPace(paceOf(at.first))} at ${at.first.avgHr}" else "", modifier = Modifier.padding(bottom = 6.dp))
            }
            PaceArea(pts, e.s.targetPace.toDouble(), e.today, scrub, { if (it != scrub) { scrub = it; if (it != null) tick(false) } }, Modifier.fillMaxWidth().height(170.dp),
                line = p.accent, target = p.ink, muted = p.muted, dot = p.ink, ground = p.bg)
            val tr = e.trend; val cur = e.current; val tg = e.s.targetPace.toDouble()
            Small(when {
                cur == null || cur <= tg -> "Drag across the chart to read any run. The dashed line is your target."
                tr != null && tr.b < -0.02 -> ((tg - tr.a) / tr.b).roundToInt().let { days -> if (days > 1100) "Your trend is nearly flat. More easy volume is the lever." else tr.t0.plusDays(days.toLong()).let { "On this trend you reach ${fmtPace(tg)} around ${MONTHS[it.monthValue - 1]} ${it.year}." } }
                else -> "Drag across the chart to read any run. A projection appears after 4 steady runs across 3 weeks."
            })
        }
    }

    Column(Modifier.rise(2), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var pick by remember { mutableStateOf<Int?>(null) }
        val tick = haptic()
        Head("Weekly minutes", 20)
        val base = mondayOf(e.today); val wi = pick ?: 7
        val wmon = base.minusWeeks((7 - wi).toLong()); val sp = e.span(wmon, wmon.plusDays(7))
        Small("Week of ${fmtDay(wmon)}: ${sp.easy.roundToInt()} easy, ${sp.hard.roundToInt()} harder, ${U.dist(sp.km, 1)}." + if (pick == null) " Tap a bar." else "", p.ink)
        Legend(p.easy to "Easy (Zone 1–2)", p.hard to "Zone 3 and above")
        WeekChart(e, pick) { if (it != pick) { pick = it; tick(false) } }
    }

    Panel {
        var picked by remember { mutableStateOf<LocalDate?>(null) }
        Head("Last 12 weeks", 18)
        Legend(p.easy to "Easy run", p.hard to "Zone 3 and above")
        val start = mondayOf(e.today).minusWeeks(11)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (w in 0..11) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (i in 0..6) {
                    val d = start.plusDays((w * 7 + i).toLong()); val rs = e.runsOn(d); val shape = RoundedCornerShape(4.dp)
                    val mins = rs.sumOf { it.durSec } / 60
                    val fill = when {
                        d.isAfter(e.today) -> Color.Transparent
                        rs.isEmpty() -> p.surface2
                        else -> (if (rs.all { e.isEasy(it) }) p.easy else p.hard).copy(alpha = if (mins < 30) .55f else if (mins < 60) .8f else 1f)
                    }
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(shape).background(fill)
                        .then(if (d == e.today) Modifier.border(2.dp, p.ink, shape) else if (d.isAfter(e.today)) Modifier.border(1.dp, p.line, shape) else Modifier)
                        .clickable { picked = d })
                }
            }
        }
        val d = picked
        Small(if (d == null) "Each column is a week, Monday at the top. Darker means longer. Tap a day." else "${DOW[d.dayOfWeek.value - 1]} ${fmtDay(d)}: " + e.runsOn(d).let { rs ->
            if (rs.isEmpty()) (if (d.isAfter(e.today)) "still to come." else "no run.")
            else rs.joinToString("; ") { r -> "${TYPE_NAMES[r.type] ?: "Run"}, ${U.dist(r.distKm)} at ${fmtPace(paceOf(r))}" + if (r.avgHr > 0) ", ${r.avgHr} bpm" else "" }
        })
    }

    Panel {
        Head("Personal records", 18)
        if (e.runs.isEmpty()) Body("Records appear after your first run.", p.muted)
        else listOfNotNull(
            "Longest run" to "${U.dist(g.longest)}", "Longest time on feet" to fmtDur(g.longSec),
            "Best Zone 2 estimate" to (g.bestEst?.let { "${fmtPace(it)} ${U.per}" } ?: "–"), "Fastest run, 3 km+" to (g.fast?.let { "${fmtPace(it)} ${U.per}" } ?: "–"),
            "Biggest week" to "${U.dist(g.bigWeek, 1)}", if (gameOn) "Longest streak" to "${g.best} week${if (g.best == 1) "" else "s"}" else null,
            "Total distance" to "${U.dist(g.km, 1)}",
        ).forEachIndexed { i, (k, v) ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
            Row(verticalAlignment = Alignment.CenterVertically) { Body(k, modifier = Modifier.weight(1f)); Text(v, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = p.ink) }
        }
    }
}

@Composable
private fun Legend(vararg items: Pair<Color, String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEach { (c, t) -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(c)); Small(t) } }
    }
}

private fun DrawScope.label(text: String, x: Float, y: Float, color: Color, align: Paint.Align, bold: Boolean = false) {
    val paint = Paint().apply { isAntiAlias = true; textSize = 11.5.sp.toPx(); this.color = color.toArgb(); textAlign = align; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}

@Composable
private fun WeekChart(e: Engine, pick: Int?, onPick: (Int) -> Unit) {
    val p = LocalPal.current
    val grow = enter(800, 200)
    Canvas(Modifier.fillMaxWidth().height(200.dp).pointerInput(Unit) {
        detectTapGestures { o -> val l = 36.dp.toPx(); val slot = (size.width - l - 8.dp.toPx()) / 8; onPick(((o.x - l) / slot).toInt().coerceIn(0, 7)) }
    }) {
        val l = 36.dp.toPx(); val r = 8.dp.toPx(); val t = 12.dp.toPx(); val b = 26.dp.toPx()
        val base = mondayOf(e.today)
        val weeks = (7 downTo 0).map { base.minusWeeks(it.toLong()) }.map { it to e.span(it, it.plusDays(7)) }
        val mx = max(60.0, weeks.maxOf { it.second.easy + it.second.hard })
        val step = if (mx > 300) 120 else if (mx > 150) 60 else 30
        val top = ceil(mx / step) * step
        fun y(v: Double) = size.height - b - (v / top).toFloat() * (size.height - t - b)
        fun yb(v: Double) = size.height - b - (v / top).toFloat() * (size.height - t - b) * grow     // bars rise from the baseline
        val slot = (size.width - l - r) / 8
        val bw = min(28.dp.toPx(), slot - 8.dp.toPx())
        var v = 0.0
        while (v <= top + .1) { drawLine(p.line, Offset(l, y(v)), Offset(size.width - r, y(v)), 1.dp.toPx()); label("${v.toInt()}", l - 8.dp.toPx(), y(v) + 4.dp.toPx(), p.muted, Paint.Align.RIGHT); v += step }
        val gap = 2.dp.toPx(); val rad = CornerRadius(4.dp.toPx())
        weeks.forEachIndexed { i, (mon, s) ->
            val cx = l + (i + .5f) * slot - bw / 2
            if (pick == i) drawRoundRect(p.surface2, Offset(l + i * slot + 2.dp.toPx(), t), Size(slot - 4.dp.toPx(), size.height - t - b), CornerRadius(6.dp.toPx()))
            val ye = yb(s.easy); val yt = yb(s.easy + s.hard)
            if (s.easy > 0) drawRoundRect(p.easy, Offset(cx, ye), Size(bw, y(0.0) - ye), if (s.hard > 0) CornerRadius.Zero else rad)
            if (s.hard > 0) { val bottom = ye - if (s.easy > 0) gap else 0f; if (bottom > yt) drawRoundRect(p.hard, Offset(cx, yt), Size(bw, bottom - yt), rad) }
            if (i % 2 == 1) label(fmtDay(mon), l + (i + .5f) * slot, size.height - 6.dp.toPx(), p.muted, Paint.Align.CENTER)
        }
    }
}
