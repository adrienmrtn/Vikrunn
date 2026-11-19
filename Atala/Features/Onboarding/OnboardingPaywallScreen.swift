import SwiftUI

/// A strip of running photos gliding from right to left, endlessly. Still with Reduce Motion.
struct OnboardingPhotoStrip: View {
    let names: [String]
    var tileWidth: CGFloat = 132
    var height: CGFloat = 176
    /// Points per second.
    var speed: Double = 22

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private static let spacing: CGFloat = 10

    /// Enough tiles to cover any phone twice over, so the loop never shows a gap.
    private var tiles: [String] {
        guard !names.isEmpty else { return [] }
        var list: [String] = []
        while list.count < 8 { list.append(contentsOf: names) }
        return list
    }

    var body: some View {
        let list = tiles
        let loop = Double(CGFloat(list.count) * (tileWidth + Self.spacing))
        // The strip is an overlay of a screen-wide box, so its own width never widens the page.
        return Color.clear
            .frame(maxWidth: .infinity)
            .frame(height: height)
            .overlay(alignment: .leading) {
                TimelineView(.animation(paused: reduceMotion)) { timeline in
                    let elapsed = timeline.date.timeIntervalSinceReferenceDate
                    let shift = loop > 0 ? CGFloat((elapsed * speed).truncatingRemainder(dividingBy: loop)) : 0
                    HStack(spacing: Self.spacing) {
                        // Twice the list: when the first copy has left, the second one is exactly in its place.
                        ForEach(0..<(list.count * 2), id: \.self) { index in
                            tile(list[index % list.count])
                        }
                    }
                    .fixedSize()
                    .offset(x: -shift)
                }
            }
            .clipped()
            .accessibilityHidden(true)
    }

    private func tile(_ name: String) -> some View {
        Color(hex: 0x1B1B1B)
            .frame(width: tileWidth, height: height)
            .overlay {
                Image(name)
                    .resizable()
                    .scaledToFill()
            }
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
}

private struct OnboardingPaywallFeature: Identifiable {
    let id: Int
    let symbol: String
    let title: String
    let detail: String
}

/// The paywall, used at the end of the entry flow and wherever the app asks for Atala Pro. With the
/// RevenueCat package in the project, the main button buys the chosen plan and the prices come
/// from the store; without it, the prices are examples and the button stands in for the purchase.
struct PaywallView: View {
    /// Seconds before the close cross appears (0 inside the app, a few seconds in onboarding).
    var closeDelay: Double = 0
    /// Called when the user subscribed, restored a subscription or closed the screen.
    let onFinished: () -> Void

    init(closeDelay: Double = 0, onFinished: @escaping () -> Void) {
        self.closeDelay = closeDelay
        self.onFinished = onFinished
    }

    @EnvironmentObject private var store: AppStore
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var closeShown = false
    @ObservedObject private var purchases = PurchaseService.shared
    @State private var isBuying = false
    @State private var purchaseError: String?

    /// The plan the main button buys. Yearly is chosen from the start.
    @State private var plan: PurchasePlan = .yearly

    // Store values when the offers are loaded, the placeholder ones otherwise.
    private var trialDays: Int? {
        purchases.canPurchase ? purchases.offers[.yearly]?.trialDays : OnboardingPaywallOffer.trialDays
    }
    private var yearlyPerMonth: String {
        purchases.offers[.yearly]?.perMonth ?? OnboardingPaywallOffer.monthlyPrice
    }
    private var yearlyPrice: String { purchases.offers[.yearly]?.price ?? OnboardingPaywallOffer.yearlyPrice }
    private var monthPrice: String { purchases.offers[.monthly]?.price ?? OnboardingPaywallOffer.monthPrice }
    private var yearlySaving: Int? {
        purchases.canPurchase ? purchases.savingPercent(of: .yearly) : OnboardingPaywallOffer.yearlySaving
    }
    /// The monthly plan is offered when the store has it, and always in the placeholder.
    private var showsMonthly: Bool { !purchases.canPurchase || purchases.offers[.monthly] != nil }
    private var startsWithTrial: Bool { plan == .yearly && trialDays != nil }

    private var purchaseErrorShown: Binding<Bool> {
        Binding(
            get: { purchaseError != nil },
            set: { shown in
                if !shown { purchaseError = nil }
            }
        )
    }

    /// Buys the chosen plan when the store is ready; otherwise moves on, as the placeholder did.
    private func startTrial() {
        Haptics.confirm()
        if PurchaseService.isPlaceholder {
            // No store in this build: the button stands in for the purchase.
            purchases.setPlaceholderPro(true)
            onFinished()
            return
        }
        guard !isBuying else { return }
        isBuying = true
        Task { @MainActor in
            let outcome = await purchases.purchase(plan)
            isBuying = false
            switch outcome {
            case .purchased:
                Haptics.success()
                onFinished()
            case .cancelled:
                break
            case .failed(let message):
                purchaseError = message
            }
        }
    }

    private func restorePurchases() {
        guard !isBuying else { return }
        isBuying = true
        Task { @MainActor in
            let restored = await purchases.restore()
            isBuying = false
            if restored {
                onFinished()
            } else {
                purchaseError = "No active Atala Pro subscription was found for this Apple Account."
            }
        }
    }


    private var features: [OnboardingPaywallFeature] {
        let weeks = store.plan?.weeks.count ?? PlanEngine.maxWeeks
        return [
            OnboardingPaywallFeature(id: 0, symbol: "calendar",
                                     title: "Your personal plan",
                                     detail: "\(OnboardingCopy.weeks(weeks)) built around your days and your level"),
            OnboardingPaywallFeature(id: 1, symbol: "bubble.left.and.bubble.right.fill",
                                     title: "\(store.coach.name), your coach, 24/7",
                                     detail: "Ask anything, move a session, lighten a week"),
            OnboardingPaywallFeature(id: 2, symbol: "waveform",
                                     title: "Voice guidance on every run",
                                     detail: "Your pace and your intervals, in your ears"),
            OnboardingPaywallFeature(id: 3, symbol: "figure.cooldown",
                                     title: "Warm-ups, strength and stretching",
                                     detail: "Guided sessions to help you stay injury-free"),
            OnboardingPaywallFeature(id: 4, symbol: "chart.line.uptrend.xyaxis",
                                     title: "Progress you can see",
                                     detail: "Splits, elevation, personal bests and streaks"),
        ]
    }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    OnboardingPhotoStrip(names: AtalaPhotos.strip(for: store.answers.gender))
                        .padding(.top, 52)
                    headline
                        .padding(.horizontal, Theme.screenPadding)
                        .padding(.top, 22)
                    featureList
                        .padding(.horizontal, Theme.screenPadding)
                        .padding(.top, 20)
                    offerCard
                        .padding(.horizontal, Theme.screenPadding)
                        .padding(.top, 20)
                        .padding(.bottom, 20)
                }
            }
            .scrollIndicators(.hidden)
            .scrollBounceBehavior(.basedOnSize)
            callToAction
        }
        .overlay(alignment: .top) {
            topBar
        }
        .background(Color.white.ignoresSafeArea())
        .task {
            await revealClose()
        }
        .task {
            await purchases.refresh()
        }
        .onChange(of: showsMonthly) { _, offered in
            // The monthly card can go away once the real offers load: fall back to the yearly plan.
            if !offered { plan = .yearly }
        }
        .alert("We couldn't complete that", isPresented: purchaseErrorShown) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(purchaseError ?? "Please try again.")
        }
    }

    /// Logo on the left, close cross on the right (hidden for the first seconds).
    private var topBar: some View {
        HStack {
            BrandLogo(style: .full, height: 20)
                .foregroundStyle(Theme.forest)
                .padding(.leading, 12)
            Spacer()
            Button(action: {
                Haptics.tap()
                onFinished()
            }) {
                Image(systemName: "xmark")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Theme.muted)
                    .frame(width: 32, height: 32)
                    .background(Theme.surface, in: Circle())
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel("Close")
            .opacity(closeShown ? 1 : 0)
            .allowsHitTesting(closeShown)
            .accessibilityHidden(!closeShown)
        }
        .padding(.horizontal, 8)
        .frame(height: 48)
        .background(Color.white)
    }

    private var headline: some View {
        VStack(alignment: .leading, spacing: 8) {
            (Text("\(store.firstName), finally get the ")
                + Text("guidance").foregroundColor(Theme.green)
                + Text(" you've been looking for."))
                .font(.system(size: 28, weight: .bold))
                .foregroundColor(Theme.ink)
                .lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            Text(OnboardingPaywallCopy.goalLine(answers: store.answers, plan: store.plan))
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Theme.green)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var featureList: some View {
        VStack(alignment: .leading, spacing: 14) {
            ForEach(features) { feature in
                HStack(alignment: .center, spacing: 14) {
                    IconTile(systemName: feature.symbol, size: 44)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(feature.title)
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(Theme.ink)
                        Text(feature.detail)
                            .font(.system(size: 13))
                            .foregroundStyle(Theme.muted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 0)
                }
                .accessibilityElement(children: .combine)
            }
        }
    }

    /// The plans to choose from: yearly first and selected, monthly under it.
    private var offerCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            planCard(.yearly,
                     title: "Yearly",
                     price: "\(yearlyPerMonth) a month",
                     detail: "\(yearlyPrice) billed yearly",
                     badge: trialDays.map { "\($0) DAYS FREE" },
                     saving: yearlySaving.map { "Save \($0)%" })
            if showsMonthly {
                planCard(.monthly,
                         title: "Monthly",
                         price: "\(monthPrice) a month",
                         detail: "Billed every month, no free trial",
                         badge: nil,
                         saving: nil)
            }
            Text(startsWithTrial
                 ? "Cancel any time before the trial ends and you pay nothing."
                 : "Renews automatically. Cancel any time in your App Store settings.")
                .font(.system(size: 13))
                .foregroundStyle(Theme.body)
                .fixedSize(horizontal: false, vertical: true)
            if !purchases.canPurchase {
                Text("Prices shown as an example. Nothing is charged in this version.")
                    .font(.system(size: 12))
                    .foregroundStyle(Theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func planCard(_ option: PurchasePlan, title: String, price: String, detail: String,
                          badge: String?, saving: String?) -> some View {
        let selected = plan == option
        return Button {
            Haptics.select()
            plan = option
        } label: {
            HStack(alignment: .center, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Text(title)
                            .font(.system(size: 15, weight: .bold))
                            .foregroundStyle(Theme.ink)
                        if let badge {
                            Text(badge)
                                .font(.system(size: 11, weight: .bold))
                                .kerning(0.6)
                                .foregroundStyle(Color.white)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Theme.green, in: Capsule())
                        }
                        if let saving {
                            Text(saving)
                                .font(.system(size: 11, weight: .bold))
                                .foregroundStyle(Theme.orangeDeep)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Theme.orangeTint, in: Capsule())
                        }
                    }
                    Text(price)
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(Theme.ink)
                    Text(detail)
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.muted)
                }
                Spacer(minLength: 8)
                SelectionMark(selected: selected)
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(selected ? Theme.greenTint : Color.white,
                        in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .stroke(selected ? Theme.green : Theme.controlBorder, lineWidth: selected ? 1.5 : 1)
            )
            .contentShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private var callToAction: some View {
        VStack(spacing: 10) {
            Button(action: startTrial) {
                VStack(spacing: 2) {
                    Text(isBuying ? "One moment…" : (startsWithTrial ? "Start my free trial" : "Subscribe"))
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(Color.white)
                    Text("Easy to cancel, at any time")
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.greenOnDark)
                }
                .frame(maxWidth: .infinity)
                .frame(height: 64)
                .background(Theme.green, in: Capsule())
            }
            .buttonStyle(.plain)
            .disabled(isBuying)
            HStack(spacing: 6) {
                Image(systemName: "lock.fill")
                    .font(.system(size: 11))
                Text("Secured by the App Store")
                    .font(.system(size: 12))
                if purchases.canPurchase {
                    Text("·")
                        .font(.system(size: 12))
                    Button("Restore purchases", action: restorePurchases)
                        .font(.system(size: 12, weight: .semibold))
                        .disabled(isBuying)
                }
            }
            .foregroundStyle(Theme.muted)
            // Apple asks for both links next to the price of a subscription.
            Text("[Terms of use](https://vikstudios.com/atala/terms) · [Privacy policy](https://vikstudios.com/atala)")
                .font(.system(size: 12))
                .foregroundStyle(Theme.muted)
                .tint(Theme.ink)
        }
        .padding(.horizontal, Theme.screenPadding)
        .padding(.top, 12)
        .padding(.bottom, 8)
        .background(Color.white)
        .overlay(alignment: .top) {
            Rectangle().fill(Theme.hairline).frame(height: 1)
        }
    }

    /// The delay is a rule of the screen, so it is kept with Reduce Motion; only the fade is dropped.
    @MainActor
    private func revealClose() async {
        guard !closeShown else { return }
        try? await Task.sleep(nanoseconds: UInt64(closeDelay * 1_000_000_000))
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

/// Screen 31 of the entry flow: the paywall, with its close cross held back for a few seconds.
struct OnboardingPaywallScreen: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var nav: OnboardingNavigator

    var body: some View {
        PaywallView(closeDelay: 5) {
            nav.next(store)
        }
        .onAppear {
            // Placeholder builds: coming back to the paywall starts from "not subscribed" again.
            PurchaseService.shared.setPlaceholderPro(false)
        }
    }
}

/// Presents the paywall over a screen of the app when a Pro feature is asked for.
struct ProPaywallModifier: ViewModifier {
    @Binding var isPresented: Bool
    @EnvironmentObject private var store: AppStore

    func body(content: Content) -> some View {
        content.fullScreenCover(isPresented: $isPresented) {
            PaywallView {
                isPresented = false
            }
            .environmentObject(store)
        }
    }
}

extension View {
    func proPaywall(isPresented: Binding<Bool>) -> some View {
        modifier(ProPaywallModifier(isPresented: isPresented))
    }
}

#Preview("Paywall") {
    PaywallView(closeDelay: 5, onFinished: {})
        .environmentObject(AppStore.preview)
}
