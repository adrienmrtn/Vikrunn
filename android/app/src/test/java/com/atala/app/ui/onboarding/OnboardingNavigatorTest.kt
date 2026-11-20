package com.atala.app.ui.onboarding

import android.app.Application
import com.atala.app.core.account.AuthService
import com.atala.app.core.model.CoachTone
import com.atala.app.core.model.Experience
import com.atala.app.core.model.Gender
import com.atala.app.core.model.GoalType
import com.atala.app.core.model.Motivation
import com.atala.app.core.model.PlanCalendar
import com.atala.app.core.model.RecentResult
import com.atala.app.core.model.WeeklyVolume
import com.atala.app.core.model.WorkoutKind
import com.atala.app.core.plan.PlanEngine
import com.atala.app.core.store.AppStore
import com.atala.app.testing.TestSupport
import kotlinx.coroutines.MainScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import com.atala.app.ui.onboarding.OnboardingStep as S

/** The entry flow's order, skip rules, back, restore and end (OnboardingFlowView.swift's navigator). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class OnboardingNavigatorTest {
    private var clock = 0L
    private lateinit var store: AppStore

    @Before
    fun setUp() {
        TestSupport.reset()
        // Each move a second after the previous one: the double-tap guard never fires.
        OnboardingNavigator.nanoTime = { clock += 1_000_000_000L; clock }
        store = AppStore(null, MainScope())
    }

    @After
    fun tearDown() {
        OnboardingNavigator.nanoTime = System::nanoTime
    }

    /** Answers each screen as a user would, presses Continue, and returns the screens shown. */
    private fun walk(nav: OnboardingNavigator, answer: (S) -> Unit): List<S> {
        val shown = mutableListOf(nav.step)
        while (!store.current.hasCompletedOnboarding) {
            answer(nav.step)
            nav.next()
            if (!store.current.hasCompletedOnboarding) shown += nav.step
            assertTrue("loop", shown.size < 60)
        }
        return shown
    }

    private val beginner: (S) -> Unit = { step ->
        when (step) {
            S.FirstName -> store.updateAnswers { it.copy(firstName = "Léa") }
            S.Coach -> store.updateAnswers { it.copy(coachId = 1) }
            S.Gender -> store.updateAnswers { it.copy(gender = Gender.Woman) }
            S.Experience -> store.updateAnswers { it.copy(experience = Experience.Starting) }
            S.Volume -> store.updateAnswers { it.copy(weeklyVolume = WeeklyVolume.NotYet) }
            S.Goal -> store.updateAnswers { it.copy(goal = GoalType.FiveK) }
            S.Race -> store.updateAnswers { it.copy(hasRace = false) }
            S.Why -> store.updateAnswers { it.copy(motivation = Motivation.Health) }
            S.Days -> store.updateAnswers { it.copy(runDays = listOf(2, 4, 6)) }
            S.LongRun -> store.updateAnswers { it.copy(longRunDay = 6) }
            S.Tone -> store.updateAnswers { it.copy(tone = CoachTone.Kind) }
            // "Continue without an account".
            S.SignIn -> store.setAccount(AuthService.continueAsGuest())
            S.Loading -> store.generatePlan()
            else -> Unit
        }
    }

    @Test
    fun beginnerWalksTheWholeFlowAndGetsAPlan() {
        val nav = OnboardingNavigator(store).apply { restore() }
        assertEquals(S.Welcome, nav.step)
        val shown = walk(nav, beginner)
        assertEquals(
            listOf(
                S.Welcome, S.Language, S.FirstName, S.Intro, S.Coach, S.Hello, S.Gender, S.Experience, S.Volume,
                S.LevelIntro, S.GoalIntro, S.Goal, S.Race, S.Why, S.CoachReply, S.Consistency,
                S.Days, S.LongRun, S.Constraints, S.Message, S.Tone, S.Projection, S.Reviews, S.Community,
                S.SignIn, S.Loading, S.PlanReady, S.Ready, S.Commitment, S.BeyondCoach, S.PlanMatters, S.Trial, S.Timeline,
                S.Paywall, S.Permissions, S.Notifications,
            ),
            shown,
        )
        // Each step was saved as it was reached; the end builds the plan and opens the app.
        assertEquals(S.Notifications.rawValue, store.current.onboardingStep)
        assertTrue(store.current.hasCompletedOnboarding)
        val plan = store.current.plan
        assertNotNull(plan)
        assertEquals(GoalType.FiveK, plan!!.goal)
        assertEquals(PlanEngine.MAX_WEEKS, plan.weeks.size)
        assertEquals(1, store.current.coach.id)
        assertTrue(store.current.account!!.isGuest)
        assertTrue(plan.allWorkouts.filter { it.kind.isRun }.all { PlanCalendar.mondayIndex(it.date) in listOf(2, 4, 6) })
    }

    @Test
    fun runnerWithARecentTimeSeesTheMirrorAndTheTarget() {
        val nav = OnboardingNavigator(store).apply { restore() }
        val race = Instant.now().plus(Duration.ofDays(40))
        val shown = walk(nav) { step ->
            when (step) {
                S.FirstName -> store.updateAnswers { it.copy(firstName = "Tom") }
                S.Experience -> store.updateAnswers { it.copy(experience = Experience.SixMonthsToTwoYears) }
                S.Volume -> store.updateAnswers { it.copy(weeklyVolume = WeeklyVolume.From10To25) }
                S.Recent -> store.updateAnswers { it.copy(recentResult = RecentResult(10.0, 3000)) }
                S.Goal -> store.updateAnswers { it.copy(goal = GoalType.TenK) }
                S.Race -> store.updateAnswers { it.copy(hasRace = true, raceDate = race) }
                S.Target -> store.updateAnswers { it.copy(targetSeconds = 2880) }
                S.Why -> store.updateAnswers { it.copy(motivation = Motivation.Record) }
                S.Days -> store.updateAnswers { it.copy(runDays = listOf(1, 3, 5, 7), longRunDay = 7) }
                S.SignIn -> store.setAccount(AuthService.continueAsGuest())
                else -> Unit
            }
        }
        assertEquals(
            listOf(
                S.Welcome, S.Language, S.FirstName, S.Intro, S.Coach, S.Hello, S.Gender, S.Experience, S.Volume,
                S.Recent, S.Mirror, S.LevelFocus, S.GoalIntro, S.Goal, S.Race, S.Target, S.Why, S.CoachReply, S.Consistency,
                S.Days, S.LongRun, S.Constraints, S.Message, S.Tone, S.Projection, S.Reviews, S.Community,
                S.SignIn, S.Loading, S.PlanReady, S.Ready, S.Commitment, S.BeyondCoach, S.PlanMatters, S.Trial, S.Timeline,
                S.Paywall, S.Permissions, S.Notifications,
            ),
            shown,
        )
        // No Loading screen ran here: finishing builds the plan itself, up to the race.
        val plan = store.current.plan!!
        assertEquals(GoalType.TenK, plan.goal)
        assertEquals(2880, plan.targetSeconds)
        assertEquals(WorkoutKind.Race, plan.allWorkouts.last().kind)
        assertTrue(plan.weeks.size in 6..7)
    }

    @Test
    fun skipRulesFollowTheAnswers() {
        val nav = OnboardingNavigator(store)
        assertTrue(nav.isSkipped(S.Health))
        assertTrue(nav.isSkipped(S.LevelRisk))
        assertTrue(nav.isSkipped(S.HealthConnect))
        assertTrue(nav.isSkipped(S.Mirror))
        // No goal yet: the race and target screens are shown.
        assertFalse(nav.isSkipped(S.Race))
        store.updateAnswers { it.copy(goal = GoalType.Shape) }
        assertTrue(nav.isSkipped(S.Race))
        assertTrue(nav.isSkipped(S.Target))
        store.updateAnswers { it.copy(goal = GoalType.Half, weeklyVolume = WeeklyVolume.NotYet) }
        assertFalse(nav.isSkipped(S.Race))
        assertTrue("no target for someone who does not run yet", nav.isSkipped(S.Target))
        assertTrue(nav.isSkipped(S.Recent))
        assertFalse(nav.isSkipped(S.LevelIntro))
        assertTrue(nav.isSkipped(S.LevelFocus))
        store.updateAnswers { it.copy(weeklyVolume = WeeklyVolume.Over25, experience = Experience.OverTwoYears, recentResult = RecentResult(5.0, 1200)) }
        assertFalse(nav.isSkipped(S.Mirror))
        assertFalse(nav.isSkipped(S.Target))
        assertTrue(nav.isSkipped(S.LevelIntro))
        assertFalse(nav.isSkipped(S.LevelFocus))
        store.updateAnswers { it.copy(runDays = listOf(3)) }
        assertTrue(nav.isSkipped(S.LongRun))
        assertFalse(nav.isSkipped(S.SignIn))
        store.setAccount(AuthService.continueAsGuest())
        assertTrue(nav.isSkipped(S.SignIn))
    }

    @Test
    fun backSkipsWhatWasSkippedAndStopsWhereThereIsNoWayBack() {
        val nav = OnboardingNavigator(store).apply { restore() }
        nav.back()
        assertEquals("no way back from the welcome screen", S.Welcome, nav.step)
        // Walk the beginner up to the race question.
        while (nav.step != S.Race) {
            beginner(nav.step)
            nav.next()
        }
        nav.back()
        assertEquals(S.Goal, nav.step)
        nav.back()
        assertEquals(S.GoalIntro, nav.step)
        nav.back()
        // Over LevelFocus, Mirror, Health and Recent, all ruled out for a beginner.
        assertEquals(S.LevelIntro, nav.step)
        nav.back()
        assertEquals(S.Volume, nav.step)
        // Changing the answer changes the way forward.
        store.updateAnswers { it.copy(weeklyVolume = WeeklyVolume.Under10, experience = Experience.UnderSixMonths) }
        nav.next()
        assertEquals(S.Recent, nav.step)
        // Loading, the plan and the paywall have no way back.
        for (step in listOf(S.Loading, S.PlanReady, S.Paywall, S.Permissions, S.Notifications)) {
            nav.go(step)
            nav.back()
            assertEquals(step, nav.step)
        }
        nav.go(S.Ready)
        nav.back()
        assertEquals(S.PlanReady, nav.step)
    }

    @Test
    fun aSecondTapWithinTheTransitionIsIgnored() {
        val nav = OnboardingNavigator(store).apply { restore() }
        OnboardingNavigator.nanoTime = { 5_000_000_000L }
        nav.next()
        assertEquals(S.Language, nav.step)
        nav.next()
        assertEquals(S.Language, nav.step)
        var now = 5_000_000_000L
        OnboardingNavigator.nanoTime = { now }
        now += 310_000_000L
        nav.next()
        assertEquals(S.FirstName, nav.step)
    }

    @Test
    fun restoreResumesOnTheSavedStep() {
        // A saved step the answers now rule out resumes on the next screen shown.
        store.updateAnswers { it.copy(weeklyVolume = WeeklyVolume.NotYet, experience = Experience.Starting, goal = GoalType.FiveK, targetSeconds = 1500) }
        store.setOnboardingStep(S.Target.rawValue)
        var nav = OnboardingNavigator(store).apply { restore() }
        assertEquals(S.Why, nav.step)
        assertNull("no target kept for someone who does not run yet", store.current.answers.targetSeconds)

        // Steps out of the flow.
        store.setOnboardingStep(S.LevelRisk.rawValue)
        nav = OnboardingNavigator(store).apply { restore() }
        assertEquals(S.GoalIntro, nav.step)
        store.setOnboardingStep(S.Reminder.rawValue)
        nav = OnboardingNavigator(store).apply { restore() }
        assertEquals(S.Paywall, nav.step)

        // Past the loading screen, a plan built on another day is rebuilt from today.
        val old = PlanEngine.generate(store.current.answers, start = Instant.now().minus(Duration.ofDays(3)))
        store.update { it.copy(plan = old) }
        store.setOnboardingStep(S.Commitment.rawValue)
        nav = OnboardingNavigator(store).apply { restore() }
        assertEquals(S.Commitment, nav.step)
        assertTrue(PlanCalendar.isToday(store.current.plan!!.createdAt))
        // A plan of today is kept.
        val today = store.current.plan
        OnboardingNavigator(store).restore()
        assertTrue(today === store.current.plan)

        // An unknown raw value starts again from the welcome screen.
        store.setOnboardingStep(999)
        assertEquals(S.Welcome, OnboardingNavigator(store).apply { restore() }.step)
    }

    @Test
    fun progressRunsFromTheFirstNameToTheReviews() {
        store.updateAnswers { it.copy(experience = Experience.Starting, weeklyVolume = WeeklyVolume.NotYet, goal = GoalType.Regular) }
        val nav = OnboardingNavigator(store)
        assertEquals(0.05, nav.progress(S.FirstName)!!, 1e-9)
        assertEquals(1.0, nav.progress(S.Reviews)!!, 1e-9)
        assertNull(nav.progress(S.Hello))
        assertNull(nav.progress(S.Welcome))
        assertNull(nav.progress(S.Loading))
        val bar = S.order.filter { it.showsProgress && !nav.isSkipped(it) }.map { nav.barProgress(it) }
        assertEquals(bar.sorted(), bar)
        assertEquals(bar.size, bar.distinct().size)
        // Screens without a bar keep the value of the last one that had it.
        assertEquals(nav.barProgress(S.Coach), nav.barProgress(S.Hello), 1e-9)
        assertEquals(1.0, nav.barProgress(S.Loading), 1e-9)
        assertEquals(0.05, nav.barProgress(S.Welcome), 1e-9)
    }
}
