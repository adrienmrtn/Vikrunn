import SwiftUI

struct OnboardingTapButtonStyle: ButtonStyle {
    var active: Bool = true

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(active && configuration.isPressed ? 0.94 : 1)
    }
}

/// Full-screen moment with the coach. The background colour takes over the screen as a circle
/// growing from the bottom, then the coach and the text fade in. The whole screen is one button
/// that moves on, but only once `canContinue` is true (the coach has finished speaking).
struct OnboardingTapScreen<Backdrop: View, Top: View>: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var nav: OnboardingNavigator
    @Environment(\.onboardingStep) private var step
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var reveal: CGFloat = 0
    @State private var contentShown = false

    private let lightContent: Bool
    private let hintColor: Color
    private let hintShadow: Bool
    private let canContinue: Bool
    private let backdrop: Backdrop
    private let top: Top

    /// `lightContent`: white chevron, for dark backgrounds. `hintColor` defaults to white on dark
    /// backgrounds and ink on light ones. The backdrop must not ignore the safe area itself:
    /// it is stretched edge to edge here.
    init(lightContent: Bool = false,
         hintColor: Color? = nil,
         hintShadow: Bool = false,
         canContinue: Bool = true,
         @ViewBuilder backdrop: () -> Backdrop,
         @ViewBuilder top: () -> Top) {
        self.lightContent = lightContent
        self.hintColor = hintColor ?? (lightContent ? Color.white : Theme.ink)
        self.hintShadow = hintShadow
        self.canContinue = canContinue
        self.backdrop = backdrop()
        self.top = top()
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            Button(action: advance) {
                VStack(spacing: 0) {
                    top
                        .padding(.horizontal, 24)
                        .padding(.top, 56)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Spacer(minLength: 12)
                    figure
                        .layoutPriority(1)
                    OnboardingTapHint(color: hintColor, shadow: hintShadow, visible: canContinue)
                        .padding(.top, 16)
                        .padding(.bottom, 14)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .opacity(contentShown ? 1 : 0)
                .offset(y: contentShown ? 0 : 18)
                .contentShape(Rectangle())
            }
            .buttonStyle(OnboardingTapButtonStyle(active: canContinue))
            .accessibilityHint(canContinue ? "Tap to continue" : "")
            backButton
                .opacity(contentShown ? 1 : 0)
        }
        .background { revealedBackdrop }
        .onAppear(perform: startReveal)
    }

    /// The backdrop, edge to edge, clipped by the growing circle.
    private var revealedBackdrop: some View {
        GeometryReader { geo in
            backdrop
                .frame(width: geo.size.width, height: geo.size.height)
                .clipped()
                .clipShape(OnboardingRevealShape(progress: reveal))
        }
        .ignoresSafeArea()
    }

    private func advance() {
        guard canContinue else { return }
        nav.next(store)
    }

    /// State-driven, so the reveal always runs to the end even if the screen change is interrupted.
    private func startReveal() {
        guard reveal < 1 else { return }
        if reduceMotion {
            reveal = 1
            contentShown = true
            return
        }
        withAnimation(.easeInOut(duration: OnboardingRevealTiming.reveal)) {
            reveal = 1
        }
        withAnimation(.easeOut(duration: 0.45).delay(OnboardingRevealTiming.content)) {
            contentShown = true
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
