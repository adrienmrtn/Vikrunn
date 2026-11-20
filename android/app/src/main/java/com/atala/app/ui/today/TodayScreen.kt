package com.atala.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.CoachTone
import com.atala.app.core.model.Gender
import com.atala.app.core.model.PlanCalendar
import com.atala.app.core.model.ProAccess
import com.atala.app.core.model.Weekday
import com.atala.app.core.model.Workout
import com.atala.app.core.model.WorkoutKind
import com.atala.app.core.model.WorkoutStatus
import com.atala.app.core.store.AppState
import com.atala.app.ui.components.CoachAvatar
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.main.Navigation
import com.atala.app.ui.paywall.GiftOfferChip
import com.atala.app.ui.paywall.ProChip
import com.atala.app.ui.paywall.ProUpsellCard
import com.atala.app.ui.root.AtalaPreview
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.Theme
import java.time.Instant

/**
 * Today tab (Atala/Features/Today/TodayView.swift). From top to bottom: the header, today's session
 * with its button, a short word from the coach, the Atala Pro card on the free plan, then the week
 * and what comes next. The avatar in its header opens the profile.
 */
@Composable
fun TodayScreen(nav: Navigation) {
    val state by collectAppState()
    val isPro by ProAccess.isPro.collectAsStateWithLifecycle()

    val todayRun = state.todayWorkouts.firstOrNull { it.kind.isRun }
    val featured = state.featured(todayRun)
    val extras = state.todayWorkouts.filter { !it.kind.isRun && it.id != featured?.id }
    val upcoming = state.upcoming(featured)
    val pending = state.mainUncountedRunToday
    val thisWeek = state.mainThisWeek
    val open: (Workout) -> Unit = { nav.onOpenWorkout(it.id) }

    MainStagedEntrance(MainStagedScreen.Today) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.White)
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = Theme.screenPadding, end = Theme.screenPadding, top = 4.dp, bottom = 28.dp),
        ) {
            TodayHeader(state, isPro, nav, Modifier.mainStagedAppear(0))
            TodayWeekStrip(Modifier.padding(top = 14.dp).mainStagedAppear(1))
            Box(Modifier.padding(top = 16.dp).mainStagedAppear(2)) { TodayHero(state.answers.gender) }
            Box(
                Modifier
                    .overlapTop(56.dp)
                    .padding(horizontal = 12.dp)
                    .mainStagedAppear(3),
            ) {
                when {
                    state.plan == null -> TodayMessageCard(
                        title = L("No plan yet"),
                        message = L("Set your goal and your running days in your profile and your plan will be ready in a moment."),
                        actionTitle = L("Open my profile"),
                        action = nav.onOpenProfile,
                    )
                    featured != null -> TodaySessionCard(
                        workout = featured,
                        isToday = PlanCalendar.isToday(featured.date),
                        restDay = state.todayWorkouts.isEmpty(),
                        onOpen = { nav.onOpenWorkout(featured.id) },
                    )
                    // Nothing left from today on: the plan is over.
                    else -> MainPlanEndedCard(nav.onOpenProfile)
                }
            }
            // What belongs to today's session: a run to count, the other sessions of the day.
            Column(Modifier.mainStagedAppear(4)) {
                if (pending != null) {
                    MainCountRunCard(pending.first, pending.second, Modifier.padding(top = 14.dp))
                }
                if (state.mainPlanEnded && featured != null) {
                    // The plan is over but today's (done) session still holds the main card.
                    MainPlanEndedCard(nav.onOpenProfile, Modifier.padding(top = 14.dp))
                }
                if (extras.isNotEmpty()) {
                    TodayExtraSessions(extras, open, Modifier.padding(top = 16.dp))
                }
            }
            TodayCoachLineCard(state, todayRun, Modifier.padding(top = 16.dp).mainStagedAppear(5))
            // Free plan only, and always under the session and the coach's word.
            if (!isPro) {
                ProUpsellCard(nav.onOpenPaywall, Modifier.padding(top = 12.dp).mainStagedAppear(6))
            }
            if (thisWeek != null) {
                TodayWeekVolume(thisWeek, Modifier.padding(top = 24.dp).mainStagedAppear(7))
            }
            TodayRaceCountdown(Modifier.mainStagedAppear(7))
            if (upcoming.isNotEmpty()) {
                Column(
                    Modifier.padding(top = 24.dp).mainStagedAppear(8),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MainSectionTitle(L("Coming up"))
                    TodayUpcomingList(upcoming, open)
                }
            }
        }
    }
}

// MARK: Data

/** Today's run, or the next planned run on a rest day, or whatever session is left in the plan. */
private fun AppState.featured(todayRun: Workout?): Workout? {
    (todayRun ?: nextRun)?.let { return it }
    val today = PlanCalendar.startOfDay(Instant.now())
    return allWorkouts.firstOrNull { it.status == WorkoutStatus.Planned && it.date >= today }
}

private fun AppState.upcoming(featured: Workout?): List<Workout> {
    val tomorrow = PlanCalendar.addDays(PlanCalendar.startOfDay(Instant.now()), 1)
    return allWorkouts
        .filter { it.date >= tomorrow && it.status == WorkoutStatus.Planned && it.id != featured?.id }
        .take(2)
}

private fun AppState.subtitle(): String {
    val parts = mutableListOf(Fmt.day(Instant.now(), "EEEE d MMMM"))
    val plan = plan
    if (plan != null) {
        val number = mainCurrentWeekNumber
        if (number != null) {
            parts += L("Week %@ of %@", number, plan.weeks.size)
        } else if (mainPlanEnded) {
            parts += L("Plan complete")
        }
    }
    return parts.joinToString(" · ")
}

// MARK: Sections

/** Logo, the gift chip while the offer runs and the profile; the greeting; then the date line with the streak. */
@Composable
private fun TodayHeader(state: AppState, isPro: Boolean, nav: Navigation, modifier: Modifier) {
    val haptics = rememberHaptics()
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandLogo(height = 22.dp)
            Spacer(Modifier.weight(1f).width(8.dp))
            GiftOfferChip()
            Spacer(Modifier.width(8.dp))
            val profile = L("Profile and settings")
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) {
                        haptics.tap()
                        nav.onOpenProfile()
                    }
                    .semantics { contentDescription = profile },
                contentAlignment = Alignment.Center,
            ) {
                CoachAvatar(state.coach, size = 32.dp, modifier = Modifier.border(1.dp, Theme.hairline, CircleShape))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FitText(
                L("Hello, %@", state.firstName),
                Theme.title(32),
                Theme.ink,
                modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                minScale = 0.7f,
            )
            if (isPro) ProChip()
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FitText(state.subtitle(), Theme.font(13), Theme.muted, Modifier.weight(1f), minScale = 0.8f)
            if (state.streak.current > 0) MainStreakChip(state.streak)
        }
    }
}

/** A runner photo matching the gender given in onboarding, or the illustration otherwise. */
@Composable
private fun TodayHero(gender: Gender?) {
    when (gender) {
        Gender.Man -> MainHeroImage("hero_today_man", height = 280.dp, alignment = Alignment.TopCenter)
        Gender.Woman -> MainHeroImage("hero_today_woman", height = 280.dp, alignment = Alignment.TopCenter)
        Gender.Other, Gender.Undisclosed, null -> MainHeroImage("illu_today", height = 190.dp)
    }
}

@Composable
private fun TodayCoachLineCard(state: AppState, todayRun: Workout?, modifier: Modifier) {
    val coach = state.coach
    Row(
        modifier
            .fillMaxWidth()
            .background(Theme.greenTint, RoundedCornerShape(20.dp))
            .padding(14.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CoachAvatar(coach, size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(L("%@, your coach", coach.name), style = Theme.font(13, FontWeight.Bold), color = Theme.green)
            Text(
                TodayCoachLine.text(state, todayRun, state.nextRun),
                style = Theme.font(15).copy(lineHeight = lineHeight(15, 4)),
                color = Theme.ink,
            )
        }
    }
}

/** The coach's short line of the day, built from the real state of the plan (`TodayCoachLine`). */
internal object TodayCoachLine {
    fun text(state: AppState, todayRun: Workout?, next: Workout?): String {
        if (state.plan == null) {
            return L("Tell me your goal and the days you can run, and I will build your plan.")
        }
        if (todayRun != null) {
            return when (todayRun.status) {
                WorkoutStatus.Done -> doneLine(state.answers.tone)
                WorkoutStatus.Missed -> L("No drama about this one. If you did run, tell me and I will count it.")
                WorkoutStatus.Planned -> plannedLine(todayRun)
            }
        }
        if (next == null) {
            if (state.mainPlanEnded) {
                return L("You have done everything on the plan. Tell me what you want to aim for next.")
            }
            return L("No run left on the plan. Keep up the strength and stretching, they count too.")
        }
        val day = Weekday.long(PlanCalendar.mondayIndex(next.date))
        var line = L("No run today. Rest is when your body gets stronger. Next up: %@ on %@.", next.title, day)
        val missedThisWeek = state.mainThisWeek?.workouts?.any { it.status == WorkoutStatus.Missed } ?: false
        if (missedThisWeek) {
            line += " " + L("Don't chase the missed session, just pick the plan up where it is.")
        }
        return line
    }

    private fun doneLine(tone: CoachTone): String = when (tone) {
        CoachTone.Kind -> L("Nice work today. Eat well, sleep well, and let the session sink in.")
        CoachTone.Direct -> L("Session done. Recover properly tonight.")
        CoachTone.Playful -> L("Ticked off. The sofa is all yours tonight.")
    }

    private fun plannedLine(run: Workout): String = when (run.kind) {
        WorkoutKind.Easy, WorkoutKind.Free -> L("Keep it truly easy today: you should be able to talk the whole way.")
        WorkoutKind.Long -> L("Slow and steady today. The distance does the work, not the speed.")
        WorkoutKind.Intervals, WorkoutKind.Tempo, WorkoutKind.RacePace -> {
            val pace = run.targetPace
            if (pace != null) {
                L("Start a touch under control, then settle on %@/km. The last efforts should look like the first.", Fmt.pace(pace))
            } else {
                L("Start a touch under control. The last efforts should look like the first.")
            }
        }
        WorkoutKind.Race -> L("This is your day. Start calm, hold your pace, and trust your training.")
        WorkoutKind.Strength, WorkoutKind.Stretching -> run.subtitle
    }
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun TodayScreenPreview() {
    AtalaPreview { TodayScreen(Navigation.Preview) }
}
