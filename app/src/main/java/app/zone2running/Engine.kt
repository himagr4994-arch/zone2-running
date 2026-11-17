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
    val startMin: Int = -1,                 // start time as minutes after midnight; -1 when unknown
    val splits: List<Split> = emptyList(),  // one entry per full kilometre
    val heatPct: Double = 0.0,              // how much the weather slowed this run, in percent; 0 when unknown
    val trace: List<Int> = emptyList(),     // heart rate every TRACE_STEP seconds; 0 where there was no reading
    val pace: List<Int> = emptyList(),      // seconds per km on the same grid; 0 where unknown
    val steps: List<Step> = emptyList(),    // the guided blocks this run followed, when it was a live run
    val kind: String = "",                  // the session kind it followed: runwalk, 4x4, test…
    val cadence: Int = 0, val kcal: Int = 0, val climb: Int = 0,   // steps per minute, calories, metres climbed; 0 when unknown
    val route: List<Int> = emptyList(),     // the path, as triples: seconds from the start, latitude × 1e5, longitude × 1e5
)

/**
 * Kilometre splits worked out from a pace trace, for runs whose source gave speed but no per-kilometre
 * distance. The trace is scaled so it adds up to the recorded distance; gaps are filled at the run's average.
 */
fun splitsFromPace(pace: List<Int>, trace: List<Int>, distKm: Double): List<Split> {
    val known = pace.count { it > 0 }
    if (pace.size < 30 || known * 10 < pace.size * 6 || distKm < 1) return emptyList()
    val avg = pace.filter { it > 0 }.map { 1000.0 / it }.average()
    val step = pace.map { (if (it > 0) 1000.0 / it else avg) * TRACE_STEP }
    val k = distKm * 1000 / step.sum()
    val out = ArrayList<Split>(); var d = 0.0; var from = 0
    step.forEachIndexed { i, m ->
        d += m * k
        if (d >= (out.size + 1) * 1000.0) {
            val hr = trace.subList(from.coerceAtMost(trace.size), (i + 1).coerceAtMost(trace.size)).filter { it > 0 }
            out += Split((i + 1 - from) * TRACE_STEP, if (hr.isEmpty()) 0 else hr.average().roundToInt()); from = i + 1
        }
    }
    return out
}
const val TRACE_STEP = 10

/**
 * Heart-rate samples (seconds from the start, bpm) laid on an even grid. Between two readings up to two
 * minutes apart the value is interpolated; longer silences stay empty, because guessing across them would
 * invent data.
 */
fun traceFrom(samples: List<Pair<Long, Int>>, durSec: Int): List<Int> {
    val s = samples.filter { it.second in 30..240 }.sortedBy { it.first }
    if (s.size < 5) return emptyList()
    var j = 0
    return (0..durSec / TRACE_STEP).map { i ->
        val t = i * TRACE_STEP.toLong()
        while (j < s.size - 1 && s[j + 1].first <= t) j++
        val a = s[j]; val b = s.getOrNull(j + 1)
        when {
            a.first > t -> if (a.first - t <= 60) a.second else 0
            b == null -> if (t - a.first <= 60) a.second else 0
            b.first - a.first > 120 -> if (t - a.first <= 30) a.second else if (b.first - t <= 30) b.second else 0
            else -> (a.second + (b.second - a.second) * (t - a.first).toDouble() / (b.first - a.first)).roundToInt()
        }
    }
}

data class Split(val sec: Int, val hr: Int)

/** One block of a guided session. [sec] of 0 means open-ended. Zones are the range to stay in. */
data class Step(val label: String, val sec: Int, val zLo: Int, val zHi: Int, val kind: String)

/** Hourly weather, when the runner has switched it on: (temperature °C, dew point °C) or null. */
fun interface Wx { fun at(date: LocalDate, minute: Int): Pair<Double, Double>? }

/** Display units. Everything is stored in kilometres; this only changes what is shown and typed. */
object U {
    var miles = false
    const val MI = 1.609344
    val per get() = if (miles) "/mi" else "/km"
    val unit get() = if (miles) "mi" else "km"
    val word get() = if (miles) "mile" else "km"
    fun k(km: Double) = if (miles) km / MI else km
    fun toKm(v: Double) = if (miles) v * MI else v
    fun dist(km: Double, dec: Int = 2) = String.format(Locale.US, "%.${dec}f", k(km)) + " " + unit
    /** A number of seconds per km, shown per mile when miles are on. */
    fun s(n: Int) = if (miles) (n * MI).roundToInt() else n
    fun paceToKm(sec: Int) = if (miles) (sec / MI).roundToInt() else sec
}

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
    val snoozed: Map<String, LocalDate> = emptyMap(), // suggestions the runner said "not now" to, and when
    val miles: Boolean = false,
    val raceName: String = "", val raceDate: LocalDate? = null, val raceKm: Double = 0.0,
    val overrides: Map<LocalDate, String> = emptyMap(),   // a day's session swapped by hand: a session kind, or "skip"
    val weatherOn: Boolean = false, val city: String = "", val lat: Double = 0.0, val lon: Double = 0.0,
    val voiceOn: Boolean = true, val sensorAddr: String = "", val sensorName: String = "",
    val updateCheck: Boolean = false, val disclaimerOk: Boolean = false,
    val seenPace: Int = 0,                     // the Zone 2 pace last shown on the home screen, to notice when it improves
    val talkHr: Int = 0,                       // top of Zone 2 found with a talk test; 0 = not done, zones come from the formula
    val runWalk: Boolean = false,              // easy runs are planned as run-walk (the default for someone just starting)
    val caution: Boolean = false,              // answered yes to a health screening question: no hard sessions are planned
    val hrMeds: Boolean = false,               // on medication that changes heart rate: guidance goes by effort, not bpm
    val beepsOn: Boolean = true,               // soft tones over the last seconds of a block
    val mapTiles: Boolean = false,             // street map under the route, fetched from OpenStreetMap; off = route line only, no internet
    val hcRest: Int = 0,                       // resting heart rate last read from Health Connect, for offering an update
)

data class Session(val type: String, val title: String, val min: Int, val detail: String, val kind: String = type)
data class WeekPlan(val wi: Int, val pos: Int, val ses: Map<Int, Session>, val total: Int, val phase: String)
data class Span(val easy: Double, val hard: Double, val km: Double, val n: Int)
data class WeekHit(val need: Int, val planned: Int, val n: Int, val hit: Boolean, val s: Span)
data class Progress(val have: Double, val need: Double, val unit: String)
data class Badge(val id: String, val glyph: String, val tier: Int, val name: String, val desc: String, val got: Boolean, val prog: Progress? = null)
enum class WeekState { HIT, MISS, FROZEN, BREAK, OPEN }
data class WeekRec(val mon: LocalDate, val state: WeekState, val idx: Int, val span: Span)
data class Quest(val title: String, val have: Int, val need: Int) { val done get() = have >= need }
data class Review(val mon: LocalDate, val state: WeekState, val n: Int, val planned: Int, val easyShare: Int?, val km: Double, val line: String)
/** The one thing the home screen leads with right now. */
data class Lead(val kind: String, val mood: String, val eyebrow: String, val title: String, val body: String,
                val session: Session? = null, val date: LocalDate? = null, val run: Run? = null, val cta: String = "", val heatPct: Double = 0.0)
/** Something the app noticed in the runner's habits. It is only ever offered, never applied silently. */
sealed interface Act
data class MoveDay(val from: Int, val to: Int) : Act
data class Remind(val min: Int) : Act
data class SetMax(val hr: Int) : Act
data class SetRest(val hr: Int) : Act
object TalkTest : Act                       // put a talk test on the next easy day
object EnterTalk : Act                      // the talk test is done: ask for the number
data class RunWalk(val on: Boolean) : Act
data class Suggestion(val key: String, val title: String, val body: String, val yes: String, val act: Act)
/** [real] is false when the change is smaller than the usual spread between runs. Positive delta = faster. */
data class PaceChange(val delta: Double, val since: String, val real: Boolean = true)
/** One run's reading of Zone 2 pace. [clean] = a steady run of 20+ minutes close to the reference heart rate. */
data class Est(val run: Run, val pace: Double, val clean: Boolean)
/** The Zone 2 pace as of some day: the median of recent readings and the middle half of them. */
data class Reading(val pace: Double, val lo: Double, val hi: Double, val n: Int, val clean: Boolean)
/** A race forecast is a range, never one number. */
data class RaceCast(val sec: Int, val lo: Int, val hi: Int, val basis: String, val warn: String = "")
data class Confidence(val level: Int, val label: String, val why: String)   // 0 rough, 1 fair, 2 solid
data class Note(val tone: String, val text: String)   // tone: warn, good, plain
data class Trend(val a: Double, val b: Double, val t0: LocalDate)
data class Challenge(val goal: Int, val done: Int, val daysLeft: Int)
data class Game(
    val streak: Int, val best: Int, val weeksHit: Int, val km: Double, val longest: Double,
    val longSec: Int, val z2runs: Int, val tests: Int, val bestEst: Double?, val fast: Double?,
    val bigWeek: Double, val gain: Double, val badges: List<Badge>,
    val passes: Int = 0, val questsDone: Int = 0,
)

val TYPE_NAMES = linkedMapOf(
    "easy" to "Easy run", "long" to "Long easy run", "test" to "Zone 2 test",
    "tempo" to "Steady / tempo", "intervals" to "Intervals", "hard" to "Hard effort", "race" to "Race", "baseline" to "Starting point",
)
val DOW = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
val MONTHS_FULL = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
const val LESSON_COUNT = 9

fun fmtPace(secPerKm: Double): String { val s = (if (U.miles) secPerKm * U.MI else secPerKm).roundToInt(); return "${s / 60}:${(s % 60).toString().padStart(2, '0')}" }
fun fmtDur(sec: Int): String {
    val h = sec / 3600; val m = sec % 3600 / 60; val s = sec % 60
    return (if (h > 0) "$h:${m.toString().padStart(2, '0')}" else "$m") + ":" + s.toString().padStart(2, '0')
}
/** "1:11:37" → seconds, "45:30" → seconds, "45" → minutes. Returns 0 when unreadable. */
fun parseDur(t: String): Int {
    val p = t.trim().split(":").map { it.trim().toIntOrNull() ?: return 0 }
    return when (p.size) { 3 -> p[0] * 3600 + p[1] * 60 + p[2]; 2 -> p[0] * 60 + p[1]; 1 -> p[0] * 60; else -> 0 }
}
val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
/** 1110 → "6:30 pm". */
fun fmtClock(min: Int): String {
    val h = min / 60 % 24; val m = min % 60
    return "${if (h % 12 == 0) 12 else h % 12}${if (m > 0) ":" + m.toString().padStart(2, '0') else ""} ${if (h < 12) "am" else "pm"}"
}
fun f1(x: Double) = String.format(Locale.US, "%.1f", x)
fun f2(x: Double) = String.format(Locale.US, "%.2f", x)
fun fmtDay(d: LocalDate) = "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}"
fun mondayOf(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())
fun paceOf(r: Run) = r.durSec / r.distKm

/** Session kinds a runner can swap in, with the name shown in the picker. */
val SWAP_KINDS = linkedMapOf("easy" to "Easy run", "runwalk" to "Run-walk", "long" to "Long easy run", "tempo" to "Tempo", "4x4" to "4 × 4 min intervals", "6x3" to "6 × 3 min intervals", "test" to "Zone 2 test", "talk" to "Talk test")

const val STOP_SIGNS = "Stop and get medical help for chest pain or pressure, fainting or feeling faint, unusual breathlessness, or a racing or irregular heartbeat."
val FEELS = listOf("Gentle", "Easy", "Steady", "Hard", "All out")

fun median(v: List<Double>): Double = quantile(v, .5)
/** Quantile of an already sorted list, with straight-line interpolation between neighbours. */
fun quantile(v: List<Double>, q: Double): Double {
    val x = q * (v.size - 1); val i = floor(x).toInt(); val j = min(v.size - 1, i + 1)
    return v[i] + (v[j] - v[i]) * (x - i)
}

/**
 * Pace and heart rate of the steady part of a run. With kilometre splits that is roughly minutes 8 to 42,
 * so the warm-up and late drift weigh the same on every run; without splits it is the whole run.
 */
fun steady(r: Run): Pair<Double, Int>? {
    if (r.avgHr <= 0 || r.distKm <= 0 || r.durSec <= 0) return null
    var t = 0; var sec = 0; var beats = 0L; var n = 0
    for (sp in r.splits) { val start = t; t += sp.sec; if (start >= 480 && t <= 2520 && sp.hr > 0 && sp.sec > 0) { sec += sp.sec; beats += sp.hr.toLong() * sp.sec; n++ } }
    return if (n >= 2) sec.toDouble() / n to (beats / sec).toInt() else paceOf(r) to r.avgHr
}

/**
 * How much heat slows a run, in percent, from the sum of temperature and dew point in °F.
 * A widely used coaching rule of thumb, not a physiological model.
 */
fun heatPct(tempC: Double, dewC: Double): Double {
    val sum = tempC * 9 / 5 + 32 + dewC * 9 / 5 + 32
    return when { sum <= 100 -> 0.0; sum <= 110 -> .25; sum <= 120 -> .75; sum <= 130 -> 1.5; sum <= 140 -> 2.5; sum <= 150 -> 3.75; sum <= 160 -> 5.25; sum <= 170 -> 7.0; sum <= 180 -> 9.0; else -> 10.0 }
}

/**
 * Per-kilometre splits from distance pieces (start s, end s, metres) and heart-rate samples (s, bpm).
 * Returns nothing when the source only wrote one or two big pieces, because that can't be split honestly.
 */
fun splitsFrom(pieces: List<Triple<Long, Long, Double>>, hr: List<Pair<Long, Int>>): List<Split> {
    if (pieces.size < 3 || pieces.any { it.third > 1500 }) return emptyList()
    val out = ArrayList<Split>(); var done = 0.0; var last = pieces.first().first
    for ((a, b, m) in pieces.sortedBy { it.first }) {
        if (m <= 0) continue
        while (done + m >= (out.size + 1) * 1000.0) {
            val at = a + ((b - a) * (((out.size + 1) * 1000.0 - done) / m)).toLong()
            val beats = hr.filter { it.first in (last + 1)..at }.map { it.second }
            out += Split((at - last).toInt(), if (beats.isEmpty()) 0 else beats.average().roundToInt()); last = at
        }
        done += m
    }
    return out
}

/** All training maths. Pure Kotlin so it can be unit-tested off the phone. */
class Engine(val s: Settings, runsIn: List<Run>, val today: LocalDate, val wx: Wx? = null) {
    val runs: List<Run> = runsIn.sortedBy { it.date }

    /** Lower bounds of Zone 1..5 in bpm. */
    val zb: List<Int> = if (s.zoneMethod == "custom") {
        val c = s.customZ; listOf(c[0] - (c[1] - c[0])) + c
    } else listOf(.5, .6, .7, .8, .9).map {
        (if (s.zoneMethod == "hrr") s.restHr + it * (s.maxHr - s.restHr) else it * s.maxHr).roundToInt()
    }.let { f ->
        // A talk test beats the formula: Zone 3 starts one beat above the highest heart rate at which talking was still easy.
        if (s.talkHr <= 0) f else {
            val z3 = s.talkHr + 1; val z2 = min(f[1], z3 - 10); val z4 = max(f[3], z3 + 8)
            listOf(min(f[0], z2 - 8), z2, z3, z4, max(f[4], z4 + 6))
        }
    }
    val refHr: Int = if (s.refHr > 0) s.refHr else ((zb[1] + zb[2]) / 2.0).roundToInt()

    fun zoneOf(hr: Int): Int { var z = 1; for (i in 1..4) if (hr >= zb[i]) z++; return z }

    /**
     * Pace this run implies at the Zone 2 reference heart rate, scaled along heart-rate reserve.
     * Intervals are left out: their average says little.
     */
    fun estOf(r: Run): Est? {
        if (r.type == "intervals") return null
        val (pace, hr) = steady(r) ?: return null
        val d = refHr - s.restHr
        if (hr - s.restHr < 25 || d < 15) return null
        val p = pace * (hr - s.restHr) / d / (1 + r.heatPct / 100)             // a hot day shouldn't read as lost fitness
        val clean = abs(hr - refHr) <= 10 && (r.id == "_base" || r.durSec >= 1200) && r.type !in setOf("tempo", "hard", "race")
        return Est(r, p, clean)
    }
    fun est(r: Run): Double? = estOf(r)?.pace

    private val basePoint: Run? = if (s.basePace > 0 && s.baseHr > 0 && s.baseDate != null)
        Run("_base", s.baseDate, "baseline", 1.0, s.basePace, s.baseHr) else null

    val ests: List<Est> = (listOfNotNull(basePoint) + runs).mapNotNull { estOf(it) }.sortedBy { it.run.date }
    val eligible: List<Pair<Run, Double>> = ests.map { it.run to it.pace }

    /**
     * Zone 2 pace as of a day. Heart rate at easy effort wanders a few beats from day to day, which is worth
     * 15 to 20 seconds a kilometre, so one run proves little. This takes the median of up to eight clean
     * readings from the last six weeks; until there are three of those it uses the last five of any kind.
     */
    fun reading(asOf: LocalDate = today): Reading? {
        val all = ests.filter { !it.run.date.isAfter(asOf) }
        if (all.isEmpty()) return null
        val clean = all.filter { it.clean && ChronoUnit.DAYS.between(it.run.date, asOf) <= 42 }.takeLast(8)
        val v = (if (clean.size >= 3) clean else all.takeLast(5)).map { it.pace }.sorted()
        return Reading(median(v), quantile(v, .25), quantile(v, .75), v.size, clean.size >= 3)
    }
    private val now: Reading? = reading()
    val current: Double? = now?.pace
    /** The middle half of recent readings, once there are enough to say. */
    val band: Pair<Double, Double>? = now?.takeIf { it.n >= 4 && it.hi - it.lo >= 2 }?.let { it.lo to it.hi }
    val baseline: Double? = eligible.firstOrNull()?.second

    /** The weekday that carries the harder session, when the plan has one. */
    private val qualityDay: Int? = s.days.distinct().sorted().let { days ->
        if (!(s.quality && !s.caution && days.size >= 4)) null else {
            val longDay = if (s.longDay in days) s.longDay else days.last()
            days.filter { it != longDay }.sortedWith(compareByDescending<Int> { d -> abs(d - longDay).let { min(it, 7 - it) } }.thenBy { it }).first()
        }
    }
    /** Was this run meant to be easy? Imported runs are labelled by heart rate, so for those the weekday decides. */
    private fun meantEasy(r: Run) = r.type in setOf("easy", "long") || (r.source == "health" && r.type in setOf("tempo", "hard") && r.date.dayOfWeek.value != qualityDay)
    private fun overCeiling(r: Run) = if (r.zoneSec.size == 5 && r.zoneSec.sum() > 0) r.zoneSec.drop(2).sum() * 10 >= r.zoneSec.sum() * 4 else r.avgHr >= zb[2]
    private val easyRecent: List<Run> = runs.filter { meantEasy(it) && it.avgHr > 0 && it.heatPct < 1.5 && it.durSec >= 900 && ChronoUnit.DAYS.between(it.date, today) in 0..27 }.takeLast(5)

    /**
     * Are easy days running too hot? Only cool-weather runs count, and a run counts as over when 40% of it
     * was above the ceiling. 1 = two of the last three: slow down first. 2 = four of the last five: run-walk.
     */
    val easyOver: Int = when {
        easyRecent.size >= 5 && easyRecent.count { overCeiling(it) } >= 4 -> 2
        easyRecent.size >= 3 && easyRecent.takeLast(3).count { overCeiling(it) } >= 2 -> 1
        else -> 0
    }

    /** Heart rate decides when there is one (and it can be trusted); then the runner's own rating; then the label. */
    fun isEasy(r: Run) = when {
        r.avgHr > 0 && !s.hrMeds -> zoneOf(r.avgHr) <= 2
        r.feel > 0 -> r.feel <= 3
        else -> r.type in setOf("easy", "long", "test")
    }

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

    /**
     * Builds one session of the given kind, sized for the week it sits in. Every heart-rate target comes with
     * an effort cue, because a sensor can read wrong; on heart-rate medication the cue is the whole instruction.
     */
    private fun build(kind: String, easy: Int, long: Int): Session {
        val z2 = "${zb[1]}–${zb[2] - 1} bpm"; val z3 = "${zb[2]}–${zb[3] - 1} bpm"; val z4 = "${zb[3]}–${zb[4] - 1} bpm"
        val feel = s.hrMeds
        val easyCue = if (feel) "Go by feel, not heart rate: slow enough to speak in full sentences the whole way." else "Keep HR $z2. You should be able to speak full sentences."
        val hardCue = if (feel) "hard but repeatable, a few words at a time" else "in Zone 4 ($z4): hard but repeatable, a few words at a time"
        return when (kind) {
            "long" -> Session("long", "Long easy run", long, if (feel) "Full-sentence effort the whole way. Walk for a minute whenever talking gets hard." else "Stay under ${zb[2]} bpm the whole way, at an effort where you can talk in full sentences. Walk for a minute whenever HR climbs past it.")
            "test" -> Session("test", "Zone 2 test", 40, "10 min warm-up, then 30 min holding $refHr bpm on a flat route. It should feel easy enough to chat. It is logged as a Zone 2 test.")
            "strides" -> Session("easy", "Easy run + strides", easy, "$easyCue Then finish with 6 × 20 s relaxed fast strides.", "strides")
            "4x4" -> Session("intervals", "4 × 4 min hard", 50, "10 min easy, 4 × 4 min $hardCue. 3 min jog between, 10 min easy.", "4x4")
            "tempo" -> Session("tempo", "Tempo 20 min", 40, "10 min easy, 20 min steady ${if (feel) "" else "in Zone 3 ($z3), "}comfortably hard: short sentences only. 10 min easy.")
            "6x3" -> Session("intervals", "6 × 3 min hard", 50, "10 min easy, 6 × 3 min $hardCue. 2 min jog between, 10 min easy.", "6x3")
            "tune" -> Session("intervals", "Race tune-up", 35, "10 min easy, 3 × 3 min $hardCue. 2 min jog between, 10 min easy. Short on purpose: it keeps the legs sharp while the week stays light.", "tune")
            "talk" -> Session("tempo", "Talk test", 35, "10 min easy, then six 3-minute stages, each a little faster than the last. Near the end of every stage say a full sentence out loud, about 30 words. Note your heart rate in the last stage where talking was still comfortable: that is the top of your Zone 2.", "talk")
            "runwalk" -> Session("easy", "Easy run-walk", easy, "Jog 4 min, walk 1 min${if (feel) ", at an effort where you can chat" else ", and keep HR $z2"}. Run-walk is a normal way to train in Zone 2, not a step back.", "runwalk")
            "race" -> Session("race", s.raceName.ifBlank { "Race day" }, ((racePrediction() ?: (s.raceKm * 390).roundToInt()) / 60.0).roundToInt(),
                "Start slower than feels right for the first third, settle, and save the push for the end. Good luck.")
            else -> Session("easy", "Easy run", easy, easyCue)
        }
    }

    /** How many weeks before a race the plan eases off: one for 5 to 10 km, two for a half, three for a marathon. */
    val taperWeeks: Int = when { s.raceKm <= 12 -> 1; s.raceKm <= 30 -> 2; else -> 3 }

    /**
     * The longest a single run should be on a day, in minutes: about 10% past the longest run of the 30 days
     * before it (and never less than 5 minutes more, or under 30). A run that jumps further than that is the
     * clearest injury signal in the data; week-to-week totals are not. Null when the cap doesn't apply:
     * no history yet, or a day too far ahead to know. A negative value means a comeback after a month or more off.
     */
    fun runCap(d: LocalDate): Int? {
        if (d.isAfter(today.plusDays(7)) || runs.none { it.date.isBefore(d) }) return null
        val longest = runs.filter { it.date.isBefore(d) && ChronoUnit.DAYS.between(it.date, d) <= 30 }.maxOfOrNull { it.durSec } ?: return -1
        return max(30, (max(longest * 1.1, longest + 300.0) / 300).toInt() * 5)
    }

    /**
     * The week's sessions. Three steps build about 8% each, the fourth eases off and re-tests.
     * A race date tapers the last weeks; sessions swapped by hand always win; no easy run outgrows the cap.
     */
    fun weekPlan(mon: LocalDate): WeekPlan {
        val wi = planIndex(mon)
        val pos = wi % 4; val block = wi / 4
        val race = s.raceDate?.takeIf { s.raceKm > 0 && !it.isBefore(mon) }
        val weeksToRace = race?.let { (ChronoUnit.DAYS.between(mon, mondayOf(it)) / 7).toInt() }
        val inTaper = weeksToRace != null && weeksToRace < taperWeeks
        // Volume comes down in steps to roughly half; the number of runs and their intensity stay.
        val taper = if (!inTaper) 1.0 else when (taperWeeks) { 1 -> listOf(.6); 2 -> listOf(.55, .75); else -> listOf(.5, .65, .8) }[weeksToRace!!]
        val f = min(1.8, 1.12.pow(block)) * listOf(1.0, 1.08, 1.16, .85)[pos] * taper
        val r5 = { x: Double -> max(20, (x / 5).roundToInt() * 5) }
        val longCap = if (race == null) 150 else when { s.raceKm <= 5 -> 60; s.raceKm <= 10 -> 90; s.raceKm <= 21.1 -> 130; else -> 180 }
        val easy = min(75, r5(s.easyMin * f)); val long = min(longCap, r5(s.longMin * f))
        val days = s.days.distinct().sorted()
        val ses = HashMap<Int, Session>()
        val q = qualityDay
        if (days.isNotEmpty()) {
            val longDay = if (s.longDay in days) s.longDay else days.last()
            val others = days.filter { it != longDay }
            val testDay = if (pos == 3) others.firstOrNull { it != q } else null
            for (d in days) ses[d] = when (d) {
                longDay -> build("long", easy, long)
                testDay -> build("test", easy, long)
                q -> build(if (inTaper) "tune" else if (pos == 3) "strides" else listOf("4x4", "tempo", "6x3")[wi % 3], easy, long)
                else -> when {
                    easyOver == 2 || s.runWalk -> build("runwalk", easy, long).let { if (easyOver == 2) it.copy(detail = it.detail + " Your recent easy days spent a lot of time above ${zb[2] - 1} bpm, so this week builds the habit of staying low.") else it }
                    easyOver == 1 -> build("easy", easy, long).let { it.copy(detail = it.detail + " Your last few easy days ran above ${zb[2] - 1} bpm: slow down first, and walk the hills." + if (s.talkHr == 0 && s.zoneMethod != "custom") " If that feels like a crawl, the ceiling itself may be off. A talk test checks it." else "") }
                    else -> build("easy", easy, long)
                }
            }
        }
        if (weeksToRace == 0 && race != null) {                       // race week: short easy runs, one short sharpener, the race, then rest
            val rd = race.dayOfWeek.value
            for (d in ses.keys.toList()) if (d < rd) ses[d] = build(if (d == q && rd - d >= 3) "tune" else "easy", easy, long) else ses.remove(d)
            ses[rd] = build("race", easy, long)
        }
        for (d in 1..7) s.overrides[mon.plusDays(d - 1L)]?.let { if (it == "skip") ses.remove(d) else ses[d] = build(it, easy, long) }
        for ((d, x) in ses.toMap()) if (x.type in setOf("easy", "long")) runCap(mon.plusDays(d - 1L))?.let { cap ->
            if (cap < 0) { val m = max(20, (x.min * .6 / 5).roundToInt() * 5); if (m < x.min) ses[d] = x.copy(min = m, detail = x.detail + " Kept short on purpose: it's your first month back after a break.") }
            else if (x.min > cap) ses[d] = x.copy(min = cap, detail = x.detail + " Kept to $cap min: about 10% more than your longest run of the last 30 days. Bigger jumps than that are where injuries come from.")
        }
        val sorted = ses.toSortedMap()
        val phase = when { weeksToRace == 0 -> "Race week"; inTaper -> "Taper"; else -> listOf("Build 1", "Build 2", "Build 3", "Recovery + test")[pos] }
        return WeekPlan(wi, pos, sorted, sorted.values.sumOf { it.min }, phase)
    }

    /** A run with no plan behind it: no blocks, no targets, no Zone 2 cue. It is recorded and read afterwards like any other. */
    fun freeRun() = Session("easy", "Free run", 0, "No blocks and no targets. I'll record time, distance, pace and heart rate, and work out afterwards what kind of run it was.", "free")

    /** The blocks of a session, for the guided run. */
    fun steps(x: Session): List<Step> {
        val m = x.min * 60
        fun reps(n: Int, hard: Int, easy: Int) = (1..n).flatMap { i -> listOf(Step("Hard, rep $i of $n", hard, 4, 4, "hard"), Step("Easy jog", easy, 1, 2, "easy")) }
        return when (x.kind) {
            "4x4" -> listOf(Step("Warm up", 600, 1, 2, "warm")) + reps(4, 240, 180) + Step("Cool down", 600, 1, 2, "cool")
            "6x3" -> listOf(Step("Warm up", 600, 1, 2, "warm")) + reps(6, 180, 120) + Step("Cool down", 600, 1, 2, "cool")
            "tempo" -> listOf(Step("Warm up", 600, 1, 2, "warm"), Step("Tempo, Zone 3", 1200, 3, 3, "hard"), Step("Cool down", 600, 1, 2, "cool"))
            "test" -> listOf(Step("Warm up", 600, 1, 2, "warm"), Step("Hold $refHr bpm", 1800, 2, 2, "hold"))
            "tune" -> listOf(Step("Warm up", 600, 1, 2, "warm")) + reps(3, 180, 120) + Step("Cool down", 600, 1, 2, "cool")
            "talk" -> listOf(Step("Warm up", 600, 1, 2, "warm")) + listOf(1 to 2, 2 to 2, 2 to 3, 3 to 3, 3 to 3, 3 to 4).mapIndexed { i, (lo, hi) -> Step("Stage ${i + 1} of 6. ${if (i == 0) "Easy jog" else "A little faster"}. Say a sentence out loud", 180, lo, hi, "hold") } + Step("Cool down", 300, 1, 2, "cool")
            "runwalk" -> (0 until max(1, m / 300)).flatMap { listOf(Step("Jog", 240, 1, 2, "easy"), Step("Walk", 60, 1, 1, "walk")) }
            "strides" -> listOf(Step("Easy running", max(300, m - 480), 1, 2, "easy")) + (1..6).flatMap { i -> listOf(Step("Stride $i of 6", 20, 3, 5, "hard"), Step("Easy", 60, 1, 2, "easy")) }
            "race" -> listOf(Step("Race", 0, 1, 5, "race"))
            "free" -> listOf(Step("Free run", 0, 1, 5, "free"))
            else -> listOf(Step("Warm up", 300, 1, 2, "warm"), Step(if (x.type == "long") "Long easy running" else "Easy running", max(300, m - 300), 1, 2, "easy"))
        }
    }

    val raceDaysLeft: Int? = s.raceDate?.takeIf { s.raceKm > 0 }?.let { ChronoUnit.DAYS.between(today, it).toInt() }?.takeIf { it >= 0 }

    /**
     * Race forecast from the best sustained hard effort of the last eight weeks: a whole hard run, or the best
     * 3, 5 or 10 km inside one, taken from the splits. Interval sessions are left out because their average
     * includes the recoveries. Riegel's exponent of 1.06 holds up to the half marathon; beyond it most
     * recreational runners slow more than that, more so on low mileage, so the exponent rises.
     */
    fun raceCast(): RaceCast? {
        if (s.raceKm <= 0) return null
        data class Effort(val km: Double, val sec: Double, val date: LocalDate, val allOut: Boolean)
        val efforts = ArrayList<Effort>()
        for (r in runs) {
            if (ChronoUnit.DAYS.between(r.date, today) !in 0..56 || r.type == "intervals") continue
            val z = if (r.avgHr > 0) zoneOf(r.avgHr) else 0
            if (r.distKm >= 3 && (r.type in setOf("race", "hard", "tempo") || z >= 3)) efforts += Effort(r.distKm, r.durSec.toDouble(), r.date, r.type == "race" || z >= 4)
            for (n in listOf(3, 5, 10)) if (r.splits.size > n) {
                val best = (0..r.splits.size - n).map { r.splits.subList(it, it + n) }.minBy { w -> w.sumOf { it.sec } }
                val hr = best.filter { it.hr > 0 }.map { it.hr }.takeIf { it.isNotEmpty() }?.average()?.roundToInt() ?: 0
                val wz = if (hr > 0) zoneOf(hr) else 0
                if (wz >= 3 || (hr == 0 && r.type in setOf("race", "hard", "tempo"))) efforts += Effort(n.toDouble(), best.sumOf { it.sec }.toDouble(), r.date, wz >= 4)
            }
        }
        if (efforts.isEmpty()) return null
        val weekKm = weeks.dropLast(1).takeLast(6).map { it.span.km }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
        val long = s.raceKm > 22
        var k = when { !long -> 1.06; weekKm < 40 -> 1.10; else -> 1.08 }
        // Two all-out efforts of clearly different length show this runner's own fade.
        val hard = efforts.filter { it.allOut }
        val short = hard.minByOrNull { it.km }; val far = hard.filter { short != null && it.km >= short.km * 1.8 }.minByOrNull { it.sec / it.km }
        var own = false
        if (short != null && far != null) {
            val fastShort = hard.filter { it.km <= short.km * 1.2 }.minBy { it.sec / it.km }
            val fit = kotlin.math.ln(far.sec / fastShort.sec) / kotlin.math.ln(far.km / fastShort.km)
            if (fit in 1.02..1.2) { k = max(k, fit.coerceIn(1.04, 1.15)); own = true }
        }
        val pick = efforts.minBy { it.sec * (s.raceKm / it.km).pow(k) }
        val t = pick.sec * (s.raceKm / pick.km).pow(k)
        val warn = when {
            long && weekKm < 40 -> "Marathon forecasts from shorter runs come out optimistic on less than ${U.dist(40.0, 0)} a week. Treat the slow end as the likely one."
            pick.km < s.raceKm / 4 -> "This comes from a much shorter effort, so the range is wide."
            else -> ""
        }
        return RaceCast(t.roundToInt(), (t * .97).roundToInt(), (t * if (long) 1.07 else if (pick.km < s.raceKm / 4) 1.06 else 1.04).roundToInt(),
            "your best ${U.dist(pick.km, if (pick.km % 1.0 == 0.0) 0 else 1)} on ${fmtDay(pick.date)}" + if (own) ", and how you fade over distance" else "", warn)
    }
    fun racePrediction(): Int? = raceCast()?.sec

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
        var km = 0.0; var longest = 0.0; var longSec = 0; var z2 = 0; var tests = 0
        var hour = false; var fast: Double? = null
        for (r in runs) {
            val mins = r.durSec / 60.0
            if (r.type == "test") tests++
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
            val q = quests(w.mon).count { it.done }; questsDone += q; if (q == 3) fullQuestWeek = true
            when (w.state) {
                WeekState.HIT -> { streak++; weeksHit++; best = max(best, streak) }
                WeekState.MISS -> streak = 0
                else -> Unit                      // a rest pass, a break or the week in progress leaves the streak alone
            }
        }
        val c = current; val b0 = baseline
        val gain = if (b0 != null && c != null && eligible.size >= 3) max(0.0, b0 - c) else 0.0   // two points are noise, not progress
        val badges = listOf(
            Badge("first", "1", 1, "First steps", "Log your first run.", runs.isNotEmpty()),
            Badge("z2", "Z2", 1, "True easy", "Finish a run of 20+ minutes with average HR in Zone 2.", z2 >= 1),
            Badge("week", "1W", 1, "On plan", "Hit your weekly session goal.", weeksHit >= 1),
            Badge("test", "TEST", 1, "Benchmark", "Complete a Zone 2 test.", tests >= 1),
            Badge("l10", "10K", 1, "Ten", "Run 10 km in one go.", longest >= 10, Progress(longest, 10.0, "km")),
            Badge("pol", "80/20", 2, "Polarised", "A week of 3+ runs with at least 80% easy time.", pol),
            Badge("hour", "60′", 2, "Easy hour", "Hold Zone 2 for 60 minutes in one run.", hour),
            Badge("r1", "−15s", 2, "First rung", "Zone 2 pace ${U.s(15)} s${U.per} faster than where you started.", gain >= 15, Progress(gain, 15.0, "s")),
            Badge("s4", "4W", 2, "Habit", "Keep a 4-week streak.", best >= 4, Progress(best.toDouble(), 4.0, "weeks")),
            Badge("z10", "Z2×10", 2, "Discipline", "Keep 10 runs in Zone 2.", z2 >= 10, Progress(z2.toDouble(), 10.0, "runs")),
            Badge("k100", "100", 2, "Century", "Log 100 km in total.", km >= 100, Progress(km, 100.0, "km")),
            Badge("l15", "15K", 2, "Fifteen", "Run 15 km in one go.", longest >= 15, Progress(longest, 15.0, "km")),
            Badge("r2", "−30s", 3, "Second rung", "Zone 2 pace ${U.s(30)} s${U.per} faster than where you started.", gain >= 30, Progress(gain, 30.0, "s")),
            Badge("s8", "8W", 3, "Locked in", "Keep an 8-week streak.", best >= 8, Progress(best.toDouble(), 8.0, "weeks")),
            Badge("l21", "21K", 3, "Half marathon", "Run 21.1 km in one go.", longest >= 21.1, Progress(longest, 21.1, "km")),
            Badge("r4", "−60s", 3, "Minute faster", "Zone 2 pace ${U.s(60)} s${U.per} faster than where you started.", gain >= 60, Progress(gain, 60.0, "s")),
            Badge("k500", "500", 3, "Five hundred", "Log 500 km in total.", km >= 500, Progress(km, 500.0, "km")),
            Badge("s12", "12W", 3, "Unbreakable", "Keep a 12-week streak.", best >= 12, Progress(best.toDouble(), 12.0, "weeks")),
            Badge("quests", "3/3", 2, "Clean sweep", "Finish all three weekly quests in one week.", fullQuestWeek),
            Badge("learn", "A+", 2, "Student", "Finish every lesson.", s.lessonsDone.distinct().size >= LESSON_COUNT, Progress(s.lessonsDone.distinct().size.toDouble(), LESSON_COUNT.toDouble(), "lessons")),
            Badge("goal", fmtPace(s.targetPace.toDouble()), 3, "Arrived", "Hold your target pace at Zone 2 heart rate.", c != null && c <= s.targetPace),
        )
        Game(streak, best, weeksHit, km, longest, longSec, z2, tests, bestEst, fast, bigWeek, gain, badges, weekCalc.second, questsDone)
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
        // One run much longer than anything in the month before it is the jump that matters; weekly totals are not.
        if (since <= 3 && last.distKm >= 4) runs.filter { it.date.isBefore(last.date) && ChronoUnit.DAYS.between(it.date, last.date) <= 30 }.maxOfOrNull { it.distKm }?.let { prev ->
            if (last.distKm > prev * 1.1) out += Note("warn", "Your last run was ${((last.distKm / prev - 1) * 100).roundToInt()}% longer than anything in the 30 days before it (${U.dist(last.distKm, 1)} vs ${U.dist(prev, 1)}). Jumps past 10% are the clearest injury signal in running data. Keep the next few runs short and easy.")
        }
        // Easy heart rate but a hard feel points at tiredness, not at lost fitness.
        val rated = runs.filter { it.feel > 0 && it.avgHr > 0 && zoneOf(it.avgHr) <= 2 && ChronoUnit.DAYS.between(it.date, today) in 0..13 }.takeLast(3)
        if (rated.size >= 2 && rated.count { it.feel >= 4 } >= 2)
            out += Note("warn", "Your easy runs have felt hard lately even though your heart rate stayed low. That usually means tiredness, heat or short sleep. Take an extra rest day before the next hard session.")
        paceChange()?.let { if (it.real && it.delta >= 5 && it.since == "4 weeks ago") out += Note("good", "Your Zone 2 pace is ${U.s(it.delta.roundToInt())} s${U.per} faster than four weeks ago, and that is more than the usual wobble between runs. The base work is paying off.") }
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
        val mon = mondayOf(today); val h = t.hour; val nowMin = h * 60 + t.minute
        val u = usualRunMin
        // With a learned run time, "ready" is the two hours before it and the hour after; otherwise it is the evening.
        val part = when {
            h >= 21 || h < 4 -> "night"
            u != null && nowMin in (u - 120)..(u + 60) -> "ready"
            u != null && nowMin > u + 60 -> "after"
            u != null -> if (h < 11) "morning" else "midday"
            h < 11 -> "morning"; h < 16 -> "midday"; else -> "ready"
        }
        val evening = h >= 16 || h < 4
        val habit = u?.let { "You usually run around ${fmtClock(it)}. " } ?: ""
        // Weather for the hour the run is likely to happen: the usual time if it is still ahead, otherwise an hour from now.
        val runMin = if (u != null && u > nowMin) u else min(nowMin + 60, 23 * 60)
        val air = wx?.at(today, runMin)
        val heat = air?.let { heatPct(it.first, it.second) } ?: 0.0
        val heatLine = if (air != null && heat >= 1.5) " About ${air.first.roundToInt()}°C${if (air.second >= 20) " and humid" else ""} around ${fmtClock(runMin / 60 * 60)}: expect roughly ${if (heat % 1.0 == 0.0) heat.toInt().toString() else f1(heat)}% slower. The heart rate cap doesn't change." else ""
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
                val body = (if (easyType && z >= 3) "Next easy run, slow down or walk until you stay under ${zb[2]} bpm. " else "") + (if (evening) tomorrowLine else "Nothing more to do today.")
                Lead("done", if (z in 1..2) "proud" else if (z >= 4) "puffed" else "calm", "Today's run", title, body, run = ran)
            }
            ses?.type == "race" -> Lead("race", "proud", "Race day", ses.title, ses.detail + (racePrediction()?.let { " Your recent running points to about ${fmtDur(it)}." } ?: ""), ses, today, cta = "Log the race")
            onBreak(mon) -> Lead("break", "sleepy", "Week off", "Rest up. I'll be here.", "Your streak is safe and the plan picks up where you left it.")
            left > 0 && left >= daysLeft -> Lead("risk", "puffed", "Streak check",
                if (game.streak > 0) "Your ${game.streak}-week streak needs a run today" else "This week's goal needs a run today",
                "$left more run${if (left == 1) "" else "s"} with $daysLeft day${if (daysLeft == 1) "" else "s"} left. An easy 20 minutes counts.", ses, today, cta = "Log a run")
            ses != null && part == "night" -> Lead("late", "sleepy", "Getting late", "Short on time? 20 easy minutes still counts.",
                "A short run keeps the habit alive. Or sleep on it: you need $left more run${if (left == 1) "" else "s"} this week and have ${daysLeft - 1} day${if (daysLeft - 1 == 1) "" else "s"} after today.", ses, today, cta = "Log a short run")
            ses != null -> Lead("plan", "calm", when (part) { "morning" -> "Today's run"; "midday" -> "Later today"; "after" -> "Later than usual"; else -> "Ready when you are" },
                "${ses.title} · ${ses.min} min", when (part) { "ready" -> habit; "after" -> habit + "Any time today still counts. "; else -> "" } + ses.detail + heatLine, ses, today, cta = "Log this run", heatPct = heat)
            today.dayOfWeek.value == 1 && h < 12 && review() != null -> review()!!.let {
                Lead("review", if (it.state == WeekState.HIT) "proud" else "calm", "Last week", "${it.n} of ${it.planned} runs · ${U.dist(it.km, 1)}", it.line + " " + (if (tomorrow != null) tomorrowLine else ""))
            }
            evening -> Lead("rest", "calm", "Rest day", tomorrow?.let { "Tomorrow: ${it.title} · ${it.min} min" } ?: "Two rest days in a row. Enjoy them.",
                "Nothing to do tonight. Sleep is training too.")
            else -> Lead("rest", "sleepy", "Rest day", "No run today. I'm recovering.", "Rest is when the training sinks in. A short strength session or a two-minute lesson is plenty.")
        }
    }

    /**
     * When this runner usually starts, learned from the last eight weeks. Needs three timed runs,
     * and most of them within 90 minutes of each other, before it says anything.
     */
    val usualRunMin: Int? = runs.filter { it.startMin >= 0 && ChronoUnit.DAYS.between(it.date, today) in 0..56 }.map { it.startMin }
        .takeIf { it.size >= 3 }?.let { all -> val med = all.sorted()[all.size / 2]; if (all.count { abs(it - med) <= 90 } * 10 >= all.size * 6) med / 15 * 15 else null }

    /** Changes worth offering: a plan day that matches where the runs actually happen, and a reminder timed to them. */
    fun suggestions(): List<Suggestion> {
        val out = ArrayList<Suggestion>()
        fun live(key: String) = s.snoozed[key]?.let { ChronoUnit.DAYS.between(it, today) < 28 } != true
        val recent = runs.filter { ChronoUnit.DAYS.between(it.date, today) in 1..28 }
        fun weeksRunOn(d: Int) = recent.filter { it.date.dayOfWeek.value == d }.map { mondayOf(it.date) }.distinct().size
        val planDays = s.days.distinct()
        if (ChronoUnit.DAYS.between(planStart, today) >= 21) {
            val to = (1..7).filter { it !in planDays && weeksRunOn(it) >= 3 }.maxByOrNull { weeksRunOn(it) }
            val from = planDays.filter { weeksRunOn(it) == 0 }.minByOrNull { d -> to?.let { abs(d - it).let { g -> min(g, 7 - g) } } ?: 0 }
            if (to != null && from != null && live("days:$from>$to")) out += Suggestion("days:$from>$to", "Move ${DAY_NAMES[from - 1]}'s run to ${DAY_NAMES[to - 1]}?",
                "You've run on ${DAY_NAMES[to - 1]} in ${weeksRunOn(to)} of the last 4 weeks and not once on ${DAY_NAMES[from - 1]}. The plan can match what you actually do.", "Move it", MoveDay(from, to))
        }
        if (s.zoneMethod != "custom" && !s.hrMeds) {
            // Offered once a talk test has been run: the number only the runner knows.
            if (s.talkHr == 0 && s.overrides.any { (d, k) -> k == "talk" && !d.isAfter(today) && runsOn(d).isNotEmpty() } && live("talkhr"))
                out += Suggestion("talkhr", "What was your talking ceiling?", "The highest heart rate in your talk test at which a full sentence still came out comfortably. It becomes the top of your Zone 2.", "Save", EnterTalk)
            // A formula's max is a guess. A heart rate actually reached, twice, or once in a run that was hard throughout, is not.
            val seen = runs.filter { ChronoUnit.DAYS.between(it.date, today) in 0..365 && it.maxHr in (s.maxHr + 3)..215 && it.durSec >= 600 }.sortedByDescending { it.maxHr }
            val top = seen.getOrNull(1)?.maxHr ?: seen.firstOrNull()?.takeIf { it.avgHr >= it.maxHr * .8 }?.maxHr
            if (top != null && live("max:$top")) out += Suggestion("max:$top", "Raise your max heart rate to $top?", "You've reached $top bpm on a run, above the ${s.maxHr} your zones are built on. Using the real number moves every zone up a little.", "Use $top", SetMax(top))
            if (s.hcRest in 35..95 && abs(s.hcRest - s.restHr) >= 4 && s.zoneMethod == "hrr" && live("rest:${s.hcRest}"))
                out += Suggestion("rest:${s.hcRest}", "Update your resting heart rate to ${s.hcRest}?", "Health Connect has it at ${s.hcRest} bpm over the last week; your zones are built on ${s.restHr}. Resting heart rate falls as fitness improves, and the zones should follow.", "Use ${s.hcRest}", SetRest(s.hcRest))
            if (s.talkHr == 0 && runs.size >= 3 && s.overrides.none { (d, k) -> k == "talk" && !d.isBefore(today) } && live("talk"))
                out += Suggestion("talk", "Check your Zone 2 with a talk test?", "Your zones come from a formula, and for many people formulas miss by ten beats or more. A 35-minute talk test finds your own ceiling.", "Add it to my plan", TalkTest)
        }
        if (s.runWalk && easyRecent.size >= 3 && easyRecent.takeLast(3).none { overCeiling(it) } && easyRecent.takeLast(3).all { it.durSec >= 1200 } && live("runwalk"))
            out += Suggestion("runwalk", "Ready to drop the walk breaks?", "Your last three easy runs stayed in Zone 2. Easy days can become continuous runs; walk whenever your heart rate climbs.", "Plan easy runs", RunWalk(false))
        usualRunMin?.let { u ->
            val want = max(0, u - 60)
            if (!s.remindOn) { if (live("remind:on")) out += Suggestion("remind:on", "Want a nudge at ${fmtClock(want)}?", "You usually run around ${fmtClock(u)}. One reminder an hour before, only on days with something to say.", "Remind me", Remind(want)) }
            else if (abs(s.remindMin - want) >= 45 && live("remind:$want")) out += Suggestion("remind:$want", "Move your reminder to ${fmtClock(want)}?", "It's set for ${fmtClock(s.remindMin)}, but you usually run around ${fmtClock(u)}.", "Move it", Remind(want))
        }
        return out
    }

    /**
     * How Zone 2 pace has moved: against four weeks ago when there is history, otherwise against the start.
     * It only counts as real when the new median sits outside the middle half of the earlier readings.
     */
    fun paceChange(): PaceChange? {
        val n = now ?: return null
        val then = reading(today.minusDays(28))
        if (then != null && ests.any { it.run.date.isAfter(today.minusDays(28)) }) {
            val d = then.pace - n.pace
            return PaceChange(d, "4 weeks ago", n.n >= 3 && then.n >= 3 && abs(d) >= 3 && (n.pace < then.lo || n.pace > then.hi))
        }
        if (ests.size >= 3) { val d = baseline!! - n.pace; return PaceChange(d, "when you started", abs(d) >= max(5.0, (n.hi - n.lo) / 2)) }
        return null
    }

    /** How much to trust the Zone 2 estimate. Steady easy runs and tests read it directly; hard runs only hint at it. */
    fun confidence(): Confidence? {
        val n = now ?: return null
        val test = runs.any { it.type == "test" && ChronoUnit.DAYS.between(it.date, today) in 0..35 }
        val clean = ests.count { it.clean && ChronoUnit.DAYS.between(it.run.date, today) <= 42 }
        return when {
            n.clean && (n.n >= 5 || test) -> Confidence(2, "Solid estimate", "The middle of ${n.n} steady easy runs.")
            n.clean || clean >= 1 -> Confidence(1, "Fair estimate", "Sharpens with a few more steady easy runs or a Zone 2 test.")
            else -> Confidence(0, "Rough estimate", "Worked out from harder runs. Three steady runs near $refHr bpm will firm it up.")
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
