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

private struct OnboardingSampleNotification: Identifiable {
    let id: Int
    let time: String
    let title: String
    let body: String
}

/// After the location permission: what the reminders look like, then the system question.
/// Saying no is fine: the sessions stay in the Today tab.
struct OnboardingNotificationsScreen: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var nav: OnboardingNavigator
    /// One stage per sample notification, then the text, then the buttons.
    @State private var stage = 0
    @State private var isAsking = false

    private var samples: [OnboardingSampleNotification] {
        let coach = store.coach.name
        var first = "Easy run · 5 km today"
        if let next = store.nextRun {
            first = next.distanceKm > 0 ? "\(next.title) · \(Fmt.km(next.distanceKm)) today" : "\(next.title) today"
        }
        return [
            OnboardingSampleNotification(id: 0, time: "8:00", title: first,
                                         body: "\(coach) here. Keep it easy today: you should be able to talk the whole way."),
            OnboardingSampleNotification(id: 1, time: "Thu 8:00", title: "Intervals today",
                                         body: "\(coach) here. A quality session today. Warm up well, I'll guide you through it."),
            OnboardingSampleNotification(id: 2, time: "Sun 8:00", title: "Long run today",
                                         body: "\(coach) here. Slow and steady, the distance does the work."),
        ]
    }

    private static let delays: [Double] = [0.25, 0.45, 0.45, 0.4, 0.35]

    var body: some View {
        let list = samples
        return OnboardingScaffold(title: "Never miss a run",
                                  buttonTitle: isAsking ? "One moment…" : "Remind me to run",
                                  canContinue: !isAsking,
                                  buttonsVisible: stage >= Self.delays.count,
                                  secondaryTitle: "Not now",
                                  onSecondary: { nav.next(store) },
                                  onContinue: allow) {
            VStack(spacing: 10) {
                ForEach(list) { sample in
                    card(sample)
                        // The cards behind the first one are a little smaller and lighter, like a stack.
                        .scaleEffect(1 - CGFloat(sample.id) * 0.03)
                        .opacity(stage > sample.id ? 1 - Double(sample.id) * 0.2 : 0)
                        .offset(y: stage > sample.id ? 0 : -18)
                }
            }
            .padding(14)
            .background(Theme.surface, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Examples of reminders from your coach")
            Text(PurchaseService.shared.isInTrial
                 ? "Once your free trial is over, \(store.coach.name) reminds you on the morning of each session. Nothing else: no ads, no noise."
                 : "\(store.coach.name) reminds you on the morning of each session. Nothing else: no ads, no noise.")
                .font(.system(size: 17))
                .foregroundStyle(Theme.body)
                .lineSpacing(5)
                .fixedSize(horizontal: false, vertical: true)
                .onboardingStaged(3, stage: stage)
        }
        .onboardingStageSequence($stage, delays: Self.delays)
    }

    private func card(_ sample: OnboardingSampleNotification) -> some View {
        HStack(alignment: .top, spacing: 12) {
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(Theme.forest)
                .frame(width: 40, height: 40)
                .overlay(
                    BrandLogo(style: .mark, height: 20)
                        .foregroundStyle(Theme.cream)
                )
            VStack(alignment: .leading, spacing: 2) {
                HStack(alignment: .firstTextBaseline) {
                    Text(sample.title)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Theme.ink)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                    Spacer(minLength: 6)
                    Text(sample.time)
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.muted)
                }
                Text(sample.body)
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.body)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .shadow(color: .black.opacity(0.08), radius: 10, x: 0, y: 6)
    }

    /// Shows the system question, then moves on whatever the answer.
    private func allow() {
        guard !isAsking else { return }
        isAsking = true
        Task { @MainActor in
            _ = await RunReminders.requestAuthorization()
            isAsking = false
            nav.next(store)
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
