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
    val title: String, val xp: Int, val game: Game, val newBadges: List<Badge>, val records: List<String>,
    val message: String, val summary: String, val estLine: String?, val streakLine: String, val streakOn: Boolean,
    val share: String, val celebrate: Boolean,
    val run: Run, val zone: Int, val current: Double?, val target: Int, val streakMilestone: Int,
)

fun buildCelebration(before: Engine, after: Engine, fresh: List<Run>): Celebration {
    val r = fresh.maxBy { it.date }
    val g0 = before.game; val g = after.game
    val nb = g.badges.filter { b -> b.got && g0.badges.none { it.id == b.id && it.got } }
    val up = g.level > g0.level
    val recs = ArrayList<String>()
    if (g0.longest > 0 && g.longest > g0.longest) recs += "Longest run: ${f2(g.longest)} km"
    if (g0.bestEst != null && g.bestEst != null && g.bestEst < g0.bestEst - .5) recs += "Best Zone 2 estimate: ${fmtPace(g.bestEst)} /km"
    if (g0.fast != null && g.fast != null && g.fast < g0.fast - .5) recs += "Fastest run: ${fmtPace(g.fast)} /km"
    val z = if (r.avgHr > 0) after.zoneOf(r.avgHr) else 0
    val easyType = r.type in setOf("easy", "long", "test")
    val msg = when {
        easyType && z in 1..2 -> "Kept in Zone 2. This is the kind of run that moves your easy pace."
        easyType && z >= 3 -> "Average HR landed in Zone $z. On the next easy run, slow down or walk until it stays under ${after.zb[2]} bpm. Easy minutes earn double XP."
        r.type == "test" -> "Benchmark recorded."
        else -> "Hard work banked. Keep the next run properly easy."
    }
    val summary = "${f2(r.distKm)} km · ${fmtDur(r.durSec)} · ${fmtPace(paceOf(r))} /km" + if (r.avgHr > 0) " · ${r.avgHr} bpm (Z$z)" else ""
    val w = after.weekHit(mondayOf(r.date))
    val streakLine = if (w.hit) "Weekly goal hit. Streak: ${g.streak} week${if (g.streak == 1) "" else "s"}." else "${w.n} of ${w.need} runs toward this week's streak."
    val title = when {
        fresh.size > 1 -> "${fresh.size} runs imported"
        up -> "Level ${g.level} reached: ${levelName(g.level)}"
        nb.isNotEmpty() -> "Badge unlocked"
        recs.isNotEmpty() -> "New personal record"
        else -> "Good work"
    }
    val share = buildString {
        append("Zone 2 Running · ${DOW[r.date.dayOfWeek.value - 1]} ${fmtDay(r.date)}\n$summary\n")
        after.current?.let { append("Zone 2 pace now ${fmtPace(it)} /km, target ${fmtPace(after.s.targetPace.toDouble())}\n") }
        append("Level ${g.level} ${levelName(g.level)} · ${g.streak} week streak")
        if (nb.isNotEmpty()) append("\nUnlocked: " + nb.joinToString { it.name })
    }
    return Celebration(title, g.xp - g0.xp, g, nb, recs, msg, summary, after.est(r)?.let { "Zone 2 estimate from this run: ${fmtPace(it)} /km" },
        streakLine, g.streak > 0, share, up || nb.isNotEmpty() || recs.isNotEmpty(),
        r, z, after.current, after.s.targetPace, if (g.streak > g0.streak && g.streak in setOf(4, 8, 12, 26, 52)) g.streak else 0)
}

class AppVM(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    val health = Health(app)

    var data by mutableStateOf(store.load()); private set
    var celebration by mutableStateOf<Celebration?>(null)
    var toast by mutableStateOf<String?>(null)
    var healthGranted by mutableStateOf(false); private set
    var importing by mutableStateOf(false); private set
    /** Time of day the home screen was last opened; it decides what leads. */
    var clock by mutableStateOf(java.time.LocalTime.now())

    val settings get() = data.settings
    fun engine() = Engine(data.settings, data.runs, LocalDate.now())

    private fun commit(d: AppData) {
        var nd = d
        store.save(nd)
        try {
            store.autoBackup(nd, LocalDate.now())?.let { nd = nd.copy(settings = nd.settings.copy(lastBackup = it)); store.save(nd) }
        } catch (e: Exception) { /* a failed backup must never block saving */ }
        data = nd
    }

    fun updateSettings(f: (Settings) -> Settings) = commit(data.copy(settings = f(data.settings)))

    fun saveRun(r: Run, isNew: Boolean) {
        val before = engine()
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
        if (healthGranted && settings.autoImport && settings.onboarded) importHealth(manual = false)
    }

    fun importHealth(manual: Boolean) {
        if (importing) return
        importing = true
        viewModelScope.launch {
            try {
                val e = engine()
                val found = health.readRuns(Instant.now().minus(60, ChronoUnit.DAYS), e::zoneOf) { d, h -> e.guessType(d, h) }
                val known = data.runs.map { it.id }.toSet() + data.hidden
                // Skip anything already typed in by hand: same day, distance within 5%.
                val fresh = found.filter { n -> n.id !in known && data.runs.none { it.date == n.date && abs(it.distKm - n.distKm) <= n.distKm * .05 } }
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
        if (id !in settings.lessonsDone) { updateSettings { it.copy(lessonsDone = it.lessonsDone + id) }; toast = "Lesson complete · +40 XP" }
    }

    /** Marks or unmarks a week as time off. Break weeks never break the streak. */
    fun toggleBreak(mon: LocalDate) = updateSettings { it.copy(breakWeeks = if (mon in it.breakWeeks) it.breakWeeks - mon else it.breakWeeks + mon) }

    fun setReminder(on: Boolean, minutes: Int = settings.remindMin) {
        updateSettings { it.copy(remindOn = on, remindMin = minutes) }
        Reminders.schedule(getApplication(), settings)
    }

    fun shareRun(c: Celebration) {
        try { ShareCard.share(getApplication(), c) } catch (e: Exception) { toast = "Couldn't open sharing on this phone" }
    }

    /** Applies a suggestion the runner agreed to. */
    fun accept(sg: Suggestion) {
        when (val a = sg.act) {
            is MoveDay -> { updateSettings { it.copy(days = (it.days - a.from + a.to).distinct().sorted(), longDay = if (it.longDay == a.from) a.to else it.longDay) }; toast = "Plan updated" }
            is Remind -> { setReminder(true, a.min); toast = "Reminder set for ${fmtClock(a.min)}" }
        }
    }

    /** "Not now": the same suggestion stays away for four weeks. */
    fun snooze(sg: Suggestion) = updateSettings { it.copy(snoozed = it.snoozed + (sg.key to LocalDate.now())) }

    fun stopBackups() = updateSettings { it.copy(backupTree = "", lastBackup = "") }
}
