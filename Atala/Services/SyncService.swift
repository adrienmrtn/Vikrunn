import Foundation

/// Keeps the phone and the account in step: profile (answers, settings, coach, first name), plan and runs.
///
/// - The phone always works on its own file; the account is a mirror, never a requirement.
/// - Changes are sent a few seconds after they happen, when the app goes to the background, after
///   sign-in and after a run. Offline, they stay marked and go out at the next occasion.
/// - Per record, the most recent change wins (`updated_at` is set by the server). Runs are merged by id;
///   a deleted run is removed from the account and from the other phones too.
/// - Only a real account syncs: guests keep everything on the phone.
@MainActor
enum SyncService {
    /// End of the queue of sync operations: they run one after the other, never interleaved.
    private static var tail: Task<Void, Never>?

    // MARK: Public API

    /// Sends what changed on the phone. Silent on failure: nothing is lost, it is sent next time.
    static func pushAll(store: AppStore) async {
        _ = await serialized { () -> SyncOutcome in await runPush(store: store) }
    }

    /// Brings what changed in the account to the phone.
    /// Returns true when server data replaced or completed local data.
    @discardableResult
    static func pullAll(store: AppStore) async -> Bool {
        let result = await serialized { () -> SyncPullResult in await runPull(store: store) }
        return result.changed
    }

    /// Pull then push, with the result ("Sync now" in Profile, launch, return to the foreground).
    @discardableResult
    static func syncNow(store: AppStore) async -> SyncOutcome {
        await serialized { () -> SyncOutcome in
            guard store.isPersistent, store.hasRealAccount else { return .notSignedIn }
            guard store.syncState.reconciled else {
                // Signed in by an earlier version of the app, or interrupted right after sign-in.
                let outcome = await reconcile(store: store, policy: .askIfBoth, previous: nil)
                if case .failed(let failure) = outcome { return .failed(failure) }
                return store.syncState.reconciled ? .done : .notSignedIn
            }
            if store.syncActivity == .idle { store.syncActivity = .syncing }
            defer {
                if store.syncActivity == .syncing { store.syncActivity = .idle }
            }
            let pulled = await runPull(store: store)
            if case .failed = pulled.outcome { return pulled.outcome }
            return await runPush(store: store)
        }
    }

    /// True when something on the phone has not reached the account yet.
    static func hasPendingChanges(store: AppStore) -> Bool {
        store.hasRealAccount && (!store.syncState.reconciled || store.syncState.hasPendingChanges)
    }

    // MARK: Queue

    static func serialized<T: Sendable>(_ work: @escaping @MainActor () async -> T) async -> T {
        let previous = tail
        let task = Task { @MainActor () -> T in
            _ = await previous?.value
            return await work()
        }
        tail = Task { @MainActor in
            _ = await task.value
        }
        return await task.value
    }

    // MARK: Push

    static func runPush(store: AppStore) async -> SyncOutcome {
        guard store.isPersistent, let account = store.account, !account.isGuest,
              store.syncState.reconciled else { return .notSignedIn }
        guard store.syncState.hasPendingChanges else { return .done }
        let userID = account.userID
        do {
            try await pushProfile(store: store, userID: userID)
            try await pushPlan(store: store, userID: userID)
            try await pushRuns(store: store, userID: userID)
            try await pushDeletedRuns(store: store, userID: userID)
            store.lastSyncedAt = Date()
            return .done
        } catch let failure as SyncFailure {
            return .failed(failure)
        } catch {
            return .failed(.offline)
        }
    }

    /// Stops an exchange whose account went away (sign-out, deletion) while a request was running.
    private static func checkStillSignedIn(store: AppStore, userID: String) throws {
        guard let account = store.account, !account.isGuest, account.userID == userID else {
            throw SyncFailure.interrupted
        }
    }

    private static func pushProfile(store: AppStore, userID: String) async throws {
        guard let dirtyAt = store.syncState.profileDirtyAt else { return }
        let answers = store.answers
        guard let answersJSON = SyncCoding.jsonObject(answers),
              let settingsJSON = SyncCoding.jsonObject(store.settings) else { return }
        let row: [String: Any] = [
            "user_id": userID,
            "first_name": answers.firstName,
            "coach_id": answers.coachID,
            "answers": answersJSON,
            "settings": settingsJSON,
        ]
        let saved = try await SyncRemote.upsert("profiles", conflict: "user_id", rows: [row],
                                                returning: "user_id,updated_at", store: store)
        try checkStillSignedIn(store: store, userID: userID)
        var state = store.syncState
        if let stamp = saved.first?["updated_at"] as? String { state.profileStamp = stamp }
        // Changed again while the request was running: keep it marked for the next push.
        if state.profileDirtyAt == dirtyAt { state.profileDirtyAt = nil }
        store.syncState = state
    }

    private static func pushPlan(store: AppStore, userID: String) async throws {
        guard let dirtyAt = store.syncState.planDirtyAt else { return }
        guard let plan = store.plan, let planJSON = SyncCoding.jsonObject(plan) else {
            // No plan on the phone: nothing to send (the account's copy is left alone).
            var state = store.syncState
            state.planDirtyAt = nil
            store.syncState = state
            return
        }
        let row: [String: Any] = ["user_id": userID, "plan": planJSON]
        let saved = try await SyncRemote.upsert("plans", conflict: "user_id", rows: [row],
                                                returning: "user_id,updated_at", store: store)
        try checkStillSignedIn(store: store, userID: userID)
        var state = store.syncState
        if let stamp = saved.first?["updated_at"] as? String { state.planStamp = stamp }
        if state.planDirtyAt == dirtyAt { state.planDirtyAt = nil }
        store.syncState = state
    }

    private static func pushRuns(store: AppStore, userID: String) async throws {
        let dirty = store.syncState.runsDirtyAt
        guard !dirty.isEmpty else { return }
        var rows: [[String: Any]] = []
        var sentAt: [String: Date] = [:]
        var gone: [String] = []
        var local: [String: RunRecord] = [:]
        for run in store.runs { local[SyncState.key(run.id)] = run }
        for (key, date) in dirty {
            guard let run = local[key], let runJSON = SyncCoding.jsonObject(run) else {
                gone.append(key)
                continue
            }
            rows.append([
                "id": key,
                "user_id": userID,
                "run": runJSON,
                "run_date": SyncCoding.isoString(run.date),
                "deleted_at": NSNull(),
            ])
            sentAt[key] = date
        }
        if !gone.isEmpty {
            var state = store.syncState
            for key in gone { state.runsDirtyAt[key] = nil }
            store.syncState = state
        }
        // Runs carry their GPS route: a few per request keeps each upload small.
        var start = 0
        while start < rows.count {
            let end = min(start + 8, rows.count)
            let saved = try await SyncRemote.upsert("runs", conflict: "id", rows: Array(rows[start..<end]),
                                                    returning: "id,updated_at", store: store)
            try checkStillSignedIn(store: store, userID: userID)
            var state = store.syncState
            for row in saved {
                guard let id = row["id"] as? String, let stamp = row["updated_at"] as? String else { continue }
                let key = id.lowercased()
                state.runStamps[key] = stamp
                if state.runsDirtyAt[key] == sentAt[key] { state.runsDirtyAt[key] = nil }
            }
            store.syncState = state
            start = end
        }
    }

    /// A deleted run becomes a tombstone in the account, so the other phones remove it too.
    private static func pushDeletedRuns(store: AppStore, userID: String) async throws {
        let keys = store.syncState.deletedRunIDs
        guard !keys.isEmpty else { return }
        let now = SyncCoding.isoString(Date())
        var rows: [[String: Any]] = []
        for key in keys {
            rows.append([
                "id": key,
                "user_id": userID,
                "run": [String: Any](),
                "run_date": NSNull(),
                "deleted_at": now,
            ])
        }
        var start = 0
        while start < rows.count {
            let end = min(start + 50, rows.count)
            let saved = try await SyncRemote.upsert("runs", conflict: "id", rows: Array(rows[start..<end]),
                                                    returning: "id,updated_at", store: store)
            try checkStillSignedIn(store: store, userID: userID)
            var state = store.syncState
            for row in saved {
                guard let id = row["id"] as? String else { continue }
                let key = id.lowercased()
                state.deletedRunIDs.removeAll { $0 == key }
                if let stamp = row["updated_at"] as? String { state.runStamps[key] = stamp }
            }
            store.syncState = state
            start = end
        }
    }
}
