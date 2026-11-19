import SwiftUI

/// Large rounded photo used by the story screens of the entry flow. The picture is cropped to the
/// frame; `alignment` chooses the part that stays visible.
struct OnboardingPhotoCard<Caption: View>: View {
    let name: String
    var height: CGFloat = 300
    var alignment: Alignment = .center
    /// Darkens the bottom of the picture so white text stays readable on it.
    var scrim: Bool = false
    @ViewBuilder var caption: Caption

    var body: some View {
        Color(hex: 0x1B1B1B)
            .frame(maxWidth: .infinity)
            .frame(height: height)
            .overlay(alignment: alignment) {
                Image(name)
                    .resizable()
                    .scaledToFill()
                    .accessibilityHidden(true)
            }
            .overlay {
                if scrim {
                    LinearGradient(colors: [Color.black.opacity(0), Color.black.opacity(0.72)],
                                   startPoint: .center, endPoint: .bottom)
                }
            }
            .overlay(alignment: .bottomLeading) {
                caption
            }
            .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
    }
}

/// Right after the first name: a warm welcome before the questions start.
struct OnboardingIntroScreen: View {
    @EnvironmentObject private var store: AppStore
    /// 1 photo, 2 title, 3 text, 4 button.
    @State private var stage = 0

    private static let delays: [Double] = [0.1, 0.35, 0.35, 0.4]

    var body: some View {
        OnboardingScaffold(buttonTitle: "Let's go", buttonsVisible: stage >= Self.delays.count) {
            OnboardingPhotoCard(name: "hero_today_man", height: 320, alignment: .top, scrim: true) {
                BrandLogo(style: .full, height: 24)
                    .foregroundStyle(Theme.cream)
                    .padding(20)
            }
            .onboardingStaged(0, stage: stage)
            VStack(alignment: .leading, spacing: 6) {
                Text("Hey \(store.firstName),")
                    .font(Theme.title(32))
                    .foregroundStyle(Theme.ink)
                Text("welcome to Atala, your new running coach.")
                    .font(Theme.title(32))
                    .foregroundStyle(Theme.green)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isHeader)
            .onboardingStaged(1, stage: stage)
            Text("A few quick questions, and your plan is built around you.")
                .font(.system(size: 17))
                .foregroundStyle(Theme.body)
                .lineSpacing(5)
                .fixedSize(horizontal: false, vertical: true)
                .onboardingStaged(2, stage: stage)
        }
        .onboardingStageSequence($stage, delays: Self.delays)
    }
}

// COMMUNITY FIGURE — shown to every new user: keep it true, update it from real usage before release.
enum OnboardingCommunityFigure {
    static let value = 10_000
    static let caption = "runners coached every day"
}

/// Right after the reviews: the runners already training with Atala.
struct OnboardingCommunityScreen: View {
    /// 1 title, 2 photo and figure, 3 community card, 4 button.
    @State private var stage = 0

    private static let delays: [Double] = [0.1, 0.35, 0.7, 0.4]

    private static func grouped(_ value: Int) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.locale = Locale(identifier: "en_US")
        return formatter.string(from: NSNumber(value: value)) ?? "\(value)"
    }

    var body: some View {
        OnboardingScaffold(title: "Join the Atala community",
                           buttonTitle: "Join them",
                           buttonsVisible: stage >= Self.delays.count) {
            OnboardingPhotoCard(name: "photo_community", height: 340, alignment: .center, scrim: true) {
                VStack(alignment: .leading, spacing: 0) {
                    OnboardingCountingValue(value: stage > 1 ? Double(OnboardingCommunityFigure.value) : 0,
                                            format: Self.grouped)
                        .font(.system(size: 52, weight: .bold))
                        .monospacedDigit()
                        .foregroundStyle(Color.white)
                    Text(OnboardingCommunityFigure.caption)
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(Color.white.opacity(0.92))
                }
                .padding(20)
                .animation(.easeOut(duration: 1.1), value: stage)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(Self.grouped(OnboardingCommunityFigure.value)) \(OnboardingCommunityFigure.caption)")
            }
            .onboardingStaged(1, stage: stage)
            HStack(spacing: 14) {
                IconTile(systemName: "square.and.arrow.up", size: 48)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Atala is also a community")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(Theme.ink)
                    Text("Share your results with your friends 😉")
                        .font(.system(size: 15))
                        .foregroundStyle(Theme.body)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .card(radius: 22)
            .accessibilityElement(children: .combine)
            .onboardingStaged(2, stage: stage)
        }
        .onboardingStageSequence($stage, delays: Self.delays)
    }
}

private struct OnboardingBeyondCoachPoint: Identifiable {
    let id: Int
    let symbol: String
    let title: String

    static let all: [OnboardingBeyondCoachPoint] = [
        OnboardingBeyondCoachPoint(id: 0, symbol: "shield.lefthalf.filled",
                                   title: "Prevent injuries"),
        OnboardingBeyondCoachPoint(id: 1, symbol: "figure.cooldown",
                                   title: "Get every warm-up and stretch you need"),
        OnboardingBeyondCoachPoint(id: 2, symbol: "clock.badge.checkmark",
                                   title: "Get answers 24/7 from a coach who knows you"),
    ]
}

/// Just before the free trial: what Atala does beyond the plan. The points arrive one by one,
/// the button comes last.
struct OnboardingBeyondCoachScreen: View {
    @EnvironmentObject private var store: AppStore
    @State private var emphasised = false
    /// 1 lead-in, 2-4 the points, 5 button.
    @State private var stage = 0

    private static let delays: [Double] = [0.2, 0.55, 0.9, 0.9, 0.7]

    var body: some View {
        OnboardingScaffold(buttonsVisible: stage >= Self.delays.count) {
            OnboardingPhotoCard(name: AtalaPhotos.woman, height: 210, alignment: .top) {
                EmptyView()
            }
            OnboardingEmphasisText(text: "Atala is not just a coach.",
                                   size: 34,
                                   lineSpacing: 4,
                                   highlight: "not just a coach.",
                                   onFinished: { emphasised = true })
                .accessibilityAddTraits(.isHeader)
            Text("It will help you:")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(Theme.muted)
                .onboardingStaged(0, stage: stage)
            VStack(spacing: 12) {
                ForEach(OnboardingBeyondCoachPoint.all) { point in
                    HStack(spacing: 14) {
                        IconTile(systemName: point.symbol, size: 48)
                        Text(point.title)
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(Theme.ink)
                            .fixedSize(horizontal: false, vertical: true)
                        Spacer(minLength: 0)
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .card(radius: 22)
                    .accessibilityElement(children: .combine)
                    .onboardingStaged(point.id + 1, stage: stage)
                }
            }
        }
        .onboardingStageSequence($stage, delays: Self.delays, isActive: emphasised)
    }
}

/// After "not just a coach": why a clear plan matters, shown with the first week of the user's own plan.
struct OnboardingPlanMattersScreen: View {
    @EnvironmentObject private var store: AppStore
    @State private var emphasised = false
    /// 1 caption, then one stage per session, then the closing line and the button.
    @State private var stage = 0

    private var sessions: [Workout] {
        Array((store.plan?.weeks.first?.workouts ?? []).prefix(4))
    }

    private var delays: [Double] {
        [0.2] + Array(repeating: 0.4, count: sessions.count) + [0.5, 0.4]
    }

    private var closing: String {
        let weeks = store.plan?.weeks.count ?? PlanEngine.maxWeeks
        return "Yours is ready: \(OnboardingCopy.weeks(weeks)), session by session."
    }

    var body: some View {
        let list = sessions
        let steps = delays
        return OnboardingScaffold(buttonsVisible: stage >= steps.count) {
            OnboardingEmphasisText(text: "Having a clear, precise plan is essential in running.",
                                   size: 32,
                                   lineSpacing: 5,
                                   highlight: "clear, precise plan",
                                   onFinished: { emphasised = true })
                .accessibilityAddTraits(.isHeader)
                .padding(.top, 8)
            Text("YOUR FIRST WEEK")
                .font(.system(size: 12, weight: .bold))
                .kerning(0.8)
                .foregroundStyle(Theme.muted)
                .opacity(list.isEmpty ? 0 : 1)
                .onboardingStaged(0, stage: stage)
            VStack(spacing: 10) {
                ForEach(Array(list.enumerated()), id: \.element.id) { index, workout in
                    HStack(spacing: 14) {
                        IconTile(systemName: workout.kind.symbol,
                                 tint: workout.kind.isQuality ? Theme.orangeDeep : Theme.green,
                                 background: workout.kind.isQuality ? Theme.orangeTint : Theme.greenTint,
                                 size: 44)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(workout.title)
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundStyle(Theme.ink)
                                .lineLimit(1)
                            Text(Fmt.day(workout.date, "EEEE"))
                                .font(.system(size: 13))
                                .foregroundStyle(Theme.muted)
                        }
                        Spacer(minLength: 8)
                        Text(workout.kind.isRun ? Fmt.km(workout.distanceKm) : Fmt.minutes(workout.durationMin))
                            .font(.system(size: 15, weight: .semibold))
                            .monospacedDigit()
                            .foregroundStyle(Theme.ink)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .card(radius: 20)
                    .accessibilityElement(children: .combine)
                    .onboardingStaged(index + 1, stage: stage)
                }
            }
            Text(closing)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Theme.green)
                .fixedSize(horizontal: false, vertical: true)
                .onboardingStaged(list.count + 1, stage: stage)
        }
        .onboardingStageSequence($stage, delays: steps, isActive: emphasised)
    }
}

/// After the plan preview, before the promise: the coach has everything, one last "ready?".
struct OnboardingReadyScreen: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var coachShown = false
    /// 0 nothing said, 1 first sentence, 2 the question.
    @State private var spoken = 0

    var body: some View {
        OnboardingScaffold(buttonTitle: "I'm ready", buttonHaptic: .strong, buttonsVisible: spoken >= 2) {
            CoachPoseView(coach: store.coach, pose: .thumbsUp)
                .frame(height: 250)
                .frame(maxWidth: .infinity)
                .scaleEffect(coachShown ? 1 : 0.9, anchor: .bottom)
                .opacity(coachShown ? 1 : 0)
                .accessibilityHidden(true)
            OnboardingSpokenText(text: "Okay \(store.firstName), I've got everything I need to help you reach your goals.",
                                 size: 26, weight: .bold, color: Theme.ink, lineSpacing: 4,
                                 startDelay: 0.5,
                                 onFinished: { spoken = max(spoken, 1) })
                .accessibilityAddTraits(.isHeader)
            OnboardingSpokenText(text: "Are you ready?",
                                 size: 34, weight: .bold, color: Theme.green, lineSpacing: 2,
                                 isActive: spoken >= 1,
                                 startDelay: 0.25,
                                 onFinished: { spoken = max(spoken, 2) })
        }
        .onAppear {
            guard !coachShown else { return }
            if reduceMotion {
                coachShown = true
            } else {
                withAnimation(.spring(response: 0.55, dampingFraction: 0.74).delay(0.1)) {
                    coachShown = true
                }
            }
        }
    }
}

#Preview("Intro") {
    OnboardingIntroScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}

#Preview("Community") {
    OnboardingCommunityScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}

#Preview("Beyond coach") {
    OnboardingBeyondCoachScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}
