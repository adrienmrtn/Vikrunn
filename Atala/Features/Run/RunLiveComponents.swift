import SwiftUI

/// One segment per step of the session, coloured by kind. The current one is taller.
struct RunSegmentBar: View {
    let steps: [WorkoutStep]
    let currentIndex: Int
    let complete: Bool

    var body: some View {
        GeometryReader { geo in
            let spacing: CGFloat = steps.count > 24 ? 1 : 3
            let total: CGFloat = steps.reduce(CGFloat(0)) { $0 + RunFormat.weight($1) }
            let available: CGFloat = max(0, geo.size.width - spacing * CGFloat(max(0, steps.count - 1)))
            HStack(alignment: .center, spacing: spacing) {
                ForEach(Array(steps.enumerated()), id: \.offset) { index, step in
                    let isCurrent = !complete && index == currentIndex
                    let isDone = complete || index < currentIndex
                    Capsule()
                        .fill(isCurrent || isDone ? RunFormat.segmentColor(step.kind) : Color(hex: 0xD9D9D6))
                        .opacity(isDone ? 0.45 : 1)
                        .frame(width: total > 0 ? max(2, available * RunFormat.weight(step) / total) : 2,
                               height: isCurrent ? 10 : 6)
                }
            }
            .frame(width: geo.size.width, height: 10, alignment: .leading)
        }
        .frame(height: 10)
        .accessibilityHidden(true)
    }
}

/// Three small figures side by side (total time, distance, average pace).
struct RunStatsRow: View {
    let items: [(value: String, label: String)]

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.value)
                        .font(.system(size: 24, weight: .bold))
                        .monospacedDigit()
                        .foregroundStyle(Theme.ink)
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                    Text(item.label)
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.muted)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityElement(children: .combine)
            }
        }
    }
}

/// Current pace against the target range: green zone in the middle, black marker for the runner.
struct RunPaceGauge: View {
    /// Seconds per kilometre. 0 = unknown.
    let pace: Int
    let paceFast: Int
    let paceSlow: Int

    /// 0 = far too slow, 1 = far too fast. The target range spans 0.35 to 0.65.
    private var position: CGFloat {
        guard pace > 0, paceSlow > paceFast else { return 0.5 }
        let ratio = CGFloat(paceSlow - pace) / CGFloat(paceSlow - paceFast)
        return min(0.98, max(0.02, 0.35 + ratio * 0.30))
    }

    var body: some View {
        VStack(spacing: 6) {
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(Theme.cardBorder)
                        .frame(height: 10)
                    Capsule().fill(Theme.greenSoft)
                        .frame(width: geo.size.width * 0.30, height: 10)
                        .offset(x: geo.size.width * 0.35)
                    if pace > 0 {
                        Capsule().fill(Theme.ink)
                            .frame(width: 6, height: 20)
                            .offset(x: geo.size.width * position - 3)
                    }
                }
                .frame(width: geo.size.width, height: 20, alignment: .leading)
                .animation(.easeOut(duration: 0.6), value: pace)
            }
            .frame(height: 20)
            HStack {
                Text(L("Too slow"))
                Spacer()
                Text(L("%@ to %@", Fmt.pace(paceSlow), Fmt.pace(paceFast)))
                Spacer()
                Text(L("Too fast"))
            }
            .font(.system(size: 12))
            .foregroundStyle(Theme.muted)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(L("Pace %@, target %@ to %@", Fmt.pace(pace), Fmt.pace(paceSlow), Fmt.pace(paceFast)))
    }
}

/// The coach's last words: avatar and a white speech bubble.
struct RunCoachBubble: View {
    let coach: Coach
    let text: String
    var showsSpeaker: Bool = true

    var body: some View {
        HStack(alignment: .bottom, spacing: 8) {
            CoachAvatar(coach: coach, size: 36)
            HStack(spacing: 10) {
                if showsSpeaker {
                    Image(systemName: "speaker.wave.2.fill")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Theme.green)
                        .accessibilityHidden(true)
                }
                Text(text)
                    .font(.system(size: 15))
                    .foregroundStyle(Theme.ink)
                    .lineSpacing(2)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(Color.white, in: bubbleShape)
            .overlay(bubbleShape.stroke(Theme.cardBorder, lineWidth: 1))
            .shadow(color: .black.opacity(0.05), radius: 5, x: 0, y: 2)
            Spacer(minLength: 0)
        }
    }

    private var bubbleShape: UnevenRoundedRectangle {
        UnevenRoundedRectangle(topLeadingRadius: 20, bottomLeadingRadius: 6,
                               bottomTrailingRadius: 20, topTrailingRadius: 20, style: .continuous)
    }
}

/// Round white control (lock, skip).
struct RunRoundButton: View {
    let symbol: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(Theme.ink)
                .frame(width: 56, height: 56)
                .background(Color.white, in: Circle())
                .overlay(Circle().stroke(Theme.controlBorder, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

/// Covers the live screen so nothing is pressed by accident. A long press unlocks.
struct RunLockOverlay: View {
    let onUnlock: () -> Void

    @State private var pressing = false

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.black.opacity(0.35)
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture {}
            VStack(spacing: 12) {
                Image(systemName: "lock.fill")
                    .font(.system(size: 26, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                    .frame(width: 84, height: 84)
                    .background(Color.white, in: Circle())
                    .scaleEffect(pressing ? 1.12 : 1)
                    .animation(.easeOut(duration: 0.2), value: pressing)
                    .onLongPressGesture(minimumDuration: 0.8, maximumDistance: 60, perform: onUnlock) { isPressing in
                        pressing = isPressing
                    }
                    .accessibilityLabel(L("Unlock the screen"))
                    .accessibilityAddTraits(.isButton)
                    .accessibilityAction { onUnlock() }
                Text(L("Screen locked. Hold to unlock."))
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(.white)
            }
            .padding(.bottom, 30)
        }
    }
}
