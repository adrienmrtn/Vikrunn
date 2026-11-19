package com.atala.app.core.account

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import com.atala.app.core.l10n.L
import com.atala.app.core.model.Account
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant

/*
 * Google through the Supabase hosted page (AuthServiceProviders.swift), opened in a Custom Tab.
 * PKCE flow: the callback carries a one-time code, exchanged here for the session; the implicit
 * flow (tokens in the fragment) is still understood.
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
    try {
        val tab = CustomTabsIntent.Builder().setShowTitle(true).build()
        if (context !is Activity) tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        tab.launchUrl(context, Uri.parse(url.toString()))
    } catch (_: Exception) {
        if (webPending === pending) webPending = null
        throw AuthError.GoogleFailed
    }
    val callback = try {
        pending.await()
    } finally {
        if (webPending === pending) webPending = null
        webCancelCheck?.cancel()
    }
    val account = accountFromCallback(callback, verifier)
    noteSignedIn()
    rememberProvider(AuthProvider.Google)
    account
}

/** The app is opened with `atala://auth-callback`: finishes the web sign-in waiting for it. */
internal fun AuthService.handleWebCallback(uri: Uri) {
    if (uri.scheme?.lowercase() != SupabaseConfig.CALLBACK_SCHEME) return
    scope.launch { finishWebSignIn(uri) }
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
 * Turns the callback into an account: exchanges the one-time code (PKCE), or reads the tokens
 * from the fragment when the server answered with the implicit flow.
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
    val token = values["access_token"]
    if (token.isNullOrEmpty()) {
        val failure = (values["error_code"] ?: "") + " " + (values["error_description"] ?: "")
        val text = failure.replace("+", " ").lowercase()
        if (text.contains("provider") && (text.contains("not enabled") || text.contains("unsupported"))) {
            throw AuthError.ProviderUnavailable
        }
        if (values["error"] == "access_denied") throw AuthError.Cancelled
        val reason = failure.replace("+", " ").trim()
        throw googleFailure(reason.ifEmpty { "no code in the callback" })
    }
    val user = authRequest("/auth/v1/user", "GET", accessToken = token)
    val userId = user.string("id") ?: throw AuthError.GoogleFailed
    val seconds = values["expires_in"]?.toDoubleOrNull() ?: 3600.0
    return Account(
        userId = userId, email = user.string("email"), isGuest = false, accessToken = token,
        refreshToken = values["refresh_token"], expiresAt = Instant.now().plusMillis((seconds * 1000).toLong()),
    )
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
