package com.atala.app.ui.progress

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atala.app.core.l10n.L
import com.atala.app.ui.theme.Theme
import kotlin.math.roundToInt

/** The card of the Progress tab: white, hairline border, soft shadow. */
internal fun Modifier.progressCard(padding: Dp = 18.dp): Modifier {
    val shape = RoundedCornerShape(Theme.cardRadius)
    return this
        .fillMaxWidth()
        .shadow(3.dp, shape, ambientColor = Color.Black.copy(alpha = 0.04f), spotColor = Color.Black.copy(alpha = 0.04f))
        .background(Color.White, shape)
        .border(1.dp, Theme.cardBorder, shape)
        .padding(padding)
}

/**
 * One block of the staged entrance: fades and rises in, a little after the block before it
 * (`mainStagedAppear`). With reduced motion: a short fade, all together, nothing moves.
 */
internal fun Modifier.staged(index: Int, revealed: Boolean, reduceMotion: Boolean): Modifier = composed {
    val spec = if (reduceMotion) tween<Float>(200) else tween(500, delayMillis = index * 80, easing = FastOutSlowInEasing)
    val progress by animateFloatAsState(if (revealed) 1f else 0f, spec, label = "staged")
    graphicsLayer {
        alpha = progress
        translationY = if (reduceMotion) 0f else (1 - progress) * 16.dp.toPx()
    }
}

/** A section of the tab: its title, then its card. */
@Composable
internal fun ProgressSection(title: String, trailing: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(title, trailing)
        content()
    }
}

@Composable
internal fun SectionTitle(title: String, trailing: String? = null) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(title, style = Theme.font(18, FontWeight.Bold), color = Theme.ink, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = Theme.font(13), color = Theme.muted)
    }
}

/** A number in bold with its unit smaller and muted, and a caption under it. */
@Composable
internal fun ProgressFigure(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    size: Int = 20,
    valueColor: Color = Theme.ink,
    mutedColor: Color = Theme.muted,
) {
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = Theme.font(size, FontWeight.Bold), color = valueColor, maxLines = 1, softWrap = false)
            if (unit != null) {
                Text(unit, style = Theme.font((size * 0.6).roundToInt(), FontWeight.SemiBold), color = mutedColor,
                    maxLines = 1, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
        Text(label, style = Theme.font(12), color = mutedColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One friendly line with a small symbol, for a card that has nothing to show yet. */
@Composable
internal fun ProgressHint(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Theme.green, modifier = Modifier.size(17.dp))
        }
        Text(text, style = Theme.font(14), color = Theme.muted, modifier = Modifier.weight(1f))
    }
}

/** A thin vertical hairline between two figures of a row. */
@Composable
internal fun ProgressDivider(color: Color = Theme.hairline) {
    Box(Modifier.width(1.dp).height(30.dp).background(color))
}

/** A thin capsule filled to [fraction] (`MainProgressBar`). */
@Composable
internal fun ProgressBar(fraction: Double, label: String, modifier: Modifier = Modifier, tint: Color = Theme.orange) {
    val clamped = fraction.coerceIn(0.0, 1.0).toFloat()
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(Theme.ink.copy(alpha = 0.12f), CircleShape)
            .semantics {
                contentDescription = label
                stateDescription = L("%@ percent", (clamped * 100).roundToInt())
            },
    ) {
        Box(Modifier.fillMaxWidth(clamped).height(6.dp).background(tint, CircleShape))
    }
}
