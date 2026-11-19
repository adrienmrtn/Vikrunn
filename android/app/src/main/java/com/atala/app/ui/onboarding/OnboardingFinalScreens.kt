package com.atala.app.ui.onboarding

import androidx.compose.runtime.Composable
import com.atala.app.core.l10n.L

/** Location permission (step `Permissions`). Placeholder. */
@Composable
fun OnboardingPermissionsScreen() {
    OnboardingScaffold(title = L("Let Atala follow your runs")) {}
}

/** Health import (step `HealthConnect`, Health Connect on Android: the wording is still the iPhone's). Placeholder. */
@Composable
fun OnboardingHealthConnectScreen() {
    OnboardingScaffold(title = L("Connect Apple Health")) {}
}
