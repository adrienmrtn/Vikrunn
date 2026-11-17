import SwiftUI

/// The projection curve, from today's level (bottom left) to the goal (top right), drawn up to
/// `progress` (0...1 of the width). `closed` adds the area under the curve, for the gradient fill.
/// Line and fill share the same samples, so the fill always stops exactly under the tip of the line.
struct OnboardingProjectionCurve: Shape {
    var progress: CGFloat
    var closed: Bool = false

    /// Share of the height where the curve starts and where it ends (0 is the top).
    static let startLevel: CGFloat = 0.84
    static let goalLevel: CGFloat = 0.12
    private static let segments = 60

    var animatableData: CGFloat {
        get { progress }
        set { progress = newValue }
    }

    /// Point of the curve at `t` (0...1 across the plan): a gentle start, the big gains in the
    /// middle of the plan, then it levels off before the goal.
    static func point(at t: CGFloat, in rect: CGRect) -> CGPoint {
        let clamped = min(max(t, 0), 1)
        let eased = clamped * clamped * (3 - 2 * clamped)
        let level = 0.3 * clamped + 0.7 * eased
        let top = rect.minY + rect.height * goalLevel
        let bottom = rect.minY + rect.height * startLevel
        return CGPoint(x: rect.minX + rect.width * clamped, y: bottom - (bottom - top) * level)
    }

    func path(in rect: CGRect) -> Path {
        var path = Path()
        let end = min(max(progress, 0), 1)
        guard end > 0 else { return path }
        path.move(to: Self.point(at: 0, in: rect))
        for index in 1...Self.segments {
            let t = CGFloat(index) / CGFloat(Self.segments)
            if t >= end {
                path.addLine(to: Self.point(at: end, in: rect))
                break
            }
            path.addLine(to: Self.point(at: t, in: rect))
        }
        if closed {
            path.addLine(to: CGPoint(x: rect.minX + rect.width * end, y: rect.maxY))
            path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
            path.closeSubpath()
        }
        return path
    }
}

/// Dashed horizontal line at the level of the goal.
struct OnboardingProjectionGoalLine: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let y = rect.minY + rect.height * OnboardingProjectionCurve.goalLevel
        path.move(to: CGPoint(x: rect.minX, y: y))
        path.addLine(to: CGPoint(x: rect.maxX, y: y))
        return path
    }
}

/// Card with the curve that rises from today's level to the goal over the weeks of the plan.
/// When `drawn` becomes true the curve draws itself, the gradient rises under it, and the goal dot
/// pops in at the end. With Reduce Motion the final chart shows at once.
struct OnboardingProjectionChart: View {
    let weekCount: Int
    /// "Today" or "First week".
    let startCaption: String
    let startValue: String
    /// "Goal" or "Biggest week".
    let goalCaption: String
    let goalValue: String
    let drawn: Bool
    var drawDuration: Double = 1.1

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private static let plotHeight: CGFloat = 132
    private static let inset: CGFloat = 9

    private var drawAnimation: Animation? {
        reduceMotion ? nil : .easeInOut(duration: drawDuration)
    }

    private var popAnimation: Animation? {
        reduceMotion ? nil : .spring(response: 0.42, dampingFraction: 0.55).delay(drawDuration - 0.08)
    }

    private var weekLabels: [String] {
        let count = max(1, weekCount)
        if count >= 4 {
            return ["Today", "Week \(count / 2)", "Week \(count)"]
        }
        return ["Today", "Week \(count)"]
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            legend
            plot
            axis
        }
        .padding(.horizontal, 16)
        .padding(.top, 16)
        .padding(.bottom, 14)
        .card(radius: 24)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Projection curve: from \(startValue) to \(goalValue) over \(OnboardingCopy.weeks(weekCount))")
    }

    private var legend: some View {
        HStack(spacing: 14) {
            legendItem(color: Theme.green, caption: startCaption, value: startValue)
            Spacer(minLength: 0)
            legendItem(color: Theme.orange, caption: goalCaption, value: goalValue)
        }
    }

    private func legendItem(color: Color, caption: String, value: String) -> some View {
        HStack(spacing: 6) {
            Circle()
                .fill(color)
                .frame(width: 8, height: 8)
            Text(caption)
                .font(.system(size: 12))
                .foregroundStyle(Theme.muted)
            Text(value)
                .font(.system(size: 12, weight: .semibold))
                .monospacedDigit()
                .foregroundStyle(Theme.ink)
        }
        .lineLimit(1)
    }

    private var plot: some View {
        let progress: CGFloat = drawn ? 1 : 0
        return GeometryReader { geo in
            let rect = CGRect(x: Self.inset, y: 0,
                              width: max(1, geo.size.width - Self.inset * 2),
                              height: geo.size.height)
            let start = OnboardingProjectionCurve.point(at: 0, in: rect)
            let goal = OnboardingProjectionCurve.point(at: 1, in: rect)
            ZStack {
                Rectangle()
                    .fill(Theme.hairline)
                    .frame(height: 1)
                    .position(x: geo.size.width / 2, y: geo.size.height - 0.5)
                OnboardingProjectionGoalLine()
                    .stroke(Theme.orange.opacity(0.55), style: StrokeStyle(lineWidth: 1.5, lineCap: .round, dash: [2, 6]))
                OnboardingProjectionCurve(progress: progress, closed: true)
                    .fill(LinearGradient(colors: [Theme.green.opacity(0.26), Theme.green.opacity(0.02)],
                                         startPoint: .top, endPoint: .bottom))
                    .padding(.horizontal, Self.inset)
                    .animation(drawAnimation, value: drawn)
                OnboardingProjectionCurve(progress: progress)
                    .stroke(Theme.green, style: StrokeStyle(lineWidth: 3.5, lineCap: .round, lineJoin: .round))
                    .padding(.horizontal, Self.inset)
                    .animation(drawAnimation, value: drawn)
                Circle()
                    .fill(Color.white)
                    .frame(width: 12, height: 12)
                    .overlay(Circle().stroke(Theme.green, lineWidth: 3))
                    .position(start)
                Circle()
                    .fill(Theme.orange)
                    .frame(width: 16, height: 16)
                    .overlay(Circle().stroke(Color.white, lineWidth: 3))
                    .shadow(color: Theme.orange.opacity(0.35), radius: 5, x: 0, y: 2)
                    .scaleEffect(drawn ? 1 : 0.1)
                    .opacity(drawn ? 1 : 0)
                    .animation(popAnimation, value: drawn)
                    .position(goal)
            }
        }
        .frame(height: Self.plotHeight)
    }

    private var axis: some View {
        let labels = weekLabels
        return HStack(spacing: 0) {
            ForEach(labels.indices, id: \.self) { index in
                if index > 0 {
                    Spacer(minLength: 4)
                }
                Text(labels[index])
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(Theme.faint)
                    .lineLimit(1)
            }
        }
    }
}

#Preview {
    OnboardingProjectionChart(weekCount: 14, startCaption: "Today", startValue: "1:50:00",
                              goalCaption: "Goal", goalValue: "1:45:00", drawn: true)
        .padding(20)
}
