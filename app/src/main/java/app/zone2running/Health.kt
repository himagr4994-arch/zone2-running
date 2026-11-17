package app.zone2running

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** Read-only bridge to Android Health Connect. Nothing is written back and nothing leaves the phone. */
class Health(private val ctx: Context) {
    val permissions = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
    )
    /** Optional detail: pace through the run, cadence, calories and climb. The app works without any of it. */
    val extras = setOf(
        HealthPermission.getReadPermission(SpeedRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ElevationGainedRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
    )

    /** Average resting heart rate over the last week, or 0 when nothing has been recorded or it isn't allowed. */
    suspend fun restingHr(): Int = try {
        val v = client.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, TimeRangeFilter.after(Instant.now().minus(Duration.ofDays(7))))).records.map { it.beatsPerMinute }
        if (v.size >= 2) v.average().roundToInt() else 0
    } catch (e: Exception) { 0 }
    suspend fun extrasGranted(): Boolean = available() && client.permissionController.getGrantedPermissions().containsAll(extras)
    private val runTypes = setOf(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL)
    private val client by lazy { HealthConnectClient.getOrCreate(ctx) }

    fun available() = HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE

    suspend fun granted(): Boolean =
        available() && client.permissionController.getGrantedPermissions().containsAll(permissions)

    /** Runs recorded since [since] by any app that writes to Health Connect (watch apps, Strava, unstrap…). */
    suspend fun readRuns(since: Instant, zoneOf: (Int) -> Int, typeOf: (durSec: Int, avgHr: Int) -> String): List<Run> {
        val out = ArrayList<Run>()
        var token: String? = null
        do {
            val page = client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, TimeRangeFilter.after(since), pageToken = token))
            for (sx in page.records) {
                if (sx.exerciseType !in runTypes) continue
                val range = TimeRangeFilter.between(sx.startTime, sx.endTime)
                val metrics = setOf(DistanceRecord.DISTANCE_TOTAL, HeartRateRecord.BPM_AVG, HeartRateRecord.BPM_MAX, ExerciseSessionRecord.EXERCISE_DURATION_TOTAL)
                // Prefer numbers from the app that recorded the run, so two apps don't double the distance.
                var agg = client.aggregate(AggregateRequest(metrics, range, setOf(sx.metadata.dataOrigin)))
                if (agg[DistanceRecord.DISTANCE_TOTAL] == null) agg = client.aggregate(AggregateRequest(metrics, range))
                val km = agg[DistanceRecord.DISTANCE_TOTAL]?.inKilometers ?: continue
                val dur = (agg[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL] ?: Duration.between(sx.startTime, sx.endTime)).seconds.toInt()
                if (km < 0.5 || dur < 300) continue
                val avg = agg[HeartRateRecord.BPM_AVG]?.toInt() ?: 0
                val max = agg[HeartRateRecord.BPM_MAX]?.toInt() ?: 0
                val offset = sx.startZoneOffset ?: ZoneId.systemDefault().rules.getOffset(sx.startTime)
                val origin = sx.metadata.dataOrigin
                val beats = try {
                    client.readRecords(ReadRecordsRequest(HeartRateRecord::class, range, setOf(origin))).records.flatMap { it.samples }
                        .map { Duration.between(sx.startTime, it.time).seconds to it.beatsPerMinute.toInt() }
                } catch (e: Exception) { emptyList() }
                // Each extra is read on its own, so one missing permission or record type doesn't cost the others.
                val speed = try {
                    client.readRecords(ReadRecordsRequest(SpeedRecord::class, range, setOf(origin))).records.flatMap { it.samples }
                        .map { Duration.between(sx.startTime, it.time).seconds to it.speed.inMetersPerSecond }.sortedBy { it.first }
                } catch (e: Exception) { emptyList() }
                val paceGrid = if (speed.size < 10) emptyList() else (0..dur / TRACE_STEP).map { i ->
                    val t = i * TRACE_STEP.toLong()
                    val near = speed.filter { kotlin.math.abs(it.first - t) <= 15 }.map { it.second }
                    if (near.isEmpty() || near.average() < .5) 0 else (1000 / near.average()).roundToInt()
                }
                val steps = try { client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), range, setOf(origin)))[StepsRecord.COUNT_TOTAL] ?: 0L } catch (e: Exception) { 0L }
                val kcal = try { client.aggregate(AggregateRequest(setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL), range, setOf(origin)))[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0 } catch (e: Exception) { 0.0 }
                val climb = try { client.aggregate(AggregateRequest(setOf(ElevationGainedRecord.ELEVATION_GAINED_TOTAL), range, setOf(origin)))[ElevationGainedRecord.ELEVATION_GAINED_TOTAL]?.inMeters ?: 0.0 } catch (e: Exception) { 0.0 }
                // The path, when the recording app saved one and the runner has allowed routes to be read.
                val route = (sx.exerciseRouteResult as? ExerciseRouteResult.Data)?.let { routeOf(it.exerciseRoute, sx.startTime) } ?: emptyList()
                // Laps of about a kilometre are splits in all but name.
                val lapSplits = sx.laps.takeIf { l -> l.size >= 2 && l.dropLast(1).all { (it.length?.inMeters ?: 0.0) in 950.0..1050.0 } }?.dropLast(1)?.map { lap ->
                    val a = Duration.between(sx.startTime, lap.startTime).seconds; val b = Duration.between(sx.startTime, lap.endTime).seconds
                    val hr = beats.filter { it.first in a..b }.map { it.second }
                    Split((b - a).toInt(), if (hr.isEmpty()) 0 else hr.average().roundToInt())
                } ?: emptyList()
                val cadence = (steps * 60.0 / dur).roundToInt().takeIf { it in 120..230 } ?: 0      // anything else is not a running step count
                // The source's own average can be well off when it logs readings unevenly; a full trace is the better number.
                val grid = traceFrom(beats, dur)
                val avgTrue = grid.filter { it > 0 }.takeIf { it.size >= 30 && it.size * TRACE_STEP >= dur * .8 }?.average()?.roundToInt() ?: avg
                out += Run(
                    id = "hc_" + sx.metadata.id, date = sx.startTime.atOffset(offset).toLocalDate(), type = typeOf(dur, avg),
                    distKm = (km * 100).roundToInt() / 100.0, durSec = dur, avgHr = avgTrue, maxHr = max,
                    notes = sx.title ?: "", source = "health", zoneSec = zoneSeconds(range, sx.metadata.dataOrigin, zoneOf),
                    splits = splits(range, sx.metadata.dataOrigin, sx.startTime).ifEmpty { lapSplits }.ifEmpty { splitsFromPace(paceGrid, traceFrom(beats, dur), km) },
                    route = route,
                    startMin = sx.startTime.atOffset(offset).toLocalTime().let { it.hour * 60 + it.minute },
                    trace = traceFrom(beats, dur), pace = paceGrid, cadence = cadence, kcal = kcal.roundToInt(), climb = climb.roundToInt(),
                )
            }
            token = page.pageToken
        } while (token != null)
        return out
    }

    /** A Health Connect route as the app stores it: seconds from the start, latitude and longitude × 1e5, thinned to one point every four seconds. */
    fun routeOf(r: ExerciseRoute, start: Instant): List<Int> {
        val out = ArrayList<Int>(); var last = -99L
        for (l in r.route.sortedBy { it.time }) {
            val t = Duration.between(start, l.time).seconds
            if (t - last < 4) continue
            out += t.toInt().coerceAtLeast(0); out += (l.latitude * 1e5).roundToInt(); out += (l.longitude * 1e5).roundToInt(); last = t
        }
        return if (out.size >= 30) out else emptyList()
    }

    /** When a run started, for lining a route up with it. Null when the run can no longer be found. */
    suspend fun startOf(id: String): Instant? = try { client.readRecord(ExerciseSessionRecord::class, id).record.startTime } catch (e: Exception) { null }

    /** Per-kilometre splits, when the recording app wrote distance in small enough pieces to work them out. */
    private suspend fun splits(range: TimeRangeFilter, origin: androidx.health.connect.client.records.metadata.DataOrigin, start: Instant): List<Split> = try {
        val pieces = client.readRecords(ReadRecordsRequest(DistanceRecord::class, range, setOf(origin))).records
            .map { Triple(Duration.between(start, it.startTime).seconds, Duration.between(start, it.endTime).seconds, it.distance.inMeters) }
        val beats = client.readRecords(ReadRecordsRequest(HeartRateRecord::class, range, setOf(origin))).records.flatMap { it.samples }
            .map { Duration.between(start, it.time).seconds to it.beatsPerMinute.toInt() }
        splitsFrom(pieces, beats)
    } catch (e: Exception) { emptyList() }

    /**
     * Seconds spent in each zone, from the run's heart-rate samples. Each reading stands for the time until
     * the next one, up to two minutes: straps that report once a minute would otherwise lose half the run.
     */
    private suspend fun zoneSeconds(range: TimeRangeFilter, origin: androidx.health.connect.client.records.metadata.DataOrigin, zoneOf: (Int) -> Int): List<Int> {
        val samples = try {
            client.readRecords(ReadRecordsRequest(HeartRateRecord::class, range, setOf(origin))).records.flatMap { it.samples }.sortedBy { it.time }
        } catch (e: Exception) { return emptyList() }
        if (samples.size < 10) return emptyList()
        val sec = IntArray(5)
        for (i in 0 until samples.size - 1) {
            val dt = Duration.between(samples[i].time, samples[i + 1].time).seconds.coerceIn(0, 120).toInt()
            sec[zoneOf(samples[i].beatsPerMinute.toInt()) - 1] += dt
        }
        return sec.toList()
    }
}
