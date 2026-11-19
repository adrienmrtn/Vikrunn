import Foundation

/// Weeks in a row with at least one session done. Runners rest on purpose, so the streak counts
/// weeks, not days: a rest day never breaks it.
struct Streak: Equatable {
    /// Weeks in a row up to now. The current week counts once something is done in it; until then
    /// the streak of the previous weeks stays alive.
    var current: Int
    var best: Int
    /// True when this week already has a session done.
    var activeThisWeek: Bool

    static let none = Streak(current: 0, best: 0, activeThisWeek: false)
}

enum StreakEngine {
    static func weekly(runs: [RunRecord], workouts: [Workout], now: Date = Date()) -> Streak {
        let calendar = Calendar.plan
        var activeWeeks = Set<Date>()
        for run in runs where run.date <= now {
            activeWeeks.insert(calendar.mondayOfWeek(containing: run.date))
        }
        for workout in workouts where workout.status == .done && workout.date <= now {
            activeWeeks.insert(calendar.mondayOfWeek(containing: workout.date))
        }
        guard !activeWeeks.isEmpty else { return .none }

        func weekBefore(_ monday: Date) -> Date? {
            calendar.date(byAdding: .day, value: -7, to: monday).map { calendar.mondayOfWeek(containing: $0) }
        }

        let thisWeek = calendar.mondayOfWeek(containing: now)
        let activeThisWeek = activeWeeks.contains(thisWeek)

        // Current streak: counted back from this week, or from last week while this one is still empty.
        var current = 0
        var cursor: Date? = activeThisWeek ? thisWeek : weekBefore(thisWeek)
        while let week = cursor, activeWeeks.contains(week) {
            current += 1
            cursor = weekBefore(week)
        }

        // Best streak: longest chain of consecutive weeks.
        var best = 0
        var chain = 0
        var previous: Date?
        for week in activeWeeks.sorted() {
            if let previous, weekBefore(week) == previous {
                chain += 1
            } else {
                chain = 1
            }
            best = max(best, chain)
            previous = week
        }
        return Streak(current: current, best: max(best, current), activeThisWeek: activeThisWeek)
    }

    static func weeks(_ count: Int) -> String {
        count == 1 ? L("1 week") : L("%@ weeks", count)
    }

    /// One line that says what to do next.
    static func nudge(for streak: Streak) -> String {
        if streak.current == 0 {
            return L("Finish one session this week to start your streak.")
        }
        if streak.activeThisWeek {
            return L("This week is in the bank. Show up next week to make it %@.", streak.current + 1)
        }
        return L("One session this week keeps your streak alive.")
    }
}

extension AppStore {
    /// Weekly streak from the saved runs and the sessions marked as done.
    var streak: Streak {
        StreakEngine.weekly(runs: runs, workouts: allWorkouts)
    }
}
