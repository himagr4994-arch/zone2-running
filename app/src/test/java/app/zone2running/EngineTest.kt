package app.zone2running

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EngineTest {
    private val today = LocalDate.of(2026, 11, 16) // a Monday
    private val s = Settings(maxHr = 188, restHr = 60, onboarded = true)
    private val tenK = Run("a", today, "hard", 10.26, 4297, 162, 178)

    @Test fun zonesFromHeartRateReserve() {
        val e = Engine(s, emptyList(), today)
        assertEquals(listOf(124, 137, 150, 162, 175), e.zb)
        assertEquals(144, e.refHr)
        assertEquals(4, e.zoneOf(162)); assertEquals(2, e.zoneOf(140)); assertEquals(1, e.zoneOf(100))
    }

    @Test fun hardRunConvertsToZone2Pace() {
        val e = Engine(s, listOf(tenK), today)
        assertEquals("6:59", fmtPace(paceOf(tenK)))
        assertEquals("8:29", fmtPace(e.current!!))
    }

    @Test fun intervalsAreLeftOutOfEstimate() {
        assertNull(Engine(s, listOf(tenK.copy(type = "intervals")), today).current)
    }

    @Test fun weekPlanDefaults() {
        val p = Engine(s, listOf(tenK), today).weekPlan(today)
        assertEquals(setOf(2, 4, 6, 7), p.ses.keys)
        assertEquals("long", p.ses[7]!!.type); assertEquals(70, p.ses[7]!!.min)
        assertEquals("intervals", p.ses[4]!!.type)
        assertEquals(40, p.ses[2]!!.min)
        assertEquals(0, p.wi)
    }

    @Test fun fourthWeekEasesOffAndTests() {
        val p = Engine(s, listOf(tenK), today).weekPlan(today.plusWeeks(3))
        assertEquals(3, p.pos)
        assertTrue(p.ses.values.any { it.type == "test" })
        assertEquals(60, p.ses[7]!!.min)
    }

    @Test fun xpLevelAndBadges() {
        val g = Engine(s, listOf(tenK), today).game
        assertEquals(72, g.xp)           // 71.6 hard minutes, Monday is not a planned day
        assertEquals(1, g.level)
        assertEquals(setOf("first", "l10"), g.badges.filter { it.got }.map { it.id }.toSet())
    }

    @Test fun streakSurvivesAnUnfinishedCurrentWeek() {
        val runs = (0 until 3).map { Run("p$it", today.minusDays(7L - it * 2), "easy", 5.0, 2700, 142) } + tenK
        val g = Engine(s, runs, today).game
        assertEquals(1, g.streak); assertEquals(1, g.weeksHit)
    }

    @Test fun trendNeedsFourRunsOverThreeWeeks() {
        assertNull(Engine(s, listOf(tenK), today).trend)
        val runs = listOf(28L to 3000, 20L to 2950, 10L to 2900, 1L to 2850).map { (ago, sec) -> Run("t$ago", today.minusDays(ago), "easy", 6.0, sec, 144) }
        val t = Engine(s, runs, today).trend
        assertNotNull(t); assertTrue(t!!.b < 0)
    }

    @Test fun durations() {
        assertEquals(4297, parseDur("1:11:37")); assertEquals(2730, parseDur("45:30")); assertEquals(2700, parseDur("45")); assertEquals(0, parseDur("abc"))
        assertEquals("1:11:37", fmtDur(4297))
    }
}
