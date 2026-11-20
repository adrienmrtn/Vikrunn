package com.atala.app.core.store

import com.atala.app.core.l10n.L
import com.atala.app.core.model.Experience
import com.atala.app.core.model.Feeling
import com.atala.app.core.model.Gender
import com.atala.app.core.model.GoalType
import com.atala.app.core.model.Motivation
import com.atala.app.core.model.OnboardingAnswers
import com.atala.app.core.model.PlanCalendar
import com.atala.app.core.model.RecentResult
import com.atala.app.core.model.RunRecord
import com.atala.app.core.model.RunSource
import com.atala.app.core.model.TrainingPlan
import com.atala.app.core.model.WeeklyVolume
import com.atala.app.core.plan.PlanEngine
import kotlinx.coroutines.MainScope
import java.time.Instant

/** A filled-in runner, for Compose previews. */
object SampleData {
    val answers: OnboardingAnswers
        get() = OnboardingAnswers(
            firstName = "Adrien",
            coachId = 1,
            gender = Gender.Man,
            experience = Experience.SixMonthsToTwoYears,
            weeklyVolume = WeeklyVolume.From10To25,
            recentResult = RecentResult(distanceKm = 10.0, seconds = 49 * 60 + 10),
            goal = GoalType.Half,
            hasRace = true,
            raceDate = PlanCalendar.addDays(Instant.now(), 52),
            targetSeconds = 105 * 60,
            motivation = Motivation.Prove,
            runDays = listOf(1, 2, 4, 7),
            longRunDay = 7,
        )

    /** A few past runs so that Progress and the history are not empty. */
    fun runs(plan: TrainingPlan?): List<RunRecord> {
        val easy = plan?.paces?.easy ?: 350
        val samples = listOf(
            Triple(-2, 6.1, L("Easy run")), Triple(-4, 8.4, "6 × 800 m"), Triple(-6, 12.2, L("Long run")),
            Triple(-9, 5.6, L("Easy run")), Triple(-11, 7.9, L("Tempo")),
        )
        return samples.map { (offset, km, title) ->
            RunRecord(
                title = title, date = PlanCalendar.addDays(Instant.now(), offset), source = RunSource.Gps,
                distanceM = km * 1000, durationSec = (km * (easy - 12)).toInt(), avgHeartRate = 148,
                feeling = Feeling.Good,
            )
        }
    }

    /** An in-memory store with a plan and a few runs. */
    fun store(): AppStore {
        val sample = answers
        val plan = PlanEngine.generate(sample)
        return AppStore(file = null, scope = MainScope()).apply {
            update { AppState(answers = sample, plan = plan, runs = runs(plan), hasCompletedOnboarding = true) }
        }
    }
}
