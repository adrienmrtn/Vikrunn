import SwiftUI

/// One change of a proposal, ready to display.
private struct CoachChatChangeLine: Identifiable {
    let id: UUID
    var beforeDay: String
    var beforeTitle: String
    var afterDay: String
    var afterTitle: String
    var removed: Bool
}

/// "Plan change" card under a coach message: each change before → after, then Apply / Keep my plan.
@MainActor
struct CoachChatProposalCard: View {
    @EnvironmentObject private var store: AppStore
    let messageID: UUID
    let proposal: PlanProposal

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Plan change")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Theme.muted)

            ForEach(lines) { line in
                if proposal.status == .applied {
                    appliedRow(line)
                } else {
                    comparisonRow(line)
                }
            }

            if !proposal.summary.isEmpty {
                Text(proposal.summary)
                    .font(.system(size: 12))
                    .foregroundStyle(Theme.muted)
                    .lineSpacing(2)
                    .fixedSize(horizontal: false, vertical: true)
            }

            footer
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .stroke(Theme.hairline, lineWidth: 1)
        )
        .shadow(color: Color(hex: 0x143228).opacity(0.06), radius: 10, x: 0, y: 6)
    }

    // MARK: Rows

    private func comparisonRow(_ line: CoachChatChangeLine) -> some View {
        HStack(spacing: 10) {
            box(day: line.beforeDay, title: line.beforeTitle, dot: Theme.greenSoft, background: Theme.bubble,
                struck: false)
            Image(systemName: "arrow.right")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Theme.ink)
                .accessibilityLabel("becomes")
            box(day: line.afterDay, title: line.afterTitle, dot: Theme.orange, background: Theme.orangeTint,
                struck: false)
        }
    }

    private func appliedRow(_ line: CoachChatChangeLine) -> some View {
        box(day: line.afterDay, title: line.afterTitle, dot: Theme.greenSoft, background: Theme.bubble,
            struck: line.removed)
    }

    private func box(day: String, title: String, dot: Color, background: Color, struck: Bool) -> some View {
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
                .strikethrough(struck)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(background, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    // MARK: Footer

    @ViewBuilder
    private var footer: some View {
        switch proposal.status {
        case .pending:
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
                Button(action: { store.decline(proposalIn: messageID) }) {
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
        case .applied:
            stateLabel("Applied to your plan", symbol: "checkmark.circle.fill", color: Theme.green)
        case .declined:
            stateLabel("Plan kept as it was", symbol: "minus.circle", color: Theme.muted)
        }
    }

    private func stateLabel(_ text: String, symbol: String, color: Color) -> some View {
        HStack(spacing: 6) {
            Image(systemName: symbol)
                .font(.system(size: 14, weight: .semibold))
            Text(text)
                .font(.system(size: 13, weight: .semibold))
        }
        .foregroundStyle(color)
        .frame(height: 28)
    }

    private func apply() {
        store.apply(proposalIn: messageID)
        Task {
            await SyncService.pushAll(store: store)
        }
    }

    // MARK: Lines

    /// Before/after values. Once applied, the workout already holds the new values, so only "after" is shown.
    private var lines: [CoachChatChangeLine] {
        var result: [CoachChatChangeLine] = []
        for change in proposal.changes {
            guard let workout = store.workout(id: change.workoutID) else {
                // The session no longer exists: it was removed by this proposal, or the plan was rebuilt.
                if change.remove, proposal.status == .applied {
                    result.append(CoachChatChangeLine(id: change.id, beforeDay: "", beforeTitle: "",
                                                      afterDay: "Removed from your plan", afterTitle: "Session",
                                                      removed: true))
                }
                continue
            }
            let before = detail(title: workout.title, kind: workout.kind, km: workout.distanceKm,
                                minutes: workout.durationMin)
            if change.remove {
                result.append(CoachChatChangeLine(id: change.id, beforeDay: dayLabel(workout.date), beforeTitle: before,
                                                  afterDay: dayLabel(workout.date), afterTitle: "Removed",
                                                  removed: false))
                continue
            }
            let after = detail(title: change.newTitle ?? workout.title,
                               kind: change.newKind ?? workout.kind,
                               km: change.newDistanceKm ?? workout.distanceKm,
                               minutes: change.newDurationMin ?? workout.durationMin)
            result.append(CoachChatChangeLine(id: change.id, beforeDay: dayLabel(workout.date), beforeTitle: before,
                                              afterDay: dayLabel(change.newDate ?? workout.date), afterTitle: after,
                                              removed: false))
        }
        return result
    }

    private func detail(title: String, kind: WorkoutKind, km: Double, minutes: Int) -> String {
        if kind.isRun, km > 0 { return "\(title) · \(Fmt.km(km))" }
        if minutes > 0 { return "\(title) · \(Fmt.minutes(minutes))" }
        return title
    }

    private func dayLabel(_ date: Date) -> String {
        let calendar = Calendar.plan
        if calendar.isDateInToday(date) { return "Today" }
        if calendar.isDateInTomorrow(date) { return "Tomorrow" }
        return Fmt.day(date, "EEE d MMM")
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
