package com.atala.app.core.sync

import com.atala.app.core.account.AuthService
import com.atala.app.core.model.Account
import com.atala.app.core.model.AppSettings
import com.atala.app.core.model.OnboardingAnswers
import com.atala.app.core.model.RunRecord
import com.atala.app.core.model.SyncState
import com.atala.app.core.model.TrainingPlan
import com.atala.app.core.store.AppStore
import java.time.Instant

// Pull, merge and sign-in (SyncServicePull.swift).

// Regular pull

internal suspend fun SyncService.runPull(store: AppStore): SyncPullResult {
    val account = store.current.account
    if (!isPersistent(store) || account == null || account.isGuest || !store.current.syncState.reconciled) {
        return SyncPullResult(SyncOutcome.NotSignedIn, changed = false)
    }
    val userId = account.userId
    return try {
        val snapshot = fetchSnapshot(store, everything = false)
        if (store.current.account?.userId != userId || !store.current.syncState.reconciled) {
            return SyncPullResult(SyncOutcome.Failed(SyncFailure.Interrupted), changed = false)
        }
        val changed = merge(snapshot, store, SyncMergeBias.LastWriteWins)
        store.update { it.copy(lastSyncedAt = Instant.now()) }
        if (changed) SyncTracker.applyRemote(store) { store.refreshMissed() }
        if (store.current.messages.isEmpty()) restoreHistory?.invoke(store)
        SyncPullResult(SyncOutcome.Done, changed)
    } catch (failure: SyncFailure) {
        SyncPullResult(SyncOutcome.Failed(failure), changed = false)
    } catch (_: Throwable) {
        SyncPullResult(SyncOutcome.Failed(SyncFailure.Offline), changed = false)
    }
}

// Merge (no network: decides and applies in one go)

private fun takesRemote(dirtyAt: Instant?, stamp: String, bias: SyncMergeBias): Boolean = when (bias) {
    SyncMergeBias.Account -> true
    SyncMergeBias.Phone -> false
    SyncMergeBias.LastWriteWins -> {
        if (dirtyAt == null) true
        else SyncCoding.date(fromServer = stamp)?.let { it > dirtyAt } ?: false
    }
}

/** Applies a snapshot of the account to the store. Returns true when local data changed. */
internal fun SyncService.merge(remote: SyncRemoteSnapshot, store: AppStore, bias: SyncMergeBias): Boolean =
    SyncTracker.applyRemote(store) {
        val current = store.current
        var state = current.syncState
        val now = Instant.now()
        var newAnswers: OnboardingAnswers? = null
        var newSettings: AppSettings? = null
        var newPlan: TrainingPlan? = null

        // Profile
        val profileStamp = remote.profileStamp
        if (profileStamp != null) {
            if (profileStamp != state.profileStamp && remote.answers != null &&
                takesRemote(state.profileDirtyAt, profileStamp, bias)
            ) {
                newAnswers = remote.answers
                newSettings = remote.settings
                state = state.copy(profileStamp = profileStamp, profileDirtyAt = null)
            } else if (bias == SyncMergeBias.Phone && state.profileDirtyAt == null) {
                state = state.copy(profileDirtyAt = now)
            }
        } else if (state.profileDirtyAt == null) {
            state = state.copy(profileDirtyAt = now) // nothing in the account yet: upload
        }

        // Plan
        val planStamp = remote.planStamp
        if (planStamp != null) {
            if (planStamp != state.planStamp && remote.plan != null && takesRemote(state.planDirtyAt, planStamp, bias)) {
                newPlan = remote.plan
                state = state.copy(planStamp = planStamp, planDirtyAt = null)
            } else if (bias == SyncMergeBias.Phone && current.plan != null && state.planDirtyAt == null) {
                state = state.copy(planDirtyAt = now)
            }
        } else if (current.plan != null && state.planDirtyAt == null) {
            state = state.copy(planDirtyAt = now)
        }

        // Runs: union by id; a tombstone removes the run everywhere.
        val byKey = LinkedHashMap<String, RunRecord>()
        for (run in current.runs) byKey[SyncCoding.key(run.id)] = run
        val runStamps = state.runStamps.toMutableMap()
        val runsDirty = state.runsDirtyAt.toMutableMap()
        val deletedIds = state.deletedRunIds.toMutableList()
        val remoteKeys = mutableSetOf<String>()
        var runsChanged = false
        for (item in remote.runs) {
            remoteKeys.add(item.key)
            if (item.deleted) {
                if (byKey.remove(item.key) != null) runsChanged = true
                runStamps[item.key] = item.stamp
                runsDirty.remove(item.key)
                deletedIds.remove(item.key)
                continue
            }
            if (item.key in deletedIds) continue // deleted here, not sent yet
            val local = byKey[item.key]
            if (local != null && runStamps[item.key] == item.stamp) continue
            val record = remote.runRecords[item.key] ?: continue
            val dirtyAt = runsDirty[item.key]
            if (local != null && dirtyAt != null && !takesRemote(dirtyAt, item.stamp, SyncMergeBias.LastWriteWins)) {
                continue // edited here more recently: the push sends it
            }
            if (local != record) {
                byKey[item.key] = record
                runsChanged = true
            }
            runStamps[item.key] = item.stamp
            runsDirty.remove(item.key)
        }
        // On the phone only (recorded offline, or as a guest before signing in): upload.
        for (key in byKey.keys) {
            if (key !in remoteKeys && runsDirty[key] == null) runsDirty[key] = now
        }
        state = state.copy(runStamps = runStamps, runsDirtyAt = runsDirty, deletedRunIds = deletedIds)

        var changed = false
        val finalState = state
        store.update { s ->
            var next = s
            if (newAnswers != null && s.answers != newAnswers) {
                next = next.copy(answers = newAnswers)
                changed = true
            }
            if (newSettings != null && s.settings != newSettings) {
                next = next.copy(settings = newSettings)
                changed = true
            }
            if (newPlan != null && s.plan != newPlan) {
                next = next.copy(plan = newPlan)
                changed = true
            }
            if (runsChanged) {
                next = next.copy(runs = byKey.values.sortedByDescending { it.date })
                changed = true
            }
            next.copy(syncState = finalState)
        }
        changed
    }

// Sign-in

/**
 * Call right after a successful sign-in, instead of setting the account directly. Looks at what
 * the account holds, restores it or uploads the phone's data, and reports what happened. On failure
 * the store is left exactly as it was (still signed out, or still a guest).
 */
suspend fun SyncService.completeSignIn(account: Account, store: AppStore, policy: SyncSignInPolicy): SyncSignInOutcome =
    serialized {
        val current = store.current.account
        // Signing in again to the account already on this phone (expired session): keep everything,
        // just take the new session and catch up.
        if (current != null && !current.isGuest && current.userId == account.userId && store.current.syncState.reconciled) {
            store.setAccount(account)
            AuthService.noteSignedIn()
            runPull(store)
            runPush(store)
            return@serialized SyncSignInOutcome.Continued
        }
        val previous = SyncPreviousSession(current, store.current.syncState, store.current.lastSyncedAt)
        cancelScheduledPush()
        // Not matched yet: nothing is sent.
        store.update { it.copy(account = account, syncState = SyncState(), lastSyncedAt = null) }
        AuthService.noteSignedIn()
        reconcile(store, policy, previous)
    }

/** Answer to "Use this phone's plan" / "Use my saved plan". */
suspend fun SyncService.resolveChoice(keepPhone: Boolean, store: AppStore): SyncSignInOutcome = serialized {
    if (!store.current.hasRealAccount || store.current.syncState.reconciled) {
        setChoicePending(false)
        return@serialized SyncSignInOutcome.Continued
    }
    reconcile(
        store, SyncSignInPolicy.PreferAccount, previous = null,
        forced = if (keepPhone) SyncMergeBias.Phone else SyncMergeBias.Account,
    )
}

/** Matches the phone with the account once. [previous] is restored when the account cannot be read. */
internal suspend fun SyncService.reconcile(
    store: AppStore,
    policy: SyncSignInPolicy,
    previous: SyncPreviousSession?,
    forced: SyncMergeBias? = null,
): SyncSignInOutcome {
    val account = store.current.account
    if (!isPersistent(store) || account == null || account.isGuest) return SyncSignInOutcome.Continued
    val userId = account.userId
    setActivity(SyncActivity.Restoring)
    try {
        val snapshot = try {
            fetchSnapshot(store, everything = true)
        } catch (error: Throwable) {
            if (previous != null && store.current.account?.userId == userId) {
                store.update {
                    it.copy(account = previous.account, syncState = previous.state, lastSyncedAt = previous.lastSyncedAt)
                }
            }
            return SyncSignInOutcome.Failed(error as? SyncFailure ?: SyncFailure.Offline)
        }
        if (store.current.account?.userId != userId) return SyncSignInOutcome.Failed(SyncFailure.Interrupted)

        val local = store.current
        val accountHasData = snapshot.planStamp != null || snapshot.hasLiveRuns
        val phoneHasData = (local.plan != null && local.hasCompletedOnboarding) || local.runs.isNotEmpty()
        val bias = when {
            forced != null -> forced
            accountHasData && phoneHasData && policy == SyncSignInPolicy.AskIfBoth -> {
                setChoicePending(true)
                return SyncSignInOutcome.NeedsChoice
            }
            accountHasData -> SyncMergeBias.Account
            else -> {
                // New account. A profile saved earlier (onboarding left half-way) still comes back
                // when nothing was answered on this phone yet.
                val untouched = local.answers == OnboardingAnswers() && local.plan == null
                if (untouched) SyncMergeBias.Account else SyncMergeBias.Phone
            }
        }

        store.update { it.copy(syncState = SyncState(reconciled = true)) }
        setChoicePending(false)
        // A conversation held as a guest belongs to the plan that is being replaced.
        if (bias == SyncMergeBias.Account && snapshot.plan != null) store.update { it.copy(messages = emptyList()) }
        merge(snapshot, store, bias)
        store.update { it.copy(lastSyncedAt = Instant.now()) }

        val restoredPlan = bias == SyncMergeBias.Account && snapshot.plan != null && store.current.plan != null
        if (restoredPlan) {
            SyncTracker.applyRemote(store) {
                store.refreshMissed()
                store.update { it.copy(hasCompletedOnboarding = true) }
            }
        }
        if (store.current.messages.isEmpty()) restoreHistory?.invoke(store)
        schedulePush(store, afterSeconds = 0.3)
        return if (restoredPlan) SyncSignInOutcome.Restored else SyncSignInOutcome.Continued
    } finally {
        setActivity(SyncActivity.Idle)
    }
}
