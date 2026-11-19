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

/// Layout of the winding path: one row per week, centred, then the finish under it.
enum OnboardingPathLayout {
    static let width: CGFloat = 340
    static let rowHeight: CGFloat = 92
    static let nodeWidth: CGFloat = 64
    static let finishSize: CGFloat = 92
    static let finishTopGap: CGFloat = 14
    static let finishRowHeight: CGFloat = 190
    /// Horizontal shift of each node from the centre line: the path swings left and right.
    private static let swings: [CGFloat] = [0, 46, 66, 46, 0, -46, -66, -46]

    static func swing(_ index: Int) -> CGFloat { swings[index % swings.count] }

    static func leading(_ index: Int) -> CGFloat { (width - nodeWidth) / 2 + swing(index) }

    static func nodeCenter(_ index: Int) -> CGPoint {
        CGPoint(x: width / 2 + swing(index), y: CGFloat(index) * rowHeight + rowHeight / 2)
    }

    static func finishCenter(count: Int) -> CGPoint {
        CGPoint(x: width / 2, y: CGFloat(count) * rowHeight + finishTopGap + finishSize / 2)
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
        .accessibilityLabel(unlocked ? L("Week %@", number) : L("Week %@, locked", number))
    }
}

/// Screen 26: a preview of the plan as a winding path. The weeks light up one after the other,
/// down to the goal; there is nothing to unlock here, one button moves on.
struct OnboardingPlanReadyScreen: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var nav: OnboardingNavigator
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Number of weeks lit so far.
    @State private var revealed = 0
    @State private var finished = false

    private let finishID = 1000

    private var weeks: [PlanWeek] {
        Array((store.plan?.weeks ?? []).prefix(PlanEngine.maxWeeks))
    }

    var body: some View {
        ScrollViewReader { proxy in
            VStack(spacing: 0) {
                ScrollView {
                    VStack(spacing: 0) {
                        header
                        path
                            .padding(.top, 26)
                            .padding(.bottom, 32)
                    }
                    .frame(maxWidth: .infinity)
                }
                .scrollIndicators(.hidden)
                BottomBar {
                    PrimaryButton(title: L("Continue")) { nav.next(store) }
                }
                .overlay(alignment: .top) {
                    Rectangle().fill(Theme.cardBorder).frame(height: 1)
                }
            }
            .task {
                await play(proxy)
            }
        }
        .background(Color.white.ignoresSafeArea())
        .onAppear {
            if store.plan == nil { store.generatePlan() }
        }
    }

    private var header: some View {
        VStack(spacing: 10) {
            Text(L("YOUR PLAN IS READY"))
                .font(.system(size: 12, weight: .bold))
                .kerning(0.8)
                .foregroundStyle(Theme.green)
            Text(L("Here's how we'll work"))
                .font(Theme.title(30))
                .foregroundStyle(Theme.ink)
                .accessibilityAddTraits(.isHeader)
            Text(L("Every week you follow the path, until your goal."))
                .font(.system(size: 17))
                .foregroundStyle(Theme.body)
                .fixedSize(horizontal: false, vertical: true)
            Text("\(OnboardingCopy.weeks(weeks.count)) · \(OnboardingCopy.sessions(OnboardingCopy.sessionsPerWeek(store.answers)))")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.green)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Theme.greenTint, in: Capsule())
                .padding(.top, 4)
        }
        .multilineTextAlignment(.center)
        .padding(.horizontal, Theme.screenPadding)
        .padding(.top, 20)
    }

    private var path: some View {
        VStack(spacing: 0) {
            ForEach(Array(weeks.enumerated()), id: \.offset) { index, week in
                weekRow(index, week)
                    .id(index)
            }
            finishRow
                .id(finishID)
        }
        .frame(width: OnboardingPathLayout.width)
        .background(alignment: .topLeading) {
            OnboardingPathLine(count: weeks.count)
                .stroke(Theme.locked, style: StrokeStyle(lineWidth: 10, lineCap: .round, lineJoin: .round))
        }
    }

    /// The node on the path, and a short label on the side that has more room.
    private func weekRow(_ index: Int, _ week: PlanWeek) -> some View {
        let lit = index < revealed
        let swing = OnboardingPathLayout.swing(index)
        // Nodes on the right half get their label on the left, and the other way round.
        let labelOnLeft = swing > 0
        let labelWidth: CGFloat = 112
        let gap: CGFloat = 14
        let nodeLeading = OnboardingPathLayout.leading(index)
        let labelX = labelOnLeft
            ? nodeLeading - gap - labelWidth
            : nodeLeading + OnboardingPathLayout.nodeWidth + gap
        return ZStack(alignment: .topLeading) {
            OnboardingWeekNode(number: index + 1, phase: week.phase, unlocked: lit, isCurrent: false)
                .scaleEffect(lit ? 1 : 0.86)
                .offset(x: nodeLeading, y: (OnboardingPathLayout.rowHeight - 64) / 2)
            VStack(alignment: labelOnLeft ? .trailing : .leading, spacing: 2) {
                Text(week.phase.title)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(OnboardingPhaseStyle.chipText(week.phase))
                Text(Fmt.km(week.plannedKm, decimals: 0))
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.muted)
            }
            .frame(width: labelWidth, height: OnboardingPathLayout.rowHeight,
                   alignment: labelOnLeft ? .trailing : .leading)
            .offset(x: labelX)
            .opacity(lit ? 1 : 0)
        }
        .frame(width: OnboardingPathLayout.width, height: OnboardingPathLayout.rowHeight, alignment: .topLeading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(L("Week %@, %@, %@", index + 1, week.phase.title, Fmt.km(week.plannedKm, decimals: 0)))
    }

    private var finishRow: some View {
        VStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(finished ? Theme.orangeDeep : Color(hex: 0xCFCFCB))
                    .offset(y: 7)
                Circle()
                    .fill(finished ? Theme.orange : Theme.locked)
                Image(systemName: "flag.checkered")
                    .font(.system(size: 32, weight: .semibold))
                    .foregroundStyle(finished ? Color.white : Theme.faint)
            }
            .frame(width: OnboardingPathLayout.finishSize, height: OnboardingPathLayout.finishSize)
            .scaleEffect(finished ? 1.06 : 0.92)
            .accessibilityHidden(true)
            VStack(spacing: 2) {
                Text(finishDetail)
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(Theme.ink)
                Text(finishTitle)
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.muted)
            }
            .multilineTextAlignment(.center)
            .fixedSize(horizontal: false, vertical: true)
            .opacity(finished ? 1 : 0)
        }
        .padding(.top, OnboardingPathLayout.finishTopGap)
        .frame(width: OnboardingPathLayout.width, height: OnboardingPathLayout.finishRowHeight, alignment: .top)
        .accessibilityElement(children: .combine)
    }

    private var finishTitle: String {
        if !store.answers.goal.isOpenEnded, let race = store.plan?.raceDate {
            return Fmt.day(race, "EEEE d MMMM")
        }
        return L("End of week %@", max(1, weeks.count))
    }

    private var finishDetail: String {
        let answers = store.answers
        let goal = answers.goal ?? .regular
        if goal.isOpenEnded { return L("Running is part of your week") }
        if let target = answers.targetSeconds {
            return L("Your %@ in %@", OnboardingCopy.goalNoun(goal), Fmt.goalTime(target))
        }
        return L("Ready for your %@", OnboardingCopy.goalNoun(goal))
    }

    /// Lights the weeks one by one, following them down the page, then the finish.
    @MainActor
    private func play(_ proxy: ScrollViewProxy) async {
        guard !finished else { return }
        let count = weeks.count
        if reduceMotion {
            revealed = count
            finished = true
            return
        }
        try? await Task.sleep(nanoseconds: 700_000_000)
        for index in 0..<count {
            if Task.isCancelled { return }
            Haptics.soft()
            withAnimation(.spring(response: 0.4, dampingFraction: 0.6)) {
                revealed = index + 1
            }
            // The first weeks are on screen already: only follow once they reach the lower half.
            if index >= 3 {
                withAnimation(.easeInOut(duration: 0.5)) {
                    proxy.scrollTo(index, anchor: .center)
                }
            }
            try? await Task.sleep(nanoseconds: 380_000_000)
        }
        if Task.isCancelled { return }
        Haptics.success()
        withAnimation(.spring(response: 0.5, dampingFraction: 0.55)) {
            finished = true
        }
        withAnimation(.easeInOut(duration: 0.5)) {
            proxy.scrollTo(finishID, anchor: .bottom)
        }
    }
}

#Preview {
    OnboardingPlanReadyScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}
