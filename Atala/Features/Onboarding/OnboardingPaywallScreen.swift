import SwiftUI

/// Screen 31: the paywall. Fake and non-blocking: the close cross and the main button both move on.
/// There is no way back from it, and the cross only shows up after a few seconds.
struct OnboardingPaywallScreen: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var nav: OnboardingNavigator
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var closeShown = false

    /// Seconds before the close cross appears.
    private static let closeDelay: Double = 3

    private var offer: Text {
        Text("Try \(OnboardingPaywallOffer.trialDays) days for free,").foregroundColor(Theme.green).bold()
            + Text(" then \(OnboardingPaywallOffer.monthlyPrice) a month (\(OnboardingPaywallOffer.yearlyPrice) billed yearly)")
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                BrandLogo(style: .full, height: 20)
                    .foregroundStyle(Theme.forest)
                    .padding(.leading, 12)
                Spacer()
                Button(action: { nav.next(store) }) {
                    Image(systemName: "xmark")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Theme.muted)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel("Close")
                .opacity(closeShown ? 1 : 0)
                .allowsHitTesting(closeShown)
                .accessibilityHidden(!closeShown)
            }
            .padding(.horizontal, 8)
            Spacer(minLength: 12)
            VStack(spacing: 24) {
                CoachAvatar(coach: store.coach, size: 72)
                offer
                    .font(.system(size: 22, weight: .medium))
                    .foregroundColor(Theme.ink)
                    .lineSpacing(6)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                Text("Prices shown as an example. Nothing is charged in this version.")
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.muted)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal, 28)
            Spacer(minLength: 12)
            HStack(spacing: 8) {
                Image(systemName: "lock.fill")
                    .font(.system(size: 13))
                Text("Secured by the App Store")
                    .font(.system(size: 14))
            }
            .foregroundStyle(Theme.muted)
            .padding(.bottom, 14)
            Button(action: { nav.next(store) }) {
                VStack(spacing: 2) {
                    Text("Start my free trial")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(Color.white)
                    Text("Easy to cancel, at any time")
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.greenTint)
                }
                .frame(maxWidth: .infinity)
                .frame(height: 68)
                .background(Theme.green, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(Theme.greenTint)
        }
        .background(Color.white.ignoresSafeArea())
        .task {
            await revealClose()
        }
    }

    /// The delay is a rule of the screen, so it is kept with Reduce Motion; only the fade is dropped.
    @MainActor
    private func revealClose() async {
        guard !closeShown else { return }
        try? await Task.sleep(nanoseconds: UInt64(Self.closeDelay * 1_000_000_000))
        if Task.isCancelled { return }
        if reduceMotion {
            closeShown = true
        } else {
            withAnimation(.easeOut(duration: 0.45)) {
                closeShown = true
            }
        }
    }
}

#Preview("Paywall") {
    OnboardingPaywallScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}
