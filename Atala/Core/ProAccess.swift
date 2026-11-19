import Foundation

/// What is free and what needs Atala Pro (a subscription, its free trial included).
///
/// Free: the sessions of the first week, without the coach's voice; free runs with GPS; the simple
/// run summary and the history; streaks; a few coach messages a day.
/// Pro: the rest of the plan, the coach's voice, guided strength and stretching, the detailed run
/// figures, moving sessions, and the coach chat without a daily limit.
@MainActor
enum ProAccess {
    /// Coach messages a day without Atala Pro.
    static let freeCoachMessagesPerDay = 3

    private static let freeWeekEndKey = "atala.pro.freeWeekEnd"

    static var isPro: Bool { PurchaseService.shared.isPro }

    /// End of the free week: the Monday after the first week of the first plan built on this phone.
    /// Set once, so rebuilding the plan does not start another free week.
    static func freeWeekEnd(plan: TrainingPlan?) -> Date? {
        let defaults = UserDefaults.standard
        let saved = defaults.double(forKey: freeWeekEndKey)
        if saved > 0 { return Date(timeIntervalSince1970: saved) }
        guard let first = plan?.weeks.first else { return nil }
        let calendar = Calendar.plan
        let start = calendar.startOfDay(for: first.startDate)
        guard let end = calendar.date(byAdding: .day, value: 7, to: start) else { return nil }
        defaults.set(end.timeIntervalSince1970, forKey: freeWeekEndKey)
        return end
    }

    /// True when the session is after the free week and the user has no subscription.
    static func isLocked(_ workout: Workout, plan: TrainingPlan?) -> Bool {
        guard !isPro else { return false }
        guard let end = freeWeekEnd(plan: plan) else { return false }
        return workout.date >= end
    }

    /// The coach's voice is part of Atala Pro: without it the cues are shown but not spoken.
    static func voice(_ settings: VoiceSettings) -> VoiceSettings {
        guard !isPro else { return settings }
        var silent = settings
        silent.enabled = false
        return silent
    }

    /// Messages the user sent to the coach today.
    static func coachMessagesToday(in messages: [ChatMessage]) -> Int {
        messages.filter { $0.role == .user && Calendar.plan.isDateInToday($0.date) }.count
    }

    static func canMessageCoach(_ messages: [ChatMessage]) -> Bool {
        isPro || coachMessagesToday(in: messages) < freeCoachMessagesPerDay
    }
}
