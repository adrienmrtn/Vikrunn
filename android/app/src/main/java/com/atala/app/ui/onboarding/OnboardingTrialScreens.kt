package com.atala.app.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atala.app.core.l10n.L
import com.atala.app.core.plan.PlanEngine
import com.atala.app.ui.components.CoachAvatar
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.Theme

// PLACEHOLDER OFFER — shown until the store's real offers are loaded (`PurchaseService.offers`).
object OnboardingPaywallOffer {
    const val TRIAL_DAYS = 3
    /** The yearly plan, per month and in full. */
    const val MONTHLY_PRICE = "€4.17"
    const val YEARLY_PRICE = "€49.99"
    /** The monthly plan. */
    const val MONTH_PRICE = "€9.99"
    const val YEARLY_SAVING = 58
    /** The welcome gift: yearly at half price, per week and in full. */
    const val GIFT_WEEKLY_PRICE = "€0.48"
    const val GIFT_YEARLY_PRICE = "€24.99"
}

/**
 * Screen 28 (step `Trial`): invitation to the free trial. The headline writes itself word by word,
 * then the rest settles and the button comes last.
 */
@Composable
fun OnboardingTrialScreen() {
    val state by collectAppState()
    var emphasised by remember { mutableStateOf(false) }
    // 1 subtitle, 2-4 benefits, 5 button.
    val delays = listOf(0.15, 0.35, 0.18, 0.18, 0.45)
    val stage = rememberOnboardingStages(delays, isActive = emphasised)
    val weeks = state.plan?.weeks?.size ?: PlanEngine.MAX_WEEKS
    val benefits = listOf(
        L("Your %@ of plan, session by session", OnboardingCopy.weeks(weeks)),
        L("%@ to explain every run and answer your questions", state.coach.name),
        L("Voice guidance during your runs"),
    )
    OnboardingScaffold(buttonsVisible = stage >= delays.size) {
        CoachAvatar(state.coach, size = 96.dp)
        // The part between asterisks is the one shown in colour.
        OnboardingEmphasisText(
            L("That's why we really want you to try Atala Pro *for free.*"),
            size = 32,
            lineSpacing = 5.dp,
            isHeading = true,
            onFinished = { emphasised = true },
        )
        Text(
            L("So we can reach your goal together. %@ days, every feature, nothing to pay today.", OnboardingPaywallOffer.TRIAL_DAYS),
            style = Theme.font(17).copy(lineHeight = 25.sp),
            color = Color(0xFF5A5A5A),
            modifier = Modifier.onboardingStaged(0, stage),
        )
        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            benefits.forEachIndexed { index, benefit ->
                Row(
                    Modifier
                        .onboardingStaged(index + 1, stage)
                        .semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Theme.green, modifier = Modifier.size(22.dp))
                    Text(benefit, style = Theme.font(16), color = Theme.ink, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private data class TimelineItem(val id: Int, val icon: ImageVector, val color: Color, val title: String, val text: String)

/**
 * Screen 29 (step `Timeline`): how the free trial goes. The timeline builds itself: a node and its
 * text, the line grows down to the next node, and so on; the button comes last.
 */
@Composable
fun OnboardingTimelineScreen() {
    // Odd stages bring a node and its text, even stages grow the line under it; the last one shows the button.
    val delays = listOf(0.45, 0.4, 0.5, 0.4, 0.5, 0.55)
    val stage = rememberOnboardingStages(delays)
    val days = OnboardingPaywallOffer.TRIAL_DAYS
    val items = listOf(
        TimelineItem(0, Icons.Filled.LockOpen, Theme.green, L("Today"),
            L("You unlock everything: your full plan, your coach, voice guidance.")),
        TimelineItem(1, Icons.AutoMirrored.Filled.DirectionsRun, Theme.yellow, L("During your trial"),
            L("Run your first sessions with your coach and see how it feels.")),
        TimelineItem(2, Icons.Filled.Star, Theme.orange, L("In %@ days", days),
            L("Your subscription starts and you carry on with Atala. Cancel before and you pay nothing.")),
    )
    OnboardingScaffold(title = L("How your free trial works"), buttonsVisible = stage >= delays.size) {
        Column(Modifier.padding(top = 8.dp)) {
            items.forEach { item -> TimelineRow(item, isLast = item.id == items.lastIndex, stage = stage) }
        }
    }
}

@Composable
private fun TimelineRow(item: TimelineItem, isLast: Boolean, stage: Int) {
    val shown = stage > item.id * 2
    val lineGrown = stage > item.id * 2 + 1
    val node by animateFloatAsState(if (shown) 1f else 0f, tween(450, easing = OnboardingEasing.easeOut), label = "node")
    val line by animateFloatAsState(if (lineGrown) 1f else 0f, tween(450, easing = OnboardingEasing.easeOut), label = "line")
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            Modifier
                .width(44.dp)
                .clearAndSetSemantics {},
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .graphicsLayer {
                        val scale = 0.5f + 0.5f * node
                        scaleX = scale
                        scaleY = scale
                        alpha = node
                    }
                    .background(item.color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(item.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            if (!isLast) {
                Box(
                    Modifier
                        .width(4.dp)
                        .weight(1f)
                        .graphicsLayer {
                            scaleY = line
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        }
                        .background(item.color.copy(alpha = 0.4f)),
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(top = 2.dp, bottom = if (isLast) 0.dp else 34.dp)
                .onboardingStaged(shown, rise = 10f)
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(item.title, style = Theme.font(18, FontWeight.Bold), color = Theme.ink)
            Text(item.text, style = Theme.font(15).copy(lineHeight = 21.sp), color = Color(0xFF5A5A5A))
        }
    }
}
