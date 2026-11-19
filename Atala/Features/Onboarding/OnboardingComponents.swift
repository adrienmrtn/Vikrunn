import SwiftUI

/// Wording built from the answers and reused on several screens.
enum OnboardingCopy {
    static func goalNoun(_ goal: GoalType?) -> String {
        switch goal ?? .regular {
        case .fiveK: return "5K"
        case .tenK: return "10K"
        case .half: return L("half marathon")
        case .regular: return L("running routine")
        case .shape: return L("fitness routine")
        }
    }

    /// "Half marathon in 1:45:00", "10K" or "Run regularly".
    static func goalHeadline(_ answers: OnboardingAnswers) -> String {
        let goal = answers.goal ?? .regular
        if !goal.isOpenEnded, let target = answers.targetSeconds {
            return L("%@ in %@", goal.title, Fmt.goalTime(target))
        }
        return goal.title
    }

    static func sessionsPerWeek(_ answers: OnboardingAnswers) -> Int {
        answers.runDays.isEmpty ? 3 : answers.runDays.count
    }

    static func sessions(_ count: Int) -> String {
        count == 1 ? L("1 session a week") : L("%@ sessions a week", count)
    }

    static func weeks(_ count: Int) -> String {
        count == 1 ? L("1 week") : L("%@ weeks", count)
    }

    static func distanceName(_ km: Double) -> String {
        if km < 7.5 { return "5K" }
        if km < 15 { return "10K" }
        return L("half marathon")
    }

    static func pacePerKm(seconds: Int, km: Double) -> String {
        guard km > 0 else { return "--:--" }
        return Fmt.pace(Int((Double(seconds) / km).rounded()))
    }
}

/// Standard questionnaire screen: room for the flow's fixed header (back + progress bar), title,
/// scrolling content, bottom button.
struct OnboardingScaffold<Content: View>: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var nav: OnboardingNavigator
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private let title: String?
    private let subtitle: String?
    private let buttonTitle: String
    private let buttonHaptic: Haptics.Kind?
    private let canContinue: Bool
    private let tinted: Bool
    private let buttonsVisible: Bool
    private let secondaryTitle: String?
    private let onSecondary: (() -> Void)?
    private let onContinue: (() -> Void)?
    private let content: Content

    init(title: String? = nil,
         subtitle: String? = nil,
         buttonTitle: String = L("Continue"),
         buttonHaptic: Haptics.Kind? = .confirm,
         canContinue: Bool = true,
         tinted: Bool = false,
         buttonsVisible: Bool = true,
         secondaryTitle: String? = nil,
         onSecondary: (() -> Void)? = nil,
         onContinue: (() -> Void)? = nil,
         @ViewBuilder content: () -> Content) {
        self.title = title
        self.subtitle = subtitle
        self.buttonTitle = buttonTitle
        self.buttonHaptic = buttonHaptic
        self.canContinue = canContinue
        self.tinted = tinted
        self.buttonsVisible = buttonsVisible
        self.secondaryTitle = secondaryTitle
        self.onSecondary = onSecondary
        self.onContinue = onContinue
        self.content = content()
    }

    var body: some View {
        VStack(spacing: 0) {
            OnboardingHeaderSpace()
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    header
                    content
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 10)
                .padding(.bottom, 24)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .scrollBounceBehavior(.basedOnSize)
            .scrollIndicators(.hidden)
            // Screens that stage their content only show the buttons once everything is there.
            bottom
                .opacity(buttonsVisible ? 1 : 0)
                .allowsHitTesting(buttonsVisible)
                .accessibilityHidden(!buttonsVisible)
                .animation(.easeOut(duration: 0.35), value: buttonsVisible)
        }
        .background((tinted ? Theme.surface : Color.white).ignoresSafeArea())
        .onChange(of: buttonsVisible) { _, visible in
            // A staged reveal has ended: a soft tick as the button arrives. Not with Reduce Motion,
            // where everything is there as soon as the screen opens.
            if visible, !reduceMotion { Haptics.soft() }
        }
    }

    @ViewBuilder private var header: some View {
        if title != nil || subtitle != nil {
            VStack(alignment: .leading, spacing: 8) {
                if let title {
                    Text(title)
                        .font(Theme.title(28))
                        .foregroundStyle(Theme.ink)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityAddTraits(.isHeader)
                }
                if let subtitle {
                    Text(subtitle)
                        .font(.system(size: 15))
                        .foregroundStyle(Theme.muted)
                        .lineSpacing(3)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private func primaryTapped() {
        if let onContinue {
            onContinue()
        } else {
            nav.next(store)
        }
    }

    @ViewBuilder private var buttons: some View {
        PrimaryButton(title: buttonTitle, enabled: canContinue, haptic: buttonHaptic, action: primaryTapped)
        if let secondaryTitle, let onSecondary {
            Button(secondaryTitle, action: {
                Haptics.tap()
                onSecondary()
            })
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Theme.ink)
                .frame(maxWidth: .infinity)
                .frame(height: 44)
        }
    }

    @ViewBuilder private var bottom: some View {
        if tinted {
            VStack(spacing: 10) { buttons }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 12)
                .padding(.bottom, 8)
        } else {
            BottomBar { buttons }
        }
    }
}

/// Simple 58 pt row with a radio mark (gender, long run day).
struct OnboardingPlainOptionRow: View {
    let title: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: {
            Haptics.select()
            action()
        }) {
            HStack {
                Text(title)
                    .font(.system(size: 16, weight: selected ? .semibold : .medium))
                    .foregroundStyle(Theme.ink)
                Spacer(minLength: 12)
                SelectionMark(selected: selected)
            }
            .padding(.horizontal, 16)
            .frame(height: 58)
            .background(Color.white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(selected ? Theme.ink : Theme.controlBorder, lineWidth: selected ? 1.5 : 1)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// Big time with minus and plus buttons (hold to repeat). Tapping the time opens the wheels.
struct OnboardingTimeCard: View {
    let seconds: Int
    let caption: String
    let stepSeconds: Int
    var dimmed: Bool = false
    let onChange: (Int) -> Void
    let onTapTime: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            roundButton(symbol: "minus", label: L("Less"), delta: -stepSeconds)
            Button(action: {
                Haptics.tap()
                onTapTime()
            }) {
                VStack(spacing: 2) {
                    Text(Fmt.goalTime(seconds))
                        .font(.system(size: 48, weight: .bold))
                        .monospacedDigit()
                        .minimumScaleFactor(0.6)
                        .lineLimit(1)
                        .foregroundStyle(dimmed ? Theme.faint : Theme.ink)
                    Text(caption)
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.muted)
                }
                .frame(maxWidth: .infinity)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityHint(L("Opens the time picker"))
            roundButton(symbol: "plus", label: L("More"), delta: stepSeconds)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 26)
        .card(radius: 24)
    }

    private func roundButton(symbol: String, label: String, delta: Int) -> some View {
        Button(action: {
            Haptics.select()
            onChange(seconds + delta)
        }) {
            Image(systemName: symbol)
                .font(.system(size: 20, weight: .medium))
                .foregroundStyle(Theme.ink)
                .frame(width: 52, height: 52)
                .background(Color.white, in: Circle())
                .overlay(Circle().stroke(Theme.controlBorder, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .buttonRepeatBehavior(.enabled)
        .accessibilityLabel(label)
    }
}

/// Hours, minutes and seconds wheels shown in a small sheet.
struct OnboardingTimeWheelSheet: View {
    let title: String
    let showsHours: Bool
    let initialSeconds: Int
    let onDone: (Int) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var hours = 0
    @State private var minutes = 0
    @State private var secs = 0

    var body: some View {
        VStack(spacing: 8) {
            Text(title)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Theme.ink)
                .padding(.top, 22)
            HStack(spacing: 0) {
                if showsHours {
                    Picker(L("Hours"), selection: $hours) {
                        ForEach(0..<4, id: \.self) { value in
                            Text("\(value) h").tag(value)
                        }
                    }
                    .pickerStyle(.wheel)
                }
                Picker(L("Minutes"), selection: $minutes) {
                    ForEach(0..<(showsHours ? 60 : 180), id: \.self) { value in
                        Text(L("%@ min", value)).tag(value)
                    }
                }
                .pickerStyle(.wheel)
                Picker(L("Seconds"), selection: $secs) {
                    ForEach(0..<60, id: \.self) { value in
                        Text("\(value) s").tag(value)
                    }
                }
                .pickerStyle(.wheel)
            }
            .frame(height: 170)
            PrimaryButton(title: L("Done")) {
                onDone(hours * 3600 + minutes * 60 + secs)
                dismiss()
            }
            .padding(.horizontal, Theme.screenPadding)
            .padding(.bottom, 12)
        }
        .onAppear {
            let total = max(0, initialSeconds)
            if showsHours {
                hours = min(3, total / 3600)
                minutes = (total % 3600) / 60
            } else {
                hours = 0
                minutes = min(179, total / 60)
            }
            secs = total % 60
        }
        .presentationDetents([.height(320)])
        .presentationDragIndicator(.visible)
    }
}
