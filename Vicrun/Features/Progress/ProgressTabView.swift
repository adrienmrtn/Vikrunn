import SwiftUI

/// Progress: prediction, weekly volume, consistency, bests and the run history.
struct ProgressTabView: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text("Progress")
                        .font(.system(size: 28, weight: .bold))
                        .tracking(-0.6)
                        .foregroundStyle(Theme.ink)
                    MainHeroImage(name: "illu_progress", height: 170)
                        .padding(.top, 16)
                    ProgressTabPredictionPanel(plan: store.plan)
                        .padding(.horizontal, 12)
                        .padding(.top, -16)
                    if let plan = store.plan {
                        ProgressTabVolumeChart(plan: plan)
                            .padding(.top, 18)
                        consistencyRow(plan)
                            .padding(.top, 14)
                    }
                    totalsRow
                        .padding(.top, 16)
                    ProgressTabBestsList(bests: ProgressTabStats.bests(store.runs))
                        .padding(.top, 18)
                    ProgressTabHistory()
                        .padding(.top, 18)
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 12)
                .padding(.bottom, 28)
            }
            .background(Color.white)
            .toolbar(.hidden, for: .navigationBar)
        }
    }

    private func consistencyRow(_ plan: TrainingPlan) -> some View {
        // Sessions whose day has passed (done or missed), plus the ones already done.
        let settled = plan.allWorkouts.filter { $0.status != .planned }
        let done = settled.filter { $0.status == .done }.count
        let percent = settled.isEmpty ? "--" : "\(Int((Double(done) / Double(settled.count) * 100).rounded())) %"
        return HStack(alignment: .top, spacing: 8) {
            MainStat(value: String(format: "%.1f km/h", plan.vmaKmh), label: "VMA")
            MainStat(value: "\(done) of \(settled.count)", label: "Sessions done")
            MainStat(value: percent, label: "Consistency")
        }
        .padding(.top, 10)
        .overlay(alignment: .top) {
            Rectangle().fill(Theme.hairline).frame(height: 1)
        }
    }

    private var totalsRow: some View {
        let seconds = store.runs.reduce(0) { $0 + $1.durationSec }
        return VStack(alignment: .leading, spacing: 10) {
            MainSectionTitle(title: "Totals")
            HStack(alignment: .top, spacing: 8) {
                MainStat(value: Fmt.km(store.totalKm), label: "Distance")
                MainStat(value: "\(store.runs.count)", label: store.runs.count == 1 ? "Run" : "Runs")
                MainStat(value: Fmt.minutes(seconds / 60), label: "Time running")
            }
        }
    }
}

/// Glass panel: today's level on the goal distance, and the goal.
private struct ProgressTabPredictionPanel: View {
    let plan: TrainingPlan?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            if let plan {
                if let current = plan.currentSeconds {
                    prediction(plan: plan, current: current)
                } else {
                    level(plan: plan)
                }
            } else {
                Text("No prediction yet")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(Theme.ink)
                Text("Build your plan to see where you stand today and where it can take you.")
                    .font(.system(size: 13))
                    .foregroundStyle(Color(hex: 0x5A5A5A))
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .mainGlass(radius: 22)
    }

    @ViewBuilder
    private func prediction(plan: TrainingPlan, current: Int) -> some View {
        let goal = plan.targetSeconds ?? plan.predictedSeconds
        Text("\(plan.goal.title) · your level today")
            .font(.system(size: 13))
            .foregroundStyle(Color(hex: 0x5A5A5A))
        HStack(alignment: .firstTextBaseline) {
            Text(Fmt.duration(current))
                .font(.system(size: 38, weight: .bold))
                .tracking(-1.4)
                .foregroundStyle(Theme.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Spacer(minLength: 8)
            if let goal {
                Text(gapText(current: current, goal: goal))
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.green)
            }
        }
        if let goal {
            MainProgressBar(fraction: current > 0 ? Double(goal) / Double(current) : 0,
                            label: "Level compared with the goal")
                .padding(.top, 4)
        }
        Text(footnote(plan: plan))
            .font(.system(size: 12))
            .foregroundStyle(Color(hex: 0x5A5A5A))
            .fixedSize(horizontal: false, vertical: true)
    }

    @ViewBuilder
    private func level(plan: TrainingPlan) -> some View {
        Text("Your level today")
            .font(.system(size: 13))
            .foregroundStyle(Color(hex: 0x5A5A5A))
        Text(String(format: "%.1f km/h", plan.vmaKmh))
            .font(.system(size: 38, weight: .bold))
            .tracking(-1.4)
            .foregroundStyle(Theme.ink)
        Text("Estimated VMA · easy pace \(Fmt.pace(plan.paces.easy))/km")
            .font(.system(size: 12))
            .foregroundStyle(Color(hex: 0x5A5A5A))
    }

    private func gapText(current: Int, goal: Int) -> String {
        let gap = current - goal
        if gap <= 0 { return "Goal within reach" }
        return "− \(Fmt.duration(gap)) to the goal"
    }

    private func footnote(plan: TrainingPlan) -> String {
        if let target = plan.targetSeconds {
            if let predicted = plan.predictedSeconds, predicted != target {
                return "Goal \(Fmt.goalTime(target)) · the plan aims for \(Fmt.goalTime(predicted)) on race day"
            }
            return "Goal \(Fmt.goalTime(target))"
        }
        if let predicted = plan.predictedSeconds {
            return "The plan aims for \(Fmt.goalTime(predicted)) at the end"
        }
        return "Your prediction sharpens as you log runs."
    }
}

/// Weekly volume of the plan weeks: planned (light) against done (dark).
private struct ProgressTabVolumeChart: View {
    @EnvironmentObject private var store: AppStore
    let plan: TrainingPlan

    private let chartHeight: CGFloat = 84
    private let plannedColor = Color(hex: 0xB9D2C4)

    var body: some View {
        let currentID = store.currentWeek?.id
        let done: [Double] = plan.weeks.map { store.mainDoneKm(in: $0) }
        let top = max(1, max(plan.peakKm, done.max() ?? 0))
        let thisWeek: Double = currentIndex.map { done[$0] } ?? 0
        VStack(alignment: .leading, spacing: 12) {
            MainSectionTitle(title: "Weekly volume",
                             trailing: "\(Self.number(thisWeek)) km this week")
            HStack(alignment: .bottom, spacing: 10) {
                ForEach(Array(plan.weeks.enumerated()), id: \.element.id) { index, week in
                    column(number: index + 1,
                           planned: week.plannedKm,
                           done: done[index],
                           top: top,
                           isCurrent: week.id == currentID)
                }
            }
            HStack(spacing: 14) {
                legend(color: plannedColor, title: "Planned")
                legend(color: Theme.green, title: "Done")
            }
        }
    }

    private var currentIndex: Int? {
        guard let id = store.currentWeek?.id else { return nil }
        return plan.weeks.firstIndex { $0.id == id }
    }

    private func column(number: Int, planned: Double, done: Double, top: Double, isCurrent: Bool) -> some View {
        let plannedHeight = max(3, chartHeight * CGFloat(planned / top))
        let doneHeight = done > 0 ? max(3, chartHeight * CGFloat(done / top)) : 0
        return VStack(spacing: 5) {
            ZStack(alignment: .bottom) {
                RoundedRectangle(cornerRadius: 4, style: .continuous)
                    .fill(plannedColor)
                    .frame(height: plannedHeight)
                RoundedRectangle(cornerRadius: 4, style: .continuous)
                    .fill(Theme.green)
                    .frame(height: doneHeight)
            }
            .frame(maxWidth: .infinity)
            .frame(height: chartHeight, alignment: .bottom)
            Text("W\(number)")
                .font(.system(size: 11, weight: isCurrent ? .semibold : .regular))
                .foregroundStyle(isCurrent ? Theme.ink : Theme.muted)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Week \(number): \(Self.number(done)) of \(Self.number(planned)) kilometres")
    }

    private func legend(color: Color, title: String) -> some View {
        HStack(spacing: 6) {
            RoundedRectangle(cornerRadius: 2, style: .continuous)
                .fill(color)
                .frame(width: 10, height: 10)
            Text(title)
                .font(.system(size: 12))
                .foregroundStyle(Theme.muted)
        }
    }

    private static func number(_ km: Double) -> String {
        abs(km - km.rounded()) < 0.05 ? "\(Int(km.rounded()))" : String(format: "%.1f", km)
    }
}

#Preview {
    ProgressTabView()
        .environmentObject(AppStore.preview)
        .environmentObject(MainChrome())
}
