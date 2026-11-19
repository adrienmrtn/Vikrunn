package com.atala.app.ui.onboarding

import androidx.compose.runtime.Composable
import com.atala.app.core.l10n.L

/** Welcome carousel (step `Welcome`). Placeholder. */
@Composable
fun OnboardingWelcomeScreen() {
    OnboardingScaffold(title = L("Your Running Coach"), buttonTitle = L("Get started")) {}
}

/** First name (step `FirstName`), written to `answers.firstName`. Placeholder. */
@Composable
fun OnboardingFirstNameScreen() {
    OnboardingScaffold(title = L("What's your first name?")) {}
}
