import Foundation

/// Answers built on the phone from the plan, used when the coach service can't be reached
/// (guest account, no network, server error). Same rules: never a diagnosis, changes only as proposals.
@MainActor
enum CoachLocalReplies {
    static func reply(to text: String, store: AppStore) -> ChatMessage {
        // The iOS keyboard types curly apostrophes: "can’t" must read like "can't".
        let lowered = text.lowercased().replacingOccurrences(of: "\u{2019}", with: "'")
        let words = lowered.components(separatedBy: CharacterSet.alphanumerics.inverted).filter { !$0.isEmpty }
        let tone = store.answers.tone

        if store.plan == nil {
            return ChatMessage(role: .coach, text: "Your plan isn't ready yet. Finish setting it up and I'll have every session at hand to help you.")
        }
        if let proposed = CoachLocalProposals.reply(words: words, lowered: lowered, store: store, tone: tone) {
            return proposed
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

    static func matches(_ words: [String], _ stems: [String]) -> Bool {
        words.contains { word in stems.contains { word.hasPrefix($0) } }
    }

    static func containsAny(_ text: String, _ phrases: [String]) -> Bool {
        phrases.contains { text.contains($0) }
    }

    static func pick(_ tone: CoachTone, kind: String, direct: String, playful: String) -> String {
        switch tone {
        case .kind: return kind
        case .direct: return direct
        case .playful: return playful
        }
    }

    // MARK: Plain answers

    static func dayLabel(_ date: Date) -> String {
        let calendar = Calendar.plan
        if calendar.isDateInToday(date) { return "today" }
        if calendar.isDateInTomorrow(date) { return "tomorrow" }
        return "on " + Fmt.day(date, "EEEE d MMMM")
    }

    static func describe(_ workout: Workout) -> String {
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
}
