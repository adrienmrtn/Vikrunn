import SwiftUI

/// The "Session complete" moment shared by the end of a run and the end of a guided session.
/// The photo hero fills the top; the check mark draws itself, the title and the figures follow,
/// then a white sheet slides up with the stats, the coach, the caller's extra content (the feeling
/// choices) and, last, the primary button. With Reduce Motion everything is there at once.
struct SessionCompleteScreen<Extras: View>: View {
    private let eyebrow: String?
    private let dataLine: String
    private let stats: [SessionCompleteStat]
    private let coach: Coach
    private let coachLine: String
    private let buttonTitle: String
    private let onPrimary: () -> Void
    private let extras: Extras

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var stage = 0
    /// When the confetti started; nil when there is none on screen.
    @State private var burst: Date?

    /// Height of the photo hidden under the top of the sheet.
    private static var overlap: CGFloat { 30 }

    /// `stats`: three figures at most. `eyebrow`: an optional second line under the figures of the hero.
    init(eyebrow: String? = nil,
         dataLine: String,
         stats: [SessionCompleteStat],
         coach: Coach,
         coachLine: String,
         buttonTitle: String,
         onPrimary: @escaping () -> Void,
         @ViewBuilder extras: () -> Extras) {
        self.eyebrow = eyebrow
        self.dataLine = dataLine
        self.stats = stats
        self.coach = coach
        self.coachLine = coachLine
        self.buttonTitle = buttonTitle
        self.onPrimary = onPrimary
        self.extras = extras()
    }

    var body: some View {
        GeometryReader { geo in
            layout(size: geo.size, topInset: geo.safeAreaInsets.top)
        }
        .ignoresSafeArea(edges: .top)
        .background(Theme.forest.ignoresSafeArea())
        .task {
            await play()
        }
    }

    // MARK: Layout

    private func layout(size: CGSize, topInset: CGFloat) -> some View {
        let heroHeight: CGFloat = max(260, size.height * 0.45)
        let overlap: CGFloat = Self.overlap
        // Small phones: the avatar instead of the full-body coach.
        let compact: Bool = size.height < 700
        let checkY: CGFloat = SessionCompleteHero.checkCentreY(height: heroHeight, topInset: topInset, overlap: overlap)
        return ZStack(alignment: .top) {
            SessionCompleteHero(height: heroHeight, topInset: topInset, overlap: overlap,
                                eyebrow: eyebrow, dataLine: dataLine, stage: stage)
            if let burst {
                SessionCompleteConfetti(start: burst, originY: checkY)
                    .frame(maxWidth: .infinity)
                    .frame(height: heroHeight)
            }
            ScrollView {
                VStack(spacing: 0) {
                    // The hero stays visible through this empty room.
                    Color.clear
                        .frame(height: heroHeight - overlap)
                        .accessibilityHidden(true)
                    sheet(compact: compact, hiddenOffset: size.height)
                }
            }
            .scrollIndicators(.hidden)
            .safeAreaInset(edge: .bottom, spacing: 0) {
                bottomBar
            }
        }
    }

    /// White sheet with large rounded top corners. Its background runs far below the content, so
    /// the dark backdrop never shows under it.
    private func sheet(compact: Bool, hiddenOffset: CGFloat) -> some View {
        let shown = stage >= SessionCompleteStage.sheet
        return VStack(alignment: .leading, spacing: 20) {
            statsCard
            coachRow(compact: compact)
                .modifier(OnboardingStagedModifier(visible: stage >= SessionCompleteStage.coach))
            extras
                .modifier(OnboardingStagedModifier(visible: stage >= SessionCompleteStage.extras))
        }
        .padding(.horizontal, Theme.screenPadding)
        .padding(.top, 26)
        .padding(.bottom, 20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(alignment: .top) {
            UnevenRoundedRectangle(topLeadingRadius: 32, topTrailingRadius: 32, style: .continuous)
                .fill(Color.white)
                .frame(height: 3000)
        }
        .offset(y: shown ? 0 : hiddenOffset)
        .animation(reduceMotion ? nil : .spring(response: 0.55, dampingFraction: 0.86), value: shown)
    }

    private var statsCard: some View {
        HStack(alignment: .top, spacing: 8) {
            ForEach(Array(stats.enumerated()), id: \.offset) { index, stat in
                SessionCompleteStatView(stat: stat,
                                        shown: stage >= SessionCompleteStage.firstStat + min(index, 2))
            }
        }
        .padding(18)
        .card(radius: 24)
    }

    private func coachRow(compact: Bool) -> some View {
        HStack(alignment: .bottom, spacing: 10) {
            if compact {
                CoachAvatar(coach: coach, size: 40)
            } else {
                CoachPoseView(coach: coach, pose: .cheer)
                    .frame(width: 74, height: 118)
                    .accessibilityHidden(true)
            }
            VStack(alignment: .leading, spacing: 3) {
                Text(coach.name)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.green)
                Text(coachLine)
                    .font(.system(size: 15))
                    .foregroundStyle(Theme.ink)
                    .lineSpacing(3)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(Theme.bubble, in: UnevenRoundedRectangle(topLeadingRadius: 20, bottomLeadingRadius: 6,
                                                                 bottomTrailingRadius: 20, topTrailingRadius: 20,
                                                                 style: .continuous))
            Spacer(minLength: 0)
        }
    }

    /// Hidden with the sheet, and the button only becomes tappable at the last stage.
    private var bottomBar: some View {
        let ready = stage >= SessionCompleteStage.button
        return BottomBar {
            PrimaryButton(title: buttonTitle, action: onPrimary)
                .opacity(ready ? 1 : 0)
                .allowsHitTesting(ready)
                .accessibilityHidden(!ready)
        }
        .opacity(stage >= SessionCompleteStage.sheet ? 1 : 0)
    }

    // MARK: Sequence

    /// Waits, then moves to `target`. False when the screen has gone in the meantime.
    @MainActor
    private func step(_ target: Int, after seconds: Double,
                      animation: Animation = .easeOut(duration: 0.4)) async -> Bool {
        try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
        if Task.isCancelled { return false }
        withAnimation(animation) {
            stage = target
        }
        return true
    }

    /// The whole sequence, about 2.4 s from the first frame to the button. Cancelled with the view.
    @MainActor
    private func play() async {
        if stage != 0 {
            // Interrupted earlier (the screen went away and came back): never leave it half shown.
            stage = SessionCompleteStage.button
            burst = nil
            return
        }
        if reduceMotion {
            stage = SessionCompleteStage.button
            Haptics.success()
            return
        }
        Haptics.prepare(.success)
        guard await step(SessionCompleteStage.check, after: 0.25,
                         animation: .spring(response: 0.45, dampingFraction: 0.6)) else { return }
        // The check mark lands: the one success haptic of the moment, and the confetti.
        try? await Task.sleep(nanoseconds: 350_000_000)
        if Task.isCancelled { return }
        Haptics.success()
        burst = Date()
        guard await step(SessionCompleteStage.title, after: 0.05) else { return }
        guard await step(SessionCompleteStage.dataLine, after: 0.3) else { return }
        guard await step(SessionCompleteStage.sheet, after: 0.25) else { return }
        guard await step(SessionCompleteStage.firstStat, after: 0.3) else { return }
        guard await step(SessionCompleteStage.firstStat + 1, after: 0.12) else { return }
        guard await step(SessionCompleteStage.firstStat + 2, after: 0.12) else { return }
        guard await step(SessionCompleteStage.coach, after: 0.2) else { return }
        guard await step(SessionCompleteStage.extras, after: 0.2) else { return }
        guard await step(SessionCompleteStage.button, after: 0.25) else { return }
        // The confetti has finished by now: stop its timeline.
        try? await Task.sleep(nanoseconds: 400_000_000)
        if Task.isCancelled { return }
        burst = nil
    }
}

#Preview {
    SessionCompleteScreen(dataLine: "9.62 km in 54:48",
                          stats: [
                              SessionCompleteStat(value: 9620, label: "Distance", format: { RunFormat.distance($0) }),
                              SessionCompleteStat(value: 3288, label: "Time", format: { Fmt.duration(Int($0.rounded())) }),
                              SessionCompleteStat(value: 342, label: "Average pace", format: { "\(Fmt.pace(Int($0.rounded())))/km" }),
                          ],
                          coach: Coach.all[0],
                          coachLine: "Well done. How was it?",
                          buttonTitle: "See my summary",
                          onPrimary: {}) {
        Text("Extra content")
    }
}
