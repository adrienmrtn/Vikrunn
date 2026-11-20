package com.atala.app.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atala.app.core.l10n.L
import com.atala.app.core.model.OnboardingAnswers
import com.atala.app.core.model.PlanCalendar
import com.atala.app.core.model.isOpenEnded
import com.atala.app.core.purchases.PurchaseService
import com.atala.app.core.store.AppState
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.main.Navigation
import com.atala.app.ui.root.AtalaPreview
import com.atala.app.ui.root.LocalAppStore
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.Theme
import java.time.Instant

/**
 * Profile and settings (ProfileView.swift, a sheet on the iPhone): identity, coach, goal, training
 * days, paces, voice, subscription and reminders, language, help, account. Goal and training days
 * are edited on a copy, applied only with "Update my plan".
 */
@Composable
fun ProfileScreen(nav: Navigation) {
    val store = LocalAppStore.current
    val state by collectAppState()
    val isPro by PurchaseService.isPro.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    var draft by remember { mutableStateOf(withoutPastRace(store.current.answers)) }
    var confirmUpdate by remember { mutableStateOf(false) }
    var planUpdated by remember { mutableStateOf(false) }
    var editingTarget by rememberSaveable { mutableStateOf(false) }

    fun applyDraft() {
        haptics.play(HapticKind.Success)
        val goal = draft.goal
        val isRace = goal != null && !goal.isOpenEnded
        val hasRace = isRace && draft.hasRace && draft.raceDate != null
        val km = goal?.distanceKm
        val target = draft.targetSeconds
        val days = draft.runDays.sorted()
        store.updateAnswers { answers ->
            answers.copy(
                goal = goal,
                hasRace = hasRace,
                raceDate = if (hasRace) draft.raceDate?.let { PlanCalendar.startOfDay(it) } else null,
                targetSeconds = if (km != null && target != null) ProfileTimeRules.clamp(target, km) else null,
                runDays = days,
                longRunDay = draft.longRunDay?.takeIf { it in days },
            )
        }
        if (store.current.plan != null && store.current.profilePlanEnded) {
            // A finished plan: the next one also takes the level reached into account.
            store.profileBuildNextPlan()
        } else {
            // Keeps what was done this week and never marks earlier days as missed.
            store.profileRebuildPlan()
        }
        draft = withoutPastRace(store.current.answers)
        planUpdated = true
    }

    BackHandler(enabled = editingTarget) { editingTarget = false }

    val goal = draft.goal
    AnimatedContent(
        targetState = editingTarget && goal != null && !goal.isOpenEnded,
        transitionSpec = {
            if (targetState) {
                (slideInHorizontally(tween(300)) { it } + fadeIn(tween(300))) togetherWith fadeOut(tween(200))
            } else {
                fadeIn(tween(250)) togetherWith (slideOutHorizontally(tween(300)) { it } + fadeOut(tween(300)))
            }
        },
        label = "profilePage",
        modifier = Modifier.fillMaxSize().background(formBackground),
    ) { target ->
        if (target && goal != null) {
            ProfilePage(
                title = L("Target time"),
                large = false,
                leading = {
                    Row(
                        Modifier.clickable { haptics.tap(); editingTarget = false }.padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = Theme.green, modifier = Modifier.size(28.dp))
                        Text(L("Profile"), style = Theme.font(17), color = Theme.green)
                    }
                },
            ) {
                ProfileTargetTimeEditor(draft.targetSeconds, goal, state.answers) { draft = draft.copy(targetSeconds = it) }
            }
        } else {
            ProfilePage(
                title = L("Profile"),
                large = true,
                trailing = {
                    Text(
                        L("Done"), style = Theme.font(17, FontWeight.SemiBold), color = Theme.green,
                        modifier = Modifier.clickable { haptics.tap(); nav.onBack() }.padding(vertical = 10.dp),
                    )
                },
            ) {
                ProfileIdentitySection(state)
                ProfileCoachSection(state)
                ProfileGoalSection(draft, onDraft = { draft = it }, onOpenTarget = { editingTarget = true })
                ProfileDaysSection(draft, onDraft = { draft = it })
                ProfileUpdateSection(
                    state = state,
                    draft = draft,
                    planUpdated = planUpdated,
                    onBuild = { applyDraft() },
                    onUpdate = {
                        // The sessions to come are replaced: the confirmation opens with a warning.
                        haptics.play(HapticKind.Warning)
                        confirmUpdate = true
                    },
                    onDiscard = {
                        haptics.tap()
                        draft = withoutPastRace(state.answers)
                    },
                )
                ProfilePacesSection(state)
                ProfileVoiceSection(state, isPro, nav.onOpenPaywall)
                ProfileSubscriptionSection(isPro, nav.onOpenPaywall)
                ProfileHelpSection()
                ProfileAccountSection(state)
                ProfileFooterSection()
            }
        }
    }

    if (confirmUpdate) {
        AlertDialog(
            onDismissRequest = { confirmUpdate = false },
            containerColor = Color.White,
            title = { Text(L("Update your plan?"), style = Theme.font(18, FontWeight.SemiBold), color = Theme.ink) },
            text = {
                Text(
                    L("Your plan will be rebuilt from today with your new goal and training days. The sessions still to come are replaced. What you already did this week stays done, and your runs stay in your history."),
                    style = Theme.font(15), color = Theme.body,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmUpdate = false
                    applyDraft()
                }) { Text(L("Update my plan"), color = Theme.green, style = Theme.font(15, FontWeight.SemiBold)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUpdate = false }) { Text(L("Cancel"), color = Theme.ink) }
            },
        )
    }
}

/** The form page: a bar with its buttons, the title (large, scrolling with the content, or inline). */
@Composable
private fun ProfilePage(
    title: String,
    large: Boolean,
    leading: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(formBackground).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 16.dp)) {
            Box(Modifier.align(Alignment.CenterStart)) { leading() }
            if (!large) Text(title, style = Theme.font(17, FontWeight.SemiBold), color = Theme.ink, modifier = Modifier.align(Alignment.Center))
            Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
        }
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            if (large) {
                Text(title, style = Theme.title(34), color = Theme.ink, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp, start = 4.dp))
            } else {
                Box(Modifier.height(16.dp))
            }
            content()
        }
    }
}

/** "Build my plan", "Build my next plan", "Update my plan" / "Discard changes", or "up to date". */
@Composable
private fun ProfileUpdateSection(
    state: AppState,
    draft: OnboardingAnswers,
    planUpdated: Boolean,
    onBuild: () -> Unit,
    onUpdate: () -> Unit,
    onDiscard: () -> Unit,
) {
    val canBuild = draft.goal != null && draft.runDays.isNotEmpty()
    val changed = hasPlanChanges(draft, state.answers)
    when {
        state.plan == null -> FormSection(
            footer = if (canBuild) L("Your plan is built from your goal and your training days.")
            else L("Choose a goal and at least one training day to build your plan."),
        ) {
            FormButtonRow(L("Build my plan"), bold = true, enabled = canBuild, onClick = onBuild)
        }
        state.profilePlanEnded && !changed -> FormSection(
            footer = L("Your plan is finished. The next one starts this week, from your level today. Change the goal or the days above first if you want to."),
        ) {
            FormButtonRow(L("Build my next plan"), bold = true, enabled = canBuild, onClick = onBuild)
        }
        changed -> FormSection(
            footer = if (canBuild) L("Your changes to the goal and training days apply once you update your plan.")
            else L("Keep a goal and at least one training day to update your plan."),
        ) {
            FormButtonRow(L("Update my plan"), bold = true, enabled = canBuild, onClick = onUpdate)
            FormDivider()
            FormButtonRow(L("Discard changes"), onClick = onDiscard)
        }
        planUpdated -> FormSection {
            FormRow {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Theme.green, modifier = Modifier.size(20.dp))
                    Text(L("Your plan is up to date"), style = Theme.font(17), color = Theme.green)
                }
            }
        }
    }
}

private fun hasPlanChanges(draft: OnboardingAnswers, answers: OnboardingAnswers): Boolean {
    val saved = withoutPastRace(answers)
    if (draft.goal != saved.goal) return true
    if (draft.runDays.sorted() != saved.runDays.sorted()) return true
    if (draft.longRunDay != saved.longRunDay) return true
    if (draft.goal.isOpenEnded) return false
    if (draft.targetSeconds != saved.targetSeconds) return true
    if (draft.hasRace != saved.hasRace) return true
    if (draft.hasRace) {
        val new = draft.raceDate
        val old = saved.raceDate
        if (new != null && old != null) {
            if (!PlanCalendar.isSameDay(new, old)) return true
        } else if (new != null || old != null) {
            return true
        }
    }
    return false
}

/** A race date that is already behind us is no longer a race to prepare. */
private fun withoutPastRace(answers: OnboardingAnswers): OnboardingAnswers {
    val today = PlanCalendar.startOfDay(Instant.now())
    val date = answers.raceDate
    return if (answers.hasRace && date != null && PlanCalendar.startOfDay(date) < today) {
        answers.copy(hasRace = false, raceDate = null)
    } else {
        answers
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun ProfileScreenPreview() {
    AtalaPreview { ProfileScreen(Navigation.Preview) }
}

