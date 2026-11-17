import Foundation

/// What the user is asking for when a message calls for a change of plan, read from its words.
/// Used by the answers built on the phone (guest, offline, server not configured).
enum CoachLocalIntent {
    /// Something hurts: lighten the next session, and send the user to a professional.
    case pain
    /// Unwell: rest first, push the next session back.
    case sick
    /// The user asks to drop a session.
    case skip
    /// Tired, slept badly: move the session to a later day.
    case tired
    /// Busy, travelling, can't run on a day, or simply asking to move a session.
    case busy

    /// `words` are the lowercased words of the message, `lowered` the whole lowercased message
    /// with straight apostrophes.
    static func detect(words: [String], lowered: String) -> CoachLocalIntent? {
        if hasStem(words, painStems) || hasPhrase(lowered, painPhrases) { return .pain }
        if hasStem(words, sickStems) || hasWord(words, sickWords) || hasPhrase(lowered, sickPhrases) { return .sick }
        if hasStem(words, skipStems) || hasPhrase(lowered, skipPhrases) { return .skip }
        if hasStem(words, tiredStems) || hasPhrase(lowered, tiredPhrases) { return .tired }
        if hasStem(words, busyStems) || hasWord(words, busyWords) || hasPhrase(lowered, busyPhrases) { return .busy }
        return nil
    }

    /// The days named in the message (today, tomorrow, the weekend, a weekday), from today on, sorted.
    static func mentionedDays(words: [String], lowered: String) -> [Date] {
        let calendar = Calendar.plan
        let today = calendar.startOfDay(for: Date())
        let todayIndex = calendar.mondayIndex(of: today)
        var offsets: [Int] = []

        if hasWord(words, ["today", "tonight"])
            || hasPhrase(lowered, ["this morning", "this afternoon", "this evening"]) {
            offsets.append(0)
        }
        if hasWord(words, ["tomorrow"]) {
            offsets.append(1)
        }
        if hasWord(words, ["weekend", "weekends"]) {
            // Saturday and Sunday of the current week (only Sunday is left when today is Sunday).
            offsets.append(max(0, 6 - todayIndex))
            offsets.append(7 - todayIndex)
        }
        for index in weekdayNames.indices where hasWord(words, weekdayNames[index]) {
            // Next occurrence of that weekday, today included.
            offsets.append((index + 1 - todayIndex + 7) % 7)
        }

        var days: [Date] = []
        for offset in offsets.sorted() {
            guard let date = calendar.date(byAdding: .day, value: offset, to: today) else { continue }
            let day = calendar.startOfDay(for: date)
            if !days.contains(day) { days.append(day) }
        }
        return days
    }

    // MARK: Keywords

    /// Prefixes: "injur" matches "injury" and "injured".
    private static let painStems = ["pain", "hurt", "injur", "sore", "ache", "aching", "knee", "ankle", "shin", "calf",
                                    "calves", "achilles", "hamstring", "sprain", "strain", "twinge", "cramp",
                                    "swollen", "limp", "tendon", "blister", "stiff", "niggl", "tweak", "dizzy"]
    private static let painPhrases = ["pulled a muscle", "pulled muscle", "pulled my", "chest pain"]

    private static let sickStems = ["sick", "fever", "unwell", "nause", "migraine", "headache", "vomit", "covid",
                                    "poorly", "coughing"]
    private static let sickWords = ["ill", "flu"]
    private static let sickPhrases = ["a cold", "under the weather", "not feeling well", "don't feel well",
                                      "dont feel well", "feeling rough", "sore throat"]

    private static let skipStems = ["skip", "cancel"]
    private static let skipPhrases = ["drop my", "drop the", "drop today", "drop tomorrow", "take it off",
                                      "off the plan", "off my plan", "remove my", "remove the", "delete my",
                                      "delete the"]

    private static let tiredStems = ["tired", "exhaust", "fatigue", "knackered", "shattered", "drained", "sleepy",
                                     "hungover", "jetlag", "insomnia", "wiped"]
    private static let tiredPhrases = ["slept badly", "slept poorly", "bad night", "rough night", "short night",
                                       "didn't sleep", "didnt sleep", "did not sleep", "not slept", "no sleep",
                                       "little sleep", "poor sleep", "worn out", "burnt out", "burned out",
                                       "heavy legs", "dead legs", "no energy", "low energy", "jet lag",
                                       "need a rest", "need rest", "need a break", "day off", "rest today"]

    private static let busyStems = ["busy", "travel", "holiday", "vacation", "wedding", "abroad", "overtime",
                                    "move", "moving", "postpone", "reschedul", "swap", "shift", "delay",
                                    "conference", "flight", "raining", "snowing", "storm"]
    private static let busyWords = ["trip", "trips", "miss", "missing", "rain", "snow", "icy"]
    private static let busyPhrases = ["can't run", "cant run", "cannot run", "can not run", "won't run", "wont run",
                                      "can't make", "cant make", "cannot make", "can't train", "cant train",
                                      "cannot train", "can't go", "cant go", "can't do", "cant do", "cannot do",
                                      "won't be able", "wont be able", "not able to", "unable to", "no time",
                                      "not enough time", "don't have time", "dont have time", "work late",
                                      "working late", "push back", "put off", "another day", "other day",
                                      "different day", "do it later", "out of town", "on the road",
                                      "i'm away", "im away", "i am away", "be away", "going away",
                                      "too hot", "too cold"]

    private static let weekdayNames: [[String]] = [
        ["monday", "mondays", "mon"],
        ["tuesday", "tuesdays", "tue", "tues"],
        ["wednesday", "wednesdays"],
        ["thursday", "thursdays", "thu", "thur", "thurs"],
        ["friday", "fridays", "fri"],
        ["saturday", "saturdays"],
        ["sunday", "sundays"],
    ]

    private static func hasStem(_ words: [String], _ stems: [String]) -> Bool {
        words.contains { word in stems.contains { word.hasPrefix($0) } }
    }

    private static func hasWord(_ words: [String], _ list: [String]) -> Bool {
        words.contains { list.contains($0) }
    }

    private static func hasPhrase(_ text: String, _ phrases: [String]) -> Bool {
        phrases.contains { text.contains($0) }
    }
}
