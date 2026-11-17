package app.zone2running

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** What the reward screen shows after new runs land. */
data class Celebration(
    val title: String, val game: Game, val newBadges: List<Badge>, val records: List<String>,
    val message: String, val summary: String, val estLine: String?, val streakLine: String, val streakOn: Boolean,
    val share: String, val celebrate: Boolean,
    val run: Run, val zone: Int, val current: Double?, val target: Int, val streakMilestone: Int,
)

fun buildCelebration(before: Engine, after: Engine, fresh: List<Run>): Celebration {
    val r = fresh.maxBy { it.date }
    val g0 = before.game; val g = after.game
    val nb = g.badges.filter { b -> b.got && g0.badges.none { it.id == b.id && it.got } }
    val recs = ArrayList<String>()
    if (g0.longest > 0 && g.longest > g0.longest) recs += "Longest run: ${U.dist(g.longest)}"
    if (g0.bestEst != null && g.bestEst != null && g.bestEst < g0.bestEst - .5) recs += "Best Zone 2 estimate: ${fmtPace(g.bestEst)} ${U.per}"
    if (g0.fast != null && g.fast != null && g.fast < g0.fast - .5) recs += "Fastest run: ${fmtPace(g.fast)} ${U.per}"
    val z = if (r.avgHr > 0) after.zoneOf(r.avgHr) else 0
    val easyType = r.type in setOf("easy", "long", "test")
    val msg = when {
        easyType && z in 1..2 -> "Kept in Zone 2. This is the kind of run that moves your easy pace."
        easyType && z >= 3 -> "Average HR landed in Zone $z. On the next easy run, slow down or walk until it stays under ${after.zb[2]} bpm."
        r.type == "test" -> "Benchmark recorded."
        else -> "Hard work banked. Keep the next run properly easy."
    }
    val summary = "${U.dist(r.distKm)} · ${fmtDur(r.durSec)} · ${fmtPace(paceOf(r))} ${U.per}" + if (r.avgHr > 0) " · ${r.avgHr} bpm (Z$z)" else ""
    val w = after.weekHit(mondayOf(r.date))
    val streakLine = if (w.hit) "Weekly goal hit. Streak: ${g.streak} week${if (g.streak == 1) "" else "s"}." else "${w.n} of ${w.need} runs toward this week's streak."
    val title = when {
        fresh.size > 1 -> "${fresh.size} runs imported"
        nb.isNotEmpty() -> "Badge unlocked"
        recs.isNotEmpty() -> "New personal record"
        else -> "Good work"
    }
    val share = buildString {
        append("Zone 2 Running · ${DOW[r.date.dayOfWeek.value - 1]} ${fmtDay(r.date)}\n$summary\n")
        after.current?.let { append("Zone 2 pace now ${fmtPace(it)} ${U.per}, target ${fmtPace(after.s.targetPace.toDouble())}\n") }
        append("${g.streak} week streak")
        if (nb.isNotEmpty()) append("\nUnlocked: " + nb.joinToString { it.name })
    }
    return Celebration(title, g, nb, recs, msg, summary, after.est(r)?.let { "Zone 2 estimate from this run: ${fmtPace(it)} ${U.per}" },
        streakLine, g.streak > 0, share, nb.isNotEmpty() || recs.isNotEmpty() || g.streak > g0.streak,
        r, z, after.current, after.s.targetPace, if (g.streak > g0.streak && g.streak in setOf(4, 8, 12, 26, 52)) g.streak else 0)
}

class AppVM(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    val health = Health(app)
    val weather = WeatherCache(app)
    /** Bumped when a new forecast arrives, so the home screen recomputes. */
    var wxTick by mutableStateOf(0); private set
    var cityBusy by mutableStateOf(false); private set
    var update by mutableStateOf<Pair<String, String>?>(null); private set

    var data by mutableStateOf(store.load()); private set
    var celebration by mutableStateOf<Celebration?>(null)
    var toast by mutableStateOf<String?>(null)
    var healthGranted by mutableStateOf(false); private set
    var healthExtras by mutableStateOf(false); private set
    var importing by mutableStateOf(false); private set
    /** Time of day the home screen was last opened; it decides what leads. */
    var clock by mutableStateOf(java.time.LocalTime.now())

    val settings get() = data.settings
    fun engine() = Engine(data.settings, data.runs, LocalDate.now(), if (data.settings.weatherOn) weather else null)

    init { U.miles = data.settings.miles }

    private fun commit(d: AppData) {
        var nd = d
        store.save(nd)
        try {
            store.autoBackup(nd, LocalDate.now())?.let { nd = nd.copy(settings = nd.settings.copy(lastBackup = it)); store.save(nd) }
        } catch (e: Exception) { /* a failed backup must never block saving */ }
        U.miles = nd.settings.miles
        data = nd
    }

    fun updateSettings(f: (Settings) -> Settings) = commit(data.copy(settings = f(data.settings)))

    /** Tags a run with how much the weather slowed it, when the forecast covers the hour it started. */
    private fun withHeat(r: Run): Run {
        if (!settings.weatherOn || r.heatPct != 0.0 || r.startMin < 0) return r
        return weather.at(r.date, r.startMin)?.let { r.copy(heatPct = heatPct(it.first, it.second)) } ?: r
    }

    fun saveRun(run: Run, isNew: Boolean) {
        val before = engine()
        val r = withHeat(run)
        commit(data.copy(runs = data.runs.filter { it.id != r.id } + r))
        if (isNew && settings.gameOn) celebration = buildCelebration(before, engine(), listOf(r)) else toast = "Run saved"
    }

    fun deleteRun(id: String) {
        // Remember deleted Health Connect runs so the next import does not bring them back.
        commit(data.copy(runs = data.runs.filter { it.id != id }, hidden = if (id.startsWith("hc_")) data.hidden + id else data.hidden))
        toast = "Run deleted"
    }

    fun refreshHealth() = viewModelScope.launch {
        clock = java.time.LocalTime.now()
        healthGranted = try { health.granted() } catch (e: Exception) { false }
        healthExtras = try { health.extrasGranted() } catch (e: Exception) { false }
        if (healthGranted && settings.autoImport && settings.onboarded) importHealth(manual = false)
        refreshOnline()
    }

    /** Fetches the forecast (at most every three hours) and looks for a newer version, each only if switched on. */
    private suspend fun refreshOnline() {
        val s = settings
        if (s.weatherOn && (s.lat != 0.0 || s.lon != 0.0) && System.currentTimeMillis() - weather.fetchedAt > 3 * 3600_000L)
            Online.forecast(s.lat, s.lon)?.let { weather.store(it); wxTick++ }
        if (s.updateCheck) Online.latestRelease()?.let { if (Online.newer(it.first, BuildConfig.VERSION_NAME)) update = it }
    }

    fun setCity(name: String) {
        if (name.isBlank() || cityBusy) return
        cityBusy = true
        viewModelScope.launch {
            val hit = Online.geocode(name)
            if (hit == null) toast = "Couldn't find that place. Check the spelling and your connection."
            else {
                updateSettings { it.copy(weatherOn = true, city = hit.first, lat = hit.second, lon = hit.third) }
                Online.forecast(hit.second, hit.third)?.let { weather.store(it); wxTick++ }
                toast = "Weather on for ${hit.first}"
            }
            cityBusy = false
        }
    }

    fun weatherOff() { weather.clear(); updateSettings { it.copy(weatherOn = false) }; wxTick++ }

    /** Swaps, adds or skips the session on one day. A null kind puts the plan's own session back. */
    fun override(date: LocalDate, kind: String?) = updateSettings { it.copy(overrides = if (kind == null) it.overrides - date else it.overrides + (date to kind)) }

    fun moveSession(from: LocalDate, to: LocalDate, kind: String) = updateSettings { it.copy(overrides = it.overrides + (from to "skip") + (to to kind)) }

    fun setRace(name: String, date: LocalDate?, km: Double) = updateSettings { it.copy(raceName = name.trim().take(40), raceDate = date, raceKm = km) }

    fun importHealth(manual: Boolean) {
        if (importing) return
        importing = true
        viewModelScope.launch {
            try {
                val e = engine()
                val found = health.readRuns(Instant.now().minus(60, ChronoUnit.DAYS), e::zoneOf) { d, h -> e.guessType(d, h) }
                val known = data.runs.map { it.id }.toSet() + data.hidden
                // Skip anything already typed in by hand: same day, distance within 5%.
                val fresh = found.filter { n -> n.id !in known && data.runs.none { it.date == n.date && abs(it.distKm - n.distKm) <= n.distKm * .05 } }.map { withHeat(it) }
                // Runs imported by an older version, or before extra detail was allowed, pick up what is now readable. Anything typed in by hand stays.
                val byId = found.associateBy { it.id }
                val filled = data.runs.map { old ->
                    val n = byId[old.id] ?: return@map old
                    if ((old.trace.isEmpty() && n.trace.isNotEmpty()) || (old.pace.isEmpty() && n.pace.isNotEmpty()) || (old.cadence == 0 && n.cadence > 0) || (old.kcal == 0 && n.kcal > 0) || (old.route.isEmpty() && n.route.isNotEmpty()) || (old.splits.isEmpty() && n.splits.isNotEmpty()))
                        old.copy(route = old.route.ifEmpty { n.route }, trace = n.trace.ifEmpty { old.trace }, pace = n.pace.ifEmpty { old.pace }, zoneSec = n.zoneSec.ifEmpty { old.zoneSec }, splits = old.splits.ifEmpty { n.splits },
                            cadence = maxOf(old.cadence, n.cadence), kcal = maxOf(old.kcal, n.kcal), climb = maxOf(old.climb, n.climb))
                    else old
                }
                if (filled != data.runs) commit(data.copy(runs = filled))
                health.restingHr().takeIf { it > 0 && it != settings.hcRest }?.let { r -> updateSettings { it.copy(hcRest = r) } }
                if (fresh.isNotEmpty()) {
                    val before = engine()
                    commit(data.copy(runs = data.runs + fresh))
                    if (settings.gameOn) celebration = buildCelebration(before, engine(), fresh)
                    else toast = "${fresh.size} run${if (fresh.size == 1) "" else "s"} imported from Health Connect"
                } else if (manual) toast = "No new runs in Health Connect"
            } catch (e: Exception) {
                if (manual) toast = "Couldn't read Health Connect. Check its permissions and try again."
            } finally { importing = false }
        }
    }

    fun export(uri: Uri) { toast = try { store.export(uri, data); "Backup saved" } catch (e: Exception) { "Couldn't save the backup" } }

    fun import(uri: Uri) {
        toast = try {
            val d = store.import(uri)
            // Folder access belongs to this phone, not to the backup file.
            commit(d.copy(settings = d.settings.copy(backupTree = settings.backupTree, lastBackup = settings.lastBackup)))
            "Restored ${d.runs.size} run${if (d.runs.size == 1) "" else "s"}"
        } catch (e: Exception) { "That file isn't an Zone 2 Running backup" }
    }

    fun setBackupFolder(uri: Uri) {
        try {
            getApplication<Application>().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            commit(data.copy(settings = settings.copy(backupTree = uri.toString(), lastBackup = "")))
            toast = if (settings.lastBackup.isNotEmpty()) "Backup folder set. First backup written." else "Backup folder set"
        } catch (e: Exception) { toast = "Couldn't use that folder" }
    }

    fun finishLesson(id: String) {
        if (id !in settings.lessonsDone) { updateSettings { it.copy(lessonsDone = it.lessonsDone + id) }; toast = "Lesson complete" }
    }

    /** Marks or unmarks a week as time off. Break weeks never break the streak. */
    fun toggleBreak(mon: LocalDate) = updateSettings { it.copy(breakWeeks = if (mon in it.breakWeeks) it.breakWeeks - mon else it.breakWeeks + mon) }

    fun setReminder(on: Boolean, minutes: Int = settings.remindMin) {
        updateSettings { it.copy(remindOn = on, remindMin = minutes) }
        Reminders.schedule(getApplication(), settings)
    }

    /** Sends a run's share picture, in the chosen look, to Android's share sheet. */
    fun shareRun(r: Run, style: Int, photo: android.graphics.Bitmap?, story: Boolean) {
        val e = engine()
        try { ShareCard.share(getApplication(), r, style, photo, story) { e.zoneOf(it) } } catch (x: Exception) { toast = "Couldn't open sharing on this phone" }
    }

    /** Saves the picture to the gallery, which is how the see-through overlay gets into Instagram as a sticker. */
    fun saveShare(r: Run, style: Int, photo: android.graphics.Bitmap?, story: Boolean) {
        val e = engine()
        toast = try { if (ShareCard.save(getApplication(), r, style, photo, story) { e.zoneOf(it) }) "Saved to your gallery, in Zone 2 Running" else "Saving needs Android 10 or newer. Use Share." } catch (x: Exception) { "Couldn't save the picture" }
    }

    /** Applies a suggestion the runner agreed to. */
    fun accept(sg: Suggestion) {
        when (val a = sg.act) {
            is MoveDay -> { updateSettings { it.copy(days = (it.days - a.from + a.to).distinct().sorted(), longDay = if (it.longDay == a.from) a.to else it.longDay) }; toast = "Plan updated" }
            is Remind -> { setReminder(true, a.min); toast = "Reminder set for ${fmtClock(a.min)}" }
            is SetRest -> { updateSettings { it.copy(restHr = a.hr) }; toast = "Zones rebuilt on a resting rate of ${a.hr}" }
            is SetMax -> { updateSettings { it.copy(maxHr = a.hr) }; toast = "Zones rebuilt on ${a.hr} bpm" }
            is RunWalk -> { updateSettings { it.copy(runWalk = a.on) }; toast = "Plan updated" }
            TalkTest -> {
                val e = engine()
                val d = (0L..14L).map { e.today.plusDays(it) }.firstOrNull { d -> e.runsOn(d).isEmpty() && e.weekPlan(mondayOf(d)).ses[d.dayOfWeek.value]?.kind in setOf("easy", "runwalk") } ?: e.today.plusDays(1)
                override(d, "talk"); toast = "Talk test set for ${DAY_NAMES[d.dayOfWeek.value - 1]}"
            }
            EnterTalk -> Unit      // needs a number: the home screen asks for it and calls setTalk
        }
    }

    /** Stores the talk-test ceiling; zones rebuild around it. 0 goes back to the formula. */
    fun setTalk(hr: Int) { updateSettings { it.copy(talkHr = hr) }; toast = if (hr > 0) "Zone 2 now tops out at $hr bpm" else "Zones back on the formula" }

    /** A route the runner just agreed to share from Health Connect, for one imported run. */
    fun addRoute(run: Run, r: androidx.health.connect.client.records.ExerciseRoute?) {
        if (r == null) { toast = "No route was shared for this run"; return }
        viewModelScope.launch {
            val start = health.startOf(run.id.removePrefix("hc_"))
            val pts = if (start == null) emptyList() else health.routeOf(r, start)
            if (pts.isEmpty()) toast = "That run has no usable route"
            else commit(data.copy(runs = data.runs.map { if (it.id == run.id) it.copy(route = pts) else it }))
        }
    }

    /** One tap after a run: how it felt, 1 very easy to 5 all out. */
    fun setFeel(id: String, feel: Int) {
        val r = data.runs.firstOrNull { it.id == id } ?: return
        commit(data.copy(runs = data.runs.filter { it.id != id } + r.copy(feel = feel)))
    }

    /** "Not now": the same suggestion stays away for four weeks. */
    fun snooze(sg: Suggestion) = updateSettings { it.copy(snoozed = it.snoozed + (sg.key to LocalDate.now())) }

    /** Pull-to-refresh on the home screen: bring in new runs, and re-read the time of day. */
    fun pulled() {
        clock = java.time.LocalTime.now()
        if (healthGranted) importHealth(manual = true) else toast = "Connect Health Connect in Settings to import runs"
    }

    fun stopBackups() = updateSettings { it.copy(backupTree = "", lastBackup = "") }
}
