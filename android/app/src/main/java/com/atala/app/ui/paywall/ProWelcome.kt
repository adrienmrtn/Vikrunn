package com.atala.app.ui.paywall

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atala.app.core.l10n.L
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.components.rememberReduceMotion
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.Theme
import kotlinx.coroutines.delay

private class ProUnlockedItem(val icon: ImageVector, val title: String)

/** "Welcome to Atala Pro": the crown lands, then everything that just unlocked ticks in, one by one. */
@Composable
internal fun ProWelcomeScreen(onClose: () -> Unit) {
    val state by collectAppState()
    val haptics = rememberHaptics()
    val reduceMotion = rememberReduceMotion()
    val items = listOf(
        ProUnlockedItem(Icons.Filled.Forum, L("Your coach, any time")),
        ProUnlockedItem(Icons.Filled.GraphicEq, L("%@'s voice on every run", state.coach.name)),
        ProUnlockedItem(Icons.AutoMirrored.Filled.ShowChart, L("Your progress and predictions")),
        ProUnlockedItem(Icons.AutoMirrored.Filled.ListAlt, L("Every detail of your runs")),
    )
    /** 0 nothing, 1 the crown, 2 the title, then one stage per item, then the button. */
    var stage by remember { mutableIntStateOf(0) }
    val last = items.size + 3

    LaunchedEffect(Unit) {
        if (stage != 0) return@LaunchedEffect
        if (reduceMotion) {
            stage = last
            return@LaunchedEffect
        }
        delay(250)
        haptics.play(HapticKind.Success)
        stage = 1
        delay(550)
        stage = 2
        delay(500)
        for (index in items.indices) {
            haptics.play(HapticKind.Soft)
            stage = index + 3
            delay(380)
        }
        stage = last
    }

    val bouncy = spring<Float>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)
    val crownScale by animateFloatAsState(if (stage >= 1) 1f else 0.3f, bouncy, label = "crown")
    val crownAlpha by animateFloatAsState(if (stage >= 1) 1f else 0f, tween(300), label = "crownAlpha")
    val burst by animateFloatAsState(if (stage >= 1) 1f else 0f, bouncy, label = "burst")
    val titleAlpha by animateFloatAsState(if (stage >= 2) 1f else 0f, tween(400), label = "title")
    val buttonAlpha by animateFloatAsState(if (stage >= last) 1f else 0f, tween(400), label = "button")

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Theme.green, Theme.forest)))) {
        Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f).heightIn(min = 24.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clearAndSetSemantics {}
                    .graphicsLayer {
                        scaleX = crownScale
                        scaleY = crownScale
                        alpha = crownAlpha
                    },
                contentAlignment = Alignment.Center,
            ) {
                Rays(progress = burst, from = 56.dp, to = 98.dp, short = 4.dp, long = 24.dp)
                Box(
                    Modifier
                        .size(132.dp)
                        .shadow(16.dp, CircleShape)
                        .background(Theme.yellow, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(CrownIcon, null, tint = Theme.forest, modifier = Modifier.size(68.dp))
                }
            }
            Column(
                Modifier
                    .padding(top = 18.dp, start = 24.dp, end = 24.dp)
                    .graphicsLayer {
                        alpha = titleAlpha
                        translationY = (1 - titleAlpha) * 14.dp.toPx()
                    }
                    .semantics(mergeDescendants = true) { heading() },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(L("Welcome to Atala Pro,"), style = Theme.font(20, FontWeight.SemiBold), color = Theme.greenOnDark)
                Text(state.firstName, style = Theme.font(38, FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(
                Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items.forEachIndexed { index, item -> UnlockedRow(item, unlocked = stage >= index + 3) }
            }
            Spacer(Modifier.weight(1f).heightIn(min = 24.dp))
            Box(
                Modifier
                    .padding(start = Theme.screenPadding, end = Theme.screenPadding, bottom = 12.dp)
                    .fillMaxWidth()
                    .height(58.dp)
                    .alpha(buttonAlpha)
                    .clip(CircleShape)
                    .background(Color.White, CircleShape)
                    .clickable(role = Role.Button) {
                        haptics.confirm()
                        onClose()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(L("Let's run"), style = Theme.font(17, FontWeight.Bold), color = Theme.forest)
            }
        }
    }
}

/** A locked line that turns into a ticked one when its turn comes. */
@Composable
private fun UnlockedRow(item: ProUnlockedItem, unlocked: Boolean) {
    val pop = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
    val scale by animateFloatAsState(if (unlocked) 1f else 0.86f, pop, label = "scale")
    val tick by animateFloatAsState(if (unlocked) 1f else 0f, pop, label = "tick")
    Row(
        Modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .scale(scale)
                .background(if (unlocked) Theme.yellow else Color.White.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (unlocked) item.icon else Icons.Filled.Lock,
                contentDescription = null,
                tint = if (unlocked) Theme.forest else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            item.title,
            style = Theme.font(17, FontWeight.SemiBold),
            color = if (unlocked) Color.White else Color.White.copy(alpha = 0.45f),
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Filled.Check, null, tint = Theme.yellow, modifier = Modifier.size(16.dp).alpha(tick))
    }
}
