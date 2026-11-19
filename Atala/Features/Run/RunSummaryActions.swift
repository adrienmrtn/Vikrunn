import SwiftUI
import UIKit

/// The words shared with the picture of a run (see `RunShareCard` and `RunShareSheet`).
enum RunSummaryShare {
    static func title(for run: RunRecord) -> String {
        run.title.isEmpty ? L("Run") : L(run.title)
    }

    /// "Easy run · 6.10 km in 35:12 (5:46/km) · Tuesday 17 November · Atala"
    static func text(for run: RunRecord) -> String {
        var parts: [String] = [title(for: run)]
        if run.avgPace > 0 {
            parts.append(L("%@ in %@ (%@/km)", RunFormat.distance(run.distanceM), Fmt.duration(run.durationSec), Fmt.pace(run.avgPace)))
        } else {
            parts.append(L("%@ in %@", RunFormat.distance(run.distanceM), Fmt.duration(run.durationSec)))
        }
        parts.append(Fmt.day(run.date))
        parts.append("Atala")
        return parts.joined(separator: " · ")
    }
}

/// Actions under a run summary: count the run as the session of its day, talk to the coach, delete it.
/// Sharing sits next to "Done", at the bottom of the summary.
struct RunSummaryActions: View {
    let run: RunRecord
    /// Closes the summary, wherever it is shown (end of the run flow, or pushed from the history).
    let onClose: () -> Void

    @EnvironmentObject private var store: AppStore
    @State private var confirmDelete = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let session = store.mainSessionToCount(for: run) {
                MainCountRunCard(run: run, session: session)
            }
            Button(action: talkToCoach) {
                Label(L("Talk to my coach"), systemImage: "bubble.left")
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            .buttonStyle(SecondaryButtonStyle())
            Button(role: .destructive) {
                Haptics.warning()
                confirmDelete = true
            } label: {
                Text(L("Delete this run"))
                    .font(.system(size: 15, weight: .semibold))
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 44)
            }
            .confirmationDialog(L("Delete this run?"), isPresented: $confirmDelete, titleVisibility: .visible) {
                Button(L("Delete"), role: .destructive) { deleteRun() }
                Button(L("Cancel"), role: .cancel) {}
            } message: {
                Text(deleteMessage)
            }
        }
    }

    private var deleteMessage: String {
        var text = L("It is removed from your history and your totals.")
        if let id = run.workoutID, store.workout(id: id) != nil {
            text += " " + L("Its session goes back to not done.")
        }
        if run.source == .health {
            text += " " + L("It stays in Apple Health and will not be imported again.")
        }
        return text
    }

    private func deleteRun() {
        if run.source == .health {
            HealthKitService.shared.ignoreRun(startingAt: run.date)
        }
        store.deleteRun(run.id)
        onClose()
    }

    /// Closes the summary, then brings the Coach tab to the front.
    private func talkToCoach() {
        onClose()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.45) {
            NotificationCenter.default.post(name: .mainOpenCoachTab, object: nil)
        }
    }
}
