package app.zone2running

import android.app.Application
import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The only two reasons this app ever goes online, both off until the runner switches them on:
 * the hourly forecast (Open-Meteo, no key, no account) and the check for a newer version.
 */
object Online {
    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000; c.setRequestProperty("User-Agent", "Zone2Running/${BuildConfig.VERSION_NAME}")
        try { if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}"); return c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
    }

    /** Looks a place up by name. Returns (label, latitude, longitude). */
    suspend fun geocode(name: String): Triple<String, Double, Double>? = withContext(Dispatchers.IO) {
        try {
            val r = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + URLEncoder.encode(name.trim(), "UTF-8"))).optJSONArray("results")?.optJSONObject(0) ?: return@withContext null
            Triple(listOf(r.optString("name"), r.optString("admin1"), r.optString("country")).filter { it.isNotBlank() }.distinct().joinToString(", "), r.getDouble("latitude"), r.getDouble("longitude"))
        } catch (e: Exception) { null }
    }

    /** Two days of hourly temperature and dew point, keyed "2026-11-17T19". Null when the request fails. */
    suspend fun forecast(lat: Double, lon: Double): Map<String, Pair<Double, Double>>? = withContext(Dispatchers.IO) {
        try { parseForecast(get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&hourly=temperature_2m,dew_point_2m&forecast_days=2&past_days=1&timezone=auto")) } catch (e: Exception) { null }
    }

    fun parseForecast(json: String): Map<String, Pair<Double, Double>> {
        val h = JSONObject(json).getJSONObject("hourly")
        val t = h.getJSONArray("time"); val temp = h.getJSONArray("temperature_2m"); val dew = h.getJSONArray("dew_point_2m")
        return (0 until t.length()).filter { !temp.isNull(it) && !dew.isNull(it) }.associate { t.getString(it).take(13) to (temp.getDouble(it) to dew.getDouble(it)) }
    }

    /** Newest release of the app, as (version, page to open), or null. Needs a public repository named in the build. */
    suspend fun latestRelease(): Pair<String, String>? = withContext(Dispatchers.IO) {
        if (BuildConfig.UPDATE_REPO.isBlank()) return@withContext null
        try { JSONObject(get("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")).let { it.getString("tag_name").trimStart('v') to it.getString("html_url") } } catch (e: Exception) { null }
    }

    /** True when version [a] is newer than [b], comparing number by number. */
    fun newer(a: String, b: String): Boolean {
        val x = a.split(".").map { it.toIntOrNull() ?: 0 }; val y = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) { val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }; if (d != 0) return d > 0 }
        return false
    }
}

/** The forecast kept on the phone between fetches, so the home screen never waits on the network. */
class WeatherCache(ctx: Context) : Wx {
    private val file = File(ctx.filesDir, "weather.json")
    var hours: Map<String, Pair<Double, Double>> = emptyMap(); private set
    var fetchedAt = 0L; private set

    init {
        try {
            if (file.exists()) JSONObject(file.readText()).let { o ->
                fetchedAt = o.optLong("at"); val h = o.getJSONObject("hours")
                hours = h.keys().asSequence().associateWith { k -> h.getJSONArray(k).let { it.getDouble(0) to it.getDouble(1) } }
            }
        } catch (e: Exception) { }
    }

    fun store(fresh: Map<String, Pair<Double, Double>>) {
        hours = fresh; fetchedAt = System.currentTimeMillis()
        try { file.writeText(JSONObject().put("at", fetchedAt).put("hours", JSONObject().also { o -> fresh.forEach { (k, v) -> o.put(k, org.json.JSONArray(listOf(v.first, v.second))) } }).toString()) } catch (e: Exception) { }
    }

    fun clear() { hours = emptyMap(); fetchedAt = 0; file.delete() }

    override fun at(date: LocalDate, minute: Int) = hours["${date}T${(minute / 60).coerceIn(0, 23).toString().padStart(2, '0')}"]
}

/** Saves the reason for a crash to the phone so the runner can copy it into a bug report. Nothing is sent anywhere. */
class Z2App : Application() {
    override fun onCreate() {
        super.onCreate()
        val prior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
                File(filesDir, "last-crash.txt").writeText("${LocalDateTime.now()}\n$sw")
            } catch (x: Exception) { }
            prior?.uncaughtException(t, e)
        }
    }
}

object Diagnostics {
    fun lastCrash(ctx: Context): String? = File(ctx.filesDir, "last-crash.txt").takeIf { it.exists() }?.readText()
    fun clear(ctx: Context) { File(ctx.filesDir, "last-crash.txt").delete() }

    /** What a bug report needs, and nothing personal: no runs, no heart rates, no location. */
    fun report(ctx: Context, d: AppData, healthOk: Boolean): String = buildString {
        appendLine("Zone 2 Running ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Runs: ${d.runs.size} (${d.runs.count { it.source == "health" }} imported, ${d.runs.count { it.source == "live" }} live)")
        appendLine("Health Connect: ${if (healthOk) "connected" else "not connected"} · Reminder: ${d.settings.remindOn} · Weather: ${d.settings.weatherOn} · Miles: ${d.settings.miles}")
        appendLine("Sensor saved: ${d.settings.sensorAddr.isNotEmpty()} · Backup folder: ${d.settings.backupTree.isNotEmpty()}")
        appendLine()
        append(lastCrash(ctx)?.let { "Last crash:\n" + it.take(6000) } ?: "No crash recorded.")
    }
}
