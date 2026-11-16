package app.easyseven

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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
fun LogScreen(vm: AppVM, e: Engine, openForm: (FormSeed) -> Unit) {
    val p = LocalPal.current
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Head("Runs", modifier = Modifier.weight(1f))
            if (vm.healthGranted) Btn(if (vm.importing) "Importing…" else "Import", ghost = true, small = true) { vm.importHealth(manual = true) }
            Btn("Add run", small = true) { openForm(FormSeed()) }
        }
        if (e.runs.isEmpty()) Body(if (vm.healthGranted) "No runs yet. Tap Import to pull them from Health Connect, or add one by hand." else "No runs yet. Tap Add run and copy distance, time and average HR from your watch.", p.muted)
        e.runs.asReversed().forEachIndexed { i, r ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
            val z = if (r.avgHr > 0) e.zoneOf(r.avgHr) else 0
            Row(Modifier.fillMaxWidth().clickable { openForm(FormSeed(run = r)) }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("${DOW[r.date.dayOfWeek.value - 1]} ${fmtDay(r.date)} · ${TYPE_NAMES[r.type] ?: "Run"}", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = p.ink, fontFamily = BodyFont)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Small("${f2(r.distKm)} km · ${fmtDur(r.durSec)}")
                        if (z > 0) { Dot(p.zones[z - 1]); Small("Z$z, ${r.avgHr} bpm") }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(fmtPace(paceOf(r)), fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = p.ink)
                    e.est(r)?.let { Small("Z2 est. ${fmtPace(it)}") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunForm(vm: AppVM, e: Engine, seed: FormSeed, close: () -> Unit) {
    val p = LocalPal.current
    val r0 = seed.run
    var date by remember { mutableStateOf(r0?.date ?: seed.date) }
    var type by remember { mutableStateOf(r0?.type ?: seed.type) }
    var dist by remember { mutableStateOf(r0?.let { f2(it.distKm) } ?: "") }
    var time by remember { mutableStateOf(r0?.let { fmtDur(it.durSec) } ?: "") }
    var hr by remember { mutableStateOf(r0?.avgHr?.takeIf { it > 0 }?.toString() ?: "") }
    var maxHr by remember { mutableStateOf(r0?.maxHr?.takeIf { it > 0 }?.toString() ?: "") }
    var feel by remember { mutableIntStateOf(r0?.feel ?: 0) }
    var notes by remember { mutableStateOf(r0?.notes ?: "") }
    var picking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    fun draft() = Run(r0?.id ?: "run_${date.toString().replace("-", "")}_${System.currentTimeMillis().toString(36)}", date, type,
        ((dist.replace(',', '.').toDoubleOrNull() ?: 0.0) * 100).roundToInt() / 100.0, parseDur(time), hr.toIntOrNull() ?: 0, maxHr.toIntOrNull() ?: 0,
        feel, notes.trim().take(500), r0?.source ?: "manual")

    Panel {
        Head(if (r0 == null) "Log a run" else "Edit run")
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, p.line, RoundedCornerShape(10.dp)).clickable { picking = true }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Small("Date"); Body("${DOW[date.dayOfWeek.value - 1]} ${fmtDay(date)} ${date.year}") }
            Small("Change", p.accent)
        }
        Small("Type")
        val types = TYPE_NAMES.entries.filter { it.key != "baseline" }
        types.chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { row.forEach { (k, v) -> Pick(v.removeSuffix(" run").replace("Long easy", "Long").replace("Steady / tempo", "Tempo").replace("Hard effort", "Hard"), type == k, Modifier.weight(1f)) { type = k } } } }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Distance (km)", dist, Modifier.weight(1f), "10.26", KeyboardType.Decimal) { dist = it }
            Field("Time", time, Modifier.weight(1f), "1:11:37", KeyboardType.Ascii) { time = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Average HR", hr, Modifier.weight(1f), "142") { hr = it }
            Field("Highest HR", maxHr, Modifier.weight(1f), "optional") { maxHr = it }
        }
        Small("How it felt: 1 very easy, 3 steady, 5 all out")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Very easy", "Easy", "Steady", "Hard", "All out").forEachIndexed { i, n -> Pick("${i + 1}", feel == i + 1, Modifier.weight(1f)) { feel = if (feel == i + 1) 0 else i + 1 } }
        }
        Field("Notes (heat, route, sleep)", notes, keyboard = KeyboardType.Text, singleLine = false) { notes = it }
        val d = draft()
        val preview = if (d.distKm > 0 && d.durSec > 0) buildString {
            append("Pace ${fmtPace(paceOf(d))} /km")
            if (d.avgHr > 0) {
                append(" · Zone ${e.zoneOf(d.avgHr)}")
                e.est(d)?.let { append(" · Zone 2 estimate ${fmtPace(it)} /km") } ?: if (d.type == "intervals") append(" · intervals are left out of the estimate") else Unit
            }
        } else "Enter distance, time and average HR to see pace, zone and your Zone 2 estimate."
        NoteBox(Note("plain", preview))
        if (error.isNotEmpty()) NoteBox(Note("warn", error))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("Save run") {
                val r = draft()
                error = when {
                    r.distKm <= 0 -> "Enter the distance in km."
                    r.durSec <= 0 -> "Enter the time, like 45:30 or 1:11:37."
                    r.avgHr != 0 && r.avgHr !in 60..230 -> "Average HR looks off. Check it."
                    r.date.isAfter(LocalDate.now()) -> "That date is in the future."
                    else -> ""
                }
                if (error.isEmpty()) { vm.saveRun(r, isNew = r0 == null); close() }
            }
            Btn("Cancel", ghost = true, onClick = close)
            if (r0 != null) Btn(if (confirmDelete) "Tap again to delete" else "Delete", danger = true) {
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
fun ProgressScreen(e: Engine, gameOn: Boolean) {
    val p = LocalPal.current
    val g = e.game
    if (gameOn) Panel {
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
            else rs.joinToString("; ") { r -> "${TYPE_NAMES[r.type] ?: "Run"}, ${f2(r.distKm)} km at ${fmtPace(paceOf(r))}" + if (r.avgHr > 0) ", ${r.avgHr} bpm" else "" }
        })
    }

    Panel {
        Head("Personal records", 18)
        if (e.runs.isEmpty()) Body("Records appear after your first run.", p.muted)
        else listOfNotNull(
            "Longest run" to "${f2(g.longest)} km", "Longest time on feet" to fmtDur(g.longSec),
            "Best Zone 2 estimate" to (g.bestEst?.let { "${fmtPace(it)} /km" } ?: "–"), "Fastest run, 3 km+" to (g.fast?.let { "${fmtPace(it)} /km" } ?: "–"),
            "Biggest week" to "${f1(g.bigWeek)} km", if (gameOn) "Longest streak" to "${g.best} week${if (g.best == 1) "" else "s"}" else null,
            "Total distance" to "${f1(g.km)} km",
        ).forEachIndexed { i, (k, v) ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
            Row(verticalAlignment = Alignment.CenterVertically) { Body(k, modifier = Modifier.weight(1f)); Text(v, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = p.ink) }
        }
    }

    Panel {
        Head("Estimated Zone 2 pace, per run", 18)
        Small("Pace you would hold at ${e.refHr} bpm, worked out from each steady run. Faster is higher. Ringed points are Zone 2 tests.")
        if (e.eligible.isEmpty()) Body("Your trend appears here once you log a run with average heart rate.", p.muted) else PaceChart(e)
    }

    Panel {
        Head("Weekly running time, minutes", 18)
        Legend(p.easy to "Easy (Zone 1–2)", p.hard to "Zone 3 and above")
        WeekChart(e)
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
private fun PaceChart(e: Engine) {
    val p = LocalPal.current
    Canvas(Modifier.fillMaxWidth().height(230.dp)) {
        val l = 42.dp.toPx(); val r = 14.dp.toPx(); val t = 16.dp.toPx(); val b = 26.dp.toPx()
        val tg = e.s.targetPace.toDouble()
        val pts = e.eligible
        var d0 = pts.first().first.date
        var d1 = maxOf(pts.last().first.date, e.today)
        if (ChronoUnit.DAYS.between(d0, d1) < 14) { d0 = d0.minusDays(3); d1 = d0.plusDays(17) }
        val days = ChronoUnit.DAYS.between(d0, d1).toFloat()
        val vmin = min(tg, pts.minOf { it.second }); val vmax = pts.maxOf { it.second }
        val step = (vmax - vmin).let { if (it > 240) 60 else if (it > 100) 30 else 15 }
        val lo = floor((vmin - 8) / step) * step; val hi = ceil((vmax + 8) / step) * step
        fun x(d: LocalDate) = l + ChronoUnit.DAYS.between(d0, d) / days * (size.width - l - r)
        fun y(v: Double) = t + ((v - lo) / (hi - lo)).toFloat() * (size.height - t - b)   // faster pace sits higher
        var v = lo
        while (v <= hi + .1) {
            drawLine(p.line, Offset(l, y(v)), Offset(size.width - r, y(v)), 1.dp.toPx())
            label(fmtPace(v), l - 8.dp.toPx(), y(v) + 4.dp.toPx(), p.muted, Paint.Align.RIGHT); v += step
        }
        label(fmtDay(d0), l, size.height - 6.dp.toPx(), p.muted, Paint.Align.LEFT)
        label(fmtDay(d1), size.width - r, size.height - 6.dp.toPx(), p.muted, Paint.Align.RIGHT)
        drawLine(p.ink, Offset(l, y(tg)), Offset(size.width - r, y(tg)), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 11f)))
        label("Target ${fmtPace(tg)}", size.width - r, y(tg) + 15.dp.toPx(), p.ink, Paint.Align.RIGHT, bold = true)
        if (pts.size > 1) {
            val path = Path()
            pts.forEachIndexed { i, (run, est) -> if (i == 0) path.moveTo(x(run.date), y(est)) else path.lineTo(x(run.date), y(est)) }
            drawPath(path, p.accent, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
        }
        for ((run, est) in pts) {
            val c = Offset(x(run.date), y(est))
            if (run.type == "test") { drawCircle(p.surface, 6.dp.toPx(), c); drawCircle(p.accent, 6.dp.toPx(), c, style = Stroke(3.dp.toPx())) }
            else { drawCircle(p.surface, 6.dp.toPx(), c); drawCircle(p.accent, 4.dp.toPx(), c) }
        }
        val last = pts.last()
        label(fmtPace(last.second), min(size.width - r, x(last.first.date) + 2.dp.toPx()), y(last.second) - 12.dp.toPx(), p.ink, Paint.Align.RIGHT, bold = true)
    }
}

@Composable
private fun WeekChart(e: Engine) {
    val p = LocalPal.current
    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
        val l = 36.dp.toPx(); val r = 8.dp.toPx(); val t = 12.dp.toPx(); val b = 26.dp.toPx()
        val base = mondayOf(e.today)
        val weeks = (7 downTo 0).map { base.minusWeeks(it.toLong()) }.map { it to e.span(it, it.plusDays(7)) }
        val mx = max(60.0, weeks.maxOf { it.second.easy + it.second.hard })
        val step = if (mx > 300) 120 else if (mx > 150) 60 else 30
        val top = ceil(mx / step) * step
        fun y(v: Double) = size.height - b - (v / top).toFloat() * (size.height - t - b)
        val slot = (size.width - l - r) / 8
        val bw = min(28.dp.toPx(), slot - 8.dp.toPx())
        var v = 0.0
        while (v <= top + .1) { drawLine(p.line, Offset(l, y(v)), Offset(size.width - r, y(v)), 1.dp.toPx()); label("${v.toInt()}", l - 8.dp.toPx(), y(v) + 4.dp.toPx(), p.muted, Paint.Align.RIGHT); v += step }
        val gap = 2.dp.toPx(); val rad = CornerRadius(4.dp.toPx())
        weeks.forEachIndexed { i, (mon, s) ->
            val cx = l + (i + .5f) * slot - bw / 2
            val ye = y(s.easy); val yt = y(s.easy + s.hard)
            if (s.easy > 0) drawRoundRect(p.easy, Offset(cx, ye), Size(bw, y(0.0) - ye), if (s.hard > 0) CornerRadius.Zero else rad)
            if (s.hard > 0) { val bottom = ye - if (s.easy > 0) gap else 0f; if (bottom > yt) drawRoundRect(p.hard, Offset(cx, yt), Size(bw, bottom - yt), rad) }
            if (i % 2 == 1) label(fmtDay(mon), l + (i + .5f) * slot, size.height - 6.dp.toPx(), p.muted, Paint.Align.CENTER)
        }
    }
}
