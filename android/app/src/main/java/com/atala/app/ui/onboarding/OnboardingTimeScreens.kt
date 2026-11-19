package com.atala.app.ui.onboarding

import androidx.compose.runtime.Composable
import com.atala.app.core.l10n.L

/** Recent result (step `Recent`), skipped for people who do not run yet. Placeholder. */
@Composable
fun OnboardingRecentScreen() {
    OnboardingScaffold(title = L("A recent result?"), subtitle = L("A race or an all-out run from the last 3 months.")) {}
}

/** Target time (step `Target`), skipped for open-ended goals and people who do not run yet. Placeholder. */
@Composable
fun OnboardingTargetScreen() {
    OnboardingScaffold(title = L("What time are you aiming for?")) {}
}
