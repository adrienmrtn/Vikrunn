package com.atala.app.ui

import android.Manifest
import android.app.Application
import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.atala.app.core.l10n.L
import com.atala.app.core.model.Experience
import com.atala.app.core.model.Feeling
import com.atala.app.core.model.Gender
import com.atala.app.core.model.GoalType
import com.atala.app.core.model.Motivation
import com.atala.app.core.model.RunRecord
import com.atala.app.core.model.Weekday
import com.atala.app.core.model.WeeklyVolume
import com.atala.app.core.model.Workout
import com.atala.app.core.model.WorkoutKind
import com.atala.app.core.model.WorkoutStatus
import com.atala.app.core.run.ActiveRun
import com.atala.app.core.run.GpsTrack
import com.atala.app.core.run.RunFlowPhase
import com.atala.app.core.run.RunTrackerState
import com.atala.app.core.run.deliver
import com.atala.app.core.store.AppStore
import com.atala.app.testing.TestSupport
import com.atala.app.ui.onboarding.OnboardingStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/**
 * A beginner's first day with the real app, by what the screens show (in the app's language):
 * the onboarding, Today, Plan, a session, a run with fixes from a fake provider and its summary,
 * then the Coach, Progress and Profile screens. [shoot] is called at each screen worth a picture.
 */
class AppJourney(private val rule: ComposeTestRule, private val store: AppStore, private val shoot: (String) -> Unit = {}) {
    val ui = UiDriver(rule)

    private val step: OnboardingStep? get() = OnboardingStep.fromRawValue(store.current.onboardingStep)

    fun fail(message: String): Nothing =
        throw AssertionError("$message\nstep $step\ntexts ${ui.texts()}\n${ui.tree()}")

    /** Taps the first of [titles] on screen. None yet (a staged reveal still running): the next turn retries. */
    private fun tapAny(vararg titles: String): Boolean = titles.any { ui.tap(it) }

    private val pictures = mapOf(
        OnboardingStep.Welcome to "01_onboarding_welcome",
        OnboardingStep.Language to "02_onboarding_language",
        OnboardingStep.Coach to "03_onboarding_coach",
        OnboardingStep.Hello to "04_onboarding_tap_hello",
    )

    fun onboardAsBeginner() {
        val visits = mutableMapOf<OnboardingStep, Int>()
        var guard = 0
        while (!store.current.hasCompletedOnboarding) {
            ui.settle(1500)
            if (store.current.hasCompletedOnboarding) break
            val current = step ?: fail("unknown step")
            val count = (visits[current] ?: 0) + 1
            visits[current] = count
            println("onboarding: $current ($count)")
            if (++guard > 200 || count > 15) fail("stuck")
            // A picture once the screen has finished appearing.
            if (count == 2 || (count == 1 && current == OnboardingStep.Welcome)) pictures[current]?.let(shoot)
            when (current) {
                OnboardingStep.Welcome -> tapAny(L("Get started"))
                OnboardingStep.Language -> if (count >= 2) tapAny(L("Continue"))
                OnboardingStep.FirstName -> {
                    if (store.current.answers.firstName.isEmpty()) {
                        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Léa")
                        ui.settle(300)
                    }
                    tapAny(L("Continue"))
                }
                OnboardingStep.Coach -> if (count >= 2) tapAny(L("Choose %@", store.current.coach.name))
                OnboardingStep.Hello, OnboardingStep.GoalIntro, OnboardingStep.CoachReply -> {
                    if (count >= 2) ui.tapLabelled(L("Tap to continue"))
                }
                OnboardingStep.Gender -> {
                    ui.tap(Gender.Woman.title)
                    ui.settle(300)
                    tapAny(L("Continue"))
                }
                OnboardingStep.Experience -> {
                    ui.tap(Experience.Starting.title)
                    ui.settle(300)
                    tapAny(L("Continue"))
                }
                OnboardingStep.Volume -> {
                    ui.tap(WeeklyVolume.NotYet.title)
                    ui.settle(300)
                    tapAny(L("Continue"))
                }
                OnboardingStep.Goal -> {
                    ui.tap(GoalType.FiveK.title, substring = true)
                    ui.settle(300)
                    tapAny(L("Continue"))
                }
                OnboardingStep.Race -> {
                    ui.tap(L("Not yet"), substring = true)
                    ui.settle(300)
                    tapAny(L("Continue"))
                }
                OnboardingStep.Why -> {
                    ui.tap(Motivation.Health.title, substring = true)
                    ui.settle(300)
                    tapAny(L("Continue"))
                }
                OnboardingStep.Days -> {
                    if (store.current.answers.runDays.isEmpty()) {
                        for (day in listOf(2, 4, 6)) {
                            ui.tap(Weekday.long(day))
                            ui.settle(200)
                        }
                    }
                    tapAny(L("Continue"))
                }
                OnboardingStep.LongRun -> {
                    ui.tap(Weekday.long(6))
                    ui.settle(300)
                    tapAny(L("Continue"))
                }
                OnboardingStep.Message -> {
                    if (!ui.tap(L("Continue"))) {
                        ui.tapLabelled(L("Sends this question to your coach"))
                        ui.settle(8000)
                    }
                }
                OnboardingStep.Community -> tapAny(L("Join them"), L("Continue"))
                // "Continue without an account" in the sign-in sheet.
                OnboardingStep.SignIn -> if (!tapAny(L("Continue without an account"))) tapAny(L("Continue"))
                OnboardingStep.Loading -> {
                    if (count == 1) {
                        // Half-way through the plan being built.
                        ui.settle(300)
                        shoot("05_onboarding_loading")
                    }
                    ui.settle(4000)
                }
                OnboardingStep.PlanReady -> {
                    // Once every week is lit and the page has reached the finish.
                    if (count == 3) shoot("06_onboarding_plan_ready")
                    if (count >= 3) tapAny(L("Continue"))
                }
                OnboardingStep.Ready -> tapAny(L("I'm ready"))
                OnboardingStep.Commitment -> {
                    rule.onAllNodes(hasContentDescription(L("Signature area"))).onFirst().performTouchInput {
                        swipe(Offset(width * 0.2f, height * 0.6f), Offset(width * 0.8f, height * 0.4f), 300)
                    }
                    ui.settle(300)
                    tapAny(L("I promise"))
                }
                OnboardingStep.Paywall -> {
                    // The close button appears after 7 s (the test clock).
                    ui.settle(7500)
                    shoot("07_onboarding_paywall")
                    ui.tapDescribed(L("Close"))
                }
                OnboardingStep.Notifications -> tapAny(L("Not now"))
                OnboardingStep.Intro -> tapAny(L("Let's go"))
                else -> tapAny(L("Continue"), L("Let's go"), L("That's me"))
            }
        }
        ui.settle(2000)
    }

    /** Today, Plan, the next long run, its ready screen, the run itself and its summary. */
    fun runTheNextLongRun(): RunRecord {
        val plan = store.current.plan ?: fail("no plan")
        assertTrue(ui.showsText(L("Hello, %@", "Léa")))
        shoot("08_today_free")

        assertTrue(ui.tap(L("Plan")))
        ui.settle(1500)
        shoot("09_plan")
        assertTrue(ui.showsText(L("Long run")))
        val longRun: Workout = plan.allWorkouts.first { it.kind == WorkoutKind.Long && it.status == WorkoutStatus.Planned }
        assertTrue(ui.tap(L("Long run"), substring = true))
        ui.settle(1500)
        assertTrue(ui.showsText(longRun.why))
        shoot("10_workout_detail")

        // Precise location allowed; the fixes come from a fake provider through the tracker's callback.
        shadowOf(TestSupport.context as Application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        assertTrue(ui.tap(L("Start")))
        ui.settle(1500)
        val run = ActiveRun.current ?: fail("no run")
        assertEquals(longRun.id, run.workoutId)
        val tracker = run.tracker
        val track = GpsTrack(seed = 21)
        repeat(3) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            tracker.deliver(track.fix(0.0))
        }
        ui.settle(500)
        shoot("11_run_ready")
        assertTrue(ui.tapDescribed(L("Start")) || ui.tap(L("Start")))
        ui.settle(800)
        ui.tapLabelled(L("Start right away"))
        ui.settle(4500)
        assertEquals(RunFlowPhase.Active, run.phase.value)
        assertEquals(RunTrackerState.Running, tracker.snapshot.value.state)
        var along = 0.0
        repeat(400) { second ->
            along += 1000.0 / 330
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            tracker.deliver(track.fix(along))
            if (second % 20 == 0) ui.settle(50)
        }
        ui.settle(800)
        shoot("12_run_live")
        val distance = tracker.snapshot.value.distance
        assertTrue("distance $distance", distance in 1150.0..1290.0)

        assertTrue(ui.tapDescribed(L("Pause")))
        ui.settle(800)
        assertTrue(ui.tap(L("Finish session")) || ui.tapDescribed(L("Finish session")))
        ui.settle(800)
        assertTrue(ui.tap(L("Finish and save")))
        ui.settle(2500)
        assertEquals(1, store.current.runs.size)
        val saved = store.current.runs.single()
        assertEquals(longRun.id, saved.workoutId)
        assertEquals(WorkoutStatus.Done, store.current.workout(longRun.id)!!.status)
        ui.tap(Feeling.Good.title)
        ui.settle(500)
        var tries = 0
        while (!ui.tap(L("See my summary")) && tries++ < 10) ui.settle(800)
        ui.settle(2500)
        assertEquals(Feeling.Good, store.current.runs.single().feeling)
        assertEquals(null, ActiveRun.current)
        shoot("13_run_summary")
        return store.current.runs.single()
    }

    /** Back to the tabs, then the Coach and Progress tabs (locked for a free user) and the profile. */
    fun visitCoachProgressAndProfile() {
        ui.tap(L("Done"))
        ui.settle(1500)
        // The summary closes on the session it belongs to: back to the tabs from there.
        var backs = 0
        while (!ui.has(androidx.compose.ui.test.hasText(L("Coach"))) && backs++ < 3) {
            if (!ui.tapDescribed(L("Back"))) androidx.test.espresso.Espresso.pressBack()
            ui.settle(1500)
        }
        assertTrue(ui.tap(L("Coach")))
        ui.settle(1500)
        assertTrue(ui.showsText(L("Unlock with Atala Pro"), substring = true) || ui.showsText(L("Atala Pro"), substring = true))
        shoot("14_coach_locked")
        assertTrue(ui.tap(L("Progress")))
        ui.settle(1500)
        shoot("15_progress_locked")
        assertTrue(ui.tap(L("Today")))
        ui.settle(1500)
        assertTrue(ui.tapDescribed(L("Profile and settings")))
        ui.settle(1500)
        shoot("16_profile")
    }
}
