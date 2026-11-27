import SwiftUI
import StoreKit

/// When the App Store rating is asked for: once, and only after real use of the app (guideline
/// 5.6.3: never on first launch or during onboarding). Also what the full-screen offers (gift, Pro welcome) read to stay behind it, so the order on
/// that launch is always the same: the rating request first, the offers afterwards.
@MainActor
enum ReviewPromptState {
    private static let key = "atala.review.asked"
    private static let firstSeenKey = "atala.review.firstSeen"
    /// Days the app must have been in use (counted from the first time the main screen showed).
    static let minimumDaysOfUse: Double = 4
    /// Sessions the user must have finished first: they know what the app is worth by then.
    static let minimumSessionsDone = 2
    /// Seconds the main screen stays on before the request.
    static let delay: Double = 2.5
    /// Seconds left to the request before a full-screen offer may show on the same launch.
    static let offerHold: Double = 8

    /// When the request was made, on this launch only.
    private(set) static var requestedAt: Date?
    /// True once this launch has decided: asked, or not asked this time.
    private(set) static var settled = false

    /// True for good once the rating was asked for on this phone.
    static var wasAsked: Bool {
        UserDefaults.standard.bool(forKey: key)
    }

    /// The first time the main screen showed on this phone; recorded on the first call.
    static func firstSeen(now: Date = Date()) -> Date {
        if let saved = UserDefaults.standard.object(forKey: firstSeenKey) as? Date { return saved }
        UserDefaults.standard.set(now, forKey: firstSeenKey)
        return now
    }

    /// True once the user has used the app long enough to have an opinion on it: several days
    /// after the first launch, and at least `minimumSessionsDone` sessions finished.
    static func hasEngaged(sessionsDone: Int, now: Date = Date()) -> Bool {
        let days = now.timeIntervalSince(firstSeen(now: now)) / 86_400
        return days >= minimumDaysOfUse && sessionsDone >= minimumSessionsDone
    }

    static func noteAsked() {
        UserDefaults.standard.set(true, forKey: key)
        requestedAt = Date()
        settled = true
    }

    /// Nothing was asked on this launch (something else was on screen): the next launch tries again.
    static func noteSkipped() {
        settled = true
    }

    /// Called by the full-screen offers before they show. Returns at once when the rating was asked
    /// on an earlier launch; otherwise waits for this launch's request (10 seconds at most), then
    /// leaves it `offerHold` seconds.
    static func waitBeforeOffer() async {
        var polls = 0
        while !wasAsked, !settled, polls < 20 {
            try? await Task.sleep(nanoseconds: 500_000_000)
            if Task.isCancelled { return }
            polls += 1
        }
        guard let asked = requestedAt else { return }
        let left: Double = offerHold - Date().timeIntervalSince(asked)
        if left > 0 {
            try? await Task.sleep(nanoseconds: UInt64(left * 1_000_000_000))
        }
    }
}

/// Asks for an App Store rating once the user has really used the app (see
/// `ReviewPromptState.hasEngaged`): never on the first launch, never right after onboarding.
/// Applied to the main tab view only; `isActive` is false while the launch splash is still showing.
/// The system decides whether its dialog really appears.
struct ReviewPrompt: ViewModifier {
    let isActive: Bool
    @EnvironmentObject private var store: AppStore
    @Environment(\.requestReview) private var requestReview

    func body(content: Content) -> some View {
        content
            .task(id: isActive) {
                await askWhenDue()
            }
    }

    @MainActor
    private func askWhenDue() async {
        guard isActive, !ReviewPromptState.wasAsked else { return }
        // Not enough use yet: nothing is asked, and the offers need not wait for it.
        let done = store.allWorkouts.filter { $0.status == .done }.count
        guard ReviewPromptState.hasEngaged(sessionsDone: done) else {
            ReviewPromptState.noteSkipped()
            return
        }
        try? await Task.sleep(nanoseconds: UInt64(ReviewPromptState.delay * 1_000_000_000))
        if Task.isCancelled { return }
        // Never on top of a sheet, a cover or an alert: the next launch asks instead.
        guard !GiftOfferPresenter.somethingIsPresented else {
            ReviewPromptState.noteSkipped()
            return
        }
        ReviewPromptState.noteAsked()
        requestReview()
    }
}
