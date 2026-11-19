import SwiftUI

@main
struct AtalaApp: App {
    @StateObject private var store = AppStore()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .preferredColorScheme(.light)
                .tint(Theme.ink)
                .onOpenURL { url in
                    AuthService.shared.handleCallback(url)
                }
                .task {
                    // Launch: catch up with the account (no-op for a guest, silent when offline).
                    SyncService.resume(store: store)
                    // Subscriptions: the account's identifier links purchases across devices.
                    let account = store.account
                    PurchaseService.shared.start(userID: account?.isGuest == false ? account?.userID : nil)
                }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                // A subscription can start, renew or end while the app is away.
                Task { await PurchaseService.shared.refresh() }
                store.refreshMissed()
                SyncService.resume(store: store)
            }
            if phase == .background {
                store.save()
                SyncService.pushBeforeSuspension(store: store)
            }
        }
    }
}

/// Onboarding until it is finished, then the app itself. The launch splash sits above either one
/// for its short animation, on every cold launch.
struct RootView: View {
    @EnvironmentObject private var store: AppStore
    @ObservedObject private var purchases = PurchaseService.shared
    @ObservedObject private var language = LanguageModel.shared
    @Environment(\.scenePhase) private var scenePhase
    /// Not saved: a new process starts with the splash again.
    @State private var splashDone = false

    /// Everything the reminders depend on: when one of these changes, they are scheduled again.
    private var reminderKey: String {
        // The sessions still to do and their days: a moved or finished session changes the key.
        var planned = 0
        var days = 0.0
        for week in store.plan?.weeks ?? [] {
            for workout in week.workouts where workout.status == .planned {
                planned += 1
                days += workout.date.timeIntervalSinceReferenceDate
            }
        }
        let plan = store.plan?.id.uuidString ?? "none"
        return "\(plan)|\(planned)|\(days)|\(store.answers.coachID)|\(purchases.isPro)|\(purchases.isInTrial)|\(store.hasCompletedOnboarding)|\(scenePhase == .active)"
    }

    var body: some View {
        // Exercise pictures follow the chosen coach.
        let _ = (GuidedExerciseCoach.coachID = store.coach.id)
        ZStack {
            // What shows through while two languages cross-fade.
            Color.white.ignoresSafeArea()
            ZStack {
                if store.hasCompletedOnboarding {
                    MainTabView()
                        // The rating request comes first, once the splash is gone; the gift
                        // and the Pro welcome wait behind it (see `ReviewPromptState`).
                        .modifier(ReviewPrompt(isActive: splashDone))
                        .modifier(GiftOfferPresenter())
                        .modifier(ProWelcomePresenter())
                        .transition(.opacity)
                } else {
                    OnboardingFlowView()
                        .transition(.opacity)
                }
            }
            .animation(.easeInOut(duration: 0.35), value: store.hasCompletedOnboarding)
            .environment(\.locale, AppLanguage.locale)
            .environment(\.layoutDirection, AppLanguage.isRightToLeft ? .rightToLeft : .leftToRight)
            // A new language rebuilds every screen, so each sentence is read again: the old
            // screens fade out while the new ones fade in. The splash is outside, it never replays.
            .id(language.code)
            .transition(.opacity)
            if !splashDone {
                // The splash fades itself out, then asks to be removed.
                AtalaSplashView(onFinished: { splashDone = true })
                    .zIndex(10)
            }
        }
        .animation(.easeInOut(duration: 0.3), value: language.code)
        .authSyncPrompts()
        .task(id: "\(store.coach.id)|\(language.code)|\(scenePhase == .active)") {
            // The coach's recorded voice in the app's language: downloaded once, then kept.
            VoicePack.prepare(coachID: store.coach.id)
        }
        .task(id: reminderKey) {
            await RunReminders.refresh(store: store)
        }
        .onChange(of: store.account?.userID) { _, _ in
            // Signing in links the purchases to the account straight away, not at the next launch.
            let account = store.account
            PurchaseService.shared.start(userID: account?.isGuest == false ? account?.userID : nil)
        }
    }
}
