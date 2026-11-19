import SwiftUI

/// "Session complete" right after the run: the shared celebration, the figures of the run and
/// "How was it?". The answer is optional.
struct RunFeelingScreen: View {
    let distanceM: Double
    let durationSec: Int
    let coach: Coach
    let onDone: (Feeling?) -> Void

    @State private var feeling: Feeling?

    /// Seconds per kilometre, 0 when no distance was measured.
    private var averagePace: Int {
        distanceM > 0 ? Int(Double(durationSec) / (distanceM / 1000)) : 0
    }

    private var stats: [SessionCompleteStat] {
        [
            SessionCompleteStat(value: distanceM, label: L("Distance"),
                                format: { RunFormat.distance($0) }),
            SessionCompleteStat(value: Double(durationSec), label: L("Time"),
                                format: { Fmt.duration(Int($0.rounded())) }),
            SessionCompleteStat(value: Double(averagePace), label: L("Average pace"),
                                format: { "\(Fmt.pace(Int($0.rounded())))/km" }),
        ]
    }

    private var coachLine: String {
        feeling == .pain
            ? L("Thank you for telling me. Take it easy for the rest of the day.")
            : L("Well done, that one is in the bank. How was it?")
    }

    var body: some View {
        SessionCompleteScreen(dataLine: L("%@ in %@", RunFormat.distance(distanceM), Fmt.duration(durationSec)),
                              stats: stats,
                              coach: coach,
                              coachLine: coachLine,
                              buttonTitle: L("See my summary"),
                              onPrimary: { onDone(feeling) }) {
            VStack(spacing: 9) {
                ForEach(Feeling.allCases) { option in
                    RunFeelingRow(feeling: option,
                                  subtitle: subtitle(for: option),
                                  selected: feeling == option) {
                        feeling = feeling == option ? nil : option
                    }
                }
                Text(L("Your answer helps tune your next sessions."))
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.muted)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 6)
            }
        }
    }

    private func subtitle(for option: Feeling) -> String {
        option == .pain ? L("I'd rather tell %@", coach.name) : option.subtitle
    }
}

/// Symbols and tints of the four answers (also used by the summary and the manual entry).
enum RunFeelingStyle {
    static func symbol(_ feeling: Feeling) -> String {
        switch feeling {
        case .easy: return "face.smiling"
        case .good: return "hand.thumbsup.fill"
        case .hard: return "flame.fill"
        case .pain: return "bandage.fill"
        }
    }

    static func tint(_ feeling: Feeling) -> Color {
        switch feeling {
        case .easy: return Theme.green
        case .good: return Theme.yellowDeep
        case .hard: return Theme.orangeDeep
        case .pain: return Theme.muted
        }
    }

    static func background(_ feeling: Feeling) -> Color {
        switch feeling {
        case .easy: return Theme.greenTint
        case .good: return Theme.yellowTint
        case .hard: return Theme.orangeTint
        case .pain: return Color(hex: 0xF1F1EF)
        }
    }
}

private struct RunFeelingRow: View {
    let feeling: Feeling
    let subtitle: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: {
            Haptics.select()
            action()
        }) {
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
                    Text(feeling.title)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Theme.ink)
                    Text(subtitle)
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.muted)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                SelectionMark(selected: selected)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .frame(minHeight: 68)
            .card(selected: selected)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

#Preview {
    RunFeelingScreen(distanceM: 9620, durationSec: 3288, coach: Coach.all[0], onDone: { _ in })
}
