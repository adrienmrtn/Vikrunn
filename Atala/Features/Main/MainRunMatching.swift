import SwiftUI

// A free run, a run entered by hand or a run imported from Apple Health can stand for the session
// planned on the same day: "Count it as today's session".

extension AppStore {
    /// The run session planned on the day of this run and not done yet, when the run belongs to no session.
    func mainSessionToCount(for run: RunRecord) -> Workout? {
        if let id = run.workoutID, workout(id: id) != nil { return nil }
        return workouts(on: run.date).first { $0.kind.isRun && $0.status != .done }
    }

    /// Attaches the run to the session and marks the session as done.
    func mainCount(run runID: UUID, as workoutID: UUID) {
        guard var record = run(id: runID), let session = workout(id: workoutID) else { return }
        record.workoutID = session.id
        if record.title.isEmpty || record.title == "Run" || record.title == "Free run" {
            record.title = session.title
        }
        addRun(record)
    }

    /// A run of today that belongs to no session, with the session it could count for.
    var mainUncountedRunToday: (run: RunRecord, session: Workout)? {
        let calendar = Calendar.plan
        for record in runs where calendar.isDateInToday(record.date) {
            if let session = mainSessionToCount(for: record) {
                return (record, session)
            }
        }
        return nil
    }
}

/// "You ran 5.2 km today. Count it as today's session?"
struct MainCountRunCard: View {
    @EnvironmentObject private var store: AppStore
    let run: RunRecord
    let session: Workout

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 12) {
                IconTile(systemName: "figure.run", size: 40)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Theme.ink)
                        .fixedSize(horizontal: false, vertical: true)
                    Text(L("%@ is still open on that day.", session.title))
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            Button(buttonTitle) {
                Haptics.success()
                withAnimation(.easeInOut(duration: 0.25)) {
                    store.mainCount(run: run.id, as: session.id)
                }
            }
            .buttonStyle(SecondaryButtonStyle(haptic: nil))
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 18)
    }

    private var isToday: Bool { Calendar.plan.isDateInToday(run.date) }

    private var title: String {
        let distance = Fmt.km(run.distanceKm)
        let duration = Fmt.duration(run.durationSec)
        return isToday
            ? L("You ran %@ in %@ today", distance, duration)
            : L("You ran %@ in %@ on %@", distance, duration, Fmt.day(run.date, "EEEE"))
    }

    private var buttonTitle: String {
        isToday ? L("Count it as today's session") : L("Count it as %@'s session", Fmt.day(run.date, "EEEE"))
    }
}
