import SwiftUI

/// Home screen: today's session, the week at a glance and a short word from the coach.
struct TodayView: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var chrome: MainChrome

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text(subtitle)
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.muted)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                        .mainStagedAppear(index: 0)
                    TodayWeekStrip()
                        .padding(.top, 14)
                        .mainStagedAppear(index: 1)
                    MainHeroImage(name: "illu_today", height: 190)
                        .padding(.top, 16)
                        .mainStagedAppear(index: 2)
                    heroCard
                        .padding(.horizontal, 12)
                        .padding(.top, -56)
                        .mainStagedAppear(index: 3)
                    if !extras.isEmpty {
                        TodayExtraSessions(workouts: extras)
                            .padding(.top, 16)
                            .mainStagedAppear(index: 4)
                    }
                    if let week = store.currentWeek {
                        TodayWeekVolume(week: week)
                            .padding(.top, 18)
                            .mainStagedAppear(index: 5)
                    }
                    TodayRaceCountdown()
                        .mainStagedAppear(index: 6)
                    coachLine
                        .padding(.top, 16)
                        .mainStagedAppear(index: 7)
                    if !upcoming.isEmpty {
                        TodayUpcomingList(workouts: upcoming)
                            .padding(.top, 12)
                            .mainStagedAppear(index: 8)
                    }
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 4)
                .padding(.bottom, 28)
                .mainStagedEntrance(.today)
            }
            .background(Color.white)
            .navigationTitle("Hello, \(store.firstName)")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    profileButton
                }
            }
        }
    }

    // MARK: Data

    private var todayRun: Workout? {
        store.todayWorkouts.first { $0.kind.isRun }
    }

    /// Today's run, or the next planned one on a rest day.
    private var featured: Workout? {
        todayRun ?? store.nextRun
    }

    /// Strength and stretching sessions of the day.
    private var extras: [Workout] {
        store.todayWorkouts.filter { !$0.kind.isRun }
    }

    private var upcoming: [Workout] {
        let today = Calendar.plan.startOfDay(for: Date())
        guard let tomorrow = Calendar.plan.date(byAdding: .day, value: 1, to: today) else { return [] }
        let featuredID = featured?.id
        let list = store.allWorkouts.filter { workout in
            workout.date >= tomorrow && workout.status == .planned && workout.id != featuredID
        }
        return Array(list.prefix(2))
    }

    private var subtitle: String {
        var parts: [String] = [Fmt.day(Date(), "EEEE d MMMM")]
        if let plan = store.plan, let number = store.mainCurrentWeekNumber {
            parts.append("Week \(number) of \(plan.weeks.count)")
        }
        return parts.joined(separator: " · ")
    }

    // MARK: Sections

    /// Opens the profile, from the navigation bar.
    private var profileButton: some View {
        Button {
            chrome.showProfile = true
        } label: {
            CoachAvatar(coach: store.coach, size: 32)
                .overlay(Circle().stroke(Theme.hairline, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Profile and settings")
    }

    @ViewBuilder
    private var heroCard: some View {
        if store.plan == nil {
            TodayMessageCard(
                title: "No plan yet",
                message: "Set your goal and your running days in your profile and your plan will be ready in a moment.",
                actionTitle: "Open my profile"
            ) {
                chrome.showProfile = true
            }
        } else if let workout = featured {
            TodaySessionCard(workout: workout, isToday: todayRun != nil, restDay: todayRun == nil && extras.isEmpty)
        } else {
            TodayMessageCard(
                title: "Plan complete",
                message: "You reached the end of your plan. Set a new goal in your profile whenever you are ready.",
                actionTitle: "Set a new goal"
            ) {
                chrome.showProfile = true
            }
        }
    }

    private var coachLine: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                CoachAvatar(coach: store.coach, size: 22)
                Text("Note from \(store.coach.name)")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.ink)
            }
            Text(TodayCoachLine.text(store: store, todayRun: todayRun, next: store.nextRun))
                .font(.system(size: 14))
                .lineSpacing(4)
                .foregroundStyle(Theme.body)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// The coach's short line of the day, built from the real state of the plan.
@MainActor
enum TodayCoachLine {
    static func text(store: AppStore, todayRun: Workout?, next: Workout?) -> String {
        guard store.plan != nil else {
            return "Tell me your goal and the days you can run, and I will build your plan."
        }
        if let run = todayRun {
            switch run.status {
            case .done:
                return doneLine(tone: store.answers.tone)
            case .missed:
                return "No drama about this one. If you did run, tell me and I will count it."
            case .planned:
                return plannedLine(run)
            }
        }
        guard let next else {
            return "You have done everything on the plan. Tell me what you want to aim for next."
        }
        let day = Weekday.long(Calendar.plan.mondayIndex(of: next.date))
        var line = "No run today. Rest is when your body gets stronger. Next up: \(next.title) on \(day)."
        let missedThisWeek = store.currentWeek?.workouts.contains { $0.status == .missed } ?? false
        if missedThisWeek {
            line += " Don't chase the missed session, just pick the plan up where it is."
        }
        return line
    }

    private static func doneLine(tone: CoachTone) -> String {
        switch tone {
        case .kind: return "Nice work today. Eat well, sleep well, and let the session sink in."
        case .direct: return "Session done. Recover properly tonight."
        case .playful: return "Ticked off. The sofa is all yours tonight."
        }
    }

    private static func plannedLine(_ run: Workout) -> String {
        switch run.kind {
        case .easy, .free:
            return "Keep it truly easy today: you should be able to talk the whole way."
        case .long:
            return "Slow and steady today. The distance does the work, not the speed."
        case .intervals, .tempo, .racePace:
            if let pace = run.targetPace {
                return "Start a touch under control, then settle on \(Fmt.pace(pace))/km. The last efforts should look like the first."
            }
            return "Start a touch under control. The last efforts should look like the first."
        case .race:
            return "This is your day. Start calm, hold your pace, and trust your training."
        case .strength, .stretching:
            return run.subtitle
        }
    }
}

#Preview {
    TodayView()
        .environmentObject(AppStore.preview)
        .environmentObject(MainChrome())
}
