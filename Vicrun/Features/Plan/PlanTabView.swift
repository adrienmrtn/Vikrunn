import SwiftUI

/// The whole plan: goal, phases and every week with its sessions.
struct PlanTabView: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var chrome: MainChrome
    @State private var expanded: Set<UUID> = []
    @State private var didExpandCurrent = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text(headerCaption)
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.muted)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                        .mainStagedAppear(index: 0)
                    MainHeroImage(name: "illu_plan", height: 170)
                        .padding(.top, 14)
                        .mainStagedAppear(index: 1)
                    if let plan = store.plan {
                        PlanTabOverviewPanel(plan: plan, currentWeekID: store.currentWeek?.id)
                            .padding(.horizontal, 12)
                            .padding(.top, -16)
                            .mainStagedAppear(index: 2)
                        weekList(plan)
                            .padding(.top, 12)
                            .mainStagedAppear(index: 3)
                    } else {
                        emptyPanel
                            .padding(.horizontal, 12)
                            .padding(.top, -16)
                            .mainStagedAppear(index: 2)
                    }
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 4)
                .padding(.bottom, 28)
                .mainStagedEntrance(.plan)
            }
            .background(Color.white)
            .navigationTitle("Plan")
            .navigationBarTitleDisplayMode(.large)
            .onAppear(perform: expandCurrentWeek)
            .onChange(of: store.plan?.id) { _, _ in
                didExpandCurrent = false
                expanded = []
                expandCurrentWeek()
            }
        }
    }

    private var headerCaption: String {
        guard let plan = store.plan else { return "No plan yet" }
        var parts: [String] = [plan.goal.title]
        if let raceDate = plan.raceDate {
            parts.append("race on \(Fmt.day(raceDate, "d MMM"))")
        }
        if let target = plan.targetSeconds {
            parts.append("goal \(Fmt.goalTime(target))")
        }
        return parts.joined(separator: " · ")
    }

    private func weekList(_ plan: TrainingPlan) -> some View {
        let currentID = store.currentWeek?.id
        return VStack(spacing: 0) {
            ForEach(Array(plan.weeks.enumerated()), id: \.element.id) { index, week in
                PlanTabWeekSection(
                    week: week,
                    number: index + 1,
                    isCurrent: week.id == currentID,
                    isExpanded: expanded.contains(week.id),
                    isLast: index == plan.weeks.count - 1
                ) {
                    toggle(week.id)
                }
            }
        }
    }

    private var emptyPanel: some View {
        MainEmptyState(systemName: "calendar",
                       title: "No plan yet",
                       message: "Choose a goal and your running days in your profile to build your plan.",
                       actionTitle: "Open my profile") {
            chrome.showProfile = true
        }
        .mainGlass(radius: 20)
    }

    private func toggle(_ id: UUID) {
        withAnimation(.easeInOut(duration: 0.2)) {
            if expanded.contains(id) {
                expanded.remove(id)
            } else {
                expanded.insert(id)
            }
        }
    }

    private func expandCurrentWeek() {
        guard !didExpandCurrent, let week = store.currentWeek else { return }
        didExpandCurrent = true
        expanded.insert(week.id)
    }
}

/// Glass panel: progress through the plan and its phases.
private struct PlanTabOverviewPanel: View {
    let plan: TrainingPlan
    let currentWeekID: UUID?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            MainProgressBar(fraction: fraction, label: "Plan progress")
            HStack(alignment: .firstTextBaseline) {
                Text("Week \(currentNumber) of \(plan.weeks.count)")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                Spacer(minLength: 8)
                Text(trailingText)
                    .font(.system(size: 13))
                    .foregroundStyle(Color(hex: 0x5A5A5A))
            }
            PlanTabPhaseBar(weeks: plan.weeks, currentIndex: currentIndex)
                .padding(.top, 2)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 16)
        .mainGlass(radius: 20)
    }

    private var currentIndex: Int {
        plan.weeks.firstIndex { $0.id == currentWeekID } ?? 0
    }

    private var currentNumber: Int { currentIndex + 1 }

    private var fraction: Double {
        plan.weeks.isEmpty ? 0 : Double(currentNumber) / Double(plan.weeks.count)
    }

    private var trailingText: String {
        let today = Calendar.plan.startOfDay(for: Date())
        if let raceDate = plan.raceDate {
            let race = Calendar.plan.startOfDay(for: raceDate)
            let days = Calendar.plan.dateComponents([.day], from: today, to: race).day ?? 0
            if days < 0 { return "Race done" }
            if days == 0 { return "Race day" }
            if days < 14 { return "Race in \(days) \(days == 1 ? "day" : "days")" }
            return "Race in \(days / 7) weeks"
        }
        let left = plan.weeks.count - currentNumber
        if left <= 0 { return "Last week" }
        return "\(left) \(left == 1 ? "week" : "weeks") to go"
    }
}

/// One small segment per week, grouped and labelled by phase.
private struct PlanTabPhaseBar: View {
    let weeks: [PlanWeek]
    let currentIndex: Int

    private struct PhaseGroup: Identifiable {
        let id: Int
        let phase: PlanPhase
        var indices: [Int]
    }

    private var groups: [PhaseGroup] {
        var result: [PhaseGroup] = []
        for (index, week) in weeks.enumerated() {
            if let last = result.last, last.phase == week.phase {
                result[result.count - 1].indices.append(index)
            } else {
                result.append(PhaseGroup(id: index, phase: week.phase, indices: [index]))
            }
        }
        return result
    }

    var body: some View {
        GeometryReader { geo in
            let spacing: CGFloat = 4
            let count = max(1, weeks.count)
            let unit = max(2, (geo.size.width - spacing * CGFloat(count - 1)) / CGFloat(count))
            HStack(alignment: .top, spacing: spacing) {
                ForEach(groups) { group in
                    groupView(group, unit: unit, spacing: spacing)
                }
            }
        }
        .frame(height: 26)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Phases: \(groups.map { $0.phase.title }.joined(separator: ", "))")
    }

    private func groupView(_ group: PhaseGroup, unit: CGFloat, spacing: CGFloat) -> some View {
        let width = unit * CGFloat(group.indices.count) + spacing * CGFloat(group.indices.count - 1)
        let isCurrent = group.indices.contains(currentIndex)
        return VStack(alignment: .leading, spacing: 5) {
            HStack(spacing: spacing) {
                ForEach(group.indices, id: \.self) { index in
                    Capsule()
                        .fill(color(for: index))
                        .frame(width: unit, height: 5)
                }
            }
            Text(group.phase.title)
                .font(.system(size: 11, weight: isCurrent ? .semibold : .regular))
                .foregroundStyle(isCurrent ? Theme.ink : Theme.muted)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
        }
        .frame(width: width, alignment: .leading)
    }

    private func color(for index: Int) -> Color {
        if index < currentIndex { return Theme.green }
        if index == currentIndex { return Theme.orange }
        return Theme.ink.opacity(0.12)
    }
}

#Preview {
    PlanTabView()
        .environmentObject(AppStore.preview)
        .environmentObject(MainChrome())
}
