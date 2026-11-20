package com.atala.app.core.account

import com.atala.app.core.model.Account
import com.atala.app.core.store.AppStore
import com.atala.app.core.sync.SyncService
import com.atala.app.core.sync.SyncSignInOutcome
import com.atala.app.core.sync.SyncSignInPolicy
import com.atala.app.core.sync.completeSignIn
import com.atala.app.core.sync.hasRealAccount

/**
 * What a screen does after `SignInOptionsSheet` returned an account (AuthRestoreOverlay.swift):
 * guests are kept as they are, real accounts are matched with the phone first (restore, upload,
 * or ask). Onboarding uses [SyncSignInPolicy.PreferAccount], the profile [SyncSignInPolicy.AskIfBoth].
 *
 * - `Restored`, `NeedsChoice`: nothing to do, the root switches to the app (or asks).
 * - `Continued`: carry on (next onboarding step).
 * - `Failed`: show `failure.userMessage` under "We couldn't open your account".
 */
object AuthSignInFlow {
    suspend fun complete(account: Account, store: AppStore, policy: SyncSignInPolicy): SyncSignInOutcome {
        if (account.isGuest) {
            // Nothing leaves the phone. A guest never replaces a real account.
            if (!store.current.hasRealAccount) store.setAccount(account)
            return SyncSignInOutcome.Continued
        }
        AuthService.noteSignedIn()
        return SyncService.completeSignIn(account, store, policy)
    }
}
