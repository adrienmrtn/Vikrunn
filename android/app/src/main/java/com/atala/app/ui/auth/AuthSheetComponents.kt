package com.atala.app.ui.auth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.theme.Theme

private val authShape = RoundedCornerShape(16.dp)

/** Wide sign-in button of the options sheet (54 dp, radius 16, icon and title centred together). */
@Composable
internal fun AuthProviderButton(
    title: String,
    onClick: () -> Unit,
    dark: Boolean = false,
    loading: Boolean = false,
    enabled: Boolean = true,
    icon: @Composable () -> Unit,
) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val content = if (dark) Color.White else Theme.ink
    Box(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .graphicsLayer { alpha = if (pressed) 0.7f else 1f }
            .background(if (dark) Theme.ink else Color.White, authShape)
            .border(1.dp, if (dark) Color.Transparent else Theme.controlBorder, authShape)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button) {
                haptics.tap()
                onClick()
            }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { icon() }
            Text(title, style = Theme.font(16, FontWeight.SemiBold), color = content)
        }
        if (loading) {
            CircularProgressIndicator(
                color = content, strokeWidth = 2.dp,
                modifier = Modifier.align(Alignment.CenterEnd).size(20.dp),
            )
        }
    }
}

/** The four-colour "G", drawn with arcs so no image asset is needed. */
@Composable
internal fun AuthGoogleMark(size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val line = this.size.width * 0.2f
        val inset = line / 2
        val arcSize = Size(this.size.width - line, this.size.height - line)
        // Fractions run clockwise from 3 o'clock.
        fun arc(from: Float, to: Float, color: Color) = drawArc(
            color, startAngle = from * 360f, sweepAngle = (to - from) * 360f, useCenter = false,
            topLeft = Offset(inset, inset), size = arcSize, style = Stroke(line),
        )
        arc(0f, 0.14f, Color(0xFF4285F4))
        arc(0.14f, 0.40f, Color(0xFF34A853))
        arc(0.40f, 0.58f, Color(0xFFFBBC05))
        arc(0.58f, 0.89f, Color(0xFFEA4335))
        drawRect(
            Color(0xFF4285F4),
            topLeft = Offset(this.size.width / 2, (this.size.height - line) / 2),
            size = Size(this.size.width * 0.48f, line),
        )
    }
}

/** Bordered box around the e-mail field and the code field. */
@Composable
internal fun AuthFieldBox(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(Color.White, authShape)
            .border(1.dp, Theme.controlBorder, authShape)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
        content = content,
    )
}

/** Black capsule button with a spinner while a request is running. */
@Composable
internal fun AuthSubmitButton(title: String, onClick: () -> Unit, enabled: Boolean = true, loading: Boolean = false) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(150), label = "scale")
    val active = enabled && !loading
    Box(
        Modifier
            .fillMaxWidth()
            .height(Theme.buttonHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (pressed) 0.85f else 1f
            }
            .background(if (active) Theme.ink else Theme.faint, CircleShape)
            .clickable(interaction, indication = null, enabled = active, role = Role.Button) {
                haptics.confirm()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (loading) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            Text(title, style = Theme.font(16, FontWeight.SemiBold), color = Color.White)
        }
    }
}
