package app.zone2running

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
 *
 * The first two looks are drawn from the run's own heart-rate line, so no two runs ever produce the same
 * picture. Three put the numbers on a photo of the runner's choosing. The last has no background at all:
 * it is meant to be laid over a photo or video inside Instagram.
 */
object ShareCard {
    const val W = 1080
    val STYLES = listOf("Pulse", "Ridges", "Route", "Sunrise", "Track", "Cutout", "Duotone", "Photo", "Overlay")
    val USES_PHOTO = setOf(5, 6, 7)
    const val OVERLAY = 8
    /** Stories are 9:16, feed posts 4:5. The overlay is a square with nothing behind it. */
    fun height(style: Int, story: Boolean) = if (style == OVERLAY) 1080 else if (story) 1920 else 1350

    private class Ink(ctx: Context) {
        val bold = ResourcesCompat.getFont(ctx, R.font.barlowcondensed_bold) ?: Typeface.DEFAULT_BOLD
        val semi = ResourcesCompat.getFont(ctx, R.font.barlowcondensed_semibold) ?: Typeface.DEFAULT_BOLD
        val body = ResourcesCompat.getFont(ctx, R.font.barlow_semibold) ?: Typeface.DEFAULT
        fun text(color: Long, size: Float, face: Typeface, align: Paint.Align = Paint.Align.LEFT, spacing: Float = 0f) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toInt(); textSize = size; typeface = face; textAlign = align; letterSpacing = spacing }
        fun fill(color: Long) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toInt() }
        fun stroke(color: Long, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toInt(); style = Paint.Style.STROKE; strokeWidth = width; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    }

    /** Everything a look needs: the canvas, where the safe area starts and ends, and the three figures. */
    private class Card(val cv: Canvas, val k: Ink, val h: Float, story: Boolean, val r: Run, val zone: (Int) -> Int) {
        // Instagram covers the top and bottom of a story with its own controls: keep clear of both.
        val top = if (story) 250f else 70f
        val bottom = if (story) h - 300f else h - 80f
        val dist = f2(U.k(r.distKm)); val unit = U.unit.uppercase()
        val time = fmtDur(r.durSec); val pace = fmtPace(paceOf(r)); val per = U.per.uppercase()
        fun mark(color: Long) = cv.drawText("ZONE 2 RUNNING", 84f, top + 60f, k.text(color, 36f, k.bold, spacing = .12f))
        /** The distance, as large as fits, with its unit after it. Returns the size used. */
        fun big(y: Float, max: Float, ink: Long, dim: Long): Float {
            var size = max; while (size > 160f && k.text(0, size, k.bold).measureText(dist) > W - 310f) size -= 10f
            cv.drawText(dist, 76f, y, k.text(ink, size, k.bold))
            cv.drawText(unit, 84f + k.text(0, size, k.bold).measureText(dist) + 14f, y, k.text(dim, 84f, k.semi)); return size
        }
        fun pair(y: Float, ink: Long, dim: Long, size: Float = 128f) {
            cv.drawText(time, 84f, y, k.text(ink, size, k.bold)); cv.drawText("TIME", 84f, y + 52f, k.text(dim, 30f, k.body, spacing = .14f))
            cv.drawText(pace, 600f, y, k.text(ink, size, k.bold)); cv.drawText("PACE $per", 600f, y + 52f, k.text(dim, 30f, k.body, spacing = .14f))
        }
        /** Three figures of equal weight in a row, shrunk together until the widest fits its column. */
        fun three(y: Float, ink: Long, dim: Long, shadow: Boolean = false) {
            val colW = (W - 168f) / 3
            val cols = listOf(Triple(dist, unit, 84f), Triple(time, "TIME", 84f + colW), Triple(pace, "PACE $per", 84f + 2 * colW))
            var size = 136f
            while (size > 70f && cols.maxOf { k.text(0, size, k.bold).measureText(it.first) } > colW - 70f) size -= 4f
            for ((v, l, x) in cols) {
                cv.drawText(v, x, y, k.text(ink, size, k.bold).apply { if (shadow) setShadowLayer(18f, 0f, 4f, 0x99000000.toInt()) })
                cv.drawText(l, x, y + 54f, k.text(dim, 30f, k.body, spacing = .14f).apply { if (shadow) setShadowLayer(12f, 0f, 3f, 0x99000000.toInt()) })
            }
        }
    }

    private val ZONE_INK = longArrayOf(0xFF8FA6B8, 0xFF3CC4AD, 0xFFE0B63A, 0xFFF08A4B, 0xFFEF6A6F)

    /**
     * The run as a line of [n] points: its heart rate where there is a trace, its splits otherwise. A run with
     * neither still gets a shape, grown from its own numbers, so the picture is never blank and never borrowed.
     */
    private fun series(r: Run, n: Int): IntArray {
        val src = r.trace.filter { it > 0 }.takeIf { it.size >= 30 } ?: r.splits.filter { it.hr > 0 }.map { it.hr }.takeIf { it.size >= 4 }
        if (src == null) {
            val seed = (r.distKm * 1000 + r.durSec).toInt(); val base = if (r.avgHr > 0) r.avgHr else 140
            return IntArray(n) { i -> val t = i / n.toDouble()
                (base - 18 + 30 * min(1.0, t * 5) + 9 * sin(t * (9 + seed % 7)) + 5 * sin(t * (31 + seed % 11) + seed % 5) + 6 * t).toInt() }
        }
        return IntArray(n) { i ->
            val a = i * src.size / n; val b = maxOf(a + 1, (i + 1) * src.size / n)
            src.subList(a, min(b, src.size)).average().toInt()
        }
    }

    fun render(ctx: Context, r: Run, style: Int, photo: Bitmap? = null, story: Boolean = false, scale: Float = 1f, zone: (Int) -> Int = { 2 }): Bitmap {
        val h = height(style, story)
        val bmp = Bitmap.createBitmap((W * scale).toInt(), (h * scale).toInt(), Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp); cv.scale(scale, scale)
        val c = Card(cv, Ink(ctx), h.toFloat(), story && style != OVERLAY, r, zone)
        when (style) {
            0 -> pulse(c); 1 -> ridges(c); 2 -> route(c); 3 -> sunrise(c); 4 -> track(c)
            5 -> cutout(c, photo); 6 -> photo(c, photo, duotone = true); 7 -> photo(c, photo, duotone = false)
            else -> overlay(c)
        }
        return bmp
    }

    /**
     * Heart rate wound once round a circle: the run starts at twelve o'clock and goes clockwise, each spoke
     * as long as the heart was fast and coloured by its zone. Every run draws a different sun.
     */
    private fun pulse(c: Card) = with(c) {
        cv.drawColor(0xFF090B0C.toInt())
        val cy = top + (bottom - top) * .42f; val cx = W / 2f
        val r0 = 236f; val amp = if (h > 1500) 250f else 215f
        cv.drawCircle(cx, cy, r0 + amp * .55f, Paint().apply { shader = RadialGradient(cx, cy, r0 + amp * 1.2f, intArrayOf(0x333CC4AD, 0x00000000), null, Shader.TileMode.CLAMP) })
        val n = 240; val s = series(r, n); val lo = s.min(); val hi = maxOf(s.max(), lo + 8)
        for (i in 0 until n) {
            val a = -PI / 2 + 2 * PI * i / n
            val len = 26f + amp * ((s[i] - lo) / (hi - lo).toFloat())
            val col = ZONE_INK[(zone(s[i]) - 1).coerceIn(0, 4)]
            cv.drawLine(cx + (r0 * cos(a)).toFloat(), cy + (r0 * sin(a)).toFloat(), cx + ((r0 + len) * cos(a)).toFloat(), cy + ((r0 + len) * sin(a)).toFloat(), k.stroke(col, 7.5f))
        }
        cv.drawCircle(cx, cy - r0 + 2f, 9f, k.fill(0xFFFFFFFF))                 // where the run began
        mark(0xFF8DA29D)
        var size = 190f; while (k.text(0, size, k.bold).measureText(dist) > r0 * 1.66f) size -= 6f
        cv.drawText(dist, cx, cy + size * .28f, k.text(0xFFFFFFFF, size, k.bold, Paint.Align.CENTER))
        cv.drawText(unit, cx, cy + size * .28f + 62f, k.text(0xFF8DA29D, 40f, k.body, Paint.Align.CENTER, .2f))
        pair(bottom - 70f, 0xFFFFFFFF, 0xFF8DA29D)
    }

    /**
     * The run cut into strips and laid one in front of another like ranges of hills fading into the distance:
     * the start is the far range, the finish the nearest. Each range is as tall as the heart was fast in that
     * stretch and takes the colour of its zone.
     */
    private fun ridges(c: Card) = with(c) {
        val sky = 0xFF0E1522
        cv.drawRect(0f, 0f, W.toFloat(), h, Paint().apply { shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(0xFF1B2740.toInt(), sky.toInt()), null, Shader.TileMode.CLAMP) })
        val rows = if (h > 1500) 13 else 11; val per = 48
        val s = series(r, rows * per); val lo = s.min(); val hi = maxOf(s.max(), lo + 8)
        val y0 = top + 430f; val y1 = bottom - 250f; val gap = (y1 - y0) / (rows - 1)
        fun mix(a: Long, b: Long, f: Float): Long {
            fun ch(sh: Int): Long = (((a shr sh) and 0xFFL) * (1 - f) + ((b shr sh) and 0xFFL) * f).toLong()
            return 0xFF000000L or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
        for (row in 0 until rows) {
            val base = y0 + row * gap; val shape = Path(); val edge = Path(); shape.moveTo(0f, h)
            val avg = s.slice(row * per until (row + 1) * per).average().toInt()
            val colour = ZONE_INK[(zone(avg) - 1).coerceIn(0, 4)]
            val depth = 1f - row / (rows - 1f)                                            // 1 = far away, 0 = right in front
            for (j in 0 until per) {
                val x = W * j / (per - 1f)
                val level = (s[row * per + j] - lo) / (hi - lo).toFloat()
                // Height is the heart rate. The rolling outline is texture, seeded by the same numbers, so a run always draws the same hills.
                val seed = avg * .21 + row * 2.3
                val roll = (sin(j * .21 + seed) + .5 * sin(j * .47 + seed * 1.6) + .25 * sin(j * 1.1 + row)).toFloat()
                val y = base - gap * (.5f + 2.6f * level) - gap * .55f * roll
                if (j == 0) { shape.lineTo(x, y); edge.moveTo(x, y) } else { shape.lineTo(x, y); edge.lineTo(x, y) }
            }
            shape.lineTo(W.toFloat(), h); shape.close()
            cv.drawPath(shape, k.fill(mix(colour, sky, .25f + .55f * depth)))             // far ranges fade into the sky
            cv.drawPath(edge, k.stroke(mix(colour, 0xFFFFFFFF, .35f * (1 - depth)), 3f))
        }
        cv.drawRect(0f, bottom - 330f, W.toFloat(), h, Paint().apply { shader = LinearGradient(0f, bottom - 330f, 0f, bottom - 120f, 0, 0xE60A0F18.toInt(), Shader.TileMode.CLAMP) })
        mark(0xCCFFFFFF)
        big(top + 330f, 300f, 0xFFFFFFFF, 0xCCFFFFFF)
        pair(bottom - 70f, 0xFFFFFFFF, 0xCCFFFFFF, 116f)
    }

    /** Black, with the run's own path as the picture. Without a path it draws the heart-rate line instead. */
    private fun route(c: Card) = with(c) {
        cv.drawColor(0xFF0D0F10.toInt())
        drawPath(c, RectF(110f, top + 160f, W - 110f, bottom - 420f), 0xFF5BE0C6, 0x3CC4AD, 10f)
        mark(0xFF8DA29D)
        cv.drawRect(84f, bottom - 262f, W - 84f, bottom - 259f, k.fill(0xFF26322F))
        three(bottom - 78f, 0xFFFFFFFF, 0xFF8DA29D)
    }

    /** The route, or failing that the heart-rate line, fitted into a box with a soft glow behind it. */
    private fun drawPath(c: Card, box: RectF, line: Long, glow: Int, width: Float) = with(c) {
        val pts = ArrayList<Pair<Double, Double>>()
        val isRoute = r.route.size >= 6
        if (isRoute) for (i in 0 until r.route.size / 3) pts += mercX(r.route[i * 3 + 2] / 1e5) to mercY(r.route[i * 3 + 1] / 1e5)
        else series(r, 160).forEachIndexed { i, v -> pts += i.toDouble() to -v.toDouble() }
        val x0 = pts.minOf { it.first }; val x1 = pts.maxOf { it.first }; val y0 = pts.minOf { it.second }; val y1 = pts.maxOf { it.second }
        val sx = box.width() / (x1 - x0).coerceAtLeast(1e-9); val sy = box.height() / (y1 - y0).coerceAtLeast(1e-9)
        val s = if (isRoute) min(sx, sy) else sx; val syy = if (isRoute) s else sy * .6
        val ox = box.centerX() - ((x0 + x1) / 2 * s).toFloat(); val oy = box.centerY() - ((y0 + y1) / 2 * syy).toFloat()
        val path = Path()
        pts.forEachIndexed { i, (x, y) -> val px = ox + (x * s).toFloat(); val py = oy + (y * syy).toFloat(); if (i == 0) path.moveTo(px, py) else path.lineTo(px, py) }
        if (glow != 0) { cv.drawPath(path, k.stroke(0x33000000L or glow.toLong(), width * 4.4f)); cv.drawPath(path, k.stroke(0x66000000L or glow.toLong(), width * 2.4f)) }
        else cv.drawPath(path, k.stroke(0x66000000, width + 10f))                        // on a photo: a dark edge so a white line always reads
        cv.drawPath(path, k.stroke(line, width))
        val a = pts.first(); val b = pts.last()
        cv.drawCircle(ox + (a.first * s).toFloat(), oy + (a.second * syy).toFloat(), width * 1.5f, k.stroke(0xFFFFFFFF, width * .7f))
        cv.drawCircle(ox + (b.first * s).toFloat(), oy + (b.second * syy).toFloat(), width * 1.9f, k.fill(0xFFFFD166))
    }

    /** First light over layered hills, with a road winding toward the sun. */
    private fun sunrise(c: Card) = with(c) {
        cv.drawRect(0f, 0f, W.toFloat(), h, Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h * .73f, intArrayOf(0xFF1A2447.toInt(), 0xFF6B3A6B.toInt(), 0xFFE8735A.toInt(), 0xFFFFC56B.toInt()), floatArrayOf(0f, .45f, .8f, 1f), Shader.TileMode.CLAMP) })
        val sun = h * .667f
        cv.drawCircle(800f, sun, 250f, k.fill(0x33FFE9A8)); cv.drawCircle(800f, sun, 150f, k.fill(0xFFFFE9A8))
        fun hill(base: Float, amp: Float, phase: Double, wave: Double, color: Long) {
            val p = Path(); p.moveTo(0f, h)
            for (x in 0..W step 12) p.lineTo(x.toFloat(), base + amp * sin(x / wave + phase).toFloat() + amp * .4f * sin(x / (wave * .37) + phase * 2).toFloat())
            p.lineTo(W.toFloat(), h); p.close(); cv.drawPath(p, k.fill(color))
        }
        hill(h * .689f, 46f, .6, 210.0, 0xFF8E4460); hill(h * .756f, 60f, 2.2, 260.0, 0xFF58294F); hill(h * .837f, 44f, 4.0, 330.0, 0xFF1F1530)
        val road = Path(); road.moveTo(300f, h); road.cubicTo(520f, h * .955f, 330f, h * .896f, 590f, h * .852f); road.cubicTo(740f, h * .822f, 660f, h * .785f, 770f, h * .763f)
        cv.drawPath(road, k.stroke(0x55FFE9A8, 10f).apply { pathEffect = DashPathEffect(floatArrayOf(34f, 26f), 0f) })
        mark(0xCCFFFFFF)
        big(top + 430f, 360f, 0xFFFFFFFF, 0xDDFFFFFF); pair(top + 630f, 0xFFFFFFFF, 0xCCFFFFFF, 124f)
    }

    /** Deep green, with the lanes of a running track sweeping in from the corner and one runner on them. */
    private fun track(c: Card) = with(c) {
        cv.drawColor(0xFF0B3A34.toInt())
        val cx = W * 1.02f; val cy = h * 1.04f
        for (i in 0..9) { val rad = 330f + i * 92f; cv.drawOval(RectF(cx - rad, cy - rad * .92f, cx + rad, cy + rad * .92f), k.stroke(if (i % 2 == 0) 0xFF14504A else 0xFF10463F, 46f)) }
        val lane = 330f + 3 * 92f; val box = RectF(cx - lane, cy - lane * .92f, cx + lane, cy + lane * .92f)
        cv.drawArc(box, 200f, 52f, false, k.stroke(0xFF3CC4AD, 46f).apply { strokeCap = Paint.Cap.BUTT })
        val a = Math.toRadians(252.0); cv.drawCircle(cx + lane * cos(a).toFloat(), cy + lane * .92f * sin(a).toFloat(), 30f, k.fill(0xFFFFD166))
        mark(0xFF9CC3BB)
        big(top + 450f, 380f, 0xFFEAF6F3, 0xFF9CC3BB); pair(top + 670f, 0xFFEAF6F3, 0xFF9CC3BB)
    }

    /** Fills the card with the photo, cropping from the centre. */
    private fun cover(cv: Canvas, photo: Bitmap, h: Float, paint: Paint) {
        val scale = maxOf(W / photo.width.toFloat(), h / photo.height.toFloat())
        val sw = (W / scale).toInt(); val sh = (h / scale).toInt()
        val sx = (photo.width - sw) / 2; val sy = (photo.height - sh) / 2
        cv.drawBitmap(photo, Rect(sx, sy, sx + sw, sy + sh), Rect(0, 0, W, h.toInt()), paint)
    }

    private fun waiting(c: Card) = with(c) {
        cv.drawRoundRect(RectF(84f, top + 110f, W - 84f, bottom - 420f), 36f, 36f, k.stroke(0xFF4A5A56, 6f).apply { pathEffect = DashPathEffect(floatArrayOf(28f, 22f), 0f) })
        cv.drawText("Tap to add your photo", W / 2f, (top + bottom) / 2 - 150f, k.text(0xFF8DA29D, 56f, k.semi, Paint.Align.CENTER))
    }

    /**
     * The photo is only visible through the distance: the numerals are windows cut into a sheet of paper.
     * The less of the picture you can see, the more people look.
     */
    private fun cutout(c: Card, photo: Bitmap?) = with(c) {
        cv.drawColor(0xFFF2EDE2.toInt())
        mark(0xFF10201D)
        // Two rows of numerals, as tall as the card allows: the whole number, then the decimals.
        val whole = dist.substringBefore('.'); val part = "." + dist.substringAfter('.', "0")
        val room = (bottom - 300f) - (top + 120f)
        fun sized(s: String, max: Float): Float { var z = max; while (z > 200f && k.text(0, z, k.bold).measureText(s) > W - 150f) z -= 10f; return z }
        val z1 = sized(whole, room * .78f); val z2 = sized(part, min(z1, room * .62f))
        val y1 = top + 130f + z1 * .72f; val y2 = y1 + z2 * .78f
        val layer = cv.saveLayer(0f, 0f, W.toFloat(), h, null)
        cv.drawText(whole, 66f, y1, k.text(0xFF000000, z1, k.bold)); cv.drawText(part, 50f, y2, k.text(0xFF000000, z2, k.bold))
        val through = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) }
        if (photo != null) cover(cv, photo, h, through)
        else cv.drawRect(0f, 0f, W.toFloat(), h, through.apply { shader = LinearGradient(0f, top, W.toFloat(), bottom, intArrayOf(0xFF1A2447.toInt(), 0xFFE8735A.toInt(), 0xFFFFC56B.toInt()), null, Shader.TileMode.CLAMP) })
        cv.restoreToCount(layer)
        cv.drawText(unit, W - 84f, y2, k.text(0xFF10201D, 84f, k.semi, Paint.Align.RIGHT))
        pair(bottom - 70f, 0xFF10201D, 0xFF5B6B66)
    }

    /** The photo filling the card. As a duotone it is remade in two inks, deep green and cream, like a print. */
    private fun photo(c: Card, photo: Bitmap?, duotone: Boolean) = with(c) {
        if (photo == null) { cv.drawColor(0xFF16201E.toInt()); waiting(c) }
        else {
            val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            if (duotone) {
                // Brightness alone picks the colour: shadows go to green, highlights to cream.
                val lo = intArrayOf(0x07, 0x2E, 0x29); val hi = intArrayOf(0xFA, 0xEE, 0xD6)
                val m = FloatArray(20); val w = floatArrayOf(.299f, .587f, .114f)
                for (ch in 0..2) { val kk = (hi[ch] - lo[ch]) / 255f * 1.15f; for (j in 0..2) m[ch * 5 + j] = w[j] * kk; m[ch * 5 + 4] = lo[ch] - 14f }
                m[18] = 1f; p.colorFilter = ColorMatrixColorFilter(ColorMatrix(m))
            }
            cover(cv, photo, h, p)
            cv.drawRect(0f, 0f, W.toFloat(), top + 240f, Paint().apply { shader = LinearGradient(0f, 0f, 0f, top + 240f, 0x99000000.toInt(), 0, Shader.TileMode.CLAMP) })
            cv.drawRect(0f, h * .45f, W.toFloat(), h, Paint().apply { shader = LinearGradient(0f, h * .45f, 0f, h, intArrayOf(0, 0x99000000.toInt(), 0xEE000000.toInt()), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP) })
        }
        mark(0xE6FFFFFF)
        val ink = if (duotone) 0xFFFAEED6 else 0xFFFFFFFF
        big(bottom - 250f, if (duotone) 330f else 260f, ink, 0xDDFFFFFF); pair(bottom - 70f, ink, 0xB3FFFFFF, 108f)
    }

    /**
     * Nothing behind it at all: the path and the three figures in white, to be laid over a photo or a video
     * inside Instagram as a sticker.
     */
    private fun overlay(c: Card) = with(c) {
        drawPath(c, RectF(170f, 90f, W - 170f, 640f), 0xFFFFFFFF, 0, 13f)
        three(880f, 0xFFFFFFFF, 0xF2FFFFFF, shadow = true)
        cv.drawText("ZONE 2 RUNNING", W / 2f, 1020f, k.text(0xD9FFFFFF, 30f, k.bold, Paint.Align.CENTER, .16f).apply { setShadowLayer(10f, 0f, 2f, 0x99000000.toInt()) })
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

    /** A photo behind the numbers is saved as JPEG, since as PNG it would be several megabytes. The overlay must stay PNG to keep its transparency. */
    private fun jpeg(style: Int, photo: Bitmap?) = style in USES_PHOTO && photo != null

    fun share(ctx: Context, r: Run, style: Int, photo: Bitmap? = null, story: Boolean = false, zone: (Int) -> Int = { 2 }) {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val jpg = jpeg(style, photo)
        val file = File(dir, if (jpg) "zone2-run.jpg" else "zone2-run.png")
        file.outputStream().use { render(ctx, r, style, photo, story, 1f, zone).compress(if (jpg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, 93, it) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).setType(if (jpg) "image/jpeg" else "image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "Share run").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Puts the picture in the phone's gallery, under Pictures/Zone 2 Running. Returns false where Android doesn't allow that without a storage permission. */
    fun save(ctx: Context, r: Run, style: Int, photo: Bitmap? = null, story: Boolean = false, zone: (Int) -> Int = { 2 }): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val jpg = jpeg(style, photo)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "zone2-${r.date}-${STYLES[style].lowercase()}-${System.currentTimeMillis() / 1000}.${if (jpg) "jpg" else "png"}")
            put(MediaStore.Images.Media.MIME_TYPE, if (jpg) "image/jpeg" else "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Zone 2 Running")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        ctx.contentResolver.openOutputStream(uri)?.use { render(ctx, r, style, photo, story, 1f, zone).compress(if (jpg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, 93, it) } ?: return false
        return true
    }
}
