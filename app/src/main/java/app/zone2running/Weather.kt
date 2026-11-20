package app.zone2running

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/** One forecast hour as the strip shows it. */
private class WxHour(val date: LocalDate, val hour: Int, val temp: Double, val dew: Double, val sky: Sky?) {
    val heat get() = heatPct(temp, dew)
    val wet get() = sky != null && (sky.rain >= 50 || sky.code in 51..67 || sky.code in 80..82 || sky.code >= 95)
    val night get() = hour < 6 || hour >= 18
}

/** A WMO weather code in a word or two, and which picture stands for it. */
private fun skyWord(code: Int, night: Boolean): Pair<String, String> = when (code) {
    0 -> "Clear" to (if (night) "moon" else "sun"); 1, 2 -> "Partly cloudy" to (if (night) "cloud" else "part"); 3 -> "Overcast" to "cloud"
    45, 48 -> "Fog" to "fog"; in 51..57 -> "Drizzle" to "rain"; in 61..67 -> "Rain" to "rain"; in 71..77, 85, 86 -> "Snow" to "cloud"
    in 80..82 -> "Showers" to "rain"; in 95..99 -> "Thunderstorm" to "storm"; else -> "Cloudy" to "cloud"
}

private fun hourName(h: Int) = when { h == 0 -> "12 am"; h < 12 -> "$h am"; h == 12 -> "12 pm"; else -> "${h - 12} pm" }

/**
 * The weather where the runner is, on the home screen: now, the next three hours, and the one thing a Zone 2 runner needs from it,
 * which is what the heat or the rain will do to the run. Drawn only from the forecast already on the phone; tapping shows the rest of the day.
 */
/** Whether the full forecast card is showing. The line in the pace card opens and closes it. */
var wxOpen by mutableStateOf(false)

private fun wxHours(w: WeatherCache, today: LocalDate, now: LocalTime) = (0..15).mapNotNull { i ->
    val h = now.hour + i; val d = today.plusDays((h / 24).toLong()); val hr = h % 24
    w.at(d, hr * 60)?.let { WxHour(d, hr, it.first, it.second, w.skyAt(d, hr)) }
}.takeIf { it.firstOrNull()?.hour == now.hour } ?: emptyList()                         // empty when the forecast on the phone no longer covers this hour

/**
 * The weather in one line at the foot of the pace card: now, the next two hours, and a two-word verdict. Tapping opens the full card.
 */
@Composable
fun WeatherLine(w: WeatherCache, today: LocalDate, now: LocalTime, tick: Int, ink: Color, soft: Color, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val all = remember(tick, today, now.hour, w.fetchedAt) { wxHours(w, today, now) }
    val first = all.firstOrNull() ?: return
    val near = all.take(3)
    val rainAt = near.firstOrNull { it.wet }; val peak = near.maxBy { it.heat }
    val (tone, verdict) = when {
        rainAt != null -> "plain" to (if (rainAt === first) "Raining" else "Rain ${hourName(rainAt.hour)}")
        peak.heat >= 1.5 && peak !== first && peak.heat >= first.heat + .7 -> "warn" to "Warming up"
        first.heat >= 1.5 -> "warn" to "Warm"
        else -> "good" to "Good to run"
    }
    Row(modifier.clip(RoundedCornerShape(10.dp)).clickable { wxOpen = !wxOpen }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        first.sky?.let { WxIcon(skyWord(it.code, first.night).second, Modifier.size(24.dp)) }
        Text("${first.temp.roundToInt()}°", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = ink)
        run {
            Text(near.drop(1).joinToString("   ") { "${it.temp.roundToInt()}° ${hourName(it.hour)}" }, Modifier.weight(1f), fontFamily = BodyFont, fontSize = 14.sp, color = soft, maxLines = 1)
            val (bg, fg) = when (tone) { "warn" -> p.warnBg to p.warnInk; "good" -> p.goodBg to p.goodInk; else -> p.surface2 to p.ink }
            Text(verdict, Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp), fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = fg, maxLines = 1)
        }
    }
}

@Composable
fun WeatherStrip(w: WeatherCache, city: String, today: LocalDate, now: LocalTime, tick: Int) {
    val p = LocalPal.current
    var more by rememberSaveable { mutableStateOf(false) }
    val all = remember(tick, today, now.hour, w.fetchedAt) { wxHours(w, today, now) }
    val first = all.firstOrNull() ?: return
    val near = all.take(4)
    val (word, _) = first.sky?.let { skyWord(it.code, first.night) } ?: ("" to "")
    val humid = (100 * exp(17.625 * first.dew / (243.04 + first.dew)) / exp(17.625 * first.temp / (243.04 + first.temp))).roundToInt().coerceIn(0, 100)
    val rainAt = near.firstOrNull { it.wet }
    val peak = near.maxBy { it.heat }
    val pct = { v: Double -> v.roundToInt().coerceAtLeast(1).toString() }
    val (tone, line) = when {
        rainAt != null -> "plain" to (if (rainAt === first) "Raining now. Roads will be slippery; heart rate usually runs a little lower in the cool." else "Rain likely from ${hourName(rainAt.hour)}. Going sooner keeps you dry.")
        peak.heat >= 1.5 && peak !== first && peak.heat >= first.heat + .7 -> "warn" to "Warming up: an easy pace costs about ${pct(peak.heat)}% more by ${hourName(peak.hour)}. Sooner is cooler."
        first.heat >= 1.5 -> "warn" to "Warm and ${if (first.dew >= 20) "humid" else "dry"}: expect an easy pace about ${pct(first.heat)}% slower at the same heart rate."
        else -> "good" to "Good conditions for an easy run."
    }
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(p.surface).then(if (p.glass) Modifier.glassEdge(shape) else Modifier.border(1.dp, p.line, shape)).clickable { more = !more }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Label("${city.substringBefore(",").ifBlank { "Weather" }} · now") }
            val age = (System.currentTimeMillis() - w.fetchedAt) / 3600_000L
            if (age >= 6) Small("forecast from ${age}h ago")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            first.sky?.let { WxIcon(skyWord(it.code, first.night).second, Modifier.size(40.dp)) }
            Text("${first.temp.roundToInt()}°", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 38.sp, color = p.ink)
            Column {
                if (word.isNotEmpty()) Text(word, fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = p.ink)
                Small("Humidity $humid%" + (first.sky?.let { " · wind ${if (U.miles) "${(it.wind / 1.609).roundToInt()} mph" else "${it.wind} km/h"}" } ?: ""))
            }
        }
        val cells: @Composable RowScope.(List<WxHour>, Boolean) -> Unit = { list, fill ->
            val anyRain = list.any { it.sky != null && it.sky.rain >= 20 }                // the rain line only takes room when there is rain to show
            list.forEachIndexed { i, h ->
                Column((if (fill) Modifier.weight(1f) else Modifier.width(58.dp)).clip(RoundedCornerShape(10.dp)).background(if (h === first) p.surface2 else Color.Transparent).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (h === first) "Now" else hourName(h.hour), fontFamily = BodyFont, fontSize = 13.sp, fontWeight = if (h === first) FontWeight.SemiBold else FontWeight.Normal, color = if (h === first) p.ink else p.muted, maxLines = 1)
                    h.sky?.let { WxIcon(skyWord(it.code, h.night).second, Modifier.size(24.dp)) }
                    Text("${h.temp.roundToInt()}°", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = if (h.heat >= 1.5) p.warnInk else p.ink)
                    if (anyRain) Text(if (h.sky != null && h.sky.rain >= 20) "${h.sky.rain}%" else " ", fontFamily = BodyFont, fontSize = 12.5.sp, color = p.zones[0], maxLines = 1)
                }
            }
        }
        if (more) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) { cells(all, false) }
        else Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { cells(near, true) }
        NoteBox(Note(tone, line))
        Small(if (more) "Swipe for later hours. Percentages are the chance of rain." else "Tap for the rest of the day.")
    }
}

/** Weather pictures drawn in the app's own line style, so nothing is downloaded and they follow light and dark. */
@Composable
fun WxIcon(kind: String, modifier: Modifier = Modifier.size(24.dp)) {
    val p = LocalPal.current
    Canvas(modifier) {
        val s = size.minDimension; val st = Stroke(s * .075f, cap = StrokeCap.Round)
        fun sun(c: Offset, r: Float) {
            drawCircle(p.gold, r, c)
            for (i in 0 until 8) { val a = i * Math.PI / 4; val d = Offset(cos(a).toFloat(), sin(a).toFloat()); drawLine(p.gold, c + d * (r * 1.45f), c + d * (r * 1.95f), st.width, StrokeCap.Round) }
        }
        fun cloud(y: Float, col: Color) {
            val path = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(s * .14f, y, s * .9f, y + s * .3f, CornerRadius(s * .15f)))
                addOval(androidx.compose.ui.geometry.Rect(Offset(s * .4f, y - s * .02f), s * .2f)); addOval(androidx.compose.ui.geometry.Rect(Offset(s * .62f, y + s * .08f), s * .15f))
            }
            drawPath(path, col)
        }
        val grey = p.muted.copy(alpha = .75f)
        when (kind) {
            "sun" -> sun(center, s * .22f)
            "moon" -> { val path = Path.combine(androidx.compose.ui.graphics.PathOperation.Difference,
                Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, s * .34f)) }, Path().apply { addOval(androidx.compose.ui.geometry.Rect(center + Offset(s * .17f, -s * .12f), s * .3f)) }); drawPath(path, p.zones[0]) }
            "part" -> { sun(Offset(s * .36f, s * .36f), s * .15f); cloud(s * .5f, grey) }
            "cloud" -> cloud(s * .38f, grey)
            "fog" -> for (i in 0..2) drawLine(grey, Offset(s * .18f, s * (.34f + i * .17f)), Offset(s * .82f, s * (.34f + i * .17f)), st.width, StrokeCap.Round)
            "rain", "storm" -> {
                cloud(s * .24f, grey)
                if (kind == "rain") for (i in 0..2) drawLine(p.zones[0], Offset(s * (.34f + i * .2f), s * .66f), Offset(s * (.28f + i * .2f), s * .86f), st.width, StrokeCap.Round)
                else drawPath(Path().apply { moveTo(s * .56f, s * .58f); lineTo(s * .4f, s * .78f); lineTo(s * .52f, s * .78f); lineTo(s * .44f, s * .96f); lineTo(s * .66f, s * .72f); lineTo(s * .54f, s * .72f); close() }, p.gold)
            }
        }
    }
}
