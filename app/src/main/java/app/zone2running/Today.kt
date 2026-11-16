package app.zone2running

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun TodayScreen(vm: AppVM, e: Engine, openPlan: () -> Unit, openSetup: () -> Unit, openLesson: (Lesson) -> Unit, openForm: (FormSeed) -> Unit) {
    val p = LocalPal.current
    val game = vm.settings.gameOn
    val lead = remember(e, vm.clock) { e.lead(vm.clock) }
    val nextLesson = LESSONS.firstOrNull { it.id !in vm.settings.lessonsDone }
    var k = 0

    // 1. The lead: one answer for this moment of the day.
    Column(Modifier.rise(k++).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surface).border(1.dp, p.line, RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Dil(lead.mood, Modifier.size(84.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label(lead.eyebrow)
                Text(lead.title, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 29.sp, color = p.ink)
            }
        }
        Text(lead.body, fontFamily = BodyFont, fontSize = 15.sp, lineHeight = 21.sp, color = p.ink)
        lead.session?.takeIf { it.type == "easy" || it.type == "long" }?.let { e.easyRange() }?.let { Small("Heart rate cap ${e.zb[2] - 1} bpm. Expect about ${fmtPace(it.first)}–${fmtPace(it.second)} /km, but go by heart rate.") }
        lead.run?.let { RunFacts(it, e) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (lead.cta.isNotEmpty()) Btn(lead.cta) { openForm(FormSeed(date = lead.date ?: e.today, type = lead.session?.type ?: "easy")) }
            if (lead.kind == "rest" && nextLesson != null) Btn("2-minute lesson", ghost = lead.cta.isNotEmpty()) { openLesson(nextLesson) }
            if (lead.kind == "plan" || lead.kind == "rest" || lead.kind == "done") Btn("Full plan", ghost = true, onClick = openPlan)
        }
    }

    val mon = mondayOf(e.today)
    val heroCard: @Composable () -> Unit = { Box(Modifier.rise(k++)) { Hero(e) } }
    val weekCard: @Composable () -> Unit = { WeekCard(e, game, Modifier.rise(k++)) }
    // After a run, the week is the next thing worth seeing; otherwise the pace is.
    if (lead.kind == "done" || lead.kind == "risk") { weekCard(); heroCard() } else { heroCard(); weekCard() }

    // The last run, when it isn't already the lead.
    e.runs.lastOrNull()?.takeIf { lead.run == null }?.let { r ->
        Panel(Modifier.rise(k++)) {
            Row { Label("Last run"); Spacer(Modifier.weight(1f)); Small("${DOW[r.date.dayOfWeek.value - 1]} ${fmtDay(r.date)}") }
            RunFacts(r, e)
        }
    }

    val rv = e.review()
    if (rv != null && e.today.dayOfWeek.value <= 2 && lead.kind != "review") Panel(Modifier.rise(k++)) {
        Label("Last week in review")
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Stat("${rv.n}/${rv.planned}", "runs"); Stat(f1(rv.km), "km"); Stat(rv.easyShare?.let { "$it%" } ?: "–", "easy")
        }
        NoteBox(Note(if (rv.state == WeekState.HIT) "good" else "plain", rv.line))
    }

    if (nextLesson != null && lead.kind != "rest") Panel(Modifier.rise(k++)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Label("Two-minute lesson" + if (game) " · 40 XP" else "")
                Head(nextLesson.title, 18)
            }
            Btn("Start", ghost = true, small = true) { openLesson(nextLesson) }
        }
    }

    if (vm.health.available() && !vm.healthGranted) Panel {
        Label("Skip the typing")
        Body("Connect Health Connect and runs recorded by your watch app are imported automatically, with distance, time and heart rate.")
        Btn("Set up in Setup", ghost = true, small = true, onClick = openSetup)
    }

    val notes = e.coach().take(2)
    if (notes.isNotEmpty() && e.runs.isNotEmpty()) Panel { Label("Coach notes"); notes.forEach { NoteBox(it) } }
}

/** A run in three numbers, plus where the heart rate actually sat. */
@Composable
fun RunFacts(r: Run, e: Engine) {
    val p = LocalPal.current
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Stat(f2(r.distKm), "km"); Stat(fmtDur(r.durSec), "time"); Stat(fmtPace(paceOf(r)), "/km"); if (r.avgHr > 0) Stat("${r.avgHr}", "bpm")
    }
    val tot = r.zoneSec.sum()
    if (r.zoneSec.size == 5 && tot > 0) {
        val grow = enter(700, 200)
        Row(Modifier.fillMaxWidth(grow.coerceAtLeast(.02f)).height(14.dp).clip(RoundedCornerShape(7.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            r.zoneSec.forEachIndexed { i, sec -> if (sec > 0) Box(Modifier.weight(sec.toFloat() / tot).fillMaxHeight().background(p.zones[i])) }
        }
        val easy = r.zoneSec[0] + r.zoneSec[1]
        Small("${easy / 60} of ${tot / 60} minutes in Zone 2 or below (${(easy * 100f / tot).roundToInt()}%).", p.ink)
    } else if (r.avgHr > 0) {
        val z = e.zoneOf(r.avgHr)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Dot(p.zones[z - 1]); Small("Averaged Zone $z. Import runs through Health Connect to see minutes in each zone.", p.ink)
        }
    }
    e.est(r)?.let { Small("Zone 2 estimate from this run: ${fmtPace(it)} /km") }
}

@Composable
private fun WeekCard(e: Engine, game: Boolean, modifier: Modifier) {
    val p = LocalPal.current
    val mon = mondayOf(e.today)
    val wk = e.weekHit(mon)
    val two = e.span(e.today.minusDays(13), e.today.plusDays(1))
    val share = (two.easy + two.hard).takeIf { it > 0 }?.let { (two.easy / it * 100).roundToInt() }
    Panel(modifier) {
        Label("This week")
        val grow = enter(1000, 250)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Canvas(Modifier.size(104.dp)) {
                val w = 9.dp.toPx()
                fun ring(inset: Float, frac: Float, color: Color) {
                    val tl = Offset(inset, inset); val sz = Size(size.width - 2 * inset, size.height - 2 * inset)
                    drawArc(p.surface2, 0f, 360f, false, tl, sz, style = Stroke(w))
                    if (frac > 0f) drawArc(color, -90f, 360f * min(1f, frac) * grow, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
                }
                ring(w / 2 + 2.dp.toPx(), if (wk.planned > 0) wk.n.toFloat() / wk.planned else 0f, p.accent)
                ring(w * 1.5f + 7.dp.toPx(), (share ?: 0) / 80f, p.gold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RingLine(p.accent, "${wk.n} of ${wk.planned}", "sessions",
                    if (!game) "" else if (wk.hit) "Streak safe for this week" else (wk.need - wk.n).let { "$it more ${if (it == 1) "run keeps" else "runs keep"} your streak" })
                RingLine(p.gold, share?.let { "$it%" } ?: "–", "easy, last 14 days", "Fill the ring at 80%")
                val mins = (wk.s.easy + wk.s.hard).roundToInt()
                RingLine(null, "$mins min", "so far", e.usualWeekMinutes()?.let { "Your usual week is $it min" } ?: "${f1(wk.s.km)} km")
            }
        }
        if (game) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
            Row { Label("Weekly quests"); Spacer(Modifier.weight(1f)); Small("50 XP each") }
            e.quests(mon).forEach { q -> QuestRow(q) }
        }
    }
}

@Composable
private fun Stat(big: String, label: String) {
    Column { Text(big, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, color = LocalPal.current.ink); Small(label) }
}

@Composable
private fun QuestRow(q: Quest) {
    val p = LocalPal.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(Modifier.size(22.dp)) {
            if (q.done) {
                drawCircle(p.accent); val s = size.width
                drawLine(p.accentInk, Offset(s * .28f, s * .52f), Offset(s * .44f, s * .68f), s * .11f, StrokeCap.Round)
                drawLine(p.accentInk, Offset(s * .44f, s * .68f), Offset(s * .74f, s * .34f), s * .11f, StrokeCap.Round)
            } else drawCircle(p.line, size.width / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row { Text(q.title, Modifier.weight(1f), fontSize = 14.5.sp, color = if (q.done) p.muted else p.ink, fontFamily = BodyFont); Small("${q.have}/${q.need}") }
            Meter(q.have.toFloat() / q.need * enter(800, 350), Modifier.height(5.dp))
        }
    }
}

@Composable
private fun RingLine(dot: Color?, big: String, rest: String, sub: String) {
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
        val go = enter(1100, 150)                                  // the marker travels from the start to today's pace
        val full = if (start > tg) ((start - c) / (start - tg)).coerceIn(0.0, 1.0).toFloat() else 1f
        val frac = full * go
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("Zone 2 pace now, at ${e.refHr} bpm", p.heroMuted)
                BigPace(fmtPace(start + (c - start) * go))
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
                Text(fmtPace(tg), Modifier.weight(1f), fontSize = 13.sp, color = p.heroMuted, fontFamily = BodyFont, textAlign = TextAlign.End)
            }
        }
        val ch = e.paceChange(); val conf = e.confidence()
        val line = when {
            gap <= 0 -> "You're holding your target at Zone 2 heart rate. Set a faster one in Setup."
            ch == null -> "Log a few more runs to see how your pace is moving."
            ch.delta >= 3 -> "${ch.delta.roundToInt()} seconds faster than ${ch.since}."
            ch.delta <= -3 -> "${(-ch.delta).roundToInt()} seconds slower than ${ch.since}. Heat, tiredness or a hard run can do that."
            else -> "Level with ${ch.since}."
        }
        Text(line, color = p.heroInk, fontSize = 16.sp, lineHeight = 22.sp, fontFamily = BodyFont, fontWeight = FontWeight.Medium)
        if (conf != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { for (i in 0..2) Box(Modifier.size(8.dp).clip(CircleShape).background(if (i <= conf.level) p.heroMark else p.heroTrack)) }
            Text("${conf.label}. ${conf.why}", color = p.heroMuted, fontSize = 13.sp, lineHeight = 18.sp, fontFamily = BodyFont)
        }
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
fun PlanScreen(vm: AppVM, e: Engine, openForm: (FormSeed) -> Unit) {
    val p = LocalPal.current
    var off by remember { mutableIntStateOf(0) }
    val base = mondayOf(e.today)
    val mon = base.plusWeeks(off.toLong())
    val plan = e.weekPlan(mon)
    val onBreak = e.onBreak(mon)
    Panel(Modifier.rise(0)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Btn("←", ghost = true, small = true) { off-- }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Head("Step ${plan.wi + 1} · ${plan.phase}")
                Small("${fmtDay(mon)} – ${fmtDay(mon.plusDays(6))} · ${plan.total} min planned")
            }
            Btn("→", ghost = true, small = true) { off++ }
        }
        if (onBreak) NoteBox(Note("good", "Week off. Your streak is safe and the plan waits for you."))
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
                        Text("${s.title} · ${s.min} min", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = if (onBreak) p.muted else p.ink, fontFamily = BodyFont)
                        Small(s.detail)
                    } else {
                        rest++
                        if (rest <= 2) { Body("Strength, 20 min", p.muted); Small("Squats, lunges, calf raises, planks.") } else Body("Rest", p.muted)
                    }
                    rs.firstOrNull()?.let { r -> Small("Logged: ${f2(r.distKm)} km at ${fmtPace(paceOf(r))}" + if (r.avgHr > 0) ", ${r.avgHr} bpm" else "", p.ink) }
                }
                when {
                    rs.isNotEmpty() -> Tag("Done", p.goodBg, p.goodInk)
                    onBreak -> Unit
                    s != null && d.isBefore(e.today) -> Tag("Missed", p.warnBg, p.warnInk)
                    s != null -> Btn("Log", ghost = true, small = true) { openForm(FormSeed(date = d, type = s.type)) }
                }
            }
        }
        if (off >= 0) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Small(if (onBreak) "Feeling better? Bring the week back." else "Sick, travelling or worn out? Take the week off without losing your streak.", modifier = Modifier.weight(1f))
                Btn(if (onBreak) "I'm back" else "Take week off", ghost = true, small = true) { vm.toggleBreak(mon) }
            }
        }
    }

    // How the last eight weeks went, oldest on the left.
    val recent = e.weeks.takeLast(8)
    if (e.runs.isNotEmpty()) Panel(Modifier.rise(1)) {
        Label("Recent weeks")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            recent.forEach { w ->
                val (bg, mark) = when (w.state) {
                    WeekState.HIT -> p.accent to "✓"; WeekState.FROZEN -> p.zones[0] to "❄"; WeekState.BREAK -> p.surface2 to "z"
                    WeekState.MISS -> p.warnBg to "–"; WeekState.OPEN -> p.surface2 to "·"
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(bg), contentAlignment = Alignment.Center) {
                        Text(mark, color = if (w.state == WeekState.HIT || w.state == WeekState.FROZEN) Color.White else p.muted, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Text(fmtDay(w.mon).substringBefore(" "), fontSize = 11.sp, color = p.muted, fontFamily = BodyFont)
                }
            }
            repeat(8 - recent.size) { Spacer(Modifier.weight(1f)) }
        }
        Small("✓ goal hit · ❄ covered by a rest pass · z week off · – missed. The plan moves to the next step only after a completed week, so you never get pushed ahead of your training.")
    }

    Panel(Modifier.rise(2)) {
        val plans = (0 until 12).map { e.weekPlan(base.plusWeeks(it.toLong())) }
        val mx = max(1, plans.maxOf { it.total })
        val grow = enter(700, 200)
        Row { Label("Next 12 weeks, planned minutes"); Spacer(Modifier.weight(1f)); if (off in 0..11) Small("${plans[off].total} min") }
        Row(Modifier.height(56.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
            plans.forEachIndexed { i, w ->
                Box(Modifier.weight(1f).fillMaxHeight(max(.15f, w.total.toFloat() / mx * grow)).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(if (i == off) p.accent else p.line.copy(alpha = if (w.pos == 3) .55f else 1f)).clickable { off = i })
            }
        }
        Small("Three steps build by about 8% each, the fourth drops back so your body absorbs the work and you re-test. Tap a bar to open that week.")
    }
}

@Composable
fun Tag(t: String, bg: Color, fg: Color) =
    Text(t, Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 9.dp, vertical = 2.dp), color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = BodyFont)
