package app.zone2running

import android.graphics.Paint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private data class Scene(val title: String, val body: String)

private val SCENES = listOf(
    Scene("Hi, I'm Dil. I'm your heart.", "I do the work on every run. Give me two minutes and I'll show you how to make me stronger without wearing either of us out."),
    Scene("Most runs are too hard for me.", "Push every run and I'm always playing catch-up. You end up tired, running less than you could, and stuck at the same pace."),
    Scene("Slow down until you can chat.", "That's Zone 2. It feels almost too easy, and that's the point: easy running is the kind you can do a lot of, week after week."),
    Scene("Same heartbeat, faster pace.", "As I get stronger, the same easy effort carries you faster. That number is your Zone 2 pace, and it's what this app tracks."),
    Scene("Mostly easy. Sometimes hard.", "Top endurance athletes do roughly four-fifths of their training easy. The hard fifth still matters, so your plan keeps one harder session a week."),
    Scene("Here's the deal.", ""),
)

/** The opening story: six short scenes on why easy running works, told by the runner's own heart. */
@Composable
fun IntroStory(done: () -> Unit) {
    val p = LocalPal.current
    var page by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(p.hero).systemBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (i in SCENES.indices) {
                val w = animFloat(if (i == page) 1f else 0f, 300)
                Box(Modifier.weight(1f + w).height(5.dp).clip(RoundedCornerShape(3.dp)).background(if (i <= page) p.heroMark else p.heroTrack))
            }
            Spacer(Modifier.width(10.dp))
            Text("Skip", Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = done).padding(6.dp), color = p.heroMuted, fontFamily = BodyFont, fontSize = 15.sp)
        }
        AnimatedContent(page, Modifier.weight(1f), transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            (fadeIn() + slideInHorizontally { dir * it / 4 }) togetherWith (fadeOut() + slideOutHorizontally { -dir * it / 4 })
        }, label = "scene") { i ->
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Box(Modifier.fillMaxWidth().height(290.dp), contentAlignment = Alignment.Center) { SceneArt(i) }
                Spacer(Modifier.height(18.dp))
                Text(SCENES[i].title, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 40.sp, color = p.heroInk)
                Spacer(Modifier.height(12.dp))
                if (i < 5) Text(SCENES[i].body, fontFamily = BodyFont, fontSize = 17.sp, lineHeight = 25.sp, color = p.heroMuted)
                else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("A plan built from your pace, your heart rate and your week", "Runs imported from your watch through Health Connect", "No account. Everything stays on your phone").forEachIndexed { n, t ->
                        val v = enter(450, 250 + n * 220)
                        Row(Modifier.padding(start = ((1 - v) * 24).dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Canvas(Modifier.size(24.dp)) {
                                drawCircle(p.heroMark.copy(alpha = v))
                                val s = size.width
                                drawLine(p.hero, Offset(s * .28f, s * .52f), Offset(s * .44f, s * .68f), s * .11f, StrokeCap.Round)
                                drawLine(p.hero, Offset(s * .44f, s * .68f), Offset(s * .74f, s * .34f), s * .11f, StrokeCap.Round)
                            }
                            Text(t, color = p.heroInk.copy(alpha = v), fontFamily = BodyFont, fontSize = 16.5.sp, lineHeight = 22.sp)
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (page > 0) Text("Back", Modifier.clip(RoundedCornerShape(10.dp)).clickable { page-- }.padding(14.dp), color = p.heroMuted, fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(14.dp)).background(p.heroMark).clickable { if (page < SCENES.lastIndex) page++ else done() }.padding(horizontal = 26.dp, vertical = 15.dp)) {
                Text(if (page < SCENES.lastIndex) "Next" else "Build my plan", color = p.hero, fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun SceneArt(i: Int) {
    val p = LocalPal.current
    val face = rememberFace(R.font.barlowcondensed_bold)
    val pop = enter(600)
    when (i) {
        0 -> Box(contentAlignment = Alignment.Center) {
            Dil("hello", Modifier.size((150 + 50 * pop).dp), color = p.easy.lighten())
            val b = enter(350, 700)
            Text("hi!", Modifier.align(Alignment.TopEnd).padding(top = 18.dp).clip(RoundedCornerShape(14.dp, 14.dp, 14.dp, 3.dp)).background(p.heroInk.copy(alpha = b)).padding(horizontal = 14.dp, vertical = 6.dp),
                color = p.hero.copy(alpha = b), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp)
        }
        1 -> {
            val t = loop(1100); val rise = loop(5000)
            val bpm = if (Motion.on) (152 + 26 * sin(rise * PI)).roundToInt() else 178
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    for (k in 0..5) {            // speed lines streaming past
                        val y = size.height * (.18f + k * .13f); val x = size.width * (1.1f - ((t + k * .17f) % 1f) * 1.3f)
                        drawLine(p.heroTrack, Offset(x, y), Offset(x + size.width * .16f, y), 5.dp.toPx(), StrokeCap.Round)
                    }
                }
                Dil("puffed", Modifier.size(190.dp), running = true)
                Bpm(bpm, p.zones[4].lighten(), Modifier.align(Alignment.BottomCenter))
            }
        }
        2 -> {
            val settle = enter(1600)
            val bpm = (176 - 34 * settle).roundToInt()
            val dots = loop(1200)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Dil("calm", Modifier.size(190.dp), color = p.easy.lighten(), bpm = 70, running = true)
                Row(Modifier.align(Alignment.TopEnd).padding(top = 14.dp).clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 3.dp)).background(p.heroInk).padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (k in 0..2) Canvas(Modifier.size(9.dp)) { drawCircle(p.hero.copy(alpha = if (!Motion.on) 1f else .3f + .7f * sin(((dots + 1f - k * .2f) % 1f) * PI).toFloat())) }
                }
                Bpm(bpm, p.easy.lighten(), Modifier.align(Alignment.BottomCenter))
            }
        }
        3 -> {
            val t = loop(3600); val drop = enter(2200, 300)
            val pace = 510 - 90 * drop
            Canvas(Modifier.fillMaxSize()) {
                val label = Paint().apply { isAntiAlias = true; typeface = face; textSize = 20.sp.toPx(); color = p.heroMuted.toArgb() }
                val big = Paint().apply { isAntiAlias = true; typeface = face; textSize = 34.sp.toPx(); color = p.heroInk.toArgb(); textAlign = Paint.Align.RIGHT }
                val lanes = listOf(Triple("TODAY", 1f, "8:30"), Triple("MONTH 6", 1.6f, fmtPace(pace.toDouble())))
                lanes.forEachIndexed { k, (name, speed, txt) ->
                    val y = size.height * (.27f + k * .47f)
                    drawContext.canvas.nativeCanvas.drawText(name, 0f, y - 44.dp.toPx(), label)
                    drawContext.canvas.nativeCanvas.drawText("$txt /km", size.width, y - 40.dp.toPx(), big)
                    drawLine(p.heroTrack, Offset(0f, y + 22.dp.toPx()), Offset(size.width, y + 22.dp.toPx()), 6.dp.toPx(), StrokeCap.Round)
                    val u = 46.dp.toPx() / 100f
                    val x = if (Motion.on) ((t * speed) % 1f) * (size.width + 100 * u) - 100 * u else size.width * (.25f + k * .4f)
                    val hop = if (Motion.on) -kotlin.math.abs(sin(t * speed * 2 * PI.toFloat() * 7)) * 6.dp.toPx() else 0f
                    drawPath(heartPath(u, x, y - 26.dp.toPx() + hop), if (k == 0) p.heroMuted else p.heroMark)
                    drawContext.canvas.nativeCanvas.drawText("142 bpm", 0f, y + 50.dp.toPx(), label.apply { textSize = 15.sp.toPx() })
                    label.textSize = 20.sp.toPx()
                }
            }
        }
        4 -> {
            val sweep = enter(1500, 200)
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(230.dp)) {
                    val w = 26.dp.toPx(); val tl = Offset(w / 2, w / 2); val sz = Size(size.width - w, size.height - w)
                    drawArc(p.heroTrack, 0f, 360f, false, tl, sz, style = Stroke(w))
                    drawArc(p.easy.lighten(), -90f, 288f * sweep, false, tl, sz, style = Stroke(w, cap = StrokeCap.Butt))
                    drawArc(p.heroMark, -90f + 288f + 3f, (72f - 6f) * ((sweep - .75f) * 4).coerceIn(0f, 1f), false, tl, sz, style = Stroke(w, cap = StrokeCap.Butt))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Dil("proud", Modifier.size(96.dp), color = p.easy.lighten())
                    Text("80 / 20", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = p.heroInk)
                }
            }
        }
        else -> Dil("proud", Modifier.size((120 + 40 * pop).dp), color = p.easy.lighten(), running = true)
    }
}

@Composable
private fun Bpm(v: Int, color: Color, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text("$v", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 52.sp, color = color)
        Text(" bpm", Modifier.padding(bottom = 7.dp), fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = LocalPal.current.heroMuted)
    }
}

/** A brighter take on a colour, for marks that sit on the dark intro background. */
fun Color.lighten() = Color(red + (1 - red) * .25f, green + (1 - green) * .25f, blue + (1 - blue) * .25f)
