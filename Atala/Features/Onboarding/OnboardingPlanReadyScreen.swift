import SwiftUI

/// Colours of a plan phase on the path.
enum OnboardingPhaseStyle {
    static func main(_ phase: PlanPhase) -> Color {
        switch phase {
        case .base: return Theme.greenSoft
        case .build: return Theme.green
        case .specific: return Theme.orange
        case .taper: return Theme.yellow
        }
    }

    static func dark(_ phase: PlanPhase) -> Color {
        switch phase {
        case .base: return Color(hex: 0x2C6A58)
        case .build: return Color(hex: 0x123B30)
        case .specific: return Theme.orangeDeep
        case .taper: return Color(hex: 0xA98632)
        }
    }

    static func chipText(_ phase: PlanPhase) -> Color {
        switch phase {
        case .base, .build: return Theme.green
        case .specific: return Theme.orangeDeep
        case .taper: return Theme.yellowDeep
        }
    }

    static func chipBackground(_ phase: PlanPhase) -> Color {
        switch phase {
        case .base, .build: return Theme.greenTint
        case .specific: return Theme.orangeTint
        case .taper: return Theme.yellowTint
        }
    }
}

/// Layout of the winding path: one row per week, then the finish.
enum OnboardingPathLayout {
    static let width: CGFloat = 350
    static let rowHeight: CGFloat = 96
    static let nodeWidth: CGFloat = 64
    static let finishSize: CGFloat = 88
    static let finishLeading: CGFloat = 34
    static let finishRowHeight: CGFloat = 120
    private static let offsets: [CGFloat] = [28, 64, 80, 64, 28, 12]

    static func leading(_ index: Int) -> CGFloat { offsets[index % offsets.count] }

    static func nodeCenter(_ index: Int) -> CGPoint {
        CGPoint(x: leading(index) + nodeWidth / 2, y: CGFloat(index) * rowHeight + rowHeight / 2)
    }

    static func finishCenter(count: Int) -> CGPoint {
        CGPoint(x: finishLeading + finishSize / 2, y: CGFloat(count) * rowHeight + finishRowHeight / 2)
    }
}

/// The line that links the week nodes down to the finish.
struct OnboardingPathLine: Shape {
    let count: Int

    func path(in rect: CGRect) -> Path {
        var path = Path()
        guard count > 0 else { return path }
        path.move(to: OnboardingPathLayout.nodeCenter(0))
        for index in 1...count {
            let previous = OnboardingPathLayout.nodeCenter(index - 1)
            let next = index < count
                ? OnboardingPathLayout.nodeCenter(index)
                : OnboardingPathLayout.finishCenter(count: count)
            let midY = (previous.y + next.y) / 2
            path.addCurve(to: next,
                          control1: CGPoint(x: previous.x, y: midY),
                          control2: CGPoint(x: next.x, y: midY))
        }
        return path
    }
}

/// Bubble shown next to the current week: phase, kilometres and the runs of the week.
struct OnboardingWeekBubble: View {
    let week: PlanWeek

    private var runs: [Workout] { week.workouts.filter { $0.kind.isRun } }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 4) {
                Text(week.phase.title)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(OnboardingPhaseStyle.chipText(week.phase))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(OnboardingPhaseStyle.chipBackground(week.phase), in: Capsule())
                HStack(alignment: .firstTextBaseline) {
                    Text("Week \(week.index + 1)")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(Theme.ink)
                    Spacer(minLength: 6)
                    Text(Fmt.km(week.plannedKm, decimals: 0))
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.muted)
                }
            }
            .padding(.bottom, 8)
            if runs.isEmpty {
                Text("No run left this week: rest and get ready.")
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.vertical, 7)
            }
            ForEach(runs) { run in
                Divider().overlay(Color(hex: 0xEFEFEF))
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(Weekday.short(Calendar.plan.mondayIndex(of: run.date)))
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.muted)
                        .frame(width: 28, alignment: .leading)
                    Text(run.title)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.ink)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Text(Fmt.km(run.distanceKm))
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.muted)
                        .lineLimit(1)
                        .fixedSize()
                }
                .padding(.vertical, 7)
            }
        }
        .padding(.horizontal, 14)
        .padding(.top, 12)
        .padding(.bottom, 6)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .stroke(Theme.cardBorder, lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.1), radius: 12, x: 0, y: 8)
        .accessibilityElement(children: .combine)
    }
}

/// One week on the path: coloured when unlocked, grey with a lock otherwise.
struct OnboardingWeekNode: View {
    let number: Int
    let phase: PlanPhase
    let unlocked: Bool
    let isCurrent: Bool

    var body: some View {
        ZStack {
            Capsule()
                .fill(unlocked ? OnboardingPhaseStyle.dark(phase) : Color(hex: 0xCFCFCB))
                .frame(width: OnboardingPathLayout.nodeWidth, height: 58)
                .offset(y: 6)
            Capsule()
                .fill(unlocked ? OnboardingPhaseStyle.main(phase) : Theme.locked)
                .frame(width: OnboardingPathLayout.nodeWidth, height: 58)
            if unlocked {
                Text("\(number)")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(Color.white)
            } else {
                Image(systemName: "lock.fill")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Theme.faint)
            }
        }
        .frame(width: OnboardingPathLayout.nodeWidth, height: 64)
        .scaleEffect(isCurrent ? 1.1 : 1)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(unlocked ? "Week \(number)" : "Week \(number), locked")
    }
}

/// Screen 26: the plan as a winding path, unlocked week by week.
struct OnboardingPlanReadyScreen: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var nav: OnboardingNavigator
    @State private var current = 0
    @State private var finished = false

    private let finishID = 1000

    private var weeks: [PlanWeek] {
        Array((store.plan?.weeks ?? []).prefix(PlanEngine.maxWeeks))
    }

    private var buttonTitle: String {
        if finished || weeks.isEmpty { return "Continue" }
        if current < weeks.count - 1 { return "Unlock week \(current + 2)" }
        return store.plan?.raceDate != nil && store.answers.goal != .regular ? "See race day" : "See the finish"
    }

    var body: some View {
        ScrollViewReader { proxy in
            VStack(spacing: 0) {
                header
                ScrollView {
                    path
                        .padding(.top, 24)
                        .padding(.bottom, 220)
                        .frame(maxWidth: .infinity)
                }
                .scrollIndicators(.hidden)
                bottom(proxy)
            }
        }
        .background(Color.white.ignoresSafeArea())
        .sensoryFeedback(.impact, trigger: current)
        .sensoryFeedback(.success, trigger: finished)
        .onAppear {
            if store.plan == nil { store.generatePlan() }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("Your plan is ready")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.green)
                Spacer()
                Button("Skip") { nav.next(store) }
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.muted)
                    .frame(minWidth: 44, minHeight: 44, alignment: .trailing)
            }
            .frame(height: 28)
            Text(OnboardingCopy.goalHeadline(store.answers))
                .font(Theme.title(28))
                .foregroundStyle(Theme.ink)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            Text("\(OnboardingCopy.weeks(weeks.count)) · \(OnboardingCopy.sessions(OnboardingCopy.sessionsPerWeek(store.answers)))")
                .font(.system(size: 15))
                .foregroundStyle(Theme.muted)
        }
        .padding(.horizontal, Theme.screenPadding)
        .padding(.top, 12)
        .padding(.bottom, 14)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.cardBorder).frame(height: 1)
        }
    }

    private var path: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(weeks.enumerated()), id: \.offset) { index, week in
                weekRow(index, week)
                    .id(index)
                    .zIndex(index == current ? 1 : 0)
            }
            finishRow
                .id(finishID)
        }
        .frame(width: OnboardingPathLayout.width, alignment: .leading)
        .background(alignment: .topLeading) {
            OnboardingPathLine(count: weeks.count)
                .stroke(Theme.locked, style: StrokeStyle(lineWidth: 10, lineCap: .round, lineJoin: .round))
        }
    }

    private func weekRow(_ index: Int, _ week: PlanWeek) -> some View {
        let isCurrent = index == current && !finished
        return HStack(spacing: 0) {
            OnboardingWeekNode(number: index + 1, phase: week.phase, unlocked: index <= current, isCurrent: isCurrent)
                .animation(.spring(response: 0.4, dampingFraction: 0.42), value: current)
                .padding(.leading, OnboardingPathLayout.leading(index))
            Spacer(minLength: 0)
        }
        .frame(width: OnboardingPathLayout.width, height: OnboardingPathLayout.rowHeight, alignment: .leading)
        .overlay(alignment: .topLeading) {
            if isCurrent {
                OnboardingWeekBubble(week: week)
                    .frame(width: 192)
                    .offset(x: 158, y: 2)
                    .transition(.scale(scale: 0.7, anchor: .topLeading).combined(with: .opacity))
            }
        }
    }

    private var finishRow: some View {
        HStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(finished ? Theme.orange : Color(hex: 0xCFCFCB))
                    .offset(y: 7)
                Circle()
                    .fill(finished ? Theme.ink : Theme.locked)
                Image(systemName: "flag.checkered")
                    .font(.system(size: 30, weight: .semibold))
                    .foregroundStyle(finished ? Color.white : Theme.faint)
            }
            .frame(width: OnboardingPathLayout.finishSize, height: OnboardingPathLayout.finishSize)
            .scaleEffect(finished ? 1.08 : 1)
            .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(finishTitle)
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(finished ? Theme.ink : Theme.faint)
                Text(finishDetail)
                    .font(.system(size: 14))
                    .foregroundStyle(finished ? Theme.muted : Theme.faint)
            }
            Spacer(minLength: 0)
        }
        .padding(.leading, OnboardingPathLayout.finishLeading)
        .frame(width: OnboardingPathLayout.width, height: OnboardingPathLayout.finishRowHeight, alignment: .leading)
    }

    private var finishTitle: String {
        if store.answers.goal != .regular, let race = store.plan?.raceDate {
            return Fmt.day(race, "EEEE d MMMM")
        }
        return "End of week \(max(1, weeks.count))"
    }

    private var finishDetail: String {
        let answers = store.answers
        let goal = answers.goal ?? .regular
        if goal == .regular { return "Running is part of your week" }
        if let target = answers.targetSeconds {
            return "Your \(OnboardingCopy.goalNoun(goal)) in \(Fmt.goalTime(target))"
        }
        return "Ready for your \(OnboardingCopy.goalNoun(goal))"
    }

    private func bottom(_ proxy: ScrollViewProxy) -> some View {
        BottomBar {
            PrimaryButton(title: buttonTitle) { advance(proxy) }
        }
        .overlay(alignment: .top) {
            Rectangle().fill(Theme.cardBorder).frame(height: 1)
        }
    }

    private func advance(_ proxy: ScrollViewProxy) {
        if finished || weeks.isEmpty {
            nav.next(store)
            return
        }
        if current >= weeks.count - 1 {
            withAnimation(.spring(response: 0.45, dampingFraction: 0.55)) { finished = true }
            withAnimation(.easeInOut(duration: 0.5)) { proxy.scrollTo(finishID, anchor: .center) }
            return
        }
        let next = current + 1
        withAnimation(.spring(response: 0.4, dampingFraction: 0.7)) { current = next }
        withAnimation(.easeInOut(duration: 0.5)) {
            proxy.scrollTo(next, anchor: UnitPoint(x: 0.5, y: 0.2))
        }
    }
}

#Preview {
    OnboardingPlanReadyScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}
