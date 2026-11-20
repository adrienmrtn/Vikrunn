package com.atala.app.ui.onboarding

import androidx.compose.runtime.Composable

/** Maps a step to its screen. Each screen lives in the file named after its Swift counterpart. */
@Composable
fun OnboardingStepHost(step: OnboardingStep) {
    when (step) {
        OnboardingStep.Welcome -> OnboardingWelcomeScreen()
        OnboardingStep.FirstName -> OnboardingFirstNameScreen()
        OnboardingStep.Language -> OnboardingLanguageScreen()
        OnboardingStep.Intro -> OnboardingIntroScreen()
        OnboardingStep.Community -> OnboardingCommunityScreen()
        OnboardingStep.BeyondCoach -> OnboardingBeyondCoachScreen()
        OnboardingStep.PlanMatters -> OnboardingPlanMattersScreen()
        OnboardingStep.Ready -> OnboardingReadyScreen()
        OnboardingStep.Notifications -> OnboardingNotificationsScreen()
        OnboardingStep.Coach -> OnboardingCoachScreen()
        OnboardingStep.Hello -> OnboardingHelloScreen()
        OnboardingStep.GoalIntro -> OnboardingGoalIntroScreen()
        OnboardingStep.CoachReply -> OnboardingCoachReplyScreen()
        OnboardingStep.Gender -> OnboardingGenderScreen()
        OnboardingStep.Experience -> OnboardingExperienceScreen()
        OnboardingStep.Volume -> OnboardingVolumeScreen()
        OnboardingStep.Health -> OnboardingHealthScreen()
        OnboardingStep.Mirror -> OnboardingMirrorScreen()
        OnboardingStep.Recent -> OnboardingRecentScreen()
        OnboardingStep.Target -> OnboardingTargetScreen()
        OnboardingStep.LevelIntro -> OnboardingLevelIntroScreen()
        OnboardingStep.LevelFocus -> OnboardingLevelFocusScreen()
        OnboardingStep.Goal -> OnboardingGoalScreen()
        OnboardingStep.Race -> OnboardingRaceScreen()
        OnboardingStep.Why -> OnboardingWhyScreen()
        OnboardingStep.Consistency -> OnboardingConsistencyScreen()
        OnboardingStep.Days -> OnboardingDaysScreen()
        OnboardingStep.LongRun -> OnboardingLongRunScreen()
        OnboardingStep.Constraints -> OnboardingConstraintsScreen()
        OnboardingStep.Tone -> OnboardingToneScreen()
        OnboardingStep.Message -> OnboardingMessageScreen()
        OnboardingStep.Projection -> OnboardingProjectionScreen()
        OnboardingStep.Reviews -> OnboardingReviewsScreen()
        OnboardingStep.SignIn -> OnboardingSignInScreen()
        OnboardingStep.Loading -> OnboardingLoadingScreen()
        OnboardingStep.PlanReady -> OnboardingPlanReadyScreen()
        OnboardingStep.Commitment -> OnboardingCommitmentScreen()
        OnboardingStep.Trial -> OnboardingTrialScreen()
        OnboardingStep.Timeline -> OnboardingTimelineScreen()
        OnboardingStep.Paywall -> OnboardingPaywallScreen()
        OnboardingStep.Permissions -> OnboardingPermissionsScreen()
        OnboardingStep.HealthConnect -> OnboardingHealthConnectScreen()
        // Out of the flow: `restore` never lands on them.
        OnboardingStep.LevelRisk, OnboardingStep.Reminder -> Unit
    }
}
