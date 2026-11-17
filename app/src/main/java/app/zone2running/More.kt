package app.zone2running

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The settings cog, drawn by hand: eight teeth around a ring. */
@Composable
fun Gear(modifier: Modifier = Modifier) {
    val ink = LocalPal.current.ink
    Canvas(modifier.size(22.dp).semantics { contentDescription = "Settings" }) {
        val c = Offset(size.width / 2, size.height / 2); val r = size.minDimension / 2
        for (i in 0 until 8) rotate(i * 45f, c) { drawRoundRect(ink, Offset(c.x - r * .17f, 0f), Size(r * .34f, r * .5f), CornerRadius(r * .1f)) }
        drawCircle(ink, r * .5f, c, style = Stroke(r * .34f))
    }
}

@Composable
private fun Header(title: String, back: () -> Unit) {
    val p = LocalPal.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("←", Modifier.clip(CircleShape).clickable(onClick = back).padding(horizontal = 10.dp, vertical = 4.dp).semantics { contentDescription = "Back" }, fontSize = 26.sp, color = p.ink)
        Text(title.uppercase(), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = 1.sp, color = p.ink)
    }
}

@Composable
private fun MenuRow(title: String, sub: String, onClick: () -> Unit) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = p.ink)
            Small(sub)
        }
        Text("›", fontSize = 26.sp, color = p.muted)
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(LocalPal.current.line))

/** Everything behind the cog: lessons, settings, and the pages about the app. */
@Composable
fun MoreScreen(vm: AppVM, e: Engine, page: String, go: (String?) -> Unit, openLesson: (Lesson) -> Unit, replayIntro: () -> Unit, rerunGuide: () -> Unit) {
    val p = LocalPal.current
    when (page) {
        "learn" -> { Header("Learn") { go("menu") }; LearnScreen(vm.settings, openLesson) }
        "settings" -> { Header("Settings") { go("menu") }; SetupScreen(vm, e, replayIntro, rerunGuide, "") { go("settings/$it") } }
        "settings/zones", "settings/race", "settings/live", "settings/weather", "settings/motivation", "settings/connect", "settings/data", "settings/health" -> {
            val sub = page.substringAfter("/")
            Header(mapOf("zones" to "Zones", "race" to "Race goal", "live" to "Live run", "weather" to "Weather", "motivation" to "Badges and reminders", "connect" to "Health Connect", "data" to "Your data", "health" to "Health check")[sub] ?: "Settings") { go("settings") }
            SetupScreen(vm, e, replayIntro, rerunGuide, sub)
        }
        "about" -> {
            Header("About") { go("menu") }
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Dil("hello", Modifier.size(72.dp))
                    Column { Head("Zone 2 Running"); Small("Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})") }
                }
                Body("A training app for one goal: getting faster at an easy heart rate. It turns your runs into a Zone 2 pace, builds a plan around it, and keeps your data on your phone.")
                NoteBox(Note("plain", "Not medical advice. Zones and pace estimates are rough guides. Talk to a doctor before starting a new plan if you have a health condition. $STOP_SIGNS"))
            }
            Panel {
                MenuRow("Privacy policy", "What stays on your phone and the two times the app goes online") { go("privacy") }
                Divider()
                MenuRow("Licences and credits", "Open source under MIT, fonts, weather data") { go("licences") }
            }
            DiagnosticsPanel(vm)
        }
        "privacy" -> {
            Header("Privacy") { go("about") }
            Panel {
                Head("The short version", 20)
                Body("No account, no server of ours, no ads, no analytics. Your runs and settings never leave the phone unless you export or share them yourself.")
            }
            Panel {
                Head("Where your data lives", 20)
                Body("Runs and settings are one file in the app's private storage. If you choose a backup folder, one dated copy is written there each day and the newest seven are kept. Sharing a run hands one picture to the app you pick.")
            }
            Panel {
                Head("When the app goes online", 20)
                Body("Never, unless you switch one of these on. All are off by default.")
                Body("Street map: asks OpenStreetMap's tile server for the map squares around your route. That server sees which area you are looking at, and your internet address.")
                Body("Weather: sends the coordinates of the town you typed to Open-Meteo, a few times a day.")
                Body("Update check: asks GitHub for the latest release of this app.")
                Small("No run, heart rate, name or device identifier is included in either request.", p.ink)
            }
            Panel {
                Head("Location", 20)
                Body("GPS is used while a live run is in progress, and on the screen before it to show signal strength. The route of a live run is saved on the phone with the run, and in backups you make. It is never sent anywhere.")
            }
            Panel {
                Head("Permissions", 20)
                listOf("Health Connect" to "Import runs recorded by other apps: distance and heart rate, and optionally pace, steps, calories and climb. Read-only.", "Location" to "Measure distance during a live run.", "Bluetooth" to "Connect a heart-rate sensor. Optional.",
                    "Notifications" to "The daily reminder and the run-in-progress notice.", "Internet" to "Weather and the update check, only when switched on.").forEach { (a, b) ->
                    Column { Text(a, fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = p.ink); Small(b) }
                }
                Small("Each one is asked for when it is needed, and the app keeps working if you say no.", p.ink)
            }
            Panel {
                Head("Crash reports", 20)
                Body("If the app crashes, the reason is saved on the phone. Nothing is sent. You can copy it from About to include in a bug report.")
            }
        }
        "licences" -> {
            Header("Licences") { go("about") }
            Panel {
                Head("Zone 2 Running", 20)
                Body("Copyright © 2026 Himanshu Agrawal. Released under the MIT licence: anyone may use, copy, change and share the code, provided the copyright notice stays with it. The software comes with no warranty.")
            }
            Panel {
                Head("Credits", 20)
                listOf("Barlow and Barlow Condensed" to "Typefaces by Jeremy Tribby, SIL Open Font License 1.1.", "Open-Meteo" to "Weather data by Open-Meteo.com, CC BY 4.0.", "OpenStreetMap" to "Map tiles and data © OpenStreetMap contributors, ODbL.",
                    "Jetpack Compose and AndroidX" to "Android libraries by Google, Apache License 2.0.").forEach { (a, b) ->
                    Column { Text(a, fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = p.ink); Small(b) }
                }
            }
        }
        else -> {
            Header("More") { go(null) }
            val done = LESSONS.count { it.id in vm.settings.lessonsDone }
            Panel {
                MenuRow("Learn", "$done of ${LESSONS.size} two-minute lessons done") { go("learn") }
                Divider()
                MenuRow("Settings", "Target pace, heart rate, training week, and everything else") { go("settings") }
                Divider()
                MenuRow("About", "Version ${BuildConfig.VERSION_NAME}, privacy policy, licences, help") { go("about") }
            }
        }
    }
}
