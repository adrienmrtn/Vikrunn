import SwiftUI

/// Every screen of the entry flow. The raw value is what `AppStore.onboardingStep` stores, so it
/// identifies a screen for good: never renumber or reuse one. New screens are appended with a new
/// raw value, and the order in which screens are shown lives in `order`, not in the raw values.
enum OnboardingStep: Int, CaseIterable, Hashable {
    case welcome = 0
    case firstName, coach, hello, gender, experience, volume, recent, health, mirror
    case goalIntro, goal, race, target, why, coachReply
    case days, longRun, constraints, message, tone, projection, reviews
    case signIn, loading, planReady, commitment, trial, timeline, reminder, paywall
    case permissions, healthConnect
    // Added after the first release (raw values 33 and up). Their place in the flow is set by `order`.
    case levelIntro = 33
    case levelRisk = 34
    case levelFocus = 35
    case consistency = 36
    case intro = 37
    case community = 38
    case beyondCoach = 39
    case planMatters = 40
    case ready = 41
    case notifications = 42
    case language = 43

    /// The screens in the order they are shown. `levelRisk` (raw value 34) and `reminder` were
    /// removed from the flow: their raw values stay reserved and a step saved on one of them
    /// resumes on the next screen.
    static let order: [OnboardingStep] = [
        .welcome, .language,
        .firstName, .intro, .coach, .hello, .gender, .experience, .volume, .recent, .health, .mirror,
        .levelIntro, .levelFocus,
        .goalIntro, .goal, .race, .target, .why, .coachReply, .consistency,
        .days, .longRun, .constraints, .message, .tone, .projection, .reviews, .community,
        .signIn, .loading, .planReady, .ready, .commitment, .beyondCoach, .planMatters, .trial, .timeline, .paywall,
        .permissions, .notifications, .healthConnect,
    ]

    private static let positions: [OnboardingStep: Int] = {
        var result: [OnboardingStep: Int] = [:]
        for (index, item) in order.enumerated() {
            result[item] = index
        }
        return result
    }()

    /// Index of the step in `order`: use it, never the raw value, to compare two steps.
    var position: Int {
        OnboardingStep.positions[self] ?? 0
    }

    /// Full-screen coach "explainer" screens (tap to continue, circular reveal): no progress bar.
    var isTapScreen: Bool {
        self == .hello || self == .goalIntro || self == .coachReply
    }

    /// The bar runs from the first name to the reviews.
    var showsProgress: Bool {
        position >= OnboardingStep.firstName.position
            && position <= OnboardingStep.reviews.position
            && !isTapScreen
    }

    /// The paywall cannot be left backwards: no back button there, and `back` does nothing.
    var allowsBack: Bool {
        switch self {
        case .welcome, .loading, .planReady, .paywall, .permissions, .notifications, .healthConnect: return false
        default: return true
        }
    }

    /// The flow's fixed header shows its back chevron (explainer screens draw their own, in white).
    var showsHeaderBack: Bool {
        allowsBack && !isTapScreen
    }
}

/// Owns the current step: navigation, skipping rules and the progress value.
@MainActor
final class OnboardingNavigator: ObservableObject {
    @Published private(set) var step: OnboardingStep = .welcome
    @Published private(set) var isReady = false
    let transition = OnboardingTransitionContext()
    private var lastMove = Date.distantPast

    /// Resumes where the user left (answers and step are saved on the phone). A step saved by an
    /// earlier version keeps its raw value, so it opens the same screen.
    func restore(from store: AppStore) {
        guard !isReady else { return }
        var saved = OnboardingStep(rawValue: store.onboardingStep) ?? .welcome
        if saved == .levelRisk { saved = .goalIntro }
        // The trial-reminder screen is gone: a step saved on it resumes on the paywall.
        if saved == .reminder { saved = .paywall }
        if saved.position > OnboardingStep.loading.position {
            // A plan built on another day would start in the past: rebuild it from today.
            // Nothing can be done or missed yet, the app has not been opened.
            let fresh = store.plan.map { Calendar.plan.isDateInToday($0.createdAt) } ?? false
            if !fresh { store.generatePlan() }
        }
        step = saved
        isReady = true
    }

    /// Someone who said they are just starting, or that they do not run yet.
    static func isStarting(_ answers: OnboardingAnswers) -> Bool {
        (answers.experience ?? .starting) == .starting || answers.weeklyVolume == .notYet
    }

    func isSkipped(_ candidate: OnboardingStep, store: AppStore) -> Bool {
        switch candidate {
        case .levelRisk:
            return true
        case .health:
            // The health question is out of the flow for now (no injury or medical wording at sign-up).
            return true
        case .recent:
            // Nothing to ask someone who does not run yet.
            return store.answers.weeklyVolume == .notYet
        case .levelIntro:
            // "Starting is the hardest part" is only for people who are really starting.
            return !Self.isStarting(store.answers)
        case .levelFocus:
            // Everyone else: one screen about what the plan brings them.
            return Self.isStarting(store.answers)
        case .race, .target:
            return store.answers.goal.isOpenEnded
        case .longRun:
            return store.answers.runDays.count <= 1
        case .signIn:
            return store.account != nil
        default:
            return false
        }
    }

    func next(_ store: AppStore) {
        let order = OnboardingStep.order
        var index = step.position + 1
        while index < order.count {
            let candidate = order[index]
            if !isSkipped(candidate, store: store) {
                go(to: candidate, forward: true, store: store)
                return
            }
            index += 1
        }
        store.finishOnboarding()
    }

    func back(_ store: AppStore) {
        guard step.allowsBack else { return }
        let order = OnboardingStep.order
        var index = step.position - 1
        while index >= 0 {
            let candidate = order[index]
            if !isSkipped(candidate, store: store) {
                go(to: candidate, forward: false, store: store)
                return
            }
            index -= 1
        }
    }

    /// `forward` is kept for the call sites; the transition is the same both ways.
    func go(to target: OnboardingStep, forward: Bool, store: AppStore) {
        guard target != step else { return }
        // Ignore a second tap while the previous transition is still starting.
        let now = Date()
        guard now.timeIntervalSince(lastMove) > 0.3 else { return }
        lastMove = now
        // Explainer screens draw their own circular reveal: the screen underneath waits for it.
        transition.holdsOutgoing = target.isTapScreen
        let animation: Animation = target.isTapScreen
            ? .easeInOut(duration: OnboardingRevealTiming.hold)
            : .spring(response: 0.46, dampingFraction: 0.88)
        withAnimation(animation) {
            step = target
        }
        store.onboardingStep = target.rawValue
    }

    /// Steps left out of the bar because an earlier answer rules them out (the level screens depend
    /// on the experience, the race screens on the goal), so the remaining steps stay evenly spaced.
    private func isLeftOutOfBar(_ item: OnboardingStep, store: AppStore) -> Bool {
        switch item {
        case .recent, .levelIntro, .levelFocus, .race, .target:
            return isSkipped(item, store: store)
        default:
            return false
        }
    }

    /// 5 % on the first name, 100 % on the reviews, even steps in between.
    func progress(for candidate: OnboardingStep, store: AppStore) -> Double? {
        guard candidate.showsProgress else { return nil }
        let bar = OnboardingStep.order.filter { item in
            guard item.showsProgress else { return false }
            if item == candidate { return true }
            return !isLeftOutOfBar(item, store: store)
        }
        guard bar.count > 1, let index = bar.firstIndex(of: candidate) else { return 0.05 }
        return 0.05 + 0.95 * Double(index) / Double(bar.count - 1)
    }

    /// Value held by the fixed bar on any step: screens without a bar keep the value of the last
    /// step that had one, so the bar never runs backwards while it is hidden.
    func barProgress(for candidate: OnboardingStep, store: AppStore) -> Double {
        let order = OnboardingStep.order
        var index = candidate.position
        while index >= 0 {
            let item = order[index]
            let counts = item == candidate || !isSkipped(item, store: store)
            if item.showsProgress, counts {
                return progress(for: item, store: store) ?? 0.05
            }
            index -= 1
        }
        return 0.05
    }
}

private struct OnboardingStepKey: EnvironmentKey {
    static let defaultValue: OnboardingStep = .welcome
}

extension EnvironmentValues {
    /// The step a screen belongs to (stays the same while the screen fades out).
    var onboardingStep: OnboardingStep {
        get { self[OnboardingStepKey.self] }
        set { self[OnboardingStepKey.self] = newValue }
    }
}

/// Back chevron + progress bar, owned by the flow: it stays still while the screens cross-fade
/// underneath. Screens leave an empty `OnboardingHeaderSpace` where it sits.
struct OnboardingFlowHeader: View {
    /// 0...1. Animates from one step to the next.
    let progress: Double
    let showsBar: Bool
    let showsBack: Bool
    let onBack: () -> Void

    static let height: CGFloat = 44

    var body: some View {
        HStack(spacing: 12) {
            Button(action: onBack) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel(L("Back"))
            .padding(.leading, -12)
            .opacity(showsBack ? 1 : 0)
            .allowsHitTesting(showsBack)
            .accessibilityHidden(!showsBack)
            bar
                .opacity(showsBar ? 1 : 0)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
            Color.clear.frame(width: 32, height: 44)
        }
        .frame(height: Self.height)
        .animation(.easeOut(duration: 0.22), value: showsBar)
        .animation(.easeOut(duration: 0.22), value: showsBack)
    }

    private var bar: some View {
        let clamped = CGFloat(min(max(progress, 0), 1))
        return GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(Theme.hairline)
                Capsule().fill(Theme.ink)
                    .frame(width: max(4, geo.size.width * clamped))
            }
            .animation(.easeInOut(duration: 0.4), value: clamped)
        }
        .frame(height: 4)
    }
}

/// Empty room a screen leaves at its top for the flow's fixed header.
struct OnboardingHeaderSpace: View {
    var body: some View {
        Color.clear
            .frame(height: OnboardingFlowHeader.height)
            .accessibilityHidden(true)
    }
}

/// The whole entry flow, from the welcome carousel to the first day in the app.
struct OnboardingFlowView: View {
    @EnvironmentObject private var store: AppStore
    @StateObject private var nav = OnboardingNavigator()

    var body: some View {
        ZStack {
            Color.white.ignoresSafeArea()
            if nav.isReady {
                // Only the screen takes part in the transition.
                OnboardingStepHost(step: nav.step)
                    .id(nav.step)
                    .transition(.onboardingScreen(nav.transition, reveals: nav.step.isTapScreen))
                    .zIndex(nav.step.isTapScreen ? 1 : 0)
                // Fixed header, above every screen: never inserted or removed on a step change.
                OnboardingFlowHeader(progress: nav.barProgress(for: nav.step, store: store),
                                     showsBar: nav.step.showsProgress,
                                     showsBack: nav.step.showsHeaderBack,
                                     onBack: {
                                         Haptics.tap()
                                         nav.back(store)
                                     })
                    .padding(.horizontal, Theme.screenPadding)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                    .zIndex(2)
            }
        }
        .environmentObject(nav)
        .onAppear { nav.restore(from: store) }
    }
}

/// Maps a step to its screen.
@MainActor
struct OnboardingStepHost: View {
    let step: OnboardingStep

    var body: some View {
        content
            .environment(\.onboardingStep, step)
    }

    @ViewBuilder private var content: some View {
        if step.position <= OnboardingStep.levelFocus.position {
            profileScreens
        } else if step.position <= OnboardingStep.community.position {
            goalScreens
        } else {
            closingScreens
        }
    }

    @ViewBuilder private var profileScreens: some View {
        switch step {
        case .welcome: OnboardingWelcomeScreen()
        case .language: OnboardingLanguageScreen()
        case .firstName: OnboardingFirstNameScreen()
        case .intro: OnboardingIntroScreen()
        case .coach: OnboardingCoachScreen()
        case .hello: OnboardingHelloScreen()
        case .gender: OnboardingGenderScreen()
        case .experience: OnboardingExperienceScreen()
        case .volume: OnboardingVolumeScreen()
        case .recent: OnboardingRecentScreen()
        case .health: OnboardingHealthScreen()
        case .mirror: OnboardingMirrorScreen()
        case .levelIntro: OnboardingLevelIntroScreen()
        case .levelFocus: OnboardingLevelFocusScreen()
        default: EmptyView()
        }
    }

    @ViewBuilder private var goalScreens: some View {
        switch step {
        case .goalIntro: OnboardingGoalIntroScreen()
        case .goal: OnboardingGoalScreen()
        case .race: OnboardingRaceScreen()
        case .target: OnboardingTargetScreen()
        case .why: OnboardingWhyScreen()
        case .coachReply: OnboardingCoachReplyScreen()
        case .consistency: OnboardingConsistencyScreen()
        case .days: OnboardingDaysScreen()
        case .longRun: OnboardingLongRunScreen()
        case .constraints: OnboardingConstraintsScreen()
        case .message: OnboardingMessageScreen()
        case .tone: OnboardingToneScreen()
        case .projection: OnboardingProjectionScreen()
        case .reviews: OnboardingReviewsScreen()
        case .community: OnboardingCommunityScreen()
        default: EmptyView()
        }
    }

    @ViewBuilder private var closingScreens: some View {
        switch step {
        case .signIn: OnboardingSignInScreen()
        case .loading: OnboardingLoadingScreen()
        case .planReady: OnboardingPlanReadyScreen()
        case .ready: OnboardingReadyScreen()
        case .commitment: OnboardingCommitmentScreen()
        case .beyondCoach: OnboardingBeyondCoachScreen()
        case .planMatters: OnboardingPlanMattersScreen()
        case .trial: OnboardingTrialScreen()
        case .timeline: OnboardingTimelineScreen()
        case .notifications: OnboardingNotificationsScreen()
        case .paywall: OnboardingPaywallScreen()
        case .permissions: OnboardingPermissionsScreen()
        case .healthConnect: OnboardingHealthConnectScreen()
        default: EmptyView()
        }
    }
}

#Preview {
    OnboardingFlowView()
        .environmentObject(AppStore(inMemory: true))
}
