package com.atala.app.ui.main

import kotlinx.serialization.Serializable

// Type-safe routes of the main app's NavHost. Ids travel as strings (UUID.toString()).

@Serializable data object TodayRoute
@Serializable data object PlanRoute
@Serializable data object CoachRoute
@Serializable data object ProgressRoute

@Serializable data class WorkoutDetailRoute(val workoutId: String)
@Serializable data class RunFlowRoute(val workoutId: String? = null)
@Serializable data class ManualRunRoute(val workoutId: String? = null)
@Serializable data class RunSummaryRoute(val runId: String)
@Serializable data class GuidedSessionRoute(val workoutId: String)
@Serializable data object ProfileRoute
@Serializable data object PaywallRoute
