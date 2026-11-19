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
                }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
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
    /// Not saved: a new process starts with the splash again.
    @State private var splashDone = false

    var body: some View {
        // Exercise pictures follow the chosen coach.
        let _ = (GuidedExerciseCoach.coachID = store.answers.coachID)
        ZStack {
            ZStack {
                if store.hasCompletedOnboarding {
                    MainTabView()
                        .transition(.opacity)
                } else {
                    OnboardingFlowView()
                        .transition(.opacity)
                }
            }
            .animation(.easeInOut(duration: 0.35), value: store.hasCompletedOnboarding)
            if !splashDone {
                // The splash fades itself out, then asks to be removed.
                AtalaSplashView(onFinished: { splashDone = true })
                    .zIndex(10)
            }
        }
        .authSyncPrompts()
    }
}
