import Foundation

/// Answers built on the phone from the plan, used when the coach service can't be reached
/// (guest account, no network, server error). Same rules: never a diagnosis, changes only as proposals.
@MainActor
enum CoachLocalReplies {
    static func reply(to text: String, store: AppStore) -> ChatMessage {
        let lowered = text.lowercased()
        let words = lowered.components(separatedBy: CharacterSet.alphanumerics.inverted).filter { !$0.isEmpty }
        let tone = store.answers.tone

        if store.plan == nil {
            return ChatMessage(role: .coach, text: "Your plan isn't ready yet. Finish setting it up and I'll have every session at hand to help you.")
        }
        if matches(words, painStems) {
            return painReply(store: store, tone: tone)
        }
        if matches(words, changeStems) || containsAny(lowered, changePhrases) {
            return moveReply(store: store, tone: tone)
        }
        if words.count <= 4, matches(words, ["thank", "thanks", "cheers", "great", "perfect"]) {
            return ChatMessage(role: .coach, text: pick(tone, kind: "Any time. I'm here whenever you need me.",
                                                        direct: "Good. Back to it.",
                                                        playful: "That's what I'm here for. Now go and be brilliant."))
        }
        if matches(words, ["pace", "paces", "fast", "faster", "slow", "slower", "speed"]) {
            return ChatMessage(role: .coach, text: paceText(store: store, tone: tone))
        }
        if matches(words, ["race", "goal", "target", "ready", "predict", "finish"]) {
            return ChatMessage(role: .coach, text: goalText(store: store, tone: tone))
        }
        if matches(words, ["next", "today", "tomorrow", "tonight", "week", "session", "workout", "plan", "schedule", "when"]) {
            return ChatMessage(role: .coach, text: nextText(store: store, tone: tone))
        }
        if matches(words, ["motivat", "lazy", "bored", "quit", "struggl", "discourag", "hard", "doubt"]) {
            let lead = pick(tone, kind: "Some days are heavier than others, and that's normal.",
                            direct: "Motivation comes and goes. The habit stays.",
                            playful: "Even the best legs have grumpy days.")
            return ChatMessage(role: .coach, text: lead + " Just start the first ten minutes easy and see how you feel. " + nextText(store: store, tone: tone))
        }
        if words.count <= 3, matches(words, ["hi", "hello", "hey", "morning", "evening"]) {
            return ChatMessage(role: .coach, text: "Hi \(store.firstName). " + nextText(store: store, tone: tone))
        }
        let lead = pick(tone, kind: "I'm with you.", direct: "Here's where you stand.", playful: "Let's keep it simple.")
        return ChatMessage(role: .coach, text: lead + " " + nextText(store: store, tone: tone)
                           + " If something gets in the way of a session, tell me and I'll suggest how to move it.")
    }

    // MARK: Keywords

    private static let painStems = ["pain", "hurt", "injur", "sore", "ache", "aching", "knee", "ankle", "shin", "calf",
                                    "calves", "achilles", "hamstring", "sprain", "strain", "twinge", "cramp",
                                    "swollen", "limp", "tendon", "blister"]
    private static let changeStems = ["tired", "exhaust", "fatigue", "knackered", "busy", "travel", "trip", "miss", "skip",
                                      "move", "moving", "postpone", "reschedul", "cancel", "sick", "ill", "flu", "swap",
                                      "shift", "wedding", "holiday", "vacation", "overtime", "jetlag"]
    private static let changePhrases = ["can't make", "cant make", "cannot make", "no time", "won't be able",
                                        "wont be able", "not able to", "slept badly", "bad night", "didn't sleep",
                                        "work late", "working late", "a cold", "push back", "put off"]

    private static func matches(_ words: [String], _ stems: [String]) -> Bool {
        words.contains { word in stems.contains { word.hasPrefix($0) } }
    }

    private static func containsAny(_ text: String, _ phrases: [String]) -> Bool {
        phrases.contains { text.contains($0) }
    }

    private static func pick(_ tone: CoachTone, kind: String, direct: String, playful: String) -> String {
        switch tone {
        case .kind: return kind
        case .direct: return direct
        case .playful: return playful
        }
    }

    // MARK: Plain answers

    private static func dayLabel(_ date: Date) -> String {
        let calendar = Calendar.plan
        if calendar.isDateInToday(date) { return "today" }
        if calendar.isDateInTomorrow(date) { return "tomorrow" }
        return "on " + Fmt.day(date, "EEEE d MMMM")
    }

    private static func describe(_ workout: Workout) -> String {
        var text = workout.title
        if workout.kind.isRun, workout.distanceKm > 0 {
            text += ", " + Fmt.km(workout.distanceKm)
        }
        if workout.durationMin > 0 {
            text += ", about " + Fmt.minutes(workout.durationMin)
        }
        return text
    }

    private static func nextText(store: AppStore, tone: CoachTone) -> String {
        guard let next = store.nextRun else {
            return "There's no run left on your plan for now. Enjoy the rest, you've earned it."
        }
        var text = "Your next run is \(dayLabel(next.date)): \(describe(next))."
        if let pace = next.targetPace, pace > 0 {
            text += " Aim for around \(Fmt.pace(pace)) per km."
        }
        if next.kind == .easy || next.kind == .long {
            text += pick(tone, kind: " Keep it comfortable, you should be able to chat the whole way.",
                         direct: " Keep it easy. If you can't talk, slow down.",
                         playful: " Chatting pace only. If you can't gossip, you're going too fast.")
        }
        return text
    }

    private static func paceText(store: AppStore, tone: CoachTone) -> String {
        guard let paces = store.plan?.paces else { return nextText(store: store, tone: tone) }
        let lead = pick(tone, kind: "Here are your paces for now.", direct: "Your paces:", playful: "Your numbers, fresh from the oven:")
        return "\(lead) Easy runs around \(Fmt.pace(paces.easy)) per km, long runs \(Fmt.pace(paces.long)), "
            + "tempo \(Fmt.pace(paces.tempo)), intervals \(Fmt.pace(paces.interval)) and race pace \(Fmt.pace(paces.race)). "
            + "Most of your running should feel easy: that's what builds the engine."
    }

    private static func goalText(store: AppStore, tone: CoachTone) -> String {
        guard let plan = store.plan else { return nextText(store: store, tone: tone) }
        var parts: [String] = []
        if let raceDate = plan.raceDate {
            let today = Calendar.plan.startOfDay(for: Date())
            let days = Calendar.plan.dateComponents([.day], from: today, to: Calendar.plan.startOfDay(for: raceDate)).day ?? 0
            if days > 1 {
                parts.append("Your \(plan.goal.title) is in \(days) days, on \(Fmt.day(raceDate, "d MMMM")).")
            } else if days >= 0 {
                parts.append("Your \(plan.goal.title) is almost here.")
            }
        } else {
            parts.append("Your goal: \(plan.goal.title.lowercased()).")
        }
        if let target = plan.targetSeconds {
            parts.append("You're aiming for \(Fmt.goalTime(target)), which means about \(Fmt.pace(plan.paces.race)) per km.")
        } else if let predicted = plan.predictedSeconds {
            parts.append("The plan is built to bring you to around \(Fmt.goalTime(predicted)).")
        }
        parts.append(pick(tone, kind: "Session after session, you're getting there. Stay regular and it will come.",
                          direct: "Do the sessions, keep the easy runs easy, and you'll be ready.",
                          playful: "Keep ticking the sessions off and the finish line won't know what hit it."))
        return parts.joined(separator: " ")
    }

    // MARK: Proposals

    private static func isHard(_ workout: Workout) -> Bool {
        workout.kind.isQuality || workout.kind == .long
    }

    /// Planned runs from today on, race excluded.
    private static func movableRuns(store: AppStore) -> [Workout] {
        let today = Calendar.plan.startOfDay(for: Date())
        return store.allWorkouts.filter {
            $0.kind.isRun && $0.kind != .race && $0.status == .planned && $0.date >= today
        }
    }

    /// The first day after the session with nothing planned (and, for a hard session, no hard neighbour).
    private static func freeDay(after workout: Workout, store: AppStore) -> Date? {
        let calendar = Calendar.plan
        let start = calendar.startOfDay(for: workout.date)
        var raceDay: Date?
        if let raceDate = store.plan?.raceDate {
            raceDay = calendar.startOfDay(for: raceDate)
        }
        var firstFree: Date?
        for offset in 1...10 {
            guard let day = calendar.date(byAdding: .day, value: offset, to: start) else { continue }
            if let raceDay, day >= raceDay { break }
            guard store.workouts(on: day).isEmpty else { continue }
            if firstFree == nil { firstFree = day }
            guard isHard(workout) else { return day }
            var hardNeighbour = false
            for delta in [-1, 1] {
                guard let neighbour = calendar.date(byAdding: .day, value: delta, to: day) else { continue }
                if store.workouts(on: neighbour).contains(where: { $0.id != workout.id && isHard($0) }) {
                    hardNeighbour = true
                }
            }
            if !hardNeighbour { return day }
        }
        return firstFree
    }

    /// About 60 % of the planned distance, at least 3 km, never more than planned.
    private static func lighter(_ km: Double) -> Double {
        let reduced = max(3, (km * 0.6).rounded())
        return km > 0 ? min(km, reduced) : reduced
    }

    private static func moveReply(store: AppStore, tone: CoachTone) -> ChatMessage {
        guard let target = movableRuns(store: store).first else {
            return ChatMessage(role: .coach, text: "There's no session left to move right now. Rest, and write to me when you're ready to go again.")
        }
        let lead = pick(tone, kind: "No problem, life comes first.",
                        direct: "Fine. We adapt.",
                        playful: "Plans are made to be wiggled a little.")
        if let day = freeDay(after: target, store: store) {
            var change = PlanChange(workoutID: target.id)
            change.newDate = day
            let proposal = PlanProposal(summary: "The rest of your plan stays as it is. Nothing changes until you apply it.",
                                        changes: [change])
            let text = "\(lead) I can move your \(target.title.lowercased()) from \(dayLabel(target.date).replacingOccurrences(of: "on ", with: "")) to \(Fmt.day(day, "EEEE d MMMM")), a day with nothing planned. Does that work for you?"
            return ChatMessage(role: .coach, text: text, proposal: proposal)
        }
        var change = PlanChange(workoutID: target.id)
        change.remove = true
        let proposal = PlanProposal(summary: "One session less won't undo your training. The rest of the plan stays as it is.",
                                    changes: [change])
        let text = "\(lead) The days after it are already full, so I'd rather let your \(target.title.lowercased()) go than squeeze it in. Shall I take it off the plan?"
        return ChatMessage(role: .coach, text: text, proposal: proposal)
    }

    private static func painReply(store: AppStore, tone: CoachTone) -> ChatMessage {
        let advice = pick(tone, kind: "Thank you for telling me. I can't tell you what it is, and I don't want you to guess either: please have it looked at by a doctor or a physiotherapist, especially if it lasts or gets worse.",
                          direct: "Don't run through pain. I can't tell you what it is: see a doctor or a physiotherapist, especially if it lasts or gets worse.",
                          playful: "Let's not play the hero with that. I can't tell you what it is, so please show it to a doctor or a physiotherapist, especially if it lasts or gets worse.")
        let runs = movableRuns(store: store)
        let easyPace = store.plan?.paces.easy ?? 390

        if let hard = runs.first(where: { isHard($0) }) {
            let km = lighter(hard.distanceKm)
            var change = PlanChange(workoutID: hard.id)
            change.newKind = .easy
            change.newTitle = "Easy run"
            change.newDistanceKm = km
            change.newDurationMin = Int((km * Double(easyPace) / 60).rounded())
            let proposal = PlanProposal(summary: "Only if it feels fine on the day. If it hurts while running, stop and walk home.",
                                        changes: [change])
            let text = advice + " In the meantime I'd swap your \(hard.title.lowercased()) \(dayLabel(hard.date)) for a short easy run."
            return ChatMessage(role: .coach, text: text, proposal: proposal)
        }
        if let next = runs.first, next.distanceKm > 3 {
            let km = lighter(next.distanceKm)
            var change = PlanChange(workoutID: next.id)
            change.newDistanceKm = km
            change.newDurationMin = Int((km * Double(easyPace) / 60).rounded())
            let proposal = PlanProposal(summary: "Only if it feels fine on the day. If it hurts while running, stop and walk home.",
                                        changes: [change])
            let text = advice + " In the meantime I'd shorten your run \(dayLabel(next.date))."
            return ChatMessage(role: .coach, text: text, proposal: proposal)
        }
        return ChatMessage(role: .coach, text: advice + " Skip your next run if you're in doubt: a few days off cost far less than an injury.")
    }
}
