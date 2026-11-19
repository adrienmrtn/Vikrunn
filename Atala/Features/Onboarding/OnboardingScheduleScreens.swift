import SwiftUI

/// Screen 17: tick the days you can run. One ticked day, one session.
struct OnboardingDaysScreen: View {
    @EnvironmentObject private var store: AppStore

    private var count: Int { store.answers.runDays.count }

    private var summaryTitle: String {
        switch count {
        case 0: return "No day ticked yet"
        case 1: return "1 day ticked, 1 session a week"
        default: return "\(count) days ticked, \(count) sessions a week"
        }
    }

    private var summaryDetail: String {
        switch count {
        case 0: return "Tick at least one day to continue"
        case 1: return "A gentle start. You can add a day later"
        case 2: return "Enough to build a real habit"
        case 3, 4: return "A good rhythm to progress steadily"
        case 5, 6: return "A full week: I'll keep most runs easy"
        default: return "Every day: I'll make sure you recover"
        }
    }

    private func toggle(_ day: Int) {
        var days = store.answers.runDays
        if days.contains(day) {
            days.removeAll { $0 == day }
        } else {
            days.append(day)
        }
        days.sort()
        store.answers.runDays = days
        if days.count == 1 {
            store.answers.longRunDay = days[0]
        } else if let long = store.answers.longRunDay, !days.contains(long) {
            store.answers.longRunDay = nil
        }
    }

    var body: some View {
        OnboardingScaffold(title: "When can you run?",
                           subtitle: "One ticked day, one session.",
                           canContinue: count > 0) {
            VStack(spacing: 0) {
                ForEach(1...7, id: \.self) { day in
                    if day > 1 {
                        Divider().overlay(Color(hex: 0xEFEFEF))
                    }
                    dayRow(day)
                }
            }
            .padding(.horizontal, 16)
            .card()
            summary
        }
    }

    private func dayRow(_ day: Int) -> some View {
        let ticked = store.answers.runDays.contains(day)
        return Button(action: {
            Haptics.select()
            toggle(day)
        }) {
            HStack(spacing: 12) {
                Text(Weekday.long(day))
                    .font(.system(size: 16, weight: ticked ? .semibold : .regular))
                    .foregroundStyle(ticked ? Theme.ink : Theme.muted)
                Spacer(minLength: 12)
                RoundedRectangle(cornerRadius: 9, style: .continuous)
                    .fill(ticked ? Theme.green : Color.clear)
                    .frame(width: 26, height: 26)
                    .overlay(
                        RoundedRectangle(cornerRadius: 9, style: .continuous)
                            .stroke(ticked ? Theme.green : Color(hex: 0xC4C4C4), lineWidth: 1.5)
                    )
                    .overlay(
                        Image(systemName: "checkmark")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(Color.white)
                            .opacity(ticked ? 1 : 0)
                    )
            }
            .frame(height: 52)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(ticked ? .isSelected : [])
    }

    private var summary: some View {
        HStack(spacing: 12) {
            CoachAvatar(coach: store.coach, size: 36)
            VStack(alignment: .leading, spacing: 2) {
                Text(summaryTitle)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                Text(summaryDetail)
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.green)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

/// Screen 18: the long run day, among the ticked days.
struct OnboardingLongRunScreen: View {
    @EnvironmentObject private var store: AppStore

    private var days: [Int] { store.answers.runDays.sorted() }

    private var canContinue: Bool {
        guard let day = store.answers.longRunDay else { return false }
        return days.contains(day)
    }

    var body: some View {
        OnboardingScaffold(title: "Which day for your long run?",
                           canContinue: canContinue) {
            VStack(spacing: 10) {
                ForEach(days, id: \.self) { day in
                    OnboardingPlainOptionRow(title: Weekday.long(day),
                                             selected: store.answers.longRunDay == day) {
                        store.answers.longRunDay = day
                    }
                }
            }
        }
    }
}

/// Screen 19: constraints, all optional.
struct OnboardingConstraintsScreen: View {
    @EnvironmentObject private var store: AppStore

    private func subtitle(_ constraint: Constraint) -> String {
        switch constraint {
        case .pastInjury: return "Your coach keeps it in mind"
        case .travel: return "Tell your coach, sessions can move"
        case .treadmill: return "You can log those runs by hand"
        case .strength: return "Good news: it protects you from injury"
        case .irregularSleep: return "Easy runs stay truly easy"
        }
    }

    private func toggle(_ constraint: Constraint) {
        var list = store.answers.constraints
        if list.contains(constraint) {
            list.removeAll { $0 == constraint }
        } else {
            list.append(constraint)
        }
        store.answers.constraints = list
    }

    var body: some View {
        OnboardingScaffold(title: "Anything else to know?",
                           subtitle: "Optional.") {
            VStack(spacing: 9) {
                ForEach(Constraint.allCases) { constraint in
                    OptionCard(title: constraint.title,
                               subtitle: subtitle(constraint),
                               selected: store.answers.constraints.contains(constraint),
                               multi: true,
                               action: { toggle(constraint) }) {
                        VignetteImage(name: constraint.vignette)
                    }
                }
            }
        }
    }
}

/// Screen 21: the coach's tone.
struct OnboardingToneScreen: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        OnboardingScaffold(title: "Which tone do you prefer?") {
            VStack(spacing: 10) {
                ForEach(CoachTone.allCases) { tone in
                    toneCard(tone)
                }
            }
            Text("You can change the tone at any time.")
                .font(.system(size: 13))
                .foregroundStyle(Theme.muted)
        }
    }

    private func toneCard(_ tone: CoachTone) -> some View {
        let selected = store.answers.tone == tone
        return Button(action: {
            Haptics.select()
            store.answers.tone = tone
        }) {
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(tone.title)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Theme.ink)
                    Spacer(minLength: 12)
                    SelectionMark(selected: selected)
                }
                Text("\u{201C}\(tone.example)\u{201D}")
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.body)
                    .lineSpacing(3)
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .card(selected: selected)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

#Preview("Days") {
    OnboardingDaysScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}

#Preview("Tone") {
    OnboardingToneScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}
