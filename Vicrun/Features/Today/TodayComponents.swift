import SwiftUI

/// Glass card with the session of the day (or the next one on a rest day).
struct TodaySessionCard: View {
    let workout: Workout
    let isToday: Bool
    let restDay: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            VStack(alignment: .leading, spacing: 4) {
                if restDay {
                    Text("Rest day")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Theme.green)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Theme.greenTint, in: Capsule())
                        .padding(.bottom, 2)
                }
                HStack(spacing: 6) {
                    Circle()
                        .fill(workout.mainDotColor)
                        .frame(width: 8, height: 8)
                    Text(caption)
                        .font(.system(size: 13))
                        .foregroundStyle(Color(hex: 0x5A5A5A))
                    Spacer(minLength: 4)
                    MainStatusBadge(status: workout.status)
                }
                Text(workout.title)
                    .font(.system(size: 26, weight: .bold))
                    .tracking(-0.5)
                    .foregroundStyle(Theme.ink)
                    .lineLimit(2)
                    .minimumScaleFactor(0.75)
            }
            MainWorkoutStats(workout: workout)
            NavigationLink {
                WorkoutDetailView(workoutID: workout.id)
            } label: {
                Text("View session")
            }
            .buttonStyle(PrimaryButtonStyle())
        }
        .padding(18)
        .mainGlass(radius: 22)
    }

    private var caption: String {
        if isToday { return workout.kind.title }
        return "Next · \(Fmt.day(workout.date, "EEEE d MMM")) · \(workout.kind.title)"
    }
}

/// Glass card used when there is nothing to run (no plan, or plan finished).
struct TodayMessageCard: View {
    let title: String
    let message: String
    let actionTitle: String
    let action: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.system(size: 24, weight: .bold))
                .tracking(-0.5)
                .foregroundStyle(Theme.ink)
            Text(message)
                .font(.system(size: 14))
                .lineSpacing(4)
                .foregroundStyle(Theme.body)
                .fixedSize(horizontal: false, vertical: true)
            Button(actionTitle, action: action)
                .buttonStyle(PrimaryButtonStyle())
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .mainGlass(radius: 22)
    }
}

/// Monday-to-Sunday strip with the state of each day.
struct TodayWeekStrip: View {
    @EnvironmentObject private var store: AppStore

    private enum DayState {
        case none, planned, done, missed
    }

    var body: some View {
        HStack(spacing: 0) {
            ForEach(0..<7, id: \.self) { offset in
                dayColumn(offset: offset)
            }
        }
    }

    private func date(offset: Int) -> Date {
        let monday = Calendar.plan.mondayOfWeek(containing: Date())
        return Calendar.plan.date(byAdding: .day, value: offset, to: monday) ?? monday
    }

    private func state(on day: Date) -> DayState {
        let workouts = store.workouts(on: day)
        if workouts.isEmpty { return .none }
        if workouts.contains(where: { $0.status == .missed }) { return .missed }
        if workouts.allSatisfy({ $0.status == .done }) { return .done }
        return .planned
    }

    private func dayColumn(offset: Int) -> some View {
        let day = date(offset: offset)
        let isToday = Calendar.plan.isDateInToday(day)
        let dayState = state(on: day)
        let number = Calendar.plan.component(.day, from: day)
        let letter = String(Weekday.short(offset + 1).prefix(1))
        return VStack(spacing: 4) {
            Text(letter)
                .font(.system(size: 12, weight: isToday ? .semibold : .regular))
                .foregroundStyle(isToday ? Theme.ink : Theme.muted)
            Text("\(number)")
                .font(.system(size: 15, weight: isToday ? .semibold : .medium))
                .foregroundStyle(isToday ? Color.white : Theme.ink)
                .frame(width: 36, height: 36)
                .background(isToday ? Theme.ink : Color.clear, in: Circle())
            marker(dayState)
                .frame(width: 14, height: 14)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(Weekday.long(offset + 1)) \(number), \(stateLabel(dayState))")
    }

    @ViewBuilder
    private func marker(_ dayState: DayState) -> some View {
        switch dayState {
        case .none:
            Color.clear
        case .planned:
            Circle()
                .stroke(Theme.faint, lineWidth: 1.5)
                .frame(width: 7, height: 7)
        case .done:
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 13))
                .foregroundStyle(Theme.green)
        case .missed:
            Image(systemName: "xmark.circle.fill")
                .font(.system(size: 13))
                .foregroundStyle(Theme.orange)
        }
    }

    private func stateLabel(_ dayState: DayState) -> String {
        switch dayState {
        case .none: return "no session"
        case .planned: return "session planned"
        case .done: return "done"
        case .missed: return "missed"
        }
    }
}

/// Strength and stretching sessions of the day.
struct TodayExtraSessions: View {
    let workouts: [Workout]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            MainSectionTitle(title: "Also today")
            ForEach(workouts) { workout in
                NavigationLink {
                    WorkoutDetailView(workoutID: workout.id)
                } label: {
                    HStack(spacing: 12) {
                        IconTile(systemName: workout.kind.symbol, tint: Theme.yellowDeep,
                                 background: Theme.yellowTint, size: 40)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(workout.title)
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundStyle(Theme.ink)
                            Text(workout.subtitle)
                                .font(.system(size: 13))
                                .foregroundStyle(Theme.muted)
                                .lineLimit(1)
                        }
                        Spacer(minLength: 4)
                        MainStatusBadge(status: workout.status)
                        Image(systemName: "chevron.right")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Theme.faint)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .card(radius: 16)
                }
                .buttonStyle(.plain)
            }
        }
    }
}

/// Weekly volume: kilometres and runs done against the plan.
struct TodayWeekVolume: View {
    @EnvironmentObject private var store: AppStore
    let week: PlanWeek

    var body: some View {
        let done = store.mainDoneKm(in: week)
        let planned = week.plannedKm
        let runs = week.workouts.filter { $0.kind.isRun }
        let runsDone = runs.filter { $0.status == .done }.count
        let missed = week.workouts.filter { $0.status == .missed }.count
        VStack(alignment: .leading, spacing: 8) {
            MainSectionTitle(title: "This week",
                             trailing: "\(Self.number(done)) of \(Fmt.km(planned))")
            MainProgressBar(fraction: planned > 0 ? done / planned : 0,
                            tint: Theme.green, label: "Weekly volume")
            Text(Self.summary(runsDone: runsDone, runs: runs.count, missed: missed))
                .font(.system(size: 13))
                .foregroundStyle(Theme.muted)
        }
    }

    private static func number(_ km: Double) -> String {
        abs(km - km.rounded()) < 0.05 ? "\(Int(km.rounded()))" : String(format: "%.1f", km)
    }

    private static func summary(runsDone: Int, runs: Int, missed: Int) -> String {
        var text = "\(runsDone) of \(runs) \(runs == 1 ? "run" : "runs") done"
        if missed > 0 { text += " · \(missed) missed" }
        return text
    }
}

/// Days left before the race, when the plan has one.
struct TodayRaceCountdown: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        if let plan = store.plan, let raceDate = plan.raceDate, let days = daysLeft(to: raceDate), days >= 0 {
            HStack(spacing: 12) {
                IconTile(systemName: "flag.checkered", tint: Theme.orangeDeep,
                         background: Theme.orangeTint, size: 40)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title(goal: plan.goal, days: days))
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Theme.ink)
                    Text(detail(plan: plan, raceDate: raceDate))
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.muted)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .padding(.top, 16)
            .accessibilityElement(children: .combine)
        }
    }

    private func daysLeft(to raceDate: Date) -> Int? {
        let today = Calendar.plan.startOfDay(for: Date())
        let race = Calendar.plan.startOfDay(for: raceDate)
        return Calendar.plan.dateComponents([.day], from: today, to: race).day
    }

    private func title(goal: GoalType, days: Int) -> String {
        switch days {
        case 0: return "\(goal.title): race day is today"
        case 1: return "\(goal.title) tomorrow"
        default: return "\(goal.title) in \(days) days"
        }
    }

    private func detail(plan: TrainingPlan, raceDate: Date) -> String {
        var text = Fmt.day(raceDate, "EEEE d MMMM")
        if let target = plan.targetSeconds {
            text += " · goal \(Fmt.goalTime(target))"
        }
        return text
    }
}

/// The next sessions, one line each.
struct TodayUpcomingList: View {
    let workouts: [Workout]

    var body: some View {
        VStack(spacing: 0) {
            ForEach(workouts) { workout in
                NavigationLink {
                    WorkoutDetailView(workoutID: workout.id)
                } label: {
                    HStack(spacing: 8) {
                        Circle()
                            .fill(workout.mainDotColor)
                            .frame(width: 8, height: 8)
                        Text("\(Fmt.day(workout.date, "EEE d")) · \(workout.title)")
                            .font(.system(size: 15))
                            .foregroundStyle(Theme.ink)
                            .lineLimit(1)
                        Spacer(minLength: 8)
                        Text(workout.mainAmountText)
                            .font(.system(size: 14))
                            .foregroundStyle(Theme.muted)
                    }
                    .frame(height: 46)
                    .contentShape(Rectangle())
                    .overlay(alignment: .top) {
                        Rectangle().fill(Theme.hairline).frame(height: 1)
                    }
                }
                .buttonStyle(.plain)
            }
        }
    }
}
