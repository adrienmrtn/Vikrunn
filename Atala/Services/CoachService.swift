import Foundation

/// Talks to the coach. Uses the `coach` edge function when an account is signed in,
/// and answers from the plan on the phone otherwise (guest, offline, server error).
@MainActor
enum CoachService {
    /// Sends the user's message (already appended to `store.messages` or not) and returns the coach's answer.
    /// Never throws: there is always a reply.
    static func send(_ text: String, store: AppStore) async -> ChatMessage {
        let started = Date()
        if let remote = await remoteReply(to: text, store: store) {
            return remote
        }
        let local = CoachLocalReplies.reply(to: text, store: store)
        // A reply that pops up instantly feels wrong: leave a short beat.
        let elapsed = Date().timeIntervalSince(started)
        if elapsed < 0.9 {
            try? await Task.sleep(nanoseconds: UInt64((0.9 - elapsed) * 1_000_000_000))
        }
        return local
    }

    // MARK: Server

    /// When the server said the AI coach is not available (no key yet, daily limit), the app answers
    /// from the phone until this date instead of asking again for every message.
    private static var remotePausedUntil: Date?

    /// The answer of the `coach` edge function, or nil to answer from the phone
    /// (guest, offline, coach not configured, daily limit, any server error).
    private static func remoteReply(to text: String, store: AppStore) async -> ChatMessage? {
        guard SupabaseConfig.isConfigured, store.isPersistent, store.hasRealAccount else { return nil }
        if let pausedUntil = remotePausedUntil, pausedUntil > Date() { return nil }

        let payload: [String: Any] = [
            "messages": history(endingWith: text, store: store),
            "context": context(store: store),
        ]
        guard let body = SupabaseClient.encode(payload),
              let response = try? await AuthService.shared.authorizedSend(
                store: store, path: "/functions/v1/coach", method: "POST", body: body, timeout: 45)
        else { return nil }

        guard response.isSuccess else {
            switch response.errorCode {
            case "coach_not_configured":
                remotePausedUntil = Date().addingTimeInterval(10 * 60)
            case "daily_limit":
                remotePausedUntil = Date().addingTimeInterval(60 * 60)
            default:
                break
            }
            return nil
        }
        remotePausedUntil = nil

        let json = response.json
        let reply = (json["reply"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !reply.isEmpty else { return nil }

        var message = ChatMessage(role: .coach, text: reply)
        // Same id as the row kept in the account, so the answer to a proposal can be saved there.
        if let idString = json["messageId"] as? String, let id = UUID(uuidString: idString) {
            message.id = id
        }
        if let rawProposal = json["proposal"] as? [String: Any] {
            message.proposal = proposal(from: rawProposal, store: store)
        }
        return message
    }

    /// The last messages of the conversation, ending with the user's new message.
    private static func history(endingWith text: String, store: AppStore) -> [[String: Any]] {
        var items: [[String: Any]] = []
        for message in store.messages.suffix(20) {
            let body = message.text.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !body.isEmpty else { continue }
            items.append(["role": message.role == .user ? "user" : "coach", "text": String(body.prefix(3500))])
        }
        let newText = String(text.trimmingCharacters(in: .whitespacesAndNewlines).prefix(3500))
        let lastIsSame = (items.last?["role"] as? String) == "user" && (items.last?["text"] as? String) == newText
        if !lastIsSame {
            items.append(["role": "user", "text": newText])
        }
        return items
    }

    // MARK: Context sent to the coach

    static func dayString(_ date: Date) -> String {
        dayFormatter.string(from: date)
    }

    static func day(from string: String) -> Date? {
        guard let date = dayFormatter.date(from: string) else { return nil }
        return Calendar.plan.startOfDay(for: date)
    }

    private static let dayFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar.plan
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = Calendar.plan.timeZone
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()

    static func weekdayName(_ date: Date) -> String {
        Weekday.long(Calendar.plan.mondayIndex(of: date))
    }

    /// What the coach knows: today, the last two weeks and the next three of the plan, the recent runs,
    /// the paces, the goal, the tone and the coach's name.
    private static func context(store: AppStore) -> [String: Any] {
        let calendar = Calendar.plan
        let today = calendar.startOfDay(for: Date())
        let from = calendar.date(byAdding: .day, value: -14, to: today) ?? today
        let horizon = calendar.date(byAdding: .day, value: 22, to: today) ?? today
        let all = store.allWorkouts
        let window = all.filter { $0.date >= from && $0.date < horizon }

        var workouts: [[String: Any]] = []
        for workout in window.prefix(70) {
            workouts.append([
                "id": workout.id.uuidString,
                "date": dayString(workout.date),
                "weekday": weekdayName(workout.date),
                "kind": workout.kind.rawValue,
                "title": workout.title,
                "distanceKm": rounded(workout.distanceKm),
                "durationMin": workout.durationMin,
                "status": workout.status.rawValue,
            ])
        }

        var recentRuns: [[String: Any]] = []
        for run in store.runs.sorted(by: { $0.date > $1.date }).prefix(8) {
            var item: [String: Any] = [
                "date": dayString(run.date),
                "weekday": weekdayName(run.date),
                "title": run.title,
                "distanceKm": rounded(run.distanceKm),
                "durationMin": run.durationSec / 60,
                "avgPace": Fmt.pace(run.avgPace),
            ]
            if let feeling = run.feeling { item["feeling"] = feeling.title }
            if let heartRate = run.avgHeartRate { item["avgHeartRate"] = heartRate }
            recentRuns.append(item)
        }

        var context: [String: Any] = [
            "firstName": store.firstName,
            "coachName": store.coach.name,
            "tone": store.answers.tone.rawValue,
            "today": dayString(today),
            "weekday": weekdayName(today),
            "workouts": workouts,
            "recentRuns": recentRuns,
        ]
        if let plan = store.plan {
            context["goal"] = plan.goal.title
            if let raceDate = plan.raceDate { context["raceDate"] = dayString(raceDate) }
            if let target = plan.targetSeconds { context["targetSeconds"] = target }
            context["paces"] = [
                "easy": Fmt.pace(plan.paces.easy),
                "long": Fmt.pace(plan.paces.long),
                "tempo": Fmt.pace(plan.paces.tempo),
                "interval": Fmt.pace(plan.paces.interval),
                "race": Fmt.pace(plan.paces.race),
                "unit": "min/km",
            ]
        } else if let goal = store.answers.goal {
            context["goal"] = goal.title
        }
        return context
    }

    private static func rounded(_ value: Double) -> Double {
        guard value.isFinite else { return 0 }
        return (value * 10).rounded() / 10
    }

    // MARK: Proposal parsing

    /// Maps the server proposal to real workouts of the plan. Unknown ids and empty changes are dropped.
    static func proposal(from raw: [String: Any], store: AppStore, keepUnknown: Bool = false) -> PlanProposal? {
        guard let rawChanges = raw["changes"] as? [[String: Any]] else { return nil }
        var changes: [PlanChange] = []
        for item in rawChanges {
            guard let idString = item["workoutId"] as? String,
                  let workoutID = UUID(uuidString: idString) else { continue }
            if let workout = store.workout(id: workoutID) {
                if workout.kind == .race { continue }
            } else if !keepUnknown {
                // A fresh proposal only ever touches sessions of the current plan.
                continue
            }
            var change = PlanChange(workoutID: workoutID)
            if (item["remove"] as? Bool) == true {
                change.remove = true
                changes.append(change)
                continue
            }
            if let dateString = item["newDate"] as? String, let date = day(from: dateString) {
                change.newDate = date
            }
            if let kindString = item["newKind"] as? String, let kind = WorkoutKind(rawValue: kindString),
               kind != .race, kind != .free {
                change.newKind = kind
            }
            if let title = item["newTitle"] as? String, !title.isEmpty {
                change.newTitle = title
            }
            if let km = (item["newDistanceKm"] as? NSNumber)?.doubleValue, km >= 0, km <= 60 {
                change.newDistanceKm = km
            }
            if let minutes = (item["newDurationMin"] as? NSNumber)?.intValue, minutes >= 0, minutes <= 400 {
                change.newDurationMin = minutes
            }
            let changesSomething = change.newDate != nil || change.newKind != nil || change.newTitle != nil
                || change.newDistanceKm != nil || change.newDurationMin != nil
            if changesSomething {
                changes.append(change)
            }
        }
        guard !changes.isEmpty else { return nil }
        let summary = (raw["summary"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        return PlanProposal(summary: summary, changes: changes)
    }
}
