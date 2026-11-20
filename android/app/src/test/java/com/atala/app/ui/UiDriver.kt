package com.atala.app.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString

/** Small helpers to drive the real screens like a user, by what they show. */
class UiDriver(private val rule: ComposeTestRule) {
    /** Lets the screens move on: animations, staged reveals and delays of [millis] milliseconds. */
    fun settle(millis: Long = 1200) {
        // The clock is driven by hand: a few screens animate without end (frame loops), so the
        // test never waits for "no more frames".
        rule.mainClock.autoAdvance = false
        var left = millis
        while (left > 0) {
            val step = minOf(left, 100L)
            rule.mainClock.advanceTimeBy(step)
            left -= step
        }
        rule.waitForIdle()
    }

    private fun clickable(matcher: SemanticsMatcher): SemanticsNodeInteractionCollection =
        rule.onAllNodes(matcher and hasClickAction() and isEnabled(), useUnmergedTree = false)

    fun has(matcher: SemanticsMatcher): Boolean = clickable(matcher).fetchSemanticsNodes().isNotEmpty()

    fun showsText(text: String, substring: Boolean = false): Boolean =
        rule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()

    fun showsDescription(text: String, substring: Boolean = false): Boolean =
        rule.onAllNodes(androidx.compose.ui.test.hasContentDescription(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()

    /** Clicks the first enabled clickable node matching; false when there is none. */
    fun tap(matcher: SemanticsMatcher): Boolean {
        val nodes = clickable(matcher)
        if (nodes.fetchSemanticsNodes().isEmpty()) return false
        nodes.onFirst().performClick()
        return true
    }

    fun tap(text: String, substring: Boolean = false): Boolean = tap(hasText(text, substring = substring))

    /** Clicks the first enabled node whose click action is labelled [label]. */
    fun tapLabelled(label: String): Boolean = tap(clickLabel(label))

    fun tapDescribed(description: String): Boolean = tap(
        SemanticsMatcher("content description $description") {
            it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(description) == true
        },
    )

    fun tree(): String = rule.onAllNodes(isRoot()).fetchSemanticsNodes().indices.joinToString("\n") { index ->
        rule.onAllNodes(isRoot())[index].printToString(maxDepth = Int.MAX_VALUE)
    }

    /** Every text on screen, for diagnostics. */
    fun texts(): List<String> = rule.onAllNodes(SemanticsMatcher("has text") { it.config.contains(SemanticsProperties.Text) })
        .fetchSemanticsNodes().flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }

    companion object {
        fun clickLabel(label: String) = SemanticsMatcher("click label $label") {
            it.config.getOrNull(SemanticsActions.OnClick)?.label == label
        }
    }
}
