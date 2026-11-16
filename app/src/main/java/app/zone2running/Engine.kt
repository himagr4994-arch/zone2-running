package app.zone2running

import java.time.LocalDate
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
)

data class Session(val type: String, val title: String, val min: Int, val detail: String)
data class WeekPlan(val wi: Int, val pos: Int, val ses: Map<Int, Session>, val total: Int, val phase: String)
data class Span(val easy: Double, val hard: Double, val km: Double, val n: Int)
data class WeekHit(val need: Int, val planned: Int, val n: Int, val hit: Boolean, val s: Span)
data class Progress(val have: Double, val need: Double, val unit: String)
data class Badge(val id: String, val glyph: String, val tier: Int, val name: String, val desc: String, val got: Boolean, val prog: Progress? = null)
data class Note(val tone: String, val text: String)   // tone: warn, good, plain
data class Trend(val a: Double, val b: Double, val t0: LocalDate)
data class Challenge(val goal: Int, val done: Int, val daysLeft: Int)
data class Game(
    val xp: Int, val streak: Int, val best: Int, val weeksHit: Int, val km: Double, val longest: Double,
    val longSec: Int, val z2runs: Int, val tests: Int, val bestEst: Double?, val fast: Double?,
    val bigWeek: Double, val gain: Double, val level: Int, val lo: Int, val hi: Int, val badges: List<Badge>,
)

val TYPE_NAMES = linkedMapOf(
    "easy" to "Easy run", "long" to "Long easy run", "test" to "Zone 2 test",
    "tempo" to "Steady / tempo", "intervals" to "Intervals", "hard" to "Hard effort", "baseline" to "Starting point",
)
val DOW = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
val MONTHS_FULL = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
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
            n++; km += r.distKm; if (isEasy(r)) easy += r.durSec / 60.0 else hard += r.durSec / 60.0
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

    /** The week's sessions. Three weeks build about 8% each, the fourth eases off and re-tests. */
    fun weekPlan(mon: LocalDate): WeekPlan {
        val wi = max(0, (ChronoUnit.DAYS.between(mondayOf(planStart), mon) / 7).toInt())
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
                else -> Session("easy", "Easy run", easy, "Keep HR $z2. You should be able to speak full sentences.")
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
        val p = weekPlan(mon).ses.size
        val need = max(2, ceil(p * .75).toInt())
        val sp = span(mon, mon.plusDays(7))
        return WeekHit(need, p, sp.n, sp.n >= need, sp)
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
        var streak = 0; var best = 0; var weeksHit = 0; var pol = false; var bigWeek = 0.0
        if (runs.isNotEmpty()) {
            val cur = mondayOf(today); var m = mondayOf(runs.first().date); var run = 0
            while (!m.isAfter(cur)) {
                val w = weekHit(m); val tot = w.s.easy + w.s.hard
                bigWeek = max(bigWeek, w.s.km)
                if (w.s.n >= 3 && tot > 0 && w.s.easy / tot >= .8) pol = true
                if (w.hit) { run++; weeksHit++; xp += 100; best = max(best, run) } else if (m.isBefore(cur)) run = 0
                m = m.plusDays(7)
            }
            streak = run
        }
        val c = current; val b0 = baseline
        val gain = if (b0 != null && c != null) max(0.0, b0 - c) else 0.0
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
            Badge("goal", fmtPace(s.targetPace.toDouble()), 3, "Arrived", "Hold your target pace at Zone 2 heart rate.", c != null && c <= s.targetPace),
        )
        Game(xp, streak, best, weeksHit, km, longest, longSec, z2, tests, bestEst, fast, bigWeek, gain, lv, 150 * lv * (lv - 1), 150 * (lv + 1) * lv, badges)
    }

    /** Up to three notes drawn from the runner's own numbers. */
    fun coach(): List<Note> {
        if (runs.isEmpty()) return listOf(Note("plain", "Log your first run to get coaching notes based on your own numbers."))
        val out = ArrayList<Note>()
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

    /** Best guess at a run's type from its heart rate, for runs imported without a label. */
    fun guessType(durSec: Int, avgHr: Int): String {
        if (avgHr <= 0) return "easy"
        return when (zoneOf(avgHr)) { 1, 2 -> if (durSec >= 75 * 60) "long" else "easy"; 3 -> "tempo"; else -> "hard" }
    }
}
