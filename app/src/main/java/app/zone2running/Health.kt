package app.zone2running

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
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
                out += Run(
                    id = "hc_" + sx.metadata.id, date = sx.startTime.atOffset(offset).toLocalDate(), type = typeOf(dur, avg),
                    distKm = (km * 100).roundToInt() / 100.0, durSec = dur, avgHr = avg, maxHr = max,
                    notes = sx.title ?: "", source = "health", zoneSec = zoneSeconds(range, sx.metadata.dataOrigin, zoneOf),
                    splits = splits(range, sx.metadata.dataOrigin, sx.startTime),
                    startMin = sx.startTime.atOffset(offset).toLocalTime().let { it.hour * 60 + it.minute },
                )
            }
            token = page.pageToken
        } while (token != null)
        return out
    }

    /** Per-kilometre splits, when the recording app wrote distance in small enough pieces to work them out. */
    private suspend fun splits(range: TimeRangeFilter, origin: androidx.health.connect.client.records.metadata.DataOrigin, start: Instant): List<Split> = try {
        val pieces = client.readRecords(ReadRecordsRequest(DistanceRecord::class, range, setOf(origin))).records
            .map { Triple(Duration.between(start, it.startTime).seconds, Duration.between(start, it.endTime).seconds, it.distance.inMeters) }
        val beats = client.readRecords(ReadRecordsRequest(HeartRateRecord::class, range, setOf(origin))).records.flatMap { it.samples }
            .map { Duration.between(start, it.time).seconds to it.beatsPerMinute.toInt() }
        splitsFrom(pieces, beats)
    } catch (e: Exception) { emptyList() }

    /** Seconds spent in each zone, from the run's heart-rate samples. Gaps longer than 30 s are not counted. */
    private suspend fun zoneSeconds(range: TimeRangeFilter, origin: androidx.health.connect.client.records.metadata.DataOrigin, zoneOf: (Int) -> Int): List<Int> {
        val samples = try {
            client.readRecords(ReadRecordsRequest(HeartRateRecord::class, range, setOf(origin))).records.flatMap { it.samples }.sortedBy { it.time }
        } catch (e: Exception) { return emptyList() }
        if (samples.size < 10) return emptyList()
        val sec = IntArray(5)
        for (i in 0 until samples.size - 1) {
            val dt = Duration.between(samples[i].time, samples[i + 1].time).seconds.coerceIn(0, 30).toInt()
            sec[zoneOf(samples[i].beatsPerMinute.toInt()) - 1] += dt
        }
        return sec.toList()
    }
}
