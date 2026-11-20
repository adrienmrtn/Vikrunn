package com.atala.app.core.plan

import android.app.Application
import com.atala.app.core.model.AtalaJson
import com.atala.app.core.model.Experience
import com.atala.app.core.model.GoalType
import com.atala.app.core.model.OnboardingAnswers
import com.atala.app.core.model.Paces
import com.atala.app.core.model.PlanCalendar
import com.atala.app.core.model.PlanPhase
import com.atala.app.core.model.RecentResult
import com.atala.app.core.model.TrainingPlan
import com.atala.app.core.model.WeeklyVolume
import com.atala.app.core.model.Workout
import com.atala.app.core.model.WorkoutKind
import com.atala.app.testing.TestSupport
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * PlanEngine for several answer sets, and four plans compared with what PlanEngine.swift gives
 * (the expected text below comes from a line-by-line port of the Swift rules, not from the Kotlin).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PlanEngineTest {
    @Before
    fun setUp() = TestSupport.reset()

    private val zone = ZoneId.systemDefault()
    private fun day(y: Int, m: Int, d: Int): LocalDate = LocalDate.of(y, m, d)
    private fun at(date: LocalDate, hour: Int = 9): Instant = date.atTime(hour, 0).atZone(zone).toInstant()
    private fun Instant.local(): LocalDate = atZone(zone).toLocalDate()

    private class Case(val answers: OnboardingAnswers, val start: LocalDate)

    private val cases = mapOf(
        // Does not run yet, 5K, three days, long run on Saturday.
        "beginner5k" to Case(
            OnboardingAnswers(firstName = "Léa", experience = Experience.Starting, weeklyVolume = WeeklyVolume.NotYet,
                goal = GoalType.FiveK, runDays = listOf(2, 4, 6), longRunDay = 6),
            day(2026, 11, 16),
        ),
        // Runs 10 to 25 km a week, 5 km in 26:00, a 10K on 3 January aiming at 50:00, from a Friday.
        "tenKTarget" to Case(
            OnboardingAnswers(firstName = "Tom", experience = Experience.SixMonthsToTwoYears, weeklyVolume = WeeklyVolume.From10To25,
                recentResult = RecentResult(5.0, 1560), goal = GoalType.TenK, hasRace = true, raceDate = at(day(2027, 1, 3), 8),
                targetSeconds = 3000, runDays = listOf(1, 3, 5, 7), longRunDay = 7),
            day(2026, 11, 20),
        ),
        // Experienced, five days a week, a half marathon too far away (more than 8 weeks), from a Wednesday.
        "half5days" to Case(
            OnboardingAnswers(firstName = "Ana", experience = Experience.OverTwoYears, weeklyVolume = WeeklyVolume.Over25,
                recentResult = RecentResult(10.0, 2700), goal = GoalType.Half, hasRace = true, raceDate = at(day(2027, 3, 14), 8),
                runDays = listOf(7, 1, 2, 4, 5, 5), longRunDay = 7),
            day(2026, 11, 18),
        ),
        // "Get in shape", open goal, two days a week.
        "shape2days" to Case(
            OnboardingAnswers(firstName = "Sam", experience = Experience.UnderSixMonths, weeklyVolume = WeeklyVolume.Under10,
                goal = GoalType.Shape, runDays = listOf(3, 6), longRunDay = 6),
            day(2026, 11, 20),
        ),
    )

    private val swift = mapOf(
        "beginner5k" to """
w0 base 2026-11-16: 1 strength; 2 easy "Easy run" 2.5km 26min @615; 3 stretching; 4 easy "Easy run" 2.5km 26min @615; 6 long "Long run" 3.0km 31min @588
w1 base 2026-11-23: 1 strength; 2 easy "Easy run" 2.5km 26min @615; 3 stretching; 4 easy "Easy run" 2.5km 26min @615; 6 long "Long run" 3.2km 33min @588
w2 base 2026-11-30: 1 strength; 2 easy "Easy run" 2.6km 28min @615; 3 stretching; 4 easy "Easy run" 2.6km 28min @615; 6 long "Long run" 3.5km 36min @588
w3 base 2026-12-07: 1 strength; 2 easy "Easy run" 2.5km 26min @615; 3 stretching; 4 easy "Easy run" 2.5km 26min @615; 6 long "Long run" 3.0km 31min @588
w4 build 2026-12-14: 1 strength; 2 intervals "3 × 800 m" 6.0km 54min @420; 3 stretching; 4 easy "Easy run" 3.7km 39min @615; 6 long "Long run" 4.1km 42min @588
w5 build 2026-12-21: 1 strength; 2 tempo "2 × 8 min tempo" 4.8km 45min @471; 3 stretching; 4 easy "Easy run" 4.0km 42min @615; 6 long "Long run" 4.4km 45min @588
w6 specific 2026-12-28: 1 strength; 2 racePace "3 × 1 km at race pace" 6.6km 59min @430; 3 stretching; 4 easy "Easy run" 4.3km 46min @615; 6 long "Long run" 4.8km 49min @588
w7 specific 2027-01-04: 1 strength; 2 racePace "3 × 1 km at race pace" 6.6km 59min @430; 3 stretching; 4 easy "Easy run" 3.7km 39min @615; 6 long "Long run" 4.1km 42min @588
        """.trimIndent(),
        "tenKTarget" to """
w0 base 2026-11-16: 5 easy "Easy run + strides" 3.6km 28min @446; 7 long "Long run" 6.5km 49min @427
w1 base 2026-11-23: 1 intervals "3 × 300 m" 4.7km 34min @299; 2 strength; 3 easy "Easy run" 3.9km 30min @446; 4 stretching; 5 easy "Easy run" 3.9km 30min @446; 7 long "Long run" 7.0km 53min @427
w2 base 2026-11-30: 1 intervals "3 × 400 m" 5.0km 36min @299; 2 strength; 3 easy "Easy run + strides" 4.2km 33min @446; 4 stretching; 5 easy "Easy run + strides" 4.2km 33min @446; 7 long "Long run" 7.6km 57min @427
w3 build 2026-12-07: 1 tempo "2 × 8 min tempo" 6.5km 45min @341; 2 strength; 3 easy "Easy run" 3.6km 28min @446; 4 stretching; 5 easy "Easy run" 3.6km 28min @446; 7 long "Long run" 6.5km 49min @427
w4 build 2026-12-14: 1 intervals "3 × 800 m" 6.8km 46min @307; 2 strength; 3 easy "Easy run + strides" 4.9km 38min @446; 4 stretching; 5 easy "Easy run + strides" 4.9km 38min @446; 7 long "Long run" 8.8km 66min @427
w5 specific 2026-12-21: 1 racePace "3 × 2 km at race pace" 10.4km 66min @313; 2 strength; 3 easy "Easy run" 5.3km 41min @446; 4 stretching; 5 easy "Easy run" 5.3km 41min @446; 7 long "Long run" 9.5km 72min @427
w6 taper 2026-12-28: 1 racePace "4 × 400 m at race pace" 5.6km 39min @313; 3 easy "Easy run + strides" 3.2km 25min @446; 4 stretching; 5 easy "Easy run + strides" 3.2km 25min @446; 7 race "10K" 10.0km 53min @313
        """.trimIndent(),
        "half5days" to """
w0 base 2026-11-16: 3 strength; 4 intervals "7 × 300 m" 7.4km 42min @245; 5 easy "Easy run + strides" 2.6km 17min @366; 6 stretching; 7 long "Long run" 11.5km 72min @349
w1 base 2026-11-23: 1 intervals "8 × 300 m" 7.9km 45min @245; 2 easy "Easy run" 2.8km 18min @366; 3 strength; 4 intervals "7 × 400 m" 8.1km 45min @245; 5 easy "Easy run" 2.8km 18min @366; 6 stretching; 7 long "Long run" 12.4km 77min @349
w2 base 2026-11-30: 1 intervals "8 × 400 m" 8.7km 48min @245; 2 easy "Easy run + strides" 3.0km 19min @366; 3 strength; 4 intervals "10 × 300 m" 8.9km 50min @245; 5 easy "Easy run + strides" 3.0km 19min @366; 6 stretching; 7 long "Long run" 13.4km 84min @349
w3 base 2026-12-07: 1 intervals "7 × 300 m" 7.4km 42min @245; 2 easy "Easy run" 2.6km 17min @366; 3 strength; 4 intervals "6 × 400 m" 7.5km 42min @245; 5 easy "Easy run" 2.6km 17min @366; 6 stretching; 7 long "Long run" 11.6km 72min @349
w4 build 2026-12-14: 1 intervals "5 × 800 m" 9.9km 54min @253; 2 easy "Easy run + strides" 3.5km 23min @366; 3 strength; 4 tempo "3 × 8 min tempo" 9.9km 55min @280; 5 easy "Easy run + strides" 3.5km 23min @366; 6 stretching; 7 long "Long run" 15.7km 98min @349
w5 build 2026-12-21: 1 tempo "3 × 8 min tempo" 9.9km 55min @280; 2 easy "Easy run" 3.8km 24min @366; 3 strength; 4 intervals "6 × 800 m" 11.1km 60min @253; 5 easy "Easy run" 3.8km 24min @366; 6 stretching; 7 long "Long run" 16.9km 105min @349
w6 specific 2026-12-28: 1 racePace "3 × 3 km at race pace" 14.1km 77min @290; 2 easy "Easy run + strides" 4.1km 26min @366; 3 strength; 4 racePace "3 × 3 km at race pace" 14.1km 77min @290; 5 easy "Easy run + strides" 4.1km 26min @366; 6 stretching; 7 long "Long run" 18.3km 114min @349
w7 specific 2027-01-04: 1 racePace "3 × 3 km at race pace" 14.1km 77min @290; 2 easy "Easy run" 3.5km 23min @366; 3 strength; 4 racePace "3 × 3 km at race pace" 14.1km 77min @290; 5 easy "Easy run" 3.5km 23min @366; 6 stretching; 7 long "Long run" 15.8km 98min @349
        """.trimIndent(),
        "shape2days" to """
w0 base 2026-11-16: 6 long "Long run" 5.5km 48min @504
w1 base 2026-11-23: 1 strength; 2 stretching; 3 easy "Easy run" 4.9km 45min @527; 6 long "Long run" 5.9km 52min @504
w2 base 2026-11-30: 1 strength; 2 stretching; 3 easy "Easy run + strides" 5.2km 47min @527; 6 long "Long run" 6.4km 56min @504
w3 base 2026-12-07: 1 strength; 2 stretching; 3 easy "Easy run" 4.5km 41min @527; 6 long "Long run" 5.5km 48min @504
w4 build 2026-12-14: 1 strength; 2 stretching; 3 easy "Easy run + strides" 6.1km 56min @527; 6 long "Long run" 7.5km 66min @504
w5 build 2026-12-21: 1 strength; 2 stretching; 3 easy "Easy run" 6.6km 60min @527; 6 long "Long run" 8.1km 71min @504
w6 specific 2026-12-28: 1 strength; 2 stretching; 3 easy "Easy run + strides" 7.1km 65min @527; 6 long "Long run" 8.7km 77min @504
w7 specific 2027-01-04: 1 strength; 2 stretching; 3 easy "Easy run" 6.2km 57min @527; 6 long "Long run" 7.5km 66min @504
        """.trimIndent(),
    )

    private fun kindName(kind: WorkoutKind) = AtalaJson.encodeToJsonElement(WorkoutKind.serializer(), kind).jsonPrimitive.content
    private fun phaseName(phase: PlanPhase) = AtalaJson.encodeToJsonElement(PlanPhase.serializer(), phase).jsonPrimitive.content

    /** The plan in the same words as the Swift port's output. */
    private fun describe(plan: TrainingPlan): String = plan.weeks.joinToString("\n") { week ->
        "w${week.index} ${phaseName(week.phase)} ${week.startDate.local()}: " + week.workouts.joinToString("; ") { w ->
            val day = PlanCalendar.mondayIndex(w.date)
            if (w.kind.isRun) "$day ${kindName(w.kind)} \"${w.title}\" ${w.distanceKm}km ${w.durationMin}min @${w.targetPace}"
            else "$day ${kindName(w.kind)}"
        }
    }

    @Test
    fun plansMatchTheSwiftRules() {
        for ((name, case) in cases) {
            val plan = PlanEngine.generate(case.answers, start = at(case.start))
            assertEquals("plan $name", swift.getValue(name), describe(plan))
        }
        val tenK = PlanEngine.generate(cases.getValue("tenKTarget").answers, start = at(day(2026, 11, 20)))
        // 5 km in 26:00 gives a VMA of 12.41 km/h; 50:00 is too ambitious: race pace held at 5 % faster than today's level.
        assertEquals(12.4069, tenK.vmaKmh, 0.0001)
        assertEquals(Paces(easy = 446, long = 427, tempo = 341, interval = 299, race = 313), tenK.paces)
        assertEquals(3297, tenK.currentSeconds)
        assertEquals(3130, tenK.predictedSeconds)
        assertEquals(day(2027, 1, 3), tenK.raceDate!!.local())
        val beginner = PlanEngine.generate(cases.getValue("beginner5k").answers, start = at(day(2026, 11, 16)))
        assertEquals(Paces(easy = 615, long = 588, tempo = 471, interval = 412, race = 430), beginner.paces)
        assertEquals(2151, beginner.currentSeconds)
        assertEquals(2150, beginner.predictedSeconds)
        // A race beyond eight weeks: eight weeks without a race session, the date is still kept.
        val half = PlanEngine.generate(cases.getValue("half5days").answers, start = at(day(2026, 11, 18)))
        assertEquals(8, half.weeks.size)
        assertTrue(half.allWorkouts.none { it.kind == WorkoutKind.Race })
        assertEquals(day(2027, 3, 14), half.raceDate!!.local())
        val shape = PlanEngine.generate(cases.getValue("shape2days").answers, start = at(day(2026, 11, 20)))
        assertNull(shape.currentSeconds)
        assertNull(shape.raceDate)
    }

    private fun checkRules(name: String, answers: OnboardingAnswers, start: LocalDate, expectedWeeks: Int) {
        val plan = PlanEngine.generate(answers, start = at(start, 14))
        val today = at(start, 0)
        val runDays = answers.runDays.filter { it in 1..7 }.distinct().sorted().ifEmpty { listOf(2, 4, 7) }
        val longDay = answers.longRunDay?.takeIf { it in runDays } ?: runDays.last()
        val restDays = (1..7).filter { it !in runDays }
        val raceDay = plan.allWorkouts.firstOrNull { it.kind == WorkoutKind.Race }?.date

        assertEquals("$name: weeks", expectedWeeks, plan.weeks.size)
        assertEquals(PlanCalendar.mondayOfWeek(today), plan.startDate)
        plan.weeks.forEachIndexed { index, week ->
            assertEquals(index, week.index)
            assertEquals(1, PlanCalendar.mondayIndex(week.startDate))
            assertEquals(PlanCalendar.addDays(plan.startDate, index * 7), week.startDate)
            assertEquals(week.workouts.sortedBy { it.date }, week.workouts)
            week.workouts.forEach { workout ->
                assertEquals(index, workout.weekIndex)
                assertTrue("$name: in its week", workout.date >= week.startDate && workout.date < PlanCalendar.addDays(week.startDate, 7))
                assertEquals(PlanCalendar.startOfDay(workout.date), workout.date)
            }
            // The long run, on the chosen day, every week it is not in the past or replaced by the race.
            val longs = week.workouts.filter { it.kind == WorkoutKind.Long }
            longs.forEach { assertEquals("$name: long run day", longDay, PlanCalendar.mondayIndex(it.date)) }
            val longDate = PlanCalendar.addDays(week.startDate, longDay - 1)
            val expectedLong = longDate >= PlanCalendar.startOfDay(today) && (raceDay == null || longDate < raceDay)
            assertEquals("$name w$index: one long run", if (expectedLong) 1 else 0, longs.size)
        }
        val all = plan.allWorkouts
        assertTrue("$name: nothing in the past", all.all { it.date >= PlanCalendar.startOfDay(today) })
        val runs = all.filter { it.kind.isRun && it.kind != WorkoutKind.Race }
        assertTrue("$name: runs on the chosen days only", runs.all { PlanCalendar.mondayIndex(it.date) in runDays })
        assertEquals("$name: one run a day at most", runs.size, runs.map { it.date }.distinct().size)
        if (restDays.isNotEmpty()) {
            assertTrue("$name: strength and stretching on rest days",
                all.filter { !it.kind.isRun }.all { PlanCalendar.mondayIndex(it.date) in restDays })
        }
        raceDay?.let { race -> assertTrue("$name: nothing after the race", all.all { it.date <= race }) }

        // Sensible paces: interval < tempo < long < easy, race between interval and easy, 2:30 to 15:00 per km.
        val p = plan.paces
        assertTrue("$name paces $p", p.interval < p.tempo && p.tempo < p.long && p.long < p.easy)
        assertTrue("$name paces $p", p.race in p.interval..p.easy)
        assertTrue("$name paces $p", listOf(p.easy, p.long, p.tempo, p.interval, p.race).all { it in 150..900 })
        runs.forEach { run ->
            val pace = run.targetPace!!
            assertTrue("$name: ${run.title} at $pace", pace in p.interval..p.easy)
            assertTrue("$name: ${run.title} ${run.distanceKm} km", run.distanceKm in 1.0..25.0)
            assertTrue(run.blocks.isNotEmpty() && run.why.isNotBlank())
        }
        // The same plan comes back from its JSON (the saved file and the account).
        val json = AtalaJson.encodeToString(TrainingPlan.serializer(), plan)
        val decoded = AtalaJson.decodeFromString(TrainingPlan.serializer(), json)
        assertEquals(plan.copy(createdAt = Instant.ofEpochSecond(plan.createdAt.epochSecond)), decoded)
    }

    @Test
    fun rulesHoldForSeveralAnswerSets() {
        val base = OnboardingAnswers(firstName = "Ana")
        // Beginner who does not run yet, 5K, three days.
        checkRules("beginner 5K", cases.getValue("beginner5k").answers, day(2026, 11, 16), 8)
        checkRules("beginner 5K from Friday", cases.getValue("beginner5k").answers, day(2026, 11, 20), 8)
        // Intermediate 10K with a target time and a race in six weeks.
        checkRules("10K target", cases.getValue("tenKTarget").answers, day(2026, 11, 20), 7)
        // Half marathon in five weeks, five days.
        checkRules(
            "half in 5 weeks",
            base.copy(experience = Experience.OverTwoYears, weeklyVolume = WeeklyVolume.Over25, goal = GoalType.Half,
                hasRace = true, raceDate = at(day(2026, 12, 20)), runDays = listOf(1, 2, 4, 5, 7), longRunDay = 7),
            day(2026, 11, 17), 5,
        )
        checkRules("half too far", cases.getValue("half5days").answers, day(2026, 11, 18), 8)
        // Get in shape, two days; run regularly, five days, long run on a day that is not a run day.
        checkRules("shape 2 days", cases.getValue("shape2days").answers, day(2026, 11, 20), 8)
        checkRules(
            "regular 5 days",
            base.copy(experience = Experience.UnderSixMonths, weeklyVolume = WeeklyVolume.Under10, goal = GoalType.Regular,
                runDays = listOf(1, 2, 3, 5, 6), longRunDay = 7),
            day(2026, 11, 22), 8,
        )
        // Cautious (pain reported), one day a week, no answers about volume.
        checkRules(
            "cautious 1 day",
            base.copy(goal = GoalType.TenK, healthFlags = listOf("pain"), runDays = listOf(4)),
            day(2026, 11, 19), 8,
        )
        // No days chosen: Tuesday, Thursday, Sunday.
        checkRules("no days", base.copy(goal = GoalType.FiveK), day(2026, 11, 16), 8)
        // A race next Sunday: one week, the race and nothing after it.
        checkRules(
            "race this week",
            base.copy(experience = Experience.OverTwoYears, goal = GoalType.FiveK, hasRace = true,
                raceDate = at(day(2026, 11, 22)), runDays = listOf(2, 4, 6)),
            day(2026, 11, 17), 1,
        )
    }

    @Test
    fun beginnerPlanHasNoHardSessionAtFirstAndATaperBeforeTheRace() {
        val beginner = PlanEngine.generate(cases.getValue("beginner5k").answers, start = at(day(2026, 11, 16)))
        beginner.weeks.filter { it.phase == PlanPhase.Base }.forEach { week ->
            assertTrue(week.workouts.none { it.kind.isQuality })
        }
        assertEquals(listOf(PlanPhase.Base, PlanPhase.Base, PlanPhase.Base, PlanPhase.Base, PlanPhase.Build, PlanPhase.Build,
            PlanPhase.Specific, PlanPhase.Specific), beginner.weeks.map { it.phase })
        val tenK = PlanEngine.generate(cases.getValue("tenKTarget").answers, start = at(day(2026, 11, 20)))
        assertEquals(PlanPhase.Taper, tenK.weeks.last().phase)
        val race = tenK.allWorkouts.last()
        assertEquals(WorkoutKind.Race, race.kind)
        assertEquals(day(2027, 1, 3), race.date.local())
        assertEquals(10.0, race.distanceKm, 0.0)
        assertTrue(tenK.weeks.last().workouts.none { it.kind == WorkoutKind.Strength })
        // The same answers on the same day give the same plan (ids aside).
        val again = PlanEngine.generate(cases.getValue("tenKTarget").answers, start = at(day(2026, 11, 20)))
        assertEquals(describe(tenK), describe(again))
    }
}
