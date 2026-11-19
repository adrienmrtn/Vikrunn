package com.atala.app.core.sync

import com.atala.app.core.model.PlanCalendar
import com.atala.app.core.model.RunRecord
import com.atala.app.core.model.SyncState
import com.atala.app.core.model.TrainingPlan
import com.atala.app.core.model.WorkoutStatus
import com.atala.app.core.store.AppState
import com.atala.app.core.store.AppStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * The store hooks of the iPhone (`syncNoteChange`, `syncNoteRunsChange`, `syncApplyRemote` in
 * SyncServiceState.swift), without touching the store: every new state is compared with the last
 * one seen here, on the main thread, and what changed on the phone is marked to be sent.
 * Server data is written through [applyRemote], so it is never sent straight back.
 */
internal object SyncTracker {
    private var baseline: AppState? = null
    private var applyingRemote = false

    fun start(store: AppStore, scope: CoroutineScope) {
        baseline = store.current
        scope.launch(Dispatchers.Main.immediate) {
            store.state.collect { observe(store, it) }
        }
    }

    /** Writes server data into the store without marking it as a local change. */
    fun <T> applyRemote(store: AppStore, changes: () -> T): T {
        // Local changes not looked at yet are marked first.
        observe(store, store.current)
        val wasApplying = applyingRemote
        applyingRemote = true
        try {
            return changes()
        } finally {
            applyingRemote = wasApplying
            baseline = store.current
        }
    }

    private fun observe(store: AppStore, new: AppState) {
        val old = baseline
        baseline = new
        if (old == null || old === new || applyingRemote) return
        val profile = new.answers != old.answers || new.settings != old.settings
        // Sessions turned into "missed" by the date are the same on every phone: not a change to send.
        val plan = new.plan != old.plan && old.plan?.markingMissed() != new.plan
        val runs = new.runs != old.runs
        if (!profile && !plan && !runs) return
        // Only a real account is ever matched with the server; a sign-in starts its bookkeeping afresh.
        if (!new.hasRealAccount) return
        val now = Instant.now()
        store.update { state ->
            var sync = state.syncState
            if (profile) sync = sync.copy(profileDirtyAt = now)
            if (plan) sync = sync.copy(planDirtyAt = now)
            if (runs) sync = sync.notingRuns(old.runs, new.runs, now)
            state.copy(syncState = sync)
        }
        baseline = store.current
        SyncService.schedulePush(store)
    }

    /** Marks the new or edited runs, remembers the deleted ones. */
    private fun SyncState.notingRuns(old: List<RunRecord>, new: List<RunRecord>, now: Instant): SyncState {
        val dirty = runsDirtyAt.toMutableMap()
        val deleted = deletedRunIds.toMutableList()
        val previous = old.associateBy { it.id }
        val currentIds = new.map { it.id }.toSet()
        for (run in new) {
            if (previous[run.id] != run) {
                val key = SyncCoding.key(run.id)
                dirty[key] = now
                deleted.remove(key)
            }
        }
        for (run in old) {
            if (run.id in currentIds) continue
            val key = SyncCoding.key(run.id)
            dirty.remove(key)
            if (key !in deleted) deleted.add(key)
        }
        return copy(runsDirtyAt = dirty, deletedRunIds = deleted)
    }

    /** The plan as `AppStore.refreshMissed` leaves it today. */
    private fun TrainingPlan.markingMissed(): TrainingPlan {
        val today = PlanCalendar.startOfDay(Instant.now())
        return copy(weeks = weeks.map { week ->
            week.copy(workouts = week.workouts.map {
                if (it.status == WorkoutStatus.Planned && it.date < today) it.copy(status = WorkoutStatus.Missed) else it
            })
        })
    }
}
