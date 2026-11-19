import SwiftUI

/// Words shown and spoken during a guided session.
enum GuidedWording {
    static func position(_ exercise: GuidedPrescription, index: Int, count: Int) -> String {
        L("%@ · %@ of %@", exercise.info.kind.title, index + 1, count)
    }

    /// Short status on the right of the header.
    static func status(_ segment: GuidedSegment, _ exercise: GuidedPrescription) -> String {
        switch segment.kind {
        case .getReady: return L("Get ready")
        case .switchSide: return L("Switch sides")
        case .rest: return L("Rest")
        case .work:
            if exercise.sets > 1 { return L("Set %@ of %@", segment.setNumber, exercise.sets) }
            return exercise.isTimed ? L("Hold") : L("At your pace")
        }
    }

    /// Small line above the exercise name.
    static func eyebrow(_ segment: GuidedSegment, _ exercise: GuidedPrescription) -> String {
        switch segment.kind {
        case .getReady: return segment.exerciseIndex == 0 ? L("First up") : L("Next")
        case .switchSide: return L("Other side")
        case .rest: return L("Next: set %@ of %@", segment.setNumber + 1, exercise.sets)
        case .work: return exercise.info.muscles
        }
    }

    /// Lines that rotate under the exercise name.
    static func lines(_ segment: GuidedSegment, _ exercise: GuidedPrescription) -> [String] {
        switch segment.kind {
        case .getReady: return [exercise.info.summary]
        case .switchSide: return [L("Same thing on the other side. Take your time.")]
        case .rest: return [L("Shake it out and breathe."), L("Stop if anything hurts.")]
        case .work:
            let lines = exercise.isTimed ? exercise.info.cues + exercise.info.steps : exercise.info.cues
            return lines.isEmpty ? [exercise.info.summary] : lines
        }
    }

    /// The recorded clips for the start of a segment. A number or an exercise that was never
    /// recorded is left out: the coach then says the short version ("Next.", "Go.").
    static func clips(_ segment: GuidedSegment, _ exercise: GuidedPrescription) -> [VoiceClip] {
        switch segment.kind {
        case .getReady:
            var clips: [VoiceClip] = [segment.exerciseIndex == 0 ? .first : .next]
            guard VoiceClip.exerciseIDs.contains(exercise.info.id) else { return clips }
            clips.append(.exercise(exercise.info.id))
            if let side = segment.side { clips.append(side == .left ? .left : .right) }
            return clips
        case .switchSide:
            guard let side = segment.side else { return [.switchSides] }
            return [.switchSides, side == .left ? .left : .right]
        case .rest:
            return [.rest]
        case .work:
            let numbered = exercise.sets > 1 && segment.setNumber > 1 && VoiceClip.sets.contains(segment.setNumber)
            var clips: [VoiceClip] = [numbered ? .set(segment.setNumber) : .go]
            if let seconds = segment.durationSec {
                if VoiceClip.holds.contains(seconds) { clips.append(.hold(seconds)) }
            } else if let reps = segment.reps, VoiceClip.repCounts.contains(reps) {
                clips.append(.reps(reps))
            }
            return clips
        }
    }

    /// What the coach says when a segment starts.
    static func announcement(_ segment: GuidedSegment, _ exercise: GuidedPrescription) -> String {
        switch segment.kind {
        case .getReady:
            let first = segment.exerciseIndex == 0
            if let side = segment.side {
                return first ? L("First up: %@, %@.", exercise.info.name, side.spoken)
                             : L("Next: %@, %@.", exercise.info.name, side.spoken)
            }
            return first ? L("First up: %@.", exercise.info.name) : L("Next: %@.", exercise.info.name)
        case .switchSide:
            if let side = segment.side { return L("Switch sides. %@.", side.title) }
            return L("Switch sides.")
        case .rest:
            return L("Rest. %@ seconds.", segment.durationSec ?? exercise.restSec)
        case .work:
            let numbered = exercise.sets > 1 && segment.setNumber > 1
            if let seconds = segment.durationSec {
                return numbered ? L("Set %@. Hold for %@ seconds.", segment.setNumber, seconds)
                                : L("Go. Hold for %@ seconds.", seconds)
            }
            if let reps = segment.reps {
                return numbered ? L("Set %@. %@ reps.", segment.setNumber, reps) : L("Go. %@ reps.", reps)
            }
            return numbered ? L("Set %@.", segment.setNumber) : L("Go.")
        }
    }
}

/// The live screen: the exercise in motion, what to think about, and the timer or rep counter.
struct GuidedExerciseScreen: View {
    @ObservedObject var engine: GuidedSessionEngine
    let onPause: () -> Void
    let onHowTo: (ExerciseInfo) -> Void

    var body: some View {
        ZStack {
            Color.white.ignoresSafeArea()
            if let segment = engine.current, let exercise = engine.currentExercise {
                ScrollView {
                    VStack(spacing: 16) {
                        GuidedProgressBar(count: engine.exercises.count, currentIndex: segment.exerciseIndex)
                        header(segment, exercise)
                        panel(segment, exercise)
                        titleBlock(segment, exercise)
                        timerBlock(segment, exercise)
                    }
                    .padding(.horizontal, Theme.screenPadding)
                    .padding(.top, 14)
                    .padding(.bottom, 12)
                }
                .scrollIndicators(.hidden)
                .scrollBounceBehavior(.basedOnSize)
                .safeAreaInset(edge: .bottom, spacing: 0) { controls }
            }
        }
        .animation(.easeInOut(duration: 0.3), value: engine.index)
    }

    // MARK: Header

    private func header(_ segment: GuidedSegment, _ exercise: GuidedPrescription) -> some View {
        HStack(spacing: 10) {
            GuidedChip(text: GuidedWording.position(exercise, index: segment.exerciseIndex,
                                                    count: engine.exercises.count),
                       foreground: Theme.green, background: Theme.greenTint)
            Spacer(minLength: 8)
            Text(GuidedWording.status(segment, exercise))
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(segment.kind == .work ? Theme.muted : Theme.orangeDeep)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
    }

    // MARK: Picture

    private func panelColor(_ kind: GuidedSegmentKind) -> Color {
        switch kind {
        case .work: return Theme.greenTint
        case .rest: return Theme.orangeTint
        case .getReady, .switchSide: return Theme.surface
        }
    }

    private func panel(_ segment: GuidedSegment, _ exercise: GuidedPrescription) -> some View {
        let moving = engine.state == .running && (segment.kind == .work || segment.kind == .getReady)
        return ZStack {
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .fill(panelColor(segment.kind))
            GuidedExerciseAnimation(info: exercise.info, playing: moving)
                // The pictures show one side; the other side is the same picture mirrored.
                .scaleEffect(x: segment.side == .right ? -1 : 1, y: 1)
                .padding(.horizontal, 16)
                .padding(.top, 34)
                .padding(.bottom, 12)
                .id(segment.exerciseIndex)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 250)
        .overlay(alignment: .topLeading) {
            if let side = segment.side {
                GuidedChip(text: side.title)
                    .padding(12)
            }
        }
        .overlay(alignment: .topTrailing) {
            Button {
                Haptics.tap()
                onHowTo(exercise.info)
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "info.circle")
                        .font(.system(size: 14, weight: .semibold))
                    Text(L("How to"))
                        .font(.system(size: 14, weight: .bold))
                }
                .foregroundStyle(Theme.ink)
                .padding(.horizontal, 12)
                .frame(height: 34)
                .background(Color.white, in: Capsule())
                .frame(minWidth: 44, minHeight: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(.top, 7)
            .padding(.trailing, 12)
            .accessibilityLabel(L("How to do this exercise"))
        }
    }

    // MARK: Name and cue

    private func titleBlock(_ segment: GuidedSegment, _ exercise: GuidedPrescription) -> some View {
        let lines = GuidedWording.lines(segment, exercise)
        let cue = lines[Int(engine.segmentElapsed / 6) % max(1, lines.count)]
        return VStack(spacing: 4) {
            Text(GuidedWording.eyebrow(segment, exercise))
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(segment.kind == .work ? Theme.green : Theme.orangeDeep)
            Text(exercise.info.name)
                .font(.system(size: 30, weight: .bold))
                .tracking(-0.6)
                .foregroundStyle(Theme.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            Text(cue)
                .font(.system(size: 16))
                .foregroundStyle(Theme.body)
                .multilineTextAlignment(.center)
                .lineLimit(2)
                .minimumScaleFactor(0.85)
                .frame(maxWidth: .infinity, minHeight: 44, alignment: .center)
                .id(cue)
                .transition(.opacity)
        }
        .frame(maxWidth: .infinity)
        .animation(.easeInOut(duration: 0.3), value: cue)
    }

    // MARK: Timer or reps

    @ViewBuilder
    private func timerBlock(_ segment: GuidedSegment, _ exercise: GuidedPrescription) -> some View {
        if let remaining = engine.remainingSeconds {
            VStack(spacing: 8) {
                GuidedTimerRing(progress: engine.segmentProgress, text: "\(remaining)",
                                caption: remaining == 1 ? L("second") : L("seconds"),
                                tint: segment.kind == .work ? Theme.green : Theme.orange)
                if segment.kind == .rest {
                    Button(L("Skip rest")) { engine.completeSegment() }
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Theme.ink)
                        .frame(minWidth: 120, minHeight: 44)
                }
            }
        } else if let reps = segment.reps {
            VStack(spacing: 10) {
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text("\(engine.pacedRep ?? 1)")
                        .font(.system(size: 72, weight: .bold))
                        .monospacedDigit()
                        .tracking(-2)
                        .foregroundStyle(Theme.ink)
                    Text(L("of %@ reps", reps))
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(Theme.muted)
                }
                .accessibilityElement(children: .combine)
                Button {
                    engine.completeSegment()
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "checkmark")
                            .font(.system(size: 15, weight: .bold))
                        Text(L("Done"))
                            .font(.system(size: 17, weight: .semibold))
                    }
                    .foregroundStyle(.white)
                    .frame(width: 180, height: 52)
                    .background(Theme.green, in: Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(L("Set done"))
            }
        }
    }

    // MARK: Controls

    private var controls: some View {
        HStack {
            RunRoundButton(symbol: "backward.end.fill", label: L("Previous exercise")) {
                engine.previousExercise()
            }
            Spacer()
            Button(action: {
                Haptics.confirm()
                onPause()
            }) {
                Image(systemName: "pause.fill")
                    .font(.system(size: 30, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 84, height: 84)
                    .background(Theme.ink, in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(L("Pause"))
            Spacer()
            RunRoundButton(symbol: "forward.end.fill", label: L("Next exercise")) {
                engine.nextExercise()
            }
        }
        .padding(.horizontal, 28)
        .padding(.top, 10)
        .padding(.bottom, 12)
        .background(Color.white)
    }
}

#Preview {
    let engine = GuidedSessionEngine()
    engine.configure(exercises: GuidedPrescription.stretchingSession(phase: .base))
    engine.start()
    return GuidedExerciseScreen(engine: engine, onPause: {}, onHowTo: { _ in })
}
