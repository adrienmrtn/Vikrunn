import SwiftUI

/// Summary of a run: figures, route, fast repetitions and a word from the coach.
/// Works pushed in a NavigationStack, in a sheet, or as the last screen of the run flow.
struct RunSummaryView: View {
    let run: RunRecord
    private let onDone: (() -> Void)?

    @EnvironmentObject private var store: AppStore
    @ObservedObject private var purchases = PurchaseService.shared
    @State private var showPaywall = false
    @Environment(\.dismiss) private var dismiss

    init(run: RunRecord) {
        self.run = run
        self.onDone = nil
    }

    /// Used by the run flow, where "Done" closes the whole flow.
    init(run: RunRecord, onDone: @escaping () -> Void) {
        self.run = run
        self.onDone = onDone
    }

    /// The run as it is in the store now (it can be attached to a session from this screen).
    /// Falls back to the value it was opened with, e.g. while closing after a deletion.
    private var shown: RunRecord { store.run(id: run.id) ?? run }

    private var fastSplits: [RunSplit] { shown.splits.filter { $0.kind == .work } }

    private func close() {
        if let onDone { onDone() } else { dismiss() }
    }

    var body: some View {
        let analysis = RunAnalysis(run: shown)
        return ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header
                statsCard(analysis)
                if shown.route.count >= 2 {
                    RunRouteMap(route: shown.route)
                }
                if purchases.isPro, fastSplits.count >= 2 {
                    RunRepsCard(splits: fastSplits)
                }
                detailCards(analysis)
                coachLine
                if let feeling = shown.feeling {
                    feelingRow(feeling)
                }
                RunSummaryActions(run: shown, onClose: { close() })
                    .padding(.top, 4)
            }
            .padding(.horizontal, Theme.screenPadding)
            .padding(.top, 12)
            .padding(.bottom, 16)
        }
        .scrollIndicators(.hidden)
        .background(Color.white.ignoresSafeArea())
        .safeAreaInset(edge: .bottom, spacing: 0) {
            BottomBar {
                PrimaryButton(title: L("Done")) { close() }
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .proPaywall(isPresented: $showPaywall)
    }

    /// Pace per kilometre and elevation: shown with Atala Pro, offered otherwise.
    @ViewBuilder
    private func detailCards(_ analysis: RunAnalysis) -> some View {
        let hasSplits = analysis.kmSplits.count >= 2
        let hasElevation = analysis.elevationGainM != nil && analysis.elevation.count >= 2
        if purchases.isPro {
            if hasSplits {
                RunSplitsCard(splits: analysis.kmSplits, fastestID: analysis.fastestSplit?.id)
            }
            if let gain = analysis.elevationGainM, hasElevation {
                RunElevationCard(points: analysis.elevation, gainM: gain)
            }
        } else if hasSplits || hasElevation {
            Button {
                showPaywall = true
            } label: {
                HStack(spacing: 14) {
                    IconTile(systemName: "lock.fill", size: 44)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(L("Pace per kilometre and elevation"))
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(Theme.ink)
                        Text(L("See the detail of every run with Atala Pro"))
                            .font(.system(size: 13))
                            .foregroundStyle(Theme.muted)
                    }
                    Spacer(minLength: 4)
                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.faint)
                }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .card(radius: 22)
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: Header

    private var header: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(dateLine)
                .font(.system(size: 13))
                .foregroundStyle(Theme.muted)
            Text(titleLine)
                .font(Theme.title(28))
                .foregroundStyle(Theme.ink)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var dateLine: String {
        let day = Fmt.day(shown.date)
        switch shown.source {
        case .gps: return day
        case .manual: return L("%@ · Entered by hand", day)
        case .health: return L("%@ · From Apple Health", day)
        }
    }

    private var titleLine: String {
        let record = shown
        let title = record.title.isEmpty ? L("Run") : L(record.title)
        if let id = record.workoutID, store.workout(id: id) != nil { return L("%@, done", title) }
        return title
    }

    // MARK: Figures

    private func statsCard(_ analysis: RunAnalysis) -> some View {
        let columns: [GridItem] = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]
        return LazyVGrid(columns: columns, alignment: .leading, spacing: 16) {
            RunSummaryStat(value: RunFormat.distance(shown.distanceM), label: L("Distance"), large: true)
            RunSummaryStat(value: Fmt.duration(shown.durationSec), label: L("Duration"), large: true)
            RunSummaryStat(value: "\(Fmt.pace(shown.avgPace))/km", label: L("Average pace"), large: false)
            if let heartRate = shown.avgHeartRate {
                RunSummaryStat(value: "\(heartRate) bpm", label: L("Average heart rate"), large: false)
            }
            if let peak = shown.maxHeartRate {
                RunSummaryStat(value: "\(peak) bpm", label: L("Max heart rate"), large: false)
            }
            if purchases.isPro, let gain = analysis.elevationGainM {
                RunSummaryStat(value: "+\(gain) m", label: L("Elevation gain"), large: false)
            }
            if purchases.isPro, let fastest = analysis.fastestSplit {
                RunSummaryStat(value: "\(Fmt.pace(fastest.pace))/km", label: L("Fastest kilometre"), large: false)
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.green, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }

    // MARK: Coach and feeling

    private var coachLine: some View {
        HStack(alignment: .bottom, spacing: 8) {
            if onDone != nil {
                // Right after the run: the coach in full, cheering.
                CoachPoseView(coach: store.coach, pose: .cheer)
                    .frame(width: 76, height: 122)
            } else {
                CoachAvatar(coach: store.coach, size: 36)
            }
            Text(RunSummaryCoach.line(for: shown))
                .font(.system(size: 15))
                .foregroundStyle(Theme.ink)
                .lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .background(Theme.bubble, in: UnevenRoundedRectangle(topLeadingRadius: 20, bottomLeadingRadius: 6,
                                                                     bottomTrailingRadius: 20, topTrailingRadius: 20,
                                                                     style: .continuous))
            Spacer(minLength: 0)
        }
    }

    private func feelingRow(_ feeling: Feeling) -> some View {
        HStack(spacing: 14) {
            Circle()
                .fill(RunFeelingStyle.background(feeling))
                .frame(width: 44, height: 44)
                .overlay(
                    Image(systemName: RunFeelingStyle.symbol(feeling))
                        .font(.system(size: 19, weight: .semibold))
                        .foregroundStyle(RunFeelingStyle.tint(feeling))
                )
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(L("How it felt"))
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.muted)
                Text(feeling.title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Theme.ink)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .card()
        .accessibilityElement(children: .combine)
    }
}

private struct RunSummaryStat: View {
    let value: String
    let label: String
    let large: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value)
                .font(.system(size: large ? 28 : 20, weight: .bold))
                .monospacedDigit()
                .foregroundStyle(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            Text(label)
                .font(.system(size: 12))
                .foregroundStyle(Theme.greenOnDark)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

/// Rule-based comment of the coach, computed from the run itself.
enum RunSummaryCoach {
    static func line(for run: RunRecord) -> String {
        if run.feeling == .pain {
            return L("Thank you for telling me. Rest for the next few days and skip anything that hurts. If it is still there after that, or if it gets worse, please see a health professional. I can lighten your plan in the meantime, just ask me.")
        }
        let reps = run.splits.filter { $0.kind == .work && $0.distanceM > 0 }
        if reps.count >= 2 {
            return repsLine(reps, feeling: run.feeling)
        }
        if run.distanceM < 50 {
            return L("No distance was recorded this time (location off, or an indoor run). Your time, %@, still counts.", Fmt.duration(run.durationSec))
        }
        let distance = RunFormat.distance(run.distanceM)
        let pace = Fmt.pace(run.avgPace)
        switch run.feeling {
        case .some(.easy):
            return L("That looked comfortable: %@ at %@/km. Exactly how an easy day should feel.", distance, pace)
        case .some(.hard):
            return L("A tough one, and you saw it through: %@. Sleep well and keep the next run really easy.", distance)
        default:
            return L("%@ in %@, at %@/km. One more in the bank.", distance, Fmt.duration(run.durationSec), pace)
        }
    }

    private static func repsLine(_ reps: [RunSplit], feeling: Feeling?) -> String {
        let onTarget = reps.filter { $0.onTarget }.count
        let paces = reps.map { $0.pace }
        let fastest = paces.min() ?? 0
        let fastestNumber = (paces.firstIndex(of: fastest) ?? 0) + 1
        let lastIsFastest = fastestNumber == reps.count

        var text: String
        if onTarget == reps.count {
            text = lastIsFastest
                ? L("%@ steady reps, all on target, and the last one was your fastest.", reps.count)
                : L("%@ steady reps, all on target. Rep %@ was your fastest.", reps.count, fastestNumber)
        } else if onTarget * 2 >= reps.count {
            text = lastIsFastest
                ? L("%@ of %@ reps on target. You finished with your fastest one, a good sign.", onTarget, reps.count)
                : L("%@ of %@ reps on target. Rep %@ was your fastest. Next time, aim for the same pace from first to last.", onTarget, reps.count, fastestNumber)
        } else {
            text = L("The pace was hard to hold today, and that happens. We will come back to it with fresh legs.")
        }
        if feeling == .hard {
            text += " " + L("It cost you a lot, so the next run stays really easy.")
        } else {
            text += " " + L("Keep your next run easy to recover.")
        }
        return text
    }
}

#Preview {
    NavigationStack {
        RunSummaryView(run: RunSummarySample.run)
    }
    .environmentObject(AppStore.preview)
}
