package app.zone2running

import android.graphics.Typeface
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** One switch for all motion. Off when the phone has animations disabled, and in screen-render tests. */
object Motion { var on = true }

/** A value that runs 0 → 1 forever, once every [periodMs]. Stays at 0 when motion is off. */
@Composable
fun loop(periodMs: Int): Float {
    if (!Motion.on) return 0f
    val t = rememberInfiniteTransition(label = "loop")
    val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "v")
    return v
}

/** Runs 0 → 1 once when the caller first appears. */
@Composable
fun enter(durationMs: Int = 700, delayMs: Int = 0): Float {
    if (!Motion.on) return 1f
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val v by animateFloatAsState(if (go) 1f else 0f, tween(durationMs, delayMs, FastOutSlowInEasing), label = "enter")
    return v
}

@Composable
fun animFloat(target: Float, ms: Int = 900): Float =
    if (!Motion.on) target else animateFloatAsState(target, tween(ms, easing = FastOutSlowInEasing), label = "f").value

/** Cards fade in and rise a little, one after another. */
fun Modifier.rise(index: Int): Modifier = composed {
    val v = enter(420, index * 55)
    graphicsLayer { alpha = v; translationY = (1 - v) * 24.dp.toPx() }
}

@Composable
fun rememberFace(res: Int): Typeface {
    val ctx = LocalContext.current
    return remember(res) { ResourcesCompat.getFont(ctx, res) ?: Typeface.DEFAULT_BOLD }
}

/** Heart outline on a 100 × 100 grid, scaled by [u]. */
fun heartPath(u: Float, dx: Float = 0f, dy: Float = 0f) = Path().apply {
    moveTo(dx + 50 * u, dy + 88 * u)
    cubicTo(dx + 10 * u, dy + 60 * u, dx + 2 * u, dy + 30 * u, dx + 25 * u, dy + 18 * u)
    cubicTo(dx + 40 * u, dy + 10 * u, dx + 50 * u, dy + 22 * u, dx + 50 * u, dy + 30 * u)
    cubicTo(dx + 50 * u, dy + 22 * u, dx + 60 * u, dy + 10 * u, dx + 75 * u, dy + 18 * u)
    cubicTo(dx + 98 * u, dy + 30 * u, dx + 90 * u, dy + 60 * u, dx + 50 * u, dy + 88 * u)
    close()
}

private val FACE = Color(0xFF0B2A26)

/** Draws Dil. [beat] is the heartbeat phase 0..1, [blink] and [drip] are slower loops. */
fun DrawScope.drawDil(mood: String, body: Color, beat: Float, blink: Float, drip: Float, running: Boolean = false, step: Float = 0f) {
    val u = size.minDimension / 100f
    val ox = (size.width - 100 * u) / 2; val oy = (size.height - 100 * u) / 2
    // two quick bumps per beat, like lub-dub
    val pulse = max(0f, sin(beat * 2 * PI.toFloat())).pow(6) + .5f * max(0f, sin((beat - .18f) * 2 * PI.toFloat())).pow(6)
    val shakeX = if (mood == "puffed") sin(drip * 2 * PI.toFloat() * 6) * 1.2f * u else 0f
    if (running) {
        val a = sin(step * 2 * PI.toFloat())
        drawRoundRect(FACE, Offset(ox + (38 + a * 6) * u, oy + 86 * u), Size(10 * u, 7 * u), androidx.compose.ui.geometry.CornerRadius(3.5f * u))
        drawRoundRect(FACE, Offset(ox + (54 - a * 6) * u, oy + 86 * u), Size(10 * u, 7 * u), androidx.compose.ui.geometry.CornerRadius(3.5f * u))
    }
    translate(ox + shakeX, oy) {
        scale(1f + .07f * pulse, Offset(50 * u, 55 * u)) {
            drawPath(heartPath(u), body)
            drawOval(Color.White.copy(alpha = .22f), Offset(20 * u, 22 * u), Size(16 * u, 10 * u))
            val ey = 43 * u; val lx = 37 * u; val rx = 63 * u
            val closed = blink > .955f
            when {
                mood == "proud" -> for (x in listOf(lx, rx)) drawArc(FACE, 200f, 140f, false, Offset(x - 7 * u, ey - 5 * u), Size(14 * u, 12 * u), style = Stroke(3.2f * u, cap = StrokeCap.Round))
                mood == "sleepy" -> for (x in listOf(lx, rx)) drawArc(FACE, 20f, 140f, false, Offset(x - 7 * u, ey - 7 * u), Size(14 * u, 10 * u), style = Stroke(3.2f * u, cap = StrokeCap.Round))
                closed -> for (x in listOf(lx, rx)) drawLine(FACE, Offset(x - 6 * u, ey), Offset(x + 6 * u, ey), 3.2f * u, StrokeCap.Round)
                else -> for (x in listOf(lx, rx)) {
                    val big = mood == "puffed"
                    drawCircle(Color.White, (if (big) 10.5f else 9f) * u, Offset(x, ey))
                    drawCircle(FACE, (if (big) 3.2f else 4.6f) * u, Offset(x + 1.5f * u, ey + (if (big) 0f else 1.2f) * u))
                }
            }
            when (mood) {
                "puffed" -> drawOval(FACE, Offset(44 * u, 56 * u), Size(12 * u, 13 * u))
                "sleepy" -> drawLine(FACE, Offset(45 * u, 60 * u), Offset(55 * u, 60 * u), 3.2f * u, StrokeCap.Round)
                "calm" -> drawArc(FACE, 20f, 140f, false, Offset(42 * u, 50 * u), Size(16 * u, 12 * u), style = Stroke(3.2f * u, cap = StrokeCap.Round))
                else -> drawArc(FACE, 0f, 180f, true, Offset(40 * u, 49 * u), Size(20 * u, 18 * u))
            }
            if (mood == "proud" || mood == "hello" || mood == "happy") {
                drawCircle(Color(0xFFFF8FA3).copy(alpha = .55f), 5.5f * u, Offset(24 * u, 54 * u))
                drawCircle(Color(0xFFFF8FA3).copy(alpha = .55f), 5.5f * u, Offset(76 * u, 54 * u))
            }
        }
        if (mood == "puffed") for ((i, x) in listOf(6f, 92f).withIndex()) {
            val t = (drip + i * .5f) % 1f
            val c = Offset(x * u, (22 + t * 30) * u)
            val drop = Path().apply { moveTo(c.x, c.y - 6 * u); quadraticTo(c.x + 5 * u, c.y + 2 * u, c.x, c.y + 4 * u); quadraticTo(c.x - 5 * u, c.y + 2 * u, c.x, c.y - 6 * u); close() }
            drawPath(drop, Color(0xFF8FD3F4).copy(alpha = 1f - t))
        }
        if (mood == "sleepy") for (i in 0..1) {
            val s = (5 + i * 3) * u; val x = (80 + i * 9) * u; val y = (16 - i * 10) * u
            val z = Path().apply { moveTo(x, y); lineTo(x + s, y); lineTo(x, y + s); lineTo(x + s, y + s) }
            drawPath(z, body.copy(alpha = .8f), style = Stroke(2.2f * u, cap = StrokeCap.Round))
        }
    }
}

/** Dil, the heart who stands in for the runner's own. */
@Composable
fun Dil(mood: String, modifier: Modifier = Modifier.size(72.dp), color: Color? = null, bpm: Int = 0, running: Boolean = false) {
    val p = LocalPal.current
    val body = color ?: when (mood) { "puffed" -> p.zones[4]; "sleepy" -> p.zones[0]; else -> p.easy }
    val rate = if (bpm > 0) bpm else when (mood) { "puffed" -> 170; "sleepy" -> 48; else -> 66 }
    val beat = loop(60000 / rate); val blink = loop(4300); val drip = loop(1400); val step = loop(380)
    Canvas(modifier) { drawDil(mood, body, beat, blink, drip, running, step) }
}

/** A burst of paper for milestones. Draws nothing when motion is off. */
@Composable
fun Confetti(modifier: Modifier = Modifier.fillMaxSize()) {
    if (!Motion.on) return
    val bits = remember { List(80) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextInt(5)) } }
    val t = enter(2200)
    val cols = listOf(Color(0xFF3CC4AD), Color(0xFFFFD166), Color(0xFFD9631E), Color(0xFF0B8573), Color(0xFFEF6A6F))
    Canvas(modifier) {
        if (t >= 1f) return@Canvas
        bits.forEachIndexed { i, (a, b, c) ->
            val vx = (a - .5f) * size.width * 1.4f; val vy = -(b * .9f + .5f) * size.height * .9f
            val x = size.width / 2 + vx * t; val y = size.height * .35f + vy * t + size.height * 1.5f * t * t
            rotate((a * 720 + i * 13) * t, Offset(x, y)) { drawRect(cols[c].copy(alpha = 1f - t.pow(3)), Offset(x, y), Size(9.dp.toPx(), 5.dp.toPx())) }
        }
    }
}
