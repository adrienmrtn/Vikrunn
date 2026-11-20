package com.atala.app.core.account

import com.atala.app.core.l10n.L
import com.atala.app.core.model.Account

/** Messages shown to the user when signing in fails (AuthError in AuthService.swift). */
sealed class AuthError : Exception() {
    data object Cancelled : AuthError()
    data object NotConfigured : AuthError()
    data object Network : AuthError()
    data object InvalidEmail : AuthError()
    data object InvalidCode : AuthError()
    data object TooManyAttempts : AuthError()
    data object SessionExpired : AuthError()
    data object GoogleFailed : AuthError()
    /** The chosen way of signing in is not enabled on the server yet. */
    data object ProviderUnavailable : AuthError()
    data class Server(val text: String) : AuthError()

    /** The sentence shown to the user, in the app's language. */
    val userMessage: String
        get() = when (this) {
            Cancelled -> L("Sign-in was cancelled.")
            NotConfigured -> L("Sign-in isn't available right now. Please try again later.")
            Network -> L("No connection. Check your network and try again.")
            InvalidEmail -> L("That e-mail address doesn't look right.")
            InvalidCode -> L("That code is wrong or has expired. Check it or ask for a new one.")
            TooManyAttempts -> L("Too many attempts. Wait a minute and try again.")
            SessionExpired -> L("Your session has expired. Please sign in again.")
            GoogleFailed -> L("Sign in with Google didn't work. Please try again.")
            ProviderUnavailable -> L("This way of signing in isn't switched on yet. Please use another one.")
            is Server -> text
        }

    override val message: String get() = userMessage
}

/** Result of asking the server for a fresh session. */
internal sealed interface AuthRefreshOutcome {
    data class Refreshed(val account: Account) : AuthRefreshOutcome
    /** The refresh token is dead: the user must sign in again. */
    data object Invalid : AuthRefreshOutcome
    /** No answer (offline, timeout, server error): try again later. */
    data object Unavailable : AuthRefreshOutcome
}

/** How the current account signed in (shown in Profile). Not a secret: kept in the preferences. */
enum class AuthProvider(val rawValue: String) {
    Google("google"),
    Email("email"),
    /** Signed in on an iPhone; only shown if a restored preference says so. */
    Apple("apple");

    val title: String
        get() = when (this) {
            Apple -> "Apple"
            Google -> "Google"
            Email -> L("E-mail code")
        }

    companion object {
        fun from(raw: String?): AuthProvider? = entries.firstOrNull { it.rawValue == raw }
    }
}
