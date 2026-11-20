package com.atala.app.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlin.math.sqrt

/**
 * Sizes that follow the room on screen (`ScreenFit` on the iPhone): 1 on regular phones, less on
 * small ones (360 × 640 dp), never below [minimum].
 */
fun screenFitScale(length: Float, reference: Float, minimum: Float = 0.72f): Float {
    if (length <= 0f || reference <= 0f) return 1f
    return (length / reference).coerceIn(minimum, 1f)
}

/**
 * Scale from the height of the screen: 1 from [reference] dp up, proportionally less below.
 * A larger system font asks for a little more room, so big text and the pictures share it.
 */
@Composable
fun rememberScreenScale(reference: Float = 760f, minimum: Float = 0.72f): Float {
    val height = LocalConfiguration.current.screenHeightDp.toFloat()
    return screenFitScale(height, reference * fontRoom(), minimum)
}

/** How much more room the system font size asks for: 1 at 100 %, about 1.14 at 130 %. */
@Composable
fun fontRoom(): Float = sqrt(LocalDensity.current.fontScale.coerceAtLeast(1f))

/** [maximum] on regular phones, proportionally less on small ones (`adaptiveHeight` on the iPhone). */
@Composable
fun adaptiveHeight(maximum: Dp, reference: Float = 760f, minimum: Float = 0.72f): Dp =
    maximum * rememberScreenScale(reference, minimum)

/**
 * A column as high as the screen whose weighted spacers share the room left, exactly like a plain
 * full-height column, but that scrolls instead of cutting its content when it is taller than the
 * screen (a small phone, a large system font).
 */
@Composable
fun FillOrScrollColumn(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight),
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}
