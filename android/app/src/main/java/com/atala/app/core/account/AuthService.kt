package com.atala.app.core.account

import com.atala.app.core.model.Account
import com.atala.app.core.store.AppStore

/**
 * Accounts through Supabase Auth (Apple is iPhone only; Google and e-mail code here).
 * Shared entry points: other features call only these. The account owner fills them in.
 */
object AuthService {
    /** A usable access token for the signed-in account, refreshed when needed; null for a guest or offline. */
    suspend fun validAccessToken(store: AppStore): String? = store.current.account?.accessToken

    /** Saves what is pending, closes the session on the server, then clears the phone. */
    suspend fun signOut(store: AppStore) {
        store.signOut()
    }

    /** Called when the app is opened with `atala://auth-callback` (Google sign-in). */
    fun handleCallback(uri: android.net.Uri) {}

    /** True once the phone holds an account (guest included). */
    fun isSignedIn(store: AppStore): Boolean = store.current.account != null

    @Suppress("unused")
    private fun keep(account: Account) = account
}
