package com.atala.app.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.atala.app.core.l10n.L
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.root.collectAppState

/** Step `Intro`. Placeholder. */
@Composable
fun OnboardingIntroScreen() {
    val state by collectAppState()
    OnboardingScaffold(title = L("Hey %@,", state.firstName), buttonTitle = L("Let's go")) {}
}

// Step `Community`: OnboardingCommunityScreen lives in OnboardingProjectionScreens.kt (next to the reviews).

/** Step `BeyondCoach` (the stars mark the emphasis). Placeholder. */
@Composable
fun OnboardingBeyondCoachScreen() {
    OnboardingScaffold(title = L("Atala is *not just a coach.*").replace("*", "")) {}
}

/** Step `PlanMatters`. Placeholder. */
@Composable
fun OnboardingPlanMattersScreen() {
    OnboardingScaffold(title = L("Having a *clear, precise plan* is essential in running.").replace("*", "")) {}
}

/** Step `Ready`. Placeholder. */
@Composable
fun OnboardingReadyScreen() {
    OnboardingScaffold(title = L("Are you ready?"), buttonTitle = L("I'm ready"), buttonHaptic = HapticKind.Strong) {}
}

// Step `Notifications`: OnboardingNotificationsScreen lives in OnboardingFinalScreens.kt (next to the location permission).
