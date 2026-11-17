import Foundation

/// What the sync is doing, for the screens that show it.
enum SyncActivity: Equatable {
    case idle
    /// Regular background exchange with the account.
    case syncing
    /// Right after sign-in: the account's plan and runs are being brought to the phone.
    case restoring
}

/// Sync bookkeeping saved with the rest of the store.
///
/// "Dirty" dates use the phone's clock and mean "changed here, not sent yet".
/// "Stamps" are the server's `updated_at` exactly as received: a different stamp on the server
/// means another phone wrote since. When both sides changed, the most recent change wins.
struct SyncState: Codable, Equatable {
    /// False until the phone and the account have been matched once after sign-in. Nothing is sent before.
    var reconciled: Bool = false
    var profileDirtyAt: Date?
    var planDirtyAt: Date?
    /// Run id (lowercased UUID) to the date of its last local change.
    var runsDirtyAt: [String: Date] = [:]
    /// Runs deleted on this phone that still have to be removed from the account.
    var deletedRunIDs: [String] = []
    var profileStamp: String?
    var planStamp: String?
    var runStamps: [String: String] = [:]

    init() {}

    private enum CodingKeys: String, CodingKey {
        case reconciled, profileDirtyAt, planDirtyAt, runsDirtyAt, deletedRunIDs, profileStamp, planStamp, runStamps
    }

    // Every field is optional on disk, so a file written by another version always loads.
    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        reconciled = try values.decodeIfPresent(Bool.self, forKey: .reconciled) ?? false
        profileDirtyAt = try values.decodeIfPresent(Date.self, forKey: .profileDirtyAt)
        planDirtyAt = try values.decodeIfPresent(Date.self, forKey: .planDirtyAt)
        runsDirtyAt = try values.decodeIfPresent([String: Date].self, forKey: .runsDirtyAt) ?? [:]
        deletedRunIDs = try values.decodeIfPresent([String].self, forKey: .deletedRunIDs) ?? []
        profileStamp = try values.decodeIfPresent(String.self, forKey: .profileStamp)
        planStamp = try values.decodeIfPresent(String.self, forKey: .planStamp)
        runStamps = try values.decodeIfPresent([String: String].self, forKey: .runStamps) ?? [:]
    }

    static func key(_ id: UUID) -> String { id.uuidString.lowercased() }

    var hasPendingChanges: Bool {
        profileDirtyAt != nil || planDirtyAt != nil || !runsDirtyAt.isEmpty || !deletedRunIDs.isEmpty
    }
}

/// JSON helpers shared by the sync code. Dates are ISO 8601, like the store's own file.
enum SyncCoding {
    /// Any Codable value as a JSON object ready for a `jsonb` column.
    static func jsonObject<T: Encodable>(_ value: T) -> Any? {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        guard let data = try? encoder.encode(value) else { return nil }
        return try? JSONSerialization.jsonObject(with: data)
    }

    /// A `jsonb` value read from the server back into a model. Nil when it does not match the model.
    static func decode<T: Decodable>(_ type: T.Type, from object: Any?) -> T? {
        guard let object, JSONSerialization.isValidJSONObject(object),
              let data = try? JSONSerialization.data(withJSONObject: object) else { return nil }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try? decoder.decode(type, from: data)
    }

    static func isoString(_ date: Date) -> String {
        ISO8601DateFormatter().string(from: date)
    }

    /// Parses a Postgres `timestamptz` as PostgREST prints it ("2026-11-17T23:08:08.336123+00:00").
    static func date(fromServer text: String?) -> Date? {
        guard var value = text, !value.isEmpty else { return nil }
        // Drop the fraction: its length varies and one second is precise enough to compare changes.
        if let dot = value.firstIndex(of: ".") {
            var end = value.index(after: dot)
            while end < value.endIndex, value[end].isNumber { end = value.index(after: end) }
            value.removeSubrange(dot..<end)
        }
        return ISO8601DateFormatter().date(from: value)
    }
}

// MARK: - Store hooks

extension AppStore {
    /// True with a real account (not a guest, not signed out).
    var hasRealAccount: Bool {
        guard let account else { return false }
        return !account.isGuest
    }

    /// Called when the profile (answers, settings) or the plan changed on the phone.
    func syncNoteChange(profile: Bool) {
        guard !syncIsApplyingRemote else { return }
        if profile {
            syncState.profileDirtyAt = Date()
        } else {
            syncState.planDirtyAt = Date()
        }
        syncSchedulePush()
    }

    /// Called when the list of runs changed: marks the new or edited ones, remembers the deleted ones.
    func syncNoteRunsChange(from old: [RunRecord]) {
        guard !syncIsApplyingRemote, runs != old else { return }
        let now = Date()
        var state = syncState
        var previous: [UUID: RunRecord] = [:]
        for run in old { previous[run.id] = run }
        var currentIDs = Set<UUID>()
        for run in runs {
            currentIDs.insert(run.id)
            if previous[run.id] != run {
                let key = SyncState.key(run.id)
                state.runsDirtyAt[key] = now
                state.deletedRunIDs.removeAll { $0 == key }
            }
        }
        for run in old where !currentIDs.contains(run.id) {
            let key = SyncState.key(run.id)
            state.runsDirtyAt[key] = nil
            // Only an account can hold a copy to remove.
            if hasRealAccount, !state.deletedRunIDs.contains(key) {
                state.deletedRunIDs.append(key)
            }
        }
        syncState = state
        syncSchedulePush()
    }

    /// Sends the pending changes a few seconds after the last one, so a burst of edits is one upload.
    func syncSchedulePush(after seconds: Double = 4) {
        guard isPersistent, hasRealAccount, syncState.reconciled else { return }
        syncTask?.cancel()
        syncTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(max(0, seconds) * 1_000_000_000))
            guard !Task.isCancelled, let self else { return }
            await SyncService.pushAll(store: self)
        }
    }

    /// Writes server data into the store without marking it as a local change.
    func syncApplyRemote(_ changes: () -> Void) {
        let wasApplying = syncIsApplyingRemote
        syncIsApplyingRemote = true
        changes()
        syncIsApplyingRemote = wasApplying
    }

    /// Everything on the phone has to be sent again (first upload to an account).
    func syncMarkEverythingDirty() {
        let now = Date()
        var state = syncState
        state.profileDirtyAt = now
        state.planDirtyAt = plan == nil ? nil : now
        state.runsDirtyAt = [:]
        for run in runs { state.runsDirtyAt[SyncState.key(run.id)] = now }
        syncState = state
    }
}
