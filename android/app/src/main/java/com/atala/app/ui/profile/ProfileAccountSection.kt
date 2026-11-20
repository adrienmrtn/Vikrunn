package com.atala.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atala.app.BuildConfig
import com.atala.app.R
import com.atala.app.core.account.AuthService
import com.atala.app.core.account.AuthSignInFlow
import com.atala.app.ui.auth.RecoveredGoogleSignInEffect
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.Account
import com.atala.app.core.model.PlanCalendar
import com.atala.app.core.store.AppState
import com.atala.app.core.sync.SyncActivity
import com.atala.app.core.sync.SyncFailure
import com.atala.app.core.sync.SyncOutcome
import com.atala.app.core.sync.SyncService
import com.atala.app.core.sync.SyncSignInOutcome
import com.atala.app.core.sync.SyncSignInPolicy
import com.atala.app.core.sync.hasRealAccount
import com.atala.app.ui.auth.SignInOptionsSheet
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.root.LocalAppStore
import com.atala.app.ui.theme.Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

private enum class AccountBusy { SigningIn, Syncing, SigningOut, Deleting }

private sealed interface AccountAlert {
    data object ConfirmSignOut : AccountAlert
    data object SignOutUnsaved : AccountAlert
    data object ConfirmDelete : AccountAlert
    data class Error(val message: String) : AccountAlert
}

/** Account: who is signed in and how, last sync and "Sync now", sign in (guests), sign out, deletion. */
@Composable
internal fun ProfileAccountSection(state: AppState) {
    val store = LocalAppStore.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val sessionExpired by AuthService.sessionExpired.collectAsStateWithLifecycle()
    val activity by SyncService.activity.collectAsStateWithLifecycle()
    var showSignIn by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<AccountBusy?>(null) }
    var alert by remember { mutableStateOf<AccountAlert?>(null) }
    var syncNote by remember { mutableStateOf<String?>(null) }
    val isSignedIn = state.hasRealAccount

    fun finishSignIn(account: Account) {
        if (busy != null) return
        // Signing in again after an expired session must be the same account: the phone holds its data.
        val current = state.account
        if (current != null && !current.isGuest && !account.isGuest && current.userId != account.userId) {
            scope.launch {
                // Let the sign-in sheet close before the alert appears.
                delay(500)
                haptics.play(HapticKind.Error)
                alert = AccountAlert.Error(L("That's a different account. Sign out first to switch accounts."))
            }
            return
        }
        busy = AccountBusy.SigningIn
        scope.launch {
            // A guest's plan and runs are uploaded; if the account already has some, the app asks which to keep.
            val outcome = AuthSignInFlow.complete(account, store, SyncSignInPolicy.AskIfBoth)
            busy = null
            if (outcome is SyncSignInOutcome.Failed) {
                haptics.play(HapticKind.Error)
                alert = AccountAlert.Error(outcome.failure.userMessage)
            }
        }
    }

    fun syncNow() {
        if (busy != null) return
        haptics.tap()
        busy = AccountBusy.Syncing
        syncNote = null
        scope.launch {
            val outcome = SyncService.syncNow(store)
            busy = null
            when (outcome) {
                SyncOutcome.Done -> {
                    haptics.play(HapticKind.Success)
                    syncNote = null
                }
                SyncOutcome.NotSignedIn -> syncNote = if (AuthService.sessionExpired.value) L("Sign in again to sync.") else null
                is SyncOutcome.Failed -> {
                    haptics.play(HapticKind.Error)
                    syncNote = if (outcome.failure == SyncFailure.Offline) {
                        L("No connection. Your changes are kept on this phone and sent when you're back online.")
                    } else {
                        outcome.failure.userMessage
                    }
                }
            }
        }
    }

    /** Sends what is pending first. If that is not possible, asks before losing it. */
    fun signOut(force: Boolean) {
        if (busy != null) return
        busy = AccountBusy.SigningOut
        scope.launch {
            if (!force) {
                SyncService.syncNow(store)
                if (SyncService.hasPendingChanges(store)) {
                    busy = null
                    haptics.play(HapticKind.Warning)
                    alert = AccountAlert.SignOutUnsaved
                    return@launch
                }
            }
            AuthService.signOut(store)
            busy = null
        }
    }

    fun deleteAccount() {
        if (busy != null) return
        busy = AccountBusy.Deleting
        scope.launch {
            try {
                AuthService.deleteAccount(store)
            } catch (error: Throwable) {
                haptics.play(HapticKind.Error)
                alert = AccountAlert.Error(error.message ?: L("Something went wrong. Please try again."))
            }
            busy = null
        }
    }

    val account = state.account
    val statusText = when {
        account == null -> L("Not signed in")
        account.isGuest -> L("Guest")
        else -> account.email ?: L("Signed in")
    }

    FormSection(
        header = L("Account"),
        footer = if (isSignedIn) {
            L("Your plan, your runs and your settings are saved to your account, and come back when you sign in on another phone.")
        } else {
            L("As a guest, everything stays on this phone only. Sign in to keep your plan and your runs safe.")
        },
    ) {
        // Identity
        FormRow {
            Text(if (isSignedIn) L("Signed in as") else L("Account"), style = Theme.font(17), color = Theme.ink)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(2f, fill = false)) {
                Text(statusText, style = Theme.font(17), color = Theme.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val provider = AuthService.signedInProvider
                if (isSignedIn && provider != null) {
                    Text(L("with %@", provider.title), style = Theme.font(13), color = Theme.muted)
                }
            }
        }
        if (isSignedIn) {
            if (sessionExpired) {
                FormDivider()
                FormNote(L("Your session has expired. Your plan and runs are safe on this phone; sign in again to keep saving them to your account."))
                FormDivider()
                FormButtonRow(L("Sign in again"), enabled = busy == null) {
                    haptics.tap()
                    showSignIn = true
                }
            } else {
                FormDivider()
                FormValueRow(L("Last synced"), lastSyncedText(state.lastSyncedAt, activity != SyncActivity.Idle || busy == AccountBusy.Syncing))
                FormDivider()
                FormButtonRow(
                    if (busy == AccountBusy.Syncing) L("Syncing…") else L("Sync now"),
                    enabled = busy == null, loading = busy == AccountBusy.Syncing,
                ) { syncNow() }
                syncNote?.let { note ->
                    FormDivider()
                    FormNote(note)
                }
            }
        } else {
            FormDivider()
            FormButtonRow(
                if (busy == AccountBusy.SigningIn) L("Signing in…") else L("Sign in"),
                enabled = busy == null, loading = busy == AccountBusy.SigningIn,
            ) {
                haptics.tap()
                showSignIn = true
            }
        }
        if (isSignedIn) {
            FormDivider()
            FormButtonRow(L("Sign out"), enabled = busy == null) {
                haptics.play(HapticKind.Warning)
                alert = AccountAlert.ConfirmSignOut
            }
        }
        FormDivider()
        FormButtonRow(
            when (busy) {
                AccountBusy.Deleting -> L("Deleting…")
                AccountBusy.SigningOut -> L("Signing out…")
                else -> if (isSignedIn) L("Delete my account") else L("Erase my data")
            },
            color = Color(0xFFE5332A),
            enabled = busy == null,
            loading = busy == AccountBusy.Deleting || busy == AccountBusy.SigningOut,
        ) {
            haptics.play(HapticKind.Warning)
            alert = AccountAlert.ConfirmDelete
        }
    }

    if (showSignIn) {
        SignInOptionsSheet(
            onSignedIn = { signedIn ->
                showSignIn = false
                finishSignIn(signedIn)
            },
            onDismiss = { showSignIn = false },
        )
    }
    // Google answered after this screen was rebuilt (or the app restarted): same path.
    RecoveredGoogleSignInEffect(
        onSignedIn = { finishSignIn(it) },
        onFailed = { message -> alert = AccountAlert.Error(message) },
    )

    alert?.let { current ->
        val title = when (current) {
            AccountAlert.ConfirmSignOut -> L("Sign out?")
            AccountAlert.SignOutUnsaved -> L("Your latest changes aren't saved yet")
            AccountAlert.ConfirmDelete -> if (isSignedIn) L("Delete your account?") else L("Erase everything?")
            is AccountAlert.Error -> L("Something went wrong")
        }
        val message = when (current) {
            AccountAlert.ConfirmSignOut ->
                L("Your plan, your runs and your settings are saved in your account. They are removed from this phone and come back when you sign in again.")
            AccountAlert.SignOutUnsaved ->
                L("We couldn't reach your account, so what you changed most recently is only on this phone. If you sign out now, those changes are lost.")
            AccountAlert.ConfirmDelete -> if (isSignedIn) {
                L("Your account, your plan, your runs and your conversations with your coach are permanently deleted. This cannot be undone.")
            } else {
                L("Your plan, your runs and your conversations with your coach are removed from this phone. This cannot be undone.")
            }
            is AccountAlert.Error -> current.message
        }
        val destructive: Pair<String, () -> Unit>? = when (current) {
            AccountAlert.ConfirmSignOut -> L("Sign out") to { signOut(force = false) }
            AccountAlert.SignOutUnsaved -> L("Sign out anyway") to { signOut(force = true) }
            AccountAlert.ConfirmDelete -> (if (isSignedIn) L("Delete everything") else L("Erase everything")) to { deleteAccount() }
            is AccountAlert.Error -> null
        }
        val cancel = when (current) {
            AccountAlert.SignOutUnsaved -> L("Stay signed in")
            is AccountAlert.Error -> L("OK")
            else -> L("Cancel")
        }
        AlertDialog(
            onDismissRequest = { alert = null },
            containerColor = Color.White,
            title = { Text(title, style = Theme.font(18, FontWeight.SemiBold), color = Theme.ink) },
            text = { Text(message, style = Theme.font(15), color = Theme.body) },
            confirmButton = {
                if (destructive != null) {
                    TextButton(onClick = {
                        alert = null
                        destructive.second()
                    }) { Text(destructive.first, color = Color(0xFFE5332A), style = Theme.font(15, FontWeight.SemiBold)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { alert = null }) {
                    Text(cancel, color = Theme.ink, style = Theme.font(15, if (destructive == null) FontWeight.SemiBold else FontWeight.Normal))
                }
            },
        )
    }
}

private fun lastSyncedText(date: Instant?, syncing: Boolean): String {
    if (syncing) return L("Syncing…")
    if (date == null) return L("Not yet")
    val seconds = Duration.between(date, Instant.now()).seconds
    if (seconds < 90) return L("Just now")
    if (seconds < 3600) return L("%@ min ago", (seconds / 60).toInt())
    if (PlanCalendar.isToday(date)) return L("Today at %@", Fmt.day(date, "HH:mm"))
    return L("%@ at %@", Fmt.day(date, "d MMM"), Fmt.day(date, "HH:mm"))
}

/** Debug tools (debug builds only) and the app version. */
@Composable
internal fun ProfileFooterSection() {
    val store = LocalAppStore.current
    val haptics = rememberHaptics()
    if (BuildConfig.DEBUG) {
        FormSection {
            FormButtonRow(L("Restart onboarding"), color = Color(0xFFE5332A)) {
                haptics.play(HapticKind.Warning)
                SyncService.eraseEverything(store)
            }
        }
    }
    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(painterResource(R.drawable.logo_atala), "Atala", colorFilter = ColorFilter.tint(Theme.forest), modifier = Modifier.height(18.dp))
        val version = BuildConfig.VERSION_NAME
        val build = BuildConfig.VERSION_CODE.toString()
        Text(
            if (build != version) L("Version %@ (%@)", version, build) else L("Version %@", version),
            style = Theme.font(13).copy(textAlign = TextAlign.Center), color = Theme.muted,
        )
    }
}
