package com.atala.app.core.run

import android.Manifest
import android.app.Application
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.AtalaJson
import com.atala.app.core.model.Experience
import com.atala.app.core.model.GoalType
import com.atala.app.core.model.OnboardingAnswers
import com.atala.app.core.model.ProAccess
import com.atala.app.core.model.RunSource
import com.atala.app.core.model.StepKind
import com.atala.app.core.model.WeeklyVolume
import com.atala.app.core.model.Workout
import com.atala.app.core.model.WorkoutKind
import com.atala.app.core.model.WorkoutStatus
import com.atala.app.core.plan.PlanEngine
import com.atala.app.core.store.AppStore
import com.atala.app.testing.TestSupport
import kotlinx.coroutines.MainScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * RunTracker, ActiveRun and RunAnalysis fed with synthetic GPS through the fused provider's
 * callback, with Robolectric's clock standing in for the phone's (one fix per simulated second).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RunTrackerTest {
    private val context get() = TestSupport.context
    private lateinit var store: AppStore

    @Before
    fun setUp() {
        TestSupport.reset()
        store = AppStore(null, MainScope())
    }

    @After
    fun tearDown() {
        ActiveRun.current?.let(ActiveRun::close)
        ProAccess.setPro(false)
    }

    /** What the coach showed for each tracker event (the bubble of the live screen). */
    private class Recorder(run: ActiveRun) {
        val events = mutableListOf<RunTrackerEvent>()
        val cues = mutableListOf<Pair<RunTrackerEvent, String>>()

        init {
            val coach = run.tracker.onEvent
            run.tracker.onEvent = { event ->
                coach?.invoke(event)
                events += event
                if (event != RunTrackerEvent.PaceTick) cues += event to run.voice.lastCue.value
            }
        }

        val kilometres: List<Int> get() = events.filterIsInstance<RunTrackerEvent.Kilometre>().map { it.count }
    }

    private fun waitForBackup(condition: (RunBackup?) -> Boolean): RunBackup? {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            val backup = RunBackupStore.read(context)
            if (condition(backup)) return backup
            Thread.sleep(20)
        }
        return RunBackupStore.read(context)
    }

    @Test
    fun freeRunOf3KmWithPauseNoiseBadFixesAndAJump() {
        val track = GpsTrack()
        val run = ActiveRun.obtain(context, store, null)
        val tracker = run.tracker
        val recorder = Recorder(run)

        // Ready screen: the signal shows, nothing is measured before the start.
        tracker.prepare()
        repeat(3) {
            TestSupport.idle(1)
            tracker.deliver(track.fix(0.0))
        }
        assertEquals(RunGpsQuality.Good, tracker.snapshot.value.gps)
        assertEquals(RunTrackerState.Idle, tracker.snapshot.value.state)
        assertEquals(0.0, tracker.snapshot.value.distance, 0.0)

        run.setPhase(RunFlowPhase.Countdown)
        run.begin()
        assertEquals(RunTrackerState.Running, tracker.snapshot.value.state)
        assertEquals(RunFlowPhase.Active, run.phase.value)
        assertEquals(L("Off you go. Run the way you feel."), run.voice.lastCue.value)

        var along = 0.0
        var running = 0
        var truth = 0.0
        val paces = mutableListOf<Int>()
        fun runUntil(target: Double) {
            while (along < target) {
                // About 5:30 per km, a few percent faster and slower along the way.
                val speed = 1000.0 / 330 * (1 + 0.03 * sin(2 * PI * running / 240))
                truth += track.trueDistance(along, along + speed)
                along += speed
                running++
                TestSupport.idle(1)
                when (running) {
                    // Three inaccurate fixes in a row, 40 m off: ignored, the signal shows as poor.
                    200, 201, 202 -> {
                        tracker.deliver(track.fix(along, accuracy = 45.0 + (running - 200) * 10, eastOffset = 40.0))
                        assertEquals(RunGpsQuality.Poor, tracker.snapshot.value.gps)
                    }
                    // A jump of 150 m with a confident accuracy: faster than a runner, ignored.
                    700 -> tracker.deliver(track.fix(along, accuracy = 6.0, eastOffset = 150.0))
                    // A cached fix 20 s old: ignored.
                    800 -> tracker.deliver(track.fix(along - 60, ageSeconds = 20.0))
                    else -> tracker.deliver(track.fix(along))
                }
                if (running % 60 == 30 && running > 60) paces += tracker.snapshot.value.currentPace
            }
        }

        runUntil(1480.0)
        val beforePause = tracker.snapshot.value
        assertEquals(running, beforePause.elapsed)
        assertEquals(listOf(1), recorder.kilometres)

        // Pause: the runner stands still for a minute, fixes keep coming.
        tracker.pause()
        assertEquals(RunTrackerState.Paused, tracker.snapshot.value.state)
        assertEquals(0, tracker.snapshot.value.currentPace)
        repeat(60) {
            TestSupport.idle(1)
            tracker.deliver(track.fix(along))
        }
        val paused = tracker.snapshot.value
        assertEquals("time stands still during a pause", running, paused.elapsed)
        assertEquals("no distance during a pause", beforePause.distance, paused.distance, 0.0)

        // The copy on disk written at the pause (active_run.json), read back as the next launch would.
        val backup = waitForBackup { it?.isPaused == true }
        assertNotNull(backup)
        backup!!
        assertEquals(running.toDouble(), backup.activeSeconds, 0.01)
        assertEquals(paused.distance, backup.distance, 0.001)
        assertEquals(tracker.route.size, backup.route.size)
        assertEquals("Free run", backup.title)
        assertNull(backup.workoutId)
        assertTrue(backup.isWorthSaving)
        val recovered = backup.makeRecord()
        assertEquals(running, recovered.durationSec)
        assertEquals(RunSource.Gps, recovered.source)

        tracker.resume()
        assertEquals(RunTrackerState.Running, tracker.snapshot.value.state)
        runUntil(3030.0)

        val end = tracker.snapshot.value
        assertEquals(running, end.elapsed)
        val error = abs(end.distance - truth) / truth
        assertTrue("distance ${end.distance} vs true $truth (${error * 100} %)", error < 0.02)
        assertEquals(listOf(1, 2, 3), recorder.kilometres)

        // Paces: the current one over the last seconds, the average since the start (pause excluded).
        assertTrue("current paces $paces", paces.isNotEmpty() && paces.all { it in 300..365 })
        assertTrue(end.currentPace in 300..365)
        val trueAverage = running / (truth / 1000)
        assertTrue("average ${end.averagePace} vs $trueAverage", abs(end.averagePace - trueAverage) / trueAverage < 0.02)

        // The kilometre cue: time and average pace, as shown in the coach bubble.
        val kmCue = recorder.cues.first { it.first == RunTrackerEvent.Kilometre(1) }.second
        assertTrue(kmCue, kmCue.startsWith("1 km in ") && kmCue.contains("/km"))

        // Finishing saves exactly one run, with its route.
        val record = run.finish(save = true)
        assertNotNull(record)
        record!!
        assertSame(record, run.finish(save = true))
        assertEquals(1, store.current.runs.size)
        assertEquals(record.id, store.current.runs.single().id)
        assertEquals(RunFlowPhase.Feeling, run.phase.value)
        assertEquals(RunTrackerState.Finished, tracker.snapshot.value.state)
        assertEquals(running, record.durationSec)
        assertEquals(end.distance, record.distanceM, 0.0)
        assertEquals(RunSource.Gps, record.source)
        assertEquals("Free run", record.title)
        assertTrue("route of ${record.route.size} points", record.route.size in 300..450)
        assertTrue(record.route.zipWithNext().all { (a, b) -> !b.time.isBefore(a.time) })
        assertTrue(record.route.all { it.alt != null })
        assertTrue(record.splits.isEmpty())
        // The feeling is saved on that same run.
        run.saveFeeling(com.atala.app.core.model.Feeling.Good)
        assertEquals(com.atala.app.core.model.Feeling.Good, store.current.runs.single().feeling)
        assertEquals(1, store.current.runs.size)
        // The copy on disk goes once the run is saved.
        assertNull(waitForBackup { it == null })

        // Figures of the summary.
        val analysis = RunAnalysis(record)
        println("free run: ${"%.1f".format(end.distance)} m measured, ${"%.1f".format(truth)} m true, ${running} s, average ${end.averagePace} s/km, " +
            "paces $paces, splits ${analysis.kmSplits.map { it.pace }}, gain ${analysis.elevationGainM} m, ${record.route.size} route points")
        assertEquals(3, analysis.kmSplits.size)
        analysis.kmSplits.forEach { split ->
            assertEquals(1000.0, split.distanceM, 0.5)
            // The second kilometre holds the pause: it must not count.
            assertTrue("split ${split.id} pace ${split.pace}", split.pace in 310..350)
        }
        assertNotNull(analysis.fastestSplit)
        assertTrue(analysis.paceProfile.size >= 4)
        assertTrue(analysis.paceProfile.all { it.pace in 280.0..400.0 })
        val gain = analysis.elevationGainM
        assertNotNull(gain)
        // Three climbs of the hill: 9 m, 18 m and about 10 m.
        assertTrue("elevation gain $gain", gain!! in 28..45)
        assertTrue(analysis.elevation.size in 5..80)
        assertTrue(analysis.elevation.all { it.altitudeM in 25.0..55.0 })
    }

    /** A plan session with a warm-up, three fast 400 m, their recoveries and a cool-down. */
    private fun intervalSession(): Pair<com.atala.app.core.model.TrainingPlan, Workout> {
        val answers = OnboardingAnswers(
            firstName = "Léa", experience = Experience.SixMonthsToTwoYears, weeklyVolume = WeeklyVolume.From10To25,
            goal = GoalType.TenK, runDays = listOf(2, 4, 6, 7), longRunDay = 7,
        )
        val monday = LocalDate.of(2026, 11, 16).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val plan = PlanEngine.generate(answers, start = monday)
        val workout = plan.allWorkouts.first { it.kind == WorkoutKind.Intervals }
        return plan to workout
    }

    @Test
    fun intervalSessionStepsSplitsAndVoiceCues() {
        ProAccess.setPro(true)
        val (plan, workout) = intervalSession()
        store.update { it.copy(plan = plan) }
        val track = GpsTrack(seed = 7)
        val run = ActiveRun.obtain(context, store, workout)
        val tracker = run.tracker
        val recorder = Recorder(run)
        val steps = tracker.steps
        assertEquals(
            listOf(StepKind.Warmup, StepKind.Work, StepKind.Recovery, StepKind.Work, StepKind.Recovery, StepKind.Work, StepKind.Recovery, StepKind.Cooldown),
            steps.map { it.kind },
        )
        assertEquals(400, steps[1].distanceM)
        assertEquals(900, steps[0].durationSec)
        assertEquals(600, steps[7].durationSec)
        // What the coach says when the second fast step starts.
        assertEquals(
            "Fast, 2 of 3, 400 metres, target ${Fmt.pace(RunFormat.targetPace(steps[3])!!)} per kilometre.",
            RunFormat.stepSpeech(3, steps),
        )

        run.setPhase(RunFlowPhase.Countdown)
        run.begin()
        TestSupport.idle(1)
        tracker.deliver(track.fix(0.0))

        var along = 0.0
        var running = 0
        val stepStarts = mutableMapOf<Int, Int>()
        var completeAt: Int? = null
        var seen = 0
        while (completeAt == null || running < completeAt + 30) {
            val snapshot = tracker.snapshot.value
            val step = tracker.currentStep
            // The runner follows the coach: the second fast step is run far too fast.
            val pace = when (step?.kind) {
                StepKind.Warmup -> RunFormat.targetPace(step)!!.toDouble()
                StepKind.Work -> RunFormat.targetPace(step)!! - if (snapshot.stepIndex == 3) 40.0 else 0.0
                StepKind.Recovery -> 480.0
                StepKind.Cooldown -> 420.0
                else -> 400.0
            }
            along += 1000 / pace
            running++
            TestSupport.idle(1)
            tracker.deliver(track.fix(along))
            while (seen < recorder.events.size) {
                val event = recorder.events[seen++]
                if (event is RunTrackerEvent.StepStarted && event.index !in stepStarts) stepStarts[event.index] = running
                if (event == RunTrackerEvent.SessionComplete && completeAt == null) completeAt = running
            }
            assertTrue("session too long", running < 4000)
        }

        val started = recorder.events.filterIsInstance<RunTrackerEvent.StepStarted>().map { it.index }
        assertEquals((0..7).toList(), started)
        assertEquals(1, recorder.events.count { it == RunTrackerEvent.SessionComplete })
        // Time-based steps end on time.
        assertEquals(900.0, stepStarts.getValue(1).toDouble(), 1.0)
        assertEquals(600.0, (completeAt!! - stepStarts.getValue(7)).toDouble(), 1.0)
        // Every step start shows its cue; the coach's voice is on (Pro).
        for (index in 0..7) {
            val cue = recorder.cues.first { it.first == RunTrackerEvent.StepStarted(index) }.second
            assertEquals(RunFormat.stepCue(index, steps), cue)
        }
        assertEquals(
            L("Session complete. Well done. Stop whenever you're ready."),
            recorder.cues.first { it.first == RunTrackerEvent.SessionComplete }.second,
        )
        assertTrue(tracker.snapshot.value.stepsComplete)
        assertNull(tracker.currentStep)
        assertTrue(recorder.kilometres.isNotEmpty())
        assertEquals((1..recorder.kilometres.size).toList(), recorder.kilometres)

        val record = run.finish(save = true)!!
        assertEquals(workout.id, record.workoutId)
        assertEquals(1, store.current.runs.size)
        // The session of the plan is done, linked to the run.
        val done = store.current.workout(workout.id)!!
        assertEquals(WorkoutStatus.Done, done.status)
        assertEquals(record.id, done.runId)
        // One split per step, as run.
        assertEquals(steps.map { it.kind }, record.splits.map { it.kind })
        assertEquals(900, record.splits[0].durationSec)
        assertEquals(600, record.splits[7].durationSec)
        record.splits.filter { it.kind == StepKind.Work }.forEach { split ->
            assertTrue("fast split ${split.distanceM} m", split.distanceM in 400.0..415.0)
            assertEquals(steps[1].paceFast, split.paceFast)
        }
        record.splits.filter { it.kind == StepKind.Recovery }.forEach { split ->
            assertTrue("recovery split ${split.distanceM} m", split.distanceM in 200.0..215.0)
        }
        // The third fast step was on target, the second one far too fast.
        assertTrue(record.splits[5].onTarget)
        assertFalse(record.splits[3].onTarget)
    }

    @Test
    fun paceDriftCueDuringAFastStep() {
        val (_, workout) = intervalSession()
        val track = GpsTrack(seed = 11)
        val run = ActiveRun.obtain(context, store, workout)
        val tracker = run.tracker
        val recorder = Recorder(run)
        run.setPhase(RunFlowPhase.Countdown)
        run.begin()
        // Straight to the first fast step, then run it 40 s/km too fast.
        tracker.skipStep()
        assertEquals(1, tracker.snapshot.value.stepIndex)
        val target = RunFormat.targetPace(tracker.currentStep!!)!!
        var along = 0.0
        val shown = mutableListOf<String>()
        repeat(60) {
            along += 1000.0 / (target - 40)
            TestSupport.idle(1)
            tracker.deliver(track.fix(along))
            shown += run.voice.lastCue.value
        }
        val expected = L("A little too fast. Ease off towards %@ /km.", Fmt.pace(target))
        assertTrue("cues: ${shown.distinct()}", expected in shown)
        // Skip to the cool-down: the remaining fast steps are left out.
        tracker.skipToCooldown()
        assertEquals(StepKind.Cooldown, tracker.currentStep?.kind)
        assertEquals(listOf(0, 1, 7), recorder.events.filterIsInstance<RunTrackerEvent.StepStarted>().map { it.index })
        assertEquals(RunFormat.stepCue(7, tracker.steps), run.voice.lastCue.value)
    }

    @Test
    fun withoutLocationOnlyTheClockRuns() {
        val tracker = RunTracker(context)
        tracker.configure(null)
        tracker.prepare()
        TestSupport.idle(2)
        assertEquals(RunGpsQuality.Unknown, tracker.snapshot.value.gps)
        tracker.authorizationChanged(false)
        assertEquals(RunGpsQuality.Denied, tracker.snapshot.value.gps)
        tracker.start()
        TestSupport.idle(125)
        assertEquals(125, tracker.snapshot.value.elapsed)
        tracker.pause()
        TestSupport.idle(30)
        tracker.resume()
        TestSupport.idle(35)
        tracker.stop()
        val snapshot = tracker.snapshot.value
        assertEquals(RunTrackerState.Finished, snapshot.state)
        assertEquals(160, snapshot.elapsed)
        assertEquals(0.0, snapshot.distance, 0.0)
        assertEquals(0, snapshot.averagePace)
        val record = tracker.makeRecord("Free run", null)
        assertEquals(160, record.durationSec)
        assertEquals(0.0, record.distanceM, 0.0)
        assertTrue(record.route.isEmpty())
        assertTrue(RunAnalysis(record).kmSplits.isEmpty())
        assertNull(RunAnalysis(record).elevationGainM)
        tracker.release()
    }

    @Test
    fun approximateLocationOnlyRecordsTimeWithoutCrashing() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        assertTrue(RunTracker.hasPermission(context))
        assertFalse(RunTracker.hasPreciseLocation(context))
        val track = GpsTrack(seed = 3)
        val run = ActiveRun.obtain(context, store, null)
        val tracker = run.tracker
        tracker.prepare()
        run.setPhase(RunFlowPhase.Countdown)
        run.begin()
        var along = 0.0
        repeat(120) {
            along += 3.0
            TestSupport.idle(1)
            // Approximate fixes are kilometres wide.
            tracker.deliver(track.fix(along, accuracy = 1500.0))
        }
        assertEquals(RunGpsQuality.Poor, tracker.snapshot.value.gps)
        assertEquals(120, tracker.snapshot.value.elapsed)
        assertEquals(0.0, tracker.snapshot.value.distance, 0.0)
        val record = run.finish(save = true)!!
        assertEquals(120, record.durationSec)
        assertEquals(0.0, record.distanceM, 0.0)
        assertTrue(record.route.isEmpty())
        assertEquals(1, store.current.runs.size)
    }

    @Test
    fun backupRoundTripThroughJsonAndDisk() {
        val track = GpsTrack(seed = 5)
        val (_, workout) = intervalSession()
        val run = ActiveRun.obtain(context, store, workout)
        val tracker = run.tracker
        run.setPhase(RunFlowPhase.Countdown)
        run.begin()
        var along = 0.0
        repeat(930) {
            along += 2.2
            TestSupport.idle(1)
            tracker.deliver(track.fix(along))
        }
        val backup = tracker.backup(workout.title, workout.id)!!
        // The warm-up is closed, the fast step in progress counts as run up to now.
        assertEquals(listOf(StepKind.Warmup, StepKind.Work), backup.splits.map { it.kind })
        assertEquals(1, backup.stepIndex)
        assertFalse(backup.isPaused)

        val json = AtalaJson.encodeToString(RunBackup.serializer(), backup)
        assertFalse("dates without fractions (Swift .iso8601)", Regex("\\d\\.\\d+Z").containsMatchIn(json))
        val decoded = AtalaJson.decodeFromString(RunBackup.serializer(), json)
        fun Instant.whole() = Instant.ofEpochSecond(epochSecond)
        assertEquals(
            backup.copy(
                startDate = backup.startDate.whole(), savedAt = backup.savedAt.whole(),
                route = backup.route.map { it.copy(time = it.time.whole()) },
            ),
            decoded,
        )

        // Writes run in order off the main thread: an earlier copy (the run's own, every 15 s) can
        // still be on its way, so wait for this one.
        RunBackupStore.write(context, backup)
        val read = waitForBackup { it == decoded }
        assertEquals(decoded, read)
        val record = read!!.makeRecord()
        assertEquals(workout.id, record.workoutId)
        assertEquals(workout.title, record.title)
        assertEquals(930, record.durationSec)
        assertEquals(backup.route.size, record.route.size)
        assertEquals(backup.splits.size, record.splits.size)

        // Too short to be worth offering.
        assertFalse(backup.copy(activeSeconds = 40.0, distance = 80.0).isWorthSaving)
        assertTrue(backup.copy(activeSeconds = 40.0, distance = 120.0).isWorthSaving)

        // Not recording: no copy.
        run.finish(save = false)
        assertNull(tracker.backup("x", null))
        assertEquals(0, store.current.runs.size)
        assertNull(waitForBackup { it == null })
    }
}
