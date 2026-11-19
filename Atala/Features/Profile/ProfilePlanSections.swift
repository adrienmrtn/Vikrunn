import SwiftUI

/// Goal, race date and target time (edited on the draft).
struct ProfileGoalSection: View {
    @Binding var draft: OnboardingAnswers

    var body: some View {
        Section {
            Picker(L("Goal"), selection: goalSelection) {
                if draft.goal == nil {
                    Text(L("Choose")).tag(Optional<GoalType>.none)
                }
                ForEach(GoalType.allCases) { goal in
                    Text(goal.title).tag(Optional(goal))
                }
            }
            if let goal = draft.goal, !goal.isOpenEnded {
                Toggle(L("I have a race date"), isOn: hasRace)
                if draft.hasRace {
                    DatePicker(L("Race date"), selection: raceDate, in: earliestRace..., displayedComponents: .date)
                }
                NavigationLink {
                    ProfileTargetTimeEditor(seconds: $draft.targetSeconds, goal: goal)
                } label: {
                    HStack {
                        Text(L("Target time"))
                        Spacer()
                        Text(targetText)
                            .foregroundStyle(Theme.muted)
                    }
                }
            }
        } header: {
            Text(L("Goal"))
        } footer: {
            Text(L("Plans cover up to %@ weeks. If your race is further away, the plan builds your base first.", PlanEngine.maxWeeks))
        }
    }

    /// A target time belongs to one distance: changing the goal clears it instead of carrying
    /// a 10K time over to a half marathon.
    private var goalSelection: Binding<GoalType?> {
        Binding(
            get: { draft.goal },
            set: { newGoal in
                guard newGoal != draft.goal else { return }
                draft.goal = newGoal
                draft.targetSeconds = nil
            }
        )
    }

    private var earliestRace: Date {
        let today = Calendar.plan.startOfDay(for: Date())
        return Calendar.plan.date(byAdding: .day, value: 1, to: today) ?? today
    }

    private var defaultRace: Date {
        Calendar.plan.date(byAdding: .day, value: 42, to: Calendar.plan.startOfDay(for: Date())) ?? Date()
    }

    private var hasRace: Binding<Bool> {
        Binding(
            get: { draft.hasRace },
            set: { isOn in
                draft.hasRace = isOn
                if isOn && draft.raceDate == nil { draft.raceDate = defaultRace }
            }
        )
    }

    private var raceDate: Binding<Date> {
        Binding(
            get: { max(draft.raceDate ?? defaultRace, earliestRace) },
            set: { draft.raceDate = $0 }
        )
    }

    private var targetText: String {
        if let seconds = draft.targetSeconds { return Fmt.goalTime(seconds) }
        return L("None")
    }
}

/// Running days and long-run day (edited on the draft).
struct ProfileDaysSection: View {
    @Binding var draft: OnboardingAnswers

    var body: some View {
        Section {
            HStack(spacing: 6) {
                ForEach(1...7, id: \.self) { day in
                    dayButton(day)
                }
            }
            .padding(.vertical, 4)
            Picker(L("Long run day"), selection: $draft.longRunDay) {
                Text(L("Automatic")).tag(Optional<Int>.none)
                ForEach(draft.runDays.sorted(), id: \.self) { day in
                    Text(Weekday.long(day)).tag(Optional(day))
                }
            }
        } header: {
            Text(L("Training days"))
        } footer: {
            Text(L("One ticked day is one run per week."))
        }
    }

    private func dayButton(_ day: Int) -> some View {
        let selected = draft.runDays.contains(day)
        return Button {
            Haptics.select()
            toggle(day)
        } label: {
            Text(Weekday.short(day))
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(selected ? Color.white : Theme.ink)
                .frame(maxWidth: .infinity)
                .frame(height: 44)
                .background(selected ? Theme.green : Theme.surface,
                            in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Weekday.long(day))
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private func toggle(_ day: Int) {
        if draft.runDays.contains(day) {
            draft.runDays.removeAll { $0 == day }
            if draft.longRunDay == day { draft.longRunDay = nil }
        } else {
            draft.runDays.append(day)
            draft.runDays.sort()
        }
    }
}

/// Target time editor: hours, minutes and seconds, or no target at all.
struct ProfileTargetTimeEditor: View {
    @Binding var seconds: Int?
    let goal: GoalType

    @EnvironmentObject private var store: AppStore

    /// What the runner is worth today on this distance, from the saved answers.
    private var todaySeconds: Int? {
        PlanEngine.predictedSeconds(vma: PlanEngine.estimateVMA(store.answers), goal: goal)
    }

    /// Gentle note under the wheels: how the target compares with today's level.
    private func levelNote(for target: Int) -> String {
        guard let today = todaySeconds, let km = goal.distanceKm else { return "" }
        let bounds = OnboardingTimeRules.range(km: km)
        if target < bounds.lowerBound || target > bounds.upperBound {
            return L("Outside what the plan can work with: it will be brought back between %@ and %@.", Fmt.goalTime(bounds.lowerBound), Fmt.goalTime(bounds.upperBound))
        }
        let reachable = Int(Double(today) * 0.95)
        if target < reachable {
            return L("Ambitious from your level today (around %@). It stays your goal, and the plan trains you towards %@ first.", Fmt.goalTime(today), Fmt.goalTime(reachable))
        }
        if target <= today {
            return L("A real step up from your level today (around %@), and within reach.", Fmt.goalTime(today))
        }
        return L("Comfortable for your level today (around %@).", Fmt.goalTime(today))
    }

    var body: some View {
        Form {
            Section {
                Toggle(L("Aim for a time"), isOn: hasTarget)
            } footer: {
                Text(L("Without a target time, your paces are set from your current level."))
            }
            if let value = seconds {
                Section {
                    HStack(spacing: 0) {
                        wheel(L("Hours"), range: 0..<4, suffix: L("h"), selection: hours)
                        wheel(L("Minutes"), range: 0..<60, suffix: L("min"), selection: minutes)
                        wheel(L("Seconds"), range: 0..<60, suffix: L("s"), selection: secs)
                    }
                    .frame(height: 140)
                    HStack {
                        Text(L("Target time"))
                        Spacer()
                        Text(Fmt.goalTime(value))
                            .fontWeight(.semibold)
                    }
                } header: {
                    Text(L("Target for your %@", goal.title))
                } footer: {
                    Text(levelNote(for: value))
                }
            }
        }
        .navigationTitle(L("Target time"))
        .navigationBarTitleDisplayMode(.inline)
    }

    private func wheel(_ title: String, range: Range<Int>, suffix: String, selection: Binding<Int>) -> some View {
        Picker(title, selection: selection) {
            ForEach(range, id: \.self) { value in
                Text("\(value) \(suffix)").tag(value)
            }
        }
        .pickerStyle(.wheel)
        .frame(maxWidth: .infinity)
        .clipped()
    }

    /// Starts from today's level, rounded down to the minute, like the onboarding does.
    private var defaultSeconds: Int {
        guard let km = goal.distanceKm else { return 30 * 60 }
        let base = todaySeconds ?? OnboardingTimeRules.defaultSeconds(km: km)
        return OnboardingTimeRules.clamp(base / 60 * 60, km: km)
    }

    private var hasTarget: Binding<Bool> {
        Binding(
            get: { seconds != nil },
            set: { isOn in seconds = isOn ? (seconds ?? defaultSeconds) : nil }
        )
    }

    private var hours: Binding<Int> {
        Binding(
            get: { (seconds ?? 0) / 3600 },
            set: { newValue in
                let current = seconds ?? 0
                seconds = max(60, newValue * 3600 + current % 3600)
            }
        )
    }

    private var minutes: Binding<Int> {
        Binding(
            get: { ((seconds ?? 0) % 3600) / 60 },
            set: { newValue in
                let current = seconds ?? 0
                seconds = max(60, (current / 3600) * 3600 + newValue * 60 + current % 60)
            }
        )
    }

    private var secs: Binding<Int> {
        Binding(
            get: { (seconds ?? 0) % 60 },
            set: { newValue in
                let current = seconds ?? 0
                seconds = max(60, current - current % 60 + newValue)
            }
        )
    }
}
