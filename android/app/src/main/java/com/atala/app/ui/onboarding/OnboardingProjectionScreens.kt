package com.atala.app.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.atala.app.core.l10n.L
import com.atala.app.core.model.OnboardingAnswers
import com.atala.app.core.model.TrainingPlan
import com.atala.app.core.plan.PlanEngine
import com.atala.app.ui.root.collectAppState

/** Where the plan takes the user (step `Projection`), computed by `PlanEngine` from the real answers. Placeholder. */
@Composable
fun OnboardingProjectionScreen() {
    val state by collectAppState()
    val plan = remember(state.answers) { PlanEngine.generate(state.answers) }
    OnboardingScaffold(title = projectionTitle(plan, state.answers)) {}
}

/** Step `Reviews`. Placeholder. */
@Composable
fun OnboardingReviewsScreen() {
    OnboardingScaffold(title = L("Runners love their Atala coach")) {}
}

/**
 * Times of "today" and of the end of the plan are only shown when they come from a real recent
 * result (`hasRecentResult`). Without one, beginners included, the screen speaks of the plan itself.
 */
private fun projectionTitle(plan: TrainingPlan, answers: OnboardingAnswers): String {
    val weeks = OnboardingCopy.weeks(plan.weeks.size)
    if (plan.goal.isOpenEnded) return L("%@ to make running a habit", weeks)
    val ready = L("%@ to be ready for your %@", weeks, OnboardingCopy.goalNoun(plan.goal))
    if (!OnboardingNavigator.hasRecentResult(answers)) return ready
    val today = plan.currentSeconds
    val goal = plan.predictedSeconds
    val gain = if (today != null && goal != null) maxOf(0, today - goal) else 0
    return when {
        gain >= 120 -> L("You can gain %@ minutes in %@", gain / 60, weeks)
        gain >= 60 -> L("You can gain %@ minute in %@", 1, weeks)
        gain > 0 -> L("You can gain %@ seconds in %@", gain, weeks)
        else -> ready
    }
}
