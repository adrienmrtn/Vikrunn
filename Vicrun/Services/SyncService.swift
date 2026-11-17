import Foundation

/// Best-effort backup of the profile, plan and runs to Supabase. Silent on failure.
enum SyncService {
    @MainActor
    static func pushAll(store: AppStore) async {
        guard let account = store.account, !account.isGuest,
              let token = await AuthService.shared.validAccessToken(store: store) else { return }
        let userID = account.userID
        let now = isoString(Date())

        // Snapshot the data first: the store may change while the requests are running.
        let answers = store.answers
        let settings = store.settings
        let plan = store.plan
        let runs = store.runs

        if let answersJSON = jsonObject(answers), let settingsJSON = jsonObject(settings) {
            let row: [String: Any] = [
                "user_id": userID,
                "first_name": answers.firstName,
                "coach_id": answers.coachID,
                "answers": answersJSON,
                "settings": settingsJSON,
                "updated_at": now,
            ]
            await upsert(table: "profiles", conflict: "user_id", rows: [row], token: token)
        }

        if let plan, let planJSON = jsonObject(plan) {
            let row: [String: Any] = ["user_id": userID, "plan": planJSON, "updated_at": now]
            await upsert(table: "plans", conflict: "user_id", rows: [row], token: token)
        }

        var runRows: [[String: Any]] = []
        for run in runs {
            guard let runJSON = jsonObject(run) else { continue }
            runRows.append([
                "id": run.id.uuidString.lowercased(),
                "user_id": userID,
                "run": runJSON,
                "run_date": isoString(run.date),
                "updated_at": now,
            ])
        }
        var start = 0
        while start < runRows.count {
            let end = min(start + 25, runRows.count)
            await upsert(table: "runs", conflict: "id", rows: Array(runRows[start..<end]), token: token)
            start = end
        }
    }

    @MainActor
    private static func upsert(table: String, conflict: String, rows: [[String: Any]], token: String) async {
        guard !rows.isEmpty, let body = SupabaseClient.encode(rows) else { return }
        _ = try? await SupabaseClient.send(path: "/rest/v1/\(table)",
                                           method: "POST",
                                           query: [URLQueryItem(name: "on_conflict", value: conflict)],
                                           body: body,
                                           accessToken: token,
                                           prefer: "resolution=merge-duplicates,return=minimal")
    }

    /// Any Codable value as a JSON object ready to be stored in a `jsonb` column.
    private static func jsonObject<T: Encodable>(_ value: T) -> Any? {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        guard let data = try? encoder.encode(value) else { return nil }
        return try? JSONSerialization.jsonObject(with: data)
    }

    private static func isoString(_ date: Date) -> String {
        ISO8601DateFormatter().string(from: date)
    }
}
