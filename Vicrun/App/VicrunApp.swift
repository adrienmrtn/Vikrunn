import SwiftUI

@main
struct VicrunApp: App {
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
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { store.refreshMissed() }
            if phase == .background {
                store.save()
                Task { await SyncService.pushAll(store: store) }
            }
        }
    }
}

/// Onboarding until it is finished, then the app itself.
struct RootView: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
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
    }
}
