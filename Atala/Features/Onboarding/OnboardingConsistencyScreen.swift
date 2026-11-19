import SwiftUI

/// Right after the coach's reply to the "why": the reason Atala exists. The logo settles in, the
/// two sentences light up word by word, then the button appears.
struct OnboardingConsistencyScreen: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var logoShown = false
    @State private var finished = false

    private static let headline = "Being consistent is hard."
    private static let promise = "That's why Atala exists: a coach in your pocket, always available, "
        + "built to finally get you to your goal."
    private static let promiseHighlight = "a coach in your pocket,"

    /// The second sentence starts once the first one is lit.
    private static let promiseDelay: Double = 1.3

    var body: some View {
        OnboardingScaffold(buttonsVisible: finished) {
            BrandLogo(style: .mark, height: 52)
                .foregroundStyle(Theme.forest)
                .scaleEffect(logoShown ? 1 : 0.6, anchor: .leading)
                .opacity(logoShown ? 1 : 0)
                .padding(.top, 20)
            OnboardingEmphasisText(text: Self.headline,
                                   size: 38, lineSpacing: 2,
                                   wordInterval: 0.14,
                                   startDelay: 0.42)
            OnboardingEmphasisText(text: Self.promise,
                                   size: 26, lineSpacing: 6,
                                   highlight: Self.promiseHighlight,
                                   wordInterval: 0.105,
                                   startDelay: Self.promiseDelay,
                                   onFinished: { finished = true })
        }
        .task {
            await showLogo()
        }
    }

    @MainActor
    private func showLogo() async {
        guard !logoShown else { return }
        if reduceMotion {
            logoShown = true
        } else {
            withAnimation(.spring(response: 0.5, dampingFraction: 0.68).delay(0.15)) {
                logoShown = true
            }
        }
    }
}

#Preview("Consistency") {
    OnboardingConsistencyScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}
