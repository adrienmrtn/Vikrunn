package com.atala.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Design tokens of the retained direction ("very light glass", white, green and orange), as in Theme.swift. */
object Theme {
    val ink = Color(0xFF111111)
    val body = Color(0xFF444444)
    val muted = Color(0xFF6B6B6B)
    val faint = Color(0xFF9A9A9A)
    val hairline = Color(0xFFE4E4E4)
    val cardBorder = Color(0xFFECECEA)
    val controlBorder = Color(0xFFDADADA)
    val surface = Color(0xFFF4F6F3)
    val bubble = Color(0xFFF1F4F1)
    val green = Color(0xFF1F5D4C)
    val greenSoft = Color(0xFF3F8571)
    val greenTint = Color(0xFFE3EEEA)
    val greenOnDark = Color(0xFFCFE2DB)
    val orange = Color(0xFFE8641E)
    val orangeDeep = Color(0xFFB4480F)
    val orangeTint = Color(0xFFFBE7DA)
    val yellow = Color(0xFFD9B45A)
    val yellowDeep = Color(0xFF9A7412)
    val yellowTint = Color(0xFFFBF1D3)
    val locked = Color(0xFFE9E9E6)

    /** Brand colours of the Atala logo. */
    val forest = Color(0xFF143228)
    val cream = Color(0xFFF1F1EF)

    val screenPadding = 20.dp
    val cardRadius = 20.dp
    val buttonHeight = 54.dp

    /** `.system(size:weight:)` of the iPhone: the system font at a given size. */
    fun font(size: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(fontSize = size.sp, fontWeight = weight)

    fun title(size: Int = 28) = font(size, FontWeight.Bold)
}

/** Light only, like the iPhone app. Material components pick up the Atala colours. */
@Composable
fun AtalaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Theme.ink,
            onPrimary = Color.White,
            secondary = Theme.green,
            onSecondary = Color.White,
            tertiary = Theme.orange,
            background = Color.White,
            onBackground = Theme.ink,
            surface = Color.White,
            onSurface = Theme.ink,
            surfaceVariant = Theme.surface,
            onSurfaceVariant = Theme.muted,
            outline = Theme.hairline,
            outlineVariant = Theme.cardBorder,
            surfaceContainerLow = Color.White,
            surfaceContainer = Color.White,
        ),
        content = content,
    )
}
