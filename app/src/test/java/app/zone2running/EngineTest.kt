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
        assertTrue(e.reminder()!!.first.startsWith("Easy run today"))
        assertNull(Engine(s.copy(planStart = today, breakWeeks = listOf(today)), listOf(tenK), tue).reminder())
        assertNull(Engine(s.copy(planStart = today), listOf(tenK, Run("tu", tue, "easy", 5.0, 2400, 140)), today.plusDays(2)).reminder())   // Wednesday: rest day, week still open
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
        val plan = s.copy(planStart = today.minusWeeks(5), remindOn = true, remindMin = 1080, snoozed = mapOf("talk" to today))
        val sg = Engine(plan, mondayRunner(), today).suggestions()
        assertEquals(1, sg.size)
        assertEquals(MoveDay(2, 1), sg[0].act)                                      // Tuesday never run, Monday always
        assertEquals("Move Tuesday's run to Monday?", sg[0].title)
        // Said "not now" last week: it stays away.
        assertTrue(Engine(plan.copy(snoozed = plan.snoozed + (sg[0].key to today.minusDays(7))), mondayRunner(), today).suggestions().isEmpty())
        // A plan younger than three weeks is left alone.
        assertTrue(Engine(plan.copy(planStart = today.minusWeeks(1)), mondayRunner(), today).suggestions().isEmpty())
    }

    @Test fun offersAReminderAnHourBeforeTheUsualTime() {
        val base = s.copy(planStart = today.minusWeeks(5), days = listOf(1, 4, 6), snoozed = mapOf("talk" to today))
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
        assertTrue(raceWeek.ses.filterKeys { it < 7 }.values.all { it.kind == "easy" || it.kind == "tune" })
        assertEquals("tune", raceWeek.ses[4]!!.kind)                     // intensity stays, shortened; volume drops
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

    // ---- 0.7: the engine changes that came out of the research review ----

    private fun easy(id: String, ago: Long, min: Int, hr: Int = 143, km: Double = min / 7.5) = Run(id, today.minusDays(ago), "easy", km, min * 60, hr)

    @Test fun noRunOutgrowsTheLongestOfTheLastThirtyDaysByMuch() {
        val runs = listOf(easy("a", 10, 40), easy("b", 6, 35), easy("c", 3, 30))
        val e = Engine(s.copy(planStart = today), runs, today)
        assertEquals(45, e.runCap(today.plusDays(6)))                    // 40 min + 10% = 44, but never less than 5 more
        val long = e.weekPlan(today).ses[7]!!
        assertEquals(45, long.min); assertTrue(long.detail.contains("longest run of the last 30 days"))
        assertEquals(40, e.weekPlan(today).ses[2]!!.min)                 // an ordinary easy run is under the cap and untouched
        assertNull(e.runCap(today.plusDays(20)))                         // too far ahead to know
        assertNull(Engine(s, emptyList(), today).runCap(today))          // nothing to go on yet: setup decides
        assertEquals(70, Engine(s.copy(planStart = today), listOf(easy("d", 5, 65)), today).weekPlan(today).ses[7]!!.min)
    }

    @Test fun aMonthOffRestartsShort() {
        val e = Engine(s.copy(planStart = today), listOf(easy("a", 50, 70)), today)
        assertEquals(-1, e.runCap(today.plusDays(1)))
        val p = e.weekPlan(today)
        assertEquals(40, p.ses[7]!!.min); assertEquals(25, p.ses[2]!!.min)
        assertEquals("Easing back in", p.phase); assertTrue(p.note.contains("much shorter"))
    }

    @Test fun aLongJumpIsFlaggedButAWeeklyJumpIsNot() {
        val jump = Engine(s, listOf(easy("a", 9, 40, km = 6.0), easy("b", 1, 60, km = 9.0)), today).coach()
        assertTrue(jump.any { it.tone == "warn" && it.text.contains("50% longer") })
        val busy = Engine(s, listOf(easy("a", 8, 40, km = 6.0), easy("b", 2, 40, km = 6.0), easy("c", 1, 40, km = 6.0), easy("d", 0, 40, km = 6.0)), today).coach()
        assertTrue(busy.none { it.text.contains("above last week") || it.text.contains("longer than anything") })
    }

    @Test fun oneOddRunDoesNotMoveTheEstimate() {
        val steadyRuns = (1L..6L).map { Run("s$it", today.minusDays(it * 3), "easy", 6.0, 2880, 144) }        // 8:00/km at the reference
        val base = Engine(s, steadyRuns, today)
        assertEquals("8:00", fmtPace(base.current!!))
        val withOutlier = Engine(s, steadyRuns + Run("x", today, "easy", 6.0, 3240, 144), today)                // a 9:00/km day
        assertEquals("8:00", fmtPace(withOutlier.current!!))
        assertEquals(2, withOutlier.confidence()!!.level)
    }

    @Test fun estimateUsesTheSteadyMiddleOfTheRun() {
        // A slow first kilometre with a low heart rate, then five steady ones.
        val sp = listOf(Split(540, 120)) + List(5) { Split(480, 144) }
        val r = Run("a", today, "easy", 6.0, sp.sumOf { it.sec }, 140, splits = sp)
        assertEquals(480.0 to 144, steady(r))
        val e = Engine(s, listOf(r), today)
        assertEquals("8:00", fmtPace(e.est(r)!!)); assertTrue(e.estOf(r)!!.clean)
        assertEquals(paceOf(tenK) to 162, steady(tenK))                  // no splits: the whole run
        assertTrue(!e.estOf(tenK)!!.clean)                               // a hard run only hints at Zone 2 pace
    }

    @Test fun aSmallChangeIsCalledWobbleNotProgress() {
        fun block(from: Long, secs: List<Int>) = secs.mapIndexed { i, sec -> Run("r$from$i", today.minusDays(from - i * 3L), "easy", 6.0, sec, 144) }
        val before = block(45, listOf(2880, 2910, 2850, 2940, 2820))                     // around 8:00, spread about ±10 s/km
        val wobble = Engine(s, before + block(14, listOf(2870, 2850, 2900, 2860)), today).paceChange()!!
        assertEquals("4 weeks ago", wobble.since); assertTrue(!wobble.real)
        val faster = Engine(s, before + block(14, listOf(2760, 2750, 2770, 2740)), today).paceChange()!!
        assertTrue(faster.real); assertTrue(faster.delta > 10)
    }

    @Test fun talkTestMovesTheTopOfZone2() {
        val e = Engine(s.copy(talkHr = 156), emptyList(), today)
        assertEquals(157, e.zb[2]); assertEquals(2, e.zoneOf(156)); assertEquals(3, e.zoneOf(157))
        assertTrue(e.zb.zipWithNext().all { (a, b) -> b > a })
        val low = Engine(s.copy(talkHr = 138), emptyList(), today)
        assertEquals(139, low.zb[2]); assertTrue(low.zb[1] <= 129)
        assertEquals(listOf(135, 148, 160, 172), Engine(s.copy(talkHr = 156, zoneMethod = "custom"), emptyList(), today).zb.drop(1))   // a watch's zones are left alone
    }

    @Test fun taperLengthFollowsTheRace() {
        fun phases(km: Double) = Engine(s.copy(planStart = today.minusWeeks(8), raceDate = today.plusDays(27), raceKm = km), listOf(tenK), today)
            .let { e -> (0L..3L).map { e.weekPlan(today.plusWeeks(it)).phase } }
        assertEquals(listOf(false, false, false), phases(5.0).take(3).map { it == "Taper" }); assertEquals("Race week", phases(5.0)[3])
        assertEquals(listOf(false, false, true), phases(21.1).take(3).map { it == "Taper" })
        assertEquals(listOf(false, true, true), phases(42.2).take(3).map { it == "Taper" })
        val e = Engine(s.copy(planStart = today, raceDate = today.plusDays(13), raceKm = 42.2), emptyList(), today)
        assertEquals("tune", e.weekPlan(today).ses[4]!!.kind)            // the hard day stays, shortened
        assertEquals(4, e.weekPlan(today).ses.size)                      // and so does the number of runs
        assertEquals(3, e.taperWeeks)
    }

    @Test fun raceForecastIsARangeAndHonestAboutTheMarathon() {
        val half = Engine(s.copy(raceDate = today.plusDays(40), raceKm = 21.1), listOf(tenK), today).raceCast()!!
        assertTrue(half.lo < half.sec && half.sec < half.hi); assertTrue(half.sec in 9100..9350); assertEquals("", half.warn)
        val mara = Engine(s.copy(raceDate = today.plusDays(40), raceKm = 42.2), listOf(tenK), today).raceCast()!!
        val riegel = 4297 * Math.pow(42.2 / 10.26, 1.06)
        assertTrue(mara.sec > riegel + 600)                              // plain Riegel is at least ten minutes too fast for most
        assertTrue(mara.warn.contains("optimistic")); assertTrue(mara.hi > mara.sec * 1.06)
        // Intervals never feed it, and a fast 5 km inside a longer run does.
        assertNull(Engine(s.copy(raceKm = 10.0, raceDate = today.plusDays(40)), listOf(tenK.copy(type = "intervals")), today).raceCast())
        val sp = List(3) { Split(480, 140) } + List(5) { Split(330, 170) } + List(2) { Split(480, 140) }
        val prog = Run("p", today.minusDays(3), "easy", 10.0, sp.sumOf { it.sec }, 152, splits = sp)
        val c = Engine(s.copy(raceKm = 5.0, raceDate = today.plusDays(40)), listOf(prog), today).raceCast()!!
        assertEquals(1650, c.sec); assertTrue(c.basis.startsWith("your best 5 km"))
    }

    @Test fun hotEasyDaysGetSlowDownFirstThenRunWalk() {
        fun hot(n: Int, cool: Int = 0) = (1..n).map { Run("h$it", today.minusDays(it * 2L), "easy", 5.0, 2400, if (it <= cool) 140 else 156) }
        val one = Engine(s.copy(planStart = today), hot(3), today)
        assertEquals(1, one.easyOver)
        assertEquals("easy", one.weekPlan(today).ses[2]!!.kind); assertTrue(one.weekPlan(today).ses[2]!!.detail.contains("slow down first"))
        val two = Engine(s.copy(planStart = today), hot(5), today)
        assertEquals(2, two.easyOver); assertEquals("runwalk", two.weekPlan(today).ses[2]!!.kind)
        assertTrue(two.weekPlan(today).ses[2]!!.detail.contains("not a step back"))
        assertEquals(0, Engine(s, hot(5, cool = 3), today).easyOver)     // the latest runs are back under the ceiling
        // The same runs on hot days prove nothing about pacing.
        assertEquals(0, Engine(s, hot(5).map { it.copy(heatPct = 4.0) }, today).easyOver)
        // Zone time beats the average: 38 of 40 minutes easy is fine even if the average sits on the line.
        assertEquals(0, Engine(s, hot(5).map { it.copy(avgHr = 150, zoneSec = listOf(300, 1980, 120, 0, 0)) }, today).easyOver)
    }

    @Test fun beginnersStartWithRunWalkAndGraduate() {
        val b = s.copy(runWalk = true, planStart = today, snoozed = mapOf("talk" to today))
        assertEquals("runwalk", Engine(b, emptyList(), today).weekPlan(today).ses[2]!!.kind)
        assertEquals("long", Engine(b, emptyList(), today).weekPlan(today).ses[7]!!.kind)
        val ok = (1L..3L).map { easy("g$it", it * 2, 25, 140) }
        assertEquals(RunWalk(false), Engine(b, ok, today).suggestions().single { it.key == "runwalk" }.act)
    }

    @Test fun screeningChangesThePlanNotJustTheWording() {
        assertTrue(Engine(s.copy(caution = true), emptyList(), today).weekPlan(today).ses.values.none { it.type in setOf("intervals", "tempo") })
        val meds = Engine(s.copy(hrMeds = true), emptyList(), today).weekPlan(today)
        assertTrue(meds.ses[2]!!.detail.contains("full sentences")); assertTrue(meds.ses.values.none { it.detail.contains("bpm") })
        // Every heart-rate target carries an effort cue.
        val plain = Engine(s, emptyList(), today)
        (0L..2L).flatMap { plain.weekPlan(today.plusWeeks(it)).ses.values }.filter { it.kind != "test" }.forEach { x ->
            assertTrue(x.title, listOf("sentence", "words at a time").any { x.detail.contains(it) })
        }
    }

    @Test fun effortRatingStandsInWhenThereIsNoHeartRate() {
        val e = Engine(s, emptyList(), today)
        assertTrue(e.isEasy(Run("a", today, "tempo", 5.0, 1800, feel = 2))); assertTrue(!e.isEasy(Run("b", today, "easy", 5.0, 1800, feel = 5)))
        val tired = (1L..3L).map { easy("t$it", it * 2, 40, 140).copy(feel = 4) }
        assertTrue(Engine(s, tired, today).coach().any { it.text.contains("felt hard") })
    }

    @Test fun offersTheRealMaxAndTheTalkTest() {
        val runs = listOf(easy("a", 9, 40), easy("b", 6, 40), tenK.copy(id = "h1", date = today.minusDays(4), maxHr = 195), tenK.copy(id = "h2", date = today.minusDays(2), maxHr = 193))
        val sg = Engine(s, runs, today).suggestions()
        assertEquals(SetMax(193), sg.single { it.act is SetMax }.act)    // reached twice; a single spike is not believed
        assertEquals(TalkTest, sg.single { it.key == "talk" }.act)
        val done = s.copy(overrides = mapOf(today.minusDays(6) to "talk"))
        assertEquals(EnterTalk, Engine(done, runs, today).suggestions().first().act)
        assertTrue(Engine(done.copy(talkHr = 150), runs, today).suggestions().none { it.act == EnterTalk || it.act == TalkTest })
        assertEquals(35 * 60, Engine(s, emptyList(), today).let { e -> e.steps(e.weekPlan(today).ses[2]!!.copy(kind = "talk")).sumOf { it.sec } } + 120)
    }

    // ---- the run debrief ----

    /** A heart-rate trace on the 10-second grid, built from (seconds, start bpm, end bpm) pieces. */
    private fun trace(vararg parts: Triple<Int, Int, Int>) = parts.flatMap { (sec, a, b) -> List(sec / TRACE_STEP) { i -> a + (b - a) * i / (sec / TRACE_STEP) } }

    @Test fun samplesBecomeAnEvenTraceAndGapsStayEmpty() {
        val t = traceFrom(listOf(0L to 100, 60L to 130, 120L to 130, 500L to 150, 520L to 152, 540L to 150), 540)
        assertEquals(55, t.size); assertEquals(100, t[0]); assertEquals(115, t[3]); assertEquals(130, t[12])
        assertEquals(0, t[30])                                           // five silent minutes are not invented
        assertEquals(150, t[50]); assertTrue(traceFrom(listOf(0L to 100), 600).isEmpty())
    }

    @Test fun runWalkDebriefReadsEveryCycle() {
        val e = Engine(s.copy(planStart = today), emptyList(), today)
        val steps = e.steps(Session("easy", "Easy run-walk", 30, "", "runwalk"))
        val tr = (0 until 6).flatMap { c -> trace(Triple(240, 132 + c, 146 + c), Triple(60, 146 + c, 131 + c)) }
        val r = Run("rw", today, "easy", 3.6, 1800, 140, 151, source = "live", trace = tr, steps = steps, kind = "runwalk")
        val d = e.debrief(r)
        assertEquals(12, d.blocks.size); assertTrue(!d.planned)
        assertEquals("walk", d.blocks[1].step.kind); assertTrue(d.blocks[0].peak > d.blocks[1].low + 10)
        assertTrue(d.facts.any { it.label == "Walk breaks" && it.value.startsWith("−1") })
        assertTrue(d.learned.any { it.text.contains("Each walk brought your heart rate down") })
        assertTrue(d.head.startsWith("Run-walk"))
    }

    @Test fun intervalDebriefJudgesTheRepsNotTheAverage() {
        val e = Engine(s.copy(planStart = today), emptyList(), today)
        val steps = e.steps(Session("intervals", "4 × 4 min hard", 50, "", "4x4"))
        val tr = trace(Triple(600, 120, 140)) + (0 until 4).flatMap { c -> trace(Triple(240, 140, 170 + c * 2), Triple(180, 168, 138)) } + trace(Triple(600, 140, 125))
        val d = e.debrief(Run("iv", today, "intervals", 7.0, 2880, 148, 176, source = "live", trace = tr, steps = steps, kind = "4x4"))
        assertEquals("Intervals done", d.head)
        assertEquals(4, d.blocks.count { it.step.kind == "hard" })
        assertTrue(d.facts.any { it.label == "Rep peaks" }); assertTrue(d.facts.any { it.label == "Recovery" })
        assertTrue(d.learned.any { it.text.contains("rising gently") })
        assertTrue(d.next.contains("properly easy"))
    }

    @Test fun importedHardRunIsCalledWhatItWas() {
        val tr = trace(Triple(360, 118, 150), Triple(600, 150, 164), Triple(3340, 162, 172))
        val r = tenK.copy(source = "health", trace = tr, zoneSec = listOf(200, 160, 240, 2600, 1100))
        val d = Engine(s, listOf(r), today).debrief(r)
        assertEquals("A hard effort", d.head)
        assertTrue(d.facts.any { it.label == "Above Zone 2 from" })
        assertTrue(d.learned.any { it.text.contains("doesn't count as Zone 2 time") })
        assertTrue(d.blocks.isEmpty())                                   // no session was followed, so no blocks are made up
        // An old run with only an average still gets a verdict.
        assertEquals("A hard effort", Engine(s, listOf(tenK), today).debrief(tenK).head)
    }

    @Test fun plannedBlocksAreLaidOverAnImportedRunThatFits() {
        val set = s.copy(planStart = today, overrides = mapOf(today to "runwalk"))
        val tr = (0 until 8).flatMap { trace(Triple(240, 134, 146), Triple(60, 146, 132)) }
        val r = Run("hc", today, "easy", 4.8, 2400, 140, 147, source = "health", trace = tr)
        val d = Engine(set, listOf(r), today).debrief(r)
        assertTrue(d.planned); assertEquals(16, d.blocks.size)
        assertTrue(Engine(s.copy(planStart = today), listOf(r.copy(date = today.plusDays(1))), today).debrief(r.copy(date = today.plusDays(1))).blocks.isEmpty())
    }

    // ---- 0.9: cues, route, splits from pace ----

    @Test fun cuesWarnBeforeAHardRepAndBeepIntoEveryChange() {
        val e = Engine(s, emptyList(), today)
        val tr = RunTracker(e.steps(Session("intervals", "4 × 4 min hard", 50, "", "4x4")), e.zb)
        val log = HashMap<Int, List<LiveEvent>>()
        repeat(1500) { val ev = tr.tick(150); if (ev.isNotEmpty()) log[tr.elapsed] = ev }
        assertEquals("Hard rep in 10 seconds.", log[590]!!.single { it.kind == "warn" }.text)
        assertEquals(listOf("beep"), log[598]!!.map { it.kind }); assertEquals(listOf("beep"), log[599]!!.map { it.kind })
        assertTrue(log[600]!!.single { it.kind == "step" }.text.startsWith("Hard, rep 1 of 4"))
        assertNull(log[830])                                             // no spoken warning before an easy jog, only the tones
        assertEquals("beep", log[838]!!.single().kind)
        // The last rep is announced as such, and halfway is called once.
        val all = log.values.flatten()
        assertTrue(RunTracker(e.steps(Session("intervals", "", 50, "", "4x4")), e.zb).let { t2 -> (1..2300).flatMap { t2.tick(150) } }.any { it.text.endsWith("Last one.") })
        assertEquals(1, RunTracker(e.steps(Session("easy", "", 40, "")), e.zb).let { t3 -> (1..2400).flatMap { t3.tick(140) } }.count { it.kind == "half" })
        assertTrue(all.none { it.kind == "tick" })
    }

    @Test fun runWalkGetsAQuietNudgeBeforeEachSwitch() {
        val e = Engine(s, emptyList(), today)
        val tr = RunTracker(e.steps(Session("easy", "", 30, "", "runwalk")), e.zb)
        val log = HashMap<Int, List<LiveEvent>>()
        repeat(600) { val ev = tr.tick(140); if (ev.isNotEmpty()) log[tr.elapsed] = ev }
        assertEquals("tick", log[235]!!.single().kind); assertEquals("", log[235]!!.single().text)       // before the walk
        assertEquals("tick", log[295]!!.single().kind)                                                   // and before the jog
        assertTrue(log.values.flatten().none { it.kind == "warn" })
    }

    @Test fun liveRunKeepsItsRouteAndAccuracy() {
        val tr = RunTracker(emptyList(), listOf(124, 137, 150, 162, 175))
        assertEquals(-1, tr.acc)
        for (i in 0 until 300) { tr.tick(140); tr.fix(20.0 + i * 0.00003, 78.0, if (i == 50) 60f else 6f, i * 1000L) }
        assertEquals(6, tr.acc)
        assertTrue(tr.route.size % 3 == 0); assertTrue(tr.route.size / 3 in 60..80)       // one point every four seconds
        val r = tr.toRun(today, "easy", 600)
        assertEquals(tr.route.toList(), r.route); assertTrue(r.distKm > 0.9)
        assertEquals(2000000, r.route[1]); assertEquals(7800000, r.route[2])
    }

    @Test fun splitsCanBeWorkedOutFromAPaceTrace() {
        val pace = List(180) { 360 } + List(180) { 480 }                 // 30 min at 6:00 then 30 min at 8:00: 5 km + 3.75 km
        val sp = splitsFromPace(pace, List(360) { if (it < 180) 160 else 140 }, 8.75)
        assertEquals(8, sp.size); assertEquals(360, sp[0].sec); assertEquals(160, sp[0].hr); assertEquals(480, sp[7].sec); assertEquals(140, sp[7].hr)
        assertTrue(splitsFromPace(List(360) { if (it % 3 == 0) 400 else 0 }, emptyList(), 8.0).isEmpty())     // too patchy to trust
    }

    @Test fun offersRestingHeartRateFromHealthConnect() {
        val sg = Engine(s.copy(hcRest = 54, snoozed = mapOf("talk" to today)), listOf(tenK), today).suggestions()
        assertEquals(SetRest(54), sg.single().act)
        assertTrue(Engine(s.copy(hcRest = 58, snoozed = mapOf("talk" to today)), listOf(tenK), today).suggestions().isEmpty())     // two beats is not worth a question
    }

    @Test fun freeRunHasNoBlocksAndNoZoneTwoCue() {
        val e = Engine(s, emptyList(), today)
        val st = e.steps(e.freeRun())
        assertEquals(1, st.size); assertEquals(0, st[0].sec)
        val tr = RunTracker(st, e.zb)
        val ev = (1..900).flatMap { tr.tick(175) }                       // fifteen minutes in Zone 5
        assertTrue(ev.none { it.kind in setOf("cap", "beep", "warn", "tick", "step", "half") })
        // The same heart rate on a planned easy run is told to ease off.
        assertTrue(RunTracker(e.steps(Session("easy", "", 30, "")), e.zb).let { t2 -> (1..900).flatMap { t2.tick(175) } }.any { it.kind == "cap" })
        assertEquals("free", tr.toRun(today, "easy", 600, "free").kind)
        assertEquals("hard", e.guessType(900, 175)); assertEquals("easy", e.guessType(900, 140))
    }

    @Test fun blocksShowTheHeartRateToHoldExceptOnTheTalkTest() {
        val e = Engine(s, emptyList(), today)
        val iv = e.steps(Session("intervals", "", 50, "", "4x4"))
        assertEquals("under 150 bpm", e.target(iv[0])); assertEquals("162–174 bpm", e.target(iv[1])); assertEquals("under 150 bpm", e.target(iv[2]))
        assertEquals("walk", e.target(e.steps(Session("easy", "", 30, "", "runwalk"))[1]))
        val talk = e.steps(Session("tempo", "Talk test", 35, "", "talk"))
        assertEquals(8, talk.size); assertEquals(35 * 60 - 120, talk.sumOf { it.sec })
        assertTrue(talk.subList(1, 7).all { it.kind == "talk" && e.target(it) == "" })
        assertEquals("under 150 bpm", e.target(talk[0]))                 // the warm-up is still an ordinary easy block
        // No "ease off" during the stages, however high the heart rate goes.
        val tr = RunTracker(talk, e.zb)
        assertTrue((1..1670).flatMap { tr.tick(if (it > 600) 170 else 130) }.none { it.kind == "cap" })
        assertEquals("full sentences", Engine(s.copy(hrMeds = true), emptyList(), today).target(iv[0]))
    }

    // ---- from a real talk test on a phone ----

    /** A talk test as the app records it: ten easy minutes, six three-minute stages, five to cool down. */
    private fun talkRun(e: Engine): Run {
        val hr = listOf(141, 144, 148, 154, 160, 169); val pace = listOf(510, 511, 502, 455, 426, 359)
        val tr = ArrayList<Int>(); val pc = ArrayList<Int>()
        repeat(60) { tr += 137; pc += 506 }
        for (i in 0 until 6) repeat(18) { j -> tr += hr[i] - (if (j < 6) 4 else 0); pc += pace[i] }      // heart rate lags a minute behind each step up
        repeat(30) { tr += 145 - it; pc += 570 }
        return Run("talk", today, "tempo", 4.01, 1980, 146, 175, source = "live", trace = tr, pace = pc, kind = "talk", steps = e.steps(Session("tempo", "Talk test", 35, "", "talk")),
            zoneSec = listOf(291, 977, 431, 264, 20))
    }

    @Test fun talkTestGivesADirectReadingAndItsOwnVerdict() {
        val set = s.copy(maxHr = 186, restHr = 66, talkHr = 155)
        val e0 = Engine(set, emptyList(), today); val r = talkRun(e0)
        val e = Engine(set, listOf(r), today)
        assertEquals(147, e.refHr)
        assertEquals(6, e.talkStages(r).size); assertEquals(141, e.talkStages(r)[0].first)
        // 147 bpm sits between the 144 and 148 stages: the pace is read off between them, not scaled from anywhere.
        val est = e.estOf(r)!!; assertTrue(est.clean); assertTrue("${est.pace}", est.pace in 500.0..512.0)
        val d = e.debrief(r)
        assertEquals("Talk test done", d.head)
        assertTrue(d.learned.any { it.text.contains("141, 144, 148, 154, 160, 169") })
        assertTrue(d.learned.any { it.text.contains("Your ceiling is set at 155") && it.text.contains("6 beats wider") })
        assertTrue(d.learned.any { it.text.contains("Even steps") })                    // stages 1 to 3 were almost the same pace
        assertTrue(d.learned.none { it.text.contains("doesn't count as Zone 2") || it.text.contains("higher in the second half") })
        assertTrue(d.next.contains("under 156 bpm"))
        // Zone time follows the zones as they are now, not as they were when the run was saved.
        val now = e.zoneSecOf(r); val before = Engine(set.copy(talkHr = 0), listOf(r), today).zoneSecOf(r)
        assertEquals(1980, now.sum()); assertTrue(now[1] > before[1])
    }

    @Test fun twoDirectReadingsOutvoteScaledHardRuns() {
        val set = s.copy(maxHr = 186, restHr = 66, talkHr = 155)
        val r = talkRun(Engine(set, emptyList(), today))
        val hardRuns = (1L..5L).map { Run("h$it", today.minusDays(it * 3), "hard", 7.0, 2940, 165) }     // 7:00/km at 165: scales to about 8:33, or worse, wildly off
        val easy = Run("e", today.minusDays(20), "easy", 5.5, 2700, 147)                                  // 8:11/km right at the reference
        val e = Engine(set, hardRuns + easy + r, today)
        assertTrue(e.reading()!!.clean); assertEquals(2, e.reading()!!.n)
        assertTrue(e.current!! in 495.0..505.0)                                         // between the two direct readings
        assertEquals(1, e.confidence()!!.level)
    }

    @Test fun aTraceOverrulesASourcesOwnAverage() {
        val tr = List(186) { if (it < 20) 112 + it * 2 else 163 }
        val r = Run("x", today, "easy", 4.34, 1857, 140, 171, source = "health", trace = tr)
        assertEquals(160, trueAvg(r)); assertEquals(163, steady(r)!!.second)
        assertEquals(140, trueAvg(r.copy(trace = tr.take(40))))                         // a trace covering a fraction of the run isn't trusted
        assertTrue(!Engine(s, listOf(r), today).estOf(r)!!.clean)                       // at 163 it is no longer mistaken for an easy run
    }

    // ---- missed sessions and coming back ----

    /** Four full weeks on the default days (Tue, Thu, Sat, Sun), ending on the Sunday [lastSunday]. */
    private fun steadyWeeks(lastSunday: LocalDate, n: Int = 4): List<Run> = (0 until n).flatMap { w ->
        listOf(5L to 2400, 3L to 3000, 1L to 2400, 0L to 4200).map { (back, sec) -> lastSunday.minusWeeks(w.toLong()).minusDays(back).let { Run("s$it", it, if (back == 0L) "long" else "easy", sec / 480.0, sec, 142) } }
    }
    private val sun = today.minusDays(1)                    // Sunday 15 Nov

    @Test fun aMissedRunPointsAtTheNextOne() {
        val wed = today.plusDays(2); val tue = today.plusDays(1)
        // Wednesday is a rest day, so Tuesday's easy run can simply happen a day late.
        val m = Engine(s, steadyWeeks(sun), wed).missed()!!
        assertEquals(tue, m.date); assertEquals("easy", m.session.type); assertEquals(wed, m.moveTo); assertEquals("Run it today?", m.head); assertNull(m.todays)
        val moved = Engine(s.copy(overrides = mapOf(tue to "skip", wed to "easy")), steadyWeeks(sun), wed)
        assertNull(moved.missed()); assertEquals("easy", moved.weekPlan(today).ses[3]!!.type)
        // Letting it go clears it, and so does a run logged on the day.
        assertNull(Engine(s.copy(overrides = mapOf(tue to "skip")), steadyWeeks(sun), wed).missed())
        assertNull(Engine(s, steadyWeeks(sun) + Run("x", tue, "easy", 5.0, 2400, 140), wed).missed())
        assertNull(Engine(s, emptyList(), wed).missed())                                   // nothing to miss before the first run
        // Two in a row: only the latest is asked about, and answering it settles the matter.
        val two = s.copy(days = listOf(2, 3, 6, 7)); val fri = today.plusDays(4)
        assertEquals(wed, Engine(two, steadyWeeks(sun), today.plusDays(3)).missed()!!.date)
        assertNull(Engine(two.copy(overrides = mapOf(wed to "skip")), steadyWeeks(sun), today.plusDays(3)).missed())
        // A run since then means it is already behind them.
        assertNull(Engine(two, steadyWeeks(sun) + Run("w", wed, "easy", 5.0, 2400, 140), today.plusDays(3)).missed())
        // By Thursday a run is planned anyway: that run is the answer, and nothing is stacked onto it.
        val thu = Engine(s, steadyWeeks(sun), today.plusDays(3)).missed()!!
        assertEquals(tue, thu.date); assertNull(thu.moveTo); assertEquals("Today still counts", thu.head); assertTrue(thu.todays!!.type in setOf("intervals", "tempo"))
        // The reminder the day after speaks to it.
        assertEquals("Missed yesterday? Today is free.", Engine(s, steadyWeeks(sun), wed).reminder()!!.first)
        assertEquals("Yesterday's gone. Today still counts.", Engine(s, steadyWeeks(sun) + Run("tue", tue, "easy", 5.0, 2400, 140), today.plusDays(4).let { fri -> fri }).let { Engine(s.copy(days = listOf(2, 4, 5, 7)), steadyWeeks(sun) + Run("tue", tue, "easy", 5.0, 2400, 140), today.plusDays(4)).reminder()!!.first })
    }

    @Test fun aMissedHardSessionMovesOnlyToADayWithRoom() {
        val runs = steadyWeeks(sun) + Run("tue", today.plusDays(1), "easy", 5.0, 2400, 140)
        val fri = today.plusDays(4); val thu = today.plusDays(3); val sat = today.plusDays(5)
        val e = Engine(s, runs, fri)
        assertTrue(e.weekPlan(today).ses[4]!!.type in setOf("intervals", "tempo"))
        val m = e.missed()!!
        assertEquals(thu, m.date); assertEquals(fri, m.moveTo); assertEquals("Run it today?", m.head)      // Friday is free; Saturday after it is an easy day
        val moved = Engine(s.copy(overrides = mapOf(thu to "skip", fri to m.session.kind)), runs, fri)
        assertNull(moved.missed()); assertEquals(m.session.type, moved.weekPlan(today).ses[5]!!.type)
        // With a hard day on Saturday, Friday would put two demanding days back to back: dropped, not squeezed in.
        val tight = Engine(s.copy(overrides = mapOf(sat to "tempo")), runs, fri).missed()!!
        assertNull(tight.moveTo); assertEquals("Next run: Saturday", tight.head); assertTrue(tight.why.contains("Better dropped"))
        // A long run missed on Sunday is not carried into the new week.
        val mon = Engine(s, steadyWeeks(sun.minusWeeks(1)) + Run("a", today.minusDays(6), "easy", 5.0, 2400, 140) + Run("b", today.minusDays(4), "easy", 5.0, 2400, 140), today).missed()!!
        assertEquals(sun, mon.date); assertEquals("long", mon.session.type); assertNull(mon.moveTo); assertEquals("Next run: Tuesday", mon.head); assertEquals("Nothing to make up.", mon.why)
    }

    @Test fun theReturnIsSizedToTheGap() {
        val normal = Engine(s, steadyWeeks(sun), today).weekPlan(today)
        assertNull(Engine(s, steadyWeeks(sun), today.plusDays(5)).comeback)                // six days: nothing to manage
        // Nine days: same length, strides instead of the hard session.
        val nine = Engine(s, steadyWeeks(sun), sun.plusDays(9))
        assertEquals(9, nine.comeback!!.gap)
        nine.weekPlan(mondayOf(sun.plusDays(9))).let { p -> assertEquals("Easing back in", p.phase); assertTrue(p.ses.values.none { it.type in setOf("intervals", "tempo") }); assertTrue(p.ses.values.any { it.kind == "strides" }); assertTrue(p.note.contains("fitness is intact")) }
        // Sixteen days: 70% and all easy, then 85% with strides, then back to full.
        val d16 = sun.plusDays(16); val e16 = Engine(s, steadyWeeks(sun), d16); val m16 = mondayOf(d16)
        val w0 = e16.weekPlan(m16); val w1 = e16.weekPlan(m16.plusWeeks(1)); val w2 = e16.weekPlan(m16.plusWeeks(2))
        assertTrue(w0.note.contains("shorter and all easy")); assertTrue(w0.ses.values.all { it.kind in setOf("easy", "long", "runwalk") })
        assertTrue("${w0.total} ${w1.total} ${w2.total}", w0.total < w1.total && w1.total < w2.total)
        assertTrue(w1.ses.values.any { it.kind == "strides" } && w1.note.contains("a little shorter")); assertEquals("", w2.note)
        assertNull(e16.missed())                                                          // no nagging about each session while away
        // Once running again the same schedule holds, counted from the day of the first run back.
        val back = Engine(s, steadyWeeks(sun) + Run("back", d16, "easy", 4.0, 1800, 140), d16.plusDays(2))
        assertEquals(Comeback(16, d16), back.comeback); assertTrue(back.weekPlan(m16).note.contains("shorter and all easy"))
        // Five weeks: the plan steps back a block as well.
        val d37 = sun.plusDays(37); val before = Engine(s, steadyWeeks(sun, 6), sun).weeks.last().idx
        val e37 = Engine(s, steadyWeeks(sun, 6) + Run("back", d37, "easy", 4.0, 1800, 140), d37)
        assertTrue(e37.weekPlan(mondayOf(d37)).note.contains("much shorter")); assertTrue("${e37.weeks.last().idx} $before", e37.weeks.last().idx <= before - 3)
        assertTrue(normal.note.isEmpty())
    }

    @Test fun theRunAfterBeingUnwellIsShortAndEasy() {
        val thu = today.plusDays(3); val fri = today.plusDays(4)
        val set = s.copy(overrides = mapOf(thu to "skip"), sick = listOf(thu))
        val runs = steadyWeeks(sun) + Run("tue", today.plusDays(1), "easy", 5.0, 2400, 140)
        val p = Engine(set, runs, fri).weekPlan(today)
        assertNull(p.ses[4]); assertTrue(p.ses[6]!!.min <= 30 && p.ses[6]!!.detail.contains("unwell")); assertEquals("long", p.ses[7]!!.type)
        assertNull(Engine(set, runs, fri).missed())
        // After that first run the plan is itself again.
        val after = Engine(set, runs + Run("x", today.plusDays(5), "easy", 3.5, 1500, 138), today.plusDays(6)).weekPlan(today)
        assertTrue(!after.ses[7]!!.detail.contains("unwell"))
    }

    @Test fun theForecastSaysWhenTheLongRunCannotGetLongEnough() {
        val race = s.copy(raceName = "Half", raceDate = today.plusDays(34), raceKm = 21.1)
        val short = listOf(tenK.copy(date = today.minusDays(10)), Run("l", today.minusDays(3), "easy", 6.0, 3000, 142))
        val c = Engine(race, short, today).raceCast()!!
        assertTrue(c.warn, c.warn.contains("long run will reach"))
        val ready = short + Run("l2", today.minusDays(1), "long", 13.0, 6300, 142)
        assertTrue(!Engine(race, ready, today).raceCast()!!.warn.contains("long run will reach"))
        assertTrue(c.hi - c.sec > Engine(race, ready, today).raceCast()!!.let { it.hi - it.sec })
    }

    @Test fun notificationsFollowTheRunnersOwnHour() {
        // Four weeks of runs that start around seven in the morning.
        val runs = steadyWeeks(sun).mapIndexed { i, r -> r.copy(startMin = 415 + (i % 3) * 5) }
        val tue = today.plusDays(1)
        val e = Engine(s.copy(remindOn = true, remindMin = 1080), runs, tue)
        assertEquals(420, e.usualRunMin); assertEquals(375, e.nudgeMin); assertEquals(540, e.lateMin)    // 6:15, and a follow-up at 9:00
        val n = e.nudge()!!
        assertEquals("run", n.kind); assertTrue(n.title, n.title.startsWith("Easy run today · ")); assertTrue(n.body, n.body.contains("You usually head out around 7 am"))
        assertEquals("Still time today", e.nudge(late = true)!!.title)
        assertNull(Engine(s, runs + Run("x", tue, "easy", 5.0, 2400, 140), tue).nudge(late = true))        // already ran: silence
        assertNull(Engine(s, runs, today).nudge())                                                         // Monday is a rest day with the week wide open
        // Switched off, or with no pattern to learn from, it is the time the runner picked.
        assertEquals(1080, Engine(s.copy(remindMin = 1080, remindSmart = false), runs, tue).nudgeMin)
        assertEquals(1080, Engine(s.copy(remindMin = 1080), steadyWeeks(sun), tue).nudgeMin)
        assertNull(Engine(s.copy(remindLate = false), runs, tue).lateMin)
        // The day after a miss, the message and its kind change.
        assertEquals("free", Engine(s, runs, today.plusDays(2)).nudge()!!.kind)
        assertEquals("back", Engine(s.copy(days = listOf(2, 3, 6, 7)), runs, today.plusDays(2)).nudge()!!.kind)
    }
}
