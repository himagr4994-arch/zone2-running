package app.zone2running

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** One gentle nudge a day at the time the runner picked. Nothing is sent when there is nothing useful to say. */
object Reminders {
    private const val CHANNEL = "training"

    private fun intent(ctx: Context) = PendingIntent.getBroadcast(ctx, 1, Intent(ctx, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(ctx: Context, s: Settings) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(intent(ctx))
        if (!s.remindOn) return
        var at = LocalDate.now().atStartOfDay().plusMinutes(s.remindMin.toLong())
        if (!at.isAfter(LocalDateTime.now())) at = at.plusDays(1)
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), AlarmManager.INTERVAL_DAY, intent(ctx))
    }

    fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(ctx: Context) {
        val data = Store(ctx).load()
        if (!data.settings.remindOn || !canNotify(ctx)) return
        val (title, body) = Engine(data.settings, data.runs, LocalDate.now()).reminder() ?: return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Training reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, 2, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        nm.notify(1, NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_heart).setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(open).setAutoCancel(true).build())
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        // After a restart the alarm is gone, so set it again; otherwise this is the daily tick.
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Reminders.schedule(ctx, Store(ctx).load().settings) else Reminders.show(ctx)
    }
}
