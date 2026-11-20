package com.atala.app.ui

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.atala.app.AtalaApplication
import com.atala.app.MainActivity
import com.atala.app.core.l10n.AppLanguage
import com.atala.app.core.model.ProAccess
import com.atala.app.core.run.ActiveRun
import com.atala.app.testing.TestSupport
import com.atala.app.ui.onboarding.OnboardingNavigator
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pictures of the real app along a beginner's first day (see [AppJourney]), in French and in Arabic
 * (right to left), on a small phone (360 × 640 dp) and a regular one (411 × 891 dp).
 * Written to build/outputs/roborazzi/<language>_<size>_<screen>.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {
    @get:Rule
    val rule = createEmptyComposeRule()
    private var clock = 0L
    private var scenario: ActivityScenario<MainActivity>? = null
    private val app get() = TestSupport.context as AtalaApplication

    @Before
    fun setUp() {
        TestSupport.blockVoiceDownloads(app)
        ProAccess.setPro(false)
        OnboardingNavigator.nanoTime = { clock += 1_000_000_000L; clock }
    }

    @After
    fun tearDown() {
        OnboardingNavigator.nanoTime = System::nanoTime
        ActiveRun.current?.let(ActiveRun::close)
        scenario?.close()
        AppLanguage.chosen = null
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun journey(language: String, size: String) {
        AppLanguage.choose(language)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val journey = AppJourney(rule, app.store) { name ->
            captureScreenRoboImage(
                "build/outputs/roborazzi/${language}_${size}_$name.png",
                roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
            )
        }
        journey.ui.settle(3000)
        journey.onboardAsBeginner()
        journey.runTheNextLongRun()
        journey.visitCoachProgressAndProfile()
    }

    @Test
    @Config(qualifiers = SMALL)
    fun frenchSmallPhone() = journey("fr", "small")

    @Test
    @Config(qualifiers = NORMAL)
    fun frenchNormalPhone() = journey("fr", "normal")

    @Test
    @Config(qualifiers = SMALL)
    fun arabicSmallPhone() = journey("ar", "small")

    @Test
    @Config(qualifiers = NORMAL)
    fun arabicNormalPhone() = journey("ar", "normal")

    companion object {
        const val SMALL = "w360dp-h640dp-xhdpi"
        const val NORMAL = "w411dp-h891dp-xhdpi"
    }
}
