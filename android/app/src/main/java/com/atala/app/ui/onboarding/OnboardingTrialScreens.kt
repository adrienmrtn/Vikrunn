package com.atala.app.ui.onboarding

import androidx.compose.runtime.Composable
import com.atala.app.core.l10n.L

/** Step `Trial`. Placeholder. */
@Composable
fun OnboardingTrialScreen() {
    OnboardingScaffold(title = L("That's why we really want you to try Atala Pro *for free.*").replace("*", "")) {}
}

/** Step `Timeline`. Placeholder. */
@Composable
fun OnboardingTimelineScreen() {
    OnboardingScaffold(title = L("How your free trial works")) {}
}
