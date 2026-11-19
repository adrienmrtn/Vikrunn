import SwiftUI

/// What happens when a session is dropped on a given day.
enum PlanMoveSlot: Equatable {
    /// The session is already there.
    case current
    /// Nothing of the same kind that day: the session simply moves.
    case free
    /// Another planned session of the same kind is there: the two trade places.
    case swap(Workout)
    /// The day cannot take the session; the text says why.
    case blocked(String)
}

extension AppStore {
    /// How far ahead a session can be moved by hand.
    static let planMoveHorizonDays = 14

    /// A session can be moved by hand while it is not done and is not the race itself.
    func planCanMove(_ workout: Workout) -> Bool {
        workout.status != .done && workout.kind != .race && !planMoveDays(for: workout).isEmpty
    }

    /// Days a session can be moved to: from today, two weeks ahead at most, never past the end of
    /// the plan and never on or after race day.
    func planMoveDays(for workout: Workout) -> [Date] {
        guard let plan, let lastWeek = plan.weeks.last else { return [] }
        let calendar = Calendar.plan
        let today = calendar.startOfDay(for: Date())
        var last = calendar.date(byAdding: .day, value: 6, to: calendar.startOfDay(for: lastWeek.startDate)) ?? today
        if let race = plan.raceDate,
           let dayBefore = calendar.date(byAdding: .day, value: -1, to: calendar.startOfDay(for: race)) {
            last = min(last, dayBefore)
        }
        if let horizon = calendar.date(byAdding: .day, value: Self.planMoveHorizonDays - 1, to: today) {
            last = min(last, horizon)
        }
        guard last >= today else { return [] }
        var days: [Date] = []
        var day = today
        while day <= last, days.count < Self.planMoveHorizonDays {
            days.append(day)
            guard let next = calendar.date(byAdding: .day, value: 1, to: day) else { break }
            day = next
        }
        return days
    }

    /// The other sessions of that day, the moved one left out.
    func planOthers(on day: Date, besides workout: Workout) -> [Workout] {
        workouts(on: day).filter { $0.id != workout.id }
    }

    func planMoveSlot(for workout: Workout, on day: Date) -> PlanMoveSlot {
        let calendar = Calendar.plan
        if calendar.isDate(workout.date, inSameDayAs: day) { return .current }
        // Runs compete with runs, strength and stretching with themselves.
        let rivals = planOthers(on: day, besides: workout).filter { other in
            workout.kind.isRun ? other.kind.isRun : other.kind == workout.kind
        }
        guard let rival = rivals.first else { return .free }
        if rivals.count > 1 { return .blocked(L("Day already full")) }
        if rival.kind == .race { return .blocked(L("Race day")) }
        if rival.status == .done { return .blocked(L("Already done")) }
        // Trading places would send the other session into the past.
        let today = calendar.startOfDay(for: Date())
        if calendar.startOfDay(for: workout.date) < today { return .blocked(L("Another session that day")) }
        return .swap(rival)
    }

    /// True when a hard run (intervals, tempo, race pace, long run) would sit right next to another one.
    func planMoveIsTight(for workout: Workout, on day: Date) -> Bool {
        func isHard(_ kind: WorkoutKind) -> Bool { kind.isQuality || kind == .long }
        guard isHard(workout.kind) else { return false }
        let calendar = Calendar.plan
        // On a swap the other session lands on this one's day: two hard days side by side count too.
        if case .swap(let rival) = planMoveSlot(for: workout, on: day), isHard(rival.kind) {
            let gap = calendar.dateComponents([.day], from: calendar.startOfDay(for: workout.date),
                                              to: calendar.startOfDay(for: day)).day ?? 0
            if abs(gap) == 1 { return true }
        }
        for offset in [-1, 1] {
            guard let neighbour = calendar.date(byAdding: .day, value: offset, to: day) else { continue }
            let hardNextDoor = planOthers(on: neighbour, besides: workout).contains { other in
                isHard(other.kind) && other.status != .missed
            }
            if hardNextDoor { return true }
        }
        return false
    }

    /// Moves a session to another day, trading places with the session already there when needed.
    /// Returns false, changing nothing, when the day cannot take it.
    @discardableResult
    func planMove(_ id: UUID, to day: Date) -> Bool {
        guard let workout = workout(id: id), planCanMove(workout) else { return false }
        let calendar = Calendar.plan
        let target = calendar.startOfDay(for: day)
        guard planMoveDays(for: workout).contains(where: { calendar.isDate($0, inSameDayAs: target) }) else {
            return false
        }
        let origin = calendar.startOfDay(for: workout.date)
        switch planMoveSlot(for: workout, on: target) {
        case .current, .blocked:
            return false
        case .free:
            break
        case .swap(let other):
            updateWorkout(other.id) { session in
                session.date = origin
            }
        }
        updateWorkout(id) { session in
            session.date = target
            if session.status == .missed { session.status = .planned }
        }
        // File both sessions under the week of their new date.
        coachRehomeWorkouts()
        return true
    }
}

/// Sheet listing the coming days: tap one to move the session there.
struct PlanMoveSheet: View {
    let workoutID: UUID

    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Group {
                if let workout = store.workout(id: workoutID) {
                    list(for: workout)
                } else {
                    Color.white
                }
            }
            .background(Color.white)
            .navigationTitle(L("Move this session"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(L("Cancel")) { dismiss() }
                        .foregroundStyle(Theme.ink)
                }
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }

    private func list(for workout: Workout) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                Text("\(workout.title) · \(CoachChatPlanText.day(workout.date))")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                Text(L("Pick a day. If a session is already there, the two trade places."))
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.bottom, 6)
                ForEach(store.planMoveDays(for: workout), id: \.self) { day in
                    row(day, workout: workout)
                }
            }
            .padding(.horizontal, Theme.screenPadding)
            .padding(.top, 12)
            .padding(.bottom, 24)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func row(_ day: Date, workout: Workout) -> some View {
        let slot = store.planMoveSlot(for: workout, on: day)
        let others = store.planOthers(on: day, besides: workout)
        let tight = store.planMoveIsTight(for: workout, on: day)
        let enabled: Bool
        switch slot {
        case .free, .swap: enabled = true
        case .current, .blocked: enabled = false
        }
        return Button {
            if store.planMove(workout.id, to: day) {
                Haptics.success()
                dismiss()
            } else {
                Haptics.warning()
            }
        } label: {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(CoachChatPlanText.day(day))
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(enabled ? Theme.ink : Theme.faint)
                    Text(others.isEmpty ? L("Rest day") : others.map { $0.title }.joined(separator: ", "))
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.muted)
                        .lineLimit(1)
                    if tight, enabled {
                        Text(L("Hard session the day before or after"))
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(Theme.orangeDeep)
                    }
                }
                Spacer(minLength: 8)
                badge(for: slot)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, minHeight: 62, alignment: .leading)
            .background(Color.white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(slot == .current ? Theme.ink : Theme.controlBorder, lineWidth: slot == .current ? 1.5 : 1)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }

    private func badge(for slot: PlanMoveSlot) -> some View {
        let title: String
        let tint: Color
        let background: Color
        switch slot {
        case .current:
            title = L("Now"); tint = Color.white; background = Theme.ink
        case .free:
            title = L("Move here"); tint = Theme.green; background = Theme.greenTint
        case .swap:
            title = L("Swap"); tint = Theme.orangeDeep; background = Theme.orangeTint
        case .blocked(let reason):
            title = reason; tint = Theme.muted; background = Theme.surface
        }
        return Text(title)
            .font(.system(size: 12, weight: .semibold))
            .foregroundStyle(tint)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(background, in: Capsule())
            .lineLimit(1)
    }
}

#Preview {
    let store = AppStore.preview
    return PlanMoveSheet(workoutID: store.nextRun?.id ?? UUID())
        .environmentObject(store)
}
