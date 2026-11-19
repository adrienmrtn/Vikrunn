import SwiftUI

/// Screen 12: the goal.
struct OnboardingGoalScreen: View {
    @EnvironmentObject private var store: AppStore

    private func select(_ goal: GoalType) {
        store.answers.goal = goal
        if goal.isOpenEnded {
            // The race and target screens are skipped for these goals.
            store.answers.hasRace = false
            store.answers.raceDate = nil
            store.answers.targetSeconds = nil
        } else if let target = store.answers.targetSeconds, let km = goal.distanceKm {
            store.answers.targetSeconds = OnboardingTimeRules.clamp(target, km: km)
        }
    }

    var body: some View {
        OnboardingScaffold(title: L("What's your goal?"),
                           canContinue: store.answers.goal != nil) {
            VStack(spacing: 9) {
                ForEach(GoalType.allCases) { goal in
                    OptionCard(title: goal.title,
                               subtitle: goal.subtitle,
                               selected: store.answers.goal == goal,
                               action: { select(goal) }) {
                        VignetteImage(name: goal.vignette)
                    }
                }
            }
        }
    }
}

/// Screen 13: a race in sight? A plan covers eight weeks at most.
struct OnboardingRaceScreen: View {
    @EnvironmentObject private var store: AppStore

    /// nil until the user answers.
    @State private var hasRace: Bool?
    @State private var date = Date()

    private var range: ClosedRange<Date> {
        let calendar = Calendar.plan
        let today = calendar.startOfDay(for: Date())
        let first = calendar.date(byAdding: .day, value: 1, to: today) ?? today
        let monday = calendar.mondayOfWeek(containing: today)
        let last = calendar.date(byAdding: .day, value: PlanEngine.maxWeeks * 7 - 1, to: monday) ?? first
        return first...max(first, last)
    }

    private var weeksAway: Int {
        let calendar = Calendar.plan
        let today = calendar.startOfDay(for: Date())
        let days = calendar.dateComponents([.day], from: today, to: calendar.startOfDay(for: date)).day ?? 0
        return max(1, (days + 6) / 7)
    }

    private var weeksNote: String {
        let noun = OnboardingCopy.goalNoun(store.answers.goal)
        if weeksAway <= 2 {
            return L("That's in %@: very soon. I'll keep you fresh for your %@.", OnboardingCopy.weeks(weeksAway), noun)
        }
        return L("That's in %@: I'll build your whole plan towards that day.", OnboardingCopy.weeks(weeksAway))
    }

    var body: some View {
        OnboardingScaffold(title: L("A race in sight?"),
                           canContinue: hasRace != nil) {
            VStack(spacing: 9) {
                OptionCard(title: L("Yes, I have a date"),
                           subtitle: L("I set the whole plan on race day"),
                           selected: hasRace == true,
                           action: { choose(true) }) {
                    VignetteImage(name: "vignette_race_date")
                }
                OptionCard(title: L("Not yet"),
                           subtitle: L("We start with an 8-week plan"),
                           selected: hasRace == false,
                           action: { choose(false) }) {
                    VignetteImage(name: "vignette_race_none")
                }
            }
            if hasRace == true {
                dateField
            }
        }
        .onAppear(perform: load)
    }

    private var dateField: some View {
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 8) {
                Text(L("Race date"))
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                HStack {
                    Text(Fmt.day(date, "EEEE d MMMM yyyy"))
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Theme.ink)
                    Spacer(minLength: 8)
                    DatePicker(L("Race date"), selection: dateBinding, in: range, displayedComponents: .date)
                        .labelsHidden()
                        .datePickerStyle(.compact)
                        .environment(\.locale, AppLanguage.locale)
                        .environment(\.calendar, Calendar.plan)
                }
                .padding(.horizontal, 16)
                .frame(height: 58)
                .background(Color.white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .stroke(Theme.controlBorder, lineWidth: 1)
                )
            }
            Text(weeksNote)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Theme.green)
                .fixedSize(horizontal: false, vertical: true)
            Text(L("A plan covers %@ weeks at most. If your race is further away, choose \"Not yet\": we start building now.", PlanEngine.maxWeeks))
                .font(.system(size: 13))
                .foregroundStyle(Theme.muted)
                .lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)
        }
        .transition(.opacity.combined(with: .move(edge: .top)))
    }

    private var dateBinding: Binding<Date> {
        Binding(
            get: { date },
            set: { newValue in
                date = newValue
                store.answers.raceDate = Calendar.plan.startOfDay(for: newValue)
            }
        )
    }

    private func clamped(_ value: Date) -> Date {
        min(max(value, range.lowerBound), range.upperBound)
    }

    private func load() {
        let calendar = Calendar.plan
        let fallback = calendar.date(byAdding: .day, value: 42, to: calendar.startOfDay(for: Date())) ?? Date()
        date = clamped(store.answers.raceDate ?? fallback)
        if store.answers.hasRace {
            hasRace = true
            // A date saved on an earlier day can be out of range by now: store the corrected one.
            let day = Calendar.plan.startOfDay(for: date)
            if store.answers.raceDate != day { store.answers.raceDate = day }
        }
    }

    private func choose(_ value: Bool) {
        withAnimation(.easeInOut(duration: 0.25)) { hasRace = value }
        store.answers.hasRace = value
        store.answers.raceDate = value ? Calendar.plan.startOfDay(for: date) : nil
    }
}

/// Screen 15: why this goal.
struct OnboardingWhyScreen: View {
    @EnvironmentObject private var store: AppStore

    private var title: String {
        switch store.answers.goal ?? .regular {
        case .fiveK: return L("Why this 5K?")
        case .tenK: return L("Why this 10K?")
        case .half: return L("Why this half marathon?")
        case .regular: return L("Why do you want to run regularly?")
        case .shape: return L("Why do you want to get in shape?")
        }
    }

    var body: some View {
        OnboardingScaffold(title: title,
                           canContinue: store.answers.motivation != nil) {
            VStack(spacing: 9) {
                ForEach(Motivation.allCases) { motivation in
                    OptionCard(title: motivation.title,
                               selected: store.answers.motivation == motivation,
                               action: { store.answers.motivation = motivation }) {
                        VignetteImage(name: motivation.vignette)
                    }
                }
            }
        }
    }
}

#Preview("Goal") {
    OnboardingGoalScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}

#Preview("Race") {
    OnboardingRaceScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}
