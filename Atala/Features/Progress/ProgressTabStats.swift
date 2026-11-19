import SwiftUI

struct ProgressTabBest: Identifiable {
    let id: String
    let title: String
    let value: String
    let detail: String
}

/// The period the summary at the top of the tab covers.
enum ProgressTabPeriod: String, CaseIterable, Identifiable {
    case week, month
    var id: String { rawValue }

    var title: String {
        switch self {
        case .week: return L("This week")
        case .month: return L("This month")
        }
    }
}

/// Distance, runs and time of a period, with the distance of the period before it.
struct ProgressTabSummary {
    var km: Double
    var runs: Int
    var seconds: Int
    var previousKm: Double

    /// Average pace of the period, in seconds per kilometre (0 when nothing was run).
    var pace: Int {
        km > 0 ? Int((Double(seconds) / km).rounded()) : 0
    }

    /// Change of the distance against the period before, in percent. Nil when there is nothing to compare with.
    var changePercent: Int? {
        guard previousKm > 0 else { return nil }
        return Int(((km - previousKm) / previousKm * 100).rounded())
    }
}

/// One week of the distance chart.
struct ProgressTabWeek: Identifiable {
    let monday: Date
    let km: Double
    let plannedKm: Double
    let isCurrent: Bool
    var id: Date { monday }
}

/// What the goal card shows: the level today, the goal and the paces behind them.
struct ProgressTabGoal {
    let title: String
    let currentSeconds: Int?
    let goalSeconds: Int?
    let footnote: String
    let vmaKmh: Double
    let easyPace: Int
}

/// Figures of the Progress tab, derived from the saved runs and the plan.
enum ProgressTabStats {
    // MARK: Summary

    static func summary(_ runs: [RunRecord], period: ProgressTabPeriod, now: Date = Date()) -> ProgressTabSummary {
        let calendar = Calendar.plan
        let start: Date
        let previousStart: Date
        switch period {
        case .week:
            start = calendar.mondayOfWeek(containing: now)
            previousStart = calendar.date(byAdding: .day, value: -7, to: start) ?? start
        case .month:
            let today: Date = calendar.startOfDay(for: now)
            start = calendar.dateInterval(of: .month, for: now)?.start ?? today
            previousStart = calendar.date(byAdding: .month, value: -1, to: start) ?? start
        }
        let current: [RunRecord] = runs.filter { $0.date >= start && $0.date <= now }
        let previous: [RunRecord] = runs.filter { $0.date >= previousStart && $0.date < start }
        let km: Double = current.reduce(0) { $0 + $1.distanceKm }
        let seconds: Int = current.reduce(0) { $0 + $1.durationSec }
        let previousKm: Double = previous.reduce(0) { $0 + $1.distanceKm }
        return ProgressTabSummary(km: km, runs: current.count, seconds: seconds, previousKm: previousKm)
    }

    // MARK: Weeks

    /// The last weeks, oldest first, ending with the current one.
    static func weeks(_ runs: [RunRecord], plan: TrainingPlan?, count: Int = 12, now: Date = Date()) -> [ProgressTabWeek] {
        let calendar = Calendar.plan
        let thisMonday: Date = calendar.mondayOfWeek(containing: now)
        var totals: [Date: Double] = [:]
        for run in runs where run.date <= now {
            let monday: Date = calendar.mondayOfWeek(containing: run.date)
            totals[monday, default: 0] += run.distanceKm
        }
        var planned: [Date: Double] = [:]
        if let plan {
            for week in plan.weeks {
                let monday: Date = calendar.mondayOfWeek(containing: week.startDate)
                planned[monday] = week.plannedKm
            }
        }
        var result: [ProgressTabWeek] = []
        for back in stride(from: count - 1, through: 0, by: -1) {
            let shifted: Date = calendar.date(byAdding: .day, value: -7 * back, to: thisMonday) ?? thisMonday
            let monday: Date = calendar.mondayOfWeek(containing: shifted)
            result.append(ProgressTabWeek(monday: monday,
                                          km: totals[monday] ?? 0,
                                          plannedKm: planned[monday] ?? 0,
                                          isCurrent: back == 0))
        }
        return result
    }

    /// "12" or "12.4": a distance without its unit.
    static func number(_ km: Double) -> String {
        abs(km - km.rounded()) < 0.05 ? "\(Int(km.rounded()))" : String(format: "%.1f", km)
    }

    // MARK: Goal

    static func goal(_ plan: TrainingPlan) -> ProgressTabGoal {
        ProgressTabGoal(title: plan.goal.title,
                        currentSeconds: plan.currentSeconds,
                        goalSeconds: plan.targetSeconds ?? plan.predictedSeconds,
                        footnote: footnote(target: plan.targetSeconds, predicted: plan.predictedSeconds),
                        vmaKmh: plan.vmaKmh,
                        easyPace: plan.paces.easy)
    }

    static func footnote(target: Int?, predicted: Int?) -> String {
        if let target {
            if let predicted, predicted != target {
                return L("Goal %@ · the plan aims for %@ on race day", Fmt.goalTime(target), Fmt.goalTime(predicted))
            }
            return L("Goal %@", Fmt.goalTime(target))
        }
        if let predicted {
            return L("The plan aims for %@ at the end", Fmt.goalTime(predicted))
        }
        return L("Your prediction sharpens as you log runs.")
    }

    // MARK: Personal bests

    static func bests(_ runs: [RunRecord]) -> [ProgressTabBest] {
        var result: [ProgressTabBest] = []
        let valid = runs.filter { $0.distanceM > 0 && $0.durationSec > 0 }

        if let longest = valid.max(by: { $0.distanceM < $1.distanceM }) {
            result.append(ProgressTabBest(id: "longest", title: L("Longest run"),
                                          value: Fmt.km(longest.distanceKm),
                                          detail: dateText(longest)))
        }
        if let fastest = valid.filter({ $0.distanceM >= 1000 }).min(by: { $0.avgPace < $1.avgPace }) {
            result.append(ProgressTabBest(id: "pace", title: L("Fastest average pace"),
                                          value: "\(Fmt.pace(fastest.avgPace))/km",
                                          detail: "\(Fmt.km(fastest.distanceKm)) · \(dateText(fastest))"))
        }
        let distances: [(String, String, Double)] = [
            ("5k", L("Best 5 km"), 5),
            ("10k", L("Best 10 km"), 10),
            ("half", L("Best half marathon"), 21.0975),
        ]
        for (id, title, km) in distances {
            // A run slightly short of the distance (GPS drift) still counts.
            let candidates = valid.filter { $0.distanceKm >= km * 0.99 }
            if let best = candidates.min(by: { $0.avgPace < $1.avgPace }) {
                let seconds = Int((Double(best.durationSec) / best.distanceKm * km).rounded())
                result.append(ProgressTabBest(id: id, title: title,
                                              value: Fmt.duration(seconds),
                                              detail: L("At the pace of your %@ run · %@", Fmt.km(best.distanceKm), dateText(best))))
            }
        }
        if let longestTime = valid.max(by: { $0.durationSec < $1.durationSec }) {
            result.append(ProgressTabBest(id: "time", title: L("Longest time on your feet"),
                                          value: Fmt.duration(longestTime.durationSec),
                                          detail: dateText(longestTime)))
        }
        return result
    }

    static func bestSymbol(_ id: String) -> String {
        switch id {
        case "longest": return "arrow.left.and.right"
        case "pace": return "bolt.fill"
        case "time": return "clock.fill"
        default: return "medal.fill"
        }
    }

    static func dateText(_ run: RunRecord) -> String {
        Fmt.day(run.date, "d MMM yyyy")
    }

    static func sourceSymbol(_ source: RunSource) -> String {
        switch source {
        case .gps: return "location.fill"
        case .manual: return "square.and.pencil"
        case .health: return "heart.fill"
        }
    }

    static func sourceTitle(_ source: RunSource) -> String {
        switch source {
        case .gps: return "GPS"
        case .manual: return L("Added by hand")
        case .health: return "Apple Health"
        }
    }
}

// MARK: - Sample figures

/// Believable figures shown behind the blur of the locked tab, while the runner has little or
/// nothing of their own to show yet. Never shown with Atala Pro.
enum ProgressTabSample {
    /// Twelve weeks of three runs, building up, ending this week. Built once per launch.
    static let runs: [RunRecord] = makeRuns(now: Date())

    static let goal = ProgressTabGoal(title: L("10K"),
                                      currentSeconds: 3015,
                                      goalSeconds: 2880,
                                      footnote: ProgressTabStats.footnote(target: 2880, predicted: 2850),
                                      vmaKmh: 14.6,
                                      easyPace: 352)

    static let sessionsDone: Int = 29
    static let sessionsSettled: Int = 32

    private static func makeRuns(now: Date) -> [RunRecord] {
        let calendar = Calendar.plan
        let thisMonday: Date = calendar.mondayOfWeek(containing: now)
        // Kilometres per week, oldest first.
        let weeklyKm: [Double] = [14, 16, 18, 15, 20, 22, 24, 19, 25, 27, 29, 31]
        // Day of the week (0 = Monday), share of the week, title, pace in seconds per kilometre.
        let pattern: [(Int, Double, String, Int)] = [
            (1, 0.28, "Easy run", 352),
            (3, 0.27, "Tempo run", 305),
            (6, 0.45, "Long run", 365),
        ]
        var result: [RunRecord] = []
        for (index, km) in weeklyKm.enumerated() {
            let back: Int = weeklyKm.count - 1 - index
            let monday: Date = calendar.date(byAdding: .day, value: -7 * back, to: thisMonday) ?? thisMonday
            var added: Int = 0
            for (slot, entry) in pattern.enumerated() {
                let day: Date = calendar.date(byAdding: .day, value: entry.0, to: monday) ?? monday
                let date: Date = day.addingTimeInterval(18 * 3600)
                if date > now { continue }
                let distanceM: Double = (km * entry.1 * 1000).rounded()
                // A little faster as the weeks go by.
                let pace: Int = entry.3 - index * 2
                let duration: Int = Int((distanceM / 1000 * Double(pace)).rounded())
                let source: RunSource = (index + slot) % 3 == 0 ? .health : .gps
                result.append(RunRecord(title: entry.2, date: date, source: source,
                                        distanceM: distanceM, durationSec: duration))
                added += 1
            }
            if added == 0 {
                // Early in the week: one run at its very start, so the week is never empty.
                result.append(RunRecord(title: "Easy run", date: monday, source: .gps,
                                        distanceM: 6200, durationSec: 2130))
            }
        }
        return result
    }
}
