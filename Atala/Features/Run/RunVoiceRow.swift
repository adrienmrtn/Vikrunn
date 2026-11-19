import SwiftUI

/// The small "Atala Pro" tag with a padlock, shown where a control needs the subscription.
struct RunProTag: View {
    var body: some View {
        HStack(spacing: 5) {
            Image(systemName: "lock.fill")
                .font(.system(size: 11, weight: .bold))
            Text("Atala Pro")
                .font(.system(size: 13, weight: .bold))
                .lineLimit(1)
        }
        .foregroundStyle(Theme.green)
        .padding(.horizontal, 10)
        .frame(height: 28)
        .background(Theme.greenTint, in: Capsule())
        .fixedSize()
    }
}

/// The coach's voice switch, shared by the run and the guided sessions.
/// With Atala Pro it is a live toggle. Without it, the row shows a padlock and an "Atala Pro" tag
/// (never a green switch) and a tap calls `onLocked`, which opens the paywall.
struct CoachVoiceRow: View {
    let coachName: String
    let isPro: Bool
    @Binding var isOn: Bool
    /// Subtitle while the voice is on, and while it is off.
    let onText: String
    let offText: String
    let onLocked: () -> Void

    var body: some View {
        if isPro {
            unlocked
        } else {
            locked
        }
    }

    private var unlocked: some View {
        HStack(spacing: 14) {
            IconTile(systemName: "speaker.wave.2.fill", tint: Theme.orangeDeep,
                     background: Theme.orangeTint, size: 44)
            texts(subtitle: isOn ? onText : offText)
            Toggle(L("Coach voice"), isOn: $isOn)
                .labelsHidden()
                .tint(Theme.green)
        }
        .frame(minHeight: 60)
    }

    private var locked: some View {
        Button(action: {
            Haptics.tap()
            onLocked()
        }) {
            HStack(spacing: 14) {
                IconTile(systemName: "speaker.wave.2.fill", tint: Theme.muted,
                         background: Theme.locked, size: 44)
                texts(subtitle: L("Included in Atala Pro"))
                RunProTag()
            }
            .frame(minHeight: 60)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(L("%@'s voice, included in Atala Pro", coachName))
        .accessibilityHint(L("Shows what Atala Pro includes"))
        .accessibilityAddTraits(.isButton)
    }

    private func texts(subtitle: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(L("%@'s voice", coachName))
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Theme.ink)
            Text(subtitle)
                .font(.system(size: 13))
                .foregroundStyle(Theme.muted)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// The large call-to-action of the run screens (start, resume): taller than the standard button.
struct RunBigButtonStyle: ButtonStyle {
    var background: Color = Theme.ink
    /// Played when the button is pressed. Pass nil when the caller plays its own haptic.
    var haptic: Haptics.Kind? = .confirm

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 19, weight: .bold))
            .foregroundStyle(Color.white)
            .frame(maxWidth: .infinity)
            .frame(height: 64)
            .background(background, in: Capsule())
            .opacity(configuration.isPressed ? 0.85 : 1)
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .animation(.easeOut(duration: 0.15), value: configuration.isPressed)
            .modifier(HapticOnPress(isPressed: configuration.isPressed, kind: haptic))
    }
}
