import SwiftUI

/// Small "Restoring your plan…" state shown over the whole app while the account is being read
/// right after sign-in.
struct AuthRestoreOverlay: View {
    var body: some View {
        ZStack {
            Color.white.opacity(0.92)
                .ignoresSafeArea()
            VStack(spacing: 14) {
                SwiftUI.ProgressView()
                    .controlSize(.large)
                    .tint(Theme.green)
                Text("Restoring your plan…")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                Text("Looking for what you saved in your account.")
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.muted)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal, 32)
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isModal)
    }
}

/// App-wide account states: the restoring overlay, and the choice between the phone's plan and the
/// account's when both exist after a sign-in.
struct AuthSyncPrompts: ViewModifier {
    @EnvironmentObject private var store: AppStore
    @State private var resolving = false
    @State private var errorMessage: String?

    func body(content: Content) -> some View {
        content
            .overlay {
                if store.syncActivity == .restoring || resolving {
                    AuthRestoreOverlay()
                        .transition(.opacity)
                }
            }
            .animation(.easeInOut(duration: 0.2), value: store.syncActivity)
            .alert("Which plan do you want to keep?", isPresented: choiceShown) {
                Button("Use my saved plan") { resolve(keepPhone: false) }
                Button("Use this phone's plan") { resolve(keepPhone: true) }
            } message: {
                Text("Your account already has a plan. The one you keep replaces the other. Your runs from both are kept.")
            }
            .alert("We couldn't finish signing you in", isPresented: errorShown) {
                Button("Try again") { store.syncChoicePending = true }
            } message: {
                Text(errorMessage ?? "Please try again.")
            }
    }

    /// The alert closes itself when a button is tapped; the flag is cleared once the choice is applied.
    private var choiceShown: Binding<Bool> {
        Binding(
            get: { store.syncChoicePending && !resolving && errorMessage == nil },
            set: { _ in }
        )
    }

    private var errorShown: Binding<Bool> {
        Binding(
            get: { errorMessage != nil },
            set: { shown in
                if !shown { errorMessage = nil }
            }
        )
    }

    private func resolve(keepPhone: Bool) {
        guard !resolving else { return }
        resolving = true
        Task { @MainActor in
            let outcome = await SyncService.resolveChoice(keepPhone: keepPhone, store: store)
            resolving = false
            if case .failed(let failure) = outcome {
                errorMessage = failure.userMessage
            }
        }
    }
}

extension View {
    /// Adds the restoring overlay and the "which plan do you keep" question to the root of the app.
    func authSyncPrompts() -> some View {
        modifier(AuthSyncPrompts())
    }
}

/// What a screen does after `SignInOptionsSheet` returned an account: guests are kept as they are,
/// real accounts are matched with the phone first (restore, upload, or ask).
@MainActor
enum AuthSignInFlow {
    static func complete(_ account: Account, store: AppStore,
                         policy: SyncSignInPolicy) async -> SyncSignInOutcome {
        if account.isGuest {
            // Nothing leaves the phone. A guest never replaces a real account.
            if !store.hasRealAccount { store.account = account }
            return .continued
        }
        AuthService.shared.noteSignedIn()
        return await SyncService.completeSignIn(account, store: store, policy: policy)
    }
}
