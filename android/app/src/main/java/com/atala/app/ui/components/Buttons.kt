package com.atala.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atala.app.ui.theme.Theme

/** Black capsule, the main call to action of a screen (`PrimaryButton`). */
@Composable
fun PrimaryButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Played on tap. Null when the action plays its own. */
    haptic: HapticKind? = HapticKind.Confirm,
) {
    CapsuleButton(title, onClick, modifier, enabled, haptic, pressedAlpha = 0.85f, pressedScale = 0.98f,
        look = Modifier.background(if (enabled) Theme.ink else Theme.faint, CircleShape))
}

/** White capsule with a black outline (`SecondaryButtonStyle`). */
@Composable
fun SecondaryButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    haptic: HapticKind? = HapticKind.Tap,
) {
    CapsuleButton(title, onClick, modifier, enabled = true, haptic, pressedAlpha = 0.7f, pressedScale = 1f,
        textColor = Theme.ink,
        look = Modifier.background(Color.White, CircleShape).border(1.dp, Theme.ink, CircleShape))
}

@Composable
private fun CapsuleButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    haptic: HapticKind?,
    pressedAlpha: Float,
    pressedScale: Float,
    textColor: Color = Color.White,
    look: Modifier,
) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, tween(150), label = "scale")
    Box(
        modifier
            .fillMaxWidth()
            .height(Theme.buttonHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (pressed) pressedAlpha else 1f
            }
            .then(look)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button) {
                haptic?.let(haptics::play)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(title, style = Theme.font(16, FontWeight.SemiBold), color = textColor)
    }
}

/** Bottom call-to-action area used on most screens. */
@Composable
fun BottomBar(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(start = Theme.screenPadding, end = Theme.screenPadding, top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}
