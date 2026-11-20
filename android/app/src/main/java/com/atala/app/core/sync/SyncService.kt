package com.atala.app.core.sync

import com.atala.app.core.account.string
import com.atala.app.core.model.SyncState
import com.atala.app.core.store.AppStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * Keeps the phone and the account in step: profile (answers, settings, coach, first name), plan
 * and runs (SyncService.swift). Same tables and JSON as the iPhone, so a runner can switch phone.
 *
 * - The phone always works on its own file; the account is a mirror, never a requirement.
 * - Changes are sent a few seconds after they happen, when the app goes to the background, after
 *   sign-in and after a run. Offline, they stay marked and go out at the next occasion.
 * - Per record, the most recent change wins (`updated_at` is set by the server). Runs are merged
 *   by id; a deleted run is removed from the account and from the other phones too.
 * - Only a real account syncs: guests keep everything on the phone.
 *
 * Everything runs on the main thread (the iPhone's `@MainActor`); operations never interleave.
 */
object SyncService {
    internal val scope = MainScope()
    private val queue = Mutex()
    private var persistentStore: AppStore? = null
    private var pushJob: Job? = null
    private var lastResume = 0L

    private val _activity = MutableStateFlow(SyncActivity.Idle)
    private val _choicePending = MutableStateFlow(false)

    /** What the sync is doing right now (not saved). */
    val activity: StateFlow<SyncActivity> = _activity.asStateFlow()

    /** True while the user must choose between the phone's data and the account's (not saved). */
    val choicePending: StateFlow<Boolean> = _choicePending.asStateFlow()

    /**
     * Refills an empty conversation from the account after a pull or a sign-in
     * (`CoachService.restoreHistory` on the iPhone). Set by the coach feature; nothing when null.
     */
    var restoreHistory: (suspend (AppStore) -> Unit)? = null

    /** The store saved on the phone. Previews and tests use others: nothing is sent for them. */
    internal fun register(store: AppStore) {
        persistentStore = store
    }

    fun isPersistent(store: AppStore): Boolean = store === persistentStore

    // Public API

    /** Sends what changed on the phone. Silent on failure: nothing is lost, it is sent next time. */
    suspend fun pushAll(store: AppStore) {
        serialized { runPush(store) }
    }

    /** Brings what changed in the account to the phone. True when server data replaced or completed local data. */
    suspend fun pullAll(store: AppStore): Boolean = serialized { runPull(store) }.changed

    /** Pull then push, with the result ("Sync now" in Profile, launch, return to the foreground). */
    suspend fun syncNow(store: AppStore): SyncOutcome = serialized {
        if (!isPersistent(store) || !store.current.hasRealAccount) return@serialized SyncOutcome.NotSignedIn
        if (!store.current.syncState.reconciled) {
            // Interrupted right after sign-in.
            val outcome = reconcile(store, SyncSignInPolicy.AskIfBoth, previous = null)
            if (outcome is SyncSignInOutcome.Failed) return@serialized SyncOutcome.Failed(outcome.failure)
            return@serialized if (store.current.syncState.reconciled) SyncOutcome.Done else SyncOutcome.NotSignedIn
        }
        if (_activity.value == SyncActivity.Idle) _activity.value = SyncActivity.Syncing
        try {
            val pulled = runPull(store)
            if (pulled.outcome is SyncOutcome.Failed) return@serialized pulled.outcome
            runPush(store)
        } finally {
            if (_activity.value == SyncActivity.Syncing) _activity.value = SyncActivity.Idle
        }
    }

    /** "Not now" after a failed choice: the question comes back at the next sync (launch, foreground). */
    fun postponeChoice() {
        _choicePending.value = false
    }

    /** True when something on the phone has not reached the account yet. */
    fun hasPendingChanges(store: AppStore): Boolean {
        val state = store.current
        return state.hasRealAccount && (!state.syncState.reconciled || state.syncState.hasPendingChanges)
    }

    // Lifecycle (SyncServiceLifecycle.swift)

    /**
     * Launch and return to the foreground: bring the account's changes, then send the phone's.
     * Never blocks the screen; does nothing for a guest. Calls close together are merged.
     */
    fun resume(store: AppStore) {
        if (!isPersistent(store) || !store.current.hasRealAccount) return
        val now = System.currentTimeMillis()
        if (now - lastResume <= 15_000) return
        lastResume = now
        scope.launch { syncNow(store) }
    }

    /**
     * The app goes to the background: send what is pending now. If the process is stopped first,
     * the changes stay marked (and saved) and go out at the next launch.
     */
    fun pushBeforeSuspension(store: AppStore) {
        if (!isPersistent(store) || !store.current.hasRealAccount || !hasPendingChanges(store)) return
        pushJob?.cancel()
        pushJob = null
        scope.launch { pushAll(store) }
    }

    /** Sends the pending changes a few seconds after the last one, so a burst of edits is one upload. */
    fun schedulePush(store: AppStore, afterSeconds: Double = 4.0) {
        val state = store.current
        if (!isPersistent(store) || !state.hasRealAccount || !state.syncState.reconciled) return
        pushJob?.cancel()
        pushJob = scope.launch {
            delay((afterSeconds.coerceAtLeast(0.0) * 1000).toLong())
            if (!isActive) return@launch
            // From here the upload runs to its end: a later change schedules a new one instead of
            // cancelling this one half-way.
            pushJob = null
            pushAll(store)
        }
    }

    /** Erases everything stored on the phone and returns to the first screen (sign-out, deletion). */
    fun eraseEverything(store: AppStore) {
        pushJob?.cancel()
        pushJob = null
        _choicePending.value = false
        _activity.value = SyncActivity.Idle
        SyncTracker.applyRemote(store) { store.eraseEverything() }
    }

    // Queue

    internal suspend fun <T> serialized(work: suspend () -> T): T =
        withContext(Dispatchers.Main.immediate) { queue.withLock { work() } }

    internal fun setActivity(value: SyncActivity) {
        _activity.value = value
    }

    internal fun setChoicePending(value: Boolean) {
        _choicePending.value = value
    }

    internal fun cancelScheduledPush() {
        pushJob?.cancel()
        pushJob = null
    }

    internal fun updateSync(store: AppStore, transform: (SyncState) -> SyncState) =
        store.update { it.copy(syncState = transform(it.syncState)) }

    // Push

    internal suspend fun runPush(store: AppStore): SyncOutcome {
        val account = store.current.account
        if (!isPersistent(store) || account == null || account.isGuest || !store.current.syncState.reconciled) {
            return SyncOutcome.NotSignedIn
        }
        if (!store.current.syncState.hasPendingChanges) return SyncOutcome.Done
        val userId = account.userId
        return try {
            pushProfile(store, userId)
            pushPlan(store, userId)
            pushRuns(store, userId)
            pushDeletedRuns(store, userId)
            store.update { it.copy(lastSyncedAt = Instant.now()) }
            SyncOutcome.Done
        } catch (failure: SyncFailure) {
            SyncOutcome.Failed(failure)
        } catch (_: Throwable) {
            SyncOutcome.Failed(SyncFailure.Offline)
        }
    }

    /** Stops an exchange whose account went away (sign-out, deletion) while a request was running. */
    private fun checkStillSignedIn(store: AppStore, userId: String) {
        val account = store.current.account
        if (account == null || account.isGuest || account.userId != userId) throw SyncFailure.Interrupted
    }

    private suspend fun pushProfile(store: AppStore, userId: String) {
        val dirtyAt = store.current.syncState.profileDirtyAt ?: return
        val answers = store.current.answers
        val row = buildJsonObject {
            put("user_id", userId)
            put("first_name", answers.firstName)
            put("coach_id", answers.coachId)
            put("answers", SyncCoding.json(com.atala.app.core.model.OnboardingAnswers.serializer(), answers))
            put("settings", SyncCoding.json(com.atala.app.core.model.AppSettings.serializer(), store.current.settings))
        }
        val saved = SyncRemote.upsert("profiles", "user_id", listOf(row), "user_id,updated_at", store)
        checkStillSignedIn(store, userId)
        updateSync(store) { state ->
            var next = state
            saved.firstOrNull()?.string("updated_at")?.let { next = next.copy(profileStamp = it) }
            // Changed again while the request was running: keep it marked for the next push.
            if (next.profileDirtyAt == dirtyAt) next = next.copy(profileDirtyAt = null)
            next
        }
    }

    private suspend fun pushPlan(store: AppStore, userId: String) {
        val dirtyAt = store.current.syncState.planDirtyAt ?: return
        val plan = store.current.plan
        if (plan == null) {
            // No plan on the phone: nothing to send (the account's copy is left alone).
            updateSync(store) { it.copy(planDirtyAt = null) }
            return
        }
        val row = buildJsonObject {
            put("user_id", userId)
            put("plan", SyncCoding.json(com.atala.app.core.model.TrainingPlan.serializer(), plan))
        }
        val saved = SyncRemote.upsert("plans", "user_id", listOf(row), "user_id,updated_at", store)
        checkStillSignedIn(store, userId)
        updateSync(store) { state ->
            var next = state
            saved.firstOrNull()?.string("updated_at")?.let { next = next.copy(planStamp = it) }
            if (next.planDirtyAt == dirtyAt) next = next.copy(planDirtyAt = null)
            next
        }
    }

    private suspend fun pushRuns(store: AppStore, userId: String) {
        val dirty = store.current.syncState.runsDirtyAt
        if (dirty.isEmpty()) return
        val rows = mutableListOf<JsonObject>()
        val sentAt = mutableMapOf<String, Instant>()
        val gone = mutableListOf<String>()
        val local = store.current.runs.associateBy { SyncCoding.key(it.id) }
        for ((key, date) in dirty) {
            val run = local[key]
            if (run == null) {
                gone.add(key)
                continue
            }
            rows.add(buildJsonObject {
                put("id", key)
                put("user_id", userId)
                put("run", SyncCoding.json(com.atala.app.core.model.RunRecord.serializer(), run))
                put("run_date", SyncCoding.isoString(run.date))
                put("deleted_at", JsonNull)
            })
            sentAt[key] = date
        }
        if (gone.isNotEmpty()) updateSync(store) { it.copy(runsDirtyAt = it.runsDirtyAt - gone.toSet()) }
        // Runs carry their GPS route: a few per request keeps each upload small.
        for (chunk in rows.chunked(8)) {
            val saved = SyncRemote.upsert("runs", "id", chunk, "id,updated_at", store)
            checkStillSignedIn(store, userId)
            updateSync(store) { state ->
                val stamps = state.runStamps.toMutableMap()
                val runsDirty = state.runsDirtyAt.toMutableMap()
                for (row in saved) {
                    val id = row.string("id") ?: continue
                    val stamp = row.string("updated_at") ?: continue
                    val key = id.lowercase()
                    stamps[key] = stamp
                    if (runsDirty[key] == sentAt[key]) runsDirty.remove(key)
                }
                state.copy(runStamps = stamps, runsDirtyAt = runsDirty)
            }
        }
    }

    /** A deleted run becomes a tombstone in the account, so the other phones remove it too. */
    private suspend fun pushDeletedRuns(store: AppStore, userId: String) {
        val keys = store.current.syncState.deletedRunIds
        if (keys.isEmpty()) return
        val now = SyncCoding.isoString(Instant.now())
        val rows = keys.map { key ->
            buildJsonObject {
                put("id", key)
                put("user_id", userId)
                put("run", JsonObject(emptyMap()))
                put("run_date", JsonNull)
                put("deleted_at", now)
            }
        }
        for (chunk in rows.chunked(50)) {
            val saved = SyncRemote.upsert("runs", "id", chunk, "id,updated_at", store)
            checkStillSignedIn(store, userId)
            updateSync(store) { state ->
                val deleted = state.deletedRunIds.toMutableList()
                val stamps = state.runStamps.toMutableMap()
                for (row in saved) {
                    val key = row.string("id")?.lowercase() ?: continue
                    deleted.remove(key)
                    row.string("updated_at")?.let { stamps[key] = it }
                }
                state.copy(deletedRunIds = deleted, runStamps = stamps)
            }
        }
    }
}
