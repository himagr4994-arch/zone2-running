package app.zone2running

import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** One logged run. Days of week elsewhere use ISO numbering: 1 = Monday … 7 = Sunday. */
data class Run(
    val id: String,
    val date: LocalDate,
    val type: String,          // easy, long, test, tempo, intervals, hard
    val distKm: Double,
    val durSec: Int,
    val avgHr: Int = 0,
    val maxHr: Int = 0,
    val feel: Int = 0,
    val notes: String = "",
    val source: String = "manual",
    val zoneSec: List<Int> = emptyList(),   // seconds in Zone 1..5, when heart-rate samples were available
)

data class Settings(
    val targetPace: Int = 420,                 // seconds per km at Zone 2
    val maxHr: Int = 185,
    val restHr: Int = 60,
    val zoneMethod: String = "hrr",            // hrr, max, custom
    val customZ: List<Int> = listOf(135, 148, 160, 172), // starts of Z2..Z5
    val refHr: Int = 0,                        // 0 = middle of Zone 2
    val days: List<Int> = listOf(2, 4, 6, 7),
    val longDay: Int = 7,
    val easyMin: Int = 40,
    val longMin: Int = 70,
    val planStart: LocalDate? = null,
    val quality: Boolean = true,
    val onboarded: Boolean = false,
    val basePace: Int = 0,
    val baseHr: Int = 0,
    val baseDate: LocalDate? = null,
    val gameOn: Boolean = true,
    val autoImport: Boolean = true,
    val backupTree: String = "",
    val lastBackup: String = "",
    val introSeen: Boolean = false,
    val goalWeeks: Int = 8,                    // the streak the runner committed to in setup
    val breakWeeks: List<LocalDate> = emptyList(), // Mondays of weeks taken off (sick, travel, life)
    val lessonsDone: List<String> = emptyList(),
    val remindOn: Boolean = false,
    val remindMin: Int = 390,                  // minutes after midnight
)

data class Session(val type: String, val title: String, val min: Int, val detail: String)
data class WeekPlan(val wi: Int, val pos: Int, val ses: Map<Int, Session>, val total: Int, val phase: String)
data class Span(val easy: Double, val hard: Double, val km: Double, val n: Int)
data class WeekHit(val need: Int, val planned: Int, val n: Int, val hit: Boolean, val s: Span)
data class Progress(val have: Double, val need: Double, val unit: String)
data class Badge(val id: String, val glyph: String, val tier: Int, val name: String, val desc: String, val got: Boolean, val prog: Progress? = null)
enum class WeekState { HIT, MISS, FROZEN, BREAK, OPEN }
data class WeekRec(val mon: LocalDate, val state: WeekState, val idx: Int, val span: Span)
data class Quest(val title: String, val have: Int, val need: Int, val xp: Int = 50) { val done get() = have >= need }
data class Review(val mon: LocalDate, val state: WeekState, val n: Int, val planned: Int, val easyShare: Int?, val km: Double, val line: String)
/** The one thing the home screen leads with right now. */
data class Lead(val kind: String, val mood: String, val eyebrow: String, val title: String, val body: String,
                val session: Session? = null, val date: LocalDate? = null, val run: Run? = null, val cta: String = "")
data class PaceChange(val delta: Double, val since: String)          // positive delta = faster
data class Confidence(val level: Int, val label: String, val why: String)   // 0 rough, 1 fair, 2 solid
data class Note(val tone: String, val text: String)   // tone: warn, good, plain
data class Trend(val a: Double, val b: Double, val t0: LocalDate)
data class Challenge(val goal: Int, val done: Int, val daysLeft: Int)
data class Game(
    val xp: Int, val streak: Int, val best: Int, val weeksHit: Int, val km: Double, val longest: Double,
    val longSec: Int, val z2runs: Int, val tests: Int, val bestEst: Double?, val fast: Double?,
    val bigWeek: Double, val gain: Double, val level: Int, val lo: Int, val hi: Int, val badges: List<Badge>,
    val passes: Int = 0, val questsDone: Int = 0,
)

val TYPE_NAMES = linkedMapOf(
    "easy" to "Easy run", "long" to "Long easy run", "test" to "Zone 2 test",
    "tempo" to "Steady / tempo", "intervals" to "Intervals", "hard" to "Hard effort", "baseline" to "Starting point",
)
val DOW = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
val MONTHS_FULL = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
const val LESSON_COUNT = 9
val LEVEL_NAMES = listOf("Starter", "Jogger", "Base Builder", "Steady", "Aerobic", "Engine", "Diesel", "Metronome", "Long Hauler", "Tireless", "Marathon Heart", "Zone 2 Master")

fun levelName(n: Int) = LEVEL_NAMES.getOrElse(n - 1) { "Level $n" }
fun fmtPace(sec: Double): String { val s = sec.roundToInt(); return "${s / 60}:${(s % 60).toString().padStart(2, '0')}" }
fun fmtDur(sec: Int): String {
    val h = sec / 3600; val m = sec % 3600 / 60; val s = sec % 60
    return (if (h > 0) "$h:${m.toString().padStart(2, '0')}" else "$m") + ":" + s.toString().padStart(2, '0')
}
/** "1:11:37" → seconds, "45:30" → seconds, "45" → minutes. Returns 0 when unreadable. */
fun parseDur(t: String): Int {
    val p = t.trim().split(":").map { it.trim().toIntOrNull() ?: return 0 }
    return when (p.size) { 3 -> p[0] * 3600 + p[1] * 60 + p[2]; 2 -> p[0] * 60 + p[1]; 1 -> p[0] * 60; else -> 0 }
}
fun f1(x: Double) = String.format(Locale.US, "%.1f", x)
fun f2(x: Double) = String.format(Locale.US, "%.2f", x)
fun fmtDay(d: LocalDate) = "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}"
fun mondayOf(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())
fun paceOf(r: Run) = r.durSec / r.distKm

/** All training maths. Pure Kotlin so it can be unit-tested off the phone. */
class Engine(val s: Settings, runsIn: List<Run>, val today: LocalDate) {
    val runs: List<Run> = runsIn.sortedBy { it.date }

    /** Lower bounds of Zone 1..5 in bpm. */
    val zb: List<Int> = if (s.zoneMethod == "custom") {
        val c = s.customZ; listOf(c[0] - (c[1] - c[0])) + c
    } else listOf(.5, .6, .7, .8, .9).map {
        (if (s.zoneMethod == "hrr") s.restHr + it * (s.maxHr - s.restHr) else it * s.maxHr).roundToInt()
    }
    val refHr: Int = if (s.refHr > 0) s.refHr else ((zb[1] + zb[2]) / 2.0).roundToInt()

    fun zoneOf(hr: Int): Int { var z = 1; for (i in 1..4) if (hr >= zb[i]) z++; return z }

    /** Pace this run implies at the Zone 2 reference heart rate, scaled along heart-rate reserve. */
    fun est(r: Run): Double? {
        if (r.avgHr <= 0 || r.distKm <= 0 || r.durSec <= 0 || r.type == "intervals") return null
        val d = refHr - s.restHr
        if (r.avgHr - s.restHr < 25 || d < 15) return null
        return paceOf(r) * (r.avgHr - s.restHr) / d
    }

    private val basePoint: Run? = if (s.basePace > 0 && s.baseHr > 0 && s.baseDate != null)
        Run("_base", s.baseDate, "baseline", 1.0, s.basePace, s.baseHr) else null

    val eligible: List<Pair<Run, Double>> = (listOfNotNull(basePoint) + runs)
        .mapNotNull { r -> est(r)?.let { r to it } }.sortedBy { it.first.date }

    val current: Double? = eligible.takeLast(3).takeIf { it.isNotEmpty() }?.map { it.second }?.average()
    val baseline: Double? = eligible.firstOrNull()?.second

    fun isEasy(r: Run) = if (r.avgHr > 0) zoneOf(r.avgHr) <= 2 else r.type in setOf("easy", "long", "test")

    /** Totals for runs dated from..to (to exclusive). */
    fun span(from: LocalDate, to: LocalDate): Span {
        var easy = 0.0; var hard = 0.0; var km = 0.0; var n = 0
        for (r in runs) if (!r.date.isBefore(from) && r.date.isBefore(to)) {
            n++; km += r.distKm
            if (r.zoneSec.size == 5 && r.zoneSec.sum() > 0) { easy += (r.zoneSec[0] + r.zoneSec[1]) / 60.0; hard += r.zoneSec.drop(2).sum() / 60.0 }
            else if (isEasy(r)) easy += r.durSec / 60.0 else hard += r.durSec / 60.0
        }
        return Span(easy, hard, km, n)
    }

    fun runsOn(d: LocalDate) = runs.filter { it.date == d }

    /** Straight-line fit of the Zone 2 estimate over the last 120 days; null until there is enough data. */
    val trend: Trend? = run {
        val e = eligible.filter { ChronoUnit.DAYS.between(it.first.date, today) <= 120 }
        if (e.size < 4) return@run null
        val t0 = e.first().first.date
        val xs = e.map { ChronoUnit.DAYS.between(t0, it.first.date).toDouble() }
        val ys = e.map { it.second }
        if (xs.last() < 21) return@run null
        val mx = xs.average(); val my = ys.average()
        var sxy = 0.0; var sxx = 0.0
        for (i in xs.indices) { sxy += (xs[i] - mx) * (ys[i] - my); sxx += (xs[i] - mx).pow(2) }
        if (sxx == 0.0) null else { val b = sxy / sxx; Trend(my - b * mx, b, t0) }
    }

    val planStart: LocalDate = s.planStart ?: mondayOf(runs.firstOrNull()?.date ?: today)

    private val breakSet = s.breakWeeks.toSet()
    private val plannedCount = s.days.distinct().size
    private val weekNeed = max(1, min(max(1, plannedCount), max(2, ceil(plannedCount * .75).toInt())))

    /** True when at least half of the recent easy runs averaged Zone 3 or above. */
    val driftingHard: Boolean = runs.filter { it.type in setOf("easy", "long") && it.avgHr > 0 && ChronoUnit.DAYS.between(it.date, today) in 0..13 }
        .let { it.size >= 2 && it.count { r -> zoneOf(r.avgHr) >= 3 } * 2 >= it.size }

    /**
     * Every week from the first run to now. A missed week is covered by a rest pass when one is left
     * (one to start, one more per three completed weeks, two at most). Weeks marked as a break are neutral.
     * The plan only moves forward on completed weeks, so a missed week is repeated, not skipped.
     */
    private val weekCalc: Pair<List<WeekRec>, Int> by lazy {
        val cur = mondayOf(today); val ps = mondayOf(planStart)
        var m = runs.firstOrNull()?.let { mondayOf(it.date) }?.takeIf { !it.isAfter(cur) } ?: cur
        var idx = 0; var passes = 1; var hits = 0
        val out = ArrayList<WeekRec>()
        while (!m.isAfter(cur)) {
            val sp = span(m, m.plusDays(7))
            val st = when {
                sp.n >= weekNeed -> WeekState.HIT
                m in breakSet -> WeekState.BREAK
                m == cur -> WeekState.OPEN
                passes > 0 -> { passes--; WeekState.FROZEN }
                else -> WeekState.MISS
            }
            out += WeekRec(m, st, idx, sp)
            if (st == WeekState.HIT) { if (!m.isBefore(ps)) idx++; hits++; if (hits % 3 == 0) passes = min(2, passes + 1) }
            m = m.plusDays(7)
        }
        out to passes
    }
    val weeks: List<WeekRec> get() = weekCalc.first

    fun planIndex(mon: LocalDate): Int {
        val cur = weeks.last()
        if (mon.isAfter(cur.mon)) return max(0, cur.idx + (ChronoUnit.DAYS.between(cur.mon, mon) / 7).toInt() - if (cur.state == WeekState.BREAK) 1 else 0)
        return weeks.firstOrNull { it.mon == mon }?.idx ?: 0
    }

    fun onBreak(mon: LocalDate) = mon in breakSet

    /** Three small goals for the week, each worth XP. */
    fun quests(mon: LocalDate): List<Quest> {
        val sp = span(mon, mon.plusDays(7))
        val easyGoal = max(30, (weekPlan(mon).ses.values.filter { it.type in setOf("easy", "long", "test") }.sumOf { it.min } * .7 / 10).roundToInt() * 10)
        val inZone = runs.count { !it.date.isBefore(mon) && it.date.isBefore(mon.plusDays(7)) && it.avgHr > 0 && zoneOf(it.avgHr) <= 2 && it.durSec >= 1200 }
        return listOf(
            Quest("Run $weekNeed time${if (weekNeed == 1) "" else "s"}", min(sp.n, weekNeed), weekNeed),
            Quest("$easyGoal easy minutes", min(sp.easy.roundToInt(), easyGoal), easyGoal),
            Quest("Keep one run in Zone 2", min(inZone, 1), 1),
        )
    }

    /** The week's sessions. Three weeks build about 8% each, the fourth eases off and re-tests. */
    fun weekPlan(mon: LocalDate): WeekPlan {
        val wi = planIndex(mon)
        val pos = wi % 4; val block = wi / 4
        val f = min(1.8, 1.12.pow(block)) * listOf(1.0, 1.08, 1.16, .85)[pos]
        val r5 = { x: Double -> max(20, (x / 5).roundToInt() * 5) }
        val easy = min(75, r5(s.easyMin * f)); val long = min(150, r5(s.longMin * f))
        val days = s.days.distinct().sorted()
        val ses = LinkedHashMap<Int, Session>()
        if (days.isNotEmpty()) {
            val longDay = if (s.longDay in days) s.longDay else days.last()
            val others = days.filter { it != longDay }
            val dist = { d: Int -> abs(d - longDay).let { min(it, 7 - it) } }
            val q = if (s.quality && days.size >= 4) others.sortedWith(compareByDescending<Int> { dist(it) }.thenBy { it }).first() else null
            val testDay = if (pos == 3) others.firstOrNull { it != q } else null
            val z2 = "${zb[1]}–${zb[2] - 1} bpm"; val z3 = "${zb[2]}–${zb[3] - 1} bpm"; val z4 = "${zb[3]}–${zb[4] - 1} bpm"
            for (d in days) ses[d] = when (d) {
                longDay -> Session("long", "Long easy run", long, "Stay under ${zb[2]} bpm the whole way. Walk for a minute whenever HR climbs past it.")
                testDay -> Session("test", "Zone 2 test", 40, "10 min warm-up, then 30 min holding $refHr bpm on a flat route. It is logged as a Zone 2 test.")
                q -> if (pos == 3) Session("easy", "Easy run + strides", easy, "Keep HR $z2, then finish with 6 × 20 s relaxed fast strides.")
                else listOf(
                    Session("intervals", "4 × 4 min hard", 50, "10 min easy, 4 × 4 min in Zone 4 ($z4) with 3 min jog between, 10 min easy."),
                    Session("tempo", "Tempo 20 min", 40, "10 min easy, 20 min steady in Zone 3 ($z3), 10 min easy."),
                    Session("intervals", "6 × 3 min hard", 50, "10 min easy, 6 × 3 min in Zone 4 ($z4) with 2 min jog between, 10 min easy."),
                )[wi % 3]
                else -> if (driftingHard) Session("easy", "Easy run-walk", easy, "Jog 4 min, walk 1 min, and keep HR $z2. Your recent easy runs drifted into Zone 3, so this week builds the habit of staying low.")
                else Session("easy", "Easy run", easy, "Keep HR $z2. You should be able to speak full sentences.")
            }
        }
        return WeekPlan(wi, pos, ses, ses.values.sumOf { it.min }, listOf("Build 1", "Build 2", "Build 3", "Recovery + test")[pos])
    }

    /** Next planned session that has no run logged against it, within three weeks. */
    fun nextSession(): Pair<LocalDate, Session>? {
        for (i in 0L until 21L) {
            val d = today.plusDays(i)
            val ses = weekPlan(mondayOf(d)).ses[d.dayOfWeek.value]
            if (ses != null && runsOn(d).isEmpty()) return d to ses
        }
        return null
    }

    /** Pace band to expect on an easy day (fast end, slow end), from the current estimate. */
    fun easyRange(): Pair<Double, Double>? {
        val c = current ?: return null
        val k = c * (refHr - s.restHr)
        return k / (zb[2] - 1 - s.restHr) to k / (zb[1] - s.restHr)
    }

    /** A week keeps the streak when about three-quarters of its planned runs are done. */
    fun weekHit(mon: LocalDate): WeekHit {
        val sp = span(mon, mon.plusDays(7))
        return WeekHit(weekNeed, plannedCount, sp.n, sp.n >= weekNeed, sp)
    }

    val game: Game by lazy {
        var xp = 0; var km = 0.0; var longest = 0.0; var longSec = 0; var z2 = 0; var tests = 0
        var hour = false; var fast: Double? = null
        for (r in runs) {
            val mins = r.durSec / 60.0
            xp += (mins * (if (isEasy(r)) 2 else 1)).roundToInt()          // easy minutes earn double
            if (weekPlan(mondayOf(r.date)).ses.containsKey(r.date.dayOfWeek.value)) xp += 20
            if (r.type == "test") { xp += 100; tests++ }
            km += r.distKm; longest = max(longest, r.distKm); longSec = max(longSec, r.durSec)
            if (r.avgHr > 0 && zoneOf(r.avgHr) <= 2 && mins >= 20) { z2++; if (mins >= 60) hour = true }
            val p = paceOf(r); if (r.distKm >= 3 && (fast == null || p < fast)) fast = p
        }
        val bestEst = eligible.minOfOrNull { it.second }
        var streak = 0; var best = 0; var weeksHit = 0; var pol = false; var bigWeek = 0.0; var questsDone = 0; var fullQuestWeek = false
        if (runs.isNotEmpty()) for (w in weeks) {
            val tot = w.span.easy + w.span.hard
            bigWeek = max(bigWeek, w.span.km)
            if (w.span.n >= 3 && tot > 0 && w.span.easy / tot >= .8) pol = true
            val q = quests(w.mon).count { it.done }; questsDone += q; xp += q * 50; if (q == 3) fullQuestWeek = true
            when (w.state) {
                WeekState.HIT -> { streak++; weeksHit++; xp += 100; best = max(best, streak) }
                WeekState.MISS -> streak = 0
                else -> Unit                      // a rest pass, a break or the week in progress leaves the streak alone
            }
        }
        xp += s.lessonsDone.distinct().size * 40
        val c = current; val b0 = baseline
        val gain = if (b0 != null && c != null && eligible.size >= 3) max(0.0, b0 - c) else 0.0   // two points are noise, not progress
        xp += floor(gain / 15).toInt() * 150
        var lv = 1; while (150 * (lv + 1) * lv <= xp) lv++
        val badges = listOf(
            Badge("first", "1", 1, "First steps", "Log your first run.", runs.isNotEmpty()),
            Badge("z2", "Z2", 1, "True easy", "Finish a run of 20+ minutes with average HR in Zone 2.", z2 >= 1),
            Badge("week", "1W", 1, "On plan", "Hit your weekly session goal.", weeksHit >= 1),
            Badge("test", "TEST", 1, "Benchmark", "Complete a Zone 2 test.", tests >= 1),
            Badge("l10", "10K", 1, "Ten", "Run 10 km in one go.", longest >= 10, Progress(longest, 10.0, "km")),
            Badge("pol", "80/20", 2, "Polarised", "A week of 3+ runs with at least 80% easy time.", pol),
            Badge("hour", "60′", 2, "Easy hour", "Hold Zone 2 for 60 minutes in one run.", hour),
            Badge("r1", "−15s", 2, "First rung", "Zone 2 pace 15 s/km faster than where you started.", gain >= 15, Progress(gain, 15.0, "s")),
            Badge("s4", "4W", 2, "Habit", "Keep a 4-week streak.", best >= 4, Progress(best.toDouble(), 4.0, "weeks")),
            Badge("z10", "Z2×10", 2, "Discipline", "Keep 10 runs in Zone 2.", z2 >= 10, Progress(z2.toDouble(), 10.0, "runs")),
            Badge("k100", "100", 2, "Century", "Log 100 km in total.", km >= 100, Progress(km, 100.0, "km")),
            Badge("l15", "15K", 2, "Fifteen", "Run 15 km in one go.", longest >= 15, Progress(longest, 15.0, "km")),
            Badge("r2", "−30s", 3, "Second rung", "Zone 2 pace 30 s/km faster than where you started.", gain >= 30, Progress(gain, 30.0, "s")),
            Badge("s8", "8W", 3, "Locked in", "Keep an 8-week streak.", best >= 8, Progress(best.toDouble(), 8.0, "weeks")),
            Badge("l21", "21K", 3, "Half marathon", "Run 21.1 km in one go.", longest >= 21.1, Progress(longest, 21.1, "km")),
            Badge("r4", "−60s", 3, "Minute faster", "Zone 2 pace 60 s/km faster than where you started.", gain >= 60, Progress(gain, 60.0, "s")),
            Badge("k500", "500", 3, "Five hundred", "Log 500 km in total.", km >= 500, Progress(km, 500.0, "km")),
            Badge("s12", "12W", 3, "Unbreakable", "Keep a 12-week streak.", best >= 12, Progress(best.toDouble(), 12.0, "weeks")),
            Badge("quests", "3/3", 2, "Clean sweep", "Finish all three weekly quests in one week.", fullQuestWeek),
            Badge("learn", "A+", 2, "Student", "Finish every lesson.", s.lessonsDone.distinct().size >= LESSON_COUNT, Progress(s.lessonsDone.distinct().size.toDouble(), LESSON_COUNT.toDouble(), "lessons")),
            Badge("goal", fmtPace(s.targetPace.toDouble()), 3, "Arrived", "Hold your target pace at Zone 2 heart rate.", c != null && c <= s.targetPace),
        )
        Game(xp, streak, best, weeksHit, km, longest, longSec, z2, tests, bestEst, fast, bigWeek, gain, lv, 150 * lv * (lv - 1), 150 * (lv + 1) * lv, badges, weekCalc.second, questsDone)
    }

    /** Up to three notes drawn from the runner's own numbers. */
    fun coach(): List<Note> {
        if (runs.isEmpty()) return listOf(Note("plain", "Log your first run to get coaching notes based on your own numbers."))
        val out = ArrayList<Note>()
        weeks.dropLast(1).lastOrNull()?.let { if (it.state == WeekState.FROZEN || it.state == WeekState.MISS) out += Note("plain", if (it.state == WeekState.FROZEN) "Last week fell short, so a rest pass kept your streak. This week repeats the same step of the plan instead of building." else "Last week fell short, so this week repeats the same step of the plan. Hit it and the plan moves on.") }
        val two = span(today.minusDays(13), today.plusDays(1)); val tot = two.easy + two.hard
        if (tot > 0 && two.n >= 2 && two.easy / tot < .7)
            out += Note("warn", "${(two.hard / tot * 100).roundToInt()}% of your last two weeks was Zone 3 or harder. Aim for about 80% easy: slow down until HR stays under ${zb[2]} bpm, even if that means walk breaks.")
        else if (tot > 0 && two.n >= 3)
            out += Note("good", "Good balance: ${(two.easy / tot * 100).roundToInt()}% of your last two weeks was easy. Keep the hard days hard and the easy days easy.")
        val last = runs.last(); val since = ChronoUnit.DAYS.between(last.date, today)
        if (since <= 1 && last.avgHr > 0 && zoneOf(last.avgHr) >= 4)
            out += Note("plain", "Your last run averaged ${last.avgHr} bpm, Zone ${zoneOf(last.avgHr)}. Make the next one properly easy, capped at ${zb[2] - 1} bpm, or take a rest day.")
        if (since >= 5)
            out += Note("warn", "$since days since your last run. Consistency moves Zone 2 pace more than any single session. A 30 minute easy run today is enough.")
        val mon = mondayOf(today); val a = span(mon.minusDays(7), mon); val c = span(mon, mon.plusDays(7))
        if (a.km > 5 && c.km > a.km * 1.2)
            out += Note("warn", "This week is already ${((c.km / a.km - 1) * 100).roundToInt()}% above last week (${f1(c.km)} vs ${f1(a.km)} km). Keep jumps near 10% to stay injury-free.")
        if (eligible.size >= 4) {
            val old = eligible.filter { ChronoUnit.DAYS.between(it.first.date, today) >= 21 }.takeLast(2)
            if (old.isNotEmpty()) {
                val d = old.map { it.second }.average() - eligible.takeLast(2).map { it.second }.average()
                if (d >= 5) out += Note("good", "Your Zone 2 estimate is ${d.roundToInt()} s/km faster than three weeks ago. The base work is paying off.")
            }
        }
        val lastTest = runs.lastOrNull { it.type == "test" }
        if (runs.size >= 3 && (lastTest == null || ChronoUnit.DAYS.between(lastTest.date, today) > 28))
            out += Note("plain", "No Zone 2 test in the last 4 weeks. Run 30 min at $refHr bpm on a flat route and log it as a test. It is the cleanest read on your progress.")
        if (runs.size < 3) out += Note("plain", "The estimate sharpens with every run. Easy runs logged with average HR give the most reliable reading.")
        return out.take(3)
    }

    /** This month's easy-minutes goal, prorated when the runner started mid-month. */
    fun challenge(): Challenge {
        val m0 = today.withDayOfMonth(1); val m1 = m0.plusMonths(1)
        val start = runs.firstOrNull()?.date ?: today
        val from = if (start.isAfter(m0)) start else m0
        val goal = max(60, (weekPlan(mondayOf(today)).total * .8 * ChronoUnit.DAYS.between(from, m1) / 7 / 10).roundToInt() * 10)
        val done = runs.filter { !it.date.isBefore(m0) && it.date.isBefore(m1) && isEasy(it) }.sumOf { it.durSec / 60.0 }.roundToInt()
        return Challenge(goal, done, ChronoUnit.DAYS.between(today, m1).toInt())
    }

    /**
     * Picks what the home screen should lead with, from the time of day and what has already happened today.
     * Order matters: a finished run beats everything, a streak at risk beats the ordinary plan.
     */
    fun lead(t: LocalTime): Lead {
        val mon = mondayOf(today); val h = t.hour
        val part = when { h < 5 -> "night"; h < 11 -> "morning"; h < 16 -> "midday"; h < 21 -> "evening"; else -> "night" }
        val ses = weekPlan(mon).ses[today.dayOfWeek.value]
        val next = today.plusDays(1); val tomorrow = weekPlan(mondayOf(next)).ses[next.dayOfWeek.value]
        val tomorrowLine = tomorrow?.let { "Tomorrow: ${it.title}, ${it.min} min." } ?: "Tomorrow is a rest day."
        val w = weekHit(mon); val left = w.need - w.n; val daysLeft = 8 - today.dayOfWeek.value
        val ran = runsOn(today).lastOrNull()
        return when {
            runs.isEmpty() -> Lead("first", "hello", "Your first run", "20 easy minutes is all it takes",
                "Go slower than feels right, keep your heart rate under ${zb[2]} bpm, and log it. That one run gives me your starting line.", date = today, cta = "Log my first run")
            ran != null -> {
                val z = if (ran.avgHr > 0) zoneOf(ran.avgHr) else 0
                val easyType = ran.type in setOf("easy", "long", "test")
                val title = when { easyType && z in 1..2 -> "Kept it easy. That's the work."; easyType && z >= 3 -> "Done. A little hot for an easy day."; ran.type == "test" -> "Benchmark recorded."; else -> "Hard session banked." }
                val body = (if (easyType && z >= 3) "Next easy run, slow down or walk until you stay under ${zb[2]} bpm. " else "") + (if (part == "evening" || part == "night") tomorrowLine else "Nothing more to do today.")
                Lead("done", if (z in 1..2) "proud" else if (z >= 4) "puffed" else "calm", "Today's run", title, body, run = ran)
            }
            onBreak(mon) -> Lead("break", "sleepy", "Week off", "Rest up. I'll be here.", "Your streak is safe and the plan picks up where you left it.")
            left > 0 && left >= daysLeft -> Lead("risk", "puffed", "Streak check",
                if (game.streak > 0) "Your ${game.streak}-week streak needs a run today" else "This week's goal needs a run today",
                "$left more run${if (left == 1) "" else "s"} with $daysLeft day${if (daysLeft == 1) "" else "s"} left. An easy 20 minutes counts.", ses, today, cta = "Log a run")
            ses != null && part == "night" -> Lead("late", "sleepy", "Getting late", "Short on time? 20 easy minutes still counts.",
                "A short run keeps the habit alive. Or sleep on it: you need $left more run${if (left == 1) "" else "s"} this week and have ${daysLeft - 1} day${if (daysLeft - 1 == 1) "" else "s"} after today.", ses, today, cta = "Log a short run")
            ses != null -> Lead("plan", "calm", when (part) { "morning" -> "Today's run"; "midday" -> "Later today"; else -> "Ready when you are" },
                "${ses.title} · ${ses.min} min", ses.detail, ses, today, cta = "Log this run")
            today.dayOfWeek.value == 1 && h < 12 && review() != null -> review()!!.let {
                Lead("review", if (it.state == WeekState.HIT) "proud" else "calm", "Last week", "${it.n} of ${it.planned} runs · ${f1(it.km)} km", it.line + " " + (if (tomorrow != null) tomorrowLine else ""))
            }
            part == "evening" || part == "night" -> Lead("rest", "calm", "Rest day", tomorrow?.let { "Tomorrow: ${it.title} · ${it.min} min" } ?: "Two rest days in a row. Enjoy them.",
                "Nothing to do tonight. Sleep is training too.")
            else -> Lead("rest", "sleepy", "Rest day", "No run today. I'm recovering.", "Rest is when the training sinks in. A short strength session or a two-minute lesson is plenty.")
        }
    }

    /** How Zone 2 pace has moved: against four weeks ago when there is history, otherwise against the start. */
    fun paceChange(): PaceChange? {
        val c = current ?: return null
        val old = eligible.filter { ChronoUnit.DAYS.between(it.first.date, today) >= 28 }.takeLast(3)
        if (old.isNotEmpty() && eligible.size > old.size) return PaceChange(old.map { it.second }.average() - c, "4 weeks ago")
        if (eligible.size >= 3) return PaceChange(baseline!! - c, "when you started")
        return null
    }

    /** How much to trust the Zone 2 estimate. Easy runs and tests read it directly; hard runs only hint at it. */
    fun confidence(): Confidence? {
        if (eligible.isEmpty()) return null
        val recent = eligible.takeLast(5).map { it.first }
        val easyRuns = recent.count { it.avgHr > 0 && zoneOf(it.avgHr) <= 2 }
        val test = recent.any { it.type == "test" && ChronoUnit.DAYS.between(it.date, today) <= 35 }
        return when {
            test || easyRuns >= 3 -> Confidence(2, "Solid estimate", "Based on runs done at Zone 2 heart rate.")
            easyRuns >= 1 -> Confidence(1, "Fair estimate", "Sharpens with two more easy runs or a Zone 2 test.")
            else -> Confidence(0, "Rough estimate", "Worked out from harder runs. One easy run at Zone 2 heart rate will firm it up.")
        }
    }

    /** Average running minutes over up to four earlier weeks that had a run; null when there is no history. */
    fun usualWeekMinutes(): Int? = weeks.dropLast(1).filter { it.span.n > 0 }.takeLast(4).takeIf { it.isNotEmpty() }
        ?.map { it.span.easy + it.span.hard }?.average()?.roundToInt()

    /** How the heart mascot feels about the current state of training. */
    fun mood(): String {
        val last = runs.lastOrNull() ?: return "hello"
        val since = ChronoUnit.DAYS.between(last.date, today)
        return when {
            onBreak(mondayOf(today)) -> "sleepy"
            since >= 5 -> "sleepy"
            since <= 1 && last.avgHr > 0 && zoneOf(last.avgHr) >= 4 -> "puffed"
            weeks.last().state == WeekState.HIT -> "proud"
            else -> "calm"
        }
    }

    fun moodLine(): String = when (mood()) {
        "hello" -> "Hi, I'm Dil, your heart. Log a run and I'll tell you how we're doing."
        "sleepy" -> if (onBreak(mondayOf(today))) "Taking the week off. I'll be here when you're back. Your streak is safe." else "It's been a few days. A short easy one would wake me up nicely."
        "puffed" -> "Phew. That last one had me working. Let's keep the next run gentle."
        "proud" -> "Weekly goal done. This is exactly the kind of week that makes me stronger."
        else -> "Nice and steady. Keep me in Zone 2 and I'll keep getting stronger."
    }

    /** A short look back at the week that just ended; null when there is nothing to say. */
    fun review(): Review? {
        val w = weeks.dropLast(1).lastOrNull() ?: return null
        val tot = w.span.easy + w.span.hard
        val share = if (tot > 0) (w.span.easy / tot * 100).roundToInt() else null
        val line = when (w.state) {
            WeekState.HIT -> if (share != null && share >= 75) "Goal hit and mostly easy. Textbook week." else "Goal hit. Next step: more of those minutes in Zone 2."
            WeekState.BREAK -> "A week off. Rest counts too."
            WeekState.FROZEN -> "Short of the goal. A rest pass covered it, and the plan waits for you."
            else -> "Short of the goal and no rest pass left, so the streak restarts. One run gets it going again."
        }
        return Review(w.mon, w.state, w.span.n, plannedCount, share, w.span.km, line)
    }

    /** What the daily reminder should say, or null when there is nothing worth a notification. */
    fun reminder(): Pair<String, String>? {
        val mon = mondayOf(today)
        if (onBreak(mon)) return null
        val ses = weekPlan(mon).ses[today.dayOfWeek.value]
        if (ses != null && runsOn(today).isEmpty()) return "Today: ${ses.title} · ${ses.min} min" to ses.detail.substringBefore(". ") + "."
        val w = weekHit(mon); val left = w.need - w.n; val daysLeft = 8 - today.dayOfWeek.value
        val g = game
        if (left > 0 && left >= daysLeft && runsOn(today).isEmpty())
            return (if (g.streak > 0) "Your ${g.streak}-week streak is on the line" else "This week's goal is still open") to "$left more run${if (left == 1) "" else "s"} this week does it. An easy 20 minutes counts."
        return null
    }

    /** Best guess at a run's type from its heart rate, for runs imported without a label. */
    fun guessType(durSec: Int, avgHr: Int): String {
        if (avgHr <= 0) return "easy"
        return when (zoneOf(avgHr)) { 1, 2 -> if (durSec >= 75 * 60) "long" else "easy"; 3 -> "tempo"; else -> "hard" }
    }
}
