import SwiftUI

/// Goal, race date and target time (edited on the draft).
struct ProfileGoalSection: View {
    @Binding var draft: OnboardingAnswers

    var body: some View {
        Section {
            Picker("Goal", selection: $draft.goal) {
                if draft.goal == nil {
                    Text("Choose").tag(Optional<GoalType>.none)
                }
                ForEach(GoalType.allCases) { goal in
                    Text(goal.title).tag(Optional(goal))
                }
            }
            if let goal = draft.goal, goal != .regular {
                Toggle("I have a race date", isOn: hasRace)
                if draft.hasRace {
                    DatePicker("Race date", selection: raceDate, in: earliestRace..., displayedComponents: .date)
                }
                NavigationLink {
                    ProfileTargetTimeEditor(seconds: $draft.targetSeconds, goal: goal)
                } label: {
                    HStack {
                        Text("Target time")
                        Spacer()
                        Text(targetText)
                            .foregroundStyle(Theme.muted)
                    }
                }
            }
        } header: {
            Text("Goal")
        } footer: {
            Text("Plans cover up to 8 weeks. If your race is further away, the plan builds your base first.")
        }
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
        return "None"
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
            Picker("Long run day", selection: $draft.longRunDay) {
                Text("Automatic").tag(Optional<Int>.none)
                ForEach(draft.runDays.sorted(), id: \.self) { day in
                    Text(Weekday.long(day)).tag(Optional(day))
                }
            }
        } header: {
            Text("Training days")
        } footer: {
            Text("One ticked day is one run per week.")
        }
    }

    private func dayButton(_ day: Int) -> some View {
        let selected = draft.runDays.contains(day)
        return Button {
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

    var body: some View {
        Form {
            Section {
                Toggle("Aim for a time", isOn: hasTarget)
            } footer: {
                Text("Without a target time, your paces are set from your current level.")
            }
            if let value = seconds {
                Section("Target for your \(goal.title)") {
                    HStack(spacing: 0) {
                        wheel("Hours", range: 0..<4, suffix: "h", selection: hours)
                        wheel("Minutes", range: 0..<60, suffix: "min", selection: minutes)
                        wheel("Seconds", range: 0..<60, suffix: "s", selection: secs)
                    }
                    .frame(height: 140)
                    HStack {
                        Text("Target time")
                        Spacer()
                        Text(Fmt.goalTime(value))
                            .fontWeight(.semibold)
                    }
                }
            }
        }
        .navigationTitle("Target time")
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

    private var defaultSeconds: Int {
        switch goal {
        case .fiveK: return 30 * 60
        case .tenK: return 60 * 60
        case .half: return 2 * 3600
        case .regular: return 30 * 60
        }
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
