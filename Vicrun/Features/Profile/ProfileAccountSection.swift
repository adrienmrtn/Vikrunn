import SwiftUI

/// Account: who is signed in, sign in, sign out and account deletion.
struct ProfileAccountSection: View {
    @EnvironmentObject private var store: AppStore
    @State private var showSignIn = false
    @State private var confirmDelete = false
    @State private var busy = false
    @State private var errorMessage: String?

    var body: some View {
        Section {
            HStack {
                Text(isSignedIn ? "Signed in as" : "Account")
                Spacer()
                Text(statusText)
                    .foregroundStyle(Theme.muted)
                    .lineLimit(1)
                    .truncationMode(.middle)
            }
            .accessibilityElement(children: .combine)
            .alert("Something went wrong", isPresented: errorShown) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(errorMessage ?? "Please try again.")
            }
            if !isSignedIn {
                Button("Sign in") { showSignIn = true }
                    .sheet(isPresented: $showSignIn) {
                        SignInOptionsSheet { account in
                            store.account = account
                            showSignIn = false
                        }
                        .environmentObject(store)
                    }
            }
            if store.account != nil {
                Button("Sign out") { signOut() }
                    .disabled(busy)
            }
            Button("Delete my account", role: .destructive) { confirmDelete = true }
                .disabled(busy)
                .alert("Delete your account?", isPresented: $confirmDelete) {
                    Button("Delete everything", role: .destructive) { deleteAccount() }
                    Button("Cancel", role: .cancel) {}
                } message: {
                    Text("Your account, your plan, your runs and your conversations with your coach are permanently deleted. This cannot be undone.")
                }
        } header: {
            Text("Account")
        } footer: {
            Text(footerText)
        }
    }

    /// True with a real account (not a guest, not signed out).
    private var isSignedIn: Bool {
        guard let account = store.account else { return false }
        return !account.isGuest
    }

    private var statusText: String {
        guard let account = store.account else { return "Not signed in" }
        if account.isGuest { return "Guest" }
        return account.email ?? "Signed in"
    }

    private var footerText: String {
        isSignedIn
            ? "Your plan and your runs are saved to your account."
            : "As a guest, everything stays on this phone only. Sign in to keep your plan and your runs safe."
    }

    private var errorShown: Binding<Bool> {
        Binding(
            get: { errorMessage != nil },
            set: { shown in
                if !shown { errorMessage = nil }
            }
        )
    }

    private func signOut() {
        busy = true
        Task { @MainActor in
            await AuthService.shared.signOut(store: store)
            busy = false
        }
    }

    private func deleteAccount() {
        busy = true
        Task { @MainActor in
            do {
                try await AuthService.shared.deleteAccount(store: store)
            } catch {
                errorMessage = error.localizedDescription
            }
            busy = false
        }
    }
}

/// Debug tools (debug builds only) and the app version.
struct ProfileFooterSection: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        Section {
            #if DEBUG
            Button("Restart onboarding", role: .destructive) {
                store.eraseEverything()
            }
            #endif
        } footer: {
            Text(versionText)
                .frame(maxWidth: .infinity, alignment: .center)
                .padding(.top, 8)
        }
    }

    private var versionText: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "1.0"
        let build = info?["CFBundleVersion"] as? String
        if let build, build != version { return "Vicrun \(version) (\(build))" }
        return "Vicrun \(version)"
    }
}
