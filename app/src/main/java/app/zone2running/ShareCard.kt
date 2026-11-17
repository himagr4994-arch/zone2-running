package app.zone2running

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * The picture a run is shared as. It says three things and nothing else: how far, how long, how fast.
 * Four looks to choose from; the last one puts the numbers over a photo of the runner's own.
 */
object ShareCard {
    const val W = 1080; const val H = 1350
    val STYLES = listOf("Track", "Sunrise", "Route", "Your photo")
    const val PHOTO = 3

    private class Ink(ctx: Context) {
        val bold = ResourcesCompat.getFont(ctx, R.font.barlowcondensed_bold) ?: Typeface.DEFAULT_BOLD
        val semi = ResourcesCompat.getFont(ctx, R.font.barlowcondensed_semibold) ?: Typeface.DEFAULT_BOLD
        val body = ResourcesCompat.getFont(ctx, R.font.barlow_semibold) ?: Typeface.DEFAULT
        fun text(color: Long, size: Float, face: Typeface, align: Paint.Align = Paint.Align.LEFT, spacing: Float = 0f) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toInt(); textSize = size; typeface = face; textAlign = align; letterSpacing = spacing }
        fun fill(color: Long) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toInt() }
        fun stroke(color: Long, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toInt(); style = Paint.Style.STROKE; strokeWidth = width; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    }

    fun render(ctx: Context, r: Run, style: Int, photo: Bitmap? = null): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp); val k = Ink(ctx)
        val dist = f2(U.k(r.distKm)); val unit = U.unit.uppercase()
        val time = fmtDur(r.durSec); val pace = fmtPace(paceOf(r)); val per = U.per.uppercase()
        when (style) {
            0 -> track(cv, k, dist, unit, time, pace, per)
            1 -> sunrise(cv, k, dist, unit, time, pace, per)
            2 -> route(cv, k, r, dist, unit, time, pace, per)
            else -> photo(cv, k, photo, dist, unit, time, pace, per)
        }
        return bmp
    }

    /** The largest size, up to [max], at which the distance and its unit still fit across the card. */
    private fun fit(k: Ink, s: String, max: Float): Float { var size = max; while (size > 160f && k.text(0, size, k.bold).measureText(s) > W - 84f - 76f - 150f) size -= 10f; return size }

    private fun mark(cv: Canvas, k: Ink, color: Long, x: Float = 84f, y: Float = 130f) =
        cv.drawText("ZONE 2 RUNNING", x, y, k.text(color, 36f, k.bold, spacing = .12f))

    /** Two smaller figures side by side, each with its label underneath. */
    private fun pair(cv: Canvas, k: Ink, y: Float, time: String, pace: String, per: String, ink: Long, dim: Long, size: Float = 130f) {
        cv.drawText(time, 84f, y, k.text(ink, size, k.bold)); cv.drawText("TIME", 84f, y + 52f, k.text(dim, 30f, k.body, spacing = .14f))
        cv.drawText(pace, 600f, y, k.text(ink, size, k.bold)); cv.drawText("PACE $per", 600f, y + 52f, k.text(dim, 30f, k.body, spacing = .14f))
    }

    /** Deep green, with the lanes of a running track sweeping in from the corner and one runner on them. */
    private fun track(cv: Canvas, k: Ink, dist: String, unit: String, time: String, pace: String, per: String) {
        cv.drawColor(0xFF0B3A34.toInt())
        val cx = W * 1.02f; val cy = H * 1.06f
        for (i in 0..7) { val rad = 330f + i * 92f; cv.drawOval(RectF(cx - rad, cy - rad * .92f, cx + rad, cy + rad * .92f), k.stroke(if (i % 2 == 0) 0xFF14504A else 0xFF10463F, 46f)) }
        // One lane is lit, and a dot is somewhere along it.
        val lane = 330f + 3 * 92f; val box = RectF(cx - lane, cy - lane * .92f, cx + lane, cy + lane * .92f)
        cv.drawArc(box, 200f, 52f, false, k.stroke(0xFF3CC4AD, 46f).apply { strokeCap = Paint.Cap.BUTT })
        val a = Math.toRadians(252.0); cv.drawCircle(cx + lane * cos(a).toFloat(), cy + lane * .92f * sin(a).toFloat(), 30f, k.fill(0xFFFFD166))
        mark(cv, k, 0xFF9CC3BB)
        val big = fit(k, dist, 380f)
        cv.drawText(dist, 76f, 520f, k.text(0xFFEAF6F3, big, k.bold))
        cv.drawText(unit, 84f + k.text(0, big, k.bold).measureText(dist) + 14f, 520f, k.text(0xFF9CC3BB, 84f, k.semi))
        pair(cv, k, 740f, time, pace, per, 0xFFEAF6F3, 0xFF9CC3BB)
    }

    /** First light over layered hills, with a road winding toward the sun. */
    private fun sunrise(cv: Canvas, k: Ink, dist: String, unit: String, time: String, pace: String, per: String) {
        cv.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, 980f, intArrayOf(0xFF1A2447.toInt(), 0xFF6B3A6B.toInt(), 0xFFE8735A.toInt(), 0xFFFFC56B.toInt()), floatArrayOf(0f, .45f, .8f, 1f), Shader.TileMode.CLAMP) })
        cv.drawCircle(800f, 900f, 250f, k.fill(0x33FFE9A8)); cv.drawCircle(800f, 900f, 150f, k.fill(0xFFFFE9A8))
        fun hill(base: Float, amp: Float, phase: Double, wave: Double, color: Long) {
            val p = Path(); p.moveTo(0f, H.toFloat())
            for (x in 0..W step 12) p.lineTo(x.toFloat(), base + amp * sin(x / wave + phase).toFloat() + amp * .4f * sin(x / (wave * .37) + phase * 2).toFloat())
            p.lineTo(W.toFloat(), H.toFloat()); p.close(); cv.drawPath(p, k.fill(color))
        }
        hill(930f, 46f, .6, 210.0, 0xFF8E4460); hill(1020f, 60f, 2.2, 260.0, 0xFF58294F); hill(1130f, 44f, 4.0, 330.0, 0xFF1F1530)
        // The road, widest at your feet.
        val road = Path(); road.moveTo(300f, H.toFloat()); road.cubicTo(520f, 1290f, 330f, 1210f, 590f, 1150f); road.cubicTo(740f, 1110f, 660f, 1060f, 770f, 1030f)
        cv.drawPath(road, k.stroke(0x55FFE9A8, 10f).apply { pathEffect = DashPathEffect(floatArrayOf(34f, 26f), 0f) })
        mark(cv, k, 0xCCFFFFFF)
        val big = fit(k, dist, 360f)
        cv.drawText(dist, 76f, 500f, k.text(0xFFFFFFFF, big, k.bold))
        cv.drawText(unit, 84f + k.text(0, big, k.bold).measureText(dist) + 14f, 500f, k.text(0xDDFFFFFF, 84f, k.semi))
        pair(cv, k, 700f, time, pace, per, 0xFFFFFFFF, 0xCCFFFFFF, 124f)
    }

    /** Black, with the run's own path as the picture. Without a path it draws the heart-rate line instead. */
    private fun route(cv: Canvas, k: Ink, r: Run, dist: String, unit: String, time: String, pace: String, per: String) {
        cv.drawColor(0xFF0D0F10.toInt())
        val box = RectF(110f, 220f, W - 110f, 820f)
        val pts = ArrayList<Pair<Double, Double>>()
        if (r.route.size >= 6) for (i in 0 until r.route.size / 3) pts += mercX(r.route[i * 3 + 2] / 1e5) to mercY(r.route[i * 3 + 1] / 1e5)
        else r.trace.filter { it > 0 }.takeIf { it.size >= 20 }?.forEachIndexed { i, v -> pts += i.toDouble() to -v.toDouble() }
        if (pts.size < 2) for (i in 0..80) pts += i.toDouble() to -(sin(i / 6.0) * 6 + sin(i / 2.3) * 2 + i * .12)      // nothing recorded: a plain rolling line
        val x0 = pts.minOf { it.first }; val x1 = pts.maxOf { it.first }; val y0 = pts.minOf { it.second }; val y1 = pts.maxOf { it.second }
        val isRoute = r.route.size >= 6
        val sx = box.width() / (x1 - x0).coerceAtLeast(1e-9); val sy = box.height() / (y1 - y0).coerceAtLeast(1e-9)
        val s = if (isRoute) min(sx, sy) else sx
        val syy = if (isRoute) s else sy * .7
        val ox = box.centerX() - ((x0 + x1) / 2 * s).toFloat(); val oy = box.centerY() - ((y0 + y1) / 2 * syy).toFloat()
        val path = Path()
        pts.forEachIndexed { i, (x, y) -> val px = ox + (x * s).toFloat(); val py = oy + (y * syy).toFloat(); if (i == 0) path.moveTo(px, py) else path.lineTo(px, py) }
        cv.drawPath(path, k.stroke(0x333CC4AD, 44f)); cv.drawPath(path, k.stroke(0x663CC4AD, 24f)); cv.drawPath(path, k.stroke(0xFF5BE0C6, 10f))
        val first = pts.first(); val last = pts.last()
        cv.drawCircle(ox + (first.first * s).toFloat(), oy + (first.second * syy).toFloat(), 15f, k.stroke(0xFFFFFFFF, 7f))
        cv.drawCircle(ox + (last.first * s).toFloat(), oy + (last.second * syy).toFloat(), 19f, k.fill(0xFFFFD166))
        mark(cv, k, 0xFF8DA29D)
        // Three figures in a row along the bottom, the same weight each.
        val colW = (W - 168f) / 3
        val cols = listOf(Triple(dist, unit, 84f), Triple(time, "TIME", 84f + colW), Triple(pace, "PACE $per", 84f + 2 * colW))
        // A run over an hour has a longer time: shrink all three together until the widest fits its column.
        var size = 132f
        while (size > 70f && cols.maxOf { k.text(0, size, k.bold).measureText(it.first) } > colW - 70f) size -= 4f
        cv.drawRect(84f, 960f, W - 84f, 963f, k.fill(0xFF26322F))
        for ((v, l, x) in cols) { cv.drawText(v, x, 1140f, k.text(0xFFFFFFFF, size, k.bold)); cv.drawText(l, x, 1196f, k.text(0xFF8DA29D, 30f, k.body, spacing = .14f)) }
    }

    /** The runner's own photo, filling the card, with the numbers along the bottom. */
    private fun photo(cv: Canvas, k: Ink, photo: Bitmap?, dist: String, unit: String, time: String, pace: String, per: String) {
        if (photo == null) {
            cv.drawColor(0xFF16201E.toInt())
            cv.drawRoundRect(RectF(84f, 84f, W - 84f, 900f), 36f, 36f, k.stroke(0xFF3A4A46, 6f).apply { pathEffect = DashPathEffect(floatArrayOf(28f, 22f), 0f) })
            cv.drawText("Your photo goes here", W / 2f, 500f, k.text(0xFF8DA29D, 56f, k.semi, Paint.Align.CENTER))
        } else {
            // Fill the card, cropping whatever doesn't fit, centred.
            val scale = maxOf(W / photo.width.toFloat(), H / photo.height.toFloat())
            val sw = (W / scale).toInt(); val sh = (H / scale).toInt()
            val sx = (photo.width - sw) / 2; val sy = (photo.height - sh) / 2
            cv.drawBitmap(photo, Rect(sx, sy, sx + sw, sy + sh), Rect(0, 0, W, H), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            cv.drawRect(0f, 0f, W.toFloat(), 260f, Paint().apply { shader = LinearGradient(0f, 0f, 0f, 260f, 0x88000000.toInt(), 0, Shader.TileMode.CLAMP) })
            cv.drawRect(0f, 640f, W.toFloat(), H.toFloat(), Paint().apply { shader = LinearGradient(0f, 640f, 0f, H.toFloat(), intArrayOf(0, 0x99000000.toInt(), 0xE6000000.toInt()), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP) })
        }
        mark(cv, k, 0xE6FFFFFF)
        cv.drawText(dist, 78f, 1070f, k.text(0xFFFFFFFF, 250f, k.bold))
        cv.drawText(unit, 84f + k.text(0, 250f, k.bold).measureText(dist) + 12f, 1070f, k.text(0xDDFFFFFF, 70f, k.semi))
        pair(cv, k, 1220f, time, pace, per, 0xFFFFFFFF, 0xB3FFFFFF, 104f)
    }

    private fun mercX(lon: Double) = (lon + 180) / 360
    private fun mercY(lat: Double) = (1 - ln(tan(Math.toRadians(lat)) + 1 / cos(Math.toRadians(lat))) / PI) / 2

    /** Reads a picked photo at a sensible size, turned the right way up. */
    fun loadPhoto(ctx: Context, uri: Uri): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 28) ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val big = maxOf(info.size.width, info.size.height); if (big > 2400) d.setTargetSampleSize(big / 1600)
        } else ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 }) }
    } catch (e: Exception) { null }

    fun share(ctx: Context, r: Run, style: Int, photo: Bitmap? = null) {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val jpg = style == PHOTO                                       // a photo as PNG would be several megabytes
        val file = File(dir, if (jpg) "zone2-run.jpg" else "zone2-run.png")
        file.outputStream().use { render(ctx, r, style, photo).compress(if (jpg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, 92, it) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).setType(if (jpg) "image/jpeg" else "image/png").putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, "${U.dist(r.distKm)} in ${fmtDur(r.durSec)}, ${fmtPace(paceOf(r))} ${U.per}")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "Share run").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
