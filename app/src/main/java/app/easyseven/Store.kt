package app.easyseven

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
        val ra = JSONArray()
        for (r in d.runs) ra.put(
            JSONObject().put("id", r.id).put("date", r.date.toString()).put("type", r.type).put("distKm", r.distKm)
                .put("durSec", r.durSec).put("avgHr", r.avgHr).put("maxHr", r.maxHr).put("feel", r.feel)
                .put("notes", r.notes).put("source", r.source)
        )
        return JSONObject().put("app", "easy-seven").put("version", 1).put("settings", so).put("runs", ra)
            .put("hidden", JSONArray(d.hidden.toList())).toString(2)
    }

    fun decode(text: String): AppData {
        val o = JSONObject(text)
        val so = o.optJSONObject("settings") ?: JSONObject()
        val def = Settings()
        fun ints(a: JSONArray?, fallback: List<Int>) = if (a == null) fallback else (0 until a.length()).map { a.getInt(it) }
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
        )
        val ra = o.optJSONArray("runs") ?: JSONArray()
        val runs = (0 until ra.length()).mapNotNull { i ->
            val r = ra.getJSONObject(i)
            val d = date(r.optString("date")) ?: return@mapNotNull null
            val km = r.optDouble("distKm", 0.0); val sec = r.optInt("durSec", 0)
            if (km <= 0 || sec <= 0) null
            else Run(r.optString("id", "run_$i"), d, r.optString("type", "easy"), km, sec, r.optInt("avgHr", 0), r.optInt("maxHr", 0),
                r.optInt("feel", 0), r.optString("notes"), r.optString("source", "manual"))
        }
        val ha = o.optJSONArray("hidden") ?: JSONArray()
        return AppData(s, runs, (0 until ha.length()).map { ha.getString(it) }.toSet())
    }
}

class Store(private val ctx: Context) {
    private val file = File(ctx.filesDir, "easy-seven.json")

    fun load(): AppData {
        if (!file.exists()) return AppData()
        return try { Json.decode(file.readText()) } catch (e: Exception) {
            // Never overwrite a file we could not read: set it aside for recovery.
            file.copyTo(File(ctx.filesDir, "easy-seven.unreadable.json"), overwrite = true)
            AppData()
        }
    }

    fun save(d: AppData) {
        val tmp = File(ctx.filesDir, "easy-seven.json.tmp")
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
        val name = "easy-seven-$today.json"
        val fresh = dir.createFile("application/json", "$name.new") ?: return null
        export(fresh.uri, d)
        dir.findFile(name)?.delete()          // yesterday's file stays until the new one is in place
        fresh.renameTo(name)
        dir.listFiles().filter { (it.name ?: "").matches(Regex("easy-seven-\\d{4}-\\d{2}-\\d{2}\\.json")) }
            .sortedByDescending { it.name }.drop(7).forEach { it.delete() }
        return today.toString()
    }
}
