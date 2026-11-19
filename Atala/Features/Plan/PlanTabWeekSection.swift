import SwiftUI

/// One week of the plan: a header line, and its sessions when expanded.
struct PlanTabWeekSection: View {
    @EnvironmentObject private var store: AppStore
    @ObservedObject private var purchases = PurchaseService.shared
    let week: PlanWeek
    let number: Int
    let isCurrent: Bool
    let isExpanded: Bool
    let isLast: Bool
    let onToggle: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button(action: {
                Haptics.tap()
                onToggle()
            }) {
                headerRow
            }
            .buttonStyle(.plain)
            .accessibilityHint(isExpanded ? "Hides the sessions" : "Shows the sessions")
            if isExpanded {
                VStack(spacing: 0) {
                    if week.workouts.isEmpty {
                        Text("No session this week.")
                            .font(.system(size: 14))
                            .foregroundStyle(Theme.muted)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .frame(height: 42)
                    }
                    ForEach(week.workouts) { workout in
                        NavigationLink {
                            WorkoutDetailView(workoutID: workout.id)
                        } label: {
                            PlanTabSessionRow(workout: workout, locked: isLocked(workout))
                        }
                        .buttonStyle(.plain)
                        .hapticOnTap()
                    }
                    totalsRow
                }
                .padding(.bottom, 6)
            }
        }
        .overlay(alignment: .bottom) {
            if !isLast {
                Rectangle().fill(Theme.hairline).frame(height: 1)
            }
        }
    }

    // MARK: Data

    private func isLocked(_ workout: Workout) -> Bool {
        !purchases.isPro && ProAccess.isLocked(workout, plan: store.plan)
    }

    /// The whole week is behind the lock (a week with nothing in it is not).
    private var weekLocked: Bool {
        !week.workouts.isEmpty && week.workouts.allSatisfy { isLocked($0) }
    }

    private var doneCount: Int { week.workouts.filter { $0.status == .done }.count }
    private var missedCount: Int { week.workouts.filter { $0.status == .missed }.count }

    /// True once the week has started (so "done of planned" makes sense).
    private var hasStarted: Bool {
        Calendar.plan.startOfDay(for: week.startDate) <= Date()
    }

    private var collapsedSummary: String {
        var text = Fmt.km(week.plannedKm)
        if hasStarted {
            text += " · \(doneCount) of \(week.workouts.count)"
        }
        return text
    }

    private var totalsText: String {
        let done = store.mainDoneKm(in: week)
        let doneText = abs(done - done.rounded()) < 0.05 ? "\(Int(done.rounded()))" : String(format: "%.1f", done)
        var text = "\(doneText) of \(Fmt.km(week.plannedKm)) run · \(doneCount) of \(week.workouts.count) sessions done"
        if missedCount > 0 { text += " · \(missedCount) missed" }
        return text
    }

    // MARK: Rows

    private var headerRow: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Text("Week \(number) · \(week.phase.title)")
                .font(.system(size: isExpanded ? 17 : 15, weight: isExpanded ? .bold : .regular))
                .foregroundStyle(titleColor)
            if isCurrent && !isExpanded {
                Text("Now")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Theme.orangeDeep)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 3)
                    .background(Theme.orangeTint, in: Capsule())
            }
            if weekLocked {
                Image(systemName: "lock.fill")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(Theme.yellowDeep)
                    .padding(5)
                    .background(Theme.yellowTint, in: Circle())
                    .accessibilityLabel("Needs Atala Pro")
            }
            Spacer(minLength: 8)
            Text(isExpanded ? Fmt.km(week.plannedKm) : collapsedSummary)
                .font(.system(size: 14, weight: isExpanded ? .semibold : .regular))
                .foregroundStyle(isExpanded ? Theme.ink : Theme.muted)
            Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(Theme.faint)
        }
        .frame(height: 46)
        .contentShape(Rectangle())
    }

    private var titleColor: Color {
        if isExpanded || isCurrent { return Theme.ink }
        // Past weeks are greyed, upcoming ones stay black.
        let end = Calendar.plan.date(byAdding: .day, value: 7, to: week.startDate) ?? week.startDate
        return end <= Date() ? Theme.muted : Theme.ink
    }

    private var totalsRow: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(week.phase.summary)
                .font(.system(size: 12))
                .foregroundStyle(Theme.muted)
                .lineLimit(1)
            Text(totalsText)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Theme.body)
                .lineLimit(1)
                .minimumScaleFactor(0.75)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 6)
        .padding(.bottom, 6)
    }
}

/// One session of a week: dot, day, title, amount and state.
struct PlanTabSessionRow: View {
    let workout: Workout
    /// Behind the Atala Pro lock: the row stays readable, with a padlock in place of the amount.
    var locked: Bool = false

    var body: some View {
        HStack(spacing: 10) {
            Circle()
                .fill(workout.mainDotColor)
                .frame(width: 8, height: 8)
            Text(Weekday.short(Calendar.plan.mondayIndex(of: workout.date)))
                .font(.system(size: 13, weight: isToday ? .semibold : .regular))
                .foregroundStyle(isToday ? Theme.ink : Theme.muted)
                .frame(width: 32, alignment: .leading)
            Image(systemName: workout.kind.symbol)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Theme.muted)
                .frame(width: 18)
                .accessibilityHidden(true)
            Text(rowTitle)
                .font(.system(size: 15, weight: isToday ? .semibold : .regular))
                .foregroundStyle(workout.status == .done ? Theme.muted : Theme.ink)
                .strikethrough(workout.status == .done, color: Theme.muted)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .frame(maxWidth: .infinity, alignment: .leading)
            if workout.status == .missed {
                MainStatusBadge(status: .missed)
            } else if workout.status == .done {
                Image(systemName: "checkmark")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(Theme.green)
                    .accessibilityLabel("Done")
            }
            if locked {
                Image(systemName: "lock.fill")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(Theme.yellowDeep)
                    .accessibilityLabel("Needs Atala Pro")
            } else {
                Text(workout.mainAmountText)
                    .font(.system(size: 13, weight: isToday ? .semibold : .regular))
                    .foregroundStyle(isToday ? Theme.ink : Theme.muted)
            }
        }
        .frame(height: 42)
        .contentShape(Rectangle())
    }

    private var isToday: Bool {
        Calendar.plan.isDateInToday(workout.date)
    }

    /// "Intervals 6 × 800 m" for hard sessions, the plain title otherwise.
    private var rowTitle: String {
        switch workout.kind {
        case .intervals, .tempo, .racePace:
            if workout.title.lowercased().contains(workout.kind.title.lowercased()) { return workout.title }
            return "\(workout.kind.title) · \(workout.title)"
        default:
            return workout.title
        }
    }
}
