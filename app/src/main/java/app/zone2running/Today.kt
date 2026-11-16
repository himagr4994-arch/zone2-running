package app.zone2running

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun TodayScreen(vm: AppVM, e: Engine, openPlan: () -> Unit, openSetup: () -> Unit, openForm: (FormSeed) -> Unit) {
    val p = LocalPal.current
    Hero(e)

    val mon = mondayOf(e.today)
    val wk = e.weekHit(mon)
    val two = e.span(e.today.minusDays(13), e.today.plusDays(1))
    val share = (two.easy + two.hard).takeIf { it > 0 }?.let { (two.easy / it * 100).roundToInt() }
    Panel {
        Label("This week")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Canvas(Modifier.size(104.dp)) {
                val w = 9.dp.toPx()
                fun ring(inset: Float, frac: Float, color: androidx.compose.ui.graphics.Color) {
                    val tl = Offset(inset, inset); val sz = Size(size.width - 2 * inset, size.height - 2 * inset)
                    drawArc(p.surface2, 0f, 360f, false, tl, sz, style = Stroke(w))
                    if (frac > 0f) drawArc(color, -90f, 360f * min(1f, frac), false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
                }
                ring(w / 2 + 2.dp.toPx(), if (wk.planned > 0) wk.n.toFloat() / wk.planned else 0f, p.accent)
                ring(w * 1.5f + 7.dp.toPx(), (share ?: 0) / 80f, p.gold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RingLine(p.accent, "${wk.n} of ${wk.planned}", "sessions",
                    if (!vm.settings.gameOn) "" else if (wk.hit) "Streak safe for this week" else (wk.need - wk.n).let { "$it more ${if (it == 1) "run keeps" else "runs keep"} your streak" })
                RingLine(p.gold, share?.let { "$it%" } ?: "–", "easy, last 14 days", "Fill the ring at 80%")
                RingLine(null, "${f1(wk.s.km)} km", "this week", "")
            }
        }
    }

    if (vm.settings.gameOn) {
        val c = e.challenge()
        Panel {
            Row { Label("${MONTHS_FULL[e.today.monthValue - 1]} challenge"); Spacer(Modifier.weight(1f)); Small("${c.daysLeft} day${if (c.daysLeft == 1) "" else "s"} left") }
            Head("${c.goal} easy minutes", 18)
            Meter(c.done.toFloat() / c.goal)
            Small(if (c.done >= c.goal) "Done. ${c.done} easy minutes this month." else "${c.done} done, ${c.goal - c.done} to go. Only time in Zone 1–2 counts.")
        }
    }

    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) { Label("Next session"); Spacer(Modifier.weight(1f)); Btn("Full plan", ghost = true, small = true, onClick = openPlan) }
        val nx = e.nextSession()
        if (nx == null) Body("Pick your running days in Setup to get a plan.", p.muted)
        else {
            val (d, s) = nx
            val gap = ChronoUnit.DAYS.between(e.today, d).toInt()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.width(62.dp)) {
                    Text("${s.min}", fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 38.sp, lineHeight = 38.sp, color = p.ink)
                    Label("min")
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Head(s.title, 18)
                    Small(when (gap) { 0 -> "Today"; 1 -> "Tomorrow"; else -> "${DOW[d.dayOfWeek.value - 1]} ${fmtDay(d)}" })
                    Text(s.detail, fontSize = 14.sp, lineHeight = 19.sp, color = p.ink, fontFamily = BodyFont)
                    if (s.type == "easy" || s.type == "long") e.easyRange()?.let { Small("Expect about ${fmtPace(it.first)}–${fmtPace(it.second)} /km. Go by heart rate, not this number.") }
                    Spacer(Modifier.height(2.dp))
                    Btn("Log this run", small = true) { openForm(FormSeed(date = d, type = s.type)) }
                }
            }
        }
    }

    if (vm.health.available() && !vm.healthGranted) Panel {
        Label("Skip the typing")
        Body("Connect Health Connect and runs recorded by your watch app are imported automatically, with distance, time and heart rate.")
        Btn("Set up in Setup", ghost = true, small = true, onClick = openSetup)
    }

    Panel { Label("Coach notes"); e.coach().forEach { NoteBox(it) } }

    Panel {
        Row { Label("Your heart-rate zones"); Spacer(Modifier.weight(1f)); Small(mapOf("hrr" to "Heart-rate reserve", "max" to "% of max HR", "custom" to "Custom")[e.s.zoneMethod] ?: "") }
        ZoneStrip(e)
        Small("Zone 2 is where the easy running happens. Match these to your watch in Setup so both agree.")
    }
}

@Composable
private fun RingLine(dot: androidx.compose.ui.graphics.Color?, big: String, rest: String, sub: String) {
    val p = LocalPal.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            if (dot != null) Dot(dot)
            Text(big, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, color = p.ink)
            Text(rest, fontSize = 14.5.sp, color = p.ink, fontFamily = BodyFont)
        }
        if (sub.isNotEmpty()) Small(sub)
    }
}

/** The pace ladder: where Zone 2 pace started, where it is now, and the target at the far end. */
@Composable
fun Hero(e: Engine) {
    val p = LocalPal.current
    val tg = e.s.targetPace.toDouble()
    val c = e.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.hero).padding(horizontal = 18.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (c == null) {
            Label("Zone 2 pace target", p.heroMuted)
            BigPace(fmtPace(tg))
            Text("Log a run with distance, time and average heart rate. The app converts it to your current Zone 2 pace and starts the ladder from there.",
                color = p.heroInk, fontSize = 14.5.sp, lineHeight = 20.sp, fontFamily = BodyFont)
            return@Column
        }
        val start = max(e.baseline ?: c, c)
        val gap = c - tg
        val frac = if (start > tg) ((start - c) / (start - tg)).coerceIn(0.0, 1.0).toFloat() else 1f
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("Zone 2 pace now, at ${e.refHr} bpm", p.heroMuted)
                BigPace(fmtPace(c))
            }
            Column(horizontalAlignment = Alignment.End) {
                Label("Target", p.heroMuted)
                Text(fmtPace(tg), fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 36.sp, color = p.heroInk)
                Text(if (gap > 0) "${fmtPace(gap)} to go" else "reached", fontSize = 13.sp, color = p.heroMuted, fontFamily = BodyFont)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Canvas(Modifier.fillMaxWidth().height(38.dp)) {
                val top = 14.dp.toPx(); val h = 10.dp.toPx(); val r = CornerRadius(h / 2)
                drawRoundRect(p.heroTrack, Offset(0f, top), Size(size.width, h), r)
                if (frac > 0f) drawRoundRect(p.easy, Offset(0f, top), Size(max(size.width * frac, h), h), r)
                if (start > tg) {
                    var v = kotlin.math.ceil(tg / 15) * 15
                    while (v < start) {
                        if (v > tg) { val x = ((start - v) / (start - tg)).toFloat() * size.width; drawRect(p.heroMuted.copy(alpha = .45f), Offset(x, 10.dp.toPx()), Size(1.dp.toPx(), 18.dp.toPx())) }
                        v += 15
                    }
                }
                val mw = 6.dp.toPx()
                drawRoundRect(p.heroMark, Offset((size.width * frac - mw / 2).coerceIn(0f, size.width - mw), 6.dp.toPx()), Size(mw, 26.dp.toPx()), CornerRadius(mw / 2))
            }
            Row {
                Text("Started ${fmtPace(start)}", Modifier.weight(1f), fontSize = 13.sp, color = p.heroMuted, fontFamily = BodyFont)
                Text("each tick 15 s", fontSize = 13.sp, color = p.heroMuted, fontFamily = BodyFont)
                Text(fmtPace(tg), Modifier.weight(1f), fontSize = 13.sp, color = p.heroMuted, fontFamily = BodyFont, textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
        val t = e.trend
        val line = when {
            gap <= 0 -> "You are holding your target pace at Zone 2 heart rate. Set a faster target in Setup."
            t != null && t.b < -0.02 -> {
                val days = ((tg - t.a) / t.b).roundToInt()
                if (days > 1100) "Your trend is nearly flat. More easy volume is the lever."
                else t.t0.plusDays(days.toLong()).let { "On your current trend you reach ${fmtPace(tg)} around ${MONTHS[it.monthValue - 1]} ${it.year}, improving ${(-t.b * 30).roundToInt()} s/km a month." }
            }
            else -> "Typical progress is 10–20 s/km a month, so ${fmtPace(tg)} is roughly ${max(1, (gap / 20).roundToInt())}–${max(2, (gap / 10).roundToInt())} months of steady training away. A personal projection appears after 4 steady runs across 3 weeks."
        }
        Text(line, color = p.heroMuted, fontSize = 14.5.sp, lineHeight = 20.sp, fontFamily = BodyFont)
    }
}

@Composable
private fun BigPace(t: String) {
    val p = LocalPal.current
    Row(verticalAlignment = Alignment.Bottom) {
        Text(t, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 84.sp, lineHeight = 76.sp, color = p.heroInk)
        Text(" /km", Modifier.padding(bottom = 6.dp), fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = p.heroMuted)
    }
}

@Composable
fun PlanScreen(e: Engine, openForm: (FormSeed) -> Unit) {
    val p = LocalPal.current
    var off by remember { mutableIntStateOf(0) }
    val base = mondayOf(e.today)
    val mon = base.plusWeeks(off.toLong())
    val plan = e.weekPlan(mon)
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Btn("←", ghost = true, small = true) { off-- }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Head("Week ${plan.wi + 1} · ${plan.phase}")
                Small("${fmtDay(mon)} – ${fmtDay(mon.plusDays(6))} · ${plan.total} min planned")
            }
            Btn("→", ghost = true, small = true) { off++ }
        }
        var rest = 0
        for (i in 0..6) {
            val d = mon.plusDays(i.toLong()); val s = plan.ses[d.dayOfWeek.value]; val rs = e.runsOn(d)
            val isToday = d == e.today
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.width(54.dp)) {
                    Text(DOW[i], fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = if (isToday) p.accent else p.ink)
                    Small(fmtDay(d) + if (isToday) "\ntoday" else "")
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (s != null) {
                        Text("${s.title} · ${s.min} min", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = p.ink, fontFamily = BodyFont)
                        Small(s.detail)
                    } else {
                        rest++
                        if (rest <= 2) { Body("Strength, 20 min", p.muted); Small("Squats, lunges, calf raises, planks.") } else Body("Rest", p.muted)
                    }
                    rs.firstOrNull()?.let { r -> Small("Logged: ${f2(r.distKm)} km at ${fmtPace(paceOf(r))}" + if (r.avgHr > 0) ", ${r.avgHr} bpm" else "", p.ink) }
                }
                when {
                    rs.isNotEmpty() -> Tag("Done", p.goodBg, p.goodInk)
                    s != null && d.isBefore(e.today) -> Tag("Missed", p.warnBg, p.warnInk)
                    s != null -> Btn("Log", ghost = true, small = true) { openForm(FormSeed(date = d, type = s.type)) }
                }
            }
        }
    }
    Panel {
        val plans = (0 until 12).map { e.weekPlan(base.plusWeeks(it.toLong())) }
        val mx = max(1, plans.maxOf { it.total })
        Row { Label("Next 12 weeks, planned minutes"); Spacer(Modifier.weight(1f)); if (off in 0..11) Small("Week ${plans[off].wi + 1}: ${plans[off].total} min") }
        Row(Modifier.height(56.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
            plans.forEachIndexed { i, w ->
                Box(Modifier.weight(1f).fillMaxHeight(max(.15f, w.total.toFloat() / mx)).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(if (i == off) p.accent else p.line.copy(alpha = if (w.pos == 3) .55f else 1f)).clickable { off = i })
            }
        }
        Small("Three weeks build by about 8% each, the fourth drops back so your body absorbs the work and you re-test. Tap a bar to open that week.")
    }
}

@Composable
fun Tag(t: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) =
    Text(t, Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 9.dp, vertical = 2.dp), color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = BodyFont)
