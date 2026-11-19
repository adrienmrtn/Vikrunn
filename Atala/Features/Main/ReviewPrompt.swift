import SwiftUI
import StoreKit

/// When the App Store rating is asked for: once, a moment after the user has arrived in the app.
/// Also what the full-screen offers (gift, Pro welcome) read to stay behind it, so the order on
/// that launch is always the same: the rating request first, the offers afterwards.
@MainActor
enum ReviewPromptState {
    private static let key = "atala.review.asked"
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

/// Asks for an App Store rating the first time the main app has been on screen for a moment.
/// Applied to the main tab view only, so never during onboarding; `isActive` is false while the
/// launch splash is still showing. The system decides whether its dialog really appears.
struct ReviewPrompt: ViewModifier {
    let isActive: Bool
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
