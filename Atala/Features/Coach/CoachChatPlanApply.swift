import Foundation

/// Short texts shared by the proposal card and the applied-change snapshot.
enum CoachChatPlanText {
    /// "Easy run · 8 km" for runs, "Strength · 20 min" otherwise.
    static func detail(title: String, kind: WorkoutKind, km: Double, minutes: Int) -> String {
        if kind.isRun, km > 0 { return "\(title) · \(Fmt.km(km))" }
        if minutes > 0 { return "\(title) · \(Fmt.minutes(minutes))" }
        return title
    }

    static func detail(of workout: Workout) -> String {
        detail(title: workout.title, kind: workout.kind, km: workout.distanceKm, minutes: workout.durationMin)
    }

    /// "Today", "Tomorrow" or "Thu 19 Nov".
    static func day(_ date: Date) -> String {
        let calendar = Calendar.plan
        if calendar.isDateInToday(date) { return L("Today") }
        if calendar.isDateInTomorrow(date) { return L("Tomorrow") }
        return Fmt.day(date, "EEE d MMM")
    }
}

extension AppStore {
    /// Applies the proposal of a coach message once the user tapped "Apply".
    /// Keeps a snapshot of each session as it was (so the chat can still show what changed),
    /// then makes the plan consistent: steps of a lightened session, and sessions filed under the right week.
    func coachApply(proposalIn messageID: UUID) {
        guard let m = messages.firstIndex(where: { $0.id == messageID }),
              var proposal = messages[m].proposal, proposal.status == .pending else { return }
        for i in proposal.changes.indices {
            guard let current = workout(id: proposal.changes[i].workoutID) else { continue }
            proposal.changes[i].previousDate = current.date
            proposal.changes[i].previousDetail = CoachChatPlanText.detail(of: current)
        }
        messages[m].proposal = proposal
        apply(proposalIn: messageID)
        for change in proposal.changes where !change.remove {
            coachSyncSteps(with: change)
        }
        coachRehomeWorkouts()
    }

    /// Makes the steps of a session match a change of kind or distance, so its detail screen and the
    /// guided run follow what was agreed with the coach.
    private func coachSyncSteps(with change: PlanChange) {
        if change.newKind == .easy, let paces = plan?.paces {
            updateWorkout(change.workoutID) { session in
                let meters = Int((session.distanceKm * 10).rounded()) * 100
                let step = WorkoutStep(kind: .steady, label: L("Easy pace"), distanceM: meters,
                                       paceFast: paces.easy - 15, paceSlow: paces.easy + 20)
                session.blocks = [WorkoutBlock(steps: [step])]
                session.subtitle = L("You should be able to talk")
                session.targetPace = paces.easy
                session.effort = 3
                session.why = L("A short easy run in place of the planned session, as agreed with your coach. It keeps you moving without adding strain.")
            }
        } else if change.newDistanceKm != nil {
            updateWorkout(change.workoutID) { session in
                // Only a single steady step can simply be shortened or lengthened.
                guard session.blocks.count == 1, session.blocks[0].repeats <= 1,
                      session.blocks[0].steps.count == 1,
                      session.blocks[0].steps[0].distanceM != nil else { return }
                session.blocks[0].steps[0].distanceM = Int((session.distanceKm * 10).rounded()) * 100
            }
        }
    }

    /// Files every session under the week of its date. A session moved to another week by the coach
    /// would otherwise stay listed in its old week on the Plan tab.
    func coachRehomeWorkouts() {
        guard var plan, !plan.weeks.isEmpty else { return }
        let calendar = Calendar.plan
        let lastIndex = plan.weeks.count - 1
        var misplaced: [Workout] = []

        for w in plan.weeks.indices {
            let start = calendar.startOfDay(for: plan.weeks[w].startDate)
            guard let end = calendar.date(byAdding: .day, value: 7, to: start) else { continue }
            // Sessions before the first week or after the last one stay where they are.
            let outside = plan.weeks[w].workouts.filter { session in
                (session.date < start && w > 0) || (session.date >= end && w < lastIndex)
            }
            guard !outside.isEmpty else { continue }
            let ids = outside.map { $0.id }
            plan.weeks[w].workouts.removeAll { ids.contains($0.id) }
            misplaced.append(contentsOf: outside)
        }
        guard !misplaced.isEmpty else { return }

        for session in misplaced {
            var target = lastIndex
            for w in plan.weeks.indices {
                let start = calendar.startOfDay(for: plan.weeks[w].startDate)
                guard let end = calendar.date(byAdding: .day, value: 7, to: start) else { continue }
                if session.date < end {
                    target = w
                    break
                }
            }
            var moved = session
            moved.weekIndex = plan.weeks[target].index
            plan.weeks[target].workouts.append(moved)
            plan.weeks[target].workouts.sort { $0.date < $1.date }
        }
        self.plan = plan
    }
}
