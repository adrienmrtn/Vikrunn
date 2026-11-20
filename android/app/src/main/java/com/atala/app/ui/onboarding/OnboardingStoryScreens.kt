package com.atala.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atala.app.ui.components.adaptiveHeight
import com.atala.app.R
import com.atala.app.core.l10n.AppLanguage
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.CoachPose
import com.atala.app.core.model.Gender
import com.atala.app.core.model.WorkoutKind
import com.atala.app.core.plan.PlanEngine
import com.atala.app.core.purchases.PurchaseService
import com.atala.app.ui.components.CoachPoseImage
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.IconTile
import com.atala.app.ui.components.card
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.AtalaImages
import com.atala.app.ui.theme.Theme
import java.text.NumberFormat

/** The Atala logo in one colour (`BrandLogo`): the full word, or the mark alone. */
@Composable
fun OnboardingBrandLogo(height: Dp, color: Color, modifier: Modifier = Modifier, mark: Boolean = false) {
    Image(
        painterResource(if (mark) R.drawable.logo_atala_mark else R.drawable.logo_atala),
        contentDescription = "Atala",
        colorFilter = ColorFilter.tint(color),
        contentScale = ContentScale.Fit,
        modifier = modifier.height(height),
    )
}

/** The running photos of the catalog (`AtalaPhotos`). */
object OnboardingPhotos {
    const val MAN = "hero_today_man"
    const val WOMAN = "hero_today_woman"
    const val GROUP = "photo_community"
    const val TRIO = "photo_trio"
}

/**
 * Large rounded photo of the story screens (`OnboardingPhotoCard`), cropped to the frame;
 * [alignment] chooses the part that stays visible. [scrim] darkens the bottom for white text.
 */
@Composable
fun OnboardingPhotoCard(
    name: String,
    modifier: Modifier = Modifier,
    height: Dp = 300.dp,
    alignment: Alignment = Alignment.Center,
    scrim: Boolean = false,
    caption: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier
            .fillMaxWidth()
            // A little lower on small phones, so the text under it stays in view.
            .height(adaptiveHeight(height, minimum = 0.75f))
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF1B1B1B)),
    ) {
        AtalaImages[name]?.let { id ->
            Image(
                painterResource(id),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = alignment,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (scrim) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.5f to Color.Black.copy(alpha = 0f),
                            1f to Color.Black.copy(alpha = 0.72f),
                        ),
                    ),
            )
        }
        Box(Modifier.align(Alignment.BottomStart), content = caption)
    }
}

/** A card with an icon tile and a sentence (community, "beyond the coach"). */
@Composable
private fun OnboardingIconCard(icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .card(radius = 22.dp)
            .padding(14.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, size = 48.dp)
        Box(Modifier.weight(1f)) { content() }
    }
}

/** Right after the first name: a warm welcome before the questions start (step `Intro`). */
@Composable
fun OnboardingIntroScreen() {
    val state by collectAppState()
    // 1 photo, 2 title, 3 text, 4 button.
    val stage = rememberOnboardingStages(listOf(0.1, 0.35, 0.35, 0.4))
    OnboardingScaffold(buttonTitle = L("Let's go"), buttonsVisible = stage >= 4) {
        OnboardingPhotoCard(
            "hero_today_man",
            height = 320.dp,
            alignment = Alignment.TopCenter,
            scrim = true,
            modifier = Modifier.onboardingStaged(0, stage),
        ) {
            OnboardingBrandLogo(24.dp, Theme.cream, Modifier.padding(20.dp))
        }
        Column(
            Modifier
                .onboardingStaged(1, stage)
                .semantics(mergeDescendants = true) { heading() },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(L("Hey %@,", state.firstName), style = Theme.title(32), color = Theme.ink)
            Text(L("welcome to Atala, your new running coach."), style = Theme.title(32), color = Theme.green)
        }
        Text(
            L("A few quick questions, and your plan is built around you."),
            style = Theme.font(17).copy(lineHeight = 25.sp),
            color = Theme.body,
            modifier = Modifier.onboardingStaged(2, stage),
        )
    }
}

// COMMUNITY FIGURE — shown to every new user: keep it true, update it from real usage before release.
object OnboardingCommunityFigure {
    const val VALUE = 10_000
    val caption: String get() = L("runners coached every day")
}

private fun grouped(value: Int): String = NumberFormat.getIntegerInstance(AppLanguage.locale).format(value)

/** Right after the reviews: the runners already training with Atala (step `Community`). */
@Composable
fun OnboardingCommunityScreen() {
    // 1 title, 2 photo and figure, 3 community card, 4 button.
    val stage = rememberOnboardingStages(listOf(0.1, 0.35, 0.7, 0.4))
    OnboardingScaffold(
        title = L("Join the Atala community"),
        buttonTitle = L("Join them"),
        buttonsVisible = stage >= 4,
    ) {
        OnboardingPhotoCard(
            "photo_community",
            height = 340.dp,
            scrim = true,
            modifier = Modifier.onboardingStaged(1, stage),
        ) {
            Column(
                Modifier
                    .padding(20.dp)
                    .clearAndSetSemantics {
                        contentDescription = L("%@ runners coached every day", grouped(OnboardingCommunityFigure.VALUE))
                    },
            ) {
                OnboardingCountingValue(
                    value = if (stage > 1) OnboardingCommunityFigure.VALUE.toDouble() else 0.0,
                    format = ::grouped,
                    style = Theme.font(52, FontWeight.Bold),
                    color = Color.White,
                    durationMillis = 1100,
                )
                Text(OnboardingCommunityFigure.caption, style = Theme.font(17, FontWeight.SemiBold), color = Color.White.copy(alpha = 0.92f))
            }
        }
        OnboardingIconCard(Icons.Filled.IosShare, Modifier.onboardingStaged(2, stage)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(L("Atala is also a community"), style = Theme.font(17, FontWeight.SemiBold), color = Theme.ink)
                Text(L("Share your results with your friends 😉"), style = Theme.font(15), color = Theme.body)
            }
        }
    }
}

/** Just before the free trial: what Atala does beyond the plan (step `BeyondCoach`). */
@Composable
fun OnboardingBeyondCoachScreen() {
    var emphasised by remember { mutableStateOf(false) }
    // 1 lead-in, 2-4 the points, 5 button.
    val stage = rememberOnboardingStages(listOf(0.2, 0.55, 0.9, 0.9, 0.7), isActive = emphasised)
    val points = listOf(
        Icons.Filled.Bolt to L("Run stronger, week after week"),
        Icons.Filled.SelfImprovement to L("Get every warm-up and stretch you need"),
        Icons.Filled.AlarmOn to L("Get answers 24/7 from a coach who knows you"),
    )
    OnboardingScaffold(buttonsVisible = stage >= 5) {
        OnboardingPhotoCard(OnboardingPhotos.WOMAN, height = 210.dp, alignment = Alignment.TopCenter)
        // The part between asterisks is the one shown in colour.
        OnboardingEmphasisText(
            text = L("Atala is *not just a coach.*"),
            size = 34,
            lineSpacing = 4.dp,
            isHeading = true,
            onFinished = { emphasised = true },
        )
        Text(
            L("It will help you:"),
            style = Theme.font(18, FontWeight.SemiBold),
            color = Theme.muted,
            modifier = Modifier.onboardingStaged(0, stage),
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            points.forEachIndexed { index, (icon, title) ->
                OnboardingIconCard(icon, Modifier.onboardingStaged(index + 1, stage)) {
                    Text(title, style = Theme.font(17, FontWeight.SemiBold), color = Theme.ink)
                }
            }
        }
    }
}

/**
 * Small rounded running photo that stands for a session in a list (`OnboardingSessionThumbnail`).
 * A hard session keeps its orange mark, as a dot in the corner.
 */
@Composable
fun OnboardingSessionThumbnail(name: String, quality: Boolean = false, size: Dp = 52.dp) {
    Box(Modifier.size(size)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1B1B1B)),
        ) {
            AtalaImages[name]?.let { id ->
                Image(
                    painterResource(id),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (quality) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 3.dp, y = 3.dp)
                    .size(12.dp)
                    .background(Color.White, CircleShape)
                    .padding(2.dp)
                    .background(Theme.orange, CircleShape),
            )
        }
    }
}

object OnboardingSessionPhotos {
    /** The photo that suits a kind of session best, then the others, so a list can avoid repeats. */
    private fun candidates(kind: WorkoutKind, gender: Gender?): List<String> {
        val runner = if (gender == Gender.Man) OnboardingPhotos.MAN else OnboardingPhotos.WOMAN
        val other = if (gender == Gender.Man) OnboardingPhotos.WOMAN else OnboardingPhotos.MAN
        return when (kind) {
            WorkoutKind.Easy, WorkoutKind.Free ->
                listOf(runner, "welcome_photo_3", "welcome_photo_1", other, OnboardingPhotos.TRIO, OnboardingPhotos.GROUP)
            WorkoutKind.Intervals, WorkoutKind.Tempo, WorkoutKind.RacePace ->
                listOf(OnboardingPhotos.TRIO, "welcome_photo_2", other, "welcome_photo_1", runner, OnboardingPhotos.GROUP)
            WorkoutKind.Long, WorkoutKind.Race ->
                listOf(OnboardingPhotos.GROUP, "welcome_photo_1", "welcome_photo_3", OnboardingPhotos.TRIO, other, runner)
            WorkoutKind.Strength, WorkoutKind.Stretching ->
                listOf("photo_session_complete", "welcome_photo_2", "welcome_photo_3", other, OnboardingPhotos.TRIO, runner)
        }
    }

    /** One photo per session, in order, never the same twice while there are photos left. */
    fun photos(kinds: List<WorkoutKind>, gender: Gender?): List<String> {
        val result = mutableListOf<String>()
        for (kind in kinds) {
            val options = candidates(kind, gender)
            result.add(options.firstOrNull { it !in result } ?: options.firstOrNull() ?: OnboardingPhotos.TRIO)
        }
        return result
    }
}

/** After "not just a coach": why a clear plan matters, with the first week of the user's own plan (step `PlanMatters`). */
@Composable
fun OnboardingPlanMattersScreen() {
    val state by collectAppState()
    var emphasised by remember { mutableStateOf(false) }
    val sessions = state.plan?.weeks?.firstOrNull()?.workouts.orEmpty().take(4)
    // 1 caption, then one stage per session, then the closing line and the button.
    val delays = listOf(0.2) + List(sessions.size) { 0.4 } + listOf(0.5, 0.4)
    val stage = rememberOnboardingStages(delays, isActive = emphasised)
    val photos = remember(sessions.map { it.kind }, state.answers.gender) {
        OnboardingSessionPhotos.photos(sessions.map { it.kind }, state.answers.gender)
    }
    val weeks = state.plan?.weeks?.size ?: PlanEngine.MAX_WEEKS
    OnboardingScaffold(buttonsVisible = stage >= delays.size) {
        OnboardingEmphasisText(
            text = L("Having a *clear, precise plan* is essential in running."),
            size = 32,
            lineSpacing = 5.dp,
            isHeading = true,
            onFinished = { emphasised = true },
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            L("YOUR FIRST WEEK"),
            style = Theme.font(12, FontWeight.Bold).copy(letterSpacing = 0.8.sp),
            color = Theme.muted,
            modifier = Modifier.onboardingStaged(sessions.isNotEmpty() && stage > 0),
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            sessions.forEachIndexed { index, workout ->
                Row(
                    Modifier
                        .onboardingStaged(index + 1, stage)
                        .fillMaxWidth()
                        .card(radius = 20.dp)
                        .padding(start = 10.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
                        .semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OnboardingSessionThumbnail(photos.getOrElse(index) { OnboardingPhotos.TRIO }, quality = workout.kind.isQuality)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(workout.title, style = Theme.font(16, FontWeight.SemiBold), color = Theme.ink, maxLines = 1)
                        Text(Fmt.day(workout.date, "EEEE"), style = Theme.font(13), color = Theme.muted)
                    }
                    Text(
                        if (workout.kind.isRun) Fmt.km(workout.distanceKm) else Fmt.minutes(workout.durationMin),
                        style = Theme.font(15, FontWeight.SemiBold).copy(fontFeatureSettings = "tnum"),
                        color = Theme.ink,
                    )
                }
            }
        }
        Text(
            L("Yours is ready: %@, session by session.", OnboardingCopy.weeks(weeks)),
            style = Theme.font(17, FontWeight.SemiBold),
            color = Theme.green,
            modifier = Modifier.onboardingStaged(sessions.size + 1, stage),
        )
    }
}

/** After the plan preview, before the promise: one last "ready?" (step `Ready`). */
@Composable
fun OnboardingReadyScreen() {
    val state by collectAppState()
    var coachShown by remember { mutableStateOf(false) }
    // 0 nothing said, 1 first sentence, 2 the question.
    var spoken by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        coachShown = true
    }
    OnboardingScaffold(buttonTitle = L("I'm ready"), buttonHaptic = HapticKind.Strong, buttonsVisible = spoken >= 2) {
        CoachPoseImage(
            state.coach,
            pose = CoachPose.ThumbsUp,
            modifier = Modifier
                .fillMaxWidth()
                .height(adaptiveHeight(250.dp))
                .onboardingSpringIn(coachShown, from = 0.9f, response = 0.55, damping = 0.74)
                .clearAndSetSemantics {},
        )
        OnboardingSpokenText(
            text = L("Okay %@, I've got everything I need to help you reach your goals.", state.firstName),
            size = 26,
            weight = FontWeight.Bold,
            lineSpacing = 4.dp,
            startDelay = 0.5,
            isHeading = true,
            onFinished = { spoken = maxOf(spoken, 1) },
        )
        OnboardingSpokenText(
            text = L("Are you ready?"),
            size = 34,
            weight = FontWeight.Bold,
            color = Theme.green,
            lineSpacing = 2.dp,
            isActive = spoken >= 1,
            startDelay = 0.25,
            onFinished = { spoken = maxOf(spoken, 2) },
        )
    }
}

private data class SampleNotification(val id: Int, val time: String, val title: String, val body: String)

/**
 * After the location permission: what the reminders look like, then the system question (step
 * `Notifications`). Saying no is fine: the sessions stay in the Today tab.
 */
@Composable
fun OnboardingNotificationsScreen() {
    val state by collectAppState()
    val nav = LocalOnboardingNavigator.current
    val inTrial by PurchaseService.isInTrial.collectAsStateWithLifecycle()
    var isAsking by remember { mutableStateOf(false) }
    // One stage per sample notification, then the text, then the buttons.
    val stage = rememberOnboardingStages(listOf(0.25, 0.45, 0.45, 0.4, 0.35))
    val coach = state.coach.name
    val next = state.nextRun
    val first = when {
        next == null -> L("Easy run · 5 km today")
        next.distanceKm > 0 -> L("%@ · %@ today", next.title, Fmt.km(next.distanceKm))
        else -> L("%@ today", next.title)
    }
    val samples = listOf(
        SampleNotification(0, "8:00", first, L("%@ here. Keep it easy today: you should be able to talk the whole way.", coach)),
        SampleNotification(1, L("Thu 8:00"), L("Intervals today"), L("%@ here. A quality session today. Warm up well, I'll guide you through it.", coach)),
        SampleNotification(2, L("Sun 8:00"), L("Long run today"), L("%@ here. Slow and steady, the distance does the work.", coach)),
    )
    // Shows the system question (Android 13 and later), then moves on whatever the answer.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        isAsking = false
        nav.next()
    }
    val allow = {
        if (!isAsking) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                isAsking = true
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                nav.next()
            }
        }
    }
    OnboardingScaffold(
        title = L("Never miss a run"),
        buttonTitle = if (isAsking) L("One moment…") else L("Remind me to run"),
        canContinue = !isAsking,
        buttonsVisible = stage >= 5,
        secondaryTitle = L("Not now"),
        onSecondary = { nav.next() },
        onContinue = allow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Theme.surface, RoundedCornerShape(28.dp))
                .padding(14.dp)
                .clearAndSetSemantics { contentDescription = L("Examples of reminders from your coach") },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            samples.forEach { sample ->
                // The cards behind the first one are a little smaller and lighter, like a stack.
                val shown = stage > sample.id
                NotificationCard(
                    sample,
                    Modifier
                        .onboardingStaged(shown, rise = -18f)
                        .graphicsScale(1f - sample.id * 0.03f, 1f - sample.id * 0.2f),
                )
            }
        }
        Text(
            if (inTrial) {
                L("Once your free trial is over, %@ reminds you on the morning of each session. Nothing else: no ads, no noise.", coach)
            } else {
                L("%@ reminds you on the morning of each session. Nothing else: no ads, no noise.", coach)
            },
            style = Theme.font(17).copy(lineHeight = 25.sp),
            color = Theme.body,
            modifier = Modifier.onboardingStaged(3, stage),
        )
    }
}

private fun Modifier.graphicsScale(scale: Float, alpha: Float): Modifier = graphicsLayer {
    scaleX = scale
    scaleY = scale
    this.alpha = alpha
}

@Composable
private fun NotificationCard(sample: SampleNotification, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .fillMaxWidth()
            .shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
            .background(Color.White, shape)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(Theme.forest, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            OnboardingBrandLogo(20.dp, Theme.cream, mark = true)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OnboardingFitText(sample.title, Theme.font(15, FontWeight.SemiBold), Theme.ink, Modifier.weight(1f), minScale = 0.8f)
                Spacer(Modifier.width(6.dp))
                Text(sample.time, style = Theme.font(12), color = Theme.muted)
            }
            Text(sample.body, style = Theme.font(14), color = Theme.body, maxLines = 2)
        }
    }
}
