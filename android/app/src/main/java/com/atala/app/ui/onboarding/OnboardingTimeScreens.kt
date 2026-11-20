package com.atala.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.GoalType
import com.atala.app.core.model.RecentResult
import com.atala.app.core.plan.PlanEngine
import com.atala.app.ui.components.CoachNote
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.root.LocalAppStore
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.Theme
import kotlin.math.abs
import kotlin.math.roundToInt

/** Bounds and steps of the time pickers, per race distance (`OnboardingTimeRules`). */
object OnboardingTimeRules {
    fun range(km: Double): IntRange = when {
        km < 7.5 -> (12 * 60)..(70 * 60)
        km < 15 -> (25 * 60)..(140 * 60)
        else -> (60 * 60)..(215 * 60)
    }

    fun clamp(seconds: Int, km: Double): Int = seconds.coerceIn(range(km))

    fun defaultSeconds(km: Double): Int = when {
        km < 7.5 -> 30 * 60
        km < 15 -> 60 * 60
        else -> 130 * 60
    }

    fun step(km: Double): Int = when {
        km < 7.5 -> 5
        km < 15 -> 10
        else -> 30
    }
}

/** Screen 8 (step `Recent`): a recent result (distance and time), or none. */
@Composable
fun OnboardingRecentScreen() {
    val store = LocalAppStore.current
    val nav = LocalOnboardingNavigator.current
    val haptics = rememberHaptics()
    val saved = remember { store.current.answers.recentResult }
    var distanceKm by remember { mutableDoubleStateOf(saved?.distanceKm ?: 10.0) }
    var seconds by remember { mutableIntStateOf(saved?.seconds ?: OnboardingTimeRules.defaultSeconds(10.0)) }
    var edited by remember { mutableStateOf(saved != null) }
    var showWheel by remember { mutableStateOf(false) }
    val distances = listOf("5 km" to 5.0, "10 km" to 10.0, L("Half") to 21.0975)

    fun setTime(value: Int) {
        seconds = OnboardingTimeRules.clamp(value, distanceKm)
        edited = true
        store.updateAnswers { it.copy(recentResult = RecentResult(distanceKm, seconds)) }
    }

    fun setDistance(km: Double) {
        if (abs(km - distanceKm) <= 0.5) return
        val previousKm = maxOf(1.0, distanceKm)
        distanceKm = km
        if (edited) {
            // Keep the same pace when switching distance, as a sensible starting point.
            val pace = seconds / previousKm
            seconds = OnboardingTimeRules.clamp((pace * km).roundToInt(), km)
            store.updateAnswers { it.copy(recentResult = RecentResult(km, seconds)) }
        } else {
            seconds = OnboardingTimeRules.defaultSeconds(km)
        }
    }

    OnboardingScaffold(
        title = L("A recent result?"),
        subtitle = L("A race or an all-out run from the last 3 months."),
        canContinue = edited,
    ) {
        OnboardingSegmentedPicker(
            options = distances.map { it.first },
            selectedIndex = distances.indexOfFirst { abs(it.second - distanceKm) < 0.5 },
            onSelect = { setDistance(distances[it].second) },
        )
        OnboardingTimeCard(
            seconds = seconds,
            caption = if (edited) {
                L("that's %@ per km", OnboardingCopy.pacePerKm(seconds, distanceKm))
            } else {
                L("tap to set your time")
            },
            stepSeconds = OnboardingTimeRules.step(distanceKm),
            dimmed = !edited,
            onChange = ::setTime,
            onTapTime = { showWheel = true },
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable(role = Role.Button) {
                    haptics.tap()
                    store.updateAnswers { it.copy(recentResult = null) }
                    edited = false
                    nav.next()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                L("I don't have one"),
                style = Theme.font(15, FontWeight.SemiBold).copy(textDecoration = TextDecoration.Underline),
                color = Theme.ink,
            )
        }
    }
    if (showWheel) {
        OnboardingTimeWheelSheet(
            title = L("Your time"),
            showsHours = distanceKm > 15,
            initialSeconds = seconds,
            onDone = ::setTime,
            onDismiss = { showWheel = false },
        )
    }
}

/** Screen 14 (step `Target`): target time for the goal distance. */
@Composable
fun OnboardingTargetScreen() {
    val store = LocalAppStore.current
    val state by collectAppState()
    val goal = state.answers.goal ?: GoalType.TenK
    val km = goal.distanceKm ?: 10.0
    val todaySeconds = PlanEngine.predictedSeconds(PlanEngine.estimateVma(state.answers), goal)
        ?: OnboardingTimeRules.defaultSeconds(km)
    val saved = remember { store.current.answers.targetSeconds }
    // null until the user picks one of the two modes.
    var precise by remember { mutableStateOf(if (saved != null) true else null) }
    var seconds by remember {
        // Start from today's level, rounded down to the minute.
        mutableIntStateOf(OnboardingTimeRules.clamp(saved ?: (todaySeconds / 60 * 60), km))
    }
    var showWheel by remember { mutableStateOf(false) }

    fun choose(wantsTime: Boolean) {
        precise = wantsTime
        store.updateAnswers { it.copy(targetSeconds = if (wantsTime) seconds else null) }
    }

    fun setTime(value: Int) {
        seconds = OnboardingTimeRules.clamp(value, km)
        store.updateAnswers { it.copy(targetSeconds = seconds) }
    }

    val note = run {
        val pace = OnboardingCopy.pacePerKm(seconds, km)
        val time = Fmt.goalTime(seconds)
        val distance = Fmt.km(km)
        // Without a recent result there is no "today" to compare the target with.
        if (!OnboardingNavigator.hasRecentResult(state.answers)) {
            return@run L("%@ is %@ per km for %@. I'll build your plan towards it, step by step.", time, pace, distance)
        }
        val reachable = (todaySeconds * 0.95).toInt()
        when {
            seconds < reachable -> L(
                "%@ is %@ per km for %@. That's ambitious from where you are today (around %@). I keep it as your goal and train you towards %@ first, so you stay safe.",
                time, pace, distance, Fmt.goalTime(todaySeconds), Fmt.goalTime(reachable),
            )
            seconds <= todaySeconds -> L("%@ is %@ per km for %@. A real step up from today, and within reach.", time, pace, distance)
            else -> L("%@ is %@ per km for %@. Comfortable for your level: a good way to enjoy the day.", time, pace, distance)
        }
    }

    OnboardingScaffold(title = L("What time are you aiming for?"), canContinue = precise != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OnboardingChoiceButton(L("Just finish"), isOn = precise == false) { choose(false) }
            OnboardingChoiceButton(L("A precise time"), isOn = precise == true) { choose(true) }
        }
        AnimatedContent(
            targetState = precise,
            transitionSpec = {
                fadeIn(tween(250, easing = OnboardingEasing.easeInOut)) togetherWith fadeOut(tween(200))
            },
            label = "target mode",
        ) { mode ->
            when (mode) {
                true -> Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    OnboardingTimeCard(
                        seconds = seconds,
                        caption = L("for your %@", OnboardingCopy.goalNoun(goal)),
                        stepSeconds = if (km > 15) 60 else 15,
                        onChange = ::setTime,
                        onTapTime = { showWheel = true },
                    )
                    CoachNote(state.coach, note)
                }
                false -> CoachNote(
                    state.coach,
                    L("No clock to chase. I'll get you to the finish line feeling good, and that's a goal in itself."),
                )
                null -> Box(Modifier.fillMaxWidth())
            }
        }
    }
    if (showWheel) {
        OnboardingTimeWheelSheet(
            title = L("Your target"),
            showsHours = km > 15,
            initialSeconds = seconds,
            onDone = ::setTime,
            onDismiss = { showWheel = false },
        )
    }
}
