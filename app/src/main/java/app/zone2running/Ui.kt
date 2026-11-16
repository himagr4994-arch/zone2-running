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
import androidx.compose.runtime.Composable
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
    val bg = if (ghost || danger) Color.Transparent else p.accent
    val fg = when { danger -> p.danger; ghost -> p.ink; else -> p.accentInk }
    Box(
        modifier.clip(shape).background(bg).then(if (ghost || danger) Modifier.border(1.dp, p.line, shape) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = if (small) 12.dp else 16.dp, vertical = if (small) 8.dp else 12.dp),
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
    Box(
        modifier.clip(shape).background(if (on) p.accent else p.surface2).border(1.dp, if (on) p.accent else p.line, shape)
            .clickable(onClick = onClick).padding(vertical = 9.dp, horizontal = 4.dp),
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

@Composable
fun LevelBar(g: Game, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Level ${g.level} · ${levelName(g.level)}", Modifier.weight(1f), fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = p.ink)
            Text("${g.xp} / ${g.hi} XP", fontSize = 13.sp, color = p.muted, fontFamily = BodyFont)
        }
        Meter(((g.xp - g.lo).toFloat() / (g.hi - g.lo)).coerceAtLeast(.02f))
    }
}

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
