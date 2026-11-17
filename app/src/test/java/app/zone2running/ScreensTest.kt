package app.zone2running

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** Drives the real screens on the JVM and saves a picture of each one. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h1900dp-xhdpi")
class ScreensTest {
    @get:Rule val rule = createComposeRule()
    private val today = LocalDate.now()

    @Before fun still() { Motion.on = false; U.miles = false; Live.snap = LiveSnap() }

    private fun seeded(): AppVM {
        val vm = AppVM(ApplicationProvider.getApplicationContext())
        vm.updateSettings { it.copy(introSeen = true, onboarded = true, disclaimerOk = true, maxHr = 188, planStart = mondayOf(today).minusWeeks(5), lessonsDone = listOf("zone2", "talk")) }
        listOf(
            Triple(30L, "hard", Triple(10.26, 4297, 162)), Triple(27L, "easy", Triple(5.0, 2700, 146)), Triple(26L, "easy", Triple(6.0, 3180, 146)),
            Triple(22L, "long", Triple(9.0, 4800, 145)), Triple(20L, "easy", Triple(5.0, 2650, 144)), Triple(18L, "easy", Triple(5.0, 2600, 143)),
            Triple(15L, "test", Triple(5.6, 2900, 144)), Triple(13L, "long", Triple(10.5, 5400, 145)), Triple(11L, "easy", Triple(6.0, 3100, 143)),
            Triple(9L, "easy", Triple(6.2, 3150, 143)), Triple(5L, "tempo", Triple(7.0, 3000, 158)),
        ).forEach { (ago, type, v) -> vm.saveRun(Run("r$ago", today.minusDays(ago), type, v.first, v.second, v.third), isNew = false) }
        vm.saveRun(Run("r1", today.minusDays(1), "easy", 6.5, 3250, 142, zoneSec = listOf(200, 2450, 520, 80, 0), splits = listOf(Split(505, 139), Split(498, 141), Split(502, 142), Split(496, 143), Split(499, 143), Split(494, 144))), isNew = false)
        vm.toast = null; vm.clock = LocalTime.of(18, 0)
        return vm
    }

    private fun shot(name: String) = rule.onRoot().captureRoboImage("build/shots/$name.png")
    private fun tab(name: String) { rule.onNodeWithText(name).performClick(); rule.waitForIdle() }
    private fun cog(row: String) { rule.onNodeWithContentDescription("Settings").performClick(); rule.onNodeWithText(row).performClick(); rule.waitForIdle() }
    private fun back(times: Int = 1) { repeat(times) { rule.onNodeWithContentDescription("Back").performClick() }; rule.waitForIdle() }

    @Test fun threeTabsAndTheCog() {
        val vm = seeded()
        rule.setContent { AppTheme { App(vm) } }
        shot("today")
        tab("Plan"); shot("plan")
        tab("Progress"); shot("progress")
        rule.onNodeWithText("Runs").performClick(); rule.waitForIdle(); shot("runs")
        val d = today.minusDays(1)
        rule.onNodeWithText("${DOW[d.dayOfWeek.value - 1]} ${fmtDay(d)} · Easy run").performClick(); rule.waitForIdle()
        // Any logged run can be shared from its own page, and the card is drawn for that run.
        rule.onNodeWithText("Share").assertExists()
        val old = vm.data.runs.minBy { it.date }
        val before = Engine(vm.settings, vm.data.runs.filter { it.id != old.id }, today)
        val oldCard = ShareCard.render(ApplicationProvider.getApplicationContext(), buildCelebration(before, vm.engine(), listOf(old)))
        File("build/shots").mkdirs(); File("build/shots/share-card-old-run.png").outputStream().use { oldCard.compress(Bitmap.CompressFormat.PNG, 100, it) }
        rule.onNodeWithText("WHAT THIS RUN SAYS").assertExists(); shot("run-page")
        rule.onNodeWithText("Edit").performClick(); rule.waitForIdle(); rule.onNodeWithText("Edit run").assertExists()
        rule.onNodeWithText("Cancel").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithText("WHAT THIS RUN SAYS").assertExists()
    }


    @Test fun badgesWeekOffAndEverythingBehindTheCog() {
        val vm = seeded()
        rule.setContent { AppTheme { App(vm) } }
        tab("Progress"); rule.onNodeWithText("Badges").performClick(); rule.waitForIdle(); shot("badges")
        tab("Plan")
        rule.onNodeWithText("Take this week off").performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(listOf(mondayOf(today)), vm.settings.breakWeeks); shot("plan-week-off")
        cog("Learn"); shot("learn"); back()
        rule.onNodeWithText("Settings").performClick(); rule.waitForIdle(); shot("settings"); back()
        rule.onNodeWithText("About").performClick(); rule.waitForIdle(); shot("about")
        rule.onNodeWithText("Privacy policy").performClick(); rule.waitForIdle(); shot("privacy"); back()
        rule.onNodeWithText("Licences and credits").performClick(); rule.waitForIdle(); back(3)
        rule.onNodeWithText("Today").assertExists()
    }

    @Test @Config(qualifiers = "w400dp-h1900dp-night-xhdpi") fun dark() {
        val vm = seeded()
        rule.setContent { AppTheme { App(vm) } }
        shot("today-dark"); tab("Plan"); shot("plan-dark"); tab("Progress"); shot("progress-dark")
    }

    @Test @Config(qualifiers = "w400dp-h860dp-xhdpi") fun introStoryInMotion() {
        Motion.on = true
        val vm = AppVM(ApplicationProvider.getApplicationContext())
        rule.mainClock.autoAdvance = false
        rule.setContent { AppTheme { App(vm) } }
        for (i in 1..6) {
            rule.mainClock.advanceTimeBy(2400)
            shot("intro-$i")
            rule.onNodeWithText(if (i < 6) "Next" else "Build my plan").performClick()
            rule.mainClock.advanceTimeBy(700)
        }
        assertTrue(vm.settings.introSeen)
        Motion.on = false
    }

    @Test @Config(qualifiers = "w400dp-h900dp-xhdpi") fun todayChangesThroughTheDay() {
        val vm = seeded()
        vm.updateSettings { it.copy(days = (1..7).toList()) }                      // make today a run day
        vm.clock = LocalTime.of(7, 0)
        rule.setContent { AppTheme { App(vm) } }
        rule.onNodeWithText("TODAY'S RUN").assertExists(); shot("moment-morning")
        vm.clock = LocalTime.of(18, 30); rule.waitForIdle()
        rule.onNodeWithText("READY WHEN YOU ARE").assertExists()
        rule.onNodeWithText("Details").performClick(); rule.waitForIdle(); shot("moment-evening-details")
        vm.clock = LocalTime.of(22, 30); rule.waitForIdle()
        rule.onNodeWithText("GETTING LATE").assertExists()
        vm.clock = LocalTime.of(19, 30)
        vm.saveRun(Run("now", today, "easy", 7.0, 3480, 141, zoneSec = listOf(240, 2400, 720, 120, 0)), isNew = false); rule.waitForIdle()
        rule.onNodeWithText("Kept it easy. That's the work.").assertExists(); shot("moment-done")
    }

    @Test @Config(qualifiers = "w400dp-h900dp-xhdpi") fun paceImprovementIsNoticedOnce() {
        val vm = seeded()
        rule.setContent { AppTheme { App(vm) } }
        rule.waitForIdle()
        val first = vm.settings.seenPace
        assertTrue(first > 0)                                                      // the home screen remembered what it showed
        vm.saveRun(Run("fast", today, "easy", 6.5, 2990, 142), isNew = false); rule.waitForIdle()
        assertTrue(vm.settings.seenPace < first)                                   // and moved on once the faster pace was shown
    }

    @Test @Config(qualifiers = "w400dp-h1100dp-xhdpi") fun suggestionIsOfferedAndApplied() {
        val vm = AppVM(ApplicationProvider.getApplicationContext())
        vm.updateSettings { it.copy(introSeen = true, onboarded = true, maxHr = 188, planStart = mondayOf(today).minusWeeks(5), remindOn = true, remindMin = 1080) }
        val mon = mondayOf(today)
        for (w in 1L..4L) for ((d, start) in listOf(0L to 1140, 3L to 1155, 5L to 1125))
            vm.saveRun(Run("h$w$d", mon.minusWeeks(w).plusDays(d), "easy", 6.0, 2700, 142, startMin = start), isNew = false)
        vm.toast = null; vm.clock = LocalTime.of(18, 0)
        rule.setContent { AppTheme { App(vm) } }
        rule.onNodeWithText("Move Tuesday's run to Monday?").assertExists(); shot("suggestion")
        rule.onNodeWithText("Move it").performClick(); rule.waitForIdle()
        assertEquals(listOf(1, 4, 6, 7), vm.settings.days)
        rule.onNodeWithText("Move Tuesday's run to Monday?").assertDoesNotExist()
    }

    @Test @Config(qualifiers = "w400dp-h1500dp-xhdpi") fun liveRunScreens() {
        val vm = seeded()
        vm.updateSettings { it.copy(days = (1..7).toList()) }
        rule.setContent { AppTheme { App(vm) } }
        rule.onNodeWithText("Start run").performClick(); rule.waitForIdle()
        rule.onNodeWithText("GUIDED RUN").assertExists(); shot("live-briefing")
    }

    /** The service can't run here, so the running screen is drawn from snapshots it would publish. */
    @Test @Config(qualifiers = "w412dp-h892dp-xhdpi") fun liveRunStates() {
        val vm = seeded()
        val e = vm.engine()
        rule.setContent { AppTheme { App(vm) } }
        fun wave(n: Int, base: Int, amp: Int, rise: Int = 0) = List(n) { i -> base + rise * i / n + (amp * kotlin.math.sin(i / 4.0)).toInt() }
        val easy = e.steps(Session("easy", "Easy run", 45, ""))
        Live.type = "easy"; Live.steps = easy
        Live.snap = LiveSnap(true, false, 1325, 2740.0, 144, 2, 1, "Easy running", 1175, "easy", "", 484.0, "GPS locked", "Connected",
            listOf(Split(492, 141), Split(478, 146)), 142, "", wave(120, 141, 3))
        rule.waitForIdle(); shot("live-1-in-zone")
        rule.onNodeWithText("In Zone 2").assertExists()
        Live.snap = Live.snap.copy(hr = 153, zone = 3, trace = wave(120, 143, 3, 9), lastCue = "Ease off. Heart rate 153.")
        rule.waitForIdle(); shot("live-2-over")
        rule.onNodeWithText("Above Zone 2. Ease off or walk until you're under 150.").assertExists()
        val hard = e.steps(Session("intervals", "4 × 4 min hard", 50, "", "4x4"))
        Live.type = "intervals"; Live.steps = hard
        Live.snap = LiveSnap(true, false, 1150, 2910.0, 166, 4, 3, "Hard, rep 2 of 4", 110, "hard", "Easy jog", 338.0, "GPS locked", "Connected",
            listOf(Split(420, 138), Split(371, 152)), 149, "", wave(60, 140, 4) + wave(30, 150, 2, 16) + wave(20, 150, 3, -8) + wave(26, 148, 2, 18))
        rule.waitForIdle(); shot("live-3-intervals")
        Live.type = "easy"; Live.steps = easy
        Live.snap = LiveSnap(true, false, 1325, 2740.0, 0, 0, 1, "Easy running", 1175, "easy", "", 484.0, "GPS locked", "", listOf(Split(492, 0), Split(478, 0)))
        rule.waitForIdle(); shot("live-4-no-sensor")
        Live.snap = LiveSnap(true, true, 1325, 2740.0, 128, 1, 1, "Easy running", 1175, "easy", "", null, "GPS locked", "Connected",
            listOf(Split(492, 141), Split(478, 146)), 142, "", wave(120, 141, 3))
        rule.waitForIdle(); shot("live-5-paused")
        rule.onNodeWithText("Finish").assertExists()
        Live.snap = LiveSnap(); Live.steps = emptyList(); rule.waitForIdle()
    }

    @Test @Config(qualifiers = "w400dp-h1700dp-xhdpi") fun swapASessionAndSwitchToMiles() {
        val vm = seeded()
        vm.updateSettings { it.copy(days = (1..7).toList()) }
        rule.setContent { AppTheme { App(vm) } }
        tab("Plan")
        rule.onNodeWithText("What you'll do").performClick(); rule.waitForIdle(); shot("plan-blocks")
        rule.onNodeWithText("Change").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Run-walk").performClick(); rule.waitForIdle()
        assertEquals(mapOf(today to "runwalk"), vm.settings.overrides)
        rule.onNodeWithText("Easy run-walk").assertExists()
        try {
            vm.updateSettings { it.copy(miles = true, raceName = "City Half", raceDate = today.plusDays(40), raceKm = 21.1) }
            tab("Today")
            rule.onNodeWithText("City Half · 13.1 mi").assertExists(); shot("today-miles-race")
        } finally { U.miles = false }
    }

    @Test fun forecastParses() {
        val json = """{"hourly":{"time":["2026-11-17T18:00","2026-11-17T19:00"],"temperature_2m":[29.5,28.1],"dew_point_2m":[21.0,null]}}"""
        val h = Online.parseForecast(json)
        assertEquals(1, h.size); assertEquals(29.5 to 21.0, h["2026-11-17T18"])
    }

    @Test fun lessonCanBeFinished() {
        val vm = seeded()
        rule.setContent { AppTheme { App(vm) } }
        cog("Learn")
        rule.onNodeWithText("Why not hard every day?").performClick(); rule.waitForIdle(); shot("lesson-card")
        rule.onNodeWithText("Next").performClick(); rule.onNodeWithText("Next").performClick(); rule.onNodeWithText("Quick question").performClick()
        rule.onNodeWithText("It is too slow to count").performClick(); rule.waitForIdle()
        rule.onNodeWithText("It tires you without the payoff of a real hard session").performClick(); rule.waitForIdle(); shot("lesson-question")
        rule.onNodeWithText("Finish").performClick(); rule.waitForIdle()
        assertTrue("grey" in vm.settings.lessonsDone)
    }

    @Test fun guideThenFirstRunIsCelebrated() {
        val vm = AppVM(ApplicationProvider.getApplicationContext())
        rule.setContent { AppTheme { App(vm) } }
        rule.onNodeWithText("Skip").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Next").performClick()                                   // age 30
        rule.onNodeWithText("Next").performClick()                                   // resting 60
        rule.onNodeWithText("Yes, I know a pace and heart rate").performClick()
        rule.onNodeWithText("Next").performScrollTo().performClick()                 // 8:00 at 150 bpm
        rule.waitForIdle()
        rule.onNodeWithText("7:25 /km · Ambitious").performClick()
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("Just starting").performClick()
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("Sunday").performClick()
        rule.onNodeWithText("12 weeks · A new habit").performClick()
        rule.onNodeWithText("Not now").performClick()
        rule.onNodeWithText("I understand").performClick()
        rule.waitForIdle(); shot("guide-plan")
        rule.onNodeWithText("Start training").performScrollTo().performClick()
        rule.waitForIdle()
        assertTrue(vm.settings.onboarded)
        assertEquals(187, vm.settings.maxHr)                 // 208 − 0.7 × 30
        assertEquals(480, vm.settings.basePace)
        assertEquals(445, vm.settings.targetPace)
        assertEquals(25, vm.settings.easyMin)
        assertEquals(12, vm.settings.goalWeeks)
        shot("today-new-user")

        tab("Progress")
        rule.onNodeWithText("Runs").performClick()
        rule.onNodeWithText("Add run").performClick()
        rule.onNodeWithText("Distance (km)").performTextInput("6")
        rule.onNodeWithText("Time").performTextInput("54:00")
        rule.onNodeWithText("Average HR").performTextInput("138")
        rule.onNodeWithText("Save run").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(1, vm.data.runs.size)
        shot("reward")
        // The share picture is drawn from the same reward data.
        val card = ShareCard.render(ApplicationProvider.getApplicationContext(), vm.celebration!!)
        File("build/shots").mkdirs()
        File("build/shots/share-card.png").outputStream().use { card.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertEquals(1080, card.width)
        rule.onNodeWithText("Done").performClick()
        rule.waitForIdle()
        // Reloading from disk gives back the same run and settings.
        val again = AppVM(ApplicationProvider.getApplicationContext())
        assertEquals(vm.data.runs, again.data.runs)
        assertEquals(vm.settings, again.settings)
    }

    private fun tr(vararg parts: Triple<Int, Int, Int>) = parts.flatMap { (sec, a, b) -> List(sec / TRACE_STEP) { i -> a + (b - a) * i / (sec / TRACE_STEP) + (2 * kotlin.math.sin(i / 2.0)).toInt() } }

    /** The run page for three kinds of run: a run-walk, intervals, and an imported hard 10 km. */
    @Test @Config(qualifiers = "w412dp-h3300dp-xhdpi") fun richRunPages() {
        val vm = seeded()
        val e = vm.engine()
        val rw = e.steps(Session("easy", "Easy run-walk", 40, "", "runwalk"))
        val rwTrace = (0 until 8).flatMap { c -> tr(Triple(240, 131 + c, 145 + c), Triple(60, 145 + c, 130 + c)) }
        val a = Run("rich-rw", today, "easy", 4.9, 2400, 141, 153, source = "live", trace = rwTrace, steps = rw, kind = "runwalk",
            zoneSec = listOf(420, 1740, 240, 0, 0), splits = listOf(Split(492, 138), Split(488, 140), Split(495, 142), Split(486, 144)),
            pace = (0 until 240).map { i -> if (i % 30 >= 24) 690 + i % 7 * 6 else 450 + i % 5 * 5 }, cadence = 164, kcal = 318)
        val iv = e.steps(Session("intervals", "4 × 4 min hard", 50, "", "4x4"))
        val ivTrace = tr(Triple(600, 118, 141)) + (0 until 4).flatMap { c -> tr(Triple(240, 141, 169 + c * 2), Triple(180, 168, 139)) } + tr(Triple(600, 140, 124))
        val b = Run("rich-iv", today.minusDays(2), "intervals", 6.9, 2880, 148, 177, source = "live", trace = ivTrace, steps = iv, kind = "4x4",
            zoneSec = listOf(700, 640, 520, 900, 120), splits = listOf(Split(470, 128), Split(452, 139), Split(380, 158), Split(402, 156), Split(385, 160), Split(410, 152)),
            pace = (0 until 288).map { i -> val t = i * 10; if (t in 600 until 2280 && (t - 600) % 420 < 240) 330 + i % 4 * 4 else 480 + i % 6 * 5 })
        val c = Run("rich-hc", today.minusDays(1), "hard", 10.26, 4297, 162, 178, source = "health", notes = "Outdoor run",
            trace = tr(Triple(360, 116, 150), Triple(600, 150, 163), Triple(2400, 161, 168), Triple(930, 166, 176)), zoneSec = listOf(240, 130, 250, 2577, 1100), feel = 3)
        listOf(a, b, c).forEach { vm.saveRun(it, isNew = false) }
        var open by androidx.compose.runtime.mutableStateOf(a)
        rule.setContent { AppTheme { androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxSize().background(LocalPal.current.bg).padding(20.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(18.dp)) { RunPage(vm.engine(), open, rate = {}, edit = {}) {} } } }
        rule.waitForIdle(); shot("run-rich-runwalk")
        rule.onNodeWithText("Block by block", ignoreCase = true).assertExists()
        open = b; rule.waitForIdle(); shot("run-rich-intervals")
        open = c; rule.waitForIdle(); shot("run-rich-imported")
        rule.onNodeWithText("A hard effort").assertExists()
    }

    /** A made-up loop of about 5 km, as the app stores a route. */
    private fun loop(points: Int = 400, secs: Int = 2400) = (0 until points).flatMap { i ->
        val a = 2 * Math.PI * i / points
        listOf(secs * i / points, ((20.0 + .0062 * kotlin.math.sin(a) + .0015 * kotlin.math.sin(3 * a)) * 1e5).toInt(), ((78.0 + .0085 * kotlin.math.cos(a) + .002 * kotlin.math.sin(2 * a)) * 1e5).toInt())
    }

    @Test @Config(qualifiers = "w412dp-h892dp-xhdpi") fun liveMapAndGpsMeter() {
        val vm = seeded()
        val e = vm.engine()
        rule.setContent { AppTheme { App(vm) } }
        rule.onNodeWithText("Start run").performClick(); rule.waitForIdle()
        rule.onNodeWithText("GPS SIGNAL").assertExists(); shot("live-briefing-gps")
        Live.type = "easy"; Live.steps = e.steps(Session("easy", "Easy run", 45, ""))
        Live.snap = LiveSnap(true, false, 1325, 2740.0, 144, 2, 1, "Easy running", 1175, "easy", "", 484.0, "GPS locked", "Connected",
            listOf(Split(492, 141), Split(478, 146)), 142, "", List(120) { 141 + (3 * kotlin.math.sin(it / 4.0)).toInt() }, 6, loop().take(3 * 220))
        rule.waitForIdle()
        rule.onNodeWithText("GPS ±6 m").assertExists()
        rule.onNodeWithText("In Zone 2").performTouchInput { swipeLeft() }; rule.waitForIdle()
        rule.onNodeWithText("  swipe for the dial").assertExists(); shot("live-6-route")
        Live.snap = LiveSnap(); Live.steps = emptyList(); rule.waitForIdle()
    }

    @Test @Config(qualifiers = "w412dp-h1500dp-xhdpi") fun runPageWithRoute() {
        val vm = seeded()
        val r = Run("rich-map", today, "easy", 4.9, 2400, 141, 153, source = "live", route = loop(),
            trace = List(240) { 128 + it / 12 + (4 * kotlin.math.sin(it / 6.0)).toInt() }, zoneSec = listOf(300, 1700, 400, 0, 0),
            splits = listOf(Split(492, 138), Split(488, 142), Split(495, 146), Split(486, 149)))
        vm.saveRun(r, isNew = false)
        rule.setContent { AppTheme { androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxSize().background(LocalPal.current.bg).padding(20.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(18.dp)) { RunPage(vm.engine(), r, rate = {}, edit = {}) {} } } }
        rule.waitForIdle(); rule.onNodeWithText("ROUTE").assertExists(); shot("run-rich-route")
    }

    @Test @Config(qualifiers = "w412dp-h1250dp-xhdpi") fun planShowsWhatEachBlockAsksFor() {
        val vm = seeded()
        vm.updateSettings { it.copy(days = (1..7).toList(), overrides = mapOf(today to "talk")) }
        rule.setContent { AppTheme { App(vm) } }
        tab("Plan"); rule.onNodeWithText("What you'll do").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Stage 2 of 6. A touch faster. Say a sentence out loud").assertExists(); shot("plan-talk-test")
        vm.updateSettings { it.copy(overrides = mapOf(today to "4x4")) }; rule.waitForIdle()
        rule.onAllNodesWithText("162–174 bpm")[0].assertExists(); shot("plan-intervals-blocks")
    }
}
