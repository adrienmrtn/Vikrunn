package com.atala.app.ui.onboarding

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.atala.app.core.model.Experience
import com.atala.app.core.model.OnboardingAnswers
import com.atala.app.core.model.PlanCalendar
import com.atala.app.core.model.WeeklyVolume
import com.atala.app.core.model.isOpenEnded
import com.atala.app.core.store.AppStore
import java.time.Instant

/**
 * Owns the current step of the entry flow: navigation, skipping rules and the progress value
 * (`OnboardingNavigator` in OnboardingFlowView.swift). Screens get it from
 * `LocalOnboardingNavigator.current` and call [next] or [back]; the step is saved in the store.
 */
@Stable
class OnboardingNavigator(private val store: AppStore) {
    var step: OnboardingStep by mutableStateOf(OnboardingStep.Welcome)
        private set

    private var lastMove = 0L
    private val answers: OnboardingAnswers get() = store.current.answers

    /**
     * Resumes where the user left (answers and step are saved on the phone). A step saved by an
     * earlier version keeps its raw value, so it opens the same screen.
     */
    fun restore() {
        var saved = OnboardingStep.fromRawValue(store.current.onboardingStep) ?: OnboardingStep.Welcome
        if (saved == OnboardingStep.LevelRisk) saved = OnboardingStep.GoalIntro
        // The trial-reminder screen is gone: a step saved on it resumes on the paywall.
        if (saved == OnboardingStep.Reminder) saved = OnboardingStep.Paywall
        // Answers saved by an earlier version: no target time is kept for someone who does not run yet.
        if (isNotRunningYet(answers) && answers.targetSeconds != null) {
            store.updateAnswers { it.copy(targetSeconds = null) }
        }
        // A step the answers now rule out resumes on the next screen that is shown.
        val order = OnboardingStep.order
        var index = saved.position
        while (index < order.size && isSkipped(order[index])) index++
        if (index < order.size) saved = order[index]
        if (saved.position > OnboardingStep.Loading.position) {
            // A plan built on another day would start in the past: rebuild it from today.
            val fresh = store.current.plan?.let { PlanCalendar.isSameDay(it.createdAt, Instant.now()) } ?: false
            if (!fresh) store.generatePlan()
        }
        step = saved
    }

    fun isSkipped(candidate: OnboardingStep): Boolean = when (candidate) {
        OnboardingStep.LevelRisk -> true
        // "Here's what I see" only reflects a real result: nothing to show without one.
        OnboardingStep.Mirror -> !hasRecentResult(answers)
        OnboardingStep.Race -> answers.goal.isOpenEnded
        // No time to chase for someone who does not run yet: the goal is to finish.
        OnboardingStep.Target -> answers.goal.isOpenEnded || isNotRunningYet(answers)
        // The health question is out of the flow for now (no injury or medical wording at sign-up).
        OnboardingStep.Health -> true
        // Nothing to ask someone who does not run yet.
        OnboardingStep.Recent -> answers.weeklyVolume == WeeklyVolume.NotYet
        // "Starting is the hardest part" is only for people who are really starting.
        OnboardingStep.LevelIntro -> !isStarting(answers)
        // Everyone else: one screen about what the plan brings them.
        OnboardingStep.LevelFocus -> isStarting(answers)
        OnboardingStep.LongRun -> answers.runDays.size <= 1
        OnboardingStep.SignIn -> store.current.account != null
        // Android only: the iPhone's last step connects Apple Health, which does not exist here.
        // The notifications step ends the flow instead.
        OnboardingStep.HealthConnect -> true
        else -> false
    }

    /** The next screen that is not skipped; after the last one, the onboarding is finished and the plan built. */
    fun next() {
        val target = following()
        if (target != null) go(target) else store.finishOnboarding()
    }

    /**
     * Saves the step that follows the current one without showing it. Used when the whole flow is
     * about to be rebuilt (new language): the rebuilt flow restores itself on that step, so there
     * is one cross-fade instead of a step transition followed by a rebuild.
     * False when there is no next step: nothing was saved.
     */
    fun saveNext(): Boolean {
        val target = following() ?: return false
        store.setOnboardingStep(target.rawValue)
        return true
    }

    fun back() {
        if (!step.allowsBack) return
        val order = OnboardingStep.order
        var index = step.position - 1
        while (index >= 0) {
            if (!isSkipped(order[index])) return go(order[index])
            index--
        }
    }

    fun go(target: OnboardingStep) {
        if (target == step) return
        // Ignore a second tap while the previous transition is still starting.
        val now = nanoTime()
        if (now - lastMove < 300_000_000L) return
        lastMove = now
        step = target
        store.setOnboardingStep(target.rawValue)
    }

    private fun following(): OnboardingStep? =
        OnboardingStep.order.drop(step.position + 1).firstOrNull { !isSkipped(it) }

    /**
     * Steps left out of the bar because an earlier answer rules them out (the mirror depends on the
     * recent result, the level screens on the experience, the race screens on the goal), so the
     * remaining steps stay evenly spaced.
     */
    private fun isLeftOutOfBar(item: OnboardingStep): Boolean = when (item) {
        OnboardingStep.Recent, OnboardingStep.Health, OnboardingStep.Mirror, OnboardingStep.LevelIntro,
        OnboardingStep.LevelFocus, OnboardingStep.Race, OnboardingStep.Target -> isSkipped(item)
        else -> false
    }

    /** 5 % on the first name, 100 % on the reviews, even steps in between. Null off the bar. */
    fun progress(candidate: OnboardingStep): Double? {
        if (!candidate.showsProgress) return null
        val bar = OnboardingStep.order.filter { item ->
            item.showsProgress && (item == candidate || !isLeftOutOfBar(item))
        }
        val index = bar.indexOf(candidate)
        if (bar.size <= 1 || index < 0) return 0.05
        return 0.05 + 0.95 * index / (bar.size - 1)
    }

    /**
     * Value held by the fixed bar on any step: screens without a bar keep the value of the last
     * step that had one, so the bar never runs backwards while it is hidden.
     */
    fun barProgress(candidate: OnboardingStep = step): Double {
        val order = OnboardingStep.order
        var index = candidate.position
        while (index >= 0) {
            val item = order[index]
            if (item.showsProgress && (item == candidate || !isSkipped(item))) return progress(item) ?: 0.05
            index--
        }
        return 0.05
    }

    companion object {
        /** Clock of the double-tap guard in [go]. Tests move it on instead of waiting. */
        @VisibleForTesting
        internal var nanoTime: () -> Long = System::nanoTime

        /** Someone who said they are just starting, or that they do not run yet. */
        fun isStarting(answers: OnboardingAnswers): Boolean =
            (answers.experience ?: Experience.Starting) == Experience.Starting || answers.weeklyVolume == WeeklyVolume.NotYet

        /** Someone who said they do not run yet: there is no running to reflect back to them. */
        fun isNotRunningYet(answers: OnboardingAnswers): Boolean = answers.weeklyVolume == WeeklyVolume.NotYet

        /**
         * True when a real recent time was entered. Without one, any time, pace or level shown as
         * "today" would be made up from defaults: screens (mirror, projection) leave those figures out.
         */
        fun hasRecentResult(answers: OnboardingAnswers): Boolean {
            if (isNotRunningYet(answers)) return false
            val result = answers.recentResult ?: return false
            return result.seconds > 0 && result.distanceKm > 0
        }
    }
}
