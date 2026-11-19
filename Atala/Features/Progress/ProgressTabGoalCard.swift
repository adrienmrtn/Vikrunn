import SwiftUI

/// The goal: the runner's level today on the goal distance, how far the goal is, and the paces
/// the plan works from. All figures come from the plan as it was computed.
struct ProgressTabGoalCard: View {
    let goal: ProgressTabGoal?

    var body: some View {
        ProgressTabSection(title: L("Your goal"), trailing: goal?.title) {
            VStack(alignment: .leading, spacing: 0) {
                if let goal {
                    if let current = goal.currentSeconds {
                        prediction(goal: goal, current: current)
                    } else {
                        level(goal: goal)
                    }
                } else {
                    ProgressTabHint(systemName: "flag.checkered",
                                    text: L("Build your plan to see where you stand today and where it can take you."))
                }
            }
            .progressTabCard()
        }
    }

    @ViewBuilder
    private func prediction(goal: ProgressTabGoal, current: Int) -> some View {
        Text(L("Your level today"))
            .font(.system(size: 13))
            .foregroundStyle(Theme.muted)
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Text(Fmt.duration(current))
                .font(.system(size: 40, weight: .bold, design: .rounded))
                .foregroundStyle(Theme.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Spacer(minLength: 8)
            if let target = goal.goalSeconds {
                Text(gapText(current: current, goal: target))
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Theme.green)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                    .padding(.horizontal, 10)
                    .frame(height: 26)
                    .background(Theme.greenTint, in: Capsule())
            }
        }
        .padding(.top, 2)
        if let target = goal.goalSeconds {
            MainProgressBar(fraction: current > 0 ? Double(target) / Double(current) : 0,
                            label: L("Level compared with the goal"))
                .padding(.top, 10)
        }
        Text(goal.footnote)
            .font(.system(size: 13))
            .foregroundStyle(Theme.muted)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.top, 10)
        paces(goal: goal)
    }

    @ViewBuilder
    private func level(goal: ProgressTabGoal) -> some View {
        Text(L("Your level today"))
            .font(.system(size: 13))
            .foregroundStyle(Theme.muted)
        HStack(alignment: .firstTextBaseline, spacing: 5) {
            Text(String(format: "%.1f", goal.vmaKmh))
                .font(.system(size: 40, weight: .bold, design: .rounded))
                .foregroundStyle(Theme.ink)
            Text("km/h")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Theme.muted)
        }
        .padding(.top, 2)
        .accessibilityElement(children: .combine)
        Text(L("Estimated VMA · easy pace %@/km", Fmt.pace(goal.easyPace)))
            .font(.system(size: 13))
            .foregroundStyle(Theme.muted)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.top, 6)
    }

    private func paces(goal: ProgressTabGoal) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Rectangle()
                .fill(Theme.hairline)
                .frame(height: 1)
            HStack(alignment: .top, spacing: 12) {
                ProgressTabFigure(value: String(format: "%.1f", goal.vmaKmh), unit: "km/h",
                                  label: "VMA", size: 18)
                ProgressTabDivider()
                ProgressTabFigure(value: Fmt.pace(goal.easyPace), unit: "/km",
                                  label: L("Easy pace"), size: 18)
            }
        }
        .padding(.top, 16)
    }

    private func gapText(current: Int, goal: Int) -> String {
        let gap = current - goal
        if gap <= 0 { return L("Goal within reach") }
        return L("− %@ to the goal", Fmt.duration(gap))
    }
}
