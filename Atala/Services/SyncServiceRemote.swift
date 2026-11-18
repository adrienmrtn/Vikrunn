import Foundation

/// Why an exchange with the account did not go through.
enum SyncFailure: Error, Equatable {
    /// No connection, timeout. Nothing is lost: the changes stay marked and go out next time.
    case offline
    /// The session is dead: the user has to sign in again.
    case sessionExpired
    /// The server refused or failed (HTTP status).
    case server(Int)
    /// The account changed (sign-out, deletion) while the exchange was running.
    case interrupted

    var userMessage: String {
        switch self {
        case .offline: return "We couldn't reach your account. Check your connection and try again."
        case .sessionExpired: return "Your session has expired. Please sign in again."
        case .server: return "Our server is having a moment. Please try again shortly."
        case .interrupted: return "That was interrupted. Please try again."
        }
    }
}

/// Result of a sync, for the screens that show it.
enum SyncOutcome: Equatable {
    case done
    /// Guest, signed out, or account not matched with the phone yet.
    case notSignedIn
    case failed(SyncFailure)
}

/// PostgREST calls made as the signed-in user (row level security limits them to their own rows).
@MainActor
enum SyncRemote {
    static func rows(_ table: String, query: [URLQueryItem], store: AppStore) async throws -> [[String: Any]] {
        let response = try await send(store: store, path: "/rest/v1/\(table)", method: "GET", query: query,
                                      body: nil, prefer: nil)
        return response.jsonRows
    }

    /// Inserts or updates rows and returns the columns asked for (to read the server's `updated_at`).
    static func upsert(_ table: String, conflict: String, rows: [[String: Any]], returning: String,
                       store: AppStore) async throws -> [[String: Any]] {
        guard !rows.isEmpty else { return [] }
        guard let body = SupabaseClient.encode(rows) else { throw SyncFailure.server(0) }
        let query = [URLQueryItem(name: "on_conflict", value: conflict),
                     URLQueryItem(name: "select", value: returning)]
        let response = try await send(store: store, path: "/rest/v1/\(table)", method: "POST", query: query,
                                      body: body, prefer: "resolution=merge-duplicates,return=representation")
        return response.jsonRows
    }

    /// Changes columns of the rows matched by `query` (PostgREST filters).
    static func update(_ table: String, query: [URLQueryItem], values: [String: Any],
                       store: AppStore) async throws {
        guard let body = SupabaseClient.encode(values) else { throw SyncFailure.server(0) }
        _ = try await send(store: store, path: "/rest/v1/\(table)", method: "PATCH", query: query,
                           body: body, prefer: "return=minimal")
    }

    private static func send(store: AppStore, path: String, method: String, query: [URLQueryItem],
                             body: Data?, prefer: String?) async throws -> SupabaseResponse {
        let response: SupabaseResponse
        do {
            response = try await AuthService.shared.authorizedSend(store: store, path: path, method: method,
                                                                   query: query, body: body, prefer: prefer,
                                                                   timeout: 40)
        } catch let error as AuthError {
            throw error == .sessionExpired ? SyncFailure.sessionExpired : SyncFailure.offline
        } catch {
            throw SyncFailure.offline
        }
        guard response.isSuccess else {
            if response.status == 401 { throw SyncFailure.sessionExpired }
            throw SyncFailure.server(response.status)
        }
        return response
    }
}

/// What the account holds, downloaded before anything is changed on the phone.
struct SyncRemoteSnapshot {
    var profileStamp: String?
    var answers: OnboardingAnswers?
    var settings: AppSettings?
    var planStamp: String?
    /// Downloaded only when it differs from what the phone last saw.
    var plan: TrainingPlan?
    var runs: [SyncRemoteRun] = []
    /// Full runs, by id, for the ones that had to be downloaded.
    var runRecords: [String: RunRecord] = [:]

    var hasLiveRuns: Bool { runs.contains { !$0.deleted } }
}

struct SyncRemoteRun {
    let key: String
    let stamp: String
    let deleted: Bool
}

extension SyncService {
    /// Reads the account: profile, plan (when changed or `everything`), the list of runs, and the runs
    /// the phone does not have in that version. Changes nothing locally.
    static func fetchSnapshot(store: AppStore, everything: Bool) async throws -> SyncRemoteSnapshot {
        guard let account = store.account, !account.isGuest else { throw SyncFailure.interrupted }
        let state = store.syncState
        let mine = URLQueryItem(name: "user_id", value: "eq.\(account.userID)")
        let one = URLQueryItem(name: "limit", value: "1")
        var snapshot = SyncRemoteSnapshot()

        let profiles = try await SyncRemote.rows("profiles", query: [
            URLQueryItem(name: "select", value: "answers,settings,updated_at"), mine, one,
        ], store: store)
        if let row = profiles.first, let stamp = row["updated_at"] as? String {
            snapshot.profileStamp = stamp
            snapshot.answers = SyncCoding.decode(OnboardingAnswers.self, from: row["answers"])
            snapshot.settings = SyncCoding.decode(AppSettings.self, from: row["settings"])
        }

        let planMeta = try await SyncRemote.rows("plans", query: [
            URLQueryItem(name: "select", value: "updated_at"), mine, one,
        ], store: store)
        if let stamp = planMeta.first?["updated_at"] as? String {
            snapshot.planStamp = stamp
            if everything || stamp != state.planStamp {
                let plans = try await SyncRemote.rows("plans", query: [
                    URLQueryItem(name: "select", value: "plan,updated_at"), mine, one,
                ], store: store)
                if let row = plans.first, let fresh = row["updated_at"] as? String {
                    snapshot.planStamp = fresh
                    snapshot.plan = SyncCoding.decode(TrainingPlan.self, from: row["plan"])
                }
            }
        }

        let runMeta = try await SyncRemote.rows("runs", query: [
            URLQueryItem(name: "select", value: "id,updated_at,deleted_at"), mine,
            URLQueryItem(name: "order", value: "updated_at.asc"),
        ], store: store)
        var localKeys = Set<String>()
        for run in store.runs { localKeys.insert(SyncState.key(run.id)) }
        var wanted: [String] = []
        for row in runMeta {
            guard let id = row["id"] as? String, let stamp = row["updated_at"] as? String else { continue }
            let key = id.lowercased()
            let deleted = !(row["deleted_at"] is NSNull) && row["deleted_at"] != nil
            snapshot.runs.append(SyncRemoteRun(key: key, stamp: stamp, deleted: deleted))
            if deleted || state.deletedRunIDs.contains(key) { continue }
            if everything || !localKeys.contains(key) || state.runStamps[key] != stamp {
                wanted.append(key)
            }
        }

        var start = 0
        while start < wanted.count {
            let end = min(start + 15, wanted.count)
            let list = wanted[start..<end].joined(separator: ",")
            let rows = try await SyncRemote.rows("runs", query: [
                URLQueryItem(name: "select", value: "id,run"), mine,
                URLQueryItem(name: "id", value: "in.(\(list))"),
            ], store: store)
            for row in rows {
                guard let id = row["id"] as? String,
                      let record = SyncCoding.decode(RunRecord.self, from: row["run"]) else { continue }
                snapshot.runRecords[id.lowercased()] = record
            }
            start = end
        }
        return snapshot
    }
}
