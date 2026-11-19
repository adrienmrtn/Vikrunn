package com.atala.app.ui.onboarding

import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.GoalType
import com.atala.app.core.model.OnboardingAnswers
import kotlin.math.roundToInt

/** Wording built from the answers and reused on several screens (`OnboardingCopy`). */
object OnboardingCopy {
    fun goalNoun(goal: GoalType?): String = when (goal ?: GoalType.Regular) {
        GoalType.FiveK -> "5K"
        GoalType.TenK -> "10K"
        GoalType.Half -> L("half marathon")
        GoalType.Regular -> L("running routine")
        GoalType.Shape -> L("fitness routine")
    }

    /** "Half marathon in 1:45:00", "10K" or "Run regularly". */
    fun goalHeadline(answers: OnboardingAnswers): String {
        val goal = answers.goal ?: GoalType.Regular
        val target = answers.targetSeconds
        if (!goal.isOpenEnded && target != null) return L("%@ in %@", goal.title, Fmt.goalTime(target))
        return goal.title
    }

    fun sessionsPerWeek(answers: OnboardingAnswers): Int = answers.runDays.size.takeIf { it > 0 } ?: 3

    fun sessions(count: Int): String = if (count == 1) L("1 session a week") else L("%@ sessions a week", count)

    fun weeks(count: Int): String = if (count == 1) L("1 week") else L("%@ weeks", count)

    fun distanceName(km: Double): String = when {
        km < 7.5 -> "5K"
        km < 15 -> "10K"
        else -> L("half marathon")
    }

    fun pacePerKm(seconds: Int, km: Double): String =
        if (km <= 0) "--:--" else Fmt.pace((seconds / km).roundToInt())
}
