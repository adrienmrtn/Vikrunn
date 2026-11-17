import SwiftUI

/// Every screen of the entry flow, in order. The raw value is what `AppStore.onboardingStep` stores,
/// so never reorder the cases once the app is released: append instead.
enum OnboardingStep: Int, CaseIterable, Hashable {
    case welcome = 0
    case firstName, coach, hello, gender, experience, volume, recent, health, mirror
    case goalIntro, goal, race, target, why, coachReply
    case days, longRun, constraints, message, tone, projection, reviews
    case signIn, loading, planReady, commitment, trial, timeline, reminder, paywall
    case permissions, healthConnect

    /// Full-screen coach "explainer" screens (tap to continue, circular reveal): no progress bar.
    var isTapScreen: Bool {
        self == .hello || self == .goalIntro || self == .coachReply
    }

    /// The bar runs from the first name to the reviews.
    var showsProgress: Bool {
        rawValue >= OnboardingStep.firstName.rawValue
            && rawValue <= OnboardingStep.reviews.rawValue
            && !isTapScreen
    }

    /// The paywall cannot be left backwards: no back button there, and `back` does nothing.
    var allowsBack: Bool {
        switch self {
        case .welcome, .loading, .planReady, .paywall, .permissions, .healthConnect: return false
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

    /// Resumes where the user left (answers and step are saved on the phone).
    func restore(from store: AppStore) {
        guard !isReady else { return }
        let saved = OnboardingStep(rawValue: store.onboardingStep) ?? .welcome
        if saved.rawValue > OnboardingStep.loading.rawValue, store.plan == nil {
            store.generatePlan()
        }
        step = saved
        isReady = true
    }

    func isSkipped(_ candidate: OnboardingStep, store: AppStore) -> Bool {
        switch candidate {
        case .race, .target:
            return store.answers.goal == .regular
        case .longRun:
            return store.answers.runDays.count <= 1
        case .signIn:
            return store.account != nil
        default:
            return false
        }
    }

    func next(_ store: AppStore) {
        var raw = step.rawValue + 1
        while let candidate = OnboardingStep(rawValue: raw) {
            if !isSkipped(candidate, store: store) {
                go(to: candidate, forward: true, store: store)
                return
            }
            raw += 1
        }
        store.finishOnboarding()
    }

    func back(_ store: AppStore) {
        guard step.allowsBack else { return }
        var raw = step.rawValue - 1
        while let candidate = OnboardingStep(rawValue: raw) {
            if !isSkipped(candidate, store: store) {
                go(to: candidate, forward: false, store: store)
                return
            }
            raw -= 1
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
            : .easeOut(duration: 0.3)
        withAnimation(animation) {
            step = target
        }
        store.onboardingStep = target.rawValue
    }

    /// 5 % on the first name, 100 % on the reviews, even steps in between.
    func progress(for candidate: OnboardingStep, store: AppStore) -> Double? {
        guard candidate.showsProgress else { return nil }
        let withoutRace = store.answers.goal == .regular
        let bar = OnboardingStep.allCases.filter { item in
            guard item.showsProgress else { return false }
            if item == candidate { return true }
            return !(withoutRace && (item == .race || item == .target))
        }
        guard bar.count > 1, let index = bar.firstIndex(of: candidate) else { return 0.05 }
        return 0.05 + 0.95 * Double(index) / Double(bar.count - 1)
    }

    /// Value held by the fixed bar on any step: screens without a bar keep the value of the last
    /// step that had one, so the bar never runs backwards while it is hidden.
    func barProgress(for candidate: OnboardingStep, store: AppStore) -> Double {
        var raw = candidate.rawValue
        while let item = OnboardingStep(rawValue: raw) {
            let counts = item == candidate || !isSkipped(item, store: store)
            if item.showsProgress, counts {
                return progress(for: item, store: store) ?? 0.05
            }
            raw -= 1
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
            .accessibilityLabel("Back")
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
                                     onBack: { nav.back(store) })
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
        if step.rawValue <= OnboardingStep.mirror.rawValue {
            profileScreens
        } else if step.rawValue <= OnboardingStep.reviews.rawValue {
            goalScreens
        } else {
            closingScreens
        }
    }

    @ViewBuilder private var profileScreens: some View {
        switch step {
        case .welcome: OnboardingWelcomeScreen()
        case .firstName: OnboardingFirstNameScreen()
        case .coach: OnboardingCoachScreen()
        case .hello: OnboardingHelloScreen()
        case .gender: OnboardingGenderScreen()
        case .experience: OnboardingExperienceScreen()
        case .volume: OnboardingVolumeScreen()
        case .recent: OnboardingRecentScreen()
        case .health: OnboardingHealthScreen()
        case .mirror: OnboardingMirrorScreen()
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
        case .days: OnboardingDaysScreen()
        case .longRun: OnboardingLongRunScreen()
        case .constraints: OnboardingConstraintsScreen()
        case .message: OnboardingMessageScreen()
        case .tone: OnboardingToneScreen()
        case .projection: OnboardingProjectionScreen()
        case .reviews: OnboardingReviewsScreen()
        default: EmptyView()
        }
    }

    @ViewBuilder private var closingScreens: some View {
        switch step {
        case .signIn: OnboardingSignInScreen()
        case .loading: OnboardingLoadingScreen()
        case .planReady: OnboardingPlanReadyScreen()
        case .commitment: OnboardingCommitmentScreen()
        case .trial: OnboardingTrialScreen()
        case .timeline: OnboardingTimelineScreen()
        case .reminder: OnboardingReminderScreen()
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
