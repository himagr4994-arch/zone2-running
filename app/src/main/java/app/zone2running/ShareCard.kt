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
    val STYLES = listOf("Pulse", "Ridges", "Route", "Sunrise", "Track", "Cutout", "Cover", "Glass", "Stamp")
    /** Looks that are nothing without a photo. Cutout and Stamp take one if offered. */
    val USES_PHOTO = setOf(6, 7)
    const val STAMP = 8
    /** Stories are 9:16, feed posts 4:5. The stamp with no photo is a square sticker with nothing behind it. */
    fun height(style: Int, story: Boolean, photo: Boolean = true) = if (style == STAMP && !photo) 1080 else if (story) 1920 else 1350
    /** A sticker is see-through, which a video can't be. */
    fun canAnimate(style: Int, photo: Boolean) = !(style == STAMP && !photo) && !(style in USES_PHOTO && !photo)
    private fun ease(x: Float) = 1f - (1f - x.coerceIn(0f, 1f)).let { it * it * it }

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
    private class Card(val cv: Canvas, val k: Ink, val h: Float, story: Boolean, val r: Run, val zone: (Int) -> Int, val p: Float) {
        /** How far the picture has drawn itself, eased; 1 for a still. The figures count up over the first four fifths. */
        val e = ease(p); private val count = ease(p / .8f)
        // Instagram covers the top and bottom of a story with its own controls: keep clear of both.
        val top = if (story) 250f else 70f
        val bottom = if (story) h - 300f else h - 80f
        val dist = f2(U.k(r.distKm) * count); val unit = U.unit.uppercase()
        val time = fmtDur((r.durSec * count).toInt()); val pace = fmtPace(paceOf(r)); val per = U.per.uppercase()
        /** The full distance, for sizing type so it doesn't jump while the number counts up. */
        val distFull = f2(U.k(r.distKm))
        fun mark(color: Long) = cv.drawText("ZONE 2 RUNNING", 84f, top + 60f, k.text(color, 36f, k.bold, spacing = .12f))
        /** The distance, as large as fits, with its unit after it. Returns the size used. */
        fun big(y: Float, max: Float, ink: Long, dim: Long): Float {
            var size = max; while (size > 160f && k.text(0, size, k.bold).measureText(distFull) > W - 310f) size -= 10f
            cv.drawText(dist, 76f, y, k.text(ink, size, k.bold))
            cv.drawText(unit, 84f + k.text(0, size, k.bold).measureText(distFull) + 14f, y, k.text(dim, 84f, k.semi)); return size
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
            while (size > 70f && listOf(distFull, fmtDur(r.durSec), pace).maxOf { k.text(0, size, k.bold).measureText(it) } > colW - 70f) size -= 4f
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

    /**
     * Draws one look. [progress] below 1 gives a frame of the animated version: the picture part-way through
     * drawing itself, with the figures still counting up.
     */
    fun render(ctx: Context, r: Run, style: Int, photo: Bitmap? = null, story: Boolean = false, scale: Float = 1f, progress: Float = 1f, zone: (Int) -> Int = { 2 }): Bitmap {
        val h = height(style, story, photo != null)
        val bmp = Bitmap.createBitmap((W * scale).toInt() / 2 * 2, (h * scale).toInt() / 2 * 2, Bitmap.Config.ARGB_8888)     // even sizes: video needs them
        val cv = Canvas(bmp); cv.scale(scale, scale)
        val c = Card(cv, Ink(ctx), h.toFloat(), story && !(style == STAMP && photo == null), r, zone, progress)
        when (style) {
            0 -> pulse(c); 1 -> ridges(c); 2 -> route(c); 3 -> sunrise(c); 4 -> track(c)
            5 -> cutout(c, photo); 6 -> cover(c, photo); 7 -> glass(c, photo)
            else -> stamp(c, photo)
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
        for (i in 0 until (n * e).toInt()) {
            val a = -PI / 2 + 2 * PI * i / n
            val len = 26f + amp * ((s[i] - lo) / (hi - lo).toFloat())
            val col = ZONE_INK[(zone(s[i]) - 1).coerceIn(0, 4)]
            cv.drawLine(cx + (r0 * cos(a)).toFloat(), cy + (r0 * sin(a)).toFloat(), cx + ((r0 + len) * cos(a)).toFloat(), cy + ((r0 + len) * sin(a)).toFloat(), k.stroke(col, 7.5f))
        }
        cv.drawCircle(cx, cy - r0 + 2f, 9f, k.fill(0xFFFFFFFF))                 // where the run began
        mark(0xFF8DA29D)
        var size = 190f; while (k.text(0, size, k.bold).measureText(distFull) > r0 * 1.66f) size -= 6f
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
            val arrive = ease(e * rows - row)                                             // each range rises into place in turn
            if (arrive <= 0f) continue
            val base = y0 + row * gap + (1 - arrive) * gap * 3f; val shape = Path(); val edge = Path(); shape.moveTo(0f, h)
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
        // In the animated version the line is drawn as far as the run has got, with the dot at its head.
        val measure = android.graphics.PathMeasure(path, false); val drawn = Path(); val head = FloatArray(2)
        measure.getSegment(0f, measure.length * e, drawn, true); measure.getPosTan(measure.length * e, head, null)
        if (glow != 0) { cv.drawPath(drawn, k.stroke(0x33000000L or glow.toLong(), width * 4.4f)); cv.drawPath(drawn, k.stroke(0x66000000L or glow.toLong(), width * 2.4f)) }
        else cv.drawPath(drawn, k.stroke(0x66000000, width + 10f))                       // on a photo: a dark edge so a white line always reads
        cv.drawPath(drawn, k.stroke(line, width))
        val a = pts.first()
        cv.drawCircle(ox + (a.first * s).toFloat(), oy + (a.second * syy).toFloat(), width * 1.5f, k.stroke(0xFFFFFFFF, width * .7f))
        cv.drawCircle(head[0], head[1], width * 1.9f, k.fill(0xFFFFD166))
    }

    /** First light over layered hills, with a road winding toward the sun. */
    private fun sunrise(c: Card) = with(c) {
        cv.drawRect(0f, 0f, W.toFloat(), h, Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h * .73f, intArrayOf(0xFF1A2447.toInt(), 0xFF6B3A6B.toInt(), 0xFFE8735A.toInt(), 0xFFFFC56B.toInt()), floatArrayOf(0f, .45f, .8f, 1f), Shader.TileMode.CLAMP) })
        val sun = h * .667f + (1 - e) * 300f
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
        cv.drawArc(box, 200f, 52f * e, false, k.stroke(0xFF3CC4AD, 46f).apply { strokeCap = Paint.Cap.BUTT })
        val a = Math.toRadians(200.0 + 52.0 * e); cv.drawCircle(cx + lane * cos(a).toFloat(), cy + lane * .92f * sin(a).toFloat(), 30f, k.fill(0xFFFFD166))
        mark(0xFF9CC3BB)
        big(top + 450f, 380f, 0xFFEAF6F3, 0xFF9CC3BB); pair(top + 670f, 0xFFEAF6F3, 0xFF9CC3BB)
    }

    /** Fills the card with the photo, cropping from the centre. A zoom above 1 crops in, for a slow push while the video plays. */
    private fun fill(cv: Canvas, photo: Bitmap, h: Float, paint: Paint, zoom: Float = 1f) {
        val scale = maxOf(W / photo.width.toFloat(), h / photo.height.toFloat()) * zoom
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
        val wholeFull = distFull.substringBefore('.')
        val room = (bottom - 300f) - (top + 120f)
        fun sized(s: String, max: Float): Float { var z = max; while (z > 200f && k.text(0, z, k.bold).measureText(s) > W - 150f) z -= 10f; return z }
        val z1 = sized(wholeFull, room * .78f); val z2 = sized(part, min(z1, room * .62f))
        val y1 = top + 130f + z1 * .72f; val y2 = y1 + z2 * .78f
        val layer = cv.saveLayer(0f, 0f, W.toFloat(), h, null)
        cv.drawText(whole, 66f, y1, k.text(0xFF000000, z1, k.bold)); cv.drawText(part, 50f, y2, k.text(0xFF000000, z2, k.bold))
        val through = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) }
        if (photo != null) fill(cv, photo, h, through, 1.12f - .12f * e)
        else cv.drawRect(0f, 0f, W.toFloat(), h, through.apply { shader = LinearGradient(0f, top, W.toFloat(), bottom, intArrayOf(0xFF1A2447.toInt(), 0xFFE8735A.toInt(), 0xFFFFC56B.toInt()), null, Shader.TileMode.CLAMP) })
        cv.restoreToCount(layer)
        cv.drawText(unit, W - 84f, y2, k.text(0xFF10201D, 84f, k.semi, Paint.Align.RIGHT))
        pair(bottom - 70f, 0xFF10201D, 0xFF5B6B66)
    }

    /**
     * A magazine cover: the photo edge to edge inside a thin white frame, the distance as the masthead across
     * the top, and the other two figures as cover lines down the side.
     */
    private fun cover(c: Card, photo: Bitmap?) = with(c) {
        if (photo == null) { cv.drawColor(0xFF16201E.toInt()); waiting(c) }
        else {
            fill(cv, photo, h, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG), 1.1f - .1f * e)
            cv.drawRect(0f, 0f, W.toFloat(), top + 620f, Paint().apply { shader = LinearGradient(0f, 0f, 0f, top + 620f, 0xB3000000.toInt(), 0, Shader.TileMode.CLAMP) })
            cv.drawRect(0f, h * .55f, W.toFloat(), h, Paint().apply { shader = LinearGradient(0f, h * .55f, 0f, h, 0, 0xCC000000.toInt(), Shader.TileMode.CLAMP) })
        }
        cv.drawRect(34f, top - 30f, W - 34f, bottom + 30f, k.stroke(0xE6FFFFFF, 5f).apply { strokeJoin = Paint.Join.MITER })
        // The masthead: the distance, stretched to the full width like a title.
        var size = 520f; while (size > 200f && k.text(0, size, k.bold).measureText(distFull) > W - 150f) size -= 10f
        cv.drawText("ZONE 2 RUNNING", W / 2f, top + 52f, k.text(0xF2FFFFFF, 32f, k.bold, Paint.Align.CENTER, .3f))
        cv.drawText(dist, W / 2f - k.text(0, size, k.bold).measureText(distFull) / 2, top + 80f + size * .74f, k.text(0xFFFFFFFF, size, k.bold))
        cv.drawRect(74f, top + 110f + size * .74f, W - 74f, top + 116f + size * .74f, k.fill(0xFFFFFFFF))
        cv.drawText(if (U.miles) "MILES" else "KILOMETRES", W - 74f, top + 172f + size * .74f, k.text(0xFFFFFFFF, 44f, k.bold, Paint.Align.RIGHT, .22f))
        // Cover lines, bottom left, each a small heading over a figure.
        val y = bottom - 60f
        cv.drawText("IN", 74f, y - 292f, k.text(0xFFFFD166, 40f, k.bold, spacing = .2f)); cv.drawText(time, 74f, y - 170f, k.text(0xFFFFFFFF, 132f, k.bold))
        cv.drawText("AT", 74f, y - 112f, k.text(0xFFFFD166, 40f, k.bold, spacing = .2f)); cv.drawText(pace, 74f, y + 10f, k.text(0xFFFFFFFF, 132f, k.bold))
        cv.drawText(per, 74f + k.text(0, 132f, k.bold).measureText(pace) + 14f, y + 10f, k.text(0xDDFFFFFF, 50f, k.semi))
    }

    /**
     * The photo untouched, with the three figures on a pane of frosted glass near the bottom: the picture
     * behind the pane is blurred, the rest stays sharp.
     */
    private fun glass(c: Card, photo: Bitmap?) = with(c) {
        if (photo == null) { cv.drawColor(0xFF16201E.toInt()); waiting(c); mark(0xE6FFFFFF); return@with }
        val sharp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        fill(cv, photo, h, sharp, 1.08f - .08f * e)
        cv.drawRect(0f, 0f, W.toFloat(), top + 220f, Paint().apply { shader = LinearGradient(0f, 0f, 0f, top + 220f, 0x80000000.toInt(), 0, Shader.TileMode.CLAMP) })
        // The pane slides up into place.
        val rise = (1 - e) * 420f
        val pane = RectF(54f, bottom - 400f + rise, W - 54f, bottom - 40f + rise)
        // Blur by shrinking the picture to a few dozen pixels and stretching it back.
        val tiny = Bitmap.createBitmap(W / 30, (h / 30).toInt(), Bitmap.Config.ARGB_8888)
        Canvas(tiny).apply { scale(1 / 30f, 1 / 30f); fill(this, photo, h, sharp, 1.08f - .08f * e) }
        cv.save(); cv.clipPath(Path().apply { addRoundRect(pane, 56f, 56f, Path.Direction.CW) })
        cv.drawBitmap(tiny, null, Rect(0, 0, W, h.toInt()), sharp)
        cv.drawRect(pane, k.fill(0x40101414)); cv.drawRect(pane, Paint().apply { shader = LinearGradient(0f, pane.top, 0f, pane.bottom, 0x33FFFFFF, 0x0DFFFFFF, Shader.TileMode.CLAMP) })
        cv.restore(); tiny.recycle()
        cv.drawRoundRect(pane, 56f, 56f, k.stroke(0x73FFFFFF, 3f))
        mark(0xF2FFFFFF)
        // Distance large on the left of the pane, time and pace stacked on the right.
        var size = 220f; while (size > 120f && k.text(0, size, k.bold).measureText(distFull) > 470f) size -= 8f
        cv.drawText(dist, pane.left + 52f, pane.centerY() + size * .26f, k.text(0xFFFFFFFF, size, k.bold))
        cv.drawText(unit, pane.left + 56f, pane.centerY() + size * .26f + 58f, k.text(0xD9FFFFFF, 34f, k.body, spacing = .16f))
        val x = pane.left + 620f
        cv.drawRect(x - 44f, pane.top + 60f, x - 41f, pane.bottom - 60f, k.fill(0x59FFFFFF))
        cv.drawText(time, x, pane.top + 150f, k.text(0xFFFFFFFF, 96f, k.bold)); cv.drawText("TIME", x, pane.top + 192f, k.text(0xD9FFFFFF, 28f, k.body, spacing = .16f))
        cv.drawText(pace, x, pane.top + 296f, k.text(0xFFFFFFFF, 96f, k.bold)); cv.drawText("PACE $per", x, pane.top + 338f, k.text(0xD9FFFFFF, 28f, k.body, spacing = .16f))
    }

    /**
     * A round rubber stamp: the distance in the middle, time and pace set round the rim. On a photo it lands in
     * the corner at a tilt. With no photo it has nothing behind it at all, to be used as a sticker in Instagram.
     */
    private fun stamp(c: Card, photo: Bitmap?) = with(c) {
        val rad = 330f
        var cx = W / 2f; var cy = h / 2f; var tilt = -8f
        if (photo != null) {
            fill(cv, photo, h, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG), 1.06f - .06f * e)
            cv.drawRect(0f, 0f, W.toFloat(), h, k.fill(0x26000000))
            cx = W - rad - 70f; cy = bottom - rad - 40f; tilt = -12f
            mark(0xF2FFFFFF)
        }
        // The stamp comes down hard: it starts oversized and faint, and lands at two fifths of the way through.
        val land = if (p >= 1f) 1f else ease((p - .15f) / .3f)
        if (land <= 0f) return@with
        val ink = (((0xF5 * land).toInt().toLong()) shl 24) or 0xFFFFFF
        cv.save(); cv.rotate(tilt, cx, cy); cv.scale(1.9f - .9f * land, 1.9f - .9f * land, cx, cy)
        if (photo != null) cv.drawCircle(cx, cy, rad, k.fill(((0x59 * land).toInt().toLong() shl 24) or 0x0B1110))
        cv.drawCircle(cx, cy, rad, k.stroke(ink, 14f)); cv.drawCircle(cx, cy, rad - 22f, k.stroke(ink, 4f)); cv.drawCircle(cx, cy, rad - 112f, k.stroke(ink, 4f))
        val rim = k.text(ink, 60f, k.bold, Paint.Align.CENTER, .16f)
        // Top of the rim reads clockwise; the bottom is set the other way round so it is still the right way up.
        val mid = rad - 67f
        cv.drawTextOnPath("ZONE 2 RUNNING", Path().apply { addArc(RectF(cx - mid + 20f, cy - mid + 20f, cx + mid - 20f, cy + mid - 20f), 180f, 180f) }, 0f, 0f, rim)
        cv.drawTextOnPath("$time  •  $pace $per", Path().apply { addArc(RectF(cx - mid - 22f, cy - mid - 22f, cx + mid + 22f, cy + mid + 22f), 180f, -180f) }, 0f, 0f, rim)
        for (side in listOf(-1f, 1f)) cv.drawCircle(cx + side * mid, cy, 11f, k.fill(ink))
        var size = 250f; while (size > 120f && k.text(0, size, k.bold).measureText(distFull) > (rad - 130f) * 1.9f) size -= 8f
        cv.drawText(dist, cx, cy + size * .22f, k.text(ink, size, k.bold, Paint.Align.CENTER))
        cv.drawText(unit, cx, cy + size * .22f + 66f, k.text(ink, 46f, k.bold, Paint.Align.CENTER, .3f))
        cv.restore()
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

    /** A photo behind the numbers is saved as JPEG, since as PNG it would be several megabytes. The see-through stamp must stay PNG. */
    private fun jpeg(style: Int, photo: Bitmap?) = photo != null && style >= 5

    fun share(ctx: Context, r: Run, style: Int, photo: Bitmap? = null, story: Boolean = false, zone: (Int) -> Int = { 2 }) {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val jpg = jpeg(style, photo)
        val file = File(dir, if (jpg) "zone2-run.jpg" else "zone2-run.png")
        file.outputStream().use { render(ctx, r, style, photo, story, 1f, 1f, zone).compress(if (jpg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, 93, it) }
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
        ctx.contentResolver.openOutputStream(uri)?.use { render(ctx, r, style, photo, story, 1f, 1f, zone).compress(if (jpg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, 93, it) } ?: return false
        return true
    }
}
