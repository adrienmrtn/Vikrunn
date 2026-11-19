import SwiftUI

private enum ProfileAccountBusy {
    case signingIn, syncing, signingOut, deleting
}

private enum ProfileAccountAlert: Identifiable {
    case confirmSignOut
    case signOutUnsaved
    case confirmDelete
    case error(String)

    var id: String {
        switch self {
        case .confirmSignOut: return "confirmSignOut"
        case .signOutUnsaved: return "signOutUnsaved"
        case .confirmDelete: return "confirmDelete"
        case .error(let message): return "error-" + message
        }
    }
}

/// Account: who is signed in and how, last sync and "Sync now", sign in (guests), sign out, deletion.
@MainActor
struct ProfileAccountSection: View {
    @EnvironmentObject private var store: AppStore
    @ObservedObject private var auth = AuthService.shared
    @State private var showSignIn = false
    @State private var busy: ProfileAccountBusy?
    @State private var alert: ProfileAccountAlert?
    @State private var syncNote: String?

    var body: some View {
        Section {
            identityRow
            if isSignedIn {
                if auth.sessionExpired {
                    expiredRow
                } else {
                    syncRows
                }
            } else {
                signInRow
            }
            if isSignedIn {
                Button("Sign out") {
                    Haptics.warning()
                    alert = .confirmSignOut
                }
                    .disabled(busy != nil)
            }
            deleteRow
        } header: {
            Text("Account")
        } footer: {
            Text(footerText)
        }
    }

    // MARK: Rows

    private var identityRow: some View {
        HStack {
            Text(isSignedIn ? "Signed in as" : "Account")
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text(statusText)
                    .foregroundStyle(Theme.muted)
                    .lineLimit(1)
                    .truncationMode(.middle)
                if isSignedIn, let provider = auth.signedInProvider {
                    Text("with \(provider.title)")
                        .font(.footnote)
                        .foregroundStyle(Theme.muted)
                }
            }
        }
        .accessibilityElement(children: .combine)
        // Attached to this single row (always shown), not to the section: one sheet, one alert.
        .sheet(isPresented: $showSignIn) {
            SignInOptionsSheet { account in
                showSignIn = false
                finishSignIn(account)
            }
            .environmentObject(store)
        }
        .alert(alertTitle, isPresented: alertShown, presenting: alert) { item in
            alertButtons(item)
        } message: { item in
            Text(alertMessage(item))
        }
    }

    @ViewBuilder private var syncRows: some View {
        HStack {
            Text("Last synced")
            Spacer()
            Text(lastSyncedText)
                .foregroundStyle(Theme.muted)
        }
        .accessibilityElement(children: .combine)
        Button(action: syncNow) {
            HStack {
                Text(busy == .syncing ? "Syncing…" : "Sync now")
                Spacer()
                if busy == .syncing {
                    SwiftUI.ProgressView()
                }
            }
        }
        .disabled(busy != nil)
        if let syncNote {
            Text(syncNote)
                .font(.footnote)
                .foregroundStyle(Theme.muted)
        }
    }

    @ViewBuilder private var expiredRow: some View {
        Text("Your session has expired. Your plan and runs are safe on this phone; sign in again to keep saving them to your account.")
            .font(.footnote)
            .foregroundStyle(Theme.muted)
        Button("Sign in again") {
            Haptics.tap()
            showSignIn = true
        }
            .disabled(busy != nil)
    }

    private var signInRow: some View {
        Button(action: {
            Haptics.tap()
            showSignIn = true
        }) {
            HStack {
                Text(busy == .signingIn ? "Signing in…" : "Sign in")
                Spacer()
                if busy == .signingIn {
                    SwiftUI.ProgressView()
                }
            }
        }
        .disabled(busy != nil)
    }

    private var deleteRow: some View {
        Button(role: .destructive, action: {
            Haptics.warning()
            alert = .confirmDelete
        }) {
            HStack {
                Text(deleteTitle)
                Spacer()
                if busy == .deleting || busy == .signingOut {
                    SwiftUI.ProgressView()
                }
            }
        }
        .disabled(busy != nil)
    }

    // MARK: Texts

    /// True with a real account (not a guest, not signed out).
    private var isSignedIn: Bool { store.hasRealAccount }

    private var deleteTitle: String {
        if busy == .deleting { return "Deleting…" }
        if busy == .signingOut { return "Signing out…" }
        return isSignedIn ? "Delete my account" : "Erase my data"
    }

    private var statusText: String {
        guard let account = store.account else { return "Not signed in" }
        if account.isGuest { return "Guest" }
        return account.email ?? "Signed in"
    }

    private var lastSyncedText: String {
        if store.syncActivity != .idle || busy == .syncing { return "Syncing…" }
        guard let date = store.lastSyncedAt else { return "Not yet" }
        let seconds = Date().timeIntervalSince(date)
        if seconds < 90 { return "Just now" }
        if seconds < 3600 { return "\(Int(seconds / 60)) min ago" }
        if Calendar.plan.isDateInToday(date) { return "Today at " + Fmt.day(date, "HH:mm") }
        return Fmt.day(date, "d MMM 'at' HH:mm")
    }

    private var footerText: String {
        if isSignedIn {
            return "Your plan, your runs and your settings are saved to your account, and come back when you sign in on another phone."
        }
        return "As a guest, everything stays on this phone only. Sign in to keep your plan and your runs safe."
    }

    // MARK: Alerts

    private var alertShown: Binding<Bool> {
        Binding(
            get: { alert != nil },
            set: { shown in
                if !shown { alert = nil }
            }
        )
    }

    private var alertTitle: String {
        switch alert {
        case .confirmSignOut: return "Sign out?"
        case .signOutUnsaved: return "Your latest changes aren't saved yet"
        case .confirmDelete: return isSignedIn ? "Delete your account?" : "Erase everything?"
        case .error, .none: return "Something went wrong"
        }
    }

    @ViewBuilder private func alertButtons(_ item: ProfileAccountAlert) -> some View {
        switch item {
        case .confirmSignOut:
            Button("Sign out", role: .destructive) { signOut(force: false) }
            Button("Cancel", role: .cancel) {}
        case .signOutUnsaved:
            Button("Sign out anyway", role: .destructive) { signOut(force: true) }
            Button("Stay signed in", role: .cancel) {}
        case .confirmDelete:
            Button(isSignedIn ? "Delete everything" : "Erase everything", role: .destructive) { deleteAccount() }
            Button("Cancel", role: .cancel) {}
        case .error:
            Button("OK", role: .cancel) {}
        }
    }

    private func alertMessage(_ item: ProfileAccountAlert) -> String {
        switch item {
        case .confirmSignOut:
            return "Your plan, your runs and your settings are saved in your account. They are removed from this phone and come back when you sign in again."
        case .signOutUnsaved:
            return "We couldn't reach your account, so what you changed most recently is only on this phone. If you sign out now, those changes are lost."
        case .confirmDelete:
            if isSignedIn {
                return "Your account, your plan, your runs and your conversations with your coach are permanently deleted. This cannot be undone."
            }
            return "Your plan, your runs and your conversations with your coach are removed from this phone. This cannot be undone."
        case .error(let message):
            return message
        }
    }

    // MARK: Actions

    private func finishSignIn(_ account: Account) {
        guard busy == nil else { return }
        // Signing in again after an expired session must be the same account: the phone holds its data.
        if let current = store.account, !current.isGuest, !account.isGuest, current.userID != account.userID {
            Task { @MainActor in
                // Let the sign-in sheet close before the alert appears.
                try? await Task.sleep(nanoseconds: 500_000_000)
                Haptics.error()
                alert = .error("That's a different account. Sign out first to switch accounts.")
            }
            return
        }
        busy = .signingIn
        Task { @MainActor in
            // A guest's plan and runs are uploaded; if the account already has some, the app asks which to keep.
            let outcome = await AuthSignInFlow.complete(account, store: store, policy: .askIfBoth)
            busy = nil
            if case .failed(let failure) = outcome {
                Haptics.error()
                alert = .error(failure.userMessage)
            }
        }
    }

    private func syncNow() {
        guard busy == nil else { return }
        Haptics.tap()
        busy = .syncing
        syncNote = nil
        Task { @MainActor in
            let outcome = await SyncService.syncNow(store: store)
            busy = nil
            switch outcome {
            case .done:
                Haptics.success()
                syncNote = nil
            case .notSignedIn:
                syncNote = auth.sessionExpired ? "Sign in again to sync." : nil
            case .failed(let failure):
                Haptics.error()
                syncNote = failure == .offline
                    ? "No connection. Your changes are kept on this phone and sent when you're back online."
                    : failure.userMessage
            }
        }
    }

    /// Sends what is pending first. If that is not possible, asks before losing it.
    private func signOut(force: Bool) {
        guard busy == nil else { return }
        busy = .signingOut
        Task { @MainActor in
            if !force {
                _ = await SyncService.syncNow(store: store)
                if SyncService.hasPendingChanges(store: store) {
                    busy = nil
                    Haptics.warning()
                    alert = .signOutUnsaved
                    return
                }
            }
            await AuthService.shared.signOut(store: store)
            busy = nil
        }
    }

    private func deleteAccount() {
        guard busy == nil else { return }
        busy = .deleting
        Task { @MainActor in
            do {
                try await AuthService.shared.deleteAccount(store: store)
            } catch {
                Haptics.error()
                alert = .error(error.localizedDescription)
            }
            busy = nil
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
                Haptics.warning()
                store.eraseEverything()
            }
            #endif
        } footer: {
            VStack(spacing: 8) {
                BrandLogo(style: .full, height: 18)
                    .foregroundStyle(Theme.forest)
                Text(versionText)
            }
            .frame(maxWidth: .infinity, alignment: .center)
            .padding(.top, 12)
        }
    }

    private var versionText: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "1.0"
        let build = info?["CFBundleVersion"] as? String
        if let build, build != version { return "Version \(version) (\(build))" }
        return "Version \(version)"
    }
}
