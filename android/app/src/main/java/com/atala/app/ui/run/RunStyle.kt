package com.atala.app.ui.run

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.atala.app.core.l10n.L
import com.atala.app.core.model.Feeling
import com.atala.app.core.model.StepKind
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.IconTile
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.theme.Theme

/** Colours of the run screens (the colour part of RunFormat.swift). */
object RunColors {
    /** Background tint of the live screen. */
    fun tint(kind: StepKind?): Color = if (kind == StepKind.Work) Theme.orangeTint else Theme.greenTint

    /** Secondary text colour on the tinted background. */
    fun tintedText(kind: StepKind?): Color = if (kind == StepKind.Work) Color(0xFF7A4A2E) else Color(0xFF44615A)

    fun segment(kind: StepKind): Color = if (kind == StepKind.Work) Theme.orange else Theme.greenSoft

    /** Dot of a step in the session lists (WorkoutDetailFormat.dotColor). */
    fun dot(kind: StepKind): Color = when (kind) {
        StepKind.Work -> Theme.orange
        StepKind.Exercise -> Theme.yellow
        else -> Theme.greenSoft
    }
}

/** Symbols and tints of the four answers (RunFeelingStyle). */
object RunFeelingStyle {
    fun icon(feeling: Feeling): ImageVector = when (feeling) {
        Feeling.Easy -> Icons.Filled.SentimentSatisfied
        Feeling.Good -> Icons.Filled.ThumbUp
        Feeling.Hard -> Icons.Filled.LocalFireDepartment
        Feeling.Pain -> Icons.Filled.Healing
    }

    fun tint(feeling: Feeling): Color = when (feeling) {
        Feeling.Easy -> Theme.green
        Feeling.Good -> Theme.yellowDeep
        Feeling.Hard -> Theme.orangeDeep
        Feeling.Pain -> Theme.muted
    }

    fun background(feeling: Feeling): Color = when (feeling) {
        Feeling.Easy -> Theme.greenTint
        Feeling.Good -> Theme.yellowTint
        Feeling.Hard -> Theme.orangeTint
        Feeling.Pain -> Color(0xFFF1F1EF)
    }
}

/** The coach's speech bubble: rounded, with a small corner on the coach's side. */
val RunBubbleShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp)

/** Figures with digits of equal width (`.monospacedDigit()`). */
fun TextStyle.digits(): TextStyle = copy(fontFeatureSettings = "tnum")

fun TextStyle.tracking(points: Float): TextStyle = copy(letterSpacing = points.sp)

/**
 * One line of text that shrinks to fit its width (`.lineLimit(1).minimumScaleFactor(...)`), or
 * [maxLines] lines when more are allowed.
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    minScale: Float = 0.5f,
    maxLines: Int = 1,
    textAlign: TextAlign? = null,
) {
    var scale by remember(text, style) { mutableFloatStateOf(1f) }
    var ready by remember(text, style) { mutableStateOf(false) }
    val size: TextUnit = style.fontSize
    val spacing: TextUnit = style.letterSpacing
    Text(
        text,
        modifier = modifier.drawWithContent { if (ready) drawContent() },
        style = style.copy(
            fontSize = if (size.isSpecified) size * scale else size,
            letterSpacing = if (spacing.isSpecified) spacing * scale else spacing,
        ),
        color = color,
        maxLines = maxLines,
        softWrap = maxLines > 1,
        overflow = TextOverflow.Clip,
        textAlign = textAlign,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && scale > minScale) {
                scale = maxOf(minScale, scale * 0.92f)
            } else {
                ready = true
            }
        },
    )
}

/** The small "Atala Pro" tag with a padlock, shown where a control needs the subscription. */
@Composable
fun RunProTag(modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(28.dp)
            .background(Theme.greenTint, CircleShape)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Lock, null, tint = Theme.green, modifier = Modifier.size(12.dp))
        Text("Atala Pro", style = Theme.font(13, FontWeight.Bold), color = Theme.green, maxLines = 1)
    }
}

/** The Atala switch (green when on). */
@Composable
fun RunSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    Switch(
        checked = checked,
        onCheckedChange = {
            haptics.select()
            onCheckedChange(it)
        },
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Theme.green,
            checkedBorderColor = Theme.green,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Theme.controlBorder,
            uncheckedBorderColor = Theme.controlBorder,
        ),
    )
}

/**
 * The coach's voice switch, shared by the run and the guided sessions (CoachVoiceRow).
 * With Atala Pro it is a live switch. Without it, the row shows a padlock and an "Atala Pro" tag
 * (never a green switch) and a tap calls [onLocked], which opens the paywall.
 */
@Composable
fun CoachVoiceRow(
    coachName: String,
    isPro: Boolean,
    isOn: Boolean,
    onChange: (Boolean) -> Unit,
    onText: String,
    offText: String,
    onLocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    if (isPro) {
        Row(
            modifier.fillMaxWidth().heightIn(min = 60.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(Icons.AutoMirrored.Filled.VolumeUp, tint = Theme.orangeDeep, background = Theme.orangeTint, size = 44.dp)
            VoiceRowTexts(coachName, if (isOn) onText else offText, Modifier.weight(1f))
            RunSwitch(isOn, onChange)
        }
    } else {
        val label = L("%@'s voice, included in Atala Pro", coachName)
        val hint = L("Shows what Atala Pro includes")
        Row(
            modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clickable(role = Role.Button, onClickLabel = hint) {
                    haptics.tap()
                    onLocked()
                }
                .clearAndSetSemantics {
                    contentDescription = label
                    role = Role.Button
                    onClick(label = hint) { onLocked(); true }
                },
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(Icons.AutoMirrored.Filled.VolumeUp, tint = Theme.muted, background = Theme.locked, size = 44.dp)
            VoiceRowTexts(coachName, L("Included in Atala Pro"), Modifier.weight(1f))
            RunProTag()
        }
    }
}

@Composable
private fun VoiceRowTexts(coachName: String, subtitle: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(L("%@'s voice", coachName), style = Theme.font(16, FontWeight.SemiBold), color = Theme.ink)
        Text(subtitle, style = Theme.font(13), color = Theme.muted)
    }
}

/** The large call-to-action of the run screens (start, resume): taller than the standard button. */
@Composable
fun RunBigButton(
    title: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Theme.ink,
    haptic: HapticKind? = HapticKind.Confirm,
) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(150), label = "press")
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (pressed) 0.85f else 1f
            }
            .background(background, CircleShape)
            .clickable(interaction, indication = null, role = Role.Button) {
                haptic?.let(haptics::play)
                onClick()
            },
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(title, style = Theme.font(19, FontWeight.Bold), color = Color.White)
    }
}

/** White capsule with a black outline, with an icon (SecondaryButtonStyle with a Label). */
@Composable
fun RunSecondaryButton(
    title: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    haptic: HapticKind? = HapticKind.Tap,
) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier
            .fillMaxWidth()
            .height(Theme.buttonHeight)
            .graphicsLayer { alpha = if (pressed) 0.7f else 1f }
            .background(Color.White, CircleShape)
            .border(1.dp, Theme.ink, CircleShape)
            .clickable(interaction, indication = null, role = Role.Button) {
                haptic?.let(haptics::play)
                onClick()
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = Theme.ink, modifier = Modifier.size(18.dp))
        FitText(title, Theme.font(16, FontWeight.SemiBold), Theme.ink, minScale = 0.8f)
    }
}

/** A round icon in a tinted circle (feeling answers). */
@Composable
fun FeelingBadge(feeling: Feeling, size: Int = 44, iconSize: Int = 20) {
    Box(
        Modifier.size(size.dp).background(RunFeelingStyle.background(feeling), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(RunFeelingStyle.icon(feeling), null, tint = RunFeelingStyle.tint(feeling), modifier = Modifier.size(iconSize.dp))
    }
}
