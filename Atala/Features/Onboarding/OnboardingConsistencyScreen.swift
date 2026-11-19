import SwiftUI

/// Right after the coach's reply to the "why": the reason Atala exists. The logo settles in, the
/// two sentences write themselves one after the other, then the button arrives.
struct OnboardingConsistencyScreen: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var logoShown = false
    @State private var headlineDone = false
    @State private var finished = false

    private static var headline: String { L("Being consistent is hard.") }
    /// The words between the two `*` are the highlighted ones: the markers stay in the translations.
    private static var promise: String {
        L("That's why Atala exists: *a coach in your pocket,* always available, built to finally get you to your goal.")
    }

    var body: some View {
        OnboardingScaffold(buttonsVisible: finished) {
            BrandLogo(style: .mark, height: 52)
                .foregroundStyle(Theme.forest)
                .scaleEffect(logoShown ? 1 : 0.86, anchor: .leading)
                .opacity(logoShown ? 1 : 0)
                .offset(y: logoShown ? 0 : 8)
                .padding(.top, 20)
            OnboardingEmphasisText(text: Self.headline,
                                   size: 38, lineSpacing: 2,
                                   wordInterval: 0.11,
                                   startDelay: 0.45,
                                   onFinished: { headlineDone = true })
                .accessibilityAddTraits(.isHeader)
            // Starts once the headline is there, after a short breath, whatever its length
            // in the language shown.
            OnboardingEmphasisText(text: Self.promise,
                                   size: 26, lineSpacing: 6,
                                   wordInterval: 0.085,
                                   isActive: headlineDone,
                                   startDelay: 0.2,
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
            withAnimation(.easeOut(duration: 0.6).delay(0.12)) {
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
