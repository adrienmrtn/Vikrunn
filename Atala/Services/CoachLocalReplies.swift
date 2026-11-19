import Foundation

/// Answers built on the phone from the plan, used when the coach service can't be reached
/// (guest account, no network, server error). Same rules: never a diagnosis, changes only as proposals.
@MainActor
enum CoachLocalReplies {
    static func reply(to text: String, store: AppStore) -> ChatMessage {
        // The iOS keyboard types curly apostrophes: "can’t" must read like "can't".
        let canonical = CoachLocalProposals.canonical(text)
        CoachWords.readsEnglish = canonical != text
        defer { CoachWords.readsEnglish = false }
        let lowered = canonical.lowercased().replacingOccurrences(of: "\u{2019}", with: "'")
        let words = lowered.components(separatedBy: CharacterSet.alphanumerics.inverted).filter { !$0.isEmpty }
        let tone = store.answers.tone

        if store.plan == nil {
            return ChatMessage(role: .coach, text: L("Your plan isn't ready yet. Finish setting it up and I'll have every session at hand to help you."))
        }
        if let proposed = CoachLocalProposals.reply(words: words, lowered: lowered, store: store, tone: tone) {
            return proposed
        }
        if words.count <= 4, matches(words, CoachWords.pick(["thank", "thanks", "cheers", "great", "perfect"], "thanks_stems")) {
            return ChatMessage(role: .coach, text: pick(tone, kind: L("Any time. I'm here whenever you need me."),
                                                        direct: L("Good. Back to it."),
                                                        playful: L("That's what I'm here for. Now go and be brilliant.")))
        }
        if matches(words, CoachWords.pick(["pace", "paces", "fast", "faster", "slow", "slower", "speed"], "pace_stems")) {
            return ChatMessage(role: .coach, text: paceText(store: store, tone: tone))
        }
        if matches(words, CoachWords.pick(["race", "goal", "target", "ready", "predict", "finish"], "goal_stems")) {
            return ChatMessage(role: .coach, text: goalText(store: store, tone: tone))
        }
        if matches(words, CoachWords.pick(["next", "today", "tomorrow", "tonight", "week", "session", "workout", "plan", "schedule", "when"], "next_stems")) {
            return ChatMessage(role: .coach, text: nextText(store: store, tone: tone))
        }
        if matches(words, CoachWords.pick(["motivat", "lazy", "bored", "quit", "struggl", "discourag", "hard", "doubt"], "motivation_stems")) {
            let lead = pick(tone, kind: L("Some days are heavier than others, and that's normal."),
                            direct: L("Motivation comes and goes. The habit stays."),
                            playful: L("Even the best legs have grumpy days."))
            let start: String = L("Just start the first ten minutes easy and see how you feel.")
            let next: String = nextText(store: store, tone: tone)
            return ChatMessage(role: .coach, text: [lead, start, next].joined(separator: " "))
        }
        if words.count <= 3, matches(words, CoachWords.pick(["hi", "hello", "hey", "morning", "evening"], "greeting_stems")) {
            let greeting: String = L("Hi %@.", store.firstName)
            return ChatMessage(role: .coach, text: greeting + " " + nextText(store: store, tone: tone))
        }
        let lead = pick(tone, kind: L("I'm with you."), direct: L("Here's where you stand."), playful: L("Let's keep it simple."))
        let offer: String = L("If something gets in the way of a session, tell me and I'll suggest how to move it.")
        return ChatMessage(role: .coach, text: [lead, nextText(store: store, tone: tone), offer].joined(separator: " "))
    }

    // MARK: Keywords

    static func matches(_ words: [String], _ stems: [String]) -> Bool {
        // Japanese is written without spaces: a "word" is a whole clause, searched anywhere.
        let anywhere = AppLanguage.current == "ja" && !CoachWords.readsEnglish
        return words.contains { word in stems.contains { anywhere ? word.contains($0) : word.hasPrefix($0) } }
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

    /// "A and B": the items of a sentence, joined in the app's language.
    static func joined(_ items: [String]) -> String {
        guard var text = items.first else { return "" }
        for item in items.dropFirst() {
            text = L("%@ and %@", text, item)
        }
        return text
    }

    // MARK: Plain answers

    static func dayLabel(_ date: Date) -> String {
        let calendar = Calendar.plan
        if calendar.isDateInToday(date) { return L("today") }
        if calendar.isDateInTomorrow(date) { return L("tomorrow") }
        return L("on %@", Fmt.day(date, "EEEE d MMMM"))
    }

    static func describe(_ workout: Workout) -> String {
        var text = workout.title
        if workout.kind.isRun, workout.distanceKm > 0 {
            text += ", " + Fmt.km(workout.distanceKm)
        }
        if workout.durationMin > 0 {
            text += ", " + L("about %@", Fmt.minutes(workout.durationMin))
        }
        return text
    }

    private static func nextText(store: AppStore, tone: CoachTone) -> String {
        guard let next = store.nextRun else {
            return L("There's no run left on your plan for now. Enjoy the rest, you've earned it.")
        }
        var text = L("Your next run is %@: %@.", dayLabel(next.date), describe(next))
        if let pace = next.targetPace, pace > 0 {
            text += " " + L("Aim for around %@ per km.", Fmt.pace(pace))
        }
        if next.kind == .easy || next.kind == .long {
            text += " " + pick(tone, kind: L("Keep it comfortable, you should be able to chat the whole way."),
                         direct: L("Keep it easy. If you can't talk, slow down."),
                         playful: L("Chatting pace only. If you can't gossip, you're going too fast."))
        }
        return text
    }

    private static func paceText(store: AppStore, tone: CoachTone) -> String {
        guard let paces = store.plan?.paces else { return nextText(store: store, tone: tone) }
        let lead = pick(tone, kind: L("Here are your paces for now."), direct: L("Your paces:"), playful: L("Your numbers, fresh from the oven:"))
        let numbers: String = L("Easy runs around %@ per km, long runs %@, tempo %@, intervals %@ and race pace %@.",
                                Fmt.pace(paces.easy), Fmt.pace(paces.long), Fmt.pace(paces.tempo),
                                Fmt.pace(paces.interval), Fmt.pace(paces.race))
        let advice: String = L("Most of your running should feel easy: that's what builds the engine.")
        return [lead, numbers, advice].joined(separator: " ")
    }

    private static func goalText(store: AppStore, tone: CoachTone) -> String {
        guard let plan = store.plan else { return nextText(store: store, tone: tone) }
        var parts: [String] = []
        if let raceDate = plan.raceDate {
            let today = Calendar.plan.startOfDay(for: Date())
            let days = Calendar.plan.dateComponents([.day], from: today, to: Calendar.plan.startOfDay(for: raceDate)).day ?? 0
            if days > 1 {
                parts.append(L("Your %@ is in %@ days, on %@.", plan.goal.title, days, Fmt.day(raceDate, "d MMMM")))
            } else if days >= 0 {
                parts.append(L("Your %@ is almost here.", plan.goal.title))
            }
        } else {
            parts.append(L("Your goal: %@.", plan.goal.title.midSentence))
        }
        if let target = plan.targetSeconds {
            parts.append(L("You're aiming for %@, which means about %@ per km.", Fmt.goalTime(target), Fmt.pace(plan.paces.race)))
        } else if let predicted = plan.predictedSeconds {
            parts.append(L("The plan is built to bring you to around %@.", Fmt.goalTime(predicted)))
        }
        parts.append(pick(tone, kind: L("Session after session, you're getting there. Stay regular and it will come."),
                          direct: L("Do the sessions, keep the easy runs easy, and you'll be ready."),
                          playful: L("Keep ticking the sessions off and the finish line won't know what hit it.")))
        return parts.joined(separator: " ")
    }
}
