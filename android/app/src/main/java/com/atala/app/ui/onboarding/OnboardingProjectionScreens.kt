package com.atala.app.ui.onboarding

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atala.app.core.l10n.AppLanguage
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.OnboardingAnswers
import com.atala.app.core.model.TrainingPlan
import com.atala.app.core.plan.PlanEngine
import com.atala.app.ui.components.CoachNote
import com.atala.app.ui.components.IconTile
import com.atala.app.ui.components.card
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.AtalaImages
import com.atala.app.ui.theme.Theme
import kotlinx.coroutines.delay
import java.text.NumberFormat
import kotlin.math.roundToInt

// region Small motion helpers (stand-ins for OnboardingMotion.swift until the shared port lands)
// Used by the screens of this file and of the schedule, account, plan-ready and final files.

/** SwiftUI's `.easeOut`. */
internal val ProjectionEaseOut: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

/** SwiftUI's `.easeInOut`. */
internal val ProjectionEaseInOut: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/** Tabular digits (`.monospacedDigit()`). */
internal fun TextStyle.projectionDigits(): TextStyle = copy(fontFeatureSettings = "tnum")

/** "Reduce Motion": the phone's animations are switched off (animator duration scale 0). */
@Composable
internal fun rememberProjectionReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * `onboardingStageSequence`: raises the stage from 0 to `delays.size`, waiting `delays[n]` seconds
 * before stage `n + 1`. Waits until [active]; with reduced motion everything is there at once.
 */
@Composable
internal fun rememberProjectionStage(delays: List<Double>, active: Boolean = true): Int {
    val reduceMotion = rememberProjectionReduceMotion()
    var stage by rememberSaveable { mutableIntStateOf(if (reduceMotion) delays.size else 0) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        if (reduceMotion) {
            stage = delays.size
            return@LaunchedEffect
        }
        for (index in stage until delays.size) {
            delay((delays[index] * 1000).toLong())
            stage = index + 1
        }
    }
    return stage
}

/** `onboardingStaged`: fades and rises in once `stage` has passed `index`. */
@Composable
internal fun Modifier.projectionStaged(index: Int, stage: Int): Modifier {
    val progress by animateFloatAsState(if (stage > index) 1f else 0f, tween(450, easing = ProjectionEaseOut), label = "staged")
    return graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 14.dp.toPx()
    }
}

/** `OnboardingCountingValue`: a number that counts towards [value] when it changes. */
@Composable
internal fun ProjectionCountingText(
    value: Double,
    format: (Int) -> String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    durationMillis: Int = 900,
    easing: Easing = ProjectionEaseInOut,
) {
    val shown by animateFloatAsState(value.toFloat(), tween(durationMillis, easing = easing), label = "count")
    Text(format(shown.roundToInt()), style = style.projectionDigits(), color = color, maxLines = 1, softWrap = false, modifier = modifier)
}

// endregion

/** Screen 22: where the plan takes the user, computed by `PlanEngine` from the real answers. */
@Composable
fun OnboardingProjectionScreen() {
    val state by collectAppState()
    val answers = state.answers
    val plan = remember(answers) { PlanEngine.generate(answers) }
    // 1 hero card, 2 the big number counts, 3 verdict, 4-6 stat tiles, 7 coach note, 8 button.
    // The verdict waits for the count (0.9 s) to be nearly over; the tiles follow without a gap.
    val delays = remember { listOf(0.3, 0.35, 0.85, 0.3, 0.14, 0.14, 0.4, 0.4) }
    val stage = rememberProjectionStage(delays)
    val hasResult = OnboardingNavigator.hasRecentResult(answers)

    OnboardingScaffold(title = projectionTitle(plan, hasResult), buttonsVisible = stage >= delays.size) {
        ProjectionHeroCard(plan, hasResult, stage, Modifier.projectionStaged(0, stage))
        ProjectionStats(plan, answers, stage)
        CoachNote(state.coach, projectionNote(answers), Modifier.projectionStaged(6, stage))
    }
}

private fun projectionGain(plan: TrainingPlan): Int {
    val today = plan.currentSeconds ?: return 0
    val goal = plan.predictedSeconds ?: return 0
    return maxOf(0, today - goal)
}

/**
 * Times of "today" and of the end of the plan are only shown when they come from a real recent
 * result. Without one the screen shows the plan itself: its weeks and its kilometres.
 */
private fun projectionTitle(plan: TrainingPlan, hasResult: Boolean): String {
    val weeks = OnboardingCopy.weeks(plan.weeks.size)
    if (plan.goal.isOpenEnded) return L("%@ to make running a habit", weeks)
    if (!hasResult) return L("%@ to be ready for your %@", weeks, OnboardingCopy.goalNoun(plan.goal))
    val gain = projectionGain(plan)
    if (gain >= 60) {
        val minutes = gain / 60
        return if (minutes == 1) L("You can gain %@ minute in %@", minutes, weeks) else L("You can gain %@ minutes in %@", minutes, weeks)
    }
    if (gain > 0) return L("You can gain %@ seconds in %@", gain, weeks)
    return L("%@ to be ready for your %@", weeks, OnboardingCopy.goalNoun(plan.goal))
}

private fun projectionEndLabel(plan: TrainingPlan): String {
    val race = plan.raceDate
    return if (race != null) L("On %@", Fmt.day(race, "d MMMM")) else L("In %@", OnboardingCopy.weeks(plan.weeks.size))
}

private fun projectionVerdict(plan: TrainingPlan, hasResult: Boolean): String {
    if (plan.goal.isOpenEnded || !hasResult) return L("A steady build-up, never more than you can absorb")
    val target = plan.targetSeconds
    val aimed = plan.predictedSeconds
    if (target != null && aimed != null && aimed > target) return L("Ambitious: I aim for %@ first", Fmt.goalTime(aimed))
    return L("Realistic goal for your level")
}

private fun projectionNote(answers: OnboardingAnswers): String {
    val result = answers.recentResult
    if (result != null) {
        return L(
            "I start from your %@ in %@ and build up gently, with strength and stretching every week.",
            OnboardingCopy.distanceName(result.distanceKm), Fmt.goalTime(result.seconds),
        )
    }
    if (OnboardingNavigator.isNotRunningYet(answers)) {
        return L("We start gently and build up step by step, with strength and stretching every week.")
    }
    return L("I start from what you told me about your running and build up gently, with strength and stretching every week.")
}

@Composable
private fun ProjectionHeroCard(plan: TrainingPlan, hasResult: Boolean, stage: Int, modifier: Modifier) {
    val firstKm = plan.weeks.firstOrNull()?.plannedKm ?: 0.0
    val usesTimes = hasResult && !plan.goal.isOpenEnded && plan.currentSeconds != null
    // The big number starts from today's value and travels to where the plan leads (beginners: the
    // plan's own first and biggest weeks).
    val startValue = if (usesTimes) (plan.currentSeconds ?: 0).toDouble() else firstKm
    val endValue = if (usesTimes) (plan.predictedSeconds ?: plan.currentSeconds ?: 0).toDouble() else plan.peakKm
    val format: (Int) -> String = if (usesTimes) { seconds -> Fmt.goalTime(seconds) } else { km -> Fmt.km(km.toDouble(), decimals = 0) }
    val counted = stage >= 2
    val arrow by animateFloatAsState(if (counted) 1f else 0f, tween(600, easing = ProjectionEaseOut), label = "arrow")

    Column(
        modifier
            .fillMaxWidth()
            .background(Theme.green, RoundedCornerShape(28.dp))
            .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (usesTimes) L("Today") else L("First week"), style = Theme.font(13), color = Theme.greenOnDark)
                Text(format(startValue.roundToInt()), style = Theme.font(26, FontWeight.SemiBold).projectionDigits(), color = Theme.greenOnDark, maxLines = 1)
            }
            Spacer(Modifier.weight(1f).widthIn(min = 8.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Theme.greenOnDark,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .size(22.dp)
                    .offset { IntOffset(((arrow - 1f) * 14.dp.toPx()).roundToInt(), 0) }
                    .graphicsLayer { alpha = arrow },
            )
            Spacer(Modifier.weight(1f).widthIn(min = 8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (usesTimes) projectionEndLabel(plan) else L("Biggest week"), style = Theme.font(13), color = Theme.greenOnDark)
                ProjectionCountingText(
                    value = if (counted) endValue else startValue,
                    format = format,
                    style = Theme.font(42, FontWeight.Bold),
                    color = Color.White,
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .projectionStaged(2, stage)
                .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text(projectionVerdict(plan, hasResult), style = Theme.font(14, FontWeight.SemiBold), color = Color.White, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ProjectionStats(plan: TrainingPlan, answers: OnboardingAnswers, stage: Int) {
    val sessions = OnboardingCopy.sessionsPerWeek(answers)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ProjectionStatTile(
            index = 3, stage = stage, icon = Icons.Filled.CalendarMonth, target = plan.weeks.size.toDouble(),
            format = { it.toString() }, label = if (plan.weeks.size == 1) L("week of plan") else L("weeks of plan"),
        )
        ProjectionStatTile(
            index = 4, stage = stage, icon = Icons.AutoMirrored.Filled.DirectionsRun, target = sessions.toDouble(),
            format = { it.toString() }, label = if (sessions == 1) L("session a week") else L("sessions a week"),
        )
        ProjectionStatTile(
            index = 5, stage = stage, icon = Icons.Filled.BarChart, target = plan.peakKm,
            format = { Fmt.km(it.toDouble(), decimals = 0) }, label = L("the biggest week"),
        )
    }
}

/** The tile rises in at its stage and its number counts up from zero. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.ProjectionStatTile(
    index: Int,
    stage: Int,
    icon: ImageVector,
    target: Double,
    format: (Int) -> String,
    label: String,
) {
    val shown = stage > index
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .heightIn(min = 112.dp)
            .projectionStaged(index, stage)
            .card()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Theme.green, modifier = Modifier.size(22.dp))
        ProjectionCountingText(
            value = if (shown) target else 0.0,
            format = format,
            style = Theme.font(20, FontWeight.Bold),
            color = Theme.ink,
            durationMillis = 700,
            easing = ProjectionEaseOut,
        )
        Text(label, style = Theme.font(12), color = Theme.muted)
    }
}

// Reviews

/** A review of the onboarding, with the photo of its author (shown with their agreement). */
private class OnboardingReview(val text: String, val author: String, val photo: String)

// Reviews supplied by the team. They must come from real users: keep the wording they gave.
private fun onboardingSampleReviews(): List<OnboardingReview> = listOf(
    OnboardingReview(L("I finally ran 10 km under 45 minutes with Atala. It helped so much."), "Marie", "hero_today_woman"),
    OnboardingReview(L("Atala helped me get in better shape in just a few months. I strongly recommend it."), "Thomas", "hero_today_man"),
    OnboardingReview(L("A friend recommended Atala. I didn't know you could have a coach this good at this price."), "Luis", "photo_trio"),
)

/** `AtalaPhotos.strip(for: nil)`: the photos of the moving strip when no gender is used. */
private val ProjectionStripPhotos = listOf("photo_trio", "hero_today_woman", "hero_today_man", "photo_community")

/** Screen 23: what runners say. */
@Composable
fun OnboardingReviewsScreen() {
    val reviews = remember { onboardingSampleReviews() }
    // 1 photos and stars, then one stage per review, then the button.
    val delays = remember(reviews.size) { listOf(0.15) + List(reviews.size) { 0.3 } + listOf(0.35) }
    val stage = rememberProjectionStage(delays)

    OnboardingScaffold(title = L("Runners love their Atala coach"), buttonsVisible = stage >= delays.size) {
        // Runners in motion above the reviews, five stars underneath.
        Column(
            Modifier.fillMaxWidth().projectionStaged(0, stage),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ProjectionPhotoStrip(
                ProjectionStripPhotos, tileWidth = 104.dp, height = 132.dp, speed = 16f,
                modifier = Modifier.clip(RoundedCornerShape(22.dp)),
            )
            ProjectionStars(20.dp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            reviews.forEachIndexed { index, review ->
                ProjectionReviewCard(review, Modifier.projectionStaged(index + 1, stage))
            }
        }
    }
}

@Composable
private fun ProjectionStars(size: Dp) {
    Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(5) { Icon(Icons.Filled.Star, contentDescription = null, tint = Theme.yellow, modifier = Modifier.size(size)) }
    }
}

@Composable
private fun ProjectionReviewCard(review: OnboardingReview, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .card(radius = 22.dp)
            .semantics(mergeDescendants = true) {}
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProjectionStars(13.dp)
        Text(review.text, style = Theme.font(16).copy(lineHeight = 22.sp), color = Theme.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Theme.greenTint)) {
                AtalaImages[review.photo]?.let { id ->
                    Image(
                        painterResource(id), contentDescription = null, contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Text(review.author, style = Theme.font(14, FontWeight.SemiBold), color = Theme.body)
        }
    }
}

/**
 * `OnboardingPhotoStrip`: running photos gliding from right to left, endlessly. Still with reduced
 * motion. Drawn left to right whatever the language (it is decoration).
 */
@Composable
private fun ProjectionPhotoStrip(names: List<String>, tileWidth: Dp, height: Dp, speed: Float, modifier: Modifier = Modifier) {
    val spacing = 10.dp
    // Enough tiles to cover any phone twice over, so the loop never shows a gap.
    val tiles = remember(names) {
        val list = mutableListOf<String>()
        while (names.isNotEmpty() && list.size < 8) list += names
        list
    }
    val reduceMotion = rememberProjectionReduceMotion()
    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) withFrameNanos { seconds = (it - start) / 1_000_000_000f }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier
                .fillMaxWidth()
                .height(height)
                .clipToBounds()
                .clearAndSetSemantics {},
        ) {
            Row(
                Modifier
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .offset {
                        val loop = tiles.size * (tileWidth + spacing).toPx()
                        val shift = if (loop > 0) (seconds * speed * density) % loop else 0f
                        IntOffset(-shift.roundToInt(), 0)
                    },
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                // Twice the list: when the first copy has left, the second one is exactly in its place.
                repeat(tiles.size * 2) { index ->
                    Box(Modifier.width(tileWidth).height(height).clip(RoundedCornerShape(22.dp)).background(Color(0xFF1B1B1B))) {
                        AtalaImages[tiles[index % tiles.size]]?.let { id ->
                            Image(painterResource(id), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}



/** `OnboardingPhotoCard`: large rounded photo, darkened at the bottom so white text stays readable. */
@Composable
private fun ProjectionPhotoCard(name: String, height: Dp, modifier: Modifier = Modifier, caption: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF1B1B1B)),
    ) {
        AtalaImages[name]?.let { id ->
            Image(painterResource(id), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.5f to Color.Black.copy(alpha = 0f), 1f to Color.Black.copy(alpha = 0.72f))),
        )
        Box(Modifier.align(Alignment.BottomStart), content = caption)
    }
}
