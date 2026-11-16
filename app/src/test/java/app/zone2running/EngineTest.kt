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

    @Test fun firstRunEarnsItsBadges() {
        val g = Engine(s, listOf(tenK), today).game
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

    private fun at(h: Int) = java.time.LocalTime.of(h, 0)

    @Test fun leadFollowsTheDay() {
        val plan = s.copy(planStart = today.minusWeeks(1))
        val tue = today.plusDays(1); val wed = today.plusDays(2)
        val history = easyWeek(1)
        assertEquals("first", Engine(s, emptyList(), today).lead(at(8)).kind)
        assertEquals("Today's run", Engine(plan, history, tue).lead(at(7)).eyebrow)
        assertEquals("Later today", Engine(plan, history, tue).lead(at(13)).eyebrow)
        assertEquals("Ready when you are", Engine(plan, history, tue).lead(at(18)).eyebrow)
        assertEquals("late", Engine(plan, history, tue).lead(at(22)).kind)
        assertEquals("rest", Engine(plan, history, wed).lead(at(9)).kind)
        assertTrue(Engine(plan, history, wed).lead(at(20)).title.startsWith("Tomorrow: "))
        assertEquals("review", Engine(plan, history, today).lead(at(8)).kind)          // Monday morning, rest day
        val ran = history + Run("t", tue, "easy", 6.0, 2700, 142)
        assertEquals("done", Engine(plan, ran, tue).lead(at(19)).kind)
        assertEquals("break", Engine(plan.copy(breakWeeks = listOf(today)), history, tue).lead(at(8)).kind)
        // Sunday with three runs still needed and one day left: the streak comes first.
        assertEquals("risk", Engine(plan, history, today.plusDays(6)).lead(at(8)).kind)
    }

    @Test fun paceChangeAndConfidence() {
        val hardOnly = Engine(s, listOf(tenK), today)
        assertNull(hardOnly.paceChange()); assertEquals(0, hardOnly.confidence()!!.level)
        val runs = listOf(35L to 3000, 30L to 2990, 12L to 2900, 5L to 2860, 1L to 2850).map { (ago, sec) -> Run("c$ago", today.minusDays(ago), "easy", 6.0, sec, 143) }
        val e = Engine(s, runs, today)
        assertEquals("4 weeks ago", e.paceChange()!!.since); assertTrue(e.paceChange()!!.delta > 10)
        assertEquals(2, e.confidence()!!.level)
    }

    /** Four weeks of runs on Mon / Thu / Sat at about 7 pm, against a plan of Tue / Thu / Sat / Sun. */
    private fun mondayRunner(): List<Run> = (1L..4L).flatMap { w ->
        listOf(0L to 1140, 3L to 1155, 5L to 1125).map { (d, start) -> Run("h$w$d", today.minusWeeks(w).plusDays(d), "easy", 6.0, 2700, 142, startMin = start) }
    }

    @Test fun learnsTheUsualRunTime() {
        assertNull(Engine(s, listOf(tenK), today).usualRunMin)                       // no timed runs yet
        val e = Engine(s, mondayRunner(), today)
        assertEquals(1140, e.usualRunMin)                                           // 7:00 pm
        val scattered = listOf(360, 780, 1260, 540).mapIndexed { i, m -> Run("s$i", today.minusDays(i + 1L), "easy", 5.0, 2400, 142, startMin = m) }
        assertNull(Engine(s, scattered, today).usualRunMin)                         // no routine to learn from
    }

    @Test fun readyWindowFollowsTheLearnedTime() {
        val early = (1L..4L).map { Run("e$it", today.minusDays(it * 2), "easy", 5.0, 2400, 142, startMin = 375) }   // 6:15 am
        val plan = s.copy(planStart = today.minusWeeks(2), days = (1..7).toList())
        val e = Engine(plan, early, today)
        assertEquals("Ready when you are", e.lead(java.time.LocalTime.of(5, 30)).eyebrow)
        assertEquals("Later than usual", e.lead(java.time.LocalTime.of(9, 0)).eyebrow)
        assertTrue(e.lead(java.time.LocalTime.of(5, 30)).body.startsWith("You usually run around 6:15 am."))
    }

    @Test fun offersToMoveThePlanToTheDaysActuallyRun() {
        val plan = s.copy(planStart = today.minusWeeks(5), remindOn = true, remindMin = 1080)
        val sg = Engine(plan, mondayRunner(), today).suggestions()
        assertEquals(1, sg.size)
        assertEquals(MoveDay(2, 1), sg[0].act)                                      // Tuesday never run, Monday always
        assertEquals("Move Tuesday's run to Monday?", sg[0].title)
        // Said "not now" last week: it stays away.
        assertTrue(Engine(plan.copy(snoozed = mapOf(sg[0].key to today.minusDays(7))), mondayRunner(), today).suggestions().isEmpty())
        // A plan younger than three weeks is left alone.
        assertTrue(Engine(plan.copy(planStart = today.minusWeeks(1)), mondayRunner(), today).suggestions().isEmpty())
    }

    @Test fun offersAReminderAnHourBeforeTheUsualTime() {
        val base = s.copy(planStart = today.minusWeeks(5), days = listOf(1, 4, 6))
        assertEquals(Remind(1080), Engine(base, mondayRunner(), today).suggestions().single().act)
        assertEquals("Move your reminder to 6 pm?", Engine(base.copy(remindOn = true, remindMin = 390), mondayRunner(), today).suggestions().single().title)
        assertTrue(Engine(base.copy(remindOn = true, remindMin = 1080), mondayRunner(), today).suggestions().isEmpty())
        assertEquals("6:30 am", fmtClock(390)); assertEquals("12 pm", fmtClock(720)); assertEquals("12:15 am", fmtClock(15))
    }

    @Test fun milesChangeOnlyWhatIsShown() {
        try {
            U.miles = true
            assertEquals("11:16", fmtPace(420.0)); assertEquals("6.21 mi", U.dist(10.0)); assertEquals(24, U.s(15)); assertEquals(420, U.paceToKm(676))
            assertEquals(10.0, U.toKm(6.2137), .01)
        } finally { U.miles = false }
        assertEquals("7:00", fmtPace(420.0)); assertEquals("10.00 km", U.dist(10.0))
    }

    @Test fun raceTapersThePlanAndLandsOnTheDay() {
        val race = today.plusDays(13)                                   // Sunday of next week
        val e = Engine(s.copy(planStart = today, raceName = "City Half", raceDate = race, raceKm = 21.1), listOf(tenK), today)
        val taper = e.weekPlan(today)
        assertEquals("Taper", taper.phase); assertTrue(taper.ses[7]!!.min < 70)
        val raceWeek = e.weekPlan(today.plusWeeks(1))
        assertEquals("Race week", raceWeek.phase)
        assertEquals("race", raceWeek.ses[7]!!.type); assertEquals("City Half", raceWeek.ses[7]!!.title)
        assertTrue(raceWeek.ses.filterKeys { it < 7 }.values.all { it.kind == "easy" })
        assertEquals(13, e.raceDaysLeft)
        val projected = e.racePrediction()!!                             // Riegel from the 10.26 km in 1:11:37
        assertTrue(projected in 9100..9350)
        assertEquals("Build 1", e.weekPlan(today.plusWeeks(2)).phase.let { if (it.startsWith("Build") || it.startsWith("Recovery")) "Build 1" else it })
    }

    @Test fun sessionsCanBeSwappedMovedAndSkipped() {
        val tue = today.plusDays(1); val wed = today.plusDays(2); val thu = today.plusDays(3)
        val e = Engine(s.copy(planStart = today, overrides = mapOf(tue to "tempo", thu to "skip", wed to "4x4")), listOf(tenK), today)
        val p = e.weekPlan(today)
        assertEquals("tempo", p.ses[2]!!.type); assertEquals("4x4", p.ses[3]!!.kind); assertNull(p.ses[4])
        assertEquals(listOf(2, 3, 6, 7), p.ses.keys.toList())
        assertEquals(e.weekPlan(today.plusWeeks(1)).ses.keys.toList(), listOf(2, 4, 6, 7))      // other weeks are untouched
    }

    @Test fun guidedStepsAddUp() {
        val e = Engine(s, listOf(tenK), today)
        val p = e.weekPlan(today)
        val intervals = e.steps(p.ses[4]!!)
        assertEquals(600 + 4 * (240 + 180) + 600, intervals.sumOf { it.sec })
        assertEquals(4, intervals.count { it.kind == "hard" })
        val easy = e.steps(p.ses[2]!!)
        assertEquals(40 * 60, easy.sumOf { it.sec }); assertTrue(easy.all { it.zHi <= 2 })
        val walk = e.steps(Session("easy", "Easy run-walk", 40, "", "runwalk"))
        assertEquals(16, walk.size); assertEquals("Walk", walk[1].label)
        assertEquals(1800, e.steps(p.copy().let { e.weekPlan(today.plusWeeks(3)).ses.values.first { it.type == "test" } }).last().sec)
    }

    @Test fun splitsComeFromFinePiecesOnly() {
        val pieces = (0 until 11).map { Triple(it * 100L, it * 100L + 100, 250.0) }          // 2.75 km at 400 s per km
        val beats = (0 until 1100 step 10).map { it.toLong() to if (it < 400) 140 else 150 }
        val sp = splitsFrom(pieces, beats)
        assertEquals(listOf(400, 400), sp.map { it.sec }); assertEquals(140, sp[0].hr); assertEquals(150, sp[1].hr)
        assertTrue(splitsFrom(listOf(Triple(0L, 3000L, 6000.0)), beats).isEmpty())            // one big lump can't be split honestly
    }

    @Test fun heatSlowsTheExpectationNotTheEstimate() {
        assertEquals(.75, heatPct(20.0, 10.0), .001); assertEquals(7.0, heatPct(32.0, 24.0), .001); assertEquals(0.0, heatPct(10.0, 5.0), .001)
        val cool = Run("c", today, "easy", 6.0, 3000, 143); val hot = cool.copy(id = "h", heatPct = 7.0)
        val e = Engine(s, listOf(cool), today)
        assertEquals(e.est(cool)!! / 1.07, e.est(hot)!!, .01)
        val lead = Engine(s.copy(planStart = today.minusWeeks(1)), easyWeek(1), today.plusDays(1), Wx { _, _ -> 33.0 to 25.0 }).lead(at(17))
        assertTrue(lead.body.contains("% slower")); assertTrue(lead.heatPct >= 7.0)
        assertEquals(0.0, Engine(s.copy(planStart = today.minusWeeks(1)), easyWeek(1), today.plusDays(1)).lead(at(17)).heatPct, .0)
    }

    private val zb = listOf(124, 137, 150, 162, 175)

    @Test fun trackerFollowsStepsAndCuesAboveTheCap() {
        val t = RunTracker(listOf(Step("Warm up", 60, 1, 2, "warm"), Step("Hard", 30, 4, 4, "hard")), zb)
        val events = ArrayList<LiveEvent>()
        repeat(60) { events += t.tick(if (it < 30) 140 else 155) }
        assertEquals(1, t.stepIndex)
        assertTrue(events.any { it.kind == "step" && it.text.startsWith("Hard, 30 seconds") })
        assertEquals(1, events.count { it.kind == "cap" })                                    // one cue after 20 s above 150, not thirty
        repeat(30) { events += t.tick(170) }
        assertTrue(events.last().kind == "done")
        assertEquals(1, events.count { it.kind == "cap" })                                    // no cap cue during the hard block
        assertEquals(90, t.elapsed); assertEquals(30, t.zoneSec[1]); assertEquals(30, t.zoneSec[2]); assertEquals(30, t.zoneSec[3])
    }

    @Test fun trackerMeasuresDistanceAndSplits() {
        val t = RunTracker(emptyList(), zb)
        val step = 3.0 / 111_320.0                                                           // 3 m of latitude
        var km: LiveEvent? = null
        for (i in 0..400) { t.tick(142); t.fix(21.25 + i * step, 81.63, 6f, i * 1000L)?.let { km = it } }
        assertEquals(1200.0, t.meters, 6.0)
        assertEquals(1, t.splits.size); assertTrue(t.splits[0].sec in 332..336); assertEquals(142, t.splits[0].hr)
        assertTrue(km!!.text.startsWith("Kilometre 1."))
        assertEquals(333.0, t.paceNow()!!, 4.0)
        val before = t.meters
        t.fix(21.30, 81.63, 6f, 402_000L)                                                    // a 5 km jump in one second is thrown away
        t.fix(21.30 + step, 81.63, 80f, 403_000L)                                            // and so is a fix with poor accuracy
        assertEquals(before, t.meters, .001)
        val r = t.toRun(today, "easy", 1140)
        assertEquals(1.2, r.distKm, .01); assertEquals("live", r.source); assertEquals(1, r.splits.size); assertEquals(1140, r.startMin)
    }

    @Test fun versionsCompareNumberByNumber() {
        assertTrue(Online.newer("0.10.0", "0.9.3")); assertTrue(Online.newer("1.0", "0.9.9")); assertTrue(!Online.newer("0.5.0", "0.5.0")); assertTrue(!Online.newer("0.4.9", "0.5.0"))
    }
}
