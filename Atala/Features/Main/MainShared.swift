import SwiftUI

/// Shared state of the main shell: custom tab bar visibility and the profile sheet.
@MainActor
final class MainChrome: ObservableObject {
    /// Number of visible screens asking for the tab bar to be hidden (pushed details).
    @Published var hideCount: Int = 0
    @Published var showProfile: Bool = false
    /// Screens whose staged entrance already played in this shell (see `MainStagedEntrance`).
    /// Not published: recording it must not redraw anything.
    var stagedScreens: Set<MainStagedScreen> = []

    var tabBarHidden: Bool { hideCount > 0 }
}

/// Hides the custom tab bar while the modified screen is on top of a navigation stack.
struct MainHidesTabBar: ViewModifier {
    @EnvironmentObject private var chrome: MainChrome

    func body(content: Content) -> some View {
        content
            .onAppear { chrome.hideCount += 1 }
            .onDisappear { chrome.hideCount = max(0, chrome.hideCount - 1) }
    }
}

/// A run summary pushed on a navigation stack: inline title in the system bar, tab bar hidden.
struct MainRunSummaryHost: View {
    let run: RunRecord

    var body: some View {
        RunSummaryView(run: run)
            .navigationTitle("Run summary")
            .navigationBarTitleDisplayMode(.inline)
            .modifier(MainHidesTabBar())
    }
}

// MARK: - Glass panel

struct MainGlassPanel: ViewModifier {
    var radius: CGFloat = 22

    func body(content: Content) -> some View {
        content
            .background(
                RoundedRectangle(cornerRadius: radius, style: .continuous)
                    .fill(.ultraThinMaterial)
            )
            .background(
                RoundedRectangle(cornerRadius: radius, style: .continuous)
                    .fill(Color.white.opacity(0.82))
            )
            .overlay(
                RoundedRectangle(cornerRadius: radius, style: .continuous)
                    .stroke(Color.white.opacity(0.9), lineWidth: 1)
            )
            .shadow(color: Color(hex: 0x143228).opacity(0.10), radius: 10, x: 0, y: 6)
    }
}

extension View {
    /// Very light glass panel laid over an illustration.
    func mainGlass(radius: CGFloat = 22) -> some View {
        modifier(MainGlassPanel(radius: radius))
    }
}

// MARK: - Small shared views

/// Rounded illustration used at the top of the main screens.
struct MainHeroImage: View {
    let name: String
    var height: CGFloat = 170

    var body: some View {
        Color.clear
            .frame(maxWidth: .infinity)
            .frame(height: height)
            .overlay(
                Image(name)
                    .resizable()
                    .scaledToFill()
            )
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            .accessibilityHidden(true)
    }
}

/// One value with its caption, used in rows of three.
struct MainStat: View {
    let value: String
    let label: String
    var labelColor: Color = Theme.muted

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Theme.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Text(label)
                .font(.system(size: 12))
                .foregroundStyle(labelColor)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

/// Distance, duration and target pace of a session (or duration, exercises and effort).
struct MainWorkoutStats: View {
    let workout: Workout
    var labelColor: Color = Color(hex: 0x5A5A5A)

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            if workout.kind.isRun {
                MainStat(value: Fmt.km(workout.distanceKm), label: "Distance", labelColor: labelColor)
                MainStat(value: Fmt.minutes(workout.durationMin), label: "Duration", labelColor: labelColor)
                MainStat(value: paceText, label: "Target pace", labelColor: labelColor)
            } else {
                MainStat(value: Fmt.minutes(workout.durationMin), label: "Duration", labelColor: labelColor)
                MainStat(value: "\(exerciseCount)", label: "Exercises", labelColor: labelColor)
                MainStat(value: "\(workout.effort) of 10", label: "Effort", labelColor: labelColor)
            }
        }
    }

    private var paceText: String {
        if let pace = workout.targetPace { return "\(Fmt.pace(pace))/km" }
        return "Free"
    }

    private var exerciseCount: Int {
        workout.blocks.reduce(0) { $0 + $1.steps.count * max(1, $1.repeats) }
    }
}

struct MainStatusBadge: View {
    let status: WorkoutStatus

    var body: some View {
        switch status {
        case .done:
            badge("Done", systemName: "checkmark", tint: Theme.green, background: Theme.greenTint)
        case .missed:
            badge("Missed", systemName: "xmark", tint: Theme.orangeDeep, background: Theme.orangeTint)
        case .planned:
            EmptyView()
        }
    }

    private func badge(_ title: String, systemName: String, tint: Color, background: Color) -> some View {
        HStack(spacing: 4) {
            Image(systemName: systemName)
                .font(.system(size: 9, weight: .bold))
            Text(title)
                .font(.system(size: 12, weight: .semibold))
        }
        .foregroundStyle(tint)
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(background, in: Capsule())
        .accessibilityElement(children: .combine)
    }
}

/// Thin progress bar (6 pt) as in the mockups.
struct MainProgressBar: View {
    /// 0...1
    let fraction: Double
    var tint: Color = Theme.orange
    var label: String = "Progress"

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(Theme.ink.opacity(0.12))
                Capsule().fill(tint)
                    .frame(width: geo.size.width * clamped)
            }
        }
        .frame(height: 6)
        .accessibilityElement()
        .accessibilityLabel(label)
        .accessibilityValue("\(Int((clamped * 100).rounded())) percent")
    }

    private var clamped: Double { min(max(fraction, 0), 1) }
}

struct MainSectionTitle: View {
    let title: String
    var trailing: String? = nil

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(title)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.ink)
            Spacer(minLength: 8)
            if let trailing {
                Text(trailing)
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.muted)
            }
        }
    }
}

/// Empty state with an optional action (no plan yet, no runs yet).
struct MainEmptyState: View {
    let systemName: String
    let title: String
    let message: String
    var actionTitle: String? = nil
    var action: (() -> Void)? = nil

    var body: some View {
        VStack(spacing: 10) {
            IconTile(systemName: systemName, size: 48)
            Text(title)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Theme.ink)
            Text(message)
                .font(.system(size: 14))
                .foregroundStyle(Theme.muted)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            if let actionTitle, let action {
                Button(actionTitle, action: action)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Theme.green)
                    .frame(minHeight: 44)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 20)
        .padding(.horizontal, 16)
    }
}

// MARK: - Model helpers

extension Workout {
    /// Dot colour of the session kind (soft green easy, deep green long, orange hard, yellow strength).
    var mainDotColor: Color {
        switch kind {
        case .easy, .free: return Theme.greenSoft
        case .long: return Theme.green
        case .intervals, .tempo, .racePace, .race: return Theme.orange
        case .strength, .stretching: return Theme.yellow
        }
    }

    /// "9.6 km" for runs, "20 min" for strength and stretching.
    var mainAmountText: String {
        kind.isRun ? Fmt.km(distanceKm) : Fmt.minutes(durationMin)
    }
}

extension AppStore {
    /// Kilometres actually run during a plan week (all saved runs of that week).
    func mainDoneKm(in week: PlanWeek) -> Double {
        let start = Calendar.plan.startOfDay(for: week.startDate)
        guard let end = Calendar.plan.date(byAdding: .day, value: 7, to: start) else { return 0 }
        return runs
            .filter { $0.date >= start && $0.date < end }
            .reduce(0) { $0 + $1.distanceKm }
    }

    /// Kilometres run since Monday, whether or not a plan week covers these days.
    var mainKmThisWeek: Double {
        let start = Calendar.plan.mondayOfWeek(containing: Date())
        return runs
            .filter { $0.date >= start }
            .reduce(0) { $0 + $1.distanceKm }
    }

    /// 1-based position of this week in the plan. Nil before the plan starts and once it is over.
    var mainCurrentWeekNumber: Int? {
        guard let plan, let week = mainThisWeek,
              let index = plan.weeks.firstIndex(where: { $0.id == week.id }) else { return nil }
        return index + 1
    }
}
