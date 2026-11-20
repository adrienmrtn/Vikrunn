package com.atala.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atala.app.core.l10n.L
import com.atala.app.core.model.OnboardingAnswers
import com.atala.app.core.model.Weekday
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.theme.Theme

/** What the user answered on the demo card. */
enum class OnboardingPlanChangeChoice { Applied, Kept }

/**
 * The example shown in the onboarding conversation: one session moved to another day.
 * Built from the days the user ticked, so it looks like their own week. Nothing is saved.
 */
data class OnboardingPlanChangeExample(
    /** 1 = Monday ... 7 = Sunday. */
    val fromDay: Int,
    val toDay: Int,
    val title: String,
) {
    val fromName: String get() = Weekday.long(fromDay)
    val toName: String get() = Weekday.long(toDay)
    val userLine: String get() = L("I can't run this %@", fromName)

    companion object {
        fun make(answers: OnboardingAnswers): OnboardingPlanChangeExample {
            val ticked = answers.runDays.filter { it in 1..7 }.toSet().sorted()
            if (ticked.isEmpty()) return OnboardingPlanChangeExample(4, 5, L("Easy run"))
            val longDay = answers.longRunDay ?: ticked.last()
            val easyDays = ticked.filter { it != longDay }

            // Thursday when it is an easy day, otherwise the first easy day, otherwise the only day there is.
            val from = when {
                4 in easyDays -> 4
                easyDays.isNotEmpty() -> easyDays.first()
                else -> ticked.first()
            }
            val title = if (from == longDay || ticked.size == 1) L("Long run") else L("Easy run")

            // The nearest free day after it, then before it; with a full week, simply the day after.
            val to = ((from + 1)..7).firstOrNull { it !in ticked }
                ?: (from - 1 downTo 1).firstOrNull { it !in ticked }
                ?: if (from == 7) 6 else from + 1
            return OnboardingPlanChangeExample(from, to, title)
        }
    }
}

private val PlanChangeDayText = Color(0xFF5A5A5A)

/**
 * "Plan change" card of the onboarding conversation, in the style of the coach chat's proposal card.
 * A demo: the two buttons only resolve the card, the plan does not exist yet.
 */
@Composable
fun OnboardingPlanChangeCard(
    example: OnboardingPlanChangeExample,
    choice: OnboardingPlanChangeChoice?,
    onChoose: (OnboardingPlanChangeChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    val border by animateColorAsState(
        if (choice == OnboardingPlanChangeChoice.Applied) Theme.green.copy(alpha = 0.35f) else Theme.hairline,
        tween(300), label = "card border",
    )
    Column(
        modifier
            .fillMaxWidth()
            .shadow(10.dp, shape, ambientColor = Theme.forest.copy(alpha = 0.06f), spotColor = Theme.forest.copy(alpha = 0.12f))
            .background(Color.White, shape)
            .border(1.dp, border, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(L("Plan change"), style = Theme.font(12, FontWeight.SemiBold), color = Theme.muted, modifier = Modifier.weight(1f).padding(end = 8.dp))
            AnimatedVisibility(choice != null, enter = scaleIn(initialScale = 0.6f) + fadeIn(), exit = fadeOut()) {
                when (choice) {
                    OnboardingPlanChangeChoice.Applied -> PlanChangePill(L("Applied"), Icons.Filled.Check, Theme.green, Theme.greenTint)
                    OnboardingPlanChangeChoice.Kept, null -> PlanChangePill(L("Plan kept"), Icons.Filled.Remove, Theme.muted, Theme.surface)
                }
            }
        }

        AnimatedContent(choice == OnboardingPlanChangeChoice.Applied, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "plan change row") { applied ->
            if (applied) PlanChangeAppliedRow(example) else PlanChangeComparisonRow(example, dimmed = choice == OnboardingPlanChangeChoice.Kept)
        }

        AnimatedContent(choice, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "plan change bottom") { resolved ->
            if (resolved != null) {
                PlanChangeResolvedLabel(resolved)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(L("The rest of your week stays as it is"), style = Theme.font(12), color = Theme.muted)
                    PlanChangeButtons(onChoose)
                }
            }
        }
    }
}

@Composable
private fun PlanChangePill(title: String, icon: ImageVector, tint: Color, background: Color) {
    Row(
        Modifier
            .background(background, CircleShape)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(11.dp))
        Text(title, style = Theme.font(12, FontWeight.SemiBold), color = tint)
    }
}

@Composable
private fun PlanChangeResolvedLabel(choice: OnboardingPlanChangeChoice) {
    val applied = choice == OnboardingPlanChangeChoice.Applied
    val tint = if (applied) Theme.green else Theme.muted
    Row(
        Modifier.heightIn(min = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (applied) Icons.Filled.CheckCircle else Icons.Outlined.RemoveCircleOutline, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(
            if (applied) L("Applied. The rest of your week did not move.") else L("Plan kept. Nothing was changed."),
            style = Theme.font(13, FontWeight.SemiBold),
            color = tint,
        )
    }
}

@Composable
private fun PlanChangeComparisonRow(example: OnboardingPlanChangeExample, dimmed: Boolean) {
    val alpha by animateFloatAsState(if (dimmed) 0.55f else 1f, tween(300), label = "dim")
    val becomes = L("becomes")
    Row(Modifier.alpha(alpha), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        PlanChangeBox(example.fromName, example.title, Theme.greenSoft, Theme.bubble)
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Theme.ink,
            modifier = Modifier.size(16.dp).semantics { contentDescription = becomes },
        )
        PlanChangeBox(example.toName, example.title, Theme.orange, Theme.orangeTint)
    }
}

@Composable
private fun RowScope.PlanChangeBox(day: String, title: String, dot: Color, background: Color) {
    Column(
        Modifier
            .weight(1f)
            .background(background, RoundedCornerShape(14.dp))
            .semantics(mergeDescendants = true) {}
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(dot, CircleShape))
            Text(day, style = Theme.font(11), color = PlanChangeDayText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(title, style = Theme.font(14, FontWeight.SemiBold), color = Theme.ink, maxLines = 2)
    }
}

/** The session on its new day, with what changed underneath. */
@Composable
private fun PlanChangeAppliedRow(example: OnboardingPlanChangeExample) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Theme.greenTint, RoundedCornerShape(14.dp))
            .semantics(mergeDescendants = true) {}
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(example.toName, style = Theme.font(11), color = PlanChangeDayText, maxLines = 1)
        Text(example.title, style = Theme.font(14, FontWeight.SemiBold), color = Theme.ink)
        Text(L("Moved from %@", example.fromName), style = Theme.font(12, FontWeight.Medium), color = Theme.green)
    }
}

@Composable
private fun PlanChangeButtons(onChoose: (OnboardingPlanChangeChoice) -> Unit) {
    val haptics = rememberHaptics()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .weight(1f)
                .height(44.dp)
                .clip(CircleShape)
                .background(Theme.ink, CircleShape)
                .clickable(role = Role.Button) {
                    haptics.play(HapticKind.Success)
                    onChoose(OnboardingPlanChangeChoice.Applied)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(L("Apply"), style = Theme.font(14, FontWeight.SemiBold), color = Color.White)
        }
        Box(
            Modifier
                .weight(1f)
                .height(44.dp)
                .clip(CircleShape)
                .background(Color.White, CircleShape)
                .border(1.dp, Theme.ink, CircleShape)
                .clickable(role = Role.Button) {
                    haptics.tap()
                    onChoose(OnboardingPlanChangeChoice.Kept)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(L("Keep my plan"), style = Theme.font(14, FontWeight.SemiBold), color = Theme.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
