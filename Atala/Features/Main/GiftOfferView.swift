import SwiftUI
import UIKit

/// When the welcome gift is shown: to people who are not subscribed, shortly after the app opens,
/// once a day at most and three times in all. Nothing is saved to the account: it is a property
/// of this phone.
enum GiftOfferSchedule {
    private static let countKey = "atala.gift.shownCount"
    private static let lastKey = "atala.gift.lastShown"
    static let maxShows = 3
    private static let minInterval: TimeInterval = 24 * 3600
    /// Seconds after the main screen appears: the launch splash and the entrance are over by then.
    static let delay: Double = 4

    static func isDue(now: Date = Date()) -> Bool {
        let defaults = UserDefaults.standard
        guard defaults.integer(forKey: countKey) < maxShows else { return false }
        let last = defaults.double(forKey: lastKey)
        return last == 0 || now.timeIntervalSince1970 - last >= minInterval
    }

    static func noteShown(now: Date = Date()) {
        let defaults = UserDefaults.standard
        defaults.set(defaults.integer(forKey: countKey) + 1, forKey: countKey)
        defaults.set(now.timeIntervalSince1970, forKey: lastKey)
    }
}

/// Presents the gift over the main screen when it is due.
struct GiftOfferPresenter: ViewModifier {
    @EnvironmentObject private var store: AppStore
    @ObservedObject private var purchases = PurchaseService.shared
    @State private var shown = false

    func body(content: Content) -> some View {
        content
            .fullScreenCover(isPresented: $shown) {
                GiftOfferView()
                    .environmentObject(store)
            }
            .task {
                try? await Task.sleep(nanoseconds: UInt64(GiftOfferSchedule.delay * 1_000_000_000))
                if Task.isCancelled { return }
                // The subscription state may still be loading at launch: ask once more first.
                await purchases.refresh()
                guard !purchases.isPro, GiftOfferSchedule.isDue() else { return }
                // With the store connected, only offer the gift when the store really has it.
                guard !purchases.canPurchase || purchases.offers[.gift] != nil else { return }
                // Never on top of a sheet, a run or an alert: it waits for the next launch instead.
                guard !Self.somethingIsPresented else { return }
                GiftOfferSchedule.noteShown()
                shown = true
            }
    }
}

extension GiftOfferPresenter {
    /// True while a sheet, a full-screen cover or an alert is already on screen.
    @MainActor
    static var somethingIsPresented: Bool {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let window = scenes.flatMap { $0.windows }.first { $0.isKeyWindow }
        return window?.rootViewController?.presentedViewController != nil
    }
}

/// "We have a gift for you": the yearly plan at half price, with no free trial. The box drops in,
/// shakes, opens in a burst, then the offer appears.
struct GiftOfferView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @ObservedObject private var purchases = PurchaseService.shared

    /// 0 nothing, 1 the box is there, 2 it shakes, 3 it has opened, 4 the offer and the buttons.
    @State private var stage = 0
    @State private var isBuying = false
    @State private var errorText: String?

    // Store values when the gift is loaded, the placeholder ones otherwise.
    private var gift: PurchaseOffer? { purchases.offers[.gift] }
    private var weeklyPrice: String { gift?.perWeek ?? OnboardingPaywallOffer.giftWeeklyPrice }
    private var yearlyPrice: String { gift?.price ?? OnboardingPaywallOffer.giftYearlyPrice }
    private var fullPrice: String { purchases.offers[.yearly]?.price ?? OnboardingPaywallOffer.yearlyPrice }

    /// Rounded down to a multiple of five, so the badge never promises more than the real discount.
    /// Zero when the gift is not cheaper than the yearly plan: the badge is then left out.
    private var discountPercent: Int {
        guard let gift, let full = purchases.offers[.yearly], full.amount > 0 else { return 50 }
        let ratio = NSDecimalNumber(decimal: gift.amount).doubleValue / NSDecimalNumber(decimal: full.amount).doubleValue
        let percent = Int(((1 - ratio) * 100).rounded(.down))
        return max(0, percent / 5 * 5)
    }

    private var errorShown: Binding<Bool> {
        Binding(
            get: { errorText != nil },
            set: { shown in
                if !shown { errorText = nil }
            }
        )
    }

    var body: some View {
        ZStack {
            LinearGradient(colors: [Theme.green, Theme.forest], startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea()
            VStack(spacing: 0) {
                HStack {
                    Spacer()
                    Button(action: close) {
                        Image(systemName: "xmark")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(Color.white.opacity(0.85))
                            .frame(width: 32, height: 32)
                            .background(Color.white.opacity(0.16), in: Circle())
                            .frame(width: 44, height: 44)
                            .contentShape(Rectangle())
                    }
                    .accessibilityLabel(L("Close"))
                    .opacity(stage >= 4 ? 1 : 0)
                }
                .padding(.horizontal, 8)
                Spacer(minLength: 12)
                box
                Spacer(minLength: 20)
                offer
                    .opacity(stage >= 4 ? 1 : 0)
                    .offset(y: stage >= 4 ? 0 : 18)
                Spacer(minLength: 20)
                buttons
                    .opacity(stage >= 4 ? 1 : 0)
            }
        }
        .task {
            await play()
        }
        .alert(L("We couldn't complete that"), isPresented: errorShown) {
            Button(L("OK"), role: .cancel) {}
        } message: {
            Text(errorText ?? L("Please try again."))
        }
    }

    // MARK: The box

    private var box: some View {
        ZStack {
            // Rays behind the box once it is open.
            ForEach(0..<12, id: \.self) { index in
                Capsule()
                    .fill(Theme.yellow.opacity(0.85))
                    .frame(width: 5, height: stage >= 3 ? 26 : 4)
                    .offset(y: stage >= 3 ? -118 : -70)
                    .rotationEffect(.degrees(Double(index) * 30))
                    .opacity(stage >= 3 ? 1 : 0)
            }
            Circle()
                .fill(Color.white.opacity(0.12))
                .frame(width: 190, height: 190)
                .scaleEffect(stage >= 3 ? 1 : 0.7)
            Image(systemName: "gift.fill")
                .font(.system(size: 92, weight: .semibold))
                .foregroundStyle(Theme.yellow)
                .shadow(color: .black.opacity(0.25), radius: 14, x: 0, y: 10)
                .rotationEffect(.degrees(stage == 2 ? 9 : 0))
                .scaleEffect(stage == 0 ? 0.2 : (stage >= 3 ? 1.12 : 1))
                .opacity(stage == 0 ? 0 : 1)
        }
        .frame(height: 260)
        .accessibilityHidden(true)
    }

    // MARK: The offer

    private var offer: some View {
        VStack(spacing: 14) {
            Text(L("We have a gift for you, %@", store.firstName))
                .font(.system(size: 28, weight: .bold))
                .foregroundStyle(Color.white)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            if discountPercent > 0 {
                Text(L("%@% OFF", discountPercent))
                    .font(.system(size: 13, weight: .bold))
                    .kerning(1)
                    .foregroundStyle(Theme.forest)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 7)
                    .background(Theme.yellow, in: Capsule())
            }
            VStack(spacing: 4) {
                Text(L("Access your full plan for only"))
                    .font(.system(size: 17))
                    .foregroundStyle(Theme.greenOnDark)
                (Text(weeklyPrice).font(.system(size: 46, weight: .bold))
                    + Text(L(" /week")).font(.system(size: 20, weight: .semibold)))
                    .foregroundColor(Color.white)
                (Text(L("Billed %@ a year, instead of ", yearlyPrice))
                    + Text(fullPrice).strikethrough()
                    + Text("."))
                    .font(.system(size: 15))
                    .foregroundColor(Theme.greenOnDark)
            }
            .multilineTextAlignment(.center)
        }
        .padding(.horizontal, 28)
    }

    private var buttons: some View {
        VStack(spacing: 6) {
            Button(action: claim) {
                Text(isBuying ? L("One moment…") : L("Claim my gift"))
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(Theme.forest)
                    .frame(maxWidth: .infinity)
                    .frame(height: 58)
                    .background(Color.white, in: Capsule())
            }
            .buttonStyle(.plain)
            .disabled(isBuying)
            Button(L("No thanks"), action: close)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Color.white.opacity(0.85))
                .frame(height: 44)
                .disabled(isBuying)
            Text(gift != nil
                 ? L("Yearly subscription, no free trial. Renews automatically, cancel any time.")
                 : L("Price shown as an example. Nothing is charged in this version."))
                .font(.system(size: 12))
                .foregroundStyle(Theme.greenOnDark)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, Theme.screenPadding)
        .padding(.bottom, 10)
    }

    // MARK: Actions

    private func close() {
        Haptics.tap()
        dismiss()
    }

    /// Buys the gift when the store has it; otherwise closes, as a placeholder.
    private func claim() {
        Haptics.confirm()
        if PurchaseService.isPlaceholder {
            // No store in this build: the button stands in for the purchase.
            purchases.setPlaceholderPro(true)
            dismiss()
            return
        }
        guard !isBuying else { return }
        isBuying = true
        Task { @MainActor in
            let outcome = await purchases.purchase(.gift)
            isBuying = false
            switch outcome {
            case .purchased:
                Haptics.success()
                dismiss()
            case .cancelled:
                break
            case .failed(let message):
                errorText = message
            }
        }
    }

    private static func wait(_ seconds: Double) async {
        try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
    }

    @MainActor
    private func play() async {
        guard stage == 0 else { return }
        if reduceMotion {
            stage = 4
            return
        }
        withAnimation(.spring(response: 0.5, dampingFraction: 0.55)) { stage = 1 }
        await Self.wait(0.7)
        Haptics.soft()
        withAnimation(.easeInOut(duration: 0.11).repeatCount(5, autoreverses: true)) { stage = 2 }
        await Self.wait(0.7)
        Haptics.success()
        withAnimation(.spring(response: 0.45, dampingFraction: 0.5)) { stage = 3 }
        await Self.wait(0.45)
        withAnimation(.easeOut(duration: 0.45)) { stage = 4 }
    }
}

#Preview {
    GiftOfferView()
        .environmentObject(AppStore.preview)
}
