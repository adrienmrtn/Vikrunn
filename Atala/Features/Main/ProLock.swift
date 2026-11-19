import SwiftUI

/// Blurs a screen that needs Atala Pro and puts a padlock card over it, with the button that
/// opens the paywall. With Atala Pro the content is shown as it is.
struct ProLockModifier: ViewModifier {
    @ObservedObject private var purchases = PurchaseService.shared
    @State private var showPaywall = false
    let symbol: String
    let title: String
    let message: String
    let buttonTitle: String

    func body(content: Content) -> some View {
        if purchases.isPro {
            content
        } else {
            content
                .blur(radius: 9)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
                .overlay {
                    ZStack {
                        Color.white.opacity(0.35).ignoresSafeArea()
                        card
                    }
                }
                .proPaywall(isPresented: $showPaywall)
        }
    }

    private var card: some View {
        VStack(spacing: 14) {
            ZStack {
                Circle()
                    .fill(Theme.forest)
                    .frame(width: 64, height: 64)
                Image(systemName: "lock.fill")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundStyle(Color.white)
            }
            Label(L("Atala Pro"), systemImage: symbol)
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(Theme.green)
                .padding(.horizontal, 12)
                .frame(height: 28)
                .background(Theme.greenTint, in: Capsule())
            Text(title)
                .font(.system(size: 22, weight: .bold))
                .foregroundStyle(Theme.ink)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            Text(message)
                .font(.system(size: 15))
                .foregroundStyle(Theme.body)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            Button {
                Haptics.tap()
                showPaywall = true
            } label: {
                Text(buttonTitle)
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(Color.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: Theme.buttonHeight)
                    .background(Theme.forest, in: Capsule())
            }
            .buttonStyle(.plain)
            .padding(.top, 4)
        }
        .padding(22)
        .frame(maxWidth: 340)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .stroke(Theme.cardBorder, lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.12), radius: 24, x: 0, y: 12)
        .padding(.horizontal, 24)
    }
}

extension View {
    /// Locks the screen behind Atala Pro: blurred, with a padlock card and the paywall button.
    func proLocked(symbol: String, title: String, message: String,
                   buttonTitle: String = L("Unlock with Atala Pro")) -> some View {
        modifier(ProLockModifier(symbol: symbol, title: title, message: message, buttonTitle: buttonTitle))
    }
}
