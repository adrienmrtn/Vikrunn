import Foundation
#if canImport(RevenueCat)
import RevenueCat
#endif

/// What happened when the user tried to buy.
enum PurchaseOutcome: Equatable {
    case purchased
    case cancelled
    case failed(String)
}

/// Subscriptions through RevenueCat. The code compiles with or without the RevenueCat package:
/// until the package is added to the project (File, Add Package Dependencies,
/// https://github.com/RevenueCat/purchases-ios-spm), `canPurchase` stays false and the paywall
/// keeps its placeholder behaviour.
@MainActor
final class PurchaseService: ObservableObject {
    static let shared = PurchaseService()

    /// Public SDK key of the RevenueCat app "Atala (iOS)". Public keys are meant to ship in the app.
    static let apiKey = "appl_bRCiZyoHTRfuxxtWZxouCvcdQru"
    /// Identifier of the entitlement that Atala Pro unlocks.
    static let entitlementID = "atala_pro"

    /// True while the user has an active Atala Pro subscription (trial included).
    @Published private(set) var isPro = false
    /// Price of the yearly plan in the user's own currency, as the store gives it ("€49.99").
    @Published private(set) var yearlyPrice: String?
    /// The same price per month ("€4.17"), when the store can work it out.
    @Published private(set) var monthlyPrice: String?
    /// Length of the free trial in days, nil when the user is not offered one.
    @Published private(set) var trialDays: Int?
    /// True once the yearly plan is loaded from the store and can be bought.
    @Published private(set) var canPurchase = false

    private var started = false

    private init() {}

    /// Starts the SDK once, then loads the offer and the subscription state.
    /// `userID` is the account's identifier, nil for a guest.
    func start(userID: String?) {
        #if canImport(RevenueCat)
        if !started {
            started = true
            Purchases.configure(withAPIKey: Self.apiKey)
        }
        Task { @MainActor in
            if let userID, !userID.isEmpty, Purchases.shared.appUserID != userID {
                _ = try? await Purchases.shared.logIn(userID)
            }
            await refresh()
        }
        #endif
    }

    /// Reloads the yearly plan and whether Atala Pro is active.
    func refresh() async {
        #if canImport(RevenueCat)
        guard started else { return }
        if let info = try? await Purchases.shared.customerInfo() {
            isPro = info.entitlements[Self.entitlementID]?.isActive == true
        }
        if let offerings = try? await Purchases.shared.offerings(),
           let package = offerings.current?.annual {
            let product = package.storeProduct
            yearlyPrice = product.localizedPriceString
            monthlyPrice = product.localizedPricePerMonth
            if let intro = product.introductoryDiscount, intro.paymentMode == .freeTrial {
                trialDays = Self.days(in: intro.subscriptionPeriod)
            } else {
                trialDays = nil
            }
            canPurchase = true
        } else {
            canPurchase = false
        }
        #endif
    }

    /// Buys the yearly plan (the free trial starts when the user is eligible).
    func purchaseYearly() async -> PurchaseOutcome {
        #if canImport(RevenueCat)
        guard started,
              let offerings = try? await Purchases.shared.offerings(),
              let package = offerings.current?.annual else {
            return .failed("The subscription is not available right now. Please try again in a moment.")
        }
        do {
            let result = try await Purchases.shared.purchase(package: package)
            if result.userCancelled { return .cancelled }
            isPro = result.customerInfo.entitlements[Self.entitlementID]?.isActive == true
            return isPro ? .purchased : .failed("The purchase went through but Atala Pro is not active yet. Tap Restore purchases in a moment.")
        } catch {
            return .failed(error.localizedDescription)
        }
        #else
        return .failed("Subscriptions are not available in this build.")
        #endif
    }

    /// Brings back a subscription bought earlier with the same Apple Account. True when Pro is active.
    func restore() async -> Bool {
        #if canImport(RevenueCat)
        guard started else { return false }
        if let info = try? await Purchases.shared.restorePurchases() {
            isPro = info.entitlements[Self.entitlementID]?.isActive == true
        }
        return isPro
        #else
        return false
        #endif
    }

    #if canImport(RevenueCat)
    private static func days(in period: SubscriptionPeriod) -> Int {
        switch period.unit {
        case .day: return period.value
        case .week: return period.value * 7
        case .month: return period.value * 30
        case .year: return period.value * 365
        @unknown default: return period.value
        }
    }
    #endif
}
