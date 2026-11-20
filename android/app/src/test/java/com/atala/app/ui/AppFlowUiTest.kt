package com.atala.app.ui

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.atala.app.AtalaApplication
import com.atala.app.MainActivity
import com.atala.app.core.model.ProAccess
import com.atala.app.core.run.ActiveRun
import com.atala.app.core.run.RunAnalysis
import com.atala.app.testing.TestSupport
import com.atala.app.ui.onboarding.OnboardingNavigator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The real app (MainActivity, AtalaApplication and its store) driven like a user: the whole
 * onboarding as a beginner who skips the sign-in and closes the paywall, then Today, Plan, a
 * session, a run with fake GPS fixes, its summary, and the Coach, Progress and Profile screens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class AppFlowUiTest {
    @get:Rule
    val rule = createEmptyComposeRule()
    private var clock = 0L
    private var scenario: ActivityScenario<MainActivity>? = null
    private val app get() = TestSupport.context as AtalaApplication

    @Before
    fun setUp() {
        TestSupport.blockVoiceDownloads(app)
        ProAccess.setPro(false)
        // Each screen change a second after the previous one: the double-tap guard never fires.
        OnboardingNavigator.nanoTime = { clock += 1_000_000_000L; clock }
    }

    @After
    fun tearDown() {
        OnboardingNavigator.nanoTime = System::nanoTime
        ActiveRun.current?.let(ActiveRun::close)
        scenario?.close()
    }

    @Test
    fun beginnerFromTheWelcomeScreenToTheSummaryOfAFirstRun() {
        val store = app.store
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val journey = AppJourney(rule, store)
        journey.ui.settle(3000)
        journey.onboardAsBeginner()

        val state = store.current
        assertTrue(state.hasCompletedOnboarding)
        assertTrue(state.account!!.isGuest)
        assertEquals("Léa", state.answers.firstName)
        assertEquals(listOf(2, 4, 6), state.answers.runDays)
        assertEquals(8, state.plan!!.weeks.size)

        val run = journey.runTheNextLongRun()
        assertTrue(run.route.size > 100)
        // About 1.2 km: one full kilometre and the rest.
        assertEquals(listOf(true, false), RunAnalysis(run).kmSplits.map { !it.isPartial })
        // Free user: the summary keeps the splits for Pro, behind a teaser that opens the paywall.
        assertTrue(journey.ui.showsDescription("Splits, charts and heart rate: see the detail of every run with Atala Pro"))

        journey.visitCoachProgressAndProfile()
        assertTrue(journey.ui.showsText("Léa", substring = true))
    }
}
