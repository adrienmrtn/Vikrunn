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

    /// Full-screen "tap to continue" screens: no progress bar.
    var isTapScreen: Bool {
        self == .hello || self == .goalIntro || self == .coachReply
    }

    /// The bar runs from the first name to the reviews.
    var showsProgress: Bool {
        rawValue >= OnboardingStep.firstName.rawValue
            && rawValue <= OnboardingStep.reviews.rawValue
            && !isTapScreen
    }

    var allowsBack: Bool {
        switch self {
        case .welcome, .loading, .planReady, .permissions, .healthConnect: return false
        default: return true
        }
    }
}

/// Shared between the navigator and the slide transition so that a screen leaving the stage
/// always slides the right way, even when the direction changed after it was drawn.
final class OnboardingDirection {
    var forward = true
    var width: CGFloat = 400
}

struct OnboardingSlideModifier: ViewModifier {
    let direction: OnboardingDirection
    let isRemoval: Bool
    let isActive: Bool

    private var offset: CGFloat {
        guard isActive else { return 0 }
        let towardsTrailing = direction.forward != isRemoval
        return towardsTrailing ? direction.width : -direction.width
    }

    func body(content: Content) -> some View {
        content
            .offset(x: offset)
            .opacity(isActive ? 0 : 1)
    }
}

extension AnyTransition {
    /// Forward: the new screen comes from the right and the old one leaves to the left. Back: the reverse.
    static func onboardingSlide(_ direction: OnboardingDirection) -> AnyTransition {
        .asymmetric(
            insertion: .modifier(
                active: OnboardingSlideModifier(direction: direction, isRemoval: false, isActive: true),
                identity: OnboardingSlideModifier(direction: direction, isRemoval: false, isActive: false)
            ),
            removal: .modifier(
                active: OnboardingSlideModifier(direction: direction, isRemoval: true, isActive: true),
                identity: OnboardingSlideModifier(direction: direction, isRemoval: true, isActive: false)
            )
        )
    }
}

/// Owns the current step: navigation, skipping rules and the progress value.
@MainActor
final class OnboardingNavigator: ObservableObject {
    @Published private(set) var step: OnboardingStep = .welcome
    @Published private(set) var isReady = false
    let direction = OnboardingDirection()
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

    func go(to target: OnboardingStep, forward: Bool, store: AppStore) {
        guard target != step else { return }
        // Ignore a second tap while the previous transition is still starting.
        let now = Date()
        guard now.timeIntervalSince(lastMove) > 0.3 else { return }
        lastMove = now
        direction.forward = forward
        withAnimation(.spring(response: 0.45, dampingFraction: 0.88)) {
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
}

private struct OnboardingStepKey: EnvironmentKey {
    static let defaultValue: OnboardingStep = .welcome
}

extension EnvironmentValues {
    /// The step a screen belongs to (stays the same while the screen slides out).
    var onboardingStep: OnboardingStep {
        get { self[OnboardingStepKey.self] }
        set { self[OnboardingStepKey.self] = newValue }
    }
}

/// The whole entry flow, from the welcome carousel to the first day in the app.
struct OnboardingFlowView: View {
    @EnvironmentObject private var store: AppStore
    @StateObject private var nav = OnboardingNavigator()

    var body: some View {
        GeometryReader { geo in
            ZStack {
                Color.white.ignoresSafeArea()
                if nav.isReady {
                    OnboardingStepHost(step: nav.step)
                        .id(nav.step)
                        .transition(.onboardingSlide(nav.direction))
                }
            }
            .frame(width: geo.size.width, height: geo.size.height)
            .onAppear { nav.direction.width = geo.size.width }
            .onChange(of: geo.size.width) { _, width in
                nav.direction.width = width
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
