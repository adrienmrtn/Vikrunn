import Foundation

/// The paywall's personal reminder, built from the answers and the plan. Every case is one whole
/// sentence, so each can be edited on its own. Nothing here promises a result: it only restates
/// the goal the user set and what the plan contains.
enum OnboardingPaywallCopy {
    /// "1:45" for a round time over an hour, "1:45:30" otherwise, "27:30" under the hour.
    static func targetTime(_ seconds: Int) -> String {
        let total = max(0, seconds)
        let hours = total / 3600
        let minutes = (total % 3600) / 60
        let secs = total % 60
        if hours > 0 {
            return secs == 0
                ? String(format: "%d:%02d", hours, minutes)
                : String(format: "%d:%02d:%02d", hours, minutes, secs)
        }
        return String(format: "%d:%02d", minutes, secs)
    }

    /// The race day, when the user gave one: the plan's own date first, the answer otherwise.
    static func raceDay(answers: OnboardingAnswers, plan: TrainingPlan?) -> String? {
        if let date = plan?.raceDate {
            return Fmt.day(date, "d MMMM")
        }
        if answers.hasRace, let date = answers.raceDate {
            return Fmt.day(date, "d MMMM")
        }
        return nil
    }

    /// First line: the goal in the user's own terms.
    static func goalLine(answers: OnboardingAnswers, plan: TrainingPlan?) -> String {
        let goal = answers.goal ?? plan?.goal ?? .regular
        if goal == .regular {
            switch answers.runDays.count {
            case 0: return "Running every week, for good."
            case 1: return "Running once a week, for good."
            case 2: return "Running twice a week, for good."
            default: return "Running \(answers.runDays.count) times a week, for good."
            }
        }
        let noun = OnboardingCopy.goalNoun(goal)
        let day = raceDay(answers: answers, plan: plan)
        if let target = answers.targetSeconds ?? plan?.targetSeconds {
            let time = targetTime(target)
            if let day {
                return "Your \(noun) in \(time), on \(day)."
            }
            return "Your \(noun) in \(time)."
        }
        if let day {
            return "Your \(noun), on \(day)."
        }
        return "Your \(noun), one session at a time."
    }

    /// Second line: the plan and the coach.
    static func planLine(coachName: String) -> String {
        "Your plan is ready, with \(coachName) by your side."
    }

    /// Third line: why they run. Nil when the question was not answered.
    static func motivationLine(_ motivation: Motivation?) -> String? {
        guard let motivation else { return nil }
        switch motivation {
        case .athletic: return "Because you want an athletic body."
        case .prove: return "Because you want to prove to yourself you can do it."
        case .record: return "Because you want to beat your record."
        case .health: return "Because you want to take care of your health."
        case .clearHead: return "Because you want to clear your head."
        case .together: return "Because you want to run with people you love."
        }
    }
}
