@file:UseSerializers(InstantSerializer::class)

package com.atala.app.core.model

import com.atala.app.core.l10n.L
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant

// Raw values (`@SerialName`) are the Swift case names: answers saved on either phone decode on the other.

@Serializable
enum class Gender {
    @SerialName("woman") Woman,
    @SerialName("man") Man,
    @SerialName("other") Other,
    @SerialName("undisclosed") Undisclosed;

    val title: String
        get() = when (this) {
            Woman -> L("Woman")
            Man -> L("Man")
            Other -> L("Non-binary")
            Undisclosed -> L("Prefer not to say")
        }
}

@Serializable
enum class GoalType {
    @SerialName("fiveK") FiveK,
    @SerialName("tenK") TenK,
    @SerialName("half") Half,
    @SerialName("regular") Regular,
    @SerialName("shape") Shape;

    val title: String
        get() = when (this) {
            FiveK -> L("5K")
            TenK -> L("10K")
            Half -> L("Half marathon")
            Regular -> L("Run regularly")
            Shape -> L("Get in shape")
        }

    val subtitle: String
        get() = when (this) {
            FiveK -> L("Your first distance")
            TenK -> L("The great classic")
            Half -> L("21.1 km")
            Regular -> L("A steady habit, no race planned")
            Shape -> L("I just want to feel fit and strong")
        }

    /** Race distance in kilometres, null for the goals without a race. */
    val distanceKm: Double?
        get() = when (this) {
            FiveK -> 5.0
            TenK -> 10.0
            Half -> 21.0975
            Regular, Shape -> null
        }

    val vignette: String
        get() = when (this) {
            FiveK -> "vignette_goal_5k"
            TenK -> "vignette_goal_10k"
            Half -> "vignette_goal_half"
            Regular -> "vignette_goal_regular"
            Shape -> "vignette_why_athletic"
        }

    /** True for the goals with no race distance, date or target time ("run regularly", "get in shape"). */
    val isOpenEnded: Boolean get() = this == Regular || this == Shape
}

/** False while no goal is chosen. */
val GoalType?.isOpenEnded: Boolean get() = this?.isOpenEnded ?: false

@Serializable
enum class Experience {
    @SerialName("starting") Starting,
    @SerialName("underSixMonths") UnderSixMonths,
    @SerialName("sixMonthsToTwoYears") SixMonthsToTwoYears,
    @SerialName("overTwoYears") OverTwoYears;

    val title: String
        get() = when (this) {
            Starting -> L("I'm just starting")
            UnderSixMonths -> L("Less than 6 months")
            SixMonthsToTwoYears -> L("6 months to 2 years")
            OverTwoYears -> L("More than 2 years")
        }

    val vignette: String
        get() = when (this) {
            Starting -> "vignette_exp_1"
            UnderSixMonths -> "vignette_exp_2"
            SixMonthsToTwoYears -> "vignette_exp_3"
            OverTwoYears -> "vignette_exp_4"
        }
}

@Serializable
enum class WeeklyVolume {
    @SerialName("notYet") NotYet,
    @SerialName("under10") Under10,
    @SerialName("from10to25") From10To25,
    @SerialName("over25") Over25;

    val title: String
        get() = when (this) {
            NotYet -> L("I don't run yet")
            Under10 -> L("Less than 10 km")
            From10To25 -> L("10 to 25 km")
            Over25 -> L("More than 25 km")
        }

    val vignette: String
        get() = when (this) {
            NotYet -> "vignette_exp_1"
            Under10 -> "vignette_volume_1"
            From10To25 -> "vignette_volume_2"
            Over25 -> "vignette_volume_3"
        }
}

@Serializable
enum class Motivation {
    @SerialName("athletic") Athletic,
    @SerialName("prove") Prove,
    @SerialName("record") Record,
    @SerialName("health") Health,
    @SerialName("clearHead") ClearHead,
    @SerialName("together") Together;

    val title: String
        get() = when (this) {
            Athletic -> L("Get in better shape")
            Prove -> L("Prove to myself I can do it")
            Record -> L("Beat my record")
            Health -> L("Take care of my health")
            ClearHead -> L("Clear my head")
            Together -> L("Run with people I love")
        }

    val subtitle: String
        get() = when (this) {
            Athletic -> L("Look and feel stronger")
            Prove -> L("See something through")
            Record -> L("Do better than last time")
            Health -> L("Move more, feel better")
            ClearHead -> L("Switch off from the day")
            Together -> L("Share the effort")
        }

    /** The coach's adaptive answer shown right after the "why" question. */
    val coachReply: String
        get() = when (this) {
            Athletic -> L("I'll get you there, one session at a time.")
            Prove -> L("I'll help you prove it to yourself.")
            Record -> L("We'll go and get that record together.")
            Health -> L("We'll build a habit your body thanks you for.")
            ClearHead -> L("I'll make every run a breath of fresh air.")
            Together -> L("I'll get you ready to enjoy every shared kilometre.")
        }

    val vignette: String
        get() = when (this) {
            Athletic -> "vignette_why_athletic"
            Prove -> "vignette_why_prove"
            Record -> "vignette_why_record"
            Health -> "vignette_why_health"
            ClearHead -> "vignette_why_clear_head"
            Together -> "vignette_why_together"
        }
}

@Serializable
enum class Constraint {
    @SerialName("pastInjury") PastInjury,
    @SerialName("travel") Travel,
    @SerialName("treadmill") Treadmill,
    @SerialName("strength") Strength,
    @SerialName("irregularSleep") IrregularSleep;

    val title: String
        get() = when (this) {
            PastInjury -> L("A past injury")
            Travel -> L("I travel often")
            Treadmill -> L("I sometimes run on a treadmill")
            Strength -> L("I already do strength training")
            IrregularSleep -> L("Irregular hours or sleep")
        }

    val vignette: String
        get() = when (this) {
            PastInjury -> "vignette_constraint_injury"
            Travel -> "vignette_constraint_travel"
            Treadmill -> "vignette_constraint_treadmill"
            Strength -> "vignette_constraint_strength"
            IrregularSleep -> "vignette_constraint_sleep"
        }
}

@Serializable
enum class CoachTone {
    @SerialName("kind") Kind,
    @SerialName("direct") Direct,
    @SerialName("playful") Playful;

    val title: String
        get() = when (this) {
            Kind -> L("Kind")
            Direct -> L("Direct")
            Playful -> L("Playful")
        }

    val example: String
        get() = when (this) {
            Kind -> L("Nice work today. Rest well, you earned it.")
            Direct -> L("Session done. Tomorrow: easy run, no excuses.")
            Playful -> L("Boom. Those 800s never saw you coming.")
        }
}

@Serializable
data class RecentResult(val distanceKm: Double, val seconds: Int)

/** Everything the questionnaire collects. Stored on the phone from the first answer. */
@Serializable
data class OnboardingAnswers(
    val firstName: String = "",
    @SerialName("coachID") val coachId: Int = 3,
    val gender: Gender? = null,
    val experience: Experience? = null,
    val weeklyVolume: WeeklyVolume? = null,
    val recentResult: RecentResult? = null,
    val healthFlags: List<String> = emptyList(),
    val goal: GoalType? = null,
    val hasRace: Boolean = false,
    val raceDate: Instant? = null,
    val targetSeconds: Int? = null,
    val motivation: Motivation? = null,
    /** Days the user can run, 1 = Monday ... 7 = Sunday. One ticked day = one session. */
    val runDays: List<Int> = emptyList(),
    val longRunDay: Int? = null,
    val constraints: List<Constraint> = emptyList(),
    val tone: CoachTone = CoachTone.Kind,
)
