package com.atala.app.ui.main

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.atala.app.ui.coach.CoachScreen
import com.atala.app.ui.guided.GuidedSessionScreen
import com.atala.app.ui.paywall.MainAppOverlays
import com.atala.app.ui.paywall.PaywallPresence
import com.atala.app.ui.paywall.PaywallScreen
import com.atala.app.ui.plan.PlanScreen
import com.atala.app.ui.plan.WorkoutDetailScreen
import com.atala.app.ui.profile.ProfileScreen
import com.atala.app.ui.progress.ProgressScreen
import com.atala.app.ui.run.ManualRunEntryScreen
import com.atala.app.ui.run.RunFlowScreen
import com.atala.app.ui.run.RunSummaryScreen
import com.atala.app.ui.today.TodayScreen
import java.util.UUID

/**
 * The application shell (`MainTabView`): the four tabs with the central "+" in the bar below, and
 * every screen opened from them. Screens get a [Navigation] and never the NavController.
 *
 * Insets: the shell consumes the navigation bar under the tab bar, so every screen can simply use
 * `Modifier.safeDrawingPadding()` (or the status/navigation bar paddings it needs when it draws
 * edge to edge).
 */
/** The app itself. [isSplashDone] is false while the launch splash still covers it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(isSplashDone: Boolean = true) {
    val controller = rememberNavController()
    val nav = remember(controller) { controller.navigation() }
    val entry by controller.currentBackStackEntryAsState()
    val tab = MainTab.entries.firstOrNull { tab ->
        entry?.destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
    }
    // The coach chat's keyboard takes the place of the bar.
    val showsTabBar = tab != null && !WindowInsets.isImeVisible
    var showPlus by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        NavHost(
            navController = controller,
            startDestination = TodayRoute,
            modifier = Modifier
                .weight(1f)
                .then(if (showsTabBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier),
            enterTransition = { fadeIn(tween(220)) },
            // The screen under a modal stays in place while the modal rises over it.
            exitTransition = { if (targetState.isModal()) ExitTransition.KeepUntilTransitionsFinished else fadeOut(tween(220)) },
            popEnterTransition = { if (initialState.isModal()) EnterTransition.None else fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(220)) },
        ) {
            composable<TodayRoute> { TodayScreen(nav) }
            composable<PlanRoute> { PlanScreen(nav) }
            composable<CoachRoute> { CoachScreen(nav) }
            composable<ProgressRoute> { ProgressScreen(nav) }

            pushed<WorkoutDetailRoute> { WorkoutDetailScreen(uuid(it.toRoute<WorkoutDetailRoute>().workoutId), nav) }
            pushed<RunSummaryRoute> { RunSummaryScreen(uuid(it.toRoute<RunSummaryRoute>().runId), nav) }

            modal<RunFlowRoute> { RunFlowScreen(it.toRoute<RunFlowRoute>().workoutId?.let(::uuid), nav) }
            modal<ManualRunRoute> { ManualRunEntryScreen(it.toRoute<ManualRunRoute>().workoutId?.let(::uuid), nav) }
            modal<GuidedSessionRoute> { GuidedSessionScreen(uuid(it.toRoute<GuidedSessionRoute>().workoutId), nav) }
            modal<ProfileRoute> { ProfileScreen(nav) }
            modal<PaywallRoute> { PaywallScreen(onFinished = nav.onBack) }
        }
        if (showsTabBar) {
            MainTabBar(selection = tab, onSelect = controller::selectTab, onPlus = { showPlus = true })
        }
    }

    if (showPlus) {
        MainPlusSheet(
            onDismiss = { showPlus = false },
            onFreeRun = { nav.onStartRun(null) },
            onPastRun = { nav.onAddPastRun(null) },
        )
    }
    // The rating request comes first, once the splash is gone; the welcome gift and the Pro welcome
    // wait behind it (see `ReviewPromptState`), only while a tab is on screen with nothing over it.
    ReviewPrompt(isActive = isSplashDone, canPresent = tab != null && !showPlus && PaywallPresence.count == 0)
    MainAppOverlays(canPresent = tab != null && !showPlus)
}

private fun uuid(value: String): UUID = UUID.fromString(value)

private fun NavHostController.navigation() = Navigation(
    onOpenWorkout = { navigate(WorkoutDetailRoute(it.toString())) },
    onStartRun = { navigate(RunFlowRoute(it?.toString())) },
    onAddPastRun = { navigate(ManualRunRoute(it?.toString())) },
    onOpenRunSummary = { runId ->
        // The finished run flow (or manual entry) gives way to the summary: back returns under it.
        val destination = currentBackStackEntry?.destination
        if (destination?.hasRoute(RunFlowRoute::class) == true || destination?.hasRoute(ManualRunRoute::class) == true) {
            popBackStack()
        }
        navigate(RunSummaryRoute(runId.toString()))
    },
    onStartGuided = { navigate(GuidedSessionRoute(it.toString())) },
    onOpenProfile = { navigate(ProfileRoute) { launchSingleTop = true } },
    onOpenPaywall = { navigate(PaywallRoute) { launchSingleTop = true } },
    onOpenCoach = {
        navigate(CoachRoute) {
            popUpTo(graph.findStartDestination().id)
            launchSingleTop = true
        }
    },
    onBack = { if (previousBackStackEntry != null) popBackStack() },
)

/** Standard bottom-navigation behaviour: one saved back stack per tab. */
private fun NavHostController.selectTab(tab: MainTab) = navigate(tab.route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

private const val DURATION = 320

/** A detail pushed from the side (navigation stack on the iPhone). */
private inline fun <reified T : Any> NavGraphBuilder.pushed(noinline content: @Composable (NavBackStackEntry) -> Unit) =
    composable<T>(
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(DURATION)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(DURATION)) },
    ) { content(it) }

private val modalRoutes = listOf(
    RunFlowRoute::class, ManualRunRoute::class, GuidedSessionRoute::class, ProfileRoute::class, PaywallRoute::class,
)

private fun NavBackStackEntry.isModal() = modalRoutes.any { destination.hasRoute(it) }

/** A full-screen cover or sheet on the iPhone: rises from the bottom, the screen below stays. */
private inline fun <reified T : Any> NavGraphBuilder.modal(noinline content: @Composable (NavBackStackEntry) -> Unit) =
    composable<T>(
        enterTransition = { slideInVertically(tween(DURATION)) { it } },
        popExitTransition = { slideOutVertically(tween(DURATION)) { it } },
    ) { content(it) }
