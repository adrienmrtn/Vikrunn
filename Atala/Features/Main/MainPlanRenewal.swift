import SwiftUI

extension Notification.Name {
    /// Posted to bring the Coach tab to the front ("Talk to my coach" in a run summary).
    static let mainOpenCoachTab = Notification.Name("atala.main.openCoachTab")
}

// MARK: - Where the runner stands in the plan

extension AppStore {
    /// The plan week that contains today. Unlike `currentWeek`, it never falls back to week 1.
    var mainThisWeek: PlanWeek? {
        guard let plan else { return nil }
        let monday = Calendar.plan.mondayOfWeek(containing: Date())
        return plan.weeks.first { Calendar.plan.isDate($0.startDate, inSameDayAs: monday) }
    }

    /// This week, or the first week before the plan starts, or the last one once it is over.
    var mainPositionWeek: PlanWeek? {
        guard let plan else { return nil }
        if let week = mainThisWeek { return week }
        guard let first = plan.weeks.first, let last = plan.weeks.last else { return nil }
        return Date() < first.startDate ? first : last
    }

    /// True when nothing is left to do: every session from today on is done (or there is none).
    var mainPlanEnded: Bool {
        guard let plan else { return false }
        let today = Calendar.plan.startOfDay(for: Date())
        return !plan.allWorkouts.contains { $0.status == .planned && $0.date >= today }
    }
}

// MARK: - Rebuilding the plan

extension AppStore {
    /// Rebuilds the plan from the current answers, from this week on, and keeps what was already
    /// done coherent:
    /// - earlier days of the week are not recreated, so nothing new is marked as missed;
    /// - sessions done since Monday stay in the week (or tick the new session of the same day);
    /// - runs that pointed to a session that no longer exists become plain runs of the history.
    func mainRebuildPlan() {
        let calendar = Calendar.plan
        let today = calendar.startOfDay(for: Date())
        let previous: [Workout] = plan?.allWorkouts ?? []

        // A race date that is behind us cannot drive a new plan.
        if answers.hasRace, let raceDate = answers.raceDate, calendar.startOfDay(for: raceDate) < today {
            var updated = answers
            updated.hasRace = false
            updated.raceDate = nil
            answers = updated
        }

        generatePlan()
        guard var rebuilt = plan else { return }

        let firstDay = calendar.startOfDay(for: rebuilt.startDate)
        let done: [Workout] = previous.filter { $0.status == .done && $0.date >= firstDay }
        /// Run id → id of the new session it now belongs to.
        var runLinks: [UUID: UUID] = [:]
        for old in done {
            let weekPosition: Int? = rebuilt.weeks.firstIndex { week in
                let start = calendar.startOfDay(for: week.startDate)
                let end = calendar.date(byAdding: .day, value: 7, to: start) ?? start
                return old.date >= start && old.date < end
            }
            guard let w = weekPosition else { continue }
            let match: Int? = rebuilt.weeks[w].workouts.firstIndex { candidate in
                guard candidate.status == .planned, calendar.isDate(candidate.date, inSameDayAs: old.date) else {
                    return false
                }
                if old.kind.isRun { return candidate.kind.isRun }
                return candidate.kind == old.kind
            }
            if let i = match {
                rebuilt.weeks[w].workouts[i].status = .done
                rebuilt.weeks[w].workouts[i].runID = old.runID
                if let runID = old.runID { runLinks[runID] = rebuilt.weeks[w].workouts[i].id }
            } else {
                var kept = old
                kept.weekIndex = rebuilt.weeks[w].index
                rebuilt.weeks[w].workouts.append(kept)
                rebuilt.weeks[w].workouts.sort { $0.date < $1.date }
            }
        }
        plan = rebuilt

        let validIDs: Set<UUID> = Set(rebuilt.allWorkouts.map { $0.id })
        var updatedRuns: [RunRecord] = runs
        var changed = false
        for i in updatedRuns.indices {
            if let newID = runLinks[updatedRuns[i].id] {
                if updatedRuns[i].workoutID != newID {
                    updatedRuns[i].workoutID = newID
                    changed = true
                }
            } else if let workoutID = updatedRuns[i].workoutID, !validIDs.contains(workoutID) {
                updatedRuns[i].workoutID = nil
                changed = true
            }
        }
        if changed { runs = updatedRuns }
        refreshMissed()
    }

    /// Starts the plan that follows a finished one: same goal and days, with the level updated from
    /// what was actually run (race result, recent weekly volume).
    func mainBuildNextPlan() {
        let calendar = Calendar.plan
        let today = calendar.startOfDay(for: Date())
        var updated = answers

        if let plan {
            // The race that was just run is the best measure of today's level.
            if let km = plan.goal.distanceKm,
               let race = plan.allWorkouts.last(where: { $0.kind == .race && $0.status == .done }),
               let record = run(id: race.runID),
               record.durationSec > 0, record.distanceKm >= km * 0.97, record.distanceKm <= km * 1.1 {
                let seconds = Int((Double(record.durationSec) / record.distanceKm * km).rounded())
                updated.recentResult = RecentResult(distanceKm: km, seconds: seconds)
            }
            // Weekly volume really run over the last weeks of the plan. Never lowered here.
            let lastWeeks: [PlanWeek] = Array(plan.weeks.suffix(3))
            if !lastWeeks.isEmpty {
                let total: Double = lastWeeks.reduce(0) { $0 + mainDoneKm(in: $1) }
                let average = total / Double(lastWeeks.count)
                if average >= 25 {
                    updated.weeklyVolume = .over25
                } else if average >= 10, [.notYet, .under10].contains(updated.weeklyVolume ?? .under10) {
                    updated.weeklyVolume = .from10to25
                }
            }
            // Someone who completed a plan is no longer "just starting".
            let runsDone = plan.allWorkouts.filter { $0.kind.isRun && $0.status == .done }.count
            if runsDone >= 8, (updated.experience ?? .starting) == .starting {
                updated.experience = .underSixMonths
            }
        }
        if updated.hasRace, let raceDate = updated.raceDate, calendar.startOfDay(for: raceDate) <= today {
            updated.hasRace = false
            updated.raceDate = nil
        }
        answers = updated
        mainRebuildPlan()
    }
}

// MARK: - "Build my next plan"

/// Primary button shown when the plan is over. Asks before replacing it.
struct MainNextPlanButton: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var chrome: MainChrome
    @State private var confirm = false

    var body: some View {
        Button("Build my next plan") { confirm = true }
            .buttonStyle(PrimaryButtonStyle())
            .confirmationDialog("Build your next plan?", isPresented: $confirm, titleVisibility: .visible) {
                Button("Build it with the same goal") {
                    Haptics.success()
                    store.mainBuildNextPlan()
                }
                Button("Change my goal first") { chrome.showProfile = true }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("A new plan of up to \(PlanEngine.maxWeeks) weeks starts this week, from your level today. Your runs stay in your history.")
            }
    }
}

/// Glass card for a finished plan, used on Today and Plan.
struct MainPlanEndedCard: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.system(size: 24, weight: .bold))
                .tracking(-0.5)
                .foregroundStyle(Theme.ink)
                .fixedSize(horizontal: false, vertical: true)
            Text(message)
                .font(.system(size: 14))
                .lineSpacing(4)
                .foregroundStyle(Theme.body)
                .fixedSize(horizontal: false, vertical: true)
            MainNextPlanButton()
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .mainGlass(radius: 22)
    }

    private var raceWasRun: Bool {
        store.allWorkouts.contains { $0.kind == .race && $0.status == .done }
    }

    private var title: String {
        raceWasRun ? "Race done. Well run." : "Plan complete"
    }

    private var message: String {
        let done = store.allWorkouts.filter { $0.status == .done }.count
        let total = store.allWorkouts.count
        var text = "You did \(done) of the \(total) sessions of this plan."
        if raceWasRun {
            text += " Take a few easy days, then start again whenever you are ready."
        } else {
            text += " Your next plan starts from your level today."
        }
        return text
    }
}
