package app.zone2running

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
import java.io.File
import java.time.LocalDate

/** Drives the real screens on the JVM and saves a picture of each one. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h2700dp-xhdpi")
class ScreensTest {
    @get:Rule val rule = createComposeRule()
    private val today = LocalDate.now()

    @Before fun still() { Motion.on = false }

    private fun seeded(): AppVM {
        val vm = AppVM(ApplicationProvider.getApplicationContext())
        vm.updateSettings { it.copy(introSeen = true, onboarded = true, maxHr = 188, planStart = mondayOf(today).minusWeeks(5), lessonsDone = listOf("zone2", "talk")) }
        listOf(
            Triple(30L, "hard", Triple(10.26, 4297, 162)), Triple(27L, "easy", Triple(5.0, 2700, 146)), Triple(26L, "easy", Triple(6.0, 3180, 146)),
            Triple(22L, "long", Triple(9.0, 4800, 145)), Triple(20L, "easy", Triple(5.0, 2650, 144)), Triple(18L, "easy", Triple(5.0, 2600, 143)),
            Triple(15L, "test", Triple(5.6, 2900, 144)), Triple(13L, "long", Triple(10.5, 5400, 145)), Triple(11L, "easy", Triple(6.0, 3100, 143)),
            Triple(9L, "easy", Triple(6.2, 3150, 143)), Triple(5L, "tempo", Triple(7.0, 3000, 158)), Triple(1L, "easy", Triple(6.5, 3250, 142)),
        ).forEach { (ago, type, v) -> vm.saveRun(Run("r$ago", today.minusDays(ago), type, v.first, v.second, v.third), isNew = false) }
        vm.toast = null
        return vm
    }

    private fun shot(name: String) = rule.onRoot().captureRoboImage("build/shots/$name.png")

    @Test fun everyTabLight() {
        val vm = seeded()
        rule.setContent { AppTheme { App(vm) } }
        shot("today")
        for (t in listOf("PLAN", "LEARN", "PROGRESS", "SETUP")) { rule.onNodeWithText(t).performClick(); rule.waitForIdle(); shot(t.lowercase()) }
        rule.onNodeWithText("PROGRESS").performClick()
        rule.onNodeWithText("Runs").performClick(); rule.waitForIdle(); shot("runs")
        rule.onNodeWithText("Badges").performClick(); rule.waitForIdle(); shot("badges")
        rule.onNodeWithText("PLAN").performClick()
        rule.onNodeWithText("Take week off").performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(listOf(mondayOf(today)), vm.settings.breakWeeks)
        shot("plan-week-off")
    }

    @Test @Config(qualifiers = "w400dp-h2700dp-night-xhdpi") fun todayAndProgressDark() {
        val vm = seeded()
        rule.setContent { AppTheme { App(vm) } }
        shot("today-dark")
        rule.onNodeWithText("LEARN").performClick(); rule.waitForIdle(); shot("learn-dark")
        rule.onNodeWithText("PROGRESS").performClick(); rule.waitForIdle(); shot("progress-dark")
    }

    @Test @Config(qualifiers = "w400dp-h860dp-xhdpi") fun introStoryInMotion() {
        Motion.on = true
        val vm = AppVM(ApplicationProvider.getApplicationContext())
        rule.mainClock.autoAdvance = false
        rule.setContent { AppTheme { App(vm) } }
        for (i in 1..6) {
            for (f in 0 until 16) { rule.mainClock.advanceTimeBy(150); shot("frames/intro-$i-${f.toString().padStart(2, '0')}") }
            shot("intro-$i")
            rule.onNodeWithText(if (i < 6) "Next" else "Build my plan").performClick()
            rule.mainClock.advanceTimeBy(700)
        }
        assertTrue(vm.settings.introSeen)
        Motion.on = false
    }

    @Test @Config(qualifiers = "w400dp-h1500dp-xhdpi") fun todayChangesThroughTheDay() {
        val vm = seeded()
        vm.updateSettings { it.copy(days = (1..7).toList()) }                      // make today a run day
        vm.clock = java.time.LocalTime.of(7, 0)
        rule.setContent { AppTheme { App(vm) } }
        rule.onNodeWithText("TODAY'S RUN").assertExists(); shot("moment-morning")
        vm.clock = java.time.LocalTime.of(18, 30); rule.waitForIdle()
        rule.onNodeWithText("READY WHEN YOU ARE").assertExists(); shot("moment-evening")
        vm.clock = java.time.LocalTime.of(22, 30); rule.waitForIdle()
        rule.onNodeWithText("GETTING LATE").assertExists(); shot("moment-late")
        vm.clock = java.time.LocalTime.of(19, 30)
        vm.saveRun(Run("now", today, "easy", 7.0, 3480, 141, zoneSec = listOf(240, 2400, 720, 120, 0)), isNew = false); rule.waitForIdle()
        rule.onNodeWithText("Kept it easy. That's the work.").assertExists(); shot("moment-done")
    }

    @Test @Config(qualifiers = "w400dp-h1100dp-xhdpi") fun suggestionIsOfferedAndApplied() {
        val vm = AppVM(ApplicationProvider.getApplicationContext())
        vm.updateSettings { it.copy(introSeen = true, onboarded = true, maxHr = 188, planStart = mondayOf(today).minusWeeks(5), remindOn = true, remindMin = 1080) }
        val mon = mondayOf(today)
        for (w in 1L..4L) for ((d, start) in listOf(0L to 1140, 3L to 1155, 5L to 1125))
            vm.saveRun(Run("h$w$d", mon.minusWeeks(w).plusDays(d), "easy", 6.0, 2700, 142, startMin = start), isNew = false)
        vm.toast = null; vm.clock = java.time.LocalTime.of(18, 0)
        rule.setContent { AppTheme { App(vm) } }
        rule.onNodeWithText("Move Tuesday's run to Monday?").assertExists(); shot("suggestion")
        rule.onNodeWithText("Move it").performClick(); rule.waitForIdle()
        assertEquals(listOf(1, 4, 6, 7), vm.settings.days)
        rule.onNodeWithText("Move Tuesday's run to Monday?").assertDoesNotExist()
        shot("suggestion-applied")
    }

    @Test fun lessonEarnsXp() {
        val vm = seeded()
        rule.setContent { AppTheme { App(vm) } }
        rule.onNodeWithText("LEARN").performClick()
        rule.onNodeWithText("Why not hard every day?").performClick(); rule.waitForIdle(); shot("lesson-card")
        rule.onNodeWithText("Next").performClick(); rule.onNodeWithText("Next").performClick(); rule.onNodeWithText("Quick question").performClick()
        rule.onNodeWithText("It is too slow to count").performClick(); rule.waitForIdle()
        rule.onNodeWithText("It tires you without the payoff of a real hard session").performClick(); rule.waitForIdle(); shot("lesson-question")
        val before = vm.engine().game.xp
        rule.onNodeWithText("Finish · +40 XP").performClick(); rule.waitForIdle()
        assertTrue("grey" in vm.settings.lessonsDone)
        assertEquals(before + 40, vm.engine().game.xp)
    }

    @Test fun guideThenFirstRunEarnsReward() {
        val vm = AppVM(ApplicationProvider.getApplicationContext())
        rule.setContent { AppTheme { App(vm) } }
        rule.onNodeWithText("Skip").performClick(); rule.waitForIdle()
        shot("guide-age")
        rule.onNodeWithText("Next").performClick()                                   // age 30
        rule.waitForIdle(); shot("guide-rest")
        rule.onNodeWithText("Next").performClick()                                   // resting 60
        rule.onNodeWithText("Yes, I know a pace and heart rate").performClick()
        rule.waitForIdle(); shot("guide-now")
        rule.onNodeWithText("Next").performScrollTo().performClick()                 // 8:00 at 150 bpm
        rule.waitForIdle()
        rule.onNodeWithText("7:25 /km · Ambitious").performClick()
        rule.waitForIdle(); shot("guide-target")
        rule.onNodeWithText("Next").performClick()
        rule.waitForIdle(); shot("guide-level")
        rule.onNodeWithText("Just starting").performClick()
        rule.waitForIdle(); shot("guide-days")
        rule.onNodeWithText("Next").performClick()
        rule.waitForIdle(); shot("guide-long")
        rule.onNodeWithText("Sunday").performClick()
        rule.waitForIdle(); shot("guide-commit")
        rule.onNodeWithText("12 weeks · A new habit").performClick()
        rule.waitForIdle(); shot("guide-remind")
        rule.onNodeWithText("Not now").performClick()
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

        rule.onNodeWithText("PROGRESS").performClick()
        rule.onNodeWithText("Runs").performClick()
        rule.onNodeWithText("Add run").performClick()
        rule.onNodeWithText("Distance (km)").performTextInput("6")
        rule.onNodeWithText("Time").performTextInput("54:00")
        rule.onNodeWithText("Average HR").performTextInput("138")
        rule.waitForIdle(); shot("run-form")
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
}
