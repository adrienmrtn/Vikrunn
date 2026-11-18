import Foundation
import UIKit

/// Holds the identifier of the background task that lets a push finish after the app is left.
private final class SyncBackgroundToken: @unchecked Sendable {
    var id: UIBackgroundTaskIdentifier = .invalid
}

extension SyncService {
    private static var lastResume = Date.distantPast

    /// Launch and return to the foreground: bring the account's changes, then send the phone's.
    /// Never blocks the screen; does nothing for a guest. Calls close together are merged.
    static func resume(store: AppStore) {
        guard store.isPersistent, store.hasRealAccount else { return }
        let now = Date()
        guard now.timeIntervalSince(lastResume) > 15 else { return }
        lastResume = now
        Task { @MainActor in
            await syncNow(store: store)
        }
    }

    /// The app goes to the background: send what is pending, asking iOS for the few seconds it takes.
    static func pushBeforeSuspension(store: AppStore) {
        guard store.isPersistent, store.hasRealAccount, hasPendingChanges(store: store) else { return }
        store.syncTask?.cancel()
        store.syncTask = nil
        let application = UIApplication.shared
        let token = SyncBackgroundToken()
        token.id = application.beginBackgroundTask(withName: "atala-sync") {
            // Out of time: the changes stay marked and go out at the next launch.
            if token.id != .invalid {
                application.endBackgroundTask(token.id)
                token.id = .invalid
            }
        }
        Task { @MainActor in
            await pushAll(store: store)
            if token.id != .invalid {
                application.endBackgroundTask(token.id)
                token.id = .invalid
            }
        }
    }
}
