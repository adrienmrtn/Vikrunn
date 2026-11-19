package com.atala.app.ui.paywall

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.atala.app.core.l10n.L
import com.atala.app.ui.components.ScreenPlaceholder
import com.atala.app.ui.root.collectAppState

/**
 * Atala Pro paywall (`PaywallView` in Atala/Features/Onboarding/OnboardingPaywallScreen.swift).
 * Shared by the main app (opened with `nav.onOpenPaywall`) and the onboarding's paywall step.
 *
 * @param closeDelay seconds before the close button appears (7 in the onboarding, 0 elsewhere).
 * @param onFinished closed, subscribed or not.
 */
@Composable
fun PaywallScreen(closeDelay: Double = 0.0, onFinished: () -> Unit) {
    val state by collectAppState()
    ScreenPlaceholder(
        title = L("%@, finally get the ", state.firstName) + L("guidance") + L(" you've been looking for."),
        onBack = onFinished,
    )
}
