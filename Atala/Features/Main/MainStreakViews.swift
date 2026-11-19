import SwiftUI

/// Small flame chip: "3-week streak". Greyed while this week has nothing done yet.
struct MainStreakChip: View {
    let streak: Streak

    var body: some View {
        HStack(spacing: 5) {
            Image(systemName: "flame.fill")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(streak.activeThisWeek ? Theme.orange : Theme.faint)
            Text(L("%@-week streak", streak.current))
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(streak.activeThisWeek ? Theme.orangeDeep : Theme.muted)
                .lineLimit(1)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(streak.activeThisWeek ? Theme.orangeTint : Theme.surface, in: Capsule())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(L("%@ streak", StreakEngine.weeks(streak.current)))
    }
}

/// Streak card of the Progress tab: current streak, best streak and what to do next.
struct MainStreakCard: View {
    let streak: Streak

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 14) {
                Circle()
                    .fill(streak.current > 0 ? Theme.orangeTint : Theme.surface)
                    .frame(width: 56, height: 56)
                    .overlay(
                        Image(systemName: "flame.fill")
                            .font(.system(size: 26, weight: .semibold))
                            .foregroundStyle(streak.current > 0 ? Theme.orange : Theme.faint)
                    )
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(streak.current > 0 ? L("%@ in a row", StreakEngine.weeks(streak.current)) : L("No streak yet"))
                        .font(.system(size: 22, weight: .bold))
                        .foregroundStyle(Theme.ink)
                    Text(streak.best > 0 ? L("Your best: %@", StreakEngine.weeks(streak.best)) : L("Every streak starts with one session"))
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.muted)
                }
                Spacer(minLength: 0)
            }
            Text(StreakEngine.nudge(for: streak))
                .font(.system(size: 14))
                .foregroundStyle(Theme.body)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 22)
        .accessibilityElement(children: .combine)
    }
}

#Preview {
    VStack(spacing: 16) {
        MainStreakChip(streak: Streak(current: 3, best: 5, activeThisWeek: true))
        MainStreakCard(streak: Streak(current: 3, best: 5, activeThisWeek: false))
        MainStreakCard(streak: .none)
    }
    .padding()
}
