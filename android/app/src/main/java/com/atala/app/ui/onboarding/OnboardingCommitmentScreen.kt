package com.atala.app.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atala.app.ui.components.adaptiveHeight
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.CoachPose
import com.atala.app.core.plan.PlanEngine
import com.atala.app.ui.components.CoachPoseImage
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.Theme
import java.time.Instant

/** Finger-drawn signature (`OnboardingSignaturePad`). Strokes live in memory only: nothing is saved. */
@Composable
private fun SignaturePad(strokes: MutableList<List<Offset>>, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val hint = L("Draw your signature with your finger")
    Canvas(
        modifier
            .semantics {
                contentDescription = L("Signature area")
                stateDescription = hint
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    // First stroke of the signature: a soft tick under the finger.
                    if (strokes.isEmpty()) haptics.play(HapticKind.Soft)
                    strokes.add(listOf(down.position))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        val last = strokes.lastIndex
                        if (last >= 0) strokes[last] = strokes[last] + change.position
                    }
                }
            },
    ) {
        val style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        for (stroke in strokes) {
            val first = stroke.firstOrNull() ?: continue
            val path = Path().apply {
                moveTo(first.x, first.y)
                if (stroke.size == 1) lineTo(first.x + 0.5f, first.y + 0.5f)
                stroke.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, Theme.ink, style = style)
        }
    }
}

/** Screen 27 (step `Commitment`): the commitment card, built from the real answers, signed with a finger. */
@Composable
fun OnboardingCommitmentScreen() {
    val state by collectAppState()
    val haptics = rememberHaptics()
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val signed = strokes.isNotEmpty()
    val weekCount = state.plan?.weeks?.size ?: PlanEngine.MAX_WEEKS
    val count = OnboardingCopy.sessionsPerWeek(state.answers)
    val frequency = when (count) {
        1 -> L("once a week")
        2 -> L("twice a week")
        else -> L("%@ times a week", count)
    }
    // "I, Max, will show up 3 times a week for 8 weeks." The parts between two `*` are green: the
    // markers stay in the translations, so each language puts them where they belong.
    val pledge = OnboardingMarkedSentence.annotated(
        L("I, %@, will show up *%@* for *%@*.", state.firstName, frequency, OnboardingCopy.weeks(weekCount)),
        SpanStyle(color = Theme.green),
    )

    OnboardingScaffold(
        title = L("Before you go, promise yourself you'll reach your goals."),
        subtitle = L("Sign it. The runners who commit are the ones who show up."),
        buttonTitle = L("I promise"),
        buttonHaptic = HapticKind.Strong,
        canContinue = signed,
        tinted = true,
    ) {
        val cardShape = RoundedCornerShape(28.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(15.dp, cardShape, ambientColor = Theme.green.copy(alpha = 0.12f), spotColor = Theme.green.copy(alpha = 0.12f))
                .background(Color.White, cardShape)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(pledge, style = Theme.font(20, FontWeight.SemiBold).copy(lineHeight = 29.sp), color = Theme.ink)
            val areaShape = RoundedCornerShape(18.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(areaShape)
                    .background(Color(0xFFFAFAF8)),
            ) {
                if (!signed) {
                    Text(
                        L("Sign here with your finger"),
                        style = Theme.font(14),
                        color = Theme.faint,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clearAndSetSemantics {},
                    )
                }
                SignaturePad(strokes, Modifier.fillMaxSize())
                // Dashed outline, drawn above the ink.
                Canvas(Modifier.fillMaxSize()) {
                    val width = 1.5.dp.toPx()
                    drawRoundRect(
                        Theme.faint,
                        topLeft = Offset(width / 2, width / 2),
                        size = androidx.compose.ui.geometry.Size(size.width - width, size.height - width),
                        cornerRadius = CornerRadius(18.dp.toPx()),
                        style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                    )
                }
                if (signed) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .height(44.dp)
                            .clickable {
                                haptics.tap()
                                strokes.clear()
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(L("Clear"), style = Theme.font(13, FontWeight.SemiBold), color = Theme.muted)
                    }
                }
            }
            Text(
                L("%@ · between you and you, never shared", Fmt.day(Instant.now(), "d MMMM yyyy")),
                style = Theme.font(12),
                color = Theme.muted,
            )
        }
        // The coach approves as soon as the card is signed.
        CoachPoseImage(
            state.coach,
            pose = CoachPose.ThumbsUp,
            modifier = Modifier
                .fillMaxWidth()
                .height(adaptiveHeight(180.dp))
                .onboardingSpringIn(signed)
                .clearAndSetSemantics {},
        )
    }
}
