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

/// The three things that can be bought. All of them unlock Atala Pro.
enum PurchasePlan: Hashable {
    /// Yearly, with a free trial: the main offer of the paywall.
    case yearly
    case monthly
    /// The welcome gift: yearly at half price, no free trial, offered once the paywall was declined.
    case gift
}

/// One plan as the store describes it for this user, in their own currency.
struct PurchaseOffer: Equatable {
    /// "€49.99"
    var price: String
    /// The same price per month ("€4.17"), when the store can work it out.
    var perMonth: String?
    /// The same price per week ("€0.48"), when the store can work it out.
    var perWeek: String?
    /// Length of the free trial in days, nil when the user is not offered one.
    var trialDays: Int?
    /// The price as a number, to compare plans.
    var amount: Decimal
}

/// Subscriptions through RevenueCat. The code compiles with or without the RevenueCat package:
/// until the package is added to the project (File, Add Package Dependencies,
/// https://github.com/RevenueCat/purchases-ios-spm), `canPurchase` stays false and the paywall
/// and the gift screen keep their placeholder behaviour.
@MainActor
final class PurchaseService: ObservableObject {
    static let shared = PurchaseService()

    /// Public SDK key of the RevenueCat app "Atala (iOS)". Public keys are meant to ship in the app.
    static let apiKey = "appl_bRCiZyoHTRfuxxtWZxouCvcdQru"
    /// Identifier of the entitlement that Atala Pro unlocks.
    static let entitlementID = "atala_pro"
    /// Identifier of the RevenueCat offering that holds the welcome gift.
    static let giftOfferingID = "gift"

    /// True while the user has an active Atala Pro subscription (trial included).
    @Published private(set) var isPro = false
    /// The plans loaded from the store. Empty until they are loaded, or without the package.
    @Published private(set) var offers: [PurchasePlan: PurchaseOffer] = [:]

    /// True once the yearly plan is loaded from the store and can be bought.
    var canPurchase: Bool { offers[.yearly] != nil }

    /// How much cheaper `plan` is than paying month by month for a year, in percent (58 for 58 %).
    func savingPercent(of plan: PurchasePlan) -> Int? {
        guard let yearly = offers[plan], let monthly = offers[.monthly], monthly.amount > 0 else { return nil }
        let fullYear = NSDecimalNumber(decimal: monthly.amount * 12).doubleValue
        let paid = NSDecimalNumber(decimal: yearly.amount).doubleValue
        guard fullYear > 0, paid < fullYear else { return nil }
        return Int(((1 - paid / fullYear) * 100).rounded())
    }

    private var started = false

    private static let placeholderKey = "atala.placeholder.pro"

    /// True in a build without the RevenueCat package: nothing can be bought, and the paywall's
    /// main button stands in for a purchase so the free and the Pro sides can both be tried.
    static var isPlaceholder: Bool {
        #if canImport(RevenueCat)
        return false
        #else
        return true
        #endif
    }

    private init() {
        if Self.isPlaceholder {
            isPro = UserDefaults.standard.bool(forKey: Self.placeholderKey)
        }
    }

    /// Placeholder builds only: pretends the subscription was bought (or not), on this phone.
    func setPlaceholderPro(_ active: Bool) {
        guard Self.isPlaceholder else { return }
        UserDefaults.standard.set(active, forKey: Self.placeholderKey)
        isPro = active
    }

    /// Starts the SDK once, then loads the offers and the subscription state.
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

    /// Reloads the plans and whether Atala Pro is active.
    func refresh() async {
        #if canImport(RevenueCat)
        guard started else { return }
        if let info = try? await Purchases.shared.customerInfo() {
            isPro = info.entitlements[Self.entitlementID]?.isActive == true
        }
        guard let offerings = try? await Purchases.shared.offerings() else { return }
        var loaded: [PurchasePlan: PurchaseOffer] = [:]
        if let package = offerings.current?.annual { loaded[.yearly] = await Self.offer(from: package) }
        if let package = offerings.current?.monthly { loaded[.monthly] = await Self.offer(from: package) }
        if let package = offerings.offering(identifier: Self.giftOfferingID)?.annual {
            loaded[.gift] = await Self.offer(from: package)
        }
        offers = loaded
        #endif
    }

    /// Buys a plan (the free trial starts when the plan has one and the user is eligible).
    func purchase(_ plan: PurchasePlan) async -> PurchaseOutcome {
        #if canImport(RevenueCat)
        guard started,
              let offerings = try? await Purchases.shared.offerings(),
              let package = Self.package(for: plan, in: offerings) else {
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
    private static func package(for plan: PurchasePlan, in offerings: Offerings) -> Package? {
        switch plan {
        case .yearly: return offerings.current?.annual
        case .monthly: return offerings.current?.monthly
        case .gift: return offerings.offering(identifier: giftOfferingID)?.annual
        }
    }

    /// The free trial is only announced to people the store says can still have it.
    private static func offer(from package: Package) async -> PurchaseOffer {
        let product = package.storeProduct
        var trial: Int?
        if let intro = product.introductoryDiscount, intro.paymentMode == .freeTrial {
            let status = await Purchases.shared.checkTrialOrIntroDiscountEligibility(product: product)
            if status == .eligible { trial = days(in: intro.subscriptionPeriod) }
        }
        return PurchaseOffer(price: product.localizedPriceString,
                             perMonth: product.localizedPricePerMonth,
                             perWeek: product.localizedPricePerWeek,
                             trialDays: trial,
                             amount: product.price)
    }

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
