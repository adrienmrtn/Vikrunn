package com.atala.app.ui.onboarding

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atala.app.ui.theme.Theme
import kotlin.math.max

/**
 * A translated sentence whose emphasised part sits between two asterisks, as in
 * "Atala is *not just a coach.*" (`OnboardingMarkedSentence`): the sentence without the marks,
 * and the part to highlight. Without exactly two asterisks the whole sentence comes back and
 * nothing is highlighted.
 */
object OnboardingMarkedSentence {
    data class Parsed(val text: String, val highlight: String?)

    fun parse(marked: String): Parsed {
        val parts = marked.split("*")
        if (parts.size != 3) return Parsed(parts.joinToString(""), null)
        val phrase = parts[1].trim()
        return Parsed(parts.joinToString(""), phrase.ifEmpty { null })
    }

    /**
     * The sentence as styled text: every part between two `*` takes [highlight] (the pledge of
     * the commitment card has two of them). The markers are not shown.
     */
    fun annotated(marked: String, highlight: SpanStyle): AnnotatedString = buildAnnotatedString {
        marked.split("*").forEachIndexed { index, part ->
            if (index % 2 == 1) withStyle(highlight) { append(part) } else append(part)
        }
    }
}

/**
 * Headline that writes itself (`OnboardingEmphasisText`): each word fades in and rises a few
 * points, one after the other at an even pace, in its final bold form. One phrase can be
 * coloured: the part written between two `*` in [text] (translations keep the markers around
 * their own words), or else the first occurrence of [highlight]. [onFinished] is called once,
 * after the last word; with reduced motion the sentence is there at once.
 */
@Composable
fun OnboardingEmphasisText(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 28,
    lineSpacing: Dp = 4.dp,
    centered: Boolean = false,
    highlight: String? = null,
    highlightColor: Color = Theme.green,
    emphasisWeight: FontWeight = FontWeight.Bold,
    emphasisColor: Color = Theme.ink,
    /** Seconds between two words. */
    wordInterval: Double = 0.085,
    /** The sentence starts when this becomes true (used to chain several sentences). */
    isActive: Boolean = true,
    startDelay: Double = 0.32,
    isHeading: Boolean = false,
    onFinished: () -> Unit = {},
) {
    val interval = max(0.04, wordInterval)
    val plain = remember(text) { text.replace("*", "") }
    // The tokens and, among them, the ones to colour.
    val layout = remember(text, highlight) {
        val marked = text.split("*")
        if (marked.size == 3) {
            val before = onboardingRevealTokens(marked[0])
            val inside = onboardingRevealTokens(marked[1])
            val after = onboardingRevealTokens(marked[2])
            (before + inside + after) to (before.size until before.size + inside.size)
        } else {
            val tokens = onboardingRevealTokens(plain)
            tokens to phraseRange(tokens, highlight)
        }
    }
    val tokens = layout.first
    val coloured = layout.second
    // Characters of a word without spaces arrive faster than whole words.
    val starts = remember(tokens, interval, startDelay) {
        var time = max(0.0, startDelay)
        tokens.map { token ->
            val start = time
            time += if (token.joined && !token.spaceAfter) interval * 0.45 else interval
            start
        }
    }
    val lastStart = starts.lastOrNull() ?: 0.0
    val clock = rememberWordClock(
        key = text,
        isActive = isActive,
        end = lastStart + OnboardingWordReveal.DURATION,
        finishAfter = lastStart + OnboardingWordReveal.SETTLE,
        isEmpty = tokens.isEmpty(),
        onFinished = onFinished,
    )
    val style = TextStyle(fontSize = size.sp, fontWeight = emphasisWeight)
    OnboardingWordFlow(
        wordSpacing = (size * 0.25f).dp,
        lineSpacing = lineSpacing,
        centered = centered,
        spaceAfter = { tokens.getOrNull(it)?.spaceAfter ?: true },
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = plain
                if (isHeading) heading()
            },
    ) {
        tokens.forEachIndexed { index, token ->
            OnboardingRevealWord(
                token.text,
                style,
                if (index in coloured) highlightColor else emphasisColor,
                clock,
                starts.getOrElse(index) { 0.0 },
            )
        }
    }
}

/** Indices of the tokens of [phrase] (first occurrence) in [tokens]; empty when it is not there. */
private fun phraseRange(tokens: List<OnboardingRevealToken>, phrase: String?): IntRange {
    if (phrase.isNullOrBlank()) return IntRange.EMPTY
    val wanted = onboardingRevealTokens(phrase).map { it.text }
    if (wanted.isEmpty() || wanted.size > tokens.size) return IntRange.EMPTY
    for (start in 0..tokens.size - wanted.size) {
        if ((wanted.indices).all { tokens[start + it].text == wanted[it] }) return start until start + wanted.size
    }
    return IntRange.EMPTY
}
