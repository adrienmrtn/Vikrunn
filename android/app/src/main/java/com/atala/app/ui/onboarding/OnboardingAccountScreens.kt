package com.atala.app.ui.onboarding

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atala.app.R
import com.atala.app.core.account.AuthSignInFlow
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.l10n.midSentence
import com.atala.app.core.model.Account
import com.atala.app.core.model.OnboardingAnswers
import com.atala.app.core.model.isOpenEnded
import com.atala.app.core.plan.PlanEngine
import com.atala.app.core.store.AppState
import com.atala.app.core.store.AppStore
import com.atala.app.core.sync.SyncSignInOutcome
import com.atala.app.core.sync.SyncSignInPolicy
import com.atala.app.ui.auth.SignInOptionsSheet
import com.atala.app.ui.components.CoachAvatar
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.Haptics
import com.atala.app.ui.components.card
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.components.rememberReduceMotion
import com.atala.app.ui.root.LocalAppStore
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private class AccountSummaryRow(val label: String, val icon: ImageVector, val value: String)

private fun accountSummaryRows(state: AppState): List<AccountSummaryRow> {
    val answers = state.answers
    val rows = mutableListOf(AccountSummaryRow(L("Goal"), Icons.Filled.TrackChanges, OnboardingCopy.goalHeadline(answers)))
    val race = answers.raceDate
    if (answers.hasRace && race != null) {
        rows += AccountSummaryRow(L("Race"), Icons.Filled.SportsScore, Fmt.day(race, "EEEE d MMMM"))
    }
    rows += AccountSummaryRow(L("Rhythm"), Icons.Filled.CalendarMonth, OnboardingCopy.sessions(OnboardingCopy.sessionsPerWeek(answers)))
    rows += AccountSummaryRow(L("Coach"), Icons.Filled.Person, L("%@, %@ tone", state.coach.name, answers.tone.title.midSentence))
    return rows
}

/**
 * Screen 24: summary of the answers, then the sign-in options sheet (`ui.auth.SignInOptionsSheet`).
 * Skipped by the navigator once the phone holds an account.
 */
@Composable
fun OnboardingSignInScreen() {
    val state by collectAppState()
    val nav = LocalOnboardingNavigator.current
    var showOptions by remember { mutableStateOf(false) }
    // An account that already holds a plan gets it back and opens the app. A new account carries on:
    // the plan is built next and saved to the account.
    val signIn = rememberOnboardingSignIn(onContinued = { nav.next() })

    OnboardingScaffold(onContinue = { showOptions = true }) {
        CoachAvatar(state.coach, size = 72.dp)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(L("It's all here, %@", state.firstName), style = Theme.title(30), color = Theme.ink, modifier = Modifier.semantics { heading() })
            Text(
                L("I have what I need to build your plan. Let's put it somewhere safe first."),
                style = Theme.font(16).copy(lineHeight = 22.sp),
                color = Color(0xFF5A5A5A),
            )
        }
        AccountSummaryCard(accountSummaryRows(state))
        Text(
            L("One account is enough to find your plan everywhere."),
            style = Theme.font(13),
            color = Theme.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showOptions) {
        SignInOptionsSheet(
            onSignedIn = { account ->
                showOptions = false
                signIn.finish(account)
            },
            onDismiss = { showOptions = false },
        )
    }
    signIn.ErrorAlert()
}

/**
 * What the onboarding does with the account `SignInOptionsSheet` returned (`finishSignIn` of the
 * iPhone's sign-in and welcome screens): [AuthSignInFlow.complete] with
 * [SyncSignInPolicy.PreferAccount]. A restored plan finishes the onboarding (the root switches to
 * the app), a new account calls [onContinued], a failure shows "We couldn't open your account".
 */
internal class OnboardingSignIn(
    private val store: AppStore,
    private val scope: CoroutineScope,
    private val haptics: Haptics,
    private val onContinued: () -> Unit,
) {
    private var isRestoring = false
    private var error by mutableStateOf<String?>(null)

    fun finish(account: Account) {
        if (isRestoring) return
        isRestoring = true
        scope.launch {
            val outcome = AuthSignInFlow.complete(account, store, SyncSignInPolicy.PreferAccount)
            isRestoring = false
            when (outcome) {
                SyncSignInOutcome.Restored, SyncSignInOutcome.NeedsChoice -> Unit // the root switches to the app
                SyncSignInOutcome.Continued -> onContinued()
                is SyncSignInOutcome.Failed -> {
                    haptics.play(HapticKind.Error)
                    error = outcome.failure.userMessage
                }
            }
        }
    }

    @Composable
    fun ErrorAlert() {
        val message = error ?: return
        AlertDialog(
            onDismissRequest = { error = null },
            containerColor = Color.White,
            title = { Text(L("We couldn't open your account"), style = Theme.font(18, FontWeight.SemiBold), color = Theme.ink) },
            text = { Text(message.ifEmpty { L("Please try again.") }, style = Theme.font(15), color = Theme.body) },
            confirmButton = {
                TextButton(onClick = { error = null }) {
                    Text(L("OK"), style = Theme.font(15, FontWeight.SemiBold), color = Theme.green)
                }
            },
        )
    }
}

@Composable
internal fun rememberOnboardingSignIn(onContinued: () -> Unit): OnboardingSignIn {
    val store = LocalAppStore.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val latest by rememberUpdatedState(onContinued)
    return remember(store, scope, haptics) { OnboardingSignIn(store, scope, haptics) { latest() } }
}

@Composable
private fun AccountSummaryCard(rows: List<AccountSummaryRow>) {
    Column(
        Modifier
            .fillMaxWidth()
            .card(radius = 24.dp)
            .padding(horizontal = 18.dp, vertical = 6.dp),
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(thickness = 1.dp, color = Theme.cardBorder)
            Row(
                Modifier.fillMaxWidth().height(56.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(row.icon, contentDescription = null, tint = Theme.green, modifier = Modifier.width(22.dp).height(18.dp))
                Text(row.label, style = Theme.font(15), color = Theme.muted)
                Text(
                    row.value,
                    style = Theme.font(15, FontWeight.SemiBold),
                    color = Theme.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
            }
        }
    }
}

/** Seconds the ring takes to fill. */
private const val LoadingFillSeconds = 4.8

/** Seconds spent on the finished ring before the next screen. */
private const val LoadingRestSeconds = 0.9

private fun loadingStages(answers: OnboardingAnswers): List<String> {
    val sessions = OnboardingCopy.sessionsPerWeek(answers)
    val list = mutableListOf(
        L("Reading your answers"),
        // No pace talk for someone who does not run yet.
        if (OnboardingNavigator.isNotRunningYet(answers)) L("Planning a gentle start") else L("Working out your paces"),
        if (sessions == 1) L("Placing your session of the week") else L("Placing your %@ sessions a week", sessions),
        L("Adding strength and stretching"),
    )
    val race = answers.raceDate
    list += if (answers.hasRace && race != null && !answers.goal.isOpenEnded) {
        L("Easing off before %@", Fmt.day(race, "d MMMM"))
    } else {
        L("Balancing your %@ weeks", PlanEngine.MAX_WEEKS)
    }
    return list
}

private fun loadingSubtitle(answers: OnboardingAnswers): String {
    var text = OnboardingCopy.goalHeadline(answers)
    val race = answers.raceDate
    if (answers.hasRace && race != null && !answers.goal.isOpenEnded) text += " · ${Fmt.day(race, "d MMMM")}"
    return text
}

/** 0...1 after `elapsed` seconds: slow start, steady middle, soft landing. Always moving forward. */
private fun loadingProgress(elapsed: Double): Double {
    val t = (elapsed / LoadingFillSeconds).coerceIn(0.0, 1.0)
    val eased = t * t * (3 - 2 * t)
    return 0.3 * t + 0.7 * eased
}

/**
 * Screen 25: staged loading while the plan is generated. The ring fills in one continuous, eased
 * movement (its value is computed from the clock at every frame, never stepped), the checklist
 * follows it, and the screen rests a moment on the finished ring before moving on.
 */
@Composable
fun OnboardingLoadingScreen() {
    val store = LocalAppStore.current
    val state by collectAppState()
    val nav = LocalOnboardingNavigator.current
    val haptics = rememberHaptics()
    val reduceMotion = rememberReduceMotion()
    // Nanosecond clock of the first frame, 0 until the screen is on.
    var startedAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(0L) }
    var appeared by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        store.generatePlan()
        appeared = true
        startedAt = withFrameNanos { it }
        delay((LoadingFillSeconds * 1000).toLong())
        completed = true
        haptics.play(HapticKind.Success)
        delay((LoadingRestSeconds * 1000).toLong())
        nav.next()
    }
    // One value per frame, from the clock.
    LaunchedEffect(completed) {
        if (completed) return@LaunchedEffect
        while (true) withFrameNanos { now = it }
    }

    val progress = when {
        completed -> 1.0
        startedAt == 0L -> 0.0
        else -> loadingProgress((now - startedAt).coerceAtLeast(0L) / 1_000_000_000.0)
    }
    val percent = (progress * 100).roundToInt()
    val shown by animateFloatAsState(if (appeared) 1f else 0f, if (reduceMotion) tween(0) else tween(500, easing = OnboardingEasing.easeOut), label = "appear")
    val stages = remember(state.answers) { loadingStages(state.answers) }
    val ringLabel = L("Plan being built, %@ percent", percent)

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 28.dp, end = 28.dp, top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        Image(
            painterResource(R.drawable.logo_atala), contentDescription = "Atala",
            colorFilter = ColorFilter.tint(Theme.forest),
            modifier = Modifier.height(22.dp).alpha(shown),
        )
        LoadingRing(
            progress = progress,
            completed = completed,
            reduceMotion = reduceMotion,
            coachAvatar = { CoachAvatar(state.coach, size = 136.dp) },
            modifier = Modifier
                .graphicsLayer {
                    alpha = shown
                    val scale = 0.94f + 0.06f * shown
                    scaleX = scale
                    scaleY = scale
                }
                .clearAndSetSemantics { contentDescription = ringLabel },
        )
        Column(
            Modifier.graphicsLayer {
                alpha = shown
                translationY = (1f - shown) * 8.dp.toPx()
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("$percent %", style = Theme.font(15, FontWeight.SemiBold).copy(fontFeatureSettings = "tnum"), color = Theme.green, modifier = Modifier.clearAndSetSemantics {})
            Crossfade(completed, label = "loading title") { done ->
                Text(
                    if (done) L("Your plan is ready") else L("I'm building your plan"),
                    style = Theme.title(26),
                    color = Theme.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().semantics { heading() },
                )
            }
            Text(loadingSubtitle(state.answers), style = Theme.font(15), color = Theme.muted, textAlign = TextAlign.Center)
        }
        LoadingStageList(stages, progress, appeared, reduceMotion)
    }
}

@Composable
private fun LoadingRing(
    progress: Double,
    completed: Boolean,
    reduceMotion: Boolean,
    coachAvatar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A small breath when the ring closes.
    val breath by animateFloatAsState(if (completed && !reduceMotion) 1.04f else 1f, spring(dampingRatio = 0.6f, stiffness = 158f), label = "breath")
    Box(modifier.size(180.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val width = 8.dp.toPx()
            val inset = width / 2
            val arcSize = Size(size.width - width, size.height - width)
            drawCircle(Theme.hairline, radius = (size.minDimension - width) / 2, style = Stroke(width))
            drawArc(
                Theme.green,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0.002, 1.0).toFloat(),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width, cap = StrokeCap.Round),
            )
        }
        Box(Modifier.graphicsLayer {
            scaleX = breath
            scaleY = breath
        }) { coachAvatar() }
    }
}

@Composable
private fun LoadingStageList(items: List<String>, progress: Double, appeared: Boolean, reduceMotion: Boolean) {
    val count = maxOf(items.size, 1).toDouble()
    Column(Modifier.fillMaxWidth()) {
        items.forEachIndexed { index, text ->
            val done = progress >= (index + 1) / count
            val active = !done && progress >= index / count
            // Each line fades in a little after the one above it.
            val entrance by animateFloatAsState(
                if (appeared) 1f else 0f,
                if (reduceMotion) tween(0) else tween(450, delayMillis = 150 + 90 * index, easing = OnboardingEasing.easeOut),
                label = "line",
            )
            val color by animateColorAsState(if (done || active) Theme.ink else Theme.faint, tween(350), label = "line colour")
            Column(Modifier.graphicsLayer {
                alpha = entrance
                translationY = (1f - entrance) * 10.dp.toPx()
            }) {
                HorizontalDivider(thickness = 1.dp, color = Theme.hairline)
                Row(
                    Modifier.fillMaxWidth().height(50.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LoadingStageMark(done, active)
                    // One weight for every state: the line never changes width.
                    Text(text, style = Theme.font(15, FontWeight.Medium), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = Theme.hairline, modifier = Modifier.alpha(if (appeared) 1f else 0f))
    }
}

/**
 * Empty circle, then tinted while its step is running, then the tick grows into it. The marks stay
 * in place, so nothing is inserted or removed.
 */
@Composable
private fun LoadingStageMark(done: Boolean, active: Boolean) {
    val tick by animateFloatAsState(if (done) 1f else 0f, spring(dampingRatio = 0.72f, stiffness = 273f), label = "tick")
    val ring by animateColorAsState(if (active) Theme.green else Theme.controlBorder, tween(300), label = "ring")
    Box(Modifier.size(22.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(20.dp).alpha(1f - tick.coerceIn(0f, 1f))) {
            val width = 2.dp.toPx()
            drawCircle(ring, radius = (size.minDimension - width) / 2, style = Stroke(width))
        }
        Icon(
            Icons.Filled.CheckCircle, contentDescription = null, tint = Theme.green,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    val scale = 0.5f + 0.5f * tick
                    scaleX = scale
                    scaleY = scale
                    alpha = tick.coerceIn(0f, 1f)
                },
        )
    }
}
