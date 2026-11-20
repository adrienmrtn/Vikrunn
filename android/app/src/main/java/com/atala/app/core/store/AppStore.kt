package com.atala.app.core.store

import android.util.AtomicFile
import androidx.core.util.readText
import androidx.core.util.writeText
import com.atala.app.core.account.AuthKeychain
import com.atala.app.core.account.withoutTokens
import com.atala.app.core.model.Account
import com.atala.app.core.model.AppSettings
import com.atala.app.core.model.AtalaJson
import com.atala.app.core.model.ChatMessage
import com.atala.app.core.model.OnboardingAnswers
import com.atala.app.core.model.PlanCalendar
import com.atala.app.core.model.ProposalStatus
import com.atala.app.core.model.RunRecord
import com.atala.app.core.model.SyncState
import com.atala.app.core.model.TrainingPlan
import com.atala.app.core.model.Workout
import com.atala.app.core.model.WorkoutStatus
import com.atala.app.core.plan.PlanEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * Single source of truth of the app. Everything is saved on the phone as one JSON file in the app's
 * files directory (`vicrun.json`, as on the iPhone), a moment after each change.
 * Screens collect [state]; every change goes through the actions below or [update].
 *
 * @param file where the state is saved; null keeps it in memory (previews, tests).
 */
@OptIn(FlowPreview::class)
class AppStore(file: File?, scope: CoroutineScope) {
    private val file = file?.let(::AtomicFile)
    private val _state = MutableStateFlow(load())

    val state: StateFlow<AppState> = _state.asStateFlow()

    /** The state right now, for actions and code outside the interface. */
    val current: AppState get() = _state.value

    init {
        if (this.file != null) {
            state.drop(1).debounce(400).onEach(::write).flowOn(Dispatchers.IO).launchIn(scope)
        }
    }

    // Persistence

    private fun load(): AppState = file?.let {
        val loaded = runCatching { AtalaJson.decodeFromString<AppState>(it.readText()) }.getOrNull() ?: AppState()
        // The session tokens live in the Keystore-encrypted vault, not in the file.
        loaded.copy(account = AuthKeychain.restore(loaded.account))
    } ?: AppState()

    private fun write(state: AppState) {
        val file = file ?: return
        AuthKeychain.sync(state.account)
        val saved = state.copy(account = state.account?.withoutTokens)
        synchronized(file) { file.writeText(AtalaJson.encodeToString(AppState.serializer(), saved)) }
    }

    /** Writes the state now, without waiting for the pending save (the app is going to the background). */
    fun save() = write(current)

    /** Any change to the state. The named actions below are preferred when one fits. */
    fun update(transform: (AppState) -> AppState) = _state.update(transform)

    fun updateAnswers(transform: (OnboardingAnswers) -> OnboardingAnswers) =
        update { it.copy(answers = transform(it.answers)) }

    fun updateSettings(transform: (AppSettings) -> AppSettings) =
        update { it.copy(settings = transform(it.settings)) }

    fun setOnboardingStep(rawValue: Int) = update { it.copy(onboardingStep = rawValue) }

    fun setAccount(account: Account?) = update { it.copy(account = account) }

    // Plan

    /** Builds (or rebuilds) the plan from the current answers. */
    fun generatePlan() = update { it.copy(plan = PlanEngine.generate(it.answers)) }

    fun finishOnboarding() = update {
        it.copy(plan = it.plan ?: PlanEngine.generate(it.answers), hasCompletedOnboarding = true)
    }

    fun updateWorkout(id: UUID, change: (Workout) -> Workout) = update { state ->
        state.plan?.updatingWorkout(id, change)?.let { state.copy(plan = it) } ?: state
    }

    fun setStatus(status: WorkoutStatus, id: UUID) = updateWorkout(id) { it.copy(status = status) }

    /** Sessions planned before today and never done become "missed". The plan is not recalculated. */
    fun refreshMissed() = update { state ->
        val plan = state.plan ?: return@update state
        val today = PlanCalendar.startOfDay(Instant.now())
        fun Workout.isOverdue() = status == WorkoutStatus.Planned && date < today
        if (plan.weeks.none { week -> week.workouts.any { it.isOverdue() } }) return@update state
        state.copy(plan = plan.copy(weeks = plan.weeks.map { week ->
            week.copy(workouts = week.workouts.map { if (it.isOverdue()) it.copy(status = WorkoutStatus.Missed) else it })
        }))
    }

    // Runs

    /** Saves a run and marks its session as done when it belongs to the plan. */
    fun addRun(run: RunRecord) = update { state ->
        val runs = (state.runs.filter { it.id != run.id } + run).sortedByDescending { it.date }
        val plan = run.workoutId?.let { workoutId ->
            state.plan?.updatingWorkout(workoutId) { it.copy(status = WorkoutStatus.Done, runId = run.id) }
        } ?: state.plan
        state.copy(runs = runs, plan = plan)
    }

    fun updateRun(id: UUID, change: (RunRecord) -> RunRecord) = update { state ->
        state.copy(runs = state.runs.map { if (it.id == id) change(it) else it })
    }

    fun deleteRun(id: UUID) {
        current.run(id)?.workoutId?.let { workoutId ->
            updateWorkout(workoutId) { it.copy(runId = null, status = WorkoutStatus.Planned) }
        }
        update { state -> state.copy(runs = state.runs.filter { it.id != id }) }
        refreshMissed()
    }

    // Coach proposals

    /** Applies a change proposed by the coach, after the user accepted it. */
    fun applyProposal(messageId: UUID) = update { state ->
        val message = state.messages.firstOrNull { it.id == messageId } ?: return@update state
        val proposal = message.proposal?.takeIf { it.status == ProposalStatus.Pending } ?: return@update state
        var plan = state.plan
        for (change in proposal.changes) {
            plan = if (change.remove) {
                plan?.removingWorkout(change.workoutId)
            } else {
                plan?.updatingWorkout(change.workoutId) { workout ->
                    var updated = workout
                    change.newDate?.let { date ->
                        updated = updated.copy(
                            date = PlanCalendar.startOfDay(date),
                            status = if (updated.status == WorkoutStatus.Missed) WorkoutStatus.Planned else updated.status,
                        )
                    }
                    updated.copy(
                        kind = change.newKind ?: updated.kind,
                        title = change.newTitle ?: updated.title,
                        distanceKm = change.newDistanceKm ?: updated.distanceKm,
                        durationMin = change.newDurationMin ?: updated.durationMin,
                    )
                } ?: plan
            }
        }
        state.copy(plan = plan, messages = state.messages.withProposalStatus(messageId, ProposalStatus.Applied))
    }

    fun declineProposal(messageId: UUID) = update { state ->
        val pending = state.messages.any { it.id == messageId && it.proposal?.status == ProposalStatus.Pending }
        if (!pending) state else state.copy(messages = state.messages.withProposalStatus(messageId, ProposalStatus.Declined))
    }

    // Account

    /** Forgets the account only. Signing out of the app also clears the phone (see the auth service). */
    fun signOut() = update { it.copy(account = null, syncState = SyncState(), lastSyncedAt = null) }

    /** Erases everything stored on the phone and returns to the first screen. */
    fun eraseEverything() {
        _state.value = AppState()
        save()
    }

    private fun List<ChatMessage>.withProposalStatus(id: UUID, status: ProposalStatus) =
        map { message ->
            if (message.id == id) message.copy(proposal = message.proposal?.copy(status = status)) else message
        }
}

/** The plan with one session changed (its week kept in date order), null when the session is not in it. */
internal fun TrainingPlan.updatingWorkout(id: UUID, change: (Workout) -> Workout): TrainingPlan? {
    val index = weeks.indexOfFirst { week -> week.workouts.any { it.id == id } }
    if (index < 0) return null
    val week = weeks[index]
    val workouts = week.workouts.map { if (it.id == id) change(it) else it }.sortedBy { it.date }
    return copy(weeks = weeks.toMutableList().apply { set(index, week.copy(workouts = workouts)) })
}

internal fun TrainingPlan.removingWorkout(id: UUID): TrainingPlan =
    copy(weeks = weeks.map { week -> week.copy(workouts = week.workouts.filter { it.id != id }) })
