package app.zone2running

import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The one main action on a screen: full width, tall, with an optional icon. */
@Composable
fun BigBtn(text: String, glyph: String? = null, ghost: Boolean = false, onClick: () -> Unit) {
    val p = LocalPal.current
    val tick = haptic()
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(if (ghost) Color.Transparent else p.accent).then(if (ghost) Modifier.border(1.dp, p.line, shape) else Modifier)
        .clickable { tick(false); onClick() }.padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (glyph != null) { Glyph(glyph, if (ghost) p.ink else p.accentInk, Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)) }
        Text(text, color = if (ghost) p.ink else p.accentInk, fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1)
    }
}

@Composable
fun TextLink(text: String, onClick: () -> Unit) {
    val tick = haptic()
    Text(text, Modifier.clip(RoundedCornerShape(8.dp)).clickable { tick(false); onClick() }.padding(horizontal = 10.dp, vertical = 8.dp),
        color = LocalPal.current.accent, fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
}

/** Home: Dil's one line, the pace, one button. Everything else is a row underneath that opens when tapped. */
@Composable
fun TodayScreen(vm: AppVM, e: Engine, openPlan: () -> Unit, openSetup: () -> Unit, openLesson: (Lesson) -> Unit, openLive: (Session) -> Unit, openRun: (Run) -> Unit, openForm: (FormSeed) -> Unit) {
    val p = LocalPal.current
    val game = vm.settings.gameOn
    val lead = remember(e, vm.clock) { e.lead(vm.clock) }
    val nextLesson = LESSONS.firstOrNull { it.id !in vm.settings.lessonsDone }
    var details by rememberSaveable { mutableStateOf(false) }
    val c = e.current; val seen = vm.settings.seenPace
    // Only a change bigger than the usual wobble between runs gets the celebration.
    val improved = c != null && seen > 0 && c < seen - 3 && e.paceChange()?.let { it.real && it.delta > 0 } == true

    Row(Modifier.rise(0), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Dil(if (improved) "proud" else lead.mood, Modifier.size(84.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label(lead.eyebrow)
            Text(lead.title, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 29.sp, color = p.ink)
        }
    }

    Box(Modifier.rise(1)) { PaceHero(vm, e, improved) }

    Column(Modifier.rise(2), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val hasAction = lead.cta.isNotEmpty()
        when {
            hasAction -> {
                BigBtn("Start run", "play") { openLive(lead.session ?: Session("easy", "Easy run", 20, "Keep HR ${e.zb[1]}–${e.zb[2] - 1} bpm. You should be able to speak full sentences.")) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextLink("Log it instead") { openForm(FormSeed(date = lead.date ?: e.today, type = lead.session?.type ?: "easy")) }
                    TextLink(if (details) "Hide details" else "Details") { details = !details }
                    TextLink("Free run") { openLive(e.freeRun()) }
                }
            }
            // Nothing planned today: a free run is the obvious thing to offer.
            lead.kind == "rest" -> BigBtn("Start a free run", "play") { openLive(e.freeRun()) }
            else -> {
                BigBtn("See plan", "calendar", ghost = true, onClick = openPlan)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { TextLink("Start a free run") { openLive(e.freeRun()) } }
            }
        }
        AnimatedVisibility(details || !hasAction) {
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(lead.body, fontFamily = BodyFont, fontSize = 15.sp, lineHeight = 21.sp, color = p.ink)
                lead.run?.let { r -> EffortRow(e.runs.firstOrNull { it.id == r.id }?.feel ?: r.feel) { vm.setFeel(r.id, it) } }
                if (lead.session?.type in setOf("intervals", "tempo", "race")) Small(STOP_SIGNS)
                lead.session?.takeIf { it.type == "easy" || it.type == "long" }?.let { e.easyRange() }?.let {
                    val slow = 1 + lead.heatPct / 100
                    Small("Cap ${e.zb[2] - 1} bpm. Expect about ${fmtPace(it.first * slow)}–${fmtPace(it.second * slow)} ${U.per}.")
                }
            }
        }
    }

    // Offered, never applied without a yes.
    e.suggestions().firstOrNull()?.let { sg ->
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok -> if (ok) vm.accept(sg) else vm.snooze(sg) }
        Panel(Modifier.rise(3)) {
            Label("Dil noticed")
            Head(sg.title, 20)
            Small(sg.body)
            if (sg.act == EnterTalk) TalkEntry(e) { vm.setTalk(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sg.act != EnterTalk) Btn(sg.yes, small = true) { if (sg.act is Remind && android.os.Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(ctx)) ask.launch(android.Manifest.permission.POST_NOTIFICATIONS) else vm.accept(sg) }
                Btn("Not now", ghost = true, small = true) { vm.snooze(sg) }
            }
        }
    }

    Column(Modifier.rise(4)) {
        val mon = mondayOf(e.today)
        val wk = e.weekHit(mon)
        val two = e.span(e.today.minusDays(13), e.today.plusDays(1))
        val share = (two.easy + two.hard).takeIf { it > 0 }?.let { (two.easy / it * 100).roundToInt() }
        WeekDots(e, mon)
        Fold("calendar", "This week", "${wk.n} of ${wk.planned} runs" + (share?.let { " · $it% easy" } ?: "") + " · ${(wk.s.easy + wk.s.hard).roundToInt()} min") { WeekDetail(e, game, wk, share) }

        vm.update?.let { (version, url) ->
            val ctx = androidx.compose.ui.platform.LocalContext.current
            Link("plus", "Version $version is out", "Installing it keeps your data") { try { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) } catch (x: Exception) { } }
        }
        e.raceDaysLeft?.let { left ->
            val cast = e.raceCast()
            val ease = "The plan eases off over the last ${listOf("week", "two weeks", "three weeks")[e.taperWeeks - 1]}."
            Fold("flag", "${e.s.raceName.ifBlank { "Your race" }} · ${U.dist(e.s.raceKm, 1)}", when (left) { 0 -> "Today"; 1 -> "Tomorrow"; else -> "In $left days" } + (cast?.let { " · ${fmtDur(it.lo)}–${fmtDur(it.hi)}" } ?: "")) {
                Small(if (cast != null) "Likely between ${fmtDur(cast.lo)} and ${fmtDur(cast.hi)}, going by ${cast.basis}. $ease" else "Run one hard effort of ${U.dist(3.0, 0)} or more and a forecast appears. $ease")
                cast?.warn?.takeIf { it.isNotEmpty() }?.let { NoteBox(Note("warn", it)) }
            }
        }
        e.runs.lastOrNull()?.let { r ->
            val easyShare = r.zoneSec.takeIf { it.size == 5 && it.sum() > 0 }?.let { " · ${((it[0] + it[1]) * 100f / it.sum()).roundToInt()}% in Zone 2" } ?: ""
            Link("road", if (r.date == e.today) "Today's run" else "Last run · ${DOW[r.date.dayOfWeek.value - 1]} ${fmtDay(r.date)}", "${U.dist(r.distKm)} · ${fmtPace(paceOf(r))} ${U.per}$easyShare") { openRun(r) }
        }
        e.review()?.takeIf { e.today.dayOfWeek.value <= 2 }?.let { rv ->
            Fold("chart", "Last week", "${rv.n} of ${rv.planned} runs · ${U.dist(rv.km, 1)}" + (rv.easyShare?.let { " · $it% easy" } ?: "")) { NoteBox(Note(if (rv.state == WeekState.HIT) "good" else "plain", rv.line)) }
        }
        if (nextLesson != null) Link("book", "Two-minute lesson", nextLesson.title) { openLesson(nextLesson) }
        val notes = e.coach()
        if (notes.isNotEmpty() && e.runs.isNotEmpty()) Fold("bolt", "Coach notes", notes.first().text) { notes.forEach { NoteBox(it) } }
        if (vm.health.available() && !vm.healthGranted) Link("plus", "Import runs automatically", "Connect Health Connect in Settings", openSetup)
    }
}

/** Seven days at a glance: done, still to come, missed, rest. */
@Composable
private fun WeekDots(e: Engine, mon: LocalDate) {
    val p = LocalPal.current
    val plan = e.weekPlan(mon)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        for (i in 0..6) {
            val d = mon.plusDays(i.toLong()); val ses = plan.ses[i + 1]; val ran = e.runsOn(d).isNotEmpty(); val today = d == e.today
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(DOW[i].take(1), fontSize = 12.sp, fontFamily = BodyFont, fontWeight = if (today) FontWeight.SemiBold else FontWeight.Normal, color = if (today) p.ink else p.muted)
                Box(Modifier.size(30.dp).clip(CircleShape).background(if (ran) p.accent else Color.Transparent)
                    .then(when { ran -> Modifier; ses != null && d.isBefore(e.today) -> Modifier.border(2.dp, p.warnInk.copy(alpha = .5f), CircleShape); ses != null -> Modifier.border(2.dp, if (today) p.ink else p.line, CircleShape); else -> Modifier }),
                    contentAlignment = Alignment.Center) {
                    when { ran -> Glyph("check", p.accentInk, Modifier.size(16.dp)); ses == null -> Glyph("dot", p.line, Modifier.size(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun WeekDetail(e: Engine, game: Boolean, wk: WeekHit, share: Int?) {
    val p = LocalPal.current
    val grow = enter(900, 100)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Canvas(Modifier.size(92.dp)) {
            val w = 9.dp.toPx()
            fun ring(inset: Float, frac: Float, color: Color) {
                val tl = Offset(inset, inset); val sz = Size(size.width - 2 * inset, size.height - 2 * inset)
                drawArc(p.surface2, 0f, 360f, false, tl, sz, style = Stroke(w))
                if (frac > 0f) drawArc(color, -90f, 360f * min(1f, frac) * grow, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
            }
            ring(w / 2 + 2.dp.toPx(), if (wk.planned > 0) wk.n.toFloat() / wk.planned else 0f, p.accent)
            ring(w * 1.5f + 7.dp.toPx(), (share ?: 0) / 80f, p.gold)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RingLine(p.accent, "${wk.n} of ${wk.planned}", "runs", if (!game) "" else if (wk.hit) "Streak safe" else (wk.need - wk.n).let { "$it more for your streak" })
            RingLine(p.gold, share?.let { "$it%" } ?: "–", "easy, 14 days", "Aim for 80%")
            e.usualWeekMinutes()?.let { Small("Your usual week: $it min") }
        }
    }
    if (game) e.quests(mondayOf(e.today)).forEach { q -> QuestRow(q) }
}

@Composable
fun Stat(big: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) { Text(big, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, color = LocalPal.current.ink); Small(label) }
}

@Composable
private fun QuestRow(q: Quest) {
    val p = LocalPal.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(if (q.done) p.accent else Color.Transparent).then(if (q.done) Modifier else Modifier.border(2.dp, p.line, CircleShape)), contentAlignment = Alignment.Center) {
            if (q.done) Glyph("check", p.accentInk, Modifier.size(13.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row { Text(q.title, Modifier.weight(1f), fontSize = 14.5.sp, color = if (q.done) p.muted else p.ink, fontFamily = BodyFont); Small("${q.have}/${q.need}") }
            Meter(q.have.toFloat() / q.need * enter(800, 200), Modifier.height(5.dp))
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

/**
 * The number the whole app is about, on top of its own history. Drag across the chart to read any run.
 * When the pace has improved since the last visit, the number rolls down to the new value.
 */
@Composable
fun PaceHero(vm: AppVM, e: Engine, improved: Boolean) {
    val p = LocalPal.current
    val tg = e.s.targetPace.toDouble()
    val c = e.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.hero)) {
        if (c == null) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Label("Zone 2 pace target", p.heroMuted)
                BigPace(fmtPace(tg))
                Text("Log a run with heart rate and your own pace takes this spot.", color = p.heroMuted, fontSize = 15.sp, lineHeight = 21.sp, fontFamily = BodyFont)
            }
            return@Column
        }
        val pts = e.eligible
        val seen = vm.settings.seenPace
        var scrub by remember { mutableStateOf<Int?>(null) }
        val tick = haptic()
        val roll = if (improved) enter(1500, 400) else 1f
        LaunchedEffect(c.roundToInt()) {
            if (seen != c.roundToInt()) { if (improved && Motion.on) { delay(1900); tick(true) }; vm.updateSettings { it.copy(seenPace = c.roundToInt()) } }
        }
        val at = scrub?.let { pts.getOrNull(it) }
        val shown = at?.second ?: if (improved) seen + (c - seen) * roll else c
        val gap = c - tg
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Label(if (at != null) "${fmtDay(at.first.date)} · ${TYPE_NAMES[at.first.type] ?: "Run"}" else "Zone 2 pace · ${e.refHr} bpm", p.heroMuted)
                    BigPace(fmtPace(shown))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Label("Target", p.heroMuted)
                    Text(fmtPace(tg), fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 34.sp, color = p.heroInk)
                    Text(if (gap > 0) "${fmtPace(gap)} to go" else "reached", fontSize = 13.sp, color = p.heroMuted, fontFamily = BodyFont)
                }
            }
            val ch = e.paceChange()
            val (line, loud) = when {
                at != null -> (if (at.first.id == "_base") "Your starting point" else "Ran ${fmtPace(paceOf(at.first))} at ${at.first.avgHr} bpm") to false
                improved -> "${U.s((seen - c).roundToInt())} seconds faster since you last looked." to true
                gap <= 0 -> "You're holding your target. Set a faster one in Settings." to true
                ch == null -> "A few more runs and the trend appears." to false
                ch.real && ch.delta >= 3 -> "${U.s(ch.delta.roundToInt())} seconds faster than ${ch.since}." to false
                ch.real && ch.delta <= -3 -> "${U.s((-ch.delta).roundToInt())} seconds slower than ${ch.since}." to false
                abs(ch.delta) >= 3 -> "About level with ${ch.since}. A few seconds either way is normal wobble between runs." to false
                else -> "Level with ${ch.since}." to false
            }
            Text(line, color = if (loud) p.heroMark else p.heroInk, fontSize = 16.sp, lineHeight = 22.sp, fontFamily = BodyFont, fontWeight = FontWeight.Medium)
        }
        PaceArea(pts, tg, e.today, scrub, { if (it != scrub) { scrub = it; if (it != null) tick(false) } }, Modifier.fillMaxWidth().height(128.dp),
            line = p.easy.lighten(), target = p.heroInk, muted = p.heroMuted, dot = p.heroMark, ground = p.hero)
        e.confidence()?.let { conf ->
            Row(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { for (i in 0..2) Box(Modifier.size(8.dp).clip(CircleShape).background(if (i <= conf.level) p.heroMark else p.heroTrack)) }
                Text(conf.label + (e.band?.let { " · likely ${fmtPace(it.first)}–${fmtPace(it.second)}" } ?: if (pts.size > 1) " · drag the chart to look back" else ""), color = p.heroMuted, fontSize = 13.sp, fontFamily = BodyFont)
            }
        }
    }
}

@Composable
private fun BigPace(t: String) {
    val p = LocalPal.current
    Row(verticalAlignment = Alignment.Bottom) {
        Text(t, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 88.sp, lineHeight = 80.sp, color = p.heroInk)
        Text(" ${U.per}", Modifier.padding(bottom = 7.dp), fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = p.heroMuted)
    }
}

/** Zone 2 pace over time as a filled curve. Faster is higher. Press or drag to pick a run; [onScrub] gets its index. */
@Composable
fun PaceArea(pts: List<Pair<Run, Double>>, targetPace: Double, today: LocalDate, scrub: Int?, onScrub: (Int?) -> Unit, modifier: Modifier,
             line: Color, target: Color, muted: Color, dot: Color, ground: Color) {
    val draw = enter(1100, 200)
    val pad = 20f
    fun xs(width: Float, density: Float): List<Float> {
        val l = pad * density; val r = width - pad * density
        if (pts.size == 1) return listOf((l + r) / 2)
        val d0 = pts.first().first.date; val span = max(1L, ChronoUnit.DAYS.between(d0, maxOf(pts.last().first.date, today))).toFloat()
        return pts.map { l + ChronoUnit.DAYS.between(d0, it.first.date) / span * (r - l) }
    }
    Canvas(modifier
        .pointerInput(pts.size) { detectTapGestures(onPress = { o -> val x = xs(size.width.toFloat(), density); onScrub(x.indices.minByOrNull { abs(x[it] - o.x) }); if (tryAwaitRelease()) onScrub(null) }) }
        .pointerInput(pts.size) {
            detectHorizontalDragGestures(onDragStart = { o -> val x = xs(size.width.toFloat(), density); onScrub(x.indices.minByOrNull { abs(x[it] - o.x) }) }, onDragEnd = { onScrub(null) }, onDragCancel = { onScrub(null) }) { ch, _ ->
                ch.consume(); val x = xs(size.width.toFloat(), density); onScrub(x.indices.minByOrNull { abs(x[it] - ch.position.x) })
            }
        }) {
        if (pts.isEmpty()) return@Canvas
        val x = xs(size.width, density)
        val vmin = min(targetPace, pts.minOf { it.second }); val vmax = max(pts.maxOf { it.second }, vmin + 30)
        val top = 16.dp.toPx(); val bottom = size.height - 10.dp.toPx()
        fun y(v: Double) = top + ((v - vmin) / (vmax - vmin)).toFloat() * (bottom - top)
        val ty = y(targetPace)
        drawLine(target.copy(alpha = .55f), Offset(0f, ty), Offset(size.width, ty), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
        drawContext.canvas.nativeCanvas.drawText("target", size.width - 20.dp.toPx(), ty + 15.dp.toPx(),
            Paint().apply { isAntiAlias = true; textSize = 11.5.sp.toPx(); color = muted.toArgb(); textAlign = Paint.Align.RIGHT })
        val ys = pts.map { y(it.second) }
        val curve = Path().apply {
            moveTo(if (pts.size == 1) 0f else x[0], ys[0])
            if (pts.size == 1) lineTo(size.width, ys[0])
            for (i in 1 until pts.size) { val mid = (x[i - 1] + x[i]) / 2; cubicTo(mid, ys[i - 1], mid, ys[i], x[i], ys[i]) }
        }
        val area = Path().apply { addPath(curve); lineTo(if (pts.size == 1) size.width else x.last(), size.height); lineTo(if (pts.size == 1) 0f else x[0], size.height); close() }
        clipRect(right = size.width * draw) {
            drawPath(area, Brush.verticalGradient(listOf(line.copy(alpha = .38f), line.copy(alpha = 0f)), startY = ys.min(), endY = size.height))
            drawPath(curve, line, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        }
        val i = scrub ?: pts.lastIndex
        if (scrub != null) drawLine(muted.copy(alpha = .7f), Offset(x[i], 0f), Offset(x[i], size.height), 1.dp.toPx())
        if (draw > .95f || scrub != null) { drawCircle(ground, 7.dp.toPx(), Offset(x[i], ys[i])); drawCircle(dot, 4.5.dp.toPx(), Offset(x[i], ys[i])) }
    }
}

/** The plan as a week you can see at once: seven days across the top, one day's detail underneath. */
@Composable
fun PlanScreen(vm: AppVM, e: Engine, openLive: (Session) -> Unit, openRun: (Run) -> Unit, openForm: (FormSeed) -> Unit) {
    val p = LocalPal.current
    val tick = haptic()
    var off by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf<LocalDate?>(null) }
    val base = mondayOf(e.today)
    val mon = base.plusWeeks(off.toLong())
    val plan = e.weekPlan(mon)
    val onBreak = e.onBreak(mon)
    var sel by remember(off) { mutableIntStateOf(if (off == 0) e.today.dayOfWeek.value - 1 else (plan.ses.keys.firstOrNull() ?: 1) - 1) }
    var restSeen = 0
    val kinds = (0..6).map { i -> plan.ses[i + 1]?.kind ?: run { restSeen++; if (restSeen <= 2) "strength" else "rest" } }

    Column(Modifier.rise(0), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(CircleShape).clickable { tick(false); off-- }.padding(10.dp)) { Glyph("left", p.ink, Modifier.size(20.dp)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Head(if (onBreak) "Week off" else "Step ${plan.wi + 1} · ${plan.phase}")
                Small("${fmtDay(mon)} – ${fmtDay(mon.plusDays(6))} · ${plan.total} min")
            }
            Box(Modifier.clip(CircleShape).clickable { tick(false); off++ }.padding(10.dp)) { Glyph("right", p.ink, Modifier.size(20.dp)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (i in 0..6) {
                val d = mon.plusDays(i.toLong()); val s = plan.ses[i + 1]; val ran = e.runsOn(d).isNotEmpty(); val today = d == e.today
                val shape = RoundedCornerShape(14.dp)
                Column(Modifier.weight(1f).clip(shape).background(if (i == sel) p.surface else Color.Transparent).then(if (i == sel) Modifier.border(1.dp, p.line, shape) else Modifier)
                    .clickable { tick(false); sel = i }.padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(DOW[i], fontSize = 12.sp, fontFamily = BodyFont, fontWeight = if (today) FontWeight.SemiBold else FontWeight.Normal, color = if (today) p.accent else p.muted)
                    SessionArt(kinds[i], Modifier.size(34.dp), done = ran)
                    Text(if (s != null) "${s.min}" else "·", fontSize = 12.sp, fontFamily = BodyFont, color = if (s != null && !ran && d.isBefore(e.today) && !onBreak) p.warnInk else p.muted)
                }
            }
        }
    }

    // The selected day.
    key(off, sel) {
        val d = mon.plusDays(sel.toLong()); val s = plan.ses[sel + 1]; val rs = e.runsOn(d); val today = d == e.today
        var showSteps by remember { mutableStateOf(false) }
        Panel(Modifier.rise(1)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SessionArt(kinds[sel], Modifier.size(58.dp), done = rs.isNotEmpty())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Label("${DAY_NAMES[sel]} ${fmtDay(d)}" + if (today) " · today" else "")
                    Head(s?.title ?: if (kinds[sel] == "strength") "Strength, 20 min" else "Rest")
                    if (s != null) Small("${s.min} min · " + if (s.type in setOf("tempo", "intervals")) "hard day" else "cap ${e.zb[2] - 1} bpm")
                }
                if (rs.isEmpty() && s != null && d.isBefore(e.today) && !onBreak) Tag("Missed", p.warnBg, p.warnInk)
            }
            Text(when { onBreak -> "You're taking this week off."; s != null -> s.detail.substringBefore(". ") + "."; kinds[sel] == "strength" -> "Squats, lunges, calf raises, planks."; else -> "Nothing planned. Rest is part of the plan." },
                fontFamily = BodyFont, fontSize = 15.sp, lineHeight = 21.sp, color = p.ink)
            if (s != null) {
                AnimatedVisibility(showSteps) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        e.steps(s).let { st -> st.take(10).forEach { x ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Dot(p.zones[(x.zHi - 1).coerceIn(0, 4)]); Body(x.label, modifier = Modifier.weight(1f)); Small(if (x.sec == 0) "open" else if (x.sec >= 60) "${x.sec / 60} min" else "${x.sec} s")
                            }
                        }; if (st.size > 10) Small("…and ${st.size - 10} more blocks.") }
                    }
                }
            }
            rs.firstOrNull()?.let { r -> Link("road", "Logged", "${U.dist(r.distKm)} · ${fmtPace(paceOf(r))} ${U.per}" + if (r.avgHr > 0) " · ${r.avgHr} bpm" else "") { openRun(r) } }
            if (rs.isEmpty() && !onBreak) {
                if (s != null && today) BigBtn("Start run", "play") { openLive(s) }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (s != null && !d.isAfter(e.today)) TextLink("Log it") { openForm(FormSeed(date = d, type = s.type)) }
                    if (s != null) TextLink(if (showSteps) "Hide blocks" else "What you'll do") { showSteps = !showSteps }
                    if (!d.isBefore(e.today)) TextLink(if (s != null) "Change" else "Add a run") { menu = d }
                }
            }
        }
    }

    menu?.let { d ->
        val cur = plan.ses[d.dayOfWeek.value]
        val close = { menu = null }
        androidx.compose.material3.AlertDialog(onDismissRequest = close, confirmButton = { androidx.compose.material3.TextButton(close) { Text("Close") } },
            title = { Text("${DAY_NAMES[d.dayOfWeek.value - 1]} ${fmtDay(d)}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Small(if (cur != null) "Planned: ${cur.title}. Swap it for:" else "Rest day. Add:")
                    SWAP_KINDS.forEach { (kind, name) -> Pick(name, cur?.kind == kind, Modifier.fillMaxWidth()) { vm.override(d, kind); close() } }
                    if (cur != null) {
                        val free = (0..6).map { mon.plusDays(it.toLong()) }.filter { it != d && !it.isBefore(e.today) && plan.ses[it.dayOfWeek.value] == null && e.runsOn(it).isEmpty() }
                        if (free.isNotEmpty()) {
                            Small("Or move it to:")
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { free.forEach { t -> Pick(DOW[t.dayOfWeek.value - 1], false, Modifier.weight(1f)) { vm.moveSession(d, t, cur.kind); close() } } }
                        }
                        Pick("Skip this session", false, Modifier.fillMaxWidth()) { vm.override(d, "skip"); close() }
                    }
                    if (d in e.s.overrides) Pick("Back to the plan", false, Modifier.fillMaxWidth()) { vm.override(d, null); close() }
                }
            })
    }

    Column(Modifier.rise(2)) {
        if (off >= 0) Link("moon", if (onBreak) "I'm back" else "Take this week off", if (onBreak) "Bring the week back" else "Sick, travelling or worn out? Your streak stays safe.") { vm.toggleBreak(mon) }
        if (e.runs.isNotEmpty()) {
            val recent = e.weeks.takeLast(8)
            Fold("chart", "Recent weeks", recent.count { it.state == WeekState.HIT }.let { "$it of ${recent.size} completed" }, startOpen = true) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    recent.forEach { w ->
                        val (bg, fg, g) = when (w.state) {
                            WeekState.HIT -> Triple(p.accent, p.accentInk, "check"); WeekState.FROZEN -> Triple(p.zones[0], Color.White, "snow"); WeekState.BREAK -> Triple(p.surface2, p.muted, "moon")
                            WeekState.MISS -> Triple(p.warnBg, p.warnInk, "dash"); WeekState.OPEN -> Triple(p.surface2, p.muted, "dot")
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { Glyph(g, fg, Modifier.fillMaxSize(.5f)) }
                            Text(fmtDay(w.mon).substringBefore(" "), fontSize = 11.sp, color = p.muted, fontFamily = BodyFont)
                        }
                    }
                    repeat(8 - recent.size) { Spacer(Modifier.weight(1f)) }
                }
                Small("Tick: goal hit. Snowflake: a rest pass covered it. Moon: week off. Dash: missed. The plan only moves on after a completed week.")
            }
        }
        val plans = (0 until 12).map { e.weekPlan(base.plusWeeks(it.toLong())) }
        Fold("bars", "Next 12 weeks", "${plans.first().total} min now, ${plans.maxOf { it.total }} at the peak") {
            val mx = max(1, plans.maxOf { it.total })
            val grow = enter(600, 100)
            Row(Modifier.height(56.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                plans.forEachIndexed { i, w ->
                    Box(Modifier.weight(1f).fillMaxHeight(max(.15f, w.total.toFloat() / mx * grow)).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (i == off) p.accent else p.line.copy(alpha = if (w.pos == 3) .55f else 1f)).clickable { tick(false); off = i })
                }
            }
            Small("Three steps build by about 8%, the fourth eases off and re-tests. Tap a bar to open that week.")
        }
    }
}

@Composable
fun Tag(t: String, bg: Color, fg: Color) =
    Text(t, Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 9.dp, vertical = 2.dp), color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = BodyFont)
