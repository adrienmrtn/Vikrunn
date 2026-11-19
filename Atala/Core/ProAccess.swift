import Foundation

/// What is free and what needs Atala Pro (a subscription, its free trial included).
///
/// Free, for good: the whole plan and its sessions, runs with GPS, the run summary, guided strength
/// and stretching, streaks.
/// Pro: talking to the coach, the Progress tab, the coach's voice, the detailed run figures.
@MainActor
enum ProAccess {
    static var isPro: Bool { PurchaseService.shared.isPro }

    /// Sessions are never locked: the plan is free. Kept so older call sites read the same.
    static func isLocked(_ workout: Workout, plan: TrainingPlan?) -> Bool {
        false
    }

    /// Nothing to reset any more (there used to be a free week).
    static func reset() {
        UserDefaults.standard.removeObject(forKey: "atala.pro.freeWeekEnd")
    }

    /// The coach's voice is part of Atala Pro: without it the cues are shown but not spoken.
    static func voice(_ settings: VoiceSettings) -> VoiceSettings {
        guard !isPro else { return settings }
        var silent = settings
        silent.enabled = false
        return silent
    }

    /// Talking to the coach is part of Atala Pro.
    static func canMessageCoach(_ messages: [ChatMessage]) -> Bool {
        isPro
    }
}
