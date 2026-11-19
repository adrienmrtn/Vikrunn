package com.atala.app.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atala.app.ui.theme.Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import java.text.BreakIterator

/**
 * How a word arrives in the texts that reveal themselves (`OnboardingWordReveal`): it fades in
 * while rising a few points. Every word has its own start time, read from one clock, so the
 * rhythm stays perfectly even.
 */
object OnboardingWordReveal {
    /** How far below its place a word starts, in dp. */
    const val RISE = 7f
    /** Time a word takes to settle. Longer than the gap between two words: they overlap softly. */
    const val DURATION = 0.6
    /** After the last word has started, how long before the text counts as said. */
    const val SETTLE = 0.38
}

/**
 * Lays words out like a paragraph aligned on the start edge (or centred), so each word can be
 * animated on its own (`OnboardingWordFlowLayout`). Every word keeps its place from the start:
 * nothing reflows while the text appears.
 */
@Composable
fun OnboardingWordFlow(
    wordSpacing: Dp,
    lineSpacing: Dp,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
    /** False after an item that is glued to the next one (characters of a Japanese word). */
    spaceAfter: (Int) -> Boolean = { true },
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        // A word wider than the whole line (rare) wraps inside it instead of overflowing.
        val placeables = measurables.map { it.measure(Constraints(maxWidth = maxWidth)) }
        val gap = wordSpacing.roundToPx()
        val lineGap = lineSpacing.roundToPx()
        val xs = IntArray(placeables.size)
        val ys = IntArray(placeables.size)
        val lineOf = IntArray(placeables.size)
        val lineWidths = mutableListOf(0)
        var x = 0
        var y = 0
        var lineHeight = 0
        var widest = 0
        placeables.forEachIndexed { index, placeable ->
            if (x > 0 && x + placeable.width > maxWidth) {
                x = 0
                y += lineHeight + lineGap
                lineHeight = 0
                lineWidths.add(0)
            }
            xs[index] = x
            ys[index] = y
            lineOf[index] = lineWidths.size - 1
            lineWidths[lineWidths.size - 1] = x + placeable.width
            widest = max(widest, x + placeable.width)
            x += placeable.width + if (spaceAfter(index)) gap else 0
            lineHeight = max(lineHeight, placeable.height)
        }
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else widest
        val height = (y + lineHeight).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width.coerceAtLeast(constraints.minWidth), height) {
            placeables.forEachIndexed { index, placeable ->
                val shift = if (centered) (width - lineWidths[lineOf[index]]) / 2 else 0
                // placeRelative mirrors the line in right-to-left languages.
                placeable.placeRelative(xs[index] + shift, ys[index])
            }
        }
    }
}

/**
 * One clock for a whole text: time in seconds since the text started. [start] runs it from 0
 * to [end]; [finish] jumps to the end (reduced motion).
 */
class OnboardingWordClock {
    private val time = Animatable(-1f)

    /** Seconds since the start, -1 before. */
    val value: Float get() = time.value

    suspend fun run(end: Double) {
        time.snapTo(0f)
        time.animateTo(end.toFloat(), tween(seconds(end), easing = LinearEasing))
    }

    suspend fun finish(end: Double) = time.snapTo(end.toFloat())

    /** 0 to 1 for a word starting at [start] (eased). */
    fun progress(start: Double): Float {
        val now = time.value
        if (now < 0f) return 0f
        val raw = ((now - start) / OnboardingWordReveal.DURATION).toFloat().coerceIn(0f, 1f)
        return OnboardingEasing.word.transform(raw)
    }
}

/** One word of a revealing text: in its final place and form, only its opacity and rise change. */
@Composable
internal fun OnboardingRevealWord(word: String, style: TextStyle, color: Color, clock: OnboardingWordClock, start: Double) {
    val rise = with(LocalDensity.current) { OnboardingWordReveal.RISE.dp.toPx() }
    Text(
        word,
        style = style,
        color = color,
        modifier = Modifier.graphicsLayer {
            val p = clock.progress(start)
            alpha = p
            translationY = (1f - p) * rise
        },
    )
}

/**
 * Drives a revealing text: once [isActive], runs the clock until [end] and calls [onFinished]
 * once, [finishAfter] seconds after the start. With reduced motion everything is there at once.
 */
@Composable
internal fun rememberWordClock(
    key: Any,
    isActive: Boolean,
    end: Double,
    finishAfter: Double,
    isEmpty: Boolean,
    onFinished: () -> Unit,
): OnboardingWordClock {
    val clock = remember(key) { OnboardingWordClock() }
    var finished by remember(key) { mutableStateOf(false) }
    val reduceMotion = rememberReduceMotion()
    val finish by rememberUpdatedState(onFinished)
    LaunchedEffect(key, isActive) {
        if (!isActive || finished) return@LaunchedEffect
        if (reduceMotion || isEmpty) {
            clock.finish(end)
            finished = true
            finish()
            return@LaunchedEffect
        }
        launch { clock.run(end) }
        delay(seconds(finishAfter).toLong())
        finished = true
        finish()
    }
    return clock
}

/**
 * Text that appears word by word, as if the coach were speaking (`OnboardingSpokenText`): a
 * steady reading pace with a breath after punctuation. The whole paragraph is laid out from the
 * start, so nothing around it moves. [onFinished] is called once, after the last word.
 */
@Composable
fun OnboardingSpokenText(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 17,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Theme.ink,
    lineSpacing: Dp = 4.dp,
    /** The coach starts speaking when this becomes true (used to chain several sentences). */
    isActive: Boolean = true,
    startDelay: Double = 0.0,
    isHeading: Boolean = false,
    onFinished: () -> Unit = {},
) {
    val words = remember(text) { onboardingRevealTokens(text) }
    val starts = remember(words, startDelay) { spokenStarts(words, startDelay) }
    val lastStart = starts.lastOrNull() ?: 0.0
    val clock = rememberWordClock(
        key = text,
        isActive = isActive,
        end = lastStart + OnboardingWordReveal.DURATION,
        finishAfter = lastStart + OnboardingWordReveal.SETTLE,
        isEmpty = words.isEmpty(),
        onFinished = onFinished,
    )
    val style = TextStyle(fontSize = size.sp, fontWeight = weight)
    OnboardingWordFlow(
        wordSpacing = (size * 0.27f).dp,
        lineSpacing = lineSpacing,
        spaceAfter = { words.getOrNull(it)?.spaceAfter ?: true },
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = text
                if (isHeading) heading()
            },
    ) {
        words.forEachIndexed { index, word ->
            OnboardingRevealWord(word.text, style, color, clock, starts.getOrElse(index) { 0.0 })
        }
    }
}

/** Seconds before the next word: a steady pace, a breath after punctuation. */
private fun spokenPause(word: OnboardingRevealToken): Double = when (word.text.lastOrNull()) {
    null -> 0.09
    '.', '!', '?', '。', '！', '？' -> 0.26
    ',', ':', ';', '、', '，' -> 0.16
    // A character of a Japanese word is a fraction of a word.
    else -> if (word.joined) 0.04 else 0.09
}

/** When each word starts, counted from the moment the coach starts speaking. */
private fun spokenStarts(words: List<OnboardingRevealToken>, startDelay: Double): List<Double> {
    var time = max(0.0, startDelay)
    return words.map { word ->
        val start = time
        time += spokenPause(word)
        start
    }
}

/** A piece of a revealing text: a word, or one character of a language written without spaces. */
data class OnboardingRevealToken(val text: String, val spaceAfter: Boolean, val joined: Boolean)

private fun isUnspaced(codePoint: Int): Boolean {
    val block = Character.UnicodeBlock.of(codePoint) ?: return false
    return block == Character.UnicodeBlock.HIRAGANA || block == Character.UnicodeBlock.KATAKANA ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION ||
        block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
}

/**
 * The text cut into words. Japanese has no spaces: its words are cut into characters, laid
 * side by side without a gap, so they still arrive one after the other.
 */
fun onboardingRevealTokens(text: String): List<OnboardingRevealToken> =
    text.split(" ").filter { it.isNotEmpty() }.flatMap { word ->
        if (word.length > 1 && word.codePoints().anyMatch(::isUnspaced)) {
            val iterator = BreakIterator.getCharacterInstance()
            iterator.setText(word)
            val pieces = mutableListOf<String>()
            var start = iterator.first()
            var end = iterator.next()
            while (end != BreakIterator.DONE) {
                pieces.add(word.substring(start, end))
                start = end
                end = iterator.next()
            }
            pieces.mapIndexed { index, piece -> OnboardingRevealToken(piece, spaceAfter = index == pieces.lastIndex, joined = true) }
        } else {
            listOf(OnboardingRevealToken(word, spaceAfter = true, joined = false))
        }
    }
