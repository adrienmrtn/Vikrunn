package com.atala.app.ui.onboarding

import androidx.compose.runtime.Composable
import com.atala.app.core.l10n.L

/** Step `Days`, written to `answers.runDays`. Placeholder. */
@Composable
fun OnboardingDaysScreen() {
    OnboardingScaffold(title = L("When can you run?"), subtitle = L("One ticked day, one session.")) {}
}

/** Step `LongRun`, skipped with one run day. Placeholder. */
@Composable
fun OnboardingLongRunScreen() {
    OnboardingScaffold(title = L("Which day for your long run?")) {}
}

/** Step `Constraints`. Placeholder. */
@Composable
fun OnboardingConstraintsScreen() {
    OnboardingScaffold(title = L("Anything else to know?"), subtitle = L("Optional.")) {}
}

/** Step `Tone`. Placeholder. */
@Composable
fun OnboardingToneScreen() {
    OnboardingScaffold(title = L("Which tone do you prefer?")) {}
}
