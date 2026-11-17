import SwiftUI

/// What the user answered on the demo card.
enum OnboardingPlanChangeChoice: Equatable {
    case applied, kept
}

/// The example shown in the onboarding conversation: one session moved to another day.
/// Built from the days the user ticked, so it looks like their own week. Nothing is saved.
struct OnboardingPlanChangeExample: Equatable {
    /// 1 = Monday ... 7 = Sunday.
    let fromDay: Int
    let toDay: Int
    let title: String

    var fromName: String { Weekday.long(fromDay) }
    var toName: String { Weekday.long(toDay) }
    var userLine: String { "I can't run this \(fromName)" }

    static func make(from answers: OnboardingAnswers) -> OnboardingPlanChangeExample {
        let ticked: [Int] = Array(Set(answers.runDays.filter { (1...7).contains($0) })).sorted()
        guard !ticked.isEmpty else {
            return OnboardingPlanChangeExample(fromDay: 4, toDay: 5, title: "Easy run")
        }
        let longDay: Int = answers.longRunDay ?? ticked[ticked.count - 1]
        let easyDays: [Int] = ticked.filter { $0 != longDay }

        // Thursday when it is an easy day, otherwise the first easy day, otherwise the only day there is.
        var from: Int = ticked[0]
        if easyDays.contains(4) {
            from = 4
        } else if let first = easyDays.first {
            from = first
        }
        let title: String = (from == longDay || ticked.count == 1) ? "Long run" : "Easy run"

        // The nearest free day after it, then before it; with a full week, simply the day after.
        var to: Int = from == 7 ? 6 : from + 1
        var found = false
        if from < 7 {
            for day in (from + 1)...7 where !ticked.contains(day) {
                to = day
                found = true
                break
            }
        }
        if !found, from > 1 {
            for day in stride(from: from - 1, through: 1, by: -1) where !ticked.contains(day) {
                to = day
                break
            }
        }
        return OnboardingPlanChangeExample(fromDay: from, toDay: to, title: title)
    }
}

/// "Plan change" card of the onboarding conversation, in the style of the coach chat's proposal card.
/// A demo: the two buttons only resolve the card, the plan does not exist yet.
struct OnboardingPlanChangeCard: View {
    let example: OnboardingPlanChangeExample
    let choice: OnboardingPlanChangeChoice?
    let onChoose: (OnboardingPlanChangeChoice) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Text("Plan change")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Theme.muted)
                Spacer(minLength: 8)
                statusPill
            }
            .frame(minHeight: 22)

            if choice == .applied {
                appliedRow
                    .transition(.opacity)
            } else {
                comparisonRow
                    .opacity(choice == .kept ? 0.55 : 1)
                    .transition(.opacity)
            }

            if let choice {
                resolvedLabel(choice)
                    .transition(.opacity)
            } else {
                Text("The rest of your week stays as it is")
                    .font(.system(size: 12))
                    .foregroundStyle(Theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
                buttons
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .stroke(choice == .applied ? Theme.green.opacity(0.35) : Theme.hairline, lineWidth: 1)
        )
        .shadow(color: Color(hex: 0x143228).opacity(0.06), radius: 10, x: 0, y: 6)
    }

    // MARK: Status

    @ViewBuilder
    private var statusPill: some View {
        if let choice {
            switch choice {
            case .applied:
                pill("Applied", symbol: "checkmark", tint: Theme.green, background: Theme.greenTint)
                    .transition(.scale(scale: 0.6).combined(with: .opacity))
            case .kept:
                pill("Plan kept", symbol: "minus", tint: Theme.muted, background: Theme.surface)
                    .transition(.scale(scale: 0.6).combined(with: .opacity))
            }
        }
    }

    private func pill(_ title: String, symbol: String, tint: Color, background: Color) -> some View {
        HStack(spacing: 4) {
            Image(systemName: symbol)
                .font(.system(size: 9, weight: .bold))
            Text(title)
                .font(.system(size: 12, weight: .semibold))
        }
        .foregroundStyle(tint)
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(background, in: Capsule())
        .accessibilityElement(children: .combine)
    }

    private func resolvedLabel(_ choice: OnboardingPlanChangeChoice) -> some View {
        let applied = choice == .applied
        return HStack(alignment: .firstTextBaseline, spacing: 6) {
            Image(systemName: applied ? "checkmark.circle.fill" : "minus.circle")
                .font(.system(size: 14, weight: .semibold))
            Text(applied ? "Applied. The rest of your week did not move." : "Plan kept. Nothing was changed.")
                .font(.system(size: 13, weight: .semibold))
                .fixedSize(horizontal: false, vertical: true)
        }
        .foregroundStyle(applied ? Theme.green : Theme.muted)
        .frame(minHeight: 28)
    }

    // MARK: Rows

    private var comparisonRow: some View {
        HStack(spacing: 10) {
            box(day: example.fromName, title: example.title, dot: Theme.greenSoft, background: Theme.bubble)
            Image(systemName: "arrow.right")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Theme.ink)
                .accessibilityLabel("becomes")
            box(day: example.toName, title: example.title, dot: Theme.orange, background: Theme.orangeTint)
        }
    }

    private func box(day: String, title: String, dot: Color, background: Color) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: 5) {
                Circle()
                    .fill(dot)
                    .frame(width: 7, height: 7)
                Text(day)
                    .font(.system(size: 11))
                    .foregroundStyle(Color(hex: 0x5A5A5A))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            Text(title)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Theme.ink)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(background, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .accessibilityElement(children: .combine)
    }

    /// The session on its new day, with what changed underneath.
    private var appliedRow: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(example.toName)
                .font(.system(size: 11))
                .foregroundStyle(Color(hex: 0x5A5A5A))
                .lineLimit(1)
            Text(example.title)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Theme.ink)
            Text("Moved from \(example.fromName)")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Theme.green)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.greenTint, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .accessibilityElement(children: .combine)
    }

    // MARK: Actions

    private var buttons: some View {
        HStack(spacing: 8) {
            Button(action: { onChoose(.applied) }) {
                Text("Apply")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Color.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 44)
                    .background(Theme.ink, in: Capsule())
                    .contentShape(Capsule())
            }
            .buttonStyle(.plain)
            Button(action: { onChoose(.kept) }) {
                Text("Keep my plan")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                    .frame(maxWidth: .infinity)
                    .frame(height: 44)
                    .background(Color.white, in: Capsule())
                    .overlay(Capsule().stroke(Theme.ink, lineWidth: 1))
                    .contentShape(Capsule())
            }
            .buttonStyle(.plain)
        }
    }
}

#Preview {
    VStack(spacing: 16) {
        OnboardingPlanChangeCard(example: OnboardingPlanChangeExample(fromDay: 4, toDay: 5, title: "Easy run"),
                                 choice: nil, onChoose: { _ in })
        OnboardingPlanChangeCard(example: OnboardingPlanChangeExample(fromDay: 4, toDay: 5, title: "Easy run"),
                                 choice: .applied, onChoose: { _ in })
    }
    .padding()
}
