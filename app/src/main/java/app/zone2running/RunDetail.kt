package app.zone2running

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private fun mmss(sec: Int) = "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"

/**
 * The run as it happened: heart rate against the zones, with every block of the session shaded behind it,
 * and pace underneath on the same clock. Drag a finger across to read any moment.
 */
@Composable
fun RunCharts(e: Engine, r: Run, d: Debrief) {
    val p = LocalPal.current
    val tr = r.trace
    var at by remember(r.id) { mutableStateOf<Int?>(null) }
    val tick = haptic()
    val pick: (Float, Int) -> Unit = { x, w -> val i = (x / w * (tr.size - 1)).roundToInt().coerceIn(0, tr.size - 1); if (i != at) { at = i; tick(false) } }
    val gesture = Modifier
        .pointerInput(r.id) { detectTapGestures(onPress = { o -> pick(o.x, size.width); if (tryAwaitRelease()) at = null }) }
        .pointerInput(r.id) { detectHorizontalDragGestures(onDragStart = { o -> pick(o.x, size.width) }, onDragEnd = { at = null }, onDragCancel = { at = null }) { ch, _ -> ch.consume(); pick(ch.position.x, size.width) } }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("Heart rate through the run")
        // What the finger is on, or what to do.
        val i = at
        val hr = i?.let { tr[it] } ?: 0
        val block = i?.let { ix -> d.blocks.firstOrNull { ix * TRACE_STEP >= it.from && ix * TRACE_STEP < it.to } }
        Text(if (i == null) "Drag across the chart to read any moment." else listOfNotNull(mmss(i * TRACE_STEP), if (hr > 0) "$hr bpm · Zone ${e.zoneOf(hr)}" else "no reading",
            r.pace.getOrNull(i)?.takeIf { it > 0 }?.let { "${fmtPace(it.toDouble())} ${U.per}" }, block?.step?.label).joinToString("  ·  "),
            fontFamily = BodyFont, fontWeight = if (i == null) FontWeight.Normal else FontWeight.SemiBold, fontSize = 15.sp, color = if (i == null) p.muted else p.ink, maxLines = 1)

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface).glassEdge(RoundedCornerShape(16.dp)).padding(vertical = 12.dp).then(gesture)) {
            HeartCanvas(e, r, d, at, Modifier.fillMaxWidth().height(210.dp))
            if (r.pace.count { it > 0 } >= 12) {
                Text("PACE", Modifier.padding(start = 12.dp, top = 10.dp), fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, letterSpacing = 1.sp, color = p.muted)
                PaceCanvas(r, at, Modifier.fillMaxWidth().height(70.dp))
            }
            TimeAxis(r.durSec, Modifier.fillMaxWidth().height(18.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (z in 0..4) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(p.zones[z])); Small("Z${z + 1}")
            }
            if (d.blocks.size > 1) Small("· shaded columns are the session's blocks")
        }
    }
}

@Composable
private fun HeartCanvas(e: Engine, r: Run, d: Debrief, at: Int?, modifier: Modifier) {
    val p = LocalPal.current
    val measurer = rememberTextMeasurer()
    val draw = enter(900, 150)
    val tr = r.trace
    Canvas(modifier) {
        val vals = tr.filter { it > 0 }
        val lo = (vals.min() - 6).toFloat(); val hi = (vals.max() + 6).toFloat()
        val strip = 7.dp.toPx(); val h = size.height - strip - 4.dp.toPx()
        fun y(v: Float) = h * (1 - (v - lo) / (hi - lo))
        fun x(i: Int) = size.width * i / (tr.size - 1f)
        fun xs(sec: Int) = size.width * sec / ((tr.size - 1f) * TRACE_STEP)
        // Zones as bands, so "above the line" needs no explaining.
        for (z in 0..4) {
            val top = if (z == 4) hi else minOf(hi, e.zb[z + 1].toFloat()); val bot = maxOf(lo, if (z == 0) lo else e.zb[z].toFloat())
            if (top <= bot) continue
            drawRect(p.zones[z].copy(alpha = if (z == 1) .20f else .11f), Offset(0f, y(top)), Size(size.width, y(bot) - y(top)))
            if (y(bot) - y(top) > 16.dp.toPx()) drawText(measurer.measure("Z${z + 1}", TextStyle(color = p.muted, fontSize = 12.5.sp, fontFamily = BodyFont, fontWeight = FontWeight.SemiBold)), topLeft = Offset(8.dp.toPx(), y(top) + 3.dp.toPx()))
        }
        // The session's blocks: a tint behind the line, and a coloured strip along the bottom.
        d.blocks.forEachIndexed { i, b ->
            val x0 = xs(b.from); val x1 = xs(b.to)
            val tint = when (b.step.kind) { "hard", "race" -> p.zones[3].copy(alpha = .16f); "walk" -> p.zones[0].copy(alpha = .22f); else -> if (i % 2 == 0) Color.Transparent else p.ink.copy(alpha = .035f) }
            drawRect(tint, Offset(x0, 0f), Size(x1 - x0, h))
            drawRoundRect(if (b.step.kind == "talk") p.muted else p.zones[(b.step.zHi - 1).coerceIn(0, 4)], Offset(x0 + 1.dp.toPx(), size.height - strip), Size((x1 - x0 - 2.dp.toPx()).coerceAtLeast(1f), strip), CornerRadius(2.dp.toPx()))
        }
        // The line takes the colour of the zone it is in.
        val last = ((tr.size - 1) * draw).toInt()
        for (i in 1..last) {
            val a = tr[i - 1]; val b = tr[i]
            if (a <= 0 || b <= 0) continue
            drawLine(p.zones[e.zoneOf(b) - 1], Offset(x(i - 1), y(a.toFloat())), Offset(x(i), y(b.toFloat())), 2.6.dp.toPx(), cap = StrokeCap.Round)
        }
        at?.let { i ->
            drawLine(p.ink.copy(alpha = .55f), Offset(x(i), 0f), Offset(x(i), size.height), 1.dp.toPx())
            if (tr[i] > 0) { drawCircle(p.surface, 7.dp.toPx(), Offset(x(i), y(tr[i].toFloat()))); drawCircle(p.zones[e.zoneOf(tr[i]) - 1], 4.5.dp.toPx(), Offset(x(i), y(tr[i].toFloat()))) }
        }
    }
}

/** Pace on the same clock as the heart rate. Faster is higher. */
@Composable
private fun PaceCanvas(r: Run, at: Int?, modifier: Modifier) {
    val p = LocalPal.current
    Canvas(modifier) {
        val v = r.pace; val ok = v.filter { it > 0 }.sorted()
        val lo = ok[ok.size / 20].toFloat() - 10; val hi = ok[ok.size * 19 / 20].toFloat() + 10      // the odd GPS spike shouldn't flatten everything else
        fun y(s: Int) = size.height * ((s.toFloat().coerceIn(lo, hi) - lo) / (hi - lo))
        fun x(i: Int) = size.width * i / (v.size - 1f)
        val path = Path(); var pen = false
        v.forEachIndexed { i, s -> if (s <= 0) pen = false else { if (pen) path.lineTo(x(i), y(s)) else path.moveTo(x(i), y(s)); pen = true } }
        drawPath(path, p.ink.copy(alpha = .7f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        at?.let { i -> drawLine(p.ink.copy(alpha = .55f), Offset(x(i.coerceAtMost(v.size - 1)), 0f), Offset(x(i.coerceAtMost(v.size - 1)), size.height), 1.dp.toPx()) }
    }
}

@Composable
private fun TimeAxis(durSec: Int, modifier: Modifier) {
    val p = LocalPal.current
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val every = when { durSec <= 1500 -> 300; durSec <= 4200 -> 600; else -> 1200 }
        var t = every
        while (t < durSec - every / 3) {
            val txt = measurer.measure("${t / 60}′", TextStyle(color = p.muted, fontSize = 12.5.sp, fontFamily = BodyFont))
            drawText(txt, topLeft = Offset(size.width * t / durSec - txt.size.width / 2f, 3.dp.toPx())); t += every
        }
    }
}

/**
 * One row per block. Repeats are paired with the recovery that follows them, because the pair is the story:
 * how high the effort took the heart rate and how far the break brought it down.
 */
@Composable
fun BlockRows(e: Engine, blocks: List<BlockStat>) {
    val p = LocalPal.current
    val lo = blocks.minOf { it.low }.toFloat() - 4; val hi = blocks.maxOf { it.peak }.toFloat() + 2
    var i = 0
    // "Jog" eight times over says nothing: number the blocks that share a name.
    val seen = HashMap<String, Int>()
    val names = blocks.map { b -> val n = b.step.label.substringBefore(". "); if (blocks.count { it.step.label.substringBefore(". ") == n } > 1) "$n ${seen.merge(n, 1, Int::plus)}" else n }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface).glassEdge(RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 6.dp)) {
        var first = true
        while (i < blocks.size) {
            val b = blocks[i]; val nx = blocks.getOrNull(i + 1)
            val pair = nx != null && ((b.step.kind == "hard" && nx.step.kind == "easy") || (b.step.kind == "easy" && nx.step.kind == "walk"))
            if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
            first = false
            Column(Modifier.padding(vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (b.step.kind == "talk") p.muted else p.zones[(b.step.zHi - 1).coerceIn(0, 4)]))
                    Text(names[i], Modifier.weight(1f), fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = p.ink, maxLines = 1)
                    Small(mmss(b.to - b.from))
                    Text("${b.avg}", fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = p.ink)
                    Small("avg")
                }
                // Where the heart rate ranged in this block, drawn on one shared scale so rows can be compared.
                Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                    fun x(v: Int) = size.width * ((v - lo) / (hi - lo)).coerceIn(0f, 1f)
                    drawRoundRect(p.surface2, Offset(0f, 2.dp.toPx()), Size(size.width, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
                    val cap = x(e.zb[2]); drawLine(p.muted, Offset(cap, 0f), Offset(cap, size.height), 1.5.dp.toPx())
                    drawRoundRect(p.zones[e.zoneOf(b.avg) - 1], Offset(x(b.low), 2.dp.toPx()), Size((x(b.peak) - x(b.low)).coerceAtLeast(4f), 6.dp.toPx()), CornerRadius(3.dp.toPx()))
                    drawCircle(p.ink, 3.5.dp.toPx(), Offset(x(b.avg), size.height / 2))
                }
                Small(listOfNotNull("${b.low}–${b.peak} bpm", if (b.step.kind == "hard") "${b.inTarget}% on target" else null,
                    if (pair) "${nx!!.step.label.substringBefore(". ").lowercase()} ${mmss(nx.to - nx.from)} brought it down ${(b.endHr - nx.low).coerceAtLeast(0)} bpm to ${nx.low}" else null).joinToString("  ·  "))
            }
            i += if (pair) 2 else 1
        }
    }
}

/** Minutes in each zone as five small bars. */
@Composable
fun ZoneRows(e: Engine, zoneSec: List<Int>) {
    val p = LocalPal.current
    val top = zoneSec.max().coerceAtLeast(1)
    val names = listOf("Recovery", "Easy", "Steady", "Hard", "Max")
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for (z in 0..4) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Z${z + 1} ${names[z]}", Modifier.width(92.dp), fontFamily = BodyFont, fontSize = 13.5.sp, color = p.ink)
            Box(Modifier.weight(1f)) { Box(Modifier.fillMaxWidth((zoneSec[z].toFloat() / top).coerceAtLeast(.012f)).height(10.dp).clip(RoundedCornerShape(5.dp)).background(p.zones[z])) }
            Text(mmss(zoneSec[z]), Modifier.width(48.dp), fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = p.ink)
            Text("${zoneSec[z] * 100 / zoneSec.sum()}%", Modifier.width(34.dp), fontFamily = BodyFont, fontSize = 13.sp, color = p.muted)
        }
    }
}

@Composable
fun FactTile(f: Fact, modifier: Modifier) {
    val p = LocalPal.current
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(p.surface).glassEdge(RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(f.label.uppercase(), fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, letterSpacing = 1.sp, color = p.muted, maxLines = 1)
        Text(f.value, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 28.sp, color = p.ink, maxLines = 1)
        if (f.note.isNotEmpty()) Text(f.note, fontFamily = BodyFont, fontSize = 13.sp, lineHeight = 17.sp, color = p.muted, maxLines = 2)
    }
}
