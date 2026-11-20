package com.atala.app.ui.onboarding

import androidx.compose.runtime.Composable
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.GoalType
import com.atala.app.core.model.Motivation
import com.atala.app.core.model.OnboardingAnswers
import com.atala.app.core.model.TrainingPlan
import com.atala.app.ui.paywall.PaywallScreen
import java.util.Locale

/**
 * Screen 31 of the entry flow: the shared paywall (`ui.paywall.PaywallScreen`, built by the Pro
 * team), its close button held back 7 s; no way back from it.
 */
@Composable
fun OnboardingPaywallScreen() {
    val nav = LocalOnboardingNavigator.current
    PaywallScreen(closeDelay = 7.0, onFinished = nav::next)
}

/**
 * The paywall's personal reminder, built from the answers and the plan (OnboardingPaywallCopy.swift).
 * Every case is one whole sentence, so each can be edited on its own. Nothing here promises a
 * result: it only restates the goal the user set and what the plan contains. For the paywall.
 */
object OnboardingPaywallCopy {
    /** "1:45" for a round time over an hour, "1:45:30" otherwise, "27:30" under the hour. */
    fun targetTime(seconds: Int): String {
        val total = maxOf(0, seconds)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val secs = total % 60
        if (hours > 0) {
            return if (secs == 0) String.format(Locale.ROOT, "%d:%02d", hours, minutes)
            else String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
        }
        return String.format(Locale.ROOT, "%d:%02d", minutes, secs)
    }

    /** The race day, when the user gave one: the plan's own date first, the answer otherwise. */
    fun raceDay(answers: OnboardingAnswers, plan: TrainingPlan?): String? {
        plan?.raceDate?.let { return Fmt.day(it, "d MMMM") }
        val date = answers.raceDate
        if (answers.hasRace && date != null) return Fmt.day(date, "d MMMM")
        return null
    }

    /** First line: the goal in the user's own terms. */
    fun goalLine(answers: OnboardingAnswers, plan: TrainingPlan?): String {
        val goal = answers.goal ?: plan?.goal ?: GoalType.Regular
        if (goal.isOpenEnded) {
            return when (answers.runDays.size) {
                0 -> L("Running every week, for good.")
                1 -> L("Running once a week, for good.")
                2 -> L("Running twice a week, for good.")
                else -> L("Running %@ times a week, for good.", answers.runDays.size)
            }
        }
        val noun = OnboardingCopy.goalNoun(goal)
        val day = raceDay(answers, plan)
        val target = answers.targetSeconds ?: plan?.targetSeconds
        if (target != null) {
            val time = targetTime(target)
            return if (day != null) L("Your %@ in %@, on %@.", noun, time, day) else L("Your %@ in %@.", noun, time)
        }
        if (day != null) return L("Your %@, on %@.", noun, day)
        return L("Your %@, one session at a time.", noun)
    }

    /** Second line: the plan and the coach. */
    fun planLine(coachName: String): String = L("Your plan is ready, with %@ by your side.", coachName)

    /** Third line: why they run. Null when the question was not answered. */
    fun motivationLine(motivation: Motivation?): String? = when (motivation) {
        null -> null
        Motivation.Athletic -> L("Because you want to get in better shape.")
        Motivation.Prove -> L("Because you want to prove to yourself you can do it.")
        Motivation.Record -> L("Because you want to beat your record.")
        Motivation.Health -> L("Because you want to take care of your health.")
        Motivation.ClearHead -> L("Because you want to clear your head.")
        Motivation.Together -> L("Because you want to run with people you love.")
    }
}
