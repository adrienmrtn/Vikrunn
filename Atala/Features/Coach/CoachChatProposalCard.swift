import SwiftUI
import UIKit

/// One change of a proposal, ready to display.
private struct CoachChatChangeLine: Identifiable {
    let id: UUID
    var beforeDay: String
    var beforeTitle: String
    var afterDay: String
    var afterTitle: String
    var removed: Bool
    /// Once applied: what changed, in a few words ("Moved from Tue 17 Nov").
    var note: String
}

/// "Plan change" card under a coach message: each change before → after, then Apply / Keep my plan.
/// Once answered, the card stays in the conversation with a clear "Applied" or "Plan kept" state.
@MainActor
struct CoachChatProposalCard: View {
    @EnvironmentObject private var store: AppStore
    let messageID: UUID
    let proposal: PlanProposal

    var body: some View {
        let lines = self.lines
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Text("Plan change")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Theme.muted)
                Spacer(minLength: 8)
                statusPill
            }

            ForEach(lines) { line in
                if proposal.status == .applied {
                    appliedRow(line)
                } else {
                    comparisonRow(line)
                        .opacity(proposal.status == .declined ? 0.55 : 1)
                }
            }

            if proposal.status == .pending {
                if lines.isEmpty {
                    Text("This suggestion has expired: your plan has changed since.")
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.muted)
                } else {
                    if !proposal.summary.isEmpty {
                        Text(proposal.summary)
                            .font(.system(size: 12))
                            .foregroundStyle(Theme.muted)
                            .lineSpacing(2)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    buttons
                }
            } else if proposal.status == .applied {
                stateLabel("Applied. Your plan is up to date in Plan and Today.",
                           symbol: "checkmark.circle.fill", color: Theme.green)
            } else {
                stateLabel("Plan kept. Nothing was changed.", symbol: "minus.circle", color: Theme.muted)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .stroke(proposal.status == .applied ? Theme.green.opacity(0.35) : Theme.hairline, lineWidth: 1)
        )
        .shadow(color: Color(hex: 0x143228).opacity(0.06), radius: 10, x: 0, y: 6)
    }

    // MARK: Status

    @ViewBuilder
    private var statusPill: some View {
        switch proposal.status {
        case .pending:
            EmptyView()
        case .applied:
            pill("Applied", symbol: "checkmark", tint: Theme.green, background: Theme.greenTint)
        case .declined:
            pill("Plan kept", symbol: "minus", tint: Theme.muted, background: Theme.surface)
        }
    }

    private func pill(_ title: String, symbol: String, tint: Color, background: Color) -> some View {
        HStack(spacing: 4) {
            Image(systemName: symbol)
                .font(.system(size: 9, weight: .bold))
            Text(title)
                .font(.system(size: 12, weight: .semibold))
        }
        .foregroundStyle(tint)
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(background, in: Capsule())
        .accessibilityElement(children: .combine)
    }

    private func stateLabel(_ text: String, symbol: String, color: Color) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 6) {
            Image(systemName: symbol)
                .font(.system(size: 14, weight: .semibold))
            Text(text)
                .font(.system(size: 13, weight: .semibold))
                .fixedSize(horizontal: false, vertical: true)
        }
        .foregroundStyle(color)
        .frame(minHeight: 28)
    }

    // MARK: Rows

    private func comparisonRow(_ line: CoachChatChangeLine) -> some View {
        HStack(spacing: 10) {
            box(day: line.beforeDay, title: line.beforeTitle, dot: Theme.greenSoft, background: Theme.bubble)
            Image(systemName: "arrow.right")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Theme.ink)
                .accessibilityLabel("becomes")
            box(day: line.afterDay, title: line.afterTitle, dot: Theme.orange, background: Theme.orangeTint)
        }
    }

    private func box(day: String, title: String, dot: Color, background: Color) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: 5) {
                Circle()
                    .fill(dot)
                    .frame(width: 7, height: 7)
                Text(day)
                    .font(.system(size: 11))
                    .foregroundStyle(Color(hex: 0x5A5A5A))
                    .lineLimit(1)
            }
            Text(title)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Theme.ink)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(background, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    /// The session as it is now in the plan, with what changed underneath.
    private func appliedRow(_ line: CoachChatChangeLine) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            if !line.afterDay.isEmpty {
                Text(line.afterDay)
                    .font(.system(size: 11))
                    .foregroundStyle(Color(hex: 0x5A5A5A))
                    .lineLimit(1)
            }
            Text(line.afterTitle)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Theme.ink)
                .strikethrough(line.removed)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
            Text(line.note)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Theme.green)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.greenTint, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .accessibilityElement(children: .combine)
    }

    // MARK: Actions

    private var buttons: some View {
        HStack(spacing: 8) {
            Button(action: { apply() }) {
                Text("Apply")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Color.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 44)
                    .background(Theme.ink, in: Capsule())
                    .contentShape(Capsule())
            }
            .buttonStyle(.plain)
            Button(action: { keep() }) {
                Text("Keep my plan")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                    .frame(maxWidth: .infinity)
                    .frame(height: 44)
                    .background(Color.white, in: Capsule())
                    .overlay(Capsule().stroke(Theme.ink, lineWidth: 1))
                    .contentShape(Capsule())
            }
            .buttonStyle(.plain)
        }
    }

    private func apply() {
        withAnimation(.easeInOut(duration: 0.25)) {
            store.coachApply(proposalIn: messageID)
        }
        Haptics.success()
        let id = messageID
        Task {
            await SyncService.pushAll(store: store)
            await CoachService.reportProposal(messageID: id, applied: true, store: store)
        }
    }

    private func keep() {
        Haptics.tap()
        withAnimation(.easeInOut(duration: 0.25)) {
            store.decline(proposalIn: messageID)
        }
        let id = messageID
        Task {
            await CoachService.reportProposal(messageID: id, applied: false, store: store)
        }
    }

    // MARK: Lines

    private var lines: [CoachChatChangeLine] {
        var result: [CoachChatChangeLine] = []
        for change in proposal.changes {
            if let line = proposal.status == .applied ? appliedLine(change) : proposedLine(change) {
                result.append(line)
            }
        }
        return result
    }

    /// Before → after, while the change is only proposed (or was declined).
    private func proposedLine(_ change: PlanChange) -> CoachChatChangeLine? {
        // A session that no longer exists (plan rebuilt since) has nothing to show.
        guard let workout = store.workout(id: change.workoutID) else { return nil }
        let before = CoachChatPlanText.detail(of: workout)
        let beforeDay = CoachChatPlanText.day(workout.date)
        if change.remove {
            return CoachChatChangeLine(id: change.id, beforeDay: beforeDay, beforeTitle: before,
                                       afterDay: beforeDay, afterTitle: "Removed", removed: false, note: "")
        }
        let after = CoachChatPlanText.detail(title: change.newTitle ?? workout.title,
                                             kind: change.newKind ?? workout.kind,
                                             km: change.newDistanceKm ?? workout.distanceKm,
                                             minutes: change.newDurationMin ?? workout.durationMin)
        return CoachChatChangeLine(id: change.id, beforeDay: beforeDay, beforeTitle: before,
                                   afterDay: CoachChatPlanText.day(change.newDate ?? workout.date),
                                   afterTitle: after, removed: false, note: "")
    }

    /// Once applied, the session already holds the new values: show them, and what they replaced.
    private func appliedLine(_ change: PlanChange) -> CoachChatChangeLine? {
        var previousDay = ""
        if let date = change.previousDate {
            previousDay = CoachChatPlanText.day(date)
        }
        if change.remove {
            return CoachChatChangeLine(id: change.id, beforeDay: previousDay, beforeTitle: "",
                                       afterDay: previousDay, afterTitle: change.previousDetail ?? "Session",
                                       removed: true, note: "Removed from your plan")
        }
        guard let workout = store.workout(id: change.workoutID) else { return nil }
        let now = CoachChatPlanText.detail(of: workout)
        var notes: [String] = []
        if let date = change.previousDate, !Calendar.plan.isDate(date, inSameDayAs: workout.date) {
            notes.append("Moved from \(previousDay)")
        }
        if let detail = change.previousDetail, detail != now {
            notes.append("Was \(detail)")
        }
        return CoachChatChangeLine(id: change.id, beforeDay: previousDay, beforeTitle: change.previousDetail ?? "",
                                   afterDay: CoachChatPlanText.day(workout.date), afterTitle: now,
                                   removed: false, note: notes.isEmpty ? "Updated" : notes.joined(separator: " · "))
    }
}

#Preview {
    let store = AppStore.preview
    var change = PlanChange(workoutID: store.nextRun?.id ?? UUID())
    change.newDate = Calendar.plan.date(byAdding: .day, value: 2, to: Date())
    let proposal = PlanProposal(summary: "The rest of your week stays as it is.", changes: [change])
    return CoachChatProposalCard(messageID: UUID(), proposal: proposal)
        .padding()
        .environmentObject(store)
}
