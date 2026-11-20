package com.atala.app.core.account

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import com.atala.app.core.l10n.L
import com.atala.app.core.model.Account
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.security.SecureRandom

/*
 * Google through the Supabase hosted page (AuthServiceProviders.swift), opened in a Custom Tab.
 * PKCE flow: the callback carries a one-time code, exchanged here for the session with the
 * verifier. The verifier is kept on disk while the page is open, so a callback that arrives after
 * the activity was rebuilt or the process ended still finishes the sign-in
 * ([AuthService.recoveredGoogleSignIn]). Tokens in the fragment (implicit flow) are never
 * accepted: every request asks for PKCE, so such a callback was not sent in answer to ours.
 */

/** Google sign-in. [context] should be the activity on screen. */
suspend fun AuthService.signInWithGoogle(context: Context): Account = onMain {
    val verifier = randomNonce(64)
    val url = if (SupabaseConfig.isConfigured) {
        SupabaseClient.url(
            "/auth/v1/authorize",
            listOf(
                "provider" to "google",
                "redirect_to" to SupabaseConfig.REDIRECT_URL,
                "code_challenge" to pkceChallenge(verifier),
                "code_challenge_method" to "s256",
            ),
        )
    } else {
        null
    } ?: throw AuthError.NotConfigured

    // Only one web sign-in at a time.
    finishWebSignIn(null)

    val pending = CompletableDeferred<Uri>()
    webPending = pending
    webLeftApp = false
    // Saved before the page opens: the callback may come back to a rebuilt activity or a new process.
    savePendingGoogleVerifier(verifier)
    try {
        val tab = CustomTabsIntent.Builder().setShowTitle(true).build()
        if (context !is Activity) tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        tab.launchUrl(context, Uri.parse(url.toString()))
    } catch (_: Exception) {
        if (webPending === pending) webPending = null
        clearPendingGoogleVerifier()
        throw AuthError.GoogleFailed
    }
    val callback = try {
        pending.await()
    } catch (cancelled: CancellationException) {
        // The screen that opened the page is gone (activity rebuilt): the saved verifier stays, so
        // the callback finishes the sign-in without it (handleWebCallback).
        throw cancelled
    } catch (error: Throwable) {
        clearPendingGoogleVerifier()
        throw error
    } finally {
        if (webPending === pending) webPending = null
        webCancelCheck?.cancel()
    }
    clearPendingGoogleVerifier()
    val account = accountFromCallback(callback, verifier)
    noteSignedIn()
    rememberProvider(AuthProvider.Google)
    account
}

/**
 * The app is opened with `atala://auth-callback`: finishes the web sign-in waiting for it. With
 * nobody waiting (the activity was rebuilt, or the process ended, while the page was open), the
 * code is exchanged with the saved verifier and the account handed to the screen showing the
 * sign-in ([AuthService.recoveredGoogleSignIn]). A callback with no sign-in of ours pending is ignored.
 */
internal fun AuthService.handleWebCallback(uri: Uri) {
    if (uri.scheme?.lowercase() != SupabaseConfig.CALLBACK_SCHEME) return
    if (webPending != null) {
        scope.launch { finishWebSignIn(uri) }
        return
    }
    val verifier = pendingGoogleVerifier() ?: return
    clearPendingGoogleVerifier()
    scope.launch {
        val result = try {
            val account = accountFromCallback(uri, verifier)
            noteSignedIn()
            rememberProvider(AuthProvider.Google)
            RecoveredGoogleSignIn.SignedIn(account)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val mapped = map(error)
            if (mapped == AuthError.Cancelled) return@launch
            RecoveredGoogleSignIn.Failed(mapped)
        }
        publishRecoveredGoogleSignIn(result)
    }
}

/** The activity goes behind the sign-in page. */
internal fun AuthService.noteActivityPaused() {
    if (webPending != null) webLeftApp = true
}

/**
 * The activity is back. With no callback, the page was closed: the sign-in is cancelled. The
 * callback, when there is one, arrives just before (onNewIntent), so a short wait is enough.
 */
internal fun AuthService.noteActivityResumed() {
    val pending = webPending ?: return
    if (!webLeftApp) return
    webCancelCheck?.cancel()
    webCancelCheck = scope.launch {
        delay(1200)
        if (webPending === pending && !pending.isCompleted) {
            webPending = null
            pending.completeExceptionally(AuthError.Cancelled)
        }
    }
}

/** Resumes the pending web sign-in exactly once: with the callback, or cancelled when null. */
internal fun AuthService.finishWebSignIn(callback: Uri?) {
    val pending = webPending ?: return
    webPending = null
    webCancelCheck?.cancel()
    if (callback != null) pending.complete(callback) else pending.completeExceptionally(AuthError.Cancelled)
}

/**
 * Turns the callback into an account by exchanging the one-time code (PKCE) with [verifier].
 * A callback with tokens but no code is refused: our request asked for PKCE, so tokens in the
 * fragment did not come from it (a forged link could otherwise sign the phone into another account).
 */
private suspend fun AuthService.accountFromCallback(url: Uri, verifier: String): Account {
    val values = callbackValues(url)
    val code = values["code"]
    if (!code.isNullOrEmpty()) {
        val payload = buildJsonObject {
            put("auth_code", code)
            put("code_verifier", verifier)
        }
        val json = try {
            authRequest("/auth/v1/token", "POST", query = listOf("grant_type" to "pkce"), body = payload.toString())
        } catch (error: AuthError) {
            // Keeps the server's reason next to the message: it tells which setting is wrong.
            if (error is AuthError.Server) throw googleFailure("token: " + error.text)
            throw error
        }
        return account(fromSession = json) ?: throw AuthError.GoogleFailed
    }
    if (!values["access_token"].isNullOrEmpty()) throw googleFailure("unexpected tokens in the callback")
    val failure = (values["error_code"] ?: "") + " " + (values["error_description"] ?: "")
    val text = failure.replace("+", " ").lowercase()
    if (text.contains("provider") && (text.contains("not enabled") || text.contains("unsupported"))) {
        throw AuthError.ProviderUnavailable
    }
    if (values["error"] == "access_denied") throw AuthError.Cancelled
    val reason = failure.replace("+", " ").trim()
    throw googleFailure(reason.ifEmpty { "no code in the callback" })
}

/** "Sign in with Google didn't work", followed by what the server said. */
private fun googleFailure(detail: String): AuthError =
    AuthError.Server(L("Sign in with Google didn't work. Please try again.") + " (" + detail.take(160) + ")")

/** Key/value pairs of the URL query (errors) and fragment (tokens). */
internal fun callbackValues(url: Uri): Map<String, String> {
    val values = mutableMapOf<String, String>()
    for (part in listOf(url.encodedQuery, url.encodedFragment)) {
        if (part.isNullOrEmpty()) continue
        for (pair in part.split("&")) {
            if (pair.isEmpty()) continue
            val index = pair.indexOf('=')
            if (index < 0) continue
            values[Uri.decode(pair.substring(0, index))] = Uri.decode(pair.substring(index + 1))
        }
    }
    return values
}

// Nonce

private val nonceCharset = "0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._"

internal fun randomNonce(length: Int = 32): String {
    val random = SecureRandom()
    return buildString { repeat(length) { append(nonceCharset[random.nextInt(nonceCharset.length)]) } }
}

/** PKCE challenge: SHA-256 of the verifier, base64url without padding. */
internal fun pkceChallenge(verifier: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.UTF_8))
    return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}
