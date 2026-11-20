package com.atala.app.core.account

import android.content.Context
import android.content.SharedPreferences
import com.atala.app.core.l10n.L
import com.atala.app.core.model.Account
import com.atala.app.core.store.AppStore
import com.atala.app.core.sync.SyncService
import com.atala.app.core.sync.hasRealAccount
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

/**
 * Accounts through Supabase Auth (GoTrue REST): Google and e-mail one-time code (AuthService.swift).
 * Sign in with Apple exists on the iPhone only. Everything runs on the main thread, like the
 * iPhone's `@MainActor` service; network calls do not block it.
 * Google sign-in, the callback and PKCE are in AuthServiceProviders.kt.
 */
object AuthService {
    private val _sessionExpired = MutableStateFlow(false)

    /**
     * True when the saved session can no longer be refreshed: the user has to sign in again.
     * Local data is kept; nothing is sent to the account meanwhile.
     */
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    internal val scope = MainScope()
    private var refreshTask: Deferred<AuthRefreshOutcome>? = null
    private var prefs: SharedPreferences? = null

    // Web sign-in in progress (AuthServiceProviders.kt).
    internal var webPending: CompletableDeferred<android.net.Uri>? = null
    internal var webLeftApp = false
    internal var webCancelCheck: Job? = null

    /** Called once at launch (AccountServices). */
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("atala", Context.MODE_PRIVATE)
    }

    // E-mail one-time code

    suspend fun sendEmailCode(email: String): Unit = onMain {
        val address = normalized(email)
        if (!isValidEmail(address)) throw AuthError.InvalidEmail
        val payload = buildJsonObject {
            put("email", address)
            put("create_user", true)
        }
        authRequest("/auth/v1/otp", "POST", body = payload.toString())
    }

    suspend fun verifyEmailCode(email: String, code: String): Account = onMain {
        val address = normalized(email)
        val digits = code.filter { it.isDigit() }
        if (!isValidEmail(address)) throw AuthError.InvalidEmail
        // The project normally sends 6 digits; accept longer codes in case the setting changes.
        if (digits.length !in 6..10) throw AuthError.InvalidCode
        val payload = buildJsonObject {
            put("type", "email")
            put("email", address)
            put("token", digits)
        }
        val response = try {
            SupabaseClient.send("/auth/v1/verify", "POST", body = payload.toString())
        } catch (error: Throwable) {
            throw map(error)
        }
        if (!response.isSuccess) {
            val mapped = error(response)
            // A refused code answers 4xx with various wordings: one clear message for all of them.
            if (mapped is AuthError.Server && response.status in 400..499) throw AuthError.InvalidCode
            throw mapped
        }
        val account = account(fromSession = response.json) ?: throw AuthError.InvalidCode
        _sessionExpired.value = false
        rememberProvider(AuthProvider.Email)
        account
    }

    // Guest

    /** Local account: nothing leaves the phone. */
    fun continueAsGuest(): Account =
        Account(userId = "guest-" + UUID.randomUUID().toString().uppercase(), email = null, isGuest = true)

    // Session

    /** A usable access token for the signed-in account, refreshed when needed; null for a guest or offline. */
    suspend fun validAccessToken(store: AppStore): String? = validAccessToken(store, forceRefresh = false)

    /**
     * A usable access token, refreshed shortly before it expires (or right away with [forceRefresh],
     * after the server refused the current one). Null for a guest, when signed out, offline with an
     * expired token, or when the session is dead.
     */
    suspend fun validAccessToken(store: AppStore, forceRefresh: Boolean): String? = onMain {
        val account = store.current.account?.takeIf { !it.isGuest } ?: return@onMain null
        val token = account.accessToken
        val expiresAt = account.expiresAt
        if (!forceRefresh && token != null && expiresAt != null && secondsUntil(expiresAt) > 90) return@onMain token
        val refreshToken = account.refreshToken?.takeIf { it.isNotEmpty() }
        if (refreshToken == null) {
            if (!forceRefresh && token != null && expiresAt == null) return@onMain token
            _sessionExpired.value = true
            return@onMain null
        }
        // Refresh tokens are single-use: concurrent callers share one refresh.
        val task = refreshTask ?: scope.async { refreshSession(refreshToken, account) }.also { refreshTask = it }
        val outcome = task.await()
        if (refreshTask === task) refreshTask = null

        when (outcome) {
            is AuthRefreshOutcome.Refreshed -> {
                val refreshed = outcome.account
                val current = store.current.account
                if (current == null || current.isGuest || current.userId != refreshed.userId) return@onMain null
                if (current.accessToken != refreshed.accessToken || current.refreshToken != refreshed.refreshToken) {
                    store.update { state ->
                        if (state.account?.userId == refreshed.userId) state.copy(account = refreshed) else state
                    }
                }
                _sessionExpired.value = false
                refreshed.accessToken
            }
            AuthRefreshOutcome.Invalid -> {
                // A refresh started with an older session can end after a new sign-in: only the
                // session still in use can be declared dead.
                if (store.current.account?.refreshToken == refreshToken) _sessionExpired.value = true
                null
            }
            AuthRefreshOutcome.Unavailable -> {
                // Offline or server trouble: the current token may still do for a moment.
                if (!forceRefresh && token != null && expiresAt != null && secondsUntil(expiresAt) > 5) token else null
            }
        }
    }

    private suspend fun refreshSession(refreshToken: String, previous: Account): AuthRefreshOutcome {
        val payload = buildJsonObject { put("refresh_token", refreshToken) }
        val response = try {
            SupabaseClient.send(
                "/auth/v1/token", "POST", query = listOf("grant_type" to "refresh_token"),
                body = payload.toString(), timeoutSeconds = 20,
            )
        } catch (_: Throwable) {
            return AuthRefreshOutcome.Unavailable
        }
        if (!response.isSuccess) {
            // 400/401/403: the refresh token is unknown, used or revoked. Anything else may pass.
            return if (response.status in listOf(400, 401, 403)) AuthRefreshOutcome.Invalid else AuthRefreshOutcome.Unavailable
        }
        val json = response.json
        account(fromSession = json)?.let { account ->
            return AuthRefreshOutcome.Refreshed(
                account.copy(
                    email = account.email ?: previous.email,
                    refreshToken = account.refreshToken ?: refreshToken,
                ),
            )
        }
        val token = json.string("access_token") ?: return AuthRefreshOutcome.Unavailable
        return AuthRefreshOutcome.Refreshed(
            previous.copy(
                accessToken = token,
                refreshToken = json.string("refresh_token") ?: refreshToken,
                expiresAt = expiry(json),
            ),
        )
    }

    /**
     * Sends a request as the signed-in user. When the server refuses the token (401), the session is
     * refreshed once and the request sent again.
     */
    suspend fun authorizedSend(
        store: AppStore,
        path: String,
        method: String = "GET",
        query: List<Pair<String, String>> = emptyList(),
        body: String? = null,
        prefer: String? = null,
        timeoutSeconds: Long = 30,
    ): SupabaseResponse = onMain {
        val token = validAccessToken(store)
            ?: throw if (_sessionExpired.value) AuthError.SessionExpired else AuthError.Network
        val first = SupabaseClient.send(path, method, query, body, token, prefer, timeoutSeconds)
        if (first.status != 401) return@onMain first
        val current = store.current.account?.accessToken
        val retryToken = if (current != null && current != token) {
            current // refreshed by another request in the meantime
        } else {
            validAccessToken(store, forceRefresh = true)
        } ?: throw if (_sessionExpired.value) AuthError.SessionExpired else AuthError.Network
        SupabaseClient.send(path, method, query, body, retryToken, prefer, timeoutSeconds)
    }

    /** Called once a sign-in succeeded (any provider). */
    fun noteSignedIn() {
        _sessionExpired.value = false
    }

    /** Called when the app is opened with `atala://auth-callback` (Google sign-in). */
    fun handleCallback(uri: android.net.Uri) = handleWebCallback(uri)

    /** True once the phone holds an account (guest included). */
    fun isSignedIn(store: AppStore): Boolean = store.current.account != null

    /**
     * Saves what is pending, closes the session on the server, then clears the phone (the data
     * stays in the account). The caller asks for confirmation first.
     */
    suspend fun signOut(store: AppStore): Unit = onMain {
        if (store.current.hasRealAccount) {
            SyncService.pushAll(store)
            validAccessToken(store)?.let { token ->
                runCatching {
                    SupabaseClient.send(
                        "/auth/v1/logout", "POST", query = listOf("scope" to "local"),
                        accessToken = token, timeoutSeconds = 10,
                    )
                }
            }
        }
        _sessionExpired.value = false
        rememberProvider(null)
        SyncService.eraseEverything(store)
    }

    /** Deletes the server data and the account when signed in, then erases the phone. */
    suspend fun deleteAccount(store: AppStore): Unit = onMain {
        if (store.current.hasRealAccount) {
            val response = try {
                authorizedSend(store, "/functions/v1/delete-account", "POST", body = "{}", timeoutSeconds = 30)
            } catch (error: Throwable) {
                throw map(error)
            }
            if (!response.isSuccess) {
                if (response.status == 401) throw AuthError.SessionExpired
                throw AuthError.Server(L("We couldn't delete your account. Please try again in a moment."))
            }
        }
        _sessionExpired.value = false
        rememberProvider(null)
        SyncService.eraseEverything(store)
    }

    // How the account signed in

    private const val PROVIDER_KEY = "auth.provider"

    val signedInProvider: AuthProvider? get() = AuthProvider.from(prefs?.getString(PROVIDER_KEY, null))

    fun rememberProvider(provider: AuthProvider?) {
        prefs?.edit()?.apply {
            if (provider != null) putString(PROVIDER_KEY, provider.rawValue) else remove(PROVIDER_KEY)
        }?.apply()
    }

    // Requests and parsing (shared with AuthServiceProviders.kt)

    internal suspend fun authRequest(
        path: String,
        method: String,
        query: List<Pair<String, String>> = emptyList(),
        body: String? = null,
        accessToken: String? = null,
    ): JsonObject {
        val response = try {
            SupabaseClient.send(path, method, query, body, accessToken)
        } catch (error: Throwable) {
            throw map(error)
        }
        if (!response.isSuccess) throw error(response)
        return response.json
    }

    /** A clear message for each kind of refusal of the auth server. */
    fun error(response: SupabaseResponse): AuthError {
        val code = response.errorCode
        val message = response.errorMessage.lowercase()
        if (response.status == 429 || code.startsWith("over_")) return AuthError.TooManyAttempts
        when (code) {
            "otp_expired", "invalid_credentials" -> return AuthError.InvalidCode
            "email_address_invalid" -> return AuthError.InvalidEmail
            "email_address_not_authorized" ->
                // No Sign in with Apple on Android: Google is the other way in.
                return AuthError.Server(L("We can't send a code to this address yet. Please use Google for now."))
            "provider_disabled", "email_provider_disabled", "otp_disabled", "signup_disabled",
            "oauth_provider_not_supported" -> return AuthError.ProviderUnavailable
            "session_not_found", "session_expired", "refresh_token_not_found", "refresh_token_already_used",
            "bad_jwt", "no_authorization", "user_not_found" -> return AuthError.SessionExpired
        }
        if (message.contains("unsupported provider") || message.contains("provider is not enabled")) {
            return AuthError.ProviderUnavailable
        }
        if (response.status >= 500) return AuthError.Server(L("Our server is having a moment. Please try again shortly."))
        // The status and the server's own code: enough to tell which setting is wrong.
        val detail = if (code.isEmpty()) response.status.toString() else "${response.status} $code"
        return AuthError.Server(L("That didn't work (%@). Please try again.", detail))
    }

    fun map(error: Throwable): AuthError = when (error) {
        is AuthError -> error
        is SupabaseError.NotConfigured -> AuthError.NotConfigured
        is SupabaseError.Network -> AuthError.Network
        is SupabaseError.Http -> when (error.status) {
            429 -> AuthError.TooManyAttempts
            401 -> AuthError.SessionExpired
            else -> AuthError.Server(L("That didn't work (%@). Please try again.", error.status))
        }
        else -> AuthError.Network
    }

    /** Builds the account from a GoTrue session (`access_token`, `refresh_token`, `expires_in`, `user`). */
    fun account(fromSession: JsonObject): Account? {
        val token = fromSession.string("access_token") ?: return null
        val user = fromSession["user"] as? JsonObject ?: return null
        val userId = user.string("id") ?: return null
        return Account(
            userId = userId, email = user.string("email"), isGuest = false, accessToken = token,
            refreshToken = fromSession.string("refresh_token"), expiresAt = expiry(fromSession),
        )
    }

    fun expiry(json: JsonObject): Instant {
        // `expires_in` first: it does not depend on the phone's clock being right.
        (json["expires_in"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0 }?.let {
            return Instant.now().plusMillis((it * 1000).toLong())
        }
        (json["expires_at"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 1_000_000_000 }?.let {
            return Instant.ofEpochMilli((it * 1000).toLong())
        }
        return Instant.now().plusSeconds(3600)
    }

    fun normalized(email: String): String = email.trim().lowercase()

    fun isValidEmail(email: String): Boolean {
        val parts = email.split("@")
        if (parts.size != 2 || parts[0].isEmpty() || email.contains(" ")) return false
        val domain = parts[1]
        return domain.contains(".") && !domain.startsWith(".") && !domain.endsWith(".")
    }

    private fun secondsUntil(date: Instant): Double = (date.toEpochMilli() - System.currentTimeMillis()) / 1000.0

    internal suspend fun <T> onMain(block: suspend () -> T): T = withContext(Dispatchers.Main.immediate) { block() }
}
