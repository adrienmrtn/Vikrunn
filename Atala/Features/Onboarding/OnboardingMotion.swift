import SwiftUI

/// Shared between the navigator and the screen transition, so a screen leaving the stage knows
/// whether the incoming one is a coach explainer that reveals itself over it.
final class OnboardingTransitionContext {
    var holdsOutgoing = false
}

enum OnboardingRevealTiming {
    /// Duration of the circular colour reveal.
    static let reveal: Double = 0.65
    /// How long the previous screen stays underneath while the circle grows.
    static let hold: Double = 0.75
    /// When the coach and the text start to fade in.
    static let content: Double = 0.42
    /// When the coach starts to speak.
    static let speech: Double = 0.7
}

/// Incoming screen: fades in while sliding in from the right and settling.
struct OnboardingEnterModifier: ViewModifier {
    let isActive: Bool

    func body(content: Content) -> some View {
        content
            .opacity(isActive ? 0 : 1)
            .scaleEffect(isActive ? 0.97 : 1)
            .offset(x: isActive ? 28 : 0, y: isActive ? 6 : 0)
    }
}

/// Outgoing screen: fades out, or only dims while an explainer screen takes over above it.
/// Animatable so that `body` is evaluated during the animation, when the context is up to date.
struct OnboardingExitModifier: ViewModifier, Animatable {
    var progress: CGFloat
    let context: OnboardingTransitionContext

    var animatableData: CGFloat {
        get { progress }
        set { progress = newValue }
    }

    func body(content: Content) -> some View {
        let clamped = Double(min(max(progress, 0), 1))
        return content.opacity(context.holdsOutgoing ? 1 - 0.35 * clamped : 1 - clamped)
    }
}

extension AnyTransition {
    /// Quick cross-fade, identical forward and back. Explainer screens (`reveals`) appear at once
    /// because they draw their own circular reveal.
    static func onboardingScreen(_ context: OnboardingTransitionContext, reveals: Bool) -> AnyTransition {
        let enter: AnyTransition = reveals
            ? AnyTransition.identity
            : AnyTransition.modifier(active: OnboardingEnterModifier(isActive: true),
                                     identity: OnboardingEnterModifier(isActive: false))
        let exit = AnyTransition.modifier(active: OnboardingExitModifier(progress: 1, context: context),
                                          identity: OnboardingExitModifier(progress: 0, context: context))
        return AnyTransition.asymmetric(insertion: enter, removal: exit)
    }
}

/// Circle growing from below the bottom edge (centre bottom) until it covers the whole rectangle.
/// At `progress` 1 the path is the full rectangle, so nothing can stay clipped.
struct OnboardingRevealShape: Shape {
    var progress: CGFloat

    var animatableData: CGFloat {
        get { progress }
        set { progress = newValue }
    }

    func path(in rect: CGRect) -> Path {
        if progress >= 1 { return Path(rect) }
        if progress <= 0 { return Path() }
        let drop = rect.width * 0.3
        let centre = CGPoint(x: rect.midX, y: rect.maxY + drop)
        let full = hypot(rect.width / 2, rect.height + drop) + 2
        let radius = full * progress
        return Path(ellipseIn: CGRect(x: centre.x - radius, y: centre.y - radius,
                                      width: radius * 2, height: radius * 2))
    }
}

/// "Tap to continue" as plain text: fades in when `visible`, then breathes gently.
struct OnboardingTapHint: View {
    let color: Color
    var shadow: Bool = false
    let visible: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var dimmed = false

    var body: some View {
        Text(L("Tap to continue"))
            .font(.system(size: 16, weight: .semibold))
            .foregroundStyle(color)
            .shadow(color: Color.black.opacity(shadow ? 0.45 : 0), radius: 6, x: 0, y: 2)
            .opacity(dimmed ? 0.45 : 1)
            .opacity(visible ? 1 : 0)
            .animation(.easeOut(duration: 0.45), value: visible)
            .accessibilityHidden(true)
            .onAppear { pulse(visible) }
            .onChange(of: visible) { _, isVisible in
                pulse(isVisible)
            }
    }

    private func pulse(_ isVisible: Bool) {
        guard isVisible, !reduceMotion, !dimmed else { return }
        withAnimation(.easeInOut(duration: 1.0).repeatForever(autoreverses: true)) {
            dimmed = true
        }
    }
}

// MARK: - Staged appearance

/// Fade + rise used by blocks that appear one after another.
struct OnboardingStagedModifier: ViewModifier {
    let visible: Bool

    func body(content: Content) -> some View {
        content
            .opacity(visible ? 1 : 0)
            .offset(y: visible ? 0 : 14)
    }
}

/// Raises `stage` from its current value to `delays.count`, waiting `delays[n]` seconds before
/// stage `n + 1`. Stops when the view disappears; with Reduce Motion everything shows at once.
/// The sequence waits until `isActive` is true (used to chain it after another animation).
struct OnboardingStageSequence: ViewModifier {
    @Binding var stage: Int
    let delays: [Double]
    var isActive: Bool = true

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        content.task(id: isActive) { await run() }
    }

    @MainActor
    private func run() async {
        guard isActive else { return }
        let total = delays.count
        let first = stage
        guard first < total else { return }
        if reduceMotion {
            stage = total
            return
        }
        for index in first..<total {
            let pause = UInt64(max(0, delays[index]) * 1_000_000_000)
            try? await Task.sleep(nanoseconds: pause)
            if Task.isCancelled { return }
            withAnimation(.easeOut(duration: 0.45)) {
                stage = index + 1
            }
        }
    }
}

extension View {
    /// Visible once `stage` has passed `index`.
    func onboardingStaged(_ index: Int, stage: Int) -> some View {
        modifier(OnboardingStagedModifier(visible: stage > index))
    }

    func onboardingStageSequence(_ stage: Binding<Int>, delays: [Double], isActive: Bool = true) -> some View {
        modifier(OnboardingStageSequence(stage: stage, delays: delays, isActive: isActive))
    }
}

/// Number that counts towards `value` when the change is animated.
struct OnboardingCountingValue: View, Animatable {
    var value: Double
    let format: (Int) -> String

    var animatableData: Double {
        get { value }
        set { value = newValue }
    }

    var body: some View {
        Text(format(Int(value.rounded())))
    }
}
