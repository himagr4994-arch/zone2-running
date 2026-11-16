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

    private fun easyWeek(weeksAgo: Long, n: Int = 3) = (0 until n).map {
        Run("w${weeksAgo}_$it", today.minusWeeks(weeksAgo).plusDays(it * 2L), "easy", 6.0, 2700, 142)
    }

    @Test fun restPassCoversOneMissedWeek() {
        val e = Engine(s, easyWeek(3) + easyWeek(1), today)
        assertEquals(listOf(WeekState.HIT, WeekState.FROZEN, WeekState.HIT, WeekState.OPEN), e.weeks.map { it.state })
        assertEquals(2, e.game.streak); assertEquals(0, e.game.passes)
    }

    @Test fun secondMissedWeekBreaksTheStreak() {
        val e = Engine(s, easyWeek(4) + easyWeek(1), today)
        assertEquals(listOf(WeekState.HIT, WeekState.FROZEN, WeekState.MISS, WeekState.HIT, WeekState.OPEN), e.weeks.map { it.state })
        assertEquals(1, e.game.streak)
    }

    @Test fun weekOffIsNeutralAndKeepsThePass() {
        val e = Engine(s.copy(breakWeeks = listOf(today.minusWeeks(2))), easyWeek(3) + easyWeek(1), today)
        assertEquals(WeekState.BREAK, e.weeks[1].state)
        assertEquals(2, e.game.streak); assertEquals(1, e.game.passes)
    }

    @Test fun planOnlyAdvancesOnCompletedWeeks() {
        val e = Engine(s.copy(planStart = today.minusWeeks(3)), easyWeek(3) + easyWeek(1), today)
        assertEquals(2, e.planIndex(today))          // three weeks passed, two completed
        assertEquals(3, e.planIndex(today.plusWeeks(1)))
    }

    @Test fun questsAndCleanSweepBadge() {
        val e = Engine(s, easyWeek(1), today)
        assertTrue(e.quests(today.minusWeeks(1)).all { it.done })
        assertTrue(e.game.badges.first { it.id == "quests" }.got)
        assertEquals(3, e.game.questsDone)
    }

    @Test fun zoneSecondsDriveTheEasyShare() {
        val r = Run("z", today, "easy", 6.0, 3000, 151, zoneSec = listOf(300, 1500, 900, 300, 0))
        val sp = Engine(s, listOf(r), today).span(today, today.plusDays(1))
        assertEquals(30.0, sp.easy, .01); assertEquals(20.0, sp.hard, .01)
    }

    @Test fun reminderNamesTodaysSessionOrStaysQuiet() {
        val tue = today.plusDays(1)
        val e = Engine(s.copy(planStart = today), listOf(tenK), tue)
        assertTrue(e.reminder()!!.first.startsWith("Today: Easy run"))
        assertNull(Engine(s.copy(planStart = today, breakWeeks = listOf(today)), listOf(tenK), tue).reminder())
        assertNull(Engine(s.copy(planStart = today), listOf(tenK), today.plusDays(2)).reminder())   // Wednesday: rest day, week still open
    }

    @Test fun lessonsAreWellFormed() {
        assertEquals(LESSON_COUNT, LESSONS.size)
        assertTrue(LESSONS.all { it.answer in it.options.indices && it.cards.size == 3 })
        assertEquals(LESSONS.size, LESSONS.map { it.id }.toSet().size)
    }
}
