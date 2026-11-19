import SwiftUI

extension View {
    /// The card of the Progress tab: white, hairline border, soft shadow.
    func progressTabCard(padding: CGFloat = 18) -> some View {
        self
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.white, in: RoundedRectangle(cornerRadius: Theme.cardRadius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Theme.cardRadius, style: .continuous)
                    .stroke(Theme.cardBorder, lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.04), radius: 8, x: 0, y: 3)
    }
}

/// A section of the tab: its title, then its card.
struct ProgressTabSection<Content: View>: View {
    let title: String
    var trailing: String? = nil
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            MainSectionTitle(title: title, trailing: trailing)
            content
        }
    }
}

/// A number in the bold rounded face with its unit smaller and muted, and a caption under it.
struct ProgressTabFigure: View {
    let value: String
    var unit: String? = nil
    let label: String
    var size: CGFloat = 20
    var valueColor: Color = Theme.ink
    var mutedColor: Color = Theme.muted

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(alignment: .firstTextBaseline, spacing: 3) {
                Text(value)
                    .font(.system(size: size, weight: .bold, design: .rounded))
                    .foregroundStyle(valueColor)
                if let unit {
                    Text(unit)
                        .font(.system(size: size * 0.6, weight: .semibold))
                        .foregroundStyle(mutedColor)
                }
            }
            .lineLimit(1)
            .minimumScaleFactor(0.7)
            Text(label)
                .font(.system(size: 12))
                .foregroundStyle(mutedColor)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

/// One friendly line with a small symbol, for a card that has nothing to show yet.
struct ProgressTabHint: View {
    let systemName: String
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: systemName)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Theme.green)
                .frame(width: 22)
                .accessibilityHidden(true)
            Text(text)
                .font(.system(size: 14))
                .foregroundStyle(Theme.muted)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
    }
}

/// A thin vertical hairline between two figures of a row.
struct ProgressTabDivider: View {
    var color: Color = Theme.hairline

    var body: some View {
        Rectangle()
            .fill(color)
            .frame(width: 1, height: 30)
            .accessibilityHidden(true)
    }
}
