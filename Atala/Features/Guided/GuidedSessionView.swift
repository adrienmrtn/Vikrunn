import SwiftUI
import UIKit

private enum GuidedFlowPhase {
    case ready, active, finish
}

/// A guided strength or stretching session, from "Start" to the summary. Present with `.fullScreenCover`.
/// It marks the session as done and dismisses itself.
struct GuidedSessionView: View {
    let workout: Workout

    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @StateObject private var engine = GuidedSessionEngine()
    @StateObject private var voice = VoiceCoach()
    @State private var phase: GuidedFlowPhase = .ready
    @State private var didSetup = false
    @State private var confirmEnd = false
    @State private var howTo: ExerciseInfo?
    /// True while the clock is stopped only because the instructions are open.
    @State private var pausedForHowTo = false

    init(workout: Workout) {
        self.workout = workout
    }

    var body: some View {
        content
            .animation(.easeInOut(duration: 0.25), value: phase)
            .animation(.easeInOut(duration: 0.25), value: engine.state)
            .onAppear { setup() }
            .onDisappear { teardown() }
            .onChange(of: engine.state) { _, newState in
                if newState == .finished { showFinish() }
            }
            .sheet(item: $howTo, onDismiss: { closeHowTo() }) { info in
                GuidedHowToSheet(info: info)
            }
            .confirmationDialog("End this session?", isPresented: $confirmEnd, titleVisibility: .visible) {
                Button("Mark as done anyway") {
                    Haptics.success()
                    leave(markDone: true)
                }
                Button("Leave without saving", role: .destructive) {
                    Haptics.warning()
                    leave(markDone: false)
                }
                Button("Keep going", role: .cancel) {}
            } message: {
                Text(endMessage)
            }
    }

    @ViewBuilder
    private var content: some View {
        switch phase {
        case .ready:
            GuidedReadyScreen(workout: workout, exercises: engine.exercises,
                              onStart: { begin() },
                              onClose: { close() })
        case .active:
            if engine.state == .paused && !pausedForHowTo {
                GuidedPauseScreen(engine: engine, coach: store.coach,
                                  onResume: { engine.resume() },
                                  onEnd: { confirmEnd = true })
            } else {
                GuidedExerciseScreen(engine: engine,
                                     onPause: { engine.pause() },
                                     onHowTo: { info in openHowTo(info) })
            }
        case .finish:
            GuidedFinishScreen(workout: workout,
                               elapsedSec: engine.totalElapsed,
                               doneCount: engine.completedExercises.count,
                               totalCount: engine.exercises.count,
                               coach: store.coach,
                               onDone: { feeling in complete(feeling: feeling) })
        }
    }

    private var endMessage: String {
        let done = engine.completedExercises.count
        let total = engine.exercises.count
        return "You completed \(done) of \(total) exercises."
    }

    // MARK: Actions

    private func setup() {
        guard !didSetup else { return }
        didSetup = true
        engine.configure(exercises: GuidedPrescription.session(for: workout))
        let store = self.store
        let voice = self.voice
        let engine = self.engine
        engine.onEvent = { [weak engine, weak voice] event in
            guard let engine, let voice else { return }
            GuidedSessionView.handle(event, engine: engine, voice: voice, settings: store.settings.voice)
        }
    }

    /// Voice and haptics for what the engine reports.
    private static func handle(_ event: GuidedSessionEvent, engine: GuidedSessionEngine,
                               voice: VoiceCoach, settings: VoiceSettings) {
        switch event {
        case .segmentStarted(let segment):
            // The first segment starts with the session, which has its own (strong) haptic.
            if segment.id > 0 { Haptics.play(segment.kind == .work ? .rigid : .tap) }
            guard engine.exercises.indices.contains(segment.exerciseIndex) else { return }
            let line = GuidedWording.announcement(segment, engine.exercises[segment.exerciseIndex])
            voice.sayNow(line, display: line, settings: settings)
        case .halfway:
            Haptics.tap()
            voice.say("Halfway.", display: "Halfway.", settings: settings)
        case .countdown(let seconds):
            // Light, light, medium, as before a run.
            Haptics.play(seconds == 1 ? .confirm : .tap)
            voice.sayNow("\(seconds)", display: "\(seconds)", settings: settings)
        case .finished:
            // The success haptic comes with the check mark of the "Session complete" screen.
            voice.sayNow("Session complete. Well done.", display: "Session complete. Well done.",
                         settings: settings)
        }
    }

    private func begin() {
        guard phase == .ready, !engine.exercises.isEmpty else { return }
        Haptics.strong()
        UIApplication.shared.isIdleTimerDisabled = true
        phase = .active
        engine.start()
    }

    private func openHowTo(_ info: ExerciseInfo) {
        // The clock waits while the instructions are open, and starts again when the sheet closes.
        guard engine.state == .running else { return }
        pausedForHowTo = true
        engine.pause()
        howTo = info
    }

    private func closeHowTo() {
        guard pausedForHowTo else { return }
        engine.resume()
        pausedForHowTo = false
    }

    private func showFinish() {
        UIApplication.shared.isIdleTimerDisabled = false
        howTo = nil
        pausedForHowTo = false
        phase = .finish
    }

    /// "Done" on the summary: the session is marked as done, with the feeling when one was given.
    private func complete(feeling: Feeling?) {
        if let feeling {
            store.updateWorkout(workout.id) { $0.feeling = feeling }
        }
        store.setStatus(.done, for: workout.id)
        close()
    }

    /// Ending early, after the confirmation.
    private func leave(markDone: Bool) {
        if markDone { store.setStatus(.done, for: workout.id) }
        close()
    }

    private func close() {
        teardown()
        dismiss()
    }

    private func teardown() {
        engine.shutdown()
        voice.stop()
        UIApplication.shared.isIdleTimerDisabled = false
    }
}

#Preview {
    let store = AppStore.preview
    let workout = store.allWorkouts.first { $0.kind == .stretching }
    return Group {
        if let workout {
            GuidedSessionView(workout: workout)
        }
    }
    .environmentObject(store)
}
