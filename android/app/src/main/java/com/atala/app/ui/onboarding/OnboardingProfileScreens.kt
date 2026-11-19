package com.atala.app.ui.onboarding

import androidx.compose.runtime.Composable
import com.atala.app.core.l10n.L

/** Step `Gender`. Placeholder. */
@Composable
fun OnboardingGenderScreen() {
    OnboardingScaffold(title = L("You are…")) {}
}

/** Step `Experience`. Placeholder. */
@Composable
fun OnboardingExperienceScreen() {
    OnboardingScaffold(title = L("How long have you been running?")) {}
}

/** Step `Volume`. Placeholder. */
@Composable
fun OnboardingVolumeScreen() {
    OnboardingScaffold(title = L("How much do you run in a week?")) {}
}

/** Step `Health`, always skipped for now. Placeholder. */
@Composable
fun OnboardingHealthScreen() {
    OnboardingScaffold(title = L("Anything about your health I should know?")) {}
}

/** "Here's what I see" (step `Mirror`), only shown when `OnboardingNavigator.hasRecentResult`. Placeholder. */
@Composable
fun OnboardingMirrorScreen() {
    OnboardingScaffold(buttonTitle = L("That's me"), title = L("Here's what I see")) {}
}
