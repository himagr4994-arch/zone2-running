package app.easyseven

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
    suspend fun readRuns(since: Instant, typeOf: (durSec: Int, avgHr: Int) -> String): List<Run> {
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
                    notes = sx.title ?: "", source = "health",
                )
            }
            token = page.pageToken
        } while (token != null)
        return out
    }
}
