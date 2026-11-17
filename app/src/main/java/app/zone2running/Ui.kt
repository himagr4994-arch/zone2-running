package app.zone2running

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colour roles. The pace-ladder hero is the one loud surface; everything else stays quiet. */
data class Pal(
    val bg: Color, val surface: Color, val surface2: Color, val ink: Color, val muted: Color, val line: Color,
    val accent: Color, val accentInk: Color, val hard: Color,
    val hero: Color, val heroInk: Color, val heroMuted: Color, val heroTrack: Color, val heroMark: Color,
    val zones: List<Color>, val warnBg: Color, val warnInk: Color, val goodBg: Color, val goodInk: Color,
    val gold: Color, val easy: Color, val medals: List<Color>, val danger: Color,
)

val LightPal = Pal(
    Color(0xFFEDF1F0), Color(0xFFFFFFFF), Color(0xFFF2F6F5), Color(0xFF10201D), Color(0xFF566864), Color(0xFFD3DDDA),
    Color(0xFF0B8573), Color(0xFFFFFFFF), Color(0xFFD9631E),
    Color(0xFF0B3A34), Color(0xFFEAF6F3), Color(0xFF9CC3BB), Color(0xFF1C554D), Color(0xFFFFD166),
    listOf(Color(0xFF7C93A6), Color(0xFF0B8573), Color(0xFFC99A12), Color(0xFFD9631E), Color(0xFFC7343A)),
    Color(0xFFFBEEDD), Color(0xFF7A3D07), Color(0xFFDDF2EC), Color(0xFF0A5347),
    Color(0xFFB8860B), Color(0xFF008C72), listOf(Color(0xFFA8703A), Color(0xFF7F8E98), Color(0xFFC29A12)), Color(0xFFC7343A),
)
val DarkPal = Pal(
    Color(0xFF0B1312), Color(0xFF131E1C), Color(0xFF1A2826), Color(0xFFE4EEEB), Color(0xFF8DA29D), Color(0xFF273734),
    Color(0xFF3CC4AD), Color(0xFF06211D), Color(0xFFC96A30),
    Color(0xFF0F2E2A), Color(0xFFEAF6F3), Color(0xFF8FB8B0), Color(0xFF1E4A43), Color(0xFFFFD166),
    listOf(Color(0xFF8FA6B8), Color(0xFF3CC4AD), Color(0xFFE0B63A), Color(0xFFF08A4B), Color(0xFFEF6A6F)),
    Color(0xFF3A2A14), Color(0xFFF6D3A6), Color(0xFF12332D), Color(0xFFA9E6D8),
    Color(0xFFE0B63A), Color(0xFF199E88), listOf(Color(0xFFC98D55), Color(0xFFA9B8C2), Color(0xFFE7C14A)), Color(0xFFEF6A6F),
)
val LocalPal = staticCompositionLocalOf { LightPal }

val Display = FontFamily(Font(R.font.barlowcondensed_semibold, FontWeight.SemiBold), Font(R.font.barlowcondensed_bold, FontWeight.Bold))
val BodyFont = FontFamily(Font(R.font.barlow_regular, FontWeight.Normal), Font(R.font.barlow_medium, FontWeight.Medium), Font(R.font.barlow_semibold, FontWeight.SemiBold))

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val p = if (dark) DarkPal else LightPal
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = p.accent, onPrimary = p.accentInk, background = p.bg, onBackground = p.ink, surface = p.surface, onSurface = p.ink,
        surfaceVariant = p.surface2, onSurfaceVariant = p.muted, outline = p.line, outlineVariant = p.line,
        secondaryContainer = p.goodBg, onSecondaryContainer = p.goodInk, surfaceContainer = p.surface, surfaceContainerHigh = p.surface,
        surfaceContainerHighest = p.surface2, error = p.danger, primaryContainer = p.goodBg, onPrimaryContainer = p.goodInk,
    )
    val body = TextStyle(fontFamily = BodyFont, fontSize = 16.sp, lineHeight = 23.sp)
    val type = Typography().run {
        copy(bodyLarge = body, bodyMedium = body.copy(fontSize = 15.sp, lineHeight = 21.sp), bodySmall = body.copy(fontSize = 13.sp, lineHeight = 18.sp),
            labelLarge = body.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp), labelMedium = body.copy(fontSize = 13.sp), labelSmall = body.copy(fontSize = 12.sp),
            titleMedium = body.copy(fontWeight = FontWeight.SemiBold), titleLarge = body.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
            headlineSmall = body.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 24.sp))
    }
    CompositionLocalProvider(LocalPal provides p) { MaterialTheme(colorScheme = scheme, typography = type, content = content) }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.surface).border(1.dp, p.line, RoundedCornerShape(14.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
    )
}

@Composable fun Label(t: String, color: Color = LocalPal.current.muted) =
    Text(t.uppercase(), fontSize = 12.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.SemiBold, color = color, fontFamily = BodyFont)

@Composable fun Head(t: String, size: Int = 22, color: Color = LocalPal.current.ink, modifier: Modifier = Modifier) =
    Text(t, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = size.sp, lineHeight = (size * 1.15).sp, color = color, modifier = modifier)

@Composable fun Body(t: String, color: Color = LocalPal.current.ink, modifier: Modifier = Modifier) =
    Text(t, fontFamily = BodyFont, fontSize = 15.sp, lineHeight = 21.sp, color = color, modifier = modifier)

@Composable fun Small(t: String, color: Color = LocalPal.current.muted, modifier: Modifier = Modifier, align: TextAlign? = null) =
    Text(t, fontFamily = BodyFont, fontSize = 13.5.sp, lineHeight = 18.sp, color = color, modifier = modifier, textAlign = align)

@Composable
fun Btn(text: String, modifier: Modifier = Modifier, ghost: Boolean = false, danger: Boolean = false, small: Boolean = false, onClick: () -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(10.dp)
    val tick = haptic()
    val bg = if (ghost || danger) Color.Transparent else p.accent
    val fg = when { danger -> p.danger; ghost -> p.ink; else -> p.accentInk }
    Box(
        modifier.clip(shape).background(bg).then(if (ghost || danger) Modifier.border(1.dp, p.line, shape) else Modifier)
            .clickable { tick(false); onClick() }.padding(horizontal = if (small) 12.dp else 16.dp, vertical = if (small) 8.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg, fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = if (small) 14.sp else 15.sp) }
}

@Composable
fun Meter(frac: Float, modifier: Modifier = Modifier, color: Color = LocalPal.current.accent) {
    val track = LocalPal.current.surface2
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, size = size, cornerRadius = r)
        val w = size.width * frac.coerceIn(0f, 1f)
        if (w > 0) drawRoundRect(color, size = Size(maxOf(w, size.height), size.height), cornerRadius = r)
    }
}

@Composable
fun Field(label: String, value: String, modifier: Modifier = Modifier, placeholder: String = "", keyboard: KeyboardType = KeyboardType.Number, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier.fillMaxWidth(), singleLine = singleLine,
        label = { Text(label, fontSize = 13.sp) }, placeholder = { Text(placeholder, color = LocalPal.current.muted) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard), shape = RoundedCornerShape(10.dp),
    )
}

/** Small pill used for day and option pickers. */
@Composable
fun Pick(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(9.dp)
    val tick = haptic()
    Box(
        modifier.clip(shape).background(if (on) p.accent else p.surface2).border(1.dp, if (on) p.accent else p.line, shape)
            .clickable { tick(false); onClick() }.padding(vertical = 9.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (on) p.accentInk else p.ink, fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1) }
}

@Composable
fun DayPicker(days: List<Int>, onToggle: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        for (d in 1..7) Pick(DOW[d - 1], d in days, Modifier.weight(1f)) { onToggle(d) }
    }
}

/** The five heart-rate zones with their bpm ranges; Zone 2 is highlighted. */
@Composable
fun ZoneStrip(e: Engine) {
    val p = LocalPal.current
    val names = listOf("Recovery", "Easy", "Steady", "Hard", "Max")
    Row(Modifier.clip(RoundedCornerShape(6.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 0..4) Column(Modifier.weight(1f).background(if (i == 1) p.goodBg else p.surface2)) {
            Box(Modifier.fillMaxWidth().height(5.dp).background(p.zones[i]))
            Column(Modifier.fillMaxWidth().padding(vertical = 7.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val ink = if (i == 1) p.goodInk else p.ink
                Text("Z${i + 1}", fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = ink)
                Text(if (i < 4) "${e.zb[i]}–${e.zb[i + 1] - 1}" else "${e.zb[i]}+", fontSize = 12.sp, color = ink, fontFamily = BodyFont, maxLines = 1)
                Text(names[i], fontSize = 11.5.sp, color = ink, fontFamily = BodyFont, maxLines = 1)
            }
        }
    }
}

@Composable
fun NoteBox(n: Note) {
    val p = LocalPal.current
    val (bg, fg) = when (n.tone) { "warn" -> p.warnBg to p.warnInk; "good" -> p.goodBg to p.goodInk; else -> p.surface2 to p.ink }
    Text(n.text, Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 12.dp, vertical = 10.dp),
        color = fg, fontFamily = BodyFont, fontSize = 14.5.sp, lineHeight = 20.sp)
}

/** Round badge. Earned ones wear a tier-coloured double ring; locked ones are a dashed outline. */
@Composable
fun Medal(b: Badge, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val p = LocalPal.current
    val ring = p.medals[b.tier - 1]
    Column(modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(62.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                if (b.got) {
                    drawCircle(ring, r - 1.dp.toPx(), style = Stroke(1.dp.toPx()))
                    drawCircle(p.surface2, r - 5.dp.toPx())
                    drawCircle(ring, r - 5.dp.toPx(), style = Stroke(3.dp.toPx()))
                } else drawCircle(p.line, r - 3.dp.toPx(), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
            }
            Text(b.glyph, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = if (b.glyph.length > 4) 14.sp else 17.sp, color = if (b.got) p.ink else p.muted)
        }
        Text(b.name, fontSize = 11.5.sp, lineHeight = 14.sp, color = if (b.got) p.ink else p.muted, fontFamily = BodyFont, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
fun Dot(color: Color) = Box(Modifier.size(10.dp).clip(CircleShape).background(color))

/** Flame used for the weekly streak, drawn so it needs no icon dependency. */
@Composable
fun Flame(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp)) {
        val s = size.width / 24f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(12.6f * s, 2f * s)
            cubicTo(13.2f * s, 5.6f * s, 18f * s, 7.6f * s, 18f * s, 13.2f * s)
            cubicTo(18f * s, 16.5f * s, 15.3f * s, 19.2f * s, 12f * s, 19.2f * s)
            cubicTo(8.7f * s, 19.2f * s, 6f * s, 16.5f * s, 6f * s, 13.2f * s)
            cubicTo(6f * s, 10.9f * s, 7.1f * s, 9.3f * s, 8.4f * s, 8.1f * s)
            cubicTo(8.5f * s, 10.1f * s, 9.3f * s, 11.3f * s, 10.5f * s, 11.6f * s)
            cubicTo(11.7f * s, 8.8f * s, 10.4f * s, 5.3f * s, 12.6f * s, 2f * s)
            close()
        }
        drawPath(path, color)
    }
}

fun Offset.plusY(dy: Float) = Offset(x, y + dy)

/** A light tick for taps and drags, a firmer one for milestones. */
@Composable
fun haptic(): (strong: Boolean) -> Unit {
    val h = androidx.compose.ui.platform.LocalHapticFeedback.current
    return { strong -> h.performHapticFeedback(if (strong) androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress else androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove) }
}

/** The app's own small icons, drawn as strokes so they match the type and need no icon library. */
@Composable
fun Glyph(name: String, color: Color, modifier: Modifier = Modifier.size(22.dp)) {
    Canvas(modifier) {
        val s = size.minDimension; val w = s * .09f
        val st = Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), w, androidx.compose.ui.graphics.StrokeCap.Round)
        fun poly(vararg p: Float, close: Boolean = false, fill: Boolean = false) {
            val path = androidx.compose.ui.graphics.Path().apply { moveTo(p[0] * s, p[1] * s); for (i in 2 until p.size step 2) lineTo(p[i] * s, p[i + 1] * s); if (close) close() }
            if (fill) drawPath(path, color) else drawPath(path, color, style = st)
        }
        when (name) {
            "heart" -> drawPath(heartPath(s / 100f * .86f, s * .07f, s * .05f), color, style = st)
            "calendar" -> { drawRoundRect(color, Offset(s * .14f, s * .2f), Size(s * .72f, s * .66f), CornerRadius(s * .12f), style = st); line(.14f, .42f, .86f, .42f); line(.34f, .1f, .34f, .26f); line(.66f, .1f, .66f, .26f) }
            "chart" -> { poly(.12f, .72f, .36f, .48f, .54f, .6f, .88f, .22f); poly(.66f, .22f, .88f, .22f, .88f, .44f) }
            "check" -> poly(.2f, .54f, .42f, .74f, .8f, .28f)
            "snow" -> { line(.5f, .14f, .5f, .86f); line(.19f, .32f, .81f, .68f); line(.19f, .68f, .81f, .32f) }
            "moon" -> drawPath(androidx.compose.ui.graphics.Path.combine(androidx.compose.ui.graphics.PathOperation.Difference,
                androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(s * .16f, s * .14f, s * .86f, s * .84f)) },
                androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(s * .38f, s * .06f, s * 1.0f, s * .68f)) }), color)
            "dash" -> line(.3f, .5f, .7f, .5f)
            "dot" -> drawCircle(color, s * .09f)
            "right" -> poly(.4f, .24f, .66f, .5f, .4f, .76f)
            "left" -> poly(.6f, .24f, .34f, .5f, .6f, .76f)
            "down" -> poly(.24f, .4f, .5f, .66f, .76f, .4f)
            "bolt" -> poly(.56f, .08f, .24f, .56f, .48f, .56f, .42f, .92f, .76f, .42f, .52f, .42f, close = true, fill = true)
            "flag" -> { line(.26f, .12f, .26f, .9f); poly(.26f, .16f, .8f, .28f, .26f, .52f, close = true, fill = true) }
            "target" -> { drawCircle(color, s * .38f, style = st); drawCircle(color, s * .2f, style = st); drawCircle(color, s * .06f) }
            "road" -> drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(s * .2f, s * .88f); cubicTo(s * .2f, s * .5f, s * .8f, s * .56f, s * .78f, s * .12f) }, color, style = st)
            "bars" -> { line(.2f, .84f, .2f, .56f); line(.4f, .84f, .4f, .2f); line(.6f, .84f, .6f, .56f); line(.8f, .84f, .8f, .2f) }
            "ramp" -> poly(.12f, .78f, .36f, .78f, .58f, .3f, .88f, .3f)
            "walk" -> { line(.1f, .5f, .42f, .5f); line(.56f, .5f, .64f, .5f); line(.78f, .5f, .9f, .5f) }
            "dumbbell" -> { line(.34f, .5f, .66f, .5f); line(.27f, .28f, .27f, .72f); line(.14f, .38f, .14f, .62f); line(.73f, .28f, .73f, .72f); line(.86f, .38f, .86f, .62f) }
            "play" -> poly(.34f, .2f, .8f, .5f, .34f, .8f, close = true, fill = true)
            "plus" -> { line(.5f, .2f, .5f, .8f); line(.2f, .5f, .8f, .5f) }
            "bell" -> { drawArc(color, 180f, 180f, false, Offset(s * .24f, s * .16f), Size(s * .52f, s * .6f), style = st); line(.24f, .46f, .24f, .7f); line(.76f, .46f, .76f, .7f); line(.16f, .72f, .84f, .72f); drawCircle(color, s * .06f, Offset(s * .5f, s * .86f)) }
            "book" -> { drawRoundRect(color, Offset(s * .18f, s * .16f), Size(s * .64f, s * .68f), CornerRadius(s * .08f), style = st); line(.36f, .36f, .64f, .36f); line(.36f, .54f, .64f, .54f) }
        }
    }
}

/** How did it feel? One tap, five answers. Tapping the chosen one again clears it. */
@Composable
fun EffortRow(feel: Int, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(if (feel > 0) "Felt ${FEELS[feel - 1].lowercase()}" else "How did it feel?")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FEELS.forEachIndexed { i, n -> Pick(n, feel == i + 1, Modifier.weight(1f)) { onPick(if (feel == i + 1) 0 else i + 1) } }
        }
    }
}

/** The talk-test result: one number, the highest heart rate at which talking was still comfortable. */
@Composable
fun TalkEntry(e: Engine, save: (Int) -> Unit) {
    var v by remember { mutableStateOf(if (e.s.talkHr > 0) "${e.s.talkHr}" else "") }
    var err by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Field("Talking ceiling (bpm)", v, Modifier.weight(1f), "${e.zb[2] - 1}") { v = it }
        Btn("Save") {
            val n = v.toIntOrNull() ?: 0
            // Anything outside a believable Zone 2 top is more likely a typo than a finding.
            if (n in (e.s.restHr + 30)..(e.s.maxHr - 8)) { err = ""; save(n) } else err = "That should sit between ${e.s.restHr + 30} and ${e.s.maxHr - 8} bpm for you."
        }
    }
    if (err.isNotEmpty()) Small(err, LocalPal.current.warnInk)
}

/** Which icon stands for a session kind. */
fun glyphFor(kind: String) = when (kind) {
    "long" -> "road"; "tempo" -> "ramp"; "4x4", "6x3", "tune", "intervals" -> "bars"; "test", "talk" -> "target"; "runwalk" -> "walk"
    "strides" -> "bolt"; "race" -> "flag"; "rest" -> "moon"; "strength" -> "dumbbell"; else -> "heart"
}

/** A session's picture: its icon on a soft disc, tinted by how hard the session is. */
@Composable
fun SessionArt(kind: String, modifier: Modifier = Modifier.size(44.dp), done: Boolean = false) {
    val p = LocalPal.current
    val hard = kind in setOf("tempo", "4x4", "6x3", "tune", "talk", "intervals", "race")
    val quiet = kind == "rest" || kind == "strength"
    val bg = when { done -> p.accent; hard -> p.warnBg; quiet -> p.surface2; else -> p.goodBg }
    val fg = when { done -> p.accentInk; hard -> p.warnInk; quiet -> p.muted; else -> p.goodInk }
    Box(modifier.clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { Glyph(if (done) "check" else glyphFor(kind), fg, Modifier.fillMaxSize(.52f)) }
}

/** A row that says one thing and opens to say the rest. */
@Composable
fun Fold(glyph: String, title: String, summary: String, startOpen: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    val tick = haptic()
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(startOpen) }
    val turn = animFloat(if (open) 180f else 0f, 220)
    Column {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { tick(false); open = !open }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Glyph(glyph, p.muted, Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = p.ink)
                if (summary.isNotEmpty()) Text(summary, fontFamily = BodyFont, fontSize = 13.5.sp, lineHeight = 18.sp, color = p.muted, maxLines = if (open) 6 else 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Glyph("down", p.muted, Modifier.size(18.dp).graphicsLayerRotate(turn))
        }
        androidx.compose.animation.AnimatedVisibility(open) { Column(Modifier.padding(start = 34.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }
    }
}

fun Modifier.graphicsLayerRotate(deg: Float) = this.then(Modifier.graphicsLayer { rotationZ = deg })

/** A row that goes somewhere. */
@Composable
fun Link(glyph: String, title: String, summary: String, onClick: () -> Unit) {
    val p = LocalPal.current
    val tick = haptic()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { tick(false); onClick() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Glyph(glyph, p.muted, Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = p.ink)
            if (summary.isNotEmpty()) Text(summary, fontFamily = BodyFont, fontSize = 13.5.sp, lineHeight = 18.sp, color = p.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Glyph("right", p.muted, Modifier.size(18.dp))
    }
}

/** Where the time went: one bar, five zones. */
@Composable
fun ZoneBar(zoneSec: List<Int>, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val tot = zoneSec.sum().toFloat()
    val grow = enter(700, 150)
    Row(modifier.fillMaxWidth(grow.coerceAtLeast(.02f)).height(14.dp).clip(RoundedCornerShape(7.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        zoneSec.forEachIndexed { i, sec -> if (sec > 0) Box(Modifier.weight(sec / tot).fillMaxHeight().background(p.zones[i])) }
    }
}
