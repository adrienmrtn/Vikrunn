package com.atala.app.ui.onboarding

/**
 * Every screen of the entry flow. [rawValue] is what `AppState.onboardingStep` stores (the same
 * numbers as the iPhone), so it identifies a screen for good: never renumber or reuse one. New
 * screens get a new raw value; the order in which screens are shown lives in [order].
 */
enum class OnboardingStep(val rawValue: Int) {
    Welcome(0), FirstName(1), Coach(2), Hello(3), Gender(4), Experience(5), Volume(6), Recent(7), Health(8), Mirror(9),
    GoalIntro(10), Goal(11), Race(12), Target(13), Why(14), CoachReply(15),
    Days(16), LongRun(17), Constraints(18), Message(19), Tone(20), Projection(21), Reviews(22),
    SignIn(23), Loading(24), PlanReady(25), Commitment(26), Trial(27), Timeline(28), Reminder(29), Paywall(30),
    Permissions(31), HealthConnect(32),
    // Added after the first release. Their place in the flow is set by `order`.
    LevelIntro(33), LevelRisk(34), LevelFocus(35), Consistency(36), Intro(37), Community(38), BeyondCoach(39),
    PlanMatters(40), Ready(41), Notifications(42), Language(43);

    /** Index of the step in [order]: use it, never the raw value, to compare two steps. */
    val position: Int get() = positions[this] ?: 0

    /** Full-screen coach "explainer" screens (tap to continue, circular reveal): no progress bar. */
    val isTapScreen: Boolean get() = this == Hello || this == GoalIntro || this == CoachReply

    /** The bar runs from the first name to the reviews. */
    val showsProgress: Boolean
        get() = position >= FirstName.position && position <= Reviews.position && !isTapScreen

    /** No way back from these: no back button, and `back` does nothing. */
    val allowsBack: Boolean
        get() = when (this) {
            Welcome, Loading, PlanReady, Paywall, Permissions, Notifications, HealthConnect -> false
            else -> true
        }

    /** The flow's fixed header shows its back chevron (explainer screens draw their own, in white). */
    val showsHeaderBack: Boolean get() = allowsBack && !isTapScreen

    companion object {
        /**
         * The screens in the order they are shown. `LevelRisk` (34) and `Reminder` (29) were removed
         * from the flow: their raw values stay reserved and a step saved on one of them resumes on
         * the next screen.
         */
        val order: List<OnboardingStep> = listOf(
            Welcome, Language,
            FirstName, Intro, Coach, Hello, Gender, Experience, Volume, Recent, Health, Mirror,
            LevelIntro, LevelFocus,
            GoalIntro, Goal, Race, Target, Why, CoachReply, Consistency,
            Days, LongRun, Constraints, Message, Tone, Projection, Reviews, Community,
            SignIn, Loading, PlanReady, Ready, Commitment, BeyondCoach, PlanMatters, Trial, Timeline, Paywall,
            Permissions, Notifications, HealthConnect,
        )

        private val positions: Map<OnboardingStep, Int> = order.withIndex().associate { (index, step) -> step to index }

        fun fromRawValue(rawValue: Int): OnboardingStep? = entries.firstOrNull { it.rawValue == rawValue }
    }
}
