package app.zone2running

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import java.io.File

/** A picture of the run for WhatsApp or Instagram: 1080 × 1350, drawn by hand so it needs no network. */
object ShareCard {
    fun render(ctx: Context, c: Celebration): Bitmap {
        val w = 1080; val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val display = ResourcesCompat.getFont(ctx, R.font.barlowcondensed_bold) ?: Typeface.DEFAULT_BOLD
        val semi = ResourcesCompat.getFont(ctx, R.font.barlowcondensed_semibold) ?: Typeface.DEFAULT_BOLD
        val body = ResourcesCompat.getFont(ctx, R.font.barlow_medium) ?: Typeface.DEFAULT
        fun paint(color: Long, size: Float, face: Typeface, align: Paint.Align = Paint.Align.LEFT) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toInt(); textSize = size; typeface = face; textAlign = align }
        val ink = 0xFFEAF6F3; val muted = 0xFF9CC3BB; val teal = 0xFF3CC4AD; val track = 0xFF1C554D; val mark = 0xFFFFD166
        cv.drawColor(0xFF0B3A34.toInt())

        val m = 84f
        cv.drawText("ZONE 2 RUNNING", m, 150f, paint(ink, 54f, display).apply { letterSpacing = .06f })
        val r = c.run
        cv.drawText("${DOW[r.date.dayOfWeek.value - 1]} ${fmtDay(r.date)} · ${TYPE_NAMES[r.type] ?: "Run"}".uppercase(), m, 215f, paint(muted, 34f, body).apply { letterSpacing = .08f })

        // little heart, top right
        val hp = Path(); val s = 1.5f; val ox = w - m - 100 * s; val oy = 70f
        hp.moveTo(ox + 50 * s, oy + 88 * s); hp.cubicTo(ox + 10 * s, oy + 60 * s, ox + 2 * s, oy + 30 * s, ox + 25 * s, oy + 18 * s)
        hp.cubicTo(ox + 40 * s, oy + 10 * s, ox + 50 * s, oy + 22 * s, ox + 50 * s, oy + 30 * s)
        hp.cubicTo(ox + 50 * s, oy + 22 * s, ox + 60 * s, oy + 10 * s, ox + 75 * s, oy + 18 * s)
        hp.cubicTo(ox + 98 * s, oy + 30 * s, ox + 90 * s, oy + 60 * s, ox + 50 * s, oy + 88 * s); hp.close()
        cv.drawPath(hp, paint(teal, 0f, body))

        cv.drawText(fmtPace(paceOf(r)), m, 520f, paint(ink, 300f, display))
        val pw = paint(ink, 300f, display).measureText(fmtPace(paceOf(r)))
        cv.drawText("/km", m + pw + 24f, 520f, paint(muted, 76f, semi))

        val stats = listOf(f2(r.distKm) to "KM", fmtDur(r.durSec) to "TIME", (if (r.avgHr > 0) "${r.avgHr}" else "–") to (if (c.zone > 0) "BPM · Z${c.zone}" else "BPM"))
        stats.forEachIndexed { i, (v, l) ->
            val x = m + i * (w - 2 * m) / 3f
            cv.drawText(v, x, 690f, paint(ink, 96f, semi)); cv.drawText(l, x, 740f, paint(muted, 32f, body).apply { letterSpacing = .08f })
        }

        cv.drawRect(m, 820f, w - m, 822f, paint(track, 0f, body))
        if (c.current != null) {
            cv.drawText("ZONE 2 PACE NOW", m, 900f, paint(muted, 32f, body).apply { letterSpacing = .08f })
            cv.drawText(fmtPace(c.current), m, 1010f, paint(ink, 120f, display))
            cv.drawText("TARGET", w - m, 900f, paint(muted, 32f, body, Paint.Align.RIGHT).apply { letterSpacing = .08f })
            cv.drawText(fmtPace(c.target.toDouble()), w - m, 1010f, paint(ink, 120f, display, Paint.Align.RIGHT))
            val gap = c.current - c.target; val start = maxOf(c.current, c.target + 90.0)
            val frac = (1 - gap / (start - c.target)).coerceIn(0.03, 1.0).toFloat()
            cv.drawRoundRect(RectF(m, 1060f, w - m, 1084f), 12f, 12f, paint(track, 0f, body))
            cv.drawRoundRect(RectF(m, 1060f, m + (w - 2 * m) * frac, 1084f), 12f, 12f, paint(teal, 0f, body))
            cv.drawRoundRect(RectF(m + (w - 2 * m) * frac - 8, 1042f, m + (w - 2 * m) * frac + 8, 1102f), 8f, 8f, paint(mark, 0f, body))
        }
        val g = c.game
        cv.drawText("Level ${g.level} · ${levelName(g.level)}", m, 1230f, paint(ink, 50f, semi))
        cv.drawText(if (g.streak > 0) "${g.streak}-week streak" else "+${c.xp} XP", w - m, 1230f, paint(mark, 50f, semi, Paint.Align.RIGHT))
        cv.drawText("Easy running, measured.", m, 1290f, paint(muted, 32f, body))
        return bmp
    }

    fun share(ctx: Context, c: Celebration) {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "zone2-run.png")
        file.outputStream().use { render(ctx, c).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, c.share)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "Share run").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
