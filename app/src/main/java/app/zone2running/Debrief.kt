package app.zone2running

import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** One block of a run as it actually went: where the heart rate started, peaked, bottomed out and ended. */
data class BlockStat(val step: Step, val from: Int, val to: Int, val avg: Int, val peak: Int, val low: Int, val startHr: Int, val endHr: Int, val inTarget: Int)
data class Fact(val label: String, val value: String, val note: String = "")
/**
 * What a finished run has to say. [planned] is true when the blocks were laid over from the plan instead of
 * recorded, which assumes the runner started the session at the start of the run.
 */
data class Debrief(val blocks: List<BlockStat>, val planned: Boolean, val facts: List<Fact>, val head: String, val learned: List<Note>, val next: String)

private val STRUCTURED = setOf("runwalk", "4x4", "6x3", "tempo", "test", "talk", "tune", "strides")
private fun mmss(sec: Int) = "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"

/** The blocks a run followed: recorded ones from a live run, otherwise the day's planned session if it fits. */
fun Engine.blocksOf(r: Run): Pair<List<Step>, Boolean> {
    if (r.steps.size > 1) return r.steps to false
    if (r.trace.isEmpty()) return emptyList<Step>() to false
    val ses = weekPlan(mondayOf(r.date)).ses[r.date.dayOfWeek.value] ?: return emptyList<Step>() to false
    if (ses.kind !in STRUCTURED) return emptyList<Step>() to false
    val st = steps(ses); val total = st.sumOf { it.sec }
    return if (total > 0 && r.durSec in (total * 8 / 10)..(total * 5 / 4)) st to true else emptyList<Step>() to false
}

fun Engine.debrief(r: Run): Debrief {
    val tr = r.trace; val has = tr.count { it > 0 } >= 30
    fun seg(from: Int, to: Int) = tr.subList((from / TRACE_STEP).coerceIn(0, tr.size), (to / TRACE_STEP).coerceIn(0, tr.size)).filter { it > 0 }
    val cap = zb[2]

    // ---- blocks
    val (steps, planned) = blocksOf(r)
    val blocks = ArrayList<BlockStat>(); var t = 0
    for (s in steps) {
        if (t >= r.durSec) break
        val end = if (s.sec > 0) minOf(r.durSec, t + s.sec) else r.durSec
        val v = seg(t, end)
        if (v.isNotEmpty()) {
            val lo = zb[s.zLo - 1]; val hi = if (s.zHi >= 5) 999 else zb[s.zHi]
            blocks += BlockStat(s, t, end, v.average().roundToInt(), v.max(), v.min(), v.first(), v.last(), v.count { it in lo until hi } * 100 / v.size)
        }
        t = end
    }
    if (blocks.isNotEmpty() && r.durSec - t >= 120) seg(t, r.durSec).takeIf { it.isNotEmpty() }?.let { v ->
        blocks += BlockStat(Step("Extra running", r.durSec - t, 1, 2, "easy"), t, r.durSec, v.average().roundToInt(), v.max(), v.min(), v.first(), v.last(), v.count { it < cap } * 100 / v.size)
    }
    val talk = steps.any { it.kind == "talk" }
    val hard = blocks.filter { it.step.kind == "hard" }
    val walks = blocks.filter { it.step.kind == "walk" }; val jogs = blocks.filter { it.step.kind == "easy" && walks.isNotEmpty() }
    val recov = blocks.filterIndexed { i, b -> i > 0 && blocks[i - 1].step.kind == "hard" && b.step.kind == "easy" }

    // ---- numbers
    val zs = zoneSecOf(r).takeIf { it.size == 5 && it.sum() > 0 }
    val easyShare = zs?.let { (it[0] + it[1]) * 100 / it.sum() } ?: if (has) tr.filter { it > 0 }.let { v -> v.count { it < cap } * 100 / v.size } else null
    val minutes = zs?.sum()?.div(60) ?: (tr.count { it > 0 } * TRACE_STEP / 60)
    val easyMin = easyShare?.let { minutes * it / 100 }
    val interval = hard.isNotEmpty() || r.type == "intervals"
    val meantEasy = r.type in setOf("easy", "long", "test")
    // Drift: the second half against the first, leaving out the warm-up. With splits it is pace per beat; without, plain beats.
    val body = if (has && !interval && walks.isEmpty() && !talk && r.durSec >= 1500) seg(480, r.durSec) else emptyList()
    val driftBpm = body.takeIf { it.size >= 60 }?.let { it.subList(it.size / 2, it.size).average() - it.subList(0, it.size / 2).average() }
    val sp = r.splits.drop(1).filter { it.hr > 0 }
    val decouple = sp.takeIf { it.size >= 4 && !interval && !talk }?.let { s ->
        fun eff(x: List<Split>) = x.sumOf { 1000.0 / it.sec } / x.sumOf { it.hr.toDouble() }
        val a = eff(s.subList(0, s.size / 2)); val b = eff(s.subList(s.size / 2, s.size)); (a - b) / a * 100
    }
    val reachedZ2 = if (has) tr.indexOfFirst { it >= zb[1] }.takeIf { it >= 0 }?.times(TRACE_STEP) else null
    // The moment heart rate went above the cap and stayed there for three minutes.
    val leftAt = if (has && !interval && !talk) (0..tr.size - 18).firstOrNull { i -> tr.subList(i, i + 18).all { it >= cap } }?.times(TRACE_STEP) else null
    var run = 0; var longest = 0
    for (v in tr) { if (v in 1 until cap) { run++; if (run > longest) longest = run } else run = 0 }
    val peakAt = if (has) tr.indexOf(tr.max()) * TRACE_STEP else null
    val walkDrop = walks.takeIf { it.size >= 2 }?.map { it.startHr - it.low }?.average()
    val recDrop = recov.takeIf { it.isNotEmpty() }?.map { it.startHr - it.low }?.average()
    val mine = estOf(r)
    val prev = if (mine == null) null else ests.lastOrNull { it.run.date.isBefore(r.date) && it.run.id != "_base" && it.clean == mine.clean && ChronoUnit.DAYS.between(it.run.date, r.date) <= 60 }

    val facts = ArrayList<Fact>()
    easyShare?.let { facts += Fact("Zone 2 or below", "$it%", "${easyMin} of $minutes min") }
    if (decouple != null) facts += Fact("Drift", "${if (decouple >= 0) "+" else "−"}${f1(abs(decouple))}%", if (decouple < 5) "Steady: pace per heartbeat held" else "Pace per heartbeat faded late")
    else driftBpm?.let { facts += Fact("Drift", "${if (it >= 0) "+" else "−"}${abs(it).roundToInt()} bpm", "Second half against the first") }
    if (hard.isNotEmpty()) facts += Fact("Rep peaks", hard.joinToString(" ") { "${it.peak}" }, "Target ${zb[hard[0].step.zLo - 1]}–${zb[hard[0].step.zHi.coerceAtMost(4)] - 1} bpm")
    recDrop?.let { facts += Fact("Recovery", "−${it.roundToInt()} bpm", "Average fall in each easy jog") }
    walkDrop?.let { facts += Fact("Walk breaks", "−${it.roundToInt()} bpm", "Average fall in each walk") }
    if (jogs.size >= 3) facts += Fact("Jog heart rate", "${jogs.first().avg} → ${jogs.last().avg}", "First jog to last")
    if (leftAt != null && meantEasy.or(r.source == "health")) facts += Fact("Above Zone 2 from", mmss(leftAt), "And stayed there 3 min or more")
    else if (reachedZ2 != null && reachedZ2 > 0 && !interval) facts += Fact("Reached Zone 2", mmss(reachedZ2), "Warm-up before it")
    if (has && longest >= 18 && !interval) facts += Fact("Longest easy stretch", "${longest * TRACE_STEP / 60} min", "Unbroken under $cap bpm")
    if (r.maxHr > 0) facts += Fact("Peak", "${r.maxHr} bpm", peakAt?.let { "At ${mmss(it)}" } ?: "")
    if (r.splits.size >= 2) r.splits.withIndex().minBy { it.value.sec }.let { facts += Fact("Fastest ${U.word}", fmtPace(it.value.sec.toDouble()), "${U.word.replaceFirstChar { c -> c.uppercase() }} ${it.index + 1}") }
    val odd = setAside(r)
    mine?.let { if (!odd) facts += Fact("Zone 2 reading", fmtPace(it.pace), if (it.clean) "Direct, at $refHr bpm" else "Scaled to $refHr bpm") }
    if (r.cadence > 0) facts += Fact("Cadence", "${r.cadence}", "Steps a minute")
    if (r.kcal > 0) facts += Fact("Energy", "${r.kcal} kcal")
    if (r.climb > 0) facts += Fact("Climb", "${r.climb} m")
    if (r.heatPct >= 1.5) facts += Fact("Heat", "+${f1(r.heatPct)}%", "Slower than a cool day")

    // ---- what it means
    val learned = ArrayList<Note>()
    if (odd) learned += Note("plain", "The heart rate recorded for this run doesn't fit your other runs, so it is left out of your Zone 2 pace and the trend chart.")
    val head: String; var next: String
    val easyPace = easyRange()?.let { " At today's fitness that is around ${fmtPace(it.first)}–${fmtPace(it.second)} ${U.per}." } ?: ""
    when {
        talk -> {
            // A talk test climbs on purpose: judging it as an easy run or a hard one would miss the point.
            head = "Talk test done"
            val st = talkStages(r)
            if (st.size >= 3) {
                learned += Note("plain", "Stage by stage your heart rate went ${st.joinToString(", ") { "${it.first}" }} bpm as the pace went from ${fmtPace(st.first().second)} to ${fmtPace(st.last().second)} ${U.per}.")
                val formula = Engine(s.copy(talkHr = 0), emptyList(), today).zb[2] - 1
                if (s.talkHr > 0) {
                    val at = st.minBy { kotlin.math.abs(it.first - s.talkHr) }
                    learned += Note("good", "Your ceiling is set at ${s.talkHr} bpm, which you reached at about ${fmtPace(at.second)} ${U.per}. " + when {
                        s.talkHr - formula >= 3 -> "The formula had it at $formula, so your Zone 2 is ${s.talkHr - formula} beats wider than it assumed."
                        formula - s.talkHr >= 3 -> "The formula had it at $formula, so your real Zone 2 tops out ${formula - s.talkHr} beats lower."
                        else -> "That is where the formula put it too."
                    })
                } else learned += Note("plain", "Enter the heart rate of the last stage where talking was still comfortable and it becomes the top of your Zone 2.")
                // Stages that barely differ can't separate one heart rate from the next.
                val gaps = st.zipWithNext { a, b -> a.second - b.second }
                val flat = gaps.count { it < 15 }; val jump = gaps.maxOrNull() ?: 0.0
                if (flat >= 2 && jump >= 30) learned += Note("plain", "$flat of the steps between stages changed the pace by under ${U.s(15)} seconds, and one jumped ${U.s(jump.roundToInt())}. Even steps of about ${U.s(20)} seconds next time would pin the ceiling down more closely.")
            }
            next = "Next run: easy, under ${zb[2]} bpm." + (easyRange()?.let { " Going by today that is around ${fmtPace(it.first)}–${fmtPace(it.second)} ${U.per}." } ?: "")
        }
        interval -> {
            head = "Intervals done"
            if (hard.size >= 2) {
                val rise = hard.last().peak - hard.first().peak; val on = hard.sumOf { (it.to - it.from) * it.inTarget / 100 } / 60; val all = hard.sumOf { it.to - it.from } / 60
                learned += Note(if (on * 2 >= all) "good" else "plain", "$on of $all hard minutes were in the target zone. Heart rate takes about a minute to climb, so the first part of every rep reads low: that's normal.")
                learned += Note(if (rise in 0..8) "good" else "plain", when { rise > 8 -> "Reps peaked ${hard.joinToString(", ") { "${it.peak}" }}: a $rise-beat climb. The first rep was a touch too fast, or the recoveries too short."; rise < 0 -> "Peaks fell from ${hard.first().peak} to ${hard.last().peak}. You faded: start the next session a little gentler."; else -> "Reps peaked ${hard.joinToString(", ") { "${it.peak}" }}: rising gently and under control, which is the shape to aim for." })
            }
            recDrop?.let { learned += Note(if (it >= 20) "good" else "plain", "Heart rate fell ${it.roundToInt()} bpm in each recovery jog. ${if (it >= 20) "A quick fall like that is a sign of good fitness." else "Jog the recoveries slower, or walk them, so each rep starts fresh."}") }
            next = "Next run: properly easy, under $cap bpm.$easyPace"
        }
        walks.size >= 2 -> {
            head = if ((easyShare ?: 0) >= 80) "Run-walk, well controlled" else "Run-walk, a little hot"
            walkDrop?.let { learned += Note(if (it >= 8) "good" else "plain", "Each walk brought your heart rate down ${it.roundToInt()} bpm on average. ${if (it >= 8) "The breaks are doing their job." else "That's a small drop: slow the walks to a real stroll."}") }
            if (jogs.size >= 3) { val creep = jogs.last().avg - jogs.first().avg
                learned += Note(if (creep <= 6) "good" else "plain", if (creep <= 6) "Jog heart rate held steady, ${jogs.first().avg} at the start and ${jogs.last().avg} at the end." else "Jog heart rate crept from ${jogs.first().avg} to ${jogs.last().avg} bpm. Ease the later jogs, or take the walk a little earlier.") }
            val over = jogs.count { it.peak >= cap }
            if (jogs.isNotEmpty()) learned += Note(if (over == 0) "good" else "plain", if (over == 0) "No jog went above Zone 2." else "$over of ${jogs.size} jogs touched Zone 3 before the walk.")
            next = if ((easyShare ?: 0) >= 90 && (walkDrop ?: 0.0) >= 8) "Next: the same session. When three in a row look like this, try 5 minutes of jogging to 1 of walking." else "Next: the same session, with the jogs a touch slower."
        }
        meantEasy || (easyShare ?: 0) >= 70 -> {
            val ok = (easyShare ?: if (r.avgHr > 0 && zoneOf(r.avgHr) <= 2) 100 else 0) >= 80
            head = if (ok) "Kept it easy" else "Harder than an easy day"
            if (easyShare != null) learned += Note(if (ok) "good" else "warn", if (ok) "$easyMin of $minutes minutes were in Zone 2 or below. This is the running that moves your easy pace." else "Only $easyMin of $minutes minutes were in Zone 2 or below. It felt like training, but it wasn't the easy kind.")
            if (!ok && leftAt != null) learned += Note("plain", "Heart rate passed $cap at ${mmss(leftAt)} and never came back. The first kilometres set the day: start slower than feels right.")
            next = if (ok) "Next: the same again. Consistency is what moves the number." else "Next easy run: stay under $cap bpm from the first minute, and walk if it climbs.$easyPace"
        }
        else -> {
            head = "A hard effort"
            val z45 = zs?.let { (it[3] + it[4]) / 60 }
            learned += Note("plain", (if (z45 != null && z45 > 0) "$z45 of $minutes minutes were in Zone 4 and 5. " else "Average heart rate was Zone ${if (r.avgHr > 0) zoneOf(r.avgHr) else 3}. ") + "A run like this builds speed, but it doesn't count as Zone 2 time, and it needs a day to recover from.")
            if (leftAt != null && leftAt < 900) learned += Note("plain", "You were above Zone 2 from ${mmss(leftAt)}. If this was meant to be easy, the start was too quick.")
            next = "Next run: properly easy, under $cap bpm.$easyPace"
        }
    }
    if (!interval && walks.isEmpty() && !talk) {
        if (decouple != null) learned += Note(if (decouple < 5) "good" else "plain", if (decouple < 5) "Pace per heartbeat held to the end (${f1(abs(decouple))}% change). Your endurance covers this distance." else "Pace per heartbeat faded ${f1(decouple)}% in the second half. That's drift: heat, fluid, or a pace slightly too quick for this length.")
        else driftBpm?.let { d -> if (d >= 8) learned += Note("plain", "Heart rate was ${d.roundToInt()} bpm higher in the second half than the first. Some drift is normal; this much means the pace or the heat was costing you.") else if (d <= 4 && meantEasy) learned += Note("good", "Heart rate barely moved from the first half to the second (${d.roundToInt()} bpm). Steady.") }
    }
    if (mine != null && prev != null) { val d = prev.pace - mine.pace
        if (abs(d) >= 5) learned += Note(if (d > 0) "good" else "plain", "At $refHr bpm this run reads ${U.s(abs(d).roundToInt())} s${U.per} ${if (d > 0) "faster" else "slower"} than ${fmtDay(prev.run.date)}. One run is a hint, not a verdict.") }
    if (r.feel >= 4 && r.avgHr > 0 && zoneOf(r.avgHr) <= 2) learned += Note("plain", "It felt hard even though your heart rate stayed low. That usually points to tiredness, heat or short sleep.")
    if (r.feel in 1..2 && r.avgHr > 0 && zoneOf(r.avgHr) >= 4) learned += Note("plain", "You rated it easy, but your heart rate says Zone ${zoneOf(r.avgHr)}. Either the sensor read high or your zones are set too low: a talk test settles it.")
    if (!has && r.avgHr > 0) learned += Note("plain", "This run has no heart-rate trace, only an average, so there is less to read from it.")
    return Debrief(blocks, planned, facts.take(8), head, learned.take(4), next)
}
