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

/// Wording of the level screens.
enum OnboardingLevelCopy {
    static let beginnerHeadline = "Starting is the hardest part."
    static let beginnerPraise = "You just did it."
    static let beginnerPromise = "In a few months, you'll be fit, with better cardio than ever."

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

/// The coach in full, in a pose that fits what was just said. Springs in when `visible`.
private struct OnboardingLevelCoachFigure: View {
    let coach: Coach
    let pose: CoachPose
    let visible: Bool
    var height: CGFloat = 210

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        CoachPoseView(coach: coach, pose: pose)
            .frame(height: height)
            .frame(maxWidth: .infinity)
            .scaleEffect(visible ? 1 : 0.92, anchor: .bottom)
            .opacity(visible ? 1 : 0)
            .animation(reduceMotion ? nil : .spring(response: 0.5, dampingFraction: 0.72), value: visible)
            .accessibilityHidden(true)
    }
}

/// Beginners: congratulations first.
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
                                     startDelay: 0.35,
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
            // The coach cheers as soon as "You just did it." has been said.
            OnboardingLevelCoachFigure(coach: store.coach, pose: .cheer, visible: spoken >= 2, height: 230)
        }
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
                             startDelay: 0.35,
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
                             startDelay: 0.35,
                             onFinished: { spoken = max(spoken, 3) })
        OnboardingLevelCoachFigure(coach: store.coach, pose: .present, visible: spoken >= 3, height: 190)
    }

    @ViewBuilder private var advanced: some View {
        OnboardingSpokenText(text: OnboardingLevelCopy.advancedHeadline,
                             size: 34, weight: .bold, color: Theme.ink, lineSpacing: 2,
                             startDelay: 0.35,
                             onFinished: { spoken = max(spoken, 1) })
            .padding(.top, 8)
        OnboardingSpokenText(text: OnboardingLevelCopy.advancedBody,
                             size: 20, weight: .regular, color: Theme.body, lineSpacing: 6,
                             isActive: spoken >= 1,
                             onFinished: { spoken = max(spoken, 3) })
        OnboardingLevelLoadChart(grown: spoken >= 3)
            .modifier(OnboardingStagedModifier(visible: spoken >= 3))
            .animation(.easeOut(duration: 0.5), value: spoken)
        OnboardingLevelCoachFigure(coach: store.coach, pose: .crossed, visible: spoken >= 3, height: 190)
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

#Preview("Level focus") {
    OnboardingLevelFocusScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}
