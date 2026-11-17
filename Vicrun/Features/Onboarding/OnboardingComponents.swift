import SwiftUI

/// Wording built from the answers and reused on several screens.
enum OnboardingCopy {
    static func goalNoun(_ goal: GoalType?) -> String {
        switch goal ?? .regular {
        case .fiveK: return "5K"
        case .tenK: return "10K"
        case .half: return "half marathon"
        case .regular: return "running routine"
        }
    }

    /// "Half marathon in 1:45:00", "10K" or "Run regularly".
    static func goalHeadline(_ answers: OnboardingAnswers) -> String {
        let goal = answers.goal ?? .regular
        if goal != .regular, let target = answers.targetSeconds {
            return "\(goal.title) in \(Fmt.goalTime(target))"
        }
        return goal.title
    }

    static func sessionsPerWeek(_ answers: OnboardingAnswers) -> Int {
        answers.runDays.isEmpty ? 3 : answers.runDays.count
    }

    static func sessions(_ count: Int) -> String {
        count == 1 ? "1 session a week" : "\(count) sessions a week"
    }

    static func weeks(_ count: Int) -> String {
        count == 1 ? "1 week" : "\(count) weeks"
    }

    static func distanceName(_ km: Double) -> String {
        if km < 7.5 { return "5K" }
        if km < 15 { return "10K" }
        return "half marathon"
    }

    static func pacePerKm(seconds: Int, km: Double) -> String {
        guard km > 0 else { return "--:--" }
        return Fmt.pace(Int((Double(seconds) / km).rounded()))
    }
}

/// Standard questionnaire screen: back + progress bar, title, scrolling content, bottom button.
struct OnboardingScaffold<Content: View>: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var nav: OnboardingNavigator
    @Environment(\.onboardingStep) private var step

    private let title: String?
    private let subtitle: String?
    private let buttonTitle: String
    private let canContinue: Bool
    private let tinted: Bool
    private let secondaryTitle: String?
    private let onSecondary: (() -> Void)?
    private let onContinue: (() -> Void)?
    private let content: Content

    init(title: String? = nil,
         subtitle: String? = nil,
         buttonTitle: String = "Continue",
         canContinue: Bool = true,
         tinted: Bool = false,
         secondaryTitle: String? = nil,
         onSecondary: (() -> Void)? = nil,
         onContinue: (() -> Void)? = nil,
         @ViewBuilder content: () -> Content) {
        self.title = title
        self.subtitle = subtitle
        self.buttonTitle = buttonTitle
        self.canContinue = canContinue
        self.tinted = tinted
        self.secondaryTitle = secondaryTitle
        self.onSecondary = onSecondary
        self.onContinue = onContinue
        self.content = content()
    }

    var body: some View {
        VStack(spacing: 0) {
            ProgressHeader(progress: nav.progress(for: step, store: store), onBack: backAction)
                .padding(.horizontal, Theme.screenPadding)
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
            bottom
        }
        .background((tinted ? Theme.surface : Color.white).ignoresSafeArea())
    }

    private var backAction: (() -> Void)? {
        guard step.allowsBack else { return nil }
        return { nav.back(store) }
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
        PrimaryButton(title: buttonTitle, enabled: canContinue, action: primaryTapped)
        if let secondaryTitle, let onSecondary {
            Button(secondaryTitle, action: onSecondary)
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
        Button(action: action) {
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

struct OnboardingTapButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.94 : 1)
    }
}

/// Full-screen moment with the coach: the whole screen is one button that moves on.
struct OnboardingTapScreen<Backdrop: View, Top: View>: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var nav: OnboardingNavigator
    @Environment(\.onboardingStep) private var step

    private let lightContent: Bool
    private let backdrop: Backdrop
    private let top: Top

    /// `lightContent`: white chevron and translucent pill, for dark backgrounds.
    init(lightContent: Bool = false,
         @ViewBuilder backdrop: () -> Backdrop,
         @ViewBuilder top: () -> Top) {
        self.lightContent = lightContent
        self.backdrop = backdrop()
        self.top = top()
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            Button(action: { nav.next(store) }) {
                VStack(spacing: 0) {
                    top
                        .padding(.horizontal, 24)
                        .padding(.top, 56)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Spacer(minLength: 12)
                    figure
                        .layoutPriority(1)
                    pill
                        .padding(.top, 14)
                        .padding(.bottom, 12)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background { backdrop }
                .contentShape(Rectangle())
            }
            .buttonStyle(OnboardingTapButtonStyle())
            .accessibilityHint("Tap to continue")
            backButton
        }
    }

    private var figure: some View {
        CoachAnimationView(coach: store.coach, animated: true)
            .frame(maxWidth: 320, maxHeight: 500)
            .background(alignment: .bottom) {
                Ellipse()
                    .fill(Color.black.opacity(0.3))
                    .frame(width: 180, height: 20)
                    .blur(radius: 9)
                    .offset(y: 4)
            }
    }

    private var pill: some View {
        Text("Tap to continue")
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(lightContent ? Color.white : Theme.ink)
            .padding(.horizontal, 16)
            .padding(.vertical, 9)
            .background(lightContent ? Color.white.opacity(0.16) : Color.white.opacity(0.9), in: Capsule())
    }

    @ViewBuilder private var backButton: some View {
        if step.allowsBack {
            Button(action: { nav.back(store) }) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(lightContent ? Color.white : Theme.ink)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel("Back")
            .padding(.leading, 8)
        }
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
            roundButton(symbol: "minus", label: "Less", delta: -stepSeconds)
            Button(action: onTapTime) {
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
            .accessibilityHint("Opens the time picker")
            roundButton(symbol: "plus", label: "More", delta: stepSeconds)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 26)
        .card(radius: 24)
    }

    private func roundButton(symbol: String, label: String, delta: Int) -> some View {
        Button(action: { onChange(seconds + delta) }) {
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
                    Picker("Hours", selection: $hours) {
                        ForEach(0..<4, id: \.self) { value in
                            Text("\(value) h").tag(value)
                        }
                    }
                    .pickerStyle(.wheel)
                }
                Picker("Minutes", selection: $minutes) {
                    ForEach(0..<(showsHours ? 60 : 180), id: \.self) { value in
                        Text("\(value) min").tag(value)
                    }
                }
                .pickerStyle(.wheel)
                Picker("Seconds", selection: $secs) {
                    ForEach(0..<60, id: \.self) { value in
                        Text("\(value) s").tag(value)
                    }
                }
                .pickerStyle(.wheel)
            }
            .frame(height: 170)
            PrimaryButton(title: "Done") {
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
