package com.atala.app.ui.auth

import androidx.compose.runtime.Composable
import com.atala.app.core.model.Account

/**
 * The sign-in choices (Google, e-mail code, guest), used by the onboarding sign-in step and the
 * profile. The account owner builds it; this placeholder only closes.
 */
@Composable
fun SignInOptionsSheet(onSignedIn: (Account) -> Unit, onDismiss: () -> Unit) {
    onDismiss()
}
