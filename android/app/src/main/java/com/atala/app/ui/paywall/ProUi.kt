package com.atala.app.ui.paywall

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.atala.app.core.l10n.L

/**
 * "[Terms of use](https://…) · [Privacy policy](https://…)": the sentence with its Markdown links
 * made tappable, as `Text(LocalizedStringKey(...))` does on the iPhone.
 */
@Composable
internal fun LinkedText(
    markdown: String,
    style: TextStyle,
    color: Color,
    linkColor: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
) {
    val text = remember(markdown, linkColor) {
        val pattern = Regex("""\[([^\]]+)]\(([^)]+)\)""")
        buildAnnotatedString {
            var index = 0
            for (match in pattern.findAll(markdown)) {
                append(markdown.substring(index, match.range.first))
                val (label, url) = match.destructured
                withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = linkColor)))) { append(label) }
                index = match.range.last + 1
            }
            append(markdown.substring(index))
        }
    }
    Text(text, style = style.copy(textAlign = textAlign), color = color, modifier = modifier)
}

/** The terms and privacy links shown next to every price of a subscription. */
internal val legalLinks: String
    get() = L("[Terms of use](https://vikstudios.com/atala/terms) · [Privacy policy](https://vikstudios.com/atala)")

/** A price struck through, inside a sentence. */
internal fun AnnotatedString.Builder.appendStruck(text: String) {
    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(text) }
}

/** The crown of Atala Pro (`crown.fill`), which Material icons do not have. */
internal val CrownIcon: ImageVector by lazy {
    ImageVector.Builder("Crown", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(3.2f, 7.4f)
            lineTo(7.6f, 11.2f)
            lineTo(11.1f, 4.6f)
            curveTo(11.5f, 3.9f, 12.5f, 3.9f, 12.9f, 4.6f)
            lineTo(16.4f, 11.2f)
            lineTo(20.8f, 7.4f)
            curveTo(21.4f, 6.9f, 22.3f, 7.4f, 22.1f, 8.2f)
            lineTo(20.0f, 17.2f)
            lineTo(4.0f, 17.2f)
            lineTo(1.9f, 8.2f)
            curveTo(1.7f, 7.4f, 2.6f, 6.9f, 3.2f, 7.4f)
            close()
            moveTo(4.4f, 18.8f)
            lineTo(19.6f, 18.8f)
            curveTo(20.1f, 18.8f, 20.4f, 19.1f, 20.4f, 19.6f)
            lineTo(20.4f, 20.2f)
            curveTo(20.4f, 20.7f, 20.1f, 21.0f, 19.6f, 21.0f)
            lineTo(4.4f, 21.0f)
            curveTo(3.9f, 21.0f, 3.6f, 20.7f, 3.6f, 20.2f)
            lineTo(3.6f, 19.6f)
            curveTo(3.6f, 19.1f, 3.9f, 18.8f, 4.4f, 18.8f)
            close()
        }
    }.build()
}
