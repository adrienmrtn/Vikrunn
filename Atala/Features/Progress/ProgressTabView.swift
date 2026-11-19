import SwiftUI

/// Progress: the period summary, streak and consistency, weekly distance, the goal, bests and the
/// run history. Part of Atala Pro: without it the tab is shown blurred behind a padlock card.
struct ProgressTabView: View {
    @EnvironmentObject private var store: AppStore
    @ObservedObject private var purchases = PurchaseService.shared
    @State private var period: ProgressTabPeriod = .week

    /// Sample figures are only ever drawn behind the blur of the locked tab, for a runner who has
    /// little or nothing of their own yet. With Atala Pro the tab always shows the real data.
    private var showsSample: Bool {
        !purchases.isPro && store.runs.count < 4
    }

    var body: some View {
        let sample: Bool = showsSample
        let runs: [RunRecord] = sample ? ProgressTabSample.runs : store.runs
        let streak: Streak = sample ? StreakEngine.weekly(runs: runs, workouts: []) : store.streak
        let goal: ProgressTabGoal? = goalInfo(sample: sample)
        let consistency: ProgressTabConsistency? = consistencyInfo(sample: sample)
        let weeks: [ProgressTabWeek] = ProgressTabStats.weeks(runs, plan: store.plan)
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    ProgressTabHero(summary: ProgressTabStats.summary(runs, period: period),
                                    period: $period,
                                    hasRuns: !runs.isEmpty)
                        .mainStagedAppear(index: 0)
                    ProgressTabStreakCard(streak: streak, consistency: consistency)
                        .mainStagedAppear(index: 1)
                    ProgressTabChart(weeks: weeks, showsPlanned: store.plan != nil)
                        .mainStagedAppear(index: 2)
                    ProgressTabGoalCard(goal: goal)
                        .mainStagedAppear(index: 3)
                    ProgressTabBestsList(bests: ProgressTabStats.bests(runs))
                        .mainStagedAppear(index: 4)
                    ProgressTabHistory(runs: runs, isSample: sample)
                        .mainStagedAppear(index: 5)
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 8)
                .padding(.bottom, 28)
                .mainStagedEntrance(.progress)
            }
            .proLocked(symbol: "chart.line.uptrend.xyaxis",
                       title: L("Unlock your progress"),
                       message: L("See your weekly distance, your streak, your personal bests and how close you are to your goal."))
            .background(Color.white)
            .navigationTitle(L("Progress"))
            .navigationBarTitleDisplayMode(.large)
        }
    }

    private func goalInfo(sample: Bool) -> ProgressTabGoal? {
        if let plan = store.plan {
            return ProgressTabStats.goal(plan)
        }
        return sample ? ProgressTabSample.goal : nil
    }

    private func consistencyInfo(sample: Bool) -> ProgressTabConsistency? {
        if let plan = store.plan {
            // Sessions whose day has passed (done or missed), plus the ones already done.
            let settled: [Workout] = plan.allWorkouts.filter { $0.status != .planned }
            let done: Int = settled.filter { $0.status == .done }.count
            if !settled.isEmpty || !sample {
                return ProgressTabConsistency(done: done, settled: settled.count)
            }
        }
        if sample {
            return ProgressTabConsistency(done: ProgressTabSample.sessionsDone,
                                          settled: ProgressTabSample.sessionsSettled)
        }
        return nil
    }
}

/// Sessions done out of the ones whose day has come.
struct ProgressTabConsistency {
    let done: Int
    let settled: Int

    /// Whole percent, nil while no session has come up yet.
    var percent: Int? {
        settled > 0 ? Int((Double(done) / Double(settled) * 100).rounded()) : nil
    }
}

// MARK: - Hero

/// The summary of the week or the month: the distance in large, then runs, time and pace, and how
/// it compares with the period before.
private struct ProgressTabHero: View {
    let summary: ProgressTabSummary
    @Binding var period: ProgressTabPeriod
    let hasRuns: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            periodPicker
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(String(format: "%.1f", summary.km))
                    .font(.system(size: 60, weight: .bold, design: .rounded))
                    .foregroundStyle(Color.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
                Text("km")
                    .font(.system(size: 22, weight: .semibold, design: .rounded))
                    .foregroundStyle(Theme.greenOnDark)
            }
            .padding(.top, 18)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(L("%@ kilometres", ProgressTabStats.number(summary.km)))
            changeLine
                .padding(.top, 2)
            Rectangle()
                .fill(Color.white.opacity(0.14))
                .frame(height: 1)
                .padding(.vertical, 16)
            HStack(alignment: .top, spacing: 12) {
                ProgressTabFigure(value: "\(summary.runs)",
                                  label: summary.runs == 1 ? L("Run") : L("Runs"),
                                  valueColor: Color.white, mutedColor: Theme.greenOnDark)
                ProgressTabFigure(value: Fmt.minutes(summary.seconds / 60),
                                  label: L("Time"),
                                  valueColor: Color.white, mutedColor: Theme.greenOnDark)
                ProgressTabFigure(value: Fmt.pace(summary.pace), unit: "/km",
                                  label: L("Average pace"),
                                  valueColor: Color.white, mutedColor: Theme.greenOnDark)
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.forest, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .shadow(color: Theme.forest.opacity(0.18), radius: 14, x: 0, y: 8)
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.25), value: period)
    }

    private var periodPicker: some View {
        HStack(spacing: 4) {
            ForEach(ProgressTabPeriod.allCases) { option in
                let selected: Bool = option == period
                Button {
                    Haptics.select()
                    period = option
                } label: {
                    Text(option.title)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(selected ? Theme.forest : Theme.greenOnDark)
                        .lineLimit(1)
                        .padding(.horizontal, 14)
                        .frame(height: 32)
                        .background(selected ? Color.white : Color.clear, in: Capsule())
                        .contentShape(Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
        .padding(3)
        .background(Color.white.opacity(0.12), in: Capsule())
    }

    private var changeLine: some View {
        HStack(spacing: 6) {
            Image(systemName: changeSymbol)
                .font(.system(size: 12, weight: .bold))
                .accessibilityHidden(true)
            Text(changeText)
                .font(.system(size: 14, weight: .medium))
                .fixedSize(horizontal: false, vertical: true)
        }
        .foregroundStyle(Theme.greenOnDark)
    }

    private var isAhead: Bool {
        summary.previousKm > 0 && summary.km >= summary.previousKm
    }

    private var changeSymbol: String {
        if !hasRuns { return "plus.circle.fill" }
        if summary.previousKm <= 0 { return "calendar" }
        return isAhead ? "arrow.up.right" : "flag.fill"
    }

    private var changeText: String {
        if !hasRuns {
            return L("Tap + to record your first run.")
        }
        let isWeek: Bool = period == .week
        if summary.previousKm <= 0 {
            return isWeek ? L("No runs last week to compare with") : L("No runs last month to compare with")
        }
        if isAhead {
            let percent: Int = summary.changePercent ?? 0
            if percent <= 0 {
                return isWeek ? L("Level with last week") : L("Level with last month")
            }
            let text: String = "\(percent) %"
            return isWeek ? L("%@ more than last week", text) : L("%@ more than last month", text)
        }
        let gap: String = Fmt.km(summary.previousKm - summary.km)
        return isWeek ? L("%@ to match last week", gap) : L("%@ to match last month", gap)
    }
}

// MARK: - Streak and consistency

/// The weekly streak, the best one and the share of sessions done, in one compact card.
private struct ProgressTabStreakCard: View {
    let streak: Streak
    let consistency: ProgressTabConsistency?

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top, spacing: 12) {
                ProgressTabFigure(value: StreakEngine.weeks(streak.current),
                                  label: L("Streak"), size: 18,
                                  valueColor: streak.current > 0 ? Theme.orangeDeep : Theme.ink)
                ProgressTabDivider()
                ProgressTabFigure(value: StreakEngine.weeks(streak.best),
                                  label: L("Best streak"), size: 18)
                if let consistency {
                    ProgressTabDivider()
                    ProgressTabFigure(value: percentValue(consistency),
                                      unit: consistency.percent == nil ? nil : "%",
                                      label: L("Sessions done"), size: 18)
                        .accessibilityLabel(L("%@ of %@ sessions done", consistency.done, consistency.settled))
                }
            }
            HStack(alignment: .top, spacing: 8) {
                Image(systemName: "flame.fill")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(streak.activeThisWeek ? Theme.orange : Theme.faint)
                    .accessibilityHidden(true)
                Text(StreakEngine.nudge(for: streak))
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.body)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .progressTabCard()
    }

    private func percentValue(_ consistency: ProgressTabConsistency) -> String {
        if let percent = consistency.percent { return "\(percent)" }
        return "--"
    }
}

#Preview {
    ProgressTabView()
        .environmentObject(AppStore.preview)
        .environmentObject(MainChrome())
}
