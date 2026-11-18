import Foundation

/// Plan changes proposed from the phone when the coach service can't be reached.
/// Every change points at a real session of the current plan and is applied only after the user accepts.
@MainActor
enum CoachLocalProposals {
    private static let pendingNote = "The rest of your plan stays as it is. Nothing changes until you tap Apply."

    /// A reply with a proposal when the message calls for a change of plan, nil otherwise.
    static func reply(words: [String], lowered: String, store: AppStore, tone: CoachTone) -> ChatMessage? {
        guard let intent = CoachLocalIntent.detect(words: words, lowered: lowered) else { return nil }
        let days = CoachLocalIntent.mentionedDays(words: words, lowered: lowered)
        switch intent {
        case .pain:
            return painReply(store: store, tone: tone)
        case .skip:
            return skipReply(days: days, store: store, tone: tone)
        case .sick, .tired, .busy:
            return moveReply(intent: intent, days: days, store: store, tone: tone)
        }
    }

    /// Suggestions shown in the chat. The first ones always lead to a proposal on the current plan.
    static func suggestedPrompts(store: AppStore) -> [String] {
        let runs = movableRuns(store: store)
        guard let first = runs.first else {
            return ["What's my next session?", "What pace should my easy runs be?", "Am I on track for my goal?"]
        }
        let calendar = Calendar.plan
        let today = calendar.startOfDay(for: Date())
        let todayIndex = calendar.mondayIndex(of: today)
        var prompts: [String] = ["I'm tired today, can we move my session?"]

        var weekend: [Date] = []
        for offset in [max(0, 6 - todayIndex), 7 - todayIndex] {
            if let day = calendar.date(byAdding: .day, value: offset, to: today) { weekend.append(day) }
        }
        let runsThisWeekend = runs.contains { run in
            weekend.contains { calendar.isDate($0, inSameDayAs: run.date) }
        }
        if runsThisWeekend {
            prompts.append("I'm travelling this weekend")
        } else {
            // A run of the coming days, named by its weekday so the coach finds it.
            let limit = calendar.date(byAdding: .day, value: 7, to: today) ?? today
            let soon = runs.first { $0.date > today && $0.date < limit } ?? first
            prompts.append("I can't run on \(Weekday.long(calendar.mondayIndex(of: soon.date)))")
        }
        prompts.append("My knee hurts a little")
        prompts.append("What's my next session?")
        return prompts
    }

    // MARK: Sessions and days

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

    /// The runs planned on the days the user named, or the next run when there is none on those days.
    private static func targets(days: [Date], store: AppStore) -> (runs: [Workout], matched: Bool) {
        let calendar = Calendar.plan
        let runs = movableRuns(store: store)
        let onDays = runs.filter { run in
            days.contains { calendar.isDate($0, inSameDayAs: run.date) }
        }
        if !onDays.isEmpty { return (Array(onDays.prefix(3)), true) }
        return (Array(runs.prefix(1)), false)
    }

    private static func hasHardNeighbour(_ day: Date, excluding id: UUID, store: AppStore) -> Bool {
        for delta in [-1, 1] {
            guard let neighbour = Calendar.plan.date(byAdding: .day, value: delta, to: day) else { continue }
            if store.workouts(on: neighbour).contains(where: { $0.id != id && isHard($0) }) { return true }
        }
        return false
    }

    /// The best day to run the session instead: no other run that day, not a blocked day, before the race,
    /// as close as possible to the original day, in the same week when it can, away from other hard sessions.
    private static func destination(for workout: Workout, earliest: Date, blocked: Set<Date>,
                                    store: AppStore) -> Date? {
        let calendar = Calendar.plan
        let today = calendar.startOfDay(for: Date())
        let original = calendar.startOfDay(for: workout.date)
        let start = max(today, calendar.startOfDay(for: earliest))
        guard let last = calendar.date(byAdding: .day, value: 7, to: original) else { return nil }
        var raceDay: Date?
        if let raceDate = store.plan?.raceDate {
            raceDay = calendar.startOfDay(for: raceDate)
        }
        let originalWeek = calendar.mondayOfWeek(containing: original)
        var best: Date?
        var bestScore = Double.greatestFiniteMagnitude

        for offset in 0...21 {
            guard let date = calendar.date(byAdding: .day, value: offset, to: start) else { continue }
            let day = calendar.startOfDay(for: date)
            if day > last { break }
            if let raceDay, day >= raceDay { break }
            if day == original || blocked.contains(day) { continue }
            let others = store.workouts(on: day).filter { $0.id != workout.id }
            if others.contains(where: { $0.kind.isRun }) { continue }

            let distance = abs(calendar.dateComponents([.day], from: original, to: day).day ?? 0)
            var score = Double(distance)
            if day < original { score += 0.5 }
            if day == today { score += 1 }
            if !others.isEmpty { score += 0.75 }
            if calendar.mondayOfWeek(containing: day) != originalWeek { score += 1.5 }
            if isHard(workout), hasHardNeighbour(day, excluding: workout.id, store: store) { score += 3 }
            if score < bestScore {
                bestScore = score
                best = day
            }
        }
        return best
    }

    /// About 60 % of the planned distance, at least 3 km, never more than planned.
    private static func lighter(_ km: Double) -> Double {
        let reduced = max(3, (km * 0.6).rounded())
        return km > 0 ? min(km, reduced) : reduced
    }

    // MARK: Wording

    private static func dayName(_ date: Date) -> String {
        let calendar = Calendar.plan
        if calendar.isDateInToday(date) { return "today" }
        if calendar.isDateInTomorrow(date) { return "tomorrow" }
        return Fmt.day(date, "EEEE d MMMM")
    }

    private static func daysText(_ days: [Date]) -> String {
        let labels = days.map { CoachLocalReplies.dayLabel($0) }
        switch labels.count {
        case 1: return labels[0]
        case 2: return "\(labels[0]) or \(labels[1])"
        default: return "on those days"
        }
    }

    private static func lead(_ intent: CoachLocalIntent, tone: CoachTone) -> String {
        switch intent {
        case .sick:
            return CoachLocalReplies.pick(tone,
                kind: "Rest comes first: no running while you feel unwell. If you have a fever, or it doesn't pass in a few days, please see a doctor.",
                direct: "No running while you're unwell. If you have a fever, or it doesn't pass in a few days, see a doctor.",
                playful: "Sofa first, shoes later. If you have a fever, or it doesn't pass in a few days, please see a doctor.")
        case .tired:
            return CoachLocalReplies.pick(tone,
                kind: "Thanks for telling me. Rest counts as training too.",
                direct: "Then rest. We move it.",
                playful: "Tired legs get a veto.")
        case .pain, .skip, .busy:
            return CoachLocalReplies.pick(tone,
                kind: "No problem, life comes first.",
                direct: "Fine. We adapt.",
                playful: "Plans are made to be wiggled a little.")
        }
    }

    // MARK: Replies

    private static func moveReply(intent: CoachLocalIntent, days: [Date], store: AppStore,
                                  tone: CoachTone) -> ChatMessage {
        let picked = targets(days: days, store: store)
        guard !picked.runs.isEmpty else {
            return ChatMessage(role: .coach, text: "There's no session left to move right now. Rest, and write to me when you're ready to go again.")
        }
        let calendar = Calendar.plan
        let today = calendar.startOfDay(for: Date())
        var blocked = Set<Date>()
        for day in days { blocked.insert(calendar.startOfDay(for: day)) }
        for run in picked.runs { blocked.insert(calendar.startOfDay(for: run.date)) }
        if intent == .tired || intent == .sick { blocked.insert(today) }
        if intent == .sick, let tomorrow = calendar.date(byAdding: .day, value: 1, to: today) {
            blocked.insert(calendar.startOfDay(for: tomorrow))
        }

        var changes: [PlanChange] = []
        var moved: [String] = []
        var dropped: [String] = []
        for run in picked.runs {
            let runDay = calendar.startOfDay(for: run.date)
            // A session only moves earlier when the user named the days they can't run.
            var earliest = calendar.date(byAdding: .day, value: 1, to: runDay) ?? runDay
            if picked.matched && intent == .busy { earliest = today }
            var change = PlanChange(workoutID: run.id)
            if let day = destination(for: run, earliest: earliest, blocked: blocked, store: store) {
                change.newDate = day
                blocked.insert(day)
                moved.append("your \(run.title.lowercased()) from \(dayName(run.date)) to \(dayName(day))")
            } else {
                change.remove = true
                dropped.append("your \(run.title.lowercased()) \(CoachLocalReplies.dayLabel(run.date))")
            }
            changes.append(change)
        }

        var text = lead(intent, tone: tone) + " "
        if !picked.matched && !days.isEmpty {
            text += "You have no run planned \(daysText(days)), so nothing is in the way there. Still, if it helps, "
        }
        var sentences: [String] = []
        if !moved.isEmpty {
            sentences.append("I can move " + moved.joined(separator: " and ") + ".")
        }
        if !dropped.isEmpty {
            let pronoun = dropped.count == 1 ? "it" : "them"
            sentences.append("I'd rather let " + dropped.joined(separator: " and ")
                             + " go than squeeze \(pronoun) in: the days around are already full.")
        }
        text += sentences.joined(separator: " ") + " Does that work for you?"
        return ChatMessage(role: .coach, text: text,
                           proposal: PlanProposal(summary: pendingNote, changes: changes))
    }

    private static func skipReply(days: [Date], store: AppStore, tone: CoachTone) -> ChatMessage {
        let picked = targets(days: days, store: store)
        guard !picked.runs.isEmpty else {
            return ChatMessage(role: .coach, text: "There's no session left to take off right now. Rest, and write to me when you're ready to go again.")
        }
        var changes: [PlanChange] = []
        var names: [String] = []
        for run in picked.runs {
            var change = PlanChange(workoutID: run.id)
            change.remove = true
            changes.append(change)
            names.append("your \(run.title.lowercased()) \(CoachLocalReplies.dayLabel(run.date))")
        }
        var text = CoachLocalReplies.pick(tone, kind: "Understood, no guilt needed.",
                                          direct: "Understood.",
                                          playful: "Permission granted.") + " "
        if !picked.matched && !days.isEmpty {
            text += "You have no run planned \(daysText(days)). Your next one is the session below. "
        }
        text += "I can take " + names.joined(separator: " and ")
            + " off the plan. One session less won't undo your training. Shall I?"
        return ChatMessage(role: .coach, text: text,
                           proposal: PlanProposal(summary: pendingNote, changes: changes))
    }

    private static func painReply(store: AppStore, tone: CoachTone) -> ChatMessage {
        let advice = CoachLocalReplies.pick(tone,
            kind: "Thank you for telling me. I can't tell you what it is, and I don't want you to guess either: please have it looked at by a doctor or a physiotherapist, especially if it lasts or gets worse.",
            direct: "Don't run through pain. I can't tell you what it is: see a doctor or a physiotherapist, especially if it lasts or gets worse.",
            playful: "Let's not play the hero with that. I can't tell you what it is, so please show it to a doctor or a physiotherapist, especially if it lasts or gets worse.")
        let note = "Only if it feels fine on the day. If it hurts while running, stop and walk home."
        let runs = movableRuns(store: store)
        let easyPace = store.plan?.paces.easy ?? 390

        if let hard = runs.first(where: { isHard($0) }) {
            let km = lighter(hard.distanceKm)
            var change = PlanChange(workoutID: hard.id)
            change.newKind = .easy
            change.newTitle = "Easy run"
            change.newDistanceKm = km
            change.newDurationMin = Int((km * Double(easyPace) / 60).rounded())
            let text = advice + " In the meantime I'd swap your \(hard.title.lowercased()) \(CoachLocalReplies.dayLabel(hard.date)) for a short easy run."
            return ChatMessage(role: .coach, text: text, proposal: PlanProposal(summary: note, changes: [change]))
        }
        if let next = runs.first, next.distanceKm > 3 {
            let km = lighter(next.distanceKm)
            var change = PlanChange(workoutID: next.id)
            change.newDistanceKm = km
            change.newDurationMin = Int((km * Double(easyPace) / 60).rounded())
            let text = advice + " In the meantime I'd shorten your run \(CoachLocalReplies.dayLabel(next.date))."
            return ChatMessage(role: .coach, text: text, proposal: PlanProposal(summary: note, changes: [change]))
        }
        return ChatMessage(role: .coach, text: advice + " Skip your next run if you're in doubt: a few days off cost far less than an injury.")
    }
}
