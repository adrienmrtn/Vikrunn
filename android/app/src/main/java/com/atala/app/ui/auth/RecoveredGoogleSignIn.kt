package com.atala.app.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atala.app.core.account.AuthService
import com.atala.app.core.account.RecoveredGoogleSignIn
import com.atala.app.core.model.Account
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.rememberHaptics

/**
 * For the screens that show the sign-in options (welcome, onboarding sign-in, profile): takes a
 * Google sign-in that finished while the screen that opened it was gone (activity rebuilt, or the
 * process ended while the Google page was open) and hands it over as if the sheet had returned it,
 * so it goes through the screen's usual `AuthSignInFlow.complete`. A failure comes as its message.
 */
@Composable
fun RecoveredGoogleSignInEffect(onSignedIn: (Account) -> Unit, onFailed: (String) -> Unit) {
    val recovered by AuthService.recoveredGoogleSignIn.collectAsStateWithLifecycle()
    val signedIn by rememberUpdatedState(onSignedIn)
    val failed by rememberUpdatedState(onFailed)
    val haptics = rememberHaptics()
    LaunchedEffect(recovered) {
        if (recovered == null) return@LaunchedEffect
        when (val taken = AuthService.takeRecoveredGoogleSignIn()) {
            is RecoveredGoogleSignIn.SignedIn -> {
                haptics.play(HapticKind.Success)
                signedIn(taken.account)
            }
            is RecoveredGoogleSignIn.Failed -> {
                haptics.play(HapticKind.Error)
                failed(taken.error.userMessage)
            }
            null -> Unit
        }
    }
}
