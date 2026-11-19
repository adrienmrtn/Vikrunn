package com.atala.app.ui.onboarding

import androidx.compose.runtime.Composable
import com.atala.app.ui.paywall.PaywallScreen

/** Step `Paywall`: the shared paywall, its close button held back 7 s; no way back from it. */
@Composable
fun OnboardingPaywallScreen() {
    val nav = LocalOnboardingNavigator.current
    PaywallScreen(closeDelay = 7.0, onFinished = nav::next)
}
