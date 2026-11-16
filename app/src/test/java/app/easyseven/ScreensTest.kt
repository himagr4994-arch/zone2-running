package app.easyseven

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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Drives the real screens on the JVM and saves a picture of each one. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h2500dp-xhdpi")
class ScreensTest {
    @get:Rule val rule = createComposeRule()
    private val today = LocalDate.now()

    private fun seeded(): AppVM {
        val vm = AppVM(ApplicationProvider.getApplicationContext())
        vm.updateSettings { it.copy(onboarded = true, maxHr = 188, planStart = mondayOf(today).minusWeeks(5)) }
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
        rule.setContent { EasyTheme { App(vm) } }
        shot("today")
        for (t in listOf("PLAN", "LOG", "PROGRESS", "SETUP")) { rule.onNodeWithText(t).performClick(); rule.waitForIdle(); shot(t.lowercase()) }
    }

    @Test @Config(qualifiers = "w400dp-h2500dp-night-xhdpi") fun todayAndProgressDark() {
        val vm = seeded()
        rule.setContent { EasyTheme { App(vm) } }
        shot("today-dark")
        rule.onNodeWithText("PROGRESS").performClick(); rule.waitForIdle(); shot("progress-dark")
    }

    @Test fun guideThenFirstRunEarnsReward() {
        val vm = AppVM(ApplicationProvider.getApplicationContext())
        rule.setContent { EasyTheme { App(vm) } }
        shot("guide-1")
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("Pace (min/km)").performTextInput("8:10")
        rule.onNodeWithText("Heart rate").performTextInput("152")
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("Age").performTextInput("40")
        rule.waitForIdle(); shot("guide-3")
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("Just starting").performClick()
        rule.waitForIdle(); shot("guide-4")
        rule.onNodeWithText("Next").performClick()
        rule.waitForIdle(); shot("guide-5")
        rule.onNodeWithText("Start training").performClick()
        rule.waitForIdle()
        assertTrue(vm.settings.onboarded)
        assertEquals(180, vm.settings.maxHr)                 // 208 − 0.7 × 40
        assertEquals(490, vm.settings.basePace)
        shot("today-new-user")

        rule.onNodeWithText("LOG").performClick()
        rule.onNodeWithText("Add run").performClick()
        rule.onNodeWithText("Distance (km)").performTextInput("6")
        rule.onNodeWithText("Time").performTextInput("54:00")
        rule.onNodeWithText("Average HR").performTextInput("138")
        rule.waitForIdle(); shot("run-form")
        rule.onNodeWithText("Save run").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(1, vm.data.runs.size)
        shot("reward")
        rule.onNodeWithText("Done").performClick()
        rule.waitForIdle()
        // Reloading from disk gives back the same run and settings.
        val again = AppVM(ApplicationProvider.getApplicationContext())
        assertEquals(vm.data.runs, again.data.runs)
        assertEquals(vm.settings, again.settings)
    }
}
