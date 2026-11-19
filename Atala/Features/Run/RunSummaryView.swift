import SwiftUI

/// Summary of a run: figures, route, fast repetitions and a word from the coach.
/// Works pushed in a NavigationStack, in a sheet, or as the last screen of the run flow.
struct RunSummaryView: View {
    let run: RunRecord
    private let onDone: (() -> Void)?

    @EnvironmentObject private var store: AppStore
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
                if fastSplits.count >= 2 {
                    RunRepsCard(splits: fastSplits)
                }
                if analysis.kmSplits.count >= 2 {
                    RunSplitsCard(splits: analysis.kmSplits, fastestID: analysis.fastestSplit?.id)
                }
                if let gain = analysis.elevationGainM, analysis.elevation.count >= 2 {
                    RunElevationCard(points: analysis.elevation, gainM: gain)
                }
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
                PrimaryButton(title: "Done") { close() }
            }
        }
        .navigationBarTitleDisplayMode(.inline)
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
        case .manual: return "\(day) · Entered by hand"
        case .health: return "\(day) · From Apple Health"
        }
    }

    private var titleLine: String {
        let record = shown
        let title = record.title.isEmpty ? "Run" : record.title
        if let id = record.workoutID, store.workout(id: id) != nil { return "\(title), done" }
        return title
    }

    // MARK: Figures

    private func statsCard(_ analysis: RunAnalysis) -> some View {
        let columns: [GridItem] = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]
        return LazyVGrid(columns: columns, alignment: .leading, spacing: 16) {
            RunSummaryStat(value: RunFormat.distance(shown.distanceM), label: "Distance", large: true)
            RunSummaryStat(value: Fmt.duration(shown.durationSec), label: "Duration", large: true)
            RunSummaryStat(value: "\(Fmt.pace(shown.avgPace))/km", label: "Average pace", large: false)
            if let heartRate = shown.avgHeartRate {
                RunSummaryStat(value: "\(heartRate) bpm", label: "Average heart rate", large: false)
            }
            if let peak = shown.maxHeartRate {
                RunSummaryStat(value: "\(peak) bpm", label: "Max heart rate", large: false)
            }
            if let gain = analysis.elevationGainM {
                RunSummaryStat(value: "+\(gain) m", label: "Elevation gain", large: false)
            }
            if let fastest = analysis.fastestSplit {
                RunSummaryStat(value: "\(Fmt.pace(fastest.pace))/km", label: "Fastest kilometre", large: false)
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.green, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }

    // MARK: Coach and feeling

    private var coachLine: some View {
        HStack(alignment: .bottom, spacing: 8) {
            CoachAvatar(coach: store.coach, size: 36)
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
                Text("How it felt")
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
            return "Thank you for telling me. Rest for the next few days and skip anything that hurts. "
                + "If it is still there after that, or if it gets worse, please see a health professional. "
                + "I can lighten your plan in the meantime, just ask me."
        }
        let reps = run.splits.filter { $0.kind == .work && $0.distanceM > 0 }
        if reps.count >= 2 {
            return repsLine(reps, feeling: run.feeling)
        }
        if run.distanceM < 50 {
            return "No distance was recorded this time (location off, or an indoor run). "
                + "Your time, \(Fmt.duration(run.durationSec)), still counts."
        }
        let distance = RunFormat.distance(run.distanceM)
        let pace = Fmt.pace(run.avgPace)
        switch run.feeling {
        case .some(.easy):
            return "That looked comfortable: \(distance) at \(pace)/km. Exactly how an easy day should feel."
        case .some(.hard):
            return "A tough one, and you saw it through: \(distance). Sleep well and keep the next run really easy."
        default:
            return "\(distance) in \(Fmt.duration(run.durationSec)), at \(pace)/km. One more in the bank."
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
            text = "\(reps.count) steady reps, all on target"
            text += lastIsFastest ? ", and the last one was your fastest." : ". Rep \(fastestNumber) was your fastest."
        } else if onTarget * 2 >= reps.count {
            text = "\(onTarget) of \(reps.count) reps on target. "
            text += lastIsFastest
                ? "You finished with your fastest one, a good sign."
                : "Rep \(fastestNumber) was your fastest. Next time, aim for the same pace from first to last."
        } else {
            text = "The pace was hard to hold today, and that happens. We will come back to it with fresh legs."
        }
        if feeling == .hard {
            text += " It cost you a lot, so the next run stays really easy."
        } else {
            text += " Keep your next run easy to recover."
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
