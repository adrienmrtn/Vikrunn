package com.atala.app.ui.main

import java.util.UUID

/**
 * Everything a screen of the main app can open, as plain lambdas. Screens receive one instance and
 * never see the NavController, so they stay previewable and independent of the navigation graph.
 * [MainScreen] builds the real one; previews use [Navigation.Preview], which does nothing.
 *
 * Ids are the model's UUIDs (`Workout.id`, `RunRecord.id`); the routes carry them as strings.
 * To open something new, add a lambda here and a route in Routes.kt (shell owner).
 */
data class Navigation(
    /** Session detail ([com.atala.app.ui.plan.WorkoutDetailScreen]). */
    val onOpenWorkout: (workoutId: UUID) -> Unit,
    /** Run flow: a plan session, or a free run with null. */
    val onStartRun: (workoutId: UUID?) -> Unit,
    /** Manual entry of a run done without the app ("+" > Add a past run, or for a session). */
    val onAddPastRun: (workoutId: UUID?) -> Unit,
    /** Run summary. Called from the run flow, it replaces the flow in the back stack. */
    val onOpenRunSummary: (runId: UUID) -> Unit,
    /** Guided strength or stretching session. */
    val onStartGuided: (workoutId: UUID) -> Unit,
    /** Profile (a sheet on the iPhone, reached from the avatar in the Today and Plan headers). */
    val onOpenProfile: () -> Unit,
    /** Atala Pro paywall, over the current screen. */
    val onOpenPaywall: () -> Unit,
    /** "Talk to my coach": closes what is open and shows the Coach tab. */
    val onOpenCoach: () -> Unit,
    /** Closes the current screen. Does nothing on a tab. */
    val onBack: () -> Unit,
) {
    companion object {
        val Preview = Navigation(
            onOpenWorkout = {}, onStartRun = {}, onAddPastRun = {}, onOpenRunSummary = {}, onStartGuided = {},
            onOpenProfile = {}, onOpenPaywall = {}, onOpenCoach = {}, onBack = {},
        )
    }
}
