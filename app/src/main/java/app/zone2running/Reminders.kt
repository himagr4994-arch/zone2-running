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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Set by a tap on a notification's "Start run"; the app opens straight onto today's run. */
object Launch { var start by androidx.compose.runtime.mutableStateOf(false) }

/**
 * The day's notifications: one before the hour the runner usually heads out, and at most one follow-up if that hour passes
 * without a run. Everything is scheduled on the phone itself; nothing is sent from anywhere.
 */
object Reminders {
    private const val CHANNEL = "training"
    const val SKIP = "app.zone2running.SKIP"
    const val MOVE = "app.zone2running.MOVE"
    private const val ID = 1

    private fun tick(ctx: Context, late: Boolean) = PendingIntent.getBroadcast(ctx, if (late) 3 else 1,
        Intent(ctx, ReminderReceiver::class.java).putExtra("late", late), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Sets the next of each alarm. Called whenever the settings or the runs change, after each one fires, and after a restart. */
    fun schedule(ctx: Context, data: AppData = Store(ctx).load()) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(tick(ctx, false)); am.cancel(tick(ctx, true))
        if (!data.settings.remindOn) return
        val e = Engine(data.settings, data.runs, LocalDate.now())
        fun arm(min: Int, late: Boolean) {
            var at = LocalDate.now().atStartOfDay().plusMinutes(min.toLong())
            if (!at.isAfter(LocalDateTime.now())) at = at.plusDays(1)
            // Allowed to fire while the phone dozes; Android may still shift it by a few minutes.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), tick(ctx, late))
        }
        arm(e.nudgeMin, false)
        e.lateMin?.let { arm(it, true) }
    }

    fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(ctx: Context, late: Boolean = false) {
        val data = Store(ctx).load()
        if (!data.settings.remindOn || !canNotify(ctx)) return
        val wx = if (data.settings.weatherOn) WeatherCache(ctx) else null
        val e = Engine(data.settings, data.runs, LocalDate.now(), wx)
        val n = e.nudge(late) ?: return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Training reminders", NotificationManager.IMPORTANCE_DEFAULT))
        nm.notify(ID, build(ctx, n, e).build())
    }

    /** The notification itself: the mascot beside the text, the app's green, and a drawn card that shows when it is pulled open. */
    fun build(ctx: Context, n: Nudge, e: Engine): NotificationCompat.Builder {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(ctx, 2, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), flags)
        val start = PendingIntent.getActivity(ctx, 4, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("start", true), flags)
        fun act(action: String, code: Int) = PendingIntent.getBroadcast(ctx, code, Intent(ctx, ReminderReceiver::class.java).setAction(action).putExtra("date", n.missed.toString()), flags)
        val b = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_heart).setColor(0xFF0B8573.toInt())
            .setContentTitle(n.title).setContentText(n.body).setLargeIcon(mascot()).setContentIntent(open).setAutoCancel(true)
        val pic = n.session?.let { card(ctx, it, e) }
        if (pic != null) b.setStyle(NotificationCompat.BigPictureStyle().bigPicture(pic).setSummaryText(n.body)) else b.setStyle(NotificationCompat.BigTextStyle().bigText(n.body))
        when (n.kind) {
            "back" -> { b.addAction(0, "Start run", start); b.addAction(0, "Not today", act(SKIP, 5)) }
            "free" -> { b.addAction(0, "Run it today", act(MOVE, 6)); b.addAction(0, "Let it go", act(SKIP, 5)) }
            "streak" -> {}
            else -> b.addAction(0, "Start run", start)
        }
        return b
    }

    /** Answers a missed day straight from the notification: writes the choice to the app's file and tidies the notification away. */
    fun answer(ctx: Context, action: String, date: LocalDate) {
        val store = Store(ctx); val d = store.load(); val s = d.settings
        val e = Engine(s, d.runs, LocalDate.now())
        val m = e.missedOn(date)
        val over = if (action == MOVE && m?.moveTo != null) s.overrides + (date to "skip") + (m.moveTo to m.session.kind) else s.overrides + (date to "skip")
        val nd = d.copy(settings = s.copy(overrides = over))
        store.save(nd)
        ctx.getSystemService(NotificationManager::class.java)?.cancel(ID)
        if (action == MOVE) show(ctx)                       // now that the run is on for today, say so, with a Start button
    }

    /** Dil, drawn plainly for the small picture beside the text. */
    fun mascot(size: Int = 192): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888); val c = Canvas(bmp); val u = size / 100f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFF008C72.toInt()
        c.drawPath(Path().apply { moveTo(50 * u, 90 * u); cubicTo(22 * u, 68 * u, 6 * u, 52 * u, 6 * u, 33 * u); cubicTo(6 * u, 19 * u, 17 * u, 9 * u, 30 * u, 9 * u); cubicTo(38 * u, 9 * u, 45 * u, 13 * u, 50 * u, 20 * u)
            cubicTo(55 * u, 13 * u, 62 * u, 9 * u, 70 * u, 9 * u); cubicTo(83 * u, 9 * u, 94 * u, 19 * u, 94 * u, 33 * u); cubicTo(94 * u, 52 * u, 78 * u, 68 * u, 50 * u, 90 * u); close() }, p)
        p.color = 0xFFFFFFFF.toInt(); c.drawCircle(36 * u, 40 * u, 9 * u, p); c.drawCircle(64 * u, 40 * u, 9 * u, p)
        p.color = 0xFF10201D.toInt(); c.drawCircle(37 * u, 41 * u, 4.5f * u, p); c.drawCircle(65 * u, 41 * u, 4.5f * u, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 4 * u; p.strokeCap = Paint.Cap.ROUND
        c.drawArc(RectF(38 * u, 46 * u, 62 * u, 66 * u), 20f, 140f, false, p)
        return bmp
    }

    /** The picture shown when the notification is pulled open: today's run on a frosted panel over the app's greens and gold. */
    fun card(ctx: Context, x: Session, e: Engine): Bitmap {
        val w = 1080; val h = 500
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val all = RectF(0f, 0f, w.toFloat(), h.toFloat())
        c.drawColor(0xFF0B3A34.toInt())
        p.shader = RadialGradient(0f, 0f, w * .75f, intArrayOf(0xFF19B89E.toInt(), 0x0019B89E), null, Shader.TileMode.CLAMP); c.drawRect(all, p)
        p.shader = RadialGradient(w.toFloat(), h.toFloat(), w * .6f, intArrayOf(0xFFFFD166.toInt(), 0x00FFD166), null, Shader.TileMode.CLAMP); c.drawRect(all, p)
        p.shader = null
        val panel = RectF(48f, 48f, w - 48f, h - 48f)
        p.color = 0x33FFFFFF; c.drawRoundRect(panel, 44f, 44f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = 0x80FFFFFF.toInt(); c.drawRoundRect(panel, 44f, 44f, p)
        p.style = Paint.Style.FILL; p.color = 0xFFFFFFFF.toInt()
        val body = try { ResourcesCompat.getFont(ctx, R.font.barlow_medium) } catch (x: Exception) { null }
        val big = try { ResourcesCompat.getFont(ctx, R.font.barlowcondensed_bold) } catch (x: Exception) { null }
        p.typeface = body; p.textSize = 46f; p.letterSpacing = .06f
        c.drawText("TODAY · ${x.title.uppercase()}", 100f, 150f, p)
        p.typeface = big; p.textSize = 170f; p.letterSpacing = 0f
        c.drawText("${x.min} min", 100f, 310f, p)
        p.typeface = body; p.textSize = 48f
        val cue = when { x.kind == "talk" -> "find your Zone 2 ceiling"; x.type in setOf("tempo", "intervals") -> "hard day"; x.type == "race" -> "race day"; e.s.hrMeds -> "full-sentence effort"; else -> "under ${e.zb[2]} bpm" }
        val temp = e.wx?.at(e.today, e.usualRunMin ?: e.nudgeMin)?.let { " · ${Math.round(it.first)}° out" } ?: ""
        c.drawText(cue + temp, 100f, 395f, p)
        return bmp
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Reminders.SKIP, Reminders.MOVE -> runCatching { LocalDate.parse(intent.getStringExtra("date")) }.getOrNull()?.let { Reminders.answer(ctx, intent.action!!, it) }
            // After a restart the alarms are gone, so set them again.
            Intent.ACTION_BOOT_COMPLETED -> Reminders.schedule(ctx)
            // The daily tick: show what there is to say, then set tomorrow's.
            else -> { Reminders.show(ctx, intent.getBooleanExtra("late", false)); Reminders.schedule(ctx) }
        }
    }
}
