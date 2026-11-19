import Foundation
import UserNotifications

/// Local reminders for the sessions of the plan, on the morning of each one.
///
/// Who gets them: people on the free plan, and subscribers who are past their free trial.
/// Someone in the middle of a free trial gets none, and nothing ever reminds anyone that a
/// trial is about to end.
@MainActor
enum RunReminders {
    /// Hour of the morning reminder, in the phone's own time zone.
    static let hour = 8
    /// How many sessions ahead are scheduled. Rescheduled every time the app opens.
    static let horizon = 12

    private static let prefix = "atala.reminder."

    /// Asks for the permission to send notifications. True when it was granted.
    static func requestAuthorization() async -> Bool {
        let center = UNUserNotificationCenter.current()
        let granted = try? await center.requestAuthorization(options: [.alert, .sound])
        return granted ?? false
    }

    static func isAuthorized() async -> Bool {
        let status = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
        return status == .authorized || status == .provisional
    }

    private static let lockNudgeKey = "atala.reminder.lockNudgeSent"

    /// Removes Atala's pending reminders and schedules the coming ones again, from the plan as it
    /// is now. Safe to call often: on launch, when the plan changes, when the subscription changes.
    static func refresh(store: AppStore) async {
        let center = UNUserNotificationCenter.current()
        // Everything that waits comes first. From here to the end nothing is awaited, so two
        // refreshes can never interleave: the last one to run decides what is scheduled.
        let authorized = await isAuthorized()
        let pending = await center.pendingNotificationRequests()
        if Task.isCancelled { return }

        let ours = pending.map { $0.identifier }.filter { $0.hasPrefix(prefix) }
        if !ours.isEmpty { center.removePendingNotificationRequests(withIdentifiers: ours) }

        let purchases = PurchaseService.shared
        // A subscriber never needs the "your plan is waiting" nudge again.
        if purchases.isPro { UserDefaults.standard.set(false, forKey: lockNudgeKey) }

        guard authorized, store.hasCompletedOnboarding, let plan = store.plan else { return }
        // No reminder of any kind during a free trial.
        guard !purchases.isInTrial else { return }

        let calendar = Calendar.plan
        let now = Date()
        let coach = store.coach.name
        var scheduled = 0
        var daysTaken = Set<Date>()

        for workout in plan.allWorkouts where workout.status == .planned {
            guard scheduled < horizon else { break }
            // One reminder a day: the first session of the day (runs come first in the plan).
            let day = calendar.startOfDay(for: workout.date)
            guard !daysTaken.contains(day) else { continue }
            var parts = calendar.dateComponents([.year, .month, .day], from: workout.date)
            parts.calendar = calendar
            parts.hour = hour
            parts.minute = 0
            guard let fireDate = calendar.date(from: parts), fireDate > now else { continue }

            let content = UNMutableNotificationContent()
            content.sound = .default
            if ProAccess.isLocked(workout, plan: plan) {
                // Free plan, after the free week: one nudge ever, then silence.
                guard !UserDefaults.standard.bool(forKey: lockNudgeKey) else { break }
                UserDefaults.standard.set(true, forKey: lockNudgeKey)
                content.title = L("Your plan is waiting")
                content.body = L("%@ is next. Unlock Atala Pro and pick up where you left off.", workout.title)
            } else {
                content.title = title(for: workout)
                content.body = body(for: workout, coach: coach)
            }
            let trigger = UNCalendarNotificationTrigger(dateMatching: parts, repeats: false)
            let request = UNNotificationRequest(identifier: prefix + workout.id.uuidString,
                                                content: content, trigger: trigger)
            center.add(request, withCompletionHandler: nil)
            daysTaken.insert(day)
            scheduled += 1
        }
    }

    /// "Easy run · 5 km today"
    private static func title(for workout: Workout) -> String {
        if workout.kind.isRun, workout.distanceKm > 0 {
            return L("%@ · %@ today", workout.title, Fmt.km(workout.distanceKm))
        }
        return L("%@ today", workout.title)
    }

    private static func body(for workout: Workout, coach: String) -> String {
        switch workout.kind {
        case .easy, .free:
            return L("%@ here. Keep it easy today: you should be able to talk the whole way.", coach)
        case .long:
            return L("%@ here. Long run day: slow and steady, the distance does the work.", coach)
        case .intervals, .tempo, .racePace:
            return L("%@ here. A quality session today. Warm up well, I'll guide you through it.", coach)
        case .race:
            return L("%@ here. This is your day. Start calm and trust your training.", coach)
        case .strength:
            return L("%@ here. A short strength session today: it keeps you running without pain.", coach)
        case .stretching:
            return L("%@ here. A few minutes of stretching today, your legs will thank you.", coach)
        }
    }
}
