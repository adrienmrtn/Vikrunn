import SwiftUI

/// The three levels the coach speaks to, derived from "How long have you been running?".
enum OnboardingRunnerLevel: Equatable {
    case beginner, intermediate, advanced

    init(_ experience: Experience?) {
        switch experience ?? .starting {
        case .starting, .underSixMonths: self = .beginner
        case .sixMonthsToTwoYears: self = .intermediate
        case .overTwoYears: self = .advanced
        }
    }
}

/// The injury figure shown to beginners and its source, kept together so both can be corrected
/// in one place.
enum OnboardingInjuryFigure {
    static let text = "New runners get injured more than twice as often as regular runners: "
        + "about 18 injuries per 1,000 hours of running, against 8."
    static let source = "Videbæk et al., Sports Medicine, 2015"
    static let unit = "Injuries per 1,000 hours of running"
    static let newRunnersRate = 18
    static let newRunnersLabel = "New runners"
    static let regularRunnersRate = 8
    static let regularRunnersLabel = "Regular runners"
}

/// Wording of the level screens.
enum OnboardingLevelCopy {
    static let beginnerHeadline = "Starting is the hardest part."
    static let beginnerPraise = "You just did it."
    static let beginnerPromise = "In a few months, you'll be fit, with better cardio than ever."

    static let riskTitle = "One thing to know before you start"
    static let riskSolution = "Atala gives you the exact warm-ups, stretches and runs for your level, "
        + "so you progress without getting hurt."
    static let riskKey = "To start running, the most important thing isn't fancy shoes or the best clothes. "
        + "It's a coach made for you."
    static let riskKeyHighlight = "It's a coach made for you."

    static let intermediateHeadline = "Tired of the same loop?"
    static let intermediateBody = "Your plan changes every week: easy runs, intervals, long runs, strength."
    static let intermediateClosing = "And I tell you why each one matters."

    static let advancedHeadline = "More training isn't better training."
    static let advancedBody = "I manage your load week by week, so you progress without overtraining."
    static let advancedChartCaption = "Your weekly load builds, then eases off"
}

/// The coach's avatar and name above what they say.
private struct OnboardingLevelCoachHeader: View {
    let coach: Coach

    var body: some View {
        HStack(spacing: 12) {
            CoachAvatar(coach: coach, size: 56)
            VStack(alignment: .leading, spacing: 2) {
                Text(coach.name)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                Text("Your coach")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.green)
            }
        }
        .accessibilityElement(children: .combine)
    }
}

/// Beginners, screen A: congratulations first.
struct OnboardingLevelIntroScreen: View {
    @EnvironmentObject private var store: AppStore
    /// 0 nothing said, 1 headline, 2 praise, 3 everything.
    @State private var spoken = 0

    var body: some View {
        OnboardingScaffold(buttonsVisible: spoken >= 3) {
            OnboardingLevelCoachHeader(coach: store.coach)
            VStack(alignment: .leading, spacing: 10) {
                OnboardingSpokenText(text: OnboardingLevelCopy.beginnerHeadline,
                                     size: 34, weight: .bold, color: Theme.ink, lineSpacing: 2,
                                     startDelay: 0.5,
                                     onFinished: { spoken = max(spoken, 1) })
                OnboardingSpokenText(text: OnboardingLevelCopy.beginnerPraise,
                                     size: 34, weight: .bold, color: Theme.green, lineSpacing: 2,
                                     isActive: spoken >= 1,
                                     onFinished: { spoken = max(spoken, 2) })
            }
            .padding(.top, 8)
            OnboardingSpokenText(text: OnboardingLevelCopy.beginnerPromise,
                                 size: 20, weight: .regular, color: Theme.body, lineSpacing: 6,
                                 isActive: spoken >= 2,
                                 onFinished: { spoken = max(spoken, 3) })
        }
    }
}

/// Beginners, screen B: the injury figure in one card, then the answer straight away.
struct OnboardingLevelRiskScreen: View {
    @EnvironmentObject private var store: AppStore
    @State private var stage = 0
    @State private var keyRead = false

    /// Title, figure card, solution, key sentence.
    private static let delays: [Double] = [0.3, 0.45, 1.3, 1.0]

    /// The key sentence starts lighting up once its card is on screen.
    private static var keyDelay: Double {
        delays.reduce(0, +) + 0.35
    }

    var body: some View {
        OnboardingScaffold(buttonsVisible: stage >= Self.delays.count && keyRead) {
            HStack(spacing: 12) {
                CoachAvatar(coach: store.coach, size: 48)
                Text(OnboardingLevelCopy.riskTitle)
                    .font(Theme.title(24))
                    .foregroundStyle(Theme.ink)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
            }
            .onboardingStaged(0, stage: stage)
            OnboardingInjuryFigureCard(grown: stage > 1)
                .onboardingStaged(1, stage: stage)
            Text(OnboardingLevelCopy.riskSolution)
                .font(.system(size: 17))
                .foregroundStyle(Theme.ink)
                .lineSpacing(5)
                .fixedSize(horizontal: false, vertical: true)
                .onboardingStaged(2, stage: stage)
            OnboardingEmphasisText(text: OnboardingLevelCopy.riskKey,
                                   size: 22, lineSpacing: 5,
                                   highlight: OnboardingLevelCopy.riskKeyHighlight,
                                   restingColor: Theme.muted,
                                   wordInterval: 0.14,
                                   startDelay: Self.keyDelay,
                                   onFinished: { keyRead = true })
                .padding(18)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.greenTint, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
                .onboardingStaged(3, stage: stage)
        }
        .onboardingStageSequence($stage, delays: Self.delays)
    }
}

/// 18 against 8: two numbers on two bars, the sentence and its source underneath.
private struct OnboardingInjuryFigureCard: View {
    /// The bars rise and the numbers count once the card is on screen.
    let grown: Bool

    private static let barHeight: CGFloat = 96

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(OnboardingInjuryFigure.unit)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Theme.muted)
            HStack(alignment: .bottom, spacing: 12) {
                column(rate: OnboardingInjuryFigure.newRunnersRate,
                       label: OnboardingInjuryFigure.newRunnersLabel,
                       tint: Theme.orangeDeep, fill: Theme.orange)
                column(rate: OnboardingInjuryFigure.regularRunnersRate,
                       label: OnboardingInjuryFigure.regularRunnersLabel,
                       tint: Theme.green, fill: Theme.greenSoft)
            }
            Text(OnboardingInjuryFigure.text)
                .font(.system(size: 14))
                .foregroundStyle(Theme.body)
                .lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)
            Text(OnboardingInjuryFigure.source)
                .font(.system(size: 11))
                .foregroundStyle(Theme.faint)
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 24)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(OnboardingInjuryFigure.text) Source: \(OnboardingInjuryFigure.source)")
    }

    private func column(rate: Int, label: String, tint: Color, fill: Color) -> some View {
        let top = max(OnboardingInjuryFigure.newRunnersRate, OnboardingInjuryFigure.regularRunnersRate, 1)
        let full = Self.barHeight * CGFloat(rate) / CGFloat(top)
        return VStack(spacing: 8) {
            VStack(spacing: 4) {
                OnboardingCountingValue(value: grown ? Double(rate) : 0, format: { "\($0)" })
                    .font(.system(size: 44, weight: .bold))
                    .monospacedDigit()
                    .foregroundStyle(tint)
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(fill)
                    .frame(height: grown ? full : 6)
                    .frame(maxWidth: .infinity)
            }
            .frame(height: Self.barHeight + 58, alignment: .bottom)
            Text(label)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity)
    }
}

/// Intermediate and advanced runners: one screen each.
struct OnboardingLevelFocusScreen: View {
    @EnvironmentObject private var store: AppStore
    /// Raised by each sentence when the coach has finished saying it.
    @State private var spoken = 0

    private var isAdvanced: Bool {
        OnboardingRunnerLevel(store.answers.experience) == .advanced
    }

    var body: some View {
        OnboardingScaffold(buttonsVisible: spoken >= 3) {
            OnboardingLevelCoachHeader(coach: store.coach)
            if isAdvanced {
                advanced
            } else {
                intermediate
            }
        }
    }

    @ViewBuilder private var intermediate: some View {
        OnboardingSpokenText(text: OnboardingLevelCopy.intermediateHeadline,
                             size: 34, weight: .bold, color: Theme.ink, lineSpacing: 2,
                             startDelay: 0.5,
                             onFinished: { spoken = max(spoken, 1) })
            .padding(.top, 8)
        OnboardingSpokenText(text: OnboardingLevelCopy.intermediateBody,
                             size: 20, weight: .regular, color: Theme.body, lineSpacing: 6,
                             isActive: spoken >= 1,
                             onFinished: { spoken = max(spoken, 2) })
        OnboardingLevelSessionTiles()
            .modifier(OnboardingStagedModifier(visible: spoken >= 2))
            .animation(.easeOut(duration: 0.45), value: spoken)
        OnboardingSpokenText(text: OnboardingLevelCopy.intermediateClosing,
                             size: 20, weight: .semibold, color: Theme.ink, lineSpacing: 6,
                             isActive: spoken >= 2,
                             startDelay: 0.5,
                             onFinished: { spoken = max(spoken, 3) })
    }

    @ViewBuilder private var advanced: some View {
        OnboardingSpokenText(text: OnboardingLevelCopy.advancedHeadline,
                             size: 34, weight: .bold, color: Theme.ink, lineSpacing: 2,
                             startDelay: 0.5,
                             onFinished: { spoken = max(spoken, 1) })
            .padding(.top, 8)
        OnboardingSpokenText(text: OnboardingLevelCopy.advancedBody,
                             size: 20, weight: .regular, color: Theme.body, lineSpacing: 6,
                             isActive: spoken >= 1,
                             onFinished: { spoken = max(spoken, 3) })
        OnboardingLevelLoadChart(grown: spoken >= 3)
            .modifier(OnboardingStagedModifier(visible: spoken >= 3))
            .animation(.easeOut(duration: 0.5), value: spoken)
    }
}

/// The four kinds of session of a week, as small tiles.
private struct OnboardingLevelSessionTiles: View {
    private static let kinds: [WorkoutKind] = [.easy, .intervals, .long, .strength]

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            ForEach(Self.kinds.indices, id: \.self) { index in
                let kind = Self.kinds[index]
                VStack(spacing: 8) {
                    IconTile(systemName: kind.symbol,
                             tint: kind.isQuality ? Theme.orangeDeep : Theme.green,
                             background: kind.isQuality ? Theme.orangeTint : Theme.greenTint,
                             size: 48)
                    Text(kind.title)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Theme.ink)
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .padding(.vertical, 14)
        .padding(.horizontal, 8)
        .frame(maxWidth: .infinity)
        .card(radius: 22)
        .accessibilityHidden(true)
    }
}

/// Eight weeks of load: three that build, then a lighter one, as the plan does.
private struct OnboardingLevelLoadChart: View {
    let grown: Bool

    private static let loads: [CGFloat] = [0.50, 0.60, 0.70, 0.56, 0.76, 0.86, 0.96, 0.77]
    private static let height: CGFloat = 96

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(OnboardingLevelCopy.advancedChartCaption)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.muted)
            HStack(alignment: .bottom, spacing: 8) {
                ForEach(Self.loads.indices, id: \.self) { index in
                    RoundedRectangle(cornerRadius: 7, style: .continuous)
                        .fill(index % 4 == 3 ? Theme.greenTint : Theme.green)
                        .frame(height: grown ? Self.height * Self.loads[index] : 6)
                        .frame(maxWidth: .infinity)
                }
            }
            .frame(height: Self.height, alignment: .bottom)
            HStack(spacing: 14) {
                legend("Build", color: Theme.green)
                legend("Lighter week", color: Theme.greenTint)
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 24)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(OnboardingLevelCopy.advancedChartCaption)
    }

    private func legend(_ title: String, color: Color) -> some View {
        HStack(spacing: 6) {
            RoundedRectangle(cornerRadius: 3, style: .continuous)
                .fill(color)
                .frame(width: 12, height: 12)
            Text(title)
                .font(.system(size: 12))
                .foregroundStyle(Theme.muted)
        }
    }
}

#Preview("Level intro") {
    OnboardingLevelIntroScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}

#Preview("Level risk") {
    OnboardingLevelRiskScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}

#Preview("Level focus") {
    OnboardingLevelFocusScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}
