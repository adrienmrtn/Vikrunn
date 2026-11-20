package com.atala.app.ui.run

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.atala.app.core.l10n.L
import com.atala.app.core.model.Coach
import com.atala.app.ui.components.CoachAvatar
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.theme.Theme
import kotlinx.coroutines.delay

/** Green full-screen 3-2-1 (RunCountdownScreen.swift). Tapping anywhere starts right away. */
@Composable
fun RunCountdownScreen(coach: Coach, heading: String, line: String, onDone: () -> Unit) {
    val haptics = rememberHaptics()
    var count by remember { mutableIntStateOf(3) }
    var finished by remember { mutableStateOf(false) }
    fun finish() {
        if (finished) return
        finished = true
        onDone()
    }
    LaunchedEffect(Unit) {
        for (value in listOf(3, 2, 1)) {
            count = value
            // Light, light, medium; the start itself is heavy (played by the run flow).
            haptics.play(if (value == 1) HapticKind.Confirm else HapticKind.Tap)
            delay(1000)
        }
        finish()
    }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val progress by pulse.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1000, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "ring",
    )
    val label = L("Start right away")
    Box(
        Modifier
            .fillMaxSize()
            .background(Theme.green)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { finish() }
            .semantics { contentDescription = label },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = Theme.screenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(heading, style = Theme.font(17, FontWeight.SemiBold), color = Theme.greenOnDark, modifier = Modifier.padding(top = 60.dp))
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(240.dp)
                        .graphicsLayer {
                            val scale = 1f + 0.9f * progress
                            scaleX = scale
                            scaleY = scale
                            alpha = 0.5f * (1f - progress)
                        }
                        .border(2.dp, Color.White, CircleShape),
                )
                Box(Modifier.size(240.dp).background(Color.White.copy(alpha = 0.12f), CircleShape))
                AnimatedContent(
                    count,
                    transitionSpec = {
                        (fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 4 }) togetherWith
                            (fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 4 })
                    },
                    label = "count",
                ) { value ->
                    Text("$value", style = Theme.font(150, FontWeight.Bold).digits(), color = Color.White)
                }
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CoachAvatar(coach, Modifier.border(2.dp, Color.White, CircleShape), size = 52.dp)
                Text(line, style = Theme.font(20, FontWeight.SemiBold), color = Color.White, textAlign = TextAlign.Center)
            }
            Text(
                L("Tap the screen to start right away"),
                style = Theme.font(14),
                color = Theme.greenOnDark,
                modifier = Modifier.padding(top = 44.dp, bottom = 20.dp),
            )
        }
    }
}
