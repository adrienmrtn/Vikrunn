import Foundation

/// The conversation as the account keeps it. The phone's history stays the reference:
/// the account's copy is only read to refill an empty conversation after sign-in.
extension CoachService {
    /// Restores the last messages from the account when the conversation on the phone is empty.
    /// Proposals come back with the answer that was given; one whose sessions no longer exist is
    /// shown as expired by the card. Silent on failure.
    static func restoreHistory(store: AppStore) async {
        guard store.isPersistent, store.hasRealAccount, store.messages.isEmpty,
              let account = store.account else { return }
        let userID = account.userID
        let query = [
            URLQueryItem(name: "select", value: "id,role,body,proposal,proposal_status,created_at"),
            URLQueryItem(name: "user_id", value: "eq.\(userID)"),
            URLQueryItem(name: "order", value: "created_at.desc,seq.desc"),
            URLQueryItem(name: "limit", value: "60"),
        ]
        guard let rows = try? await SyncRemote.rows("coach_messages", query: query, store: store) else { return }
        // The user may have written, or signed out, while the request was running.
        guard store.messages.isEmpty, store.account?.userID == userID else { return }

        var restored: [ChatMessage] = []
        for row in rows.reversed() {
            guard let body = row["body"] as? String, !body.isEmpty,
                  let roleText = row["role"] as? String else { continue }
            // The request carries a hidden "(Answer in French.)" line after the user's words: not theirs.
            let text = roleText == "user"
                ? body.replacingOccurrences(of: "\\n\\n\\(Answer in [A-Za-z ]+\\.\\)$", with: "", options: .regularExpression)
                : body
            var message = ChatMessage(role: roleText == "user" ? .user : .coach, text: text)
            if let idString = row["id"] as? String, let id = UUID(uuidString: idString) {
                message.id = id
            }
            if let date = SyncCoding.date(fromServer: row["created_at"] as? String) {
                message.date = date
            }
            if message.role == .coach, let raw = row["proposal"] as? [String: Any],
               var saved = Self.proposal(from: raw, store: store, keepUnknown: true) {
                switch row["proposal_status"] as? String {
                case "applied": saved.status = .applied
                case "declined": saved.status = .declined
                default: saved.status = .pending
                }
                message.proposal = saved
            }
            restored.append(message)
        }
        guard !restored.isEmpty else { return }
        store.messages = restored
    }

    /// Records in the account what the runner answered to a proposal, so it is not offered again on
    /// another phone. Does nothing for replies written on the phone (they are not in the account).
    static func reportProposal(messageID: UUID, applied: Bool, store: AppStore) async {
        guard store.isPersistent, store.hasRealAccount else { return }
        let query = [URLQueryItem(name: "id", value: "eq.\(messageID.uuidString.lowercased())")]
        let values: [String: Any] = ["proposal_status": applied ? "applied" : "declined"]
        _ = try? await SyncRemote.update("coach_messages", query: query, values: values, store: store)
    }
}
