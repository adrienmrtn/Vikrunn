package com.atala.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.atala.app.ui.theme.AtalaImages
import com.atala.app.ui.theme.Theme
import kotlin.math.roundToInt

/** White card with the soft border and shadow of the option cards (`.card(selected:radius:)`). */
fun Modifier.card(selected: Boolean = false, radius: Dp = Theme.cardRadius): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .shadow(4.dp, shape, ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.08f))
        .background(Color.White, shape)
        .border(if (selected) 1.5.dp else 1.dp, if (selected) Theme.ink else Theme.cardBorder, shape)
}

/** Small illustrated vignette (pastel icon on white) blended into the card. */
@Composable
fun VignetteImage(name: String, size: Dp = 48.dp) {
    val id = AtalaImages[name] ?: return
    val image = ImageBitmap.imageResource(id)
    Canvas(Modifier.size(size)) {
        // Scaled to fit, centred, multiplied so its white background takes the card's colour.
        val scale = minOf(this.size.width / image.width, this.size.height / image.height)
        val fitted = Size(image.width * scale, image.height * scale)
        drawImage(
            image,
            dstOffset = IntOffset(((this.size.width - fitted.width) / 2).roundToInt(), ((this.size.height - fitted.height) / 2).roundToInt()),
            dstSize = IntSize(fitted.width.roundToInt(), fitted.height.roundToInt()),
            blendMode = BlendMode.Multiply,
        )
    }
}

/** Rounded tile holding an icon. */
@Composable
fun IconTile(
    icon: ImageVector,
    tint: Color = Theme.green,
    background: Color = Theme.greenTint,
    size: Dp = 44.dp,
) {
    Box(
        Modifier
            .size(size)
            .background(background, RoundedCornerShape(size * 0.32f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Option row with a leading vignette or icon, a title, an optional subtitle and a radio or checkbox. */
@Composable
fun OptionCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    multi: Boolean = false,
    leading: @Composable () -> Unit = {},
) {
    val haptics = rememberHaptics()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 74.dp)
            .card(selected)
            .semantics { this.selected = selected }
            .clickable(role = if (multi) Role.Checkbox else Role.RadioButton) {
                haptics.select()
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Theme.font(16, FontWeight.SemiBold), color = Theme.ink)
            if (subtitle != null) Text(subtitle, style = Theme.font(13), color = Theme.muted)
        }
        SelectionMark(selected, multi)
    }
}

/** Radio dot, or a rounded checkbox when several answers can be ticked. */
@Composable
fun SelectionMark(selected: Boolean, multi: Boolean = false) {
    if (multi) {
        val shape = RoundedCornerShape(8.dp)
        Box(
            Modifier
                .size(24.dp)
                .background(if (selected) Theme.green else Color.Transparent, shape)
                .border(1.5.dp, if (selected) Theme.green else Color(0xFFC4C4C4), shape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
    } else {
        Canvas(Modifier.size(22.dp)) {
            val diameter = (if (selected) 16.dp else 20.5.dp).toPx()
            val width = (if (selected) 6.dp else 1.5.dp).toPx()
            drawCircle(
                color = if (selected) Theme.ink else Color(0xFFC4C4C4),
                radius = diameter / 2,
                style = Stroke(width),
            )
        }
    }
}
