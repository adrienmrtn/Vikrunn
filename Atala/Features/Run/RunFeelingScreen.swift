import SwiftUI

/// "How was it?" right after the run. The answer is optional.
struct RunFeelingScreen: View {
    let distanceM: Double
    let durationSec: Int
    let coachName: String
    let onDone: (Feeling?) -> Void

    @State private var feeling: Feeling?

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                header
                VStack(spacing: 9) {
                    ForEach(Feeling.allCases) { option in
                        RunFeelingRow(feeling: option,
                                      subtitle: subtitle(for: option),
                                      selected: feeling == option) {
                            feeling = feeling == option ? nil : option
                        }
                    }
                }
                Text("Your answer helps tune your next sessions.")
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.muted)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal, Theme.screenPadding)
            .padding(.top, 20)
            .padding(.bottom, 16)
        }
        .scrollIndicators(.hidden)
        .background(Color.white.ignoresSafeArea())
        .safeAreaInset(edge: .bottom, spacing: 0) {
            BottomBar {
                PrimaryButton(title: "See my summary") { onDone(feeling) }
            }
        }
    }

    private var header: some View {
        VStack(spacing: 12) {
            Circle()
                .fill(Theme.greenTint)
                .frame(width: 64, height: 64)
                .overlay(
                    Image(systemName: "checkmark")
                        .font(.system(size: 26, weight: .bold))
                        .foregroundStyle(Theme.green)
                )
                .accessibilityHidden(true)
            Text("Session complete")
                .font(Theme.title(28))
                .foregroundStyle(Theme.ink)
            Text("\(RunFormat.distance(distanceM)) in \(Fmt.duration(durationSec)). How was it?")
                .font(.system(size: 15))
                .foregroundStyle(Theme.muted)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
    }

    private func subtitle(for option: Feeling) -> String {
        option == .pain ? "I'd rather tell \(coachName)" : option.subtitle
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
        Button(action: action) {
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
    RunFeelingScreen(distanceM: 9620, durationSec: 3288, coachName: "Awa", onDone: { _ in })
}
