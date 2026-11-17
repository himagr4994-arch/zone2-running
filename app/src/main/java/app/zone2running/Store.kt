package app.zone2running

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

data class AppData(val settings: Settings = Settings(), val runs: List<Run> = emptyList(), val hidden: Set<String> = emptySet())

/** Everything the app knows is one JSON document: easy to back up, read and move. */
object Json {
    fun encode(d: AppData): String {
        val s = d.settings
        val so = JSONObject()
            .put("targetPace", s.targetPace).put("maxHr", s.maxHr).put("restHr", s.restHr).put("zoneMethod", s.zoneMethod)
            .put("customZ", JSONArray(s.customZ)).put("refHr", s.refHr).put("days", JSONArray(s.days)).put("longDay", s.longDay)
            .put("easyMin", s.easyMin).put("longMin", s.longMin).put("planStart", s.planStart?.toString() ?: "")
            .put("quality", s.quality).put("onboarded", s.onboarded).put("basePace", s.basePace).put("baseHr", s.baseHr)
            .put("baseDate", s.baseDate?.toString() ?: "").put("gameOn", s.gameOn).put("autoImport", s.autoImport)
            .put("backupTree", s.backupTree).put("lastBackup", s.lastBackup)
            .put("introSeen", s.introSeen).put("goalWeeks", s.goalWeeks).put("breakWeeks", JSONArray(s.breakWeeks.map { it.toString() }))
            .put("lessonsDone", JSONArray(s.lessonsDone)).put("remindOn", s.remindOn).put("remindMin", s.remindMin)
            .put("snoozed", JSONObject().also { o -> s.snoozed.forEach { (k, v) -> o.put(k, v.toString()) } })
            .put("miles", s.miles).put("raceName", s.raceName).put("raceDate", s.raceDate?.toString() ?: "").put("raceKm", s.raceKm)
            .put("overrides", JSONObject().also { o -> s.overrides.forEach { (k, v) -> o.put(k.toString(), v) } })
            .put("weatherOn", s.weatherOn).put("city", s.city).put("lat", s.lat).put("lon", s.lon)
            .put("voiceOn", s.voiceOn).put("sensorAddr", s.sensorAddr).put("sensorName", s.sensorName)
            .put("updateCheck", s.updateCheck).put("disclaimerOk", s.disclaimerOk).put("seenPace", s.seenPace)
            .put("talkHr", s.talkHr).put("runWalk", s.runWalk).put("caution", s.caution).put("hrMeds", s.hrMeds)
        val ra = JSONArray()
        for (r in d.runs) ra.put(
            JSONObject().put("id", r.id).put("date", r.date.toString()).put("type", r.type).put("distKm", r.distKm)
                .put("durSec", r.durSec).put("avgHr", r.avgHr).put("maxHr", r.maxHr).put("feel", r.feel)
                .put("notes", r.notes).put("source", r.source).put("zoneSec", JSONArray(r.zoneSec)).put("startMin", r.startMin)
                .put("splits", JSONArray(r.splits.flatMap { listOf(it.sec, it.hr) })).put("heatPct", r.heatPct)
                .put("trace", JSONArray(r.trace)).put("pace", JSONArray(r.pace)).put("kind", r.kind).put("cadence", r.cadence).put("kcal", r.kcal).put("climb", r.climb)
                .put("steps", JSONArray().also { a -> r.steps.forEach { a.put(JSONObject().put("label", it.label).put("sec", it.sec).put("zLo", it.zLo).put("zHi", it.zHi).put("kind", it.kind)) } })
        )
        return JSONObject().put("app", "zone2-running").put("version", 1).put("settings", so).put("runs", ra)
            .put("hidden", JSONArray(d.hidden.toList())).toString(2)
    }

    fun decode(text: String): AppData {
        val o = JSONObject(text)
        val so = o.optJSONObject("settings") ?: JSONObject()
        val def = Settings()
        fun ints(a: JSONArray?, fallback: List<Int>) = if (a == null) fallback else (0 until a.length()).map { a.getInt(it) }
        fun strings(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }
        fun date(v: String) = if (v.isBlank()) null else runCatching { LocalDate.parse(v) }.getOrNull()
        val s = Settings(
            targetPace = so.optInt("targetPace", def.targetPace), maxHr = so.optInt("maxHr", def.maxHr), restHr = so.optInt("restHr", def.restHr),
            zoneMethod = so.optString("zoneMethod", def.zoneMethod),
            customZ = ints(so.optJSONArray("customZ"), def.customZ).takeIf { it.size == 4 } ?: def.customZ,
            refHr = so.optInt("refHr", 0), days = ints(so.optJSONArray("days"), def.days).filter { it in 1..7 },
            longDay = so.optInt("longDay", def.longDay), easyMin = so.optInt("easyMin", def.easyMin), longMin = so.optInt("longMin", def.longMin),
            planStart = date(so.optString("planStart")), quality = so.optBoolean("quality", true), onboarded = so.optBoolean("onboarded", false),
            basePace = so.optInt("basePace", 0), baseHr = so.optInt("baseHr", 0), baseDate = date(so.optString("baseDate")),
            gameOn = so.optBoolean("gameOn", true), autoImport = so.optBoolean("autoImport", true),
            backupTree = so.optString("backupTree"), lastBackup = so.optString("lastBackup"),
            introSeen = so.optBoolean("introSeen", so.optBoolean("onboarded", false)), goalWeeks = so.optInt("goalWeeks", 8),
            breakWeeks = strings(so.optJSONArray("breakWeeks")).mapNotNull { date(it) }, lessonsDone = strings(so.optJSONArray("lessonsDone")),
            remindOn = so.optBoolean("remindOn", false), remindMin = so.optInt("remindMin", 390),
            miles = so.optBoolean("miles", false), raceName = so.optString("raceName"), raceDate = date(so.optString("raceDate")), raceKm = so.optDouble("raceKm", 0.0),
            overrides = so.optJSONObject("overrides")?.let { o -> o.keys().asSequence().mapNotNull { k -> date(k)?.let { it to o.optString(k) } }.toMap() } ?: emptyMap(),
            weatherOn = so.optBoolean("weatherOn", false), city = so.optString("city"), lat = so.optDouble("lat", 0.0), lon = so.optDouble("lon", 0.0),
            voiceOn = so.optBoolean("voiceOn", true), sensorAddr = so.optString("sensorAddr"), sensorName = so.optString("sensorName"),
            seenPace = so.optInt("seenPace", 0), talkHr = so.optInt("talkHr", 0), runWalk = so.optBoolean("runWalk", false), caution = so.optBoolean("caution", false), hrMeds = so.optBoolean("hrMeds", false), updateCheck = so.optBoolean("updateCheck", false), disclaimerOk = so.optBoolean("disclaimerOk", so.optBoolean("onboarded", false)),
            snoozed = so.optJSONObject("snoozed")?.let { o -> o.keys().asSequence().mapNotNull { k -> date(o.optString(k))?.let { k to it } }.toMap() } ?: emptyMap(),
        )
        val ra = o.optJSONArray("runs") ?: JSONArray()
        val runs = (0 until ra.length()).mapNotNull { i ->
            val r = ra.getJSONObject(i)
            val d = date(r.optString("date")) ?: return@mapNotNull null
            val km = r.optDouble("distKm", 0.0); val sec = r.optInt("durSec", 0)
            if (km <= 0 || sec <= 0) null
            else Run(r.optString("id", "run_$i"), d, r.optString("type", "easy"), km, sec, r.optInt("avgHr", 0), r.optInt("maxHr", 0),
                r.optInt("feel", 0), r.optString("notes"), r.optString("source", "manual"), ints(r.optJSONArray("zoneSec"), emptyList()), r.optInt("startMin", -1),
                ints(r.optJSONArray("splits"), emptyList()).chunked(2).filter { it.size == 2 }.map { Split(it[0], it[1]) }, r.optDouble("heatPct", 0.0),
                ints(r.optJSONArray("trace"), emptyList()), ints(r.optJSONArray("pace"), emptyList()),
                r.optJSONArray("steps")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.map { Step(it.optString("label"), it.optInt("sec"), it.optInt("zLo", 1), it.optInt("zHi", 2), it.optString("kind")) } } ?: emptyList(),
                r.optString("kind"), r.optInt("cadence", 0), r.optInt("kcal", 0), r.optInt("climb", 0))
        }
        val ha = o.optJSONArray("hidden") ?: JSONArray()
        return AppData(s, runs, (0 until ha.length()).map { ha.getString(it) }.toSet())
    }
}

class Store(private val ctx: Context) {
    private val file = File(ctx.filesDir, "zone2-running.json")

    fun load(): AppData {
        if (!file.exists()) return AppData()
        return try { Json.decode(file.readText()) } catch (e: Exception) {
            // Never overwrite a file we could not read: set it aside for recovery.
            file.copyTo(File(ctx.filesDir, "zone2-running.unreadable.json"), overwrite = true)
            AppData()
        }
    }

    fun save(d: AppData) {
        val tmp = File(ctx.filesDir, "zone2-running.json.tmp")
        tmp.writeText(Json.encode(d))
        if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
    }

    fun export(uri: Uri, d: AppData) {
        val out = ctx.contentResolver.openOutputStream(uri, "wt") ?: error("Could not open the file")
        out.use { it.write(Json.encode(d).toByteArray()) }
    }

    fun import(uri: Uri): AppData {
        val input = ctx.contentResolver.openInputStream(uri) ?: error("Could not open the file")
        return Json.decode(input.use { it.readBytes().toString(Charsets.UTF_8) })
    }

    /** Writes one dated backup a day into the folder the user chose and keeps the newest seven. Returns the date written. */
    fun autoBackup(d: AppData, today: LocalDate): String? {
        if (d.settings.backupTree.isEmpty() || d.settings.lastBackup == today.toString()) return null
        val dir = DocumentFile.fromTreeUri(ctx, Uri.parse(d.settings.backupTree)) ?: return null
        if (!dir.canWrite()) return null
        val name = "zone2-running-$today.json"
        val fresh = dir.createFile("application/json", "$name.new") ?: return null
        export(fresh.uri, d)
        dir.findFile(name)?.delete()          // yesterday's file stays until the new one is in place
        fresh.renameTo(name)
        dir.listFiles().filter { (it.name ?: "").matches(Regex("zone2-running-\\d{4}-\\d{2}-\\d{2}\\.json")) }
            .sortedByDescending { it.name }.drop(7).forEach { it.delete() }
        return today.toString()
    }
}
