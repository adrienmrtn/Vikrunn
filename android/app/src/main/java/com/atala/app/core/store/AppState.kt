@file:UseSerializers(InstantSerializer::class)

package com.atala.app.core.store

import com.atala.app.core.l10n.L
import com.atala.app.core.model.Account
import com.atala.app.core.model.AppSettings
import com.atala.app.core.model.ChatMessage
import com.atala.app.core.model.Coach
import com.atala.app.core.model.InstantSerializer
import com.atala.app.core.model.OnboardingAnswers
import com.atala.app.core.model.PlanCalendar
import com.atala.app.core.model.PlanWeek
import com.atala.app.core.model.RunRecord
import com.atala.app.core.model.Streak
import com.atala.app.core.model.StreakEngine
import com.atala.app.core.model.SyncState
import com.atala.app.core.model.TrainingPlan
import com.atala.app.core.model.Workout
import com.atala.app.core.model.WorkoutStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant
import java.util.UUID

/**
 * Everything the app knows, as one immutable value. It is also the saved file: the field names are
 * those of the iPhone's `AppStore.Snapshot`, so the same JSON loads on both platforms.
 */
@Serializable
data class AppState(
    val answers: OnboardingAnswers = OnboardingAnswers(),
    /** Raw value of the onboarding screen the user reached (`OnboardingStep`), so they can resume where they left. */
    val onboardingStep: Int = 0,
    val hasCompletedOnboarding: Boolean = false,
    val plan: TrainingPlan? = null,
    val runs: List<RunRecord> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val account: Account? = null,
    /** What still has to be sent to the account, and what was last seen there. */
    val syncState: SyncState = SyncState(),
    /** Last time the phone and the account were in step. */
    val lastSyncedAt: Instant? = null,
) {
    // Coach and profile

    val coach: Coach get() = Coach.byId(answers.coachId)
    val firstName: String get() = answers.firstName.ifEmpty { L("Runner") }

    // Plan

    val allWorkouts: List<Workout> get() = plan?.allWorkouts.orEmpty()

    fun workout(id: UUID): Workout? = allWorkouts.firstOrNull { it.id == id }

    fun workouts(day: Instant): List<Workout> = allWorkouts.filter { PlanCalendar.isSameDay(it.date, day) }

    val todayWorkouts: List<Workout> get() = workouts(Instant.now())

    /** The next planned run from today on. */
    val nextRun: Workout?
        get() {
            val today = PlanCalendar.startOfDay(Instant.now())
            return allWorkouts.firstOrNull { it.kind.isRun && it.status == WorkoutStatus.Planned && it.date >= today }
        }

    /** The plan week that contains today, else the first one. */
    val currentWeek: PlanWeek?
        get() {
            val plan = plan ?: return null
            val monday = PlanCalendar.mondayOfWeek(Instant.now())
            return plan.weeks.firstOrNull { PlanCalendar.isSameDay(it.startDate, monday) } ?: plan.weeks.firstOrNull()
        }

    // Runs

    fun run(id: UUID?): RunRecord? = id?.let { wanted -> runs.firstOrNull { it.id == wanted } }

    val totalKm: Double get() = runs.sumOf { it.distanceKm }

    /** Weekly streak from the saved runs and the sessions marked as done. */
    val streak: Streak get() = StreakEngine.weekly(runs, allWorkouts)
}
