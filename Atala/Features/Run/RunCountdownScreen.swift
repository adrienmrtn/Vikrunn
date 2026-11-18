import SwiftUI

/// Green full-screen 3-2-1. Tapping anywhere starts right away.
struct RunCountdownScreen: View {
    let coach: Coach
    let heading: String
    let line: String
    let onDone: () -> Void

    @State private var count = 3
    @State private var pulsing = false
    @State private var finished = false

    var body: some View {
        Button(action: finish) {
            ZStack {
                Theme.green.ignoresSafeArea()
                VStack(spacing: 0) {
                    Text(heading)
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(Theme.greenOnDark)
                        .padding(.top, 90)
                    Spacer(minLength: 20)
                    dial
                    Spacer(minLength: 20)
                    coachLine
                    Text("Tap the screen to start right away")
                        .font(.system(size: 14))
                        .foregroundStyle(Theme.greenOnDark)
                        .padding(.top, 44)
                        .padding(.bottom, 20)
                }
                .padding(.horizontal, Theme.screenPadding)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Start right away")
        .onAppear {
            withAnimation(.easeOut(duration: 1).repeatForever(autoreverses: false)) {
                pulsing = true
            }
        }
        .task {
            await runCountdown()
        }
    }

    private var dial: some View {
        ZStack {
            Circle()
                .stroke(Color.white, lineWidth: 2)
                .scaleEffect(pulsing ? 1.9 : 1)
                .opacity(pulsing ? 0 : 0.5)
            Circle()
                .fill(Color.white.opacity(0.12))
            Text("\(count)")
                .font(.system(size: 150, weight: .bold))
                .monospacedDigit()
                .foregroundStyle(.white)
                .contentTransition(.numericText())
        }
        .frame(width: 240, height: 240)
        .accessibilityHidden(true)
    }

    private var coachLine: some View {
        VStack(spacing: 12) {
            CoachAvatar(coach: coach, size: 52)
                .overlay(Circle().stroke(Color.white, lineWidth: 2))
            Text(line)
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func runCountdown() async {
        for value in [3, 2, 1] {
            withAnimation(.easeOut(duration: 0.2)) { count = value }
            do {
                try await Task.sleep(nanoseconds: 1_000_000_000)
            } catch {
                return
            }
        }
        finish()
    }

    private func finish() {
        guard !finished else { return }
        finished = true
        onDone()
    }
}

#Preview {
    RunCountdownScreen(coach: Coach.all[0], heading: "Warm-up in",
                       line: "Start gently. You should be able to talk.", onDone: {})
}
