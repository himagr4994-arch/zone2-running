package app.zone2running

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan

/** Web Mercator, with the whole world as the unit square. */
fun mercX(lon: Double) = (lon + 180) / 360
fun mercY(lat: Double) = (1 - ln(tan(Math.toRadians(lat)) + 1 / cos(Math.toRadians(lat))) / PI) / 2

/**
 * OpenStreetMap street tiles, only ever fetched when the runner has switched the street map on.
 * Each tile is kept on the phone after its first download, so a usual route costs the tile server
 * nothing the second time. The request carries the tile's coordinates and the app's name: nothing about the run.
 */
object Tiles {
    val got: SnapshotStateMap<String, ImageBitmap> = mutableStateMapOf()
    private val asked = HashSet<String>()
    private val pool = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    fun get(ctx: Context, z: Int, x: Int, y: Int): ImageBitmap? {
        val key = "$z/$x/$y"
        got[key]?.let { return it }
        if (!asked.add(key)) return null
        val app = ctx.applicationContext
        pool.execute {
            try {
                val f = File(File(app.cacheDir, "tiles").apply { mkdirs() }, "${z}_${x}_$y.png")
                if (!f.exists() || f.length() == 0L) {
                    val c = URL("https://tile.openstreetmap.org/$z/$x/$y.png").openConnection() as HttpURLConnection
                    c.setRequestProperty("User-Agent", "Zone2Running/${BuildConfig.VERSION_NAME} (app.zone2running; open-source running app)")
                    c.connectTimeout = 8000; c.readTimeout = 8000
                    if (c.responseCode == 200) c.inputStream.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                    c.disconnect()
                }
                BitmapFactory.decodeFile(f.path)?.asImageBitmap()?.let { bmp -> main.post { if (got.size > 80) got.clear(); got[key] = bmp } }
            } catch (e: Exception) { synchronized(asked) { asked.remove(key) } }      // offline now: ask again next time
        }
        return null
    }
}

/**
 * A run's path, fitted to the box. The line takes the colour of the heart-rate zone at each point when
 * there is a trace. With [streets] on, OpenStreetMap tiles are drawn underneath; off, it is the line alone
 * and nothing is fetched.
 */
@Composable
fun RouteMap(route: List<Int>, trace: List<Int>, e: Engine, streets: Boolean, modifier: Modifier, kmSecs: List<Int> = emptyList(), live: Boolean = false) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    val measurer = rememberTextMeasurer()
    val n = route.size / 3
    val dark = p.bg.red < .3f
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(if (streets) Color(0xFFE8ECEA) else p.surface)) {
        if (n < 2) { Text("The route appears once GPS has a fix.", Modifier.align(Alignment.Center), fontFamily = BodyFont, fontSize = 14.sp, color = p.muted); return@Box }
        Tiles.got.size      // redraw when a tile arrives
        Canvas(Modifier.fillMaxSize()) {
            val xs = DoubleArray(n) { mercX(route[it * 3 + 2] / 1e5) }; val ys = DoubleArray(n) { mercY(route[it * 3 + 1] / 1e5) }
            val pad = 26.dp.toPx()
            val minSpan = 6e-6                                               // about 240 m: a run's first minute shouldn't fill the screen
            val dx = maxOf(xs.max() - xs.min(), minSpan); val dy = maxOf(ys.max() - ys.min(), minSpan)
            val world = minOf((size.width - 2 * pad) / dx, (size.height - 2 * pad) / dy, 512.0 * 2.0.pow(17))
            val cx = (xs.max() + xs.min()) / 2; val cy = (ys.max() + ys.min()) / 2
            fun px(i: Int) = Offset((size.width / 2 + (xs[i] - cx) * world).toFloat(), (size.height / 2 + (ys[i] - cy) * world).toFloat())

            if (streets) {
                val z = (log2(world / 512) + .5).toInt().coerceIn(2, 18); val tiles = 2.0.pow(z); val ts = world / tiles
                val left = cx - size.width / 2 / world; val top = cy - size.height / 2 / world
                val x0 = floor(left * tiles).toInt(); val y0 = floor(top * tiles).toInt()
                val nx = (size.width / ts).toInt() + 2; val ny = (size.height / ts).toInt() + 2
                if (nx * ny <= 30) for (tx in x0 until x0 + nx) for (ty in y0 until y0 + ny) {
                    if (ty < 0 || ty >= tiles) continue
                    val img = Tiles.get(ctx, z, ((tx % tiles.toInt()) + tiles.toInt()) % tiles.toInt(), ty) ?: continue
                    drawImage(img, dstOffset = IntOffset(((tx / tiles - left) * world).roundToInt(), ((ty / tiles - top) * world).roundToInt()), dstSize = IntSize(ts.roundToInt() + 1, ts.roundToInt() + 1))
                }
                if (dark) drawRect(Color(0x55000000))
            }

            // A pale casing first, so the line reads on any background.
            val casing = if (streets) Color.White else p.surface
            for (i in 1 until n) drawLine(casing, px(i - 1), px(i), 8.dp.toPx(), cap = StrokeCap.Round)
            for (i in 1 until n) {
                val hr = trace.getOrNull(route[i * 3] / TRACE_STEP) ?: 0
                drawLine(if (hr > 0) p.zones[e.zoneOf(hr) - 1] else p.accent, px(i - 1), px(i), 4.5.dp.toPx(), cap = StrokeCap.Round)
            }
            kmSecs.forEachIndexed { k, sec ->
                val i = (0 until n).firstOrNull { route[it * 3] >= sec } ?: return@forEachIndexed
                val o = px(i); val t = measurer.measure("${k + 1}", TextStyle(color = Color(0xFF10201D), fontSize = 10.sp, fontFamily = BodyFont, fontWeight = FontWeight.SemiBold))
                drawCircle(Color(0xFF10201D), 9.5.dp.toPx(), o); drawCircle(Color.White, 8.dp.toPx(), o)
                drawText(t, topLeft = Offset(o.x - t.size.width / 2f, o.y - t.size.height / 2f))
            }
            drawCircle(Color.White, 7.dp.toPx(), px(0)); drawCircle(Color(0xFF10201D), 7.dp.toPx(), px(0), style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx()))
            drawCircle(Color.White, 9.dp.toPx(), px(n - 1)); drawCircle(if (live) Color(0xFF0B8573) else Color(0xFF10201D), 6.5.dp.toPx(), px(n - 1))

            // A scale bar, because a line with no map needs something to measure against.
            val metresPerPx = 40075016.0 * cos(Math.toRadians(route[1] / 1e5)) / world
            val nice = listOf(50, 100, 200, 500, 1000, 2000, 5000).lastOrNull { it / metresPerPx <= size.width * .3 } ?: 50
            val w = (nice / metresPerPx).toFloat(); val by = size.height - 14.dp.toPx(); val bx = 14.dp.toPx()
            val ink = if (streets) Color(0xFF10201D) else p.muted
            drawLine(ink, Offset(bx, by), Offset(bx + w, by), 2.dp.toPx(), cap = StrokeCap.Round)
            drawText(measurer.measure(if (U.miles) f2(nice / 1609.344) + " mi" else if (nice >= 1000) "${nice / 1000} km" else "$nice m", TextStyle(color = ink, fontSize = 11.sp, fontFamily = BodyFont, fontWeight = FontWeight.SemiBold)), topLeft = Offset(bx, by - 18.dp.toPx()))
        }
        if (streets) Text("© OpenStreetMap contributors", Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xCCFFFFFF)).padding(horizontal = 5.dp, vertical = 1.dp),
            fontFamily = BodyFont, fontSize = 10.sp, color = Color(0xFF10201D))
    }
}

/** Where each kilometre ended, in seconds from the start, from the splits. */
fun kmSecs(splits: List<Split>): List<Int> { var t = 0; return splits.map { t += it.sec; t } }

fun gpsWord(acc: Int) = when { acc < 0 -> "Looking for satellites"; acc <= 10 -> "Good"; acc <= 25 -> "Fair"; else -> "Weak" }
fun gpsColor(acc: Int) = when { acc < 0 -> Color(0xFF8DA29D); acc <= 10 -> Color(0xFF2BB673); acc <= 25 -> Color(0xFFE0A52E); else -> Color(0xFFE2574C) }

/**
 * How well the phone knows where it is, before the run starts. Starting on a weak fix makes the first
 * few hundred metres wrong, so this says when it is worth waiting.
 */
@Composable
fun GpsMeter() {
    val p = LocalPal.current
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) }
    var acc by remember { mutableIntStateOf(-1) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { got -> granted = got[Manifest.permission.ACCESS_FINE_LOCATION] == true }
    if (granted) DisposableEffect(Unit) {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val l = LocationListener { loc -> acc = loc.accuracy.roundToInt() }
        try { @SuppressLint("MissingPermission") val u = lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, l, Looper.getMainLooper()) } catch (e: Exception) { }
        onDispose { try { lm.removeUpdates(l) } catch (e: Exception) { } }
    }
    Panel {
        Label("GPS signal")
        if (!granted) {
            Body("See how accurate GPS is before you start, so the first stretch is measured properly.")
            Btn("Check GPS signal", ghost = true, small = true) { ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // Four bars, like a signal meter: more bars, tighter fix.
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val bars = when { acc < 0 -> 0; acc <= 5 -> 4; acc <= 10 -> 3; acc <= 25 -> 2; else -> 1 }
                    for (i in 1..4) Box(Modifier.width(8.dp).height((8 + i * 7).dp).clip(RoundedCornerShape(2.dp)).background(if (i <= bars) gpsColor(acc) else p.surface2))
                }
                Column(Modifier.weight(1f)) {
                    Text(if (acc < 0) "Searching…" else "± $acc m", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 32.sp, color = p.ink)
                    Small(when { acc < 0 -> "Stand in the open for a moment. It usually takes under a minute."; acc <= 10 -> "Good. Start whenever you like."; acc <= 25 -> "Fair. Waiting a few seconds more will tighten the distance."
                        else -> "Weak. Move away from buildings and trees before you start." })
                }
            }
        }
    }
}
