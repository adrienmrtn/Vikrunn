import SwiftUI
import UIKit

private enum RunFlowPhase {
    case ready, countdown, active, feeling, summary
}

/// The whole run, from "Ready to go?" to the summary. Present with `.fullScreenCover`.
/// A nil workout is a free run.
struct RunFlowView: View {
    let workout: Workout?

    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @StateObject private var tracker = RunTracker()
    @StateObject private var voice = VoiceCoach()
    @State private var phase: RunFlowPhase = .ready
    @State private var didSetup = false
    @State private var savedRun: RunRecord?
    @State private var confirmShortRun = false

    init(workout: Workout?) {
        self.workout = workout
    }

    var body: some View {
        content
            .animation(.easeInOut(duration: 0.25), value: phase)
            .onAppear { setup() }
            .onDisappear {
                // Never cut a recording here: only clean up when nothing is being recorded.
                if tracker.state == .idle || tracker.state == .finished { teardown() }
            }
            .confirmationDialog(shortRunTitle, isPresented: $confirmShortRun, titleVisibility: .visible) {
                Button(noDistance ? "Save the time only" : "Save it anyway") { finishRun(save: true) }
                Button("Discard it", role: .destructive) { finishRun(save: false) }
                Button("Keep going", role: .cancel) {}
            } message: {
                Text(shortRunMessage)
            }
    }

    @ViewBuilder
    private var content: some View {
        switch phase {
        case .ready:
            RunReadyScreen(workout: workout, tracker: tracker,
                           onStart: { phase = .countdown },
                           onClose: { close() })
        case .countdown:
            RunCountdownScreen(coach: store.coach,
                               heading: countdownHeading,
                               line: countdownLine,
                               onDone: { begin() })
        case .active:
            if tracker.state == .paused {
                RunPauseScreen(tracker: tracker, coach: store.coach,
                               onResume: { tracker.resume() },
                               onFinish: { requestFinish() })
            } else {
                RunLiveScreen(tracker: tracker, voice: voice, coach: store.coach,
                              onPause: { tracker.pause() })
            }
        case .feeling:
            RunFeelingScreen(distanceM: tracker.distance,
                             durationSec: tracker.elapsed,
                             coachName: store.coach.name,
                             onDone: { feeling in showSummary(feeling: feeling) })
        case .summary:
            if let savedRun {
                RunSummaryView(run: savedRun, onDone: { close() })
            } else {
                Color.white.ignoresSafeArea()
            }
        }
    }

    // MARK: Wording

    /// A real effort in time, but no distance: location refused, or no signal (indoors).
    private var noDistance: Bool {
        tracker.elapsed >= 60 && tracker.distance < 100
    }

    private var shortRunTitle: String {
        noDistance ? "No distance was recorded" : "This session is very short"
    }

    private var shortRunMessage: String {
        if noDistance {
            return tracker.gps == .denied
                ? "Location is off for Atala, so only your time was measured. You can save it as it is, or discard it and add the run by hand with its distance."
                : "The GPS signal never came through, so only your time was measured. You can save it as it is, or discard it and add the run by hand with its distance."
        }
        return "Less than a minute or 100 metres were recorded."
    }

    private var firstStep: WorkoutStep? { tracker.steps.first }

    private var countdownHeading: String {
        if let firstStep { return "\(firstStep.kind.title) in" }
        return "Starting in"
    }

    private var countdownLine: String {
        guard let firstStep else { return "No plan today. Run the way you feel." }
        switch firstStep.kind {
        case .work: return "Stay relaxed. Find your rhythm first."
        default: return "Start gently. You should be able to talk."
        }
    }

    // MARK: Actions

    private func setup() {
        guard !didSetup else { return }
        didSetup = true
        tracker.configure(workout: workout)
        tracker.prepare()
        let store = self.store
        let voice = self.voice
        let tracker = self.tracker
        tracker.onEvent = { [weak tracker, weak voice] event in
            guard let tracker, let voice else { return }
            voice.handle(event, tracker: tracker, settings: store.settings.voice)
        }
    }

    private func begin() {
        guard phase == .countdown else { return }
        UIApplication.shared.isIdleTimerDisabled = true
        if !tracker.hasSteps { voice.show("Off you go. Run the way you feel.") }
        tracker.start()
        phase = .active
    }

    private func requestFinish() {
        if tracker.elapsed < 60 || tracker.distance < 100 {
            confirmShortRun = true
        } else {
            finishRun(save: true)
        }
    }

    /// Stops the recording. The run is saved right away so nothing is lost if the app is closed.
    private func finishRun(save: Bool) {
        tracker.stop()
        voice.stop()
        UIApplication.shared.isIdleTimerDisabled = false
        guard save else {
            close()
            return
        }
        let run = tracker.makeRecord(title: workout?.title ?? "Free run", workoutID: workout?.id)
        store.addRun(run)
        savedRun = run
        attachHeartRate(to: run)
        phase = .feeling
    }

    /// When the Health import is on, a watch worn during the run gives its heart rate.
    private func attachHeartRate(to run: RunRecord) {
        guard store.settings.healthImportEnabled else { return }
        let store = self.store
        let end = Date()
        Task { @MainActor in
            // The watch hands its samples to the phone with a delay: look now, then once more later.
            for wait in [0.0, 60.0] {
                if wait > 0 { try? await Task.sleep(nanoseconds: UInt64(wait * 1_000_000_000)) }
                guard let measured = await HealthKitService.shared.heartRate(from: run.date, to: end) else { continue }
                store.updateRun(run.id) { record in
                    record.avgHeartRate = measured.average
                    record.maxHeartRate = measured.peak
                }
            }
        }
    }

    private func showSummary(feeling: Feeling?) {
        if var run = savedRun {
            run.feeling = feeling
            store.updateRun(run.id) { $0.feeling = feeling }
            savedRun = run
        }
        phase = .summary
    }

    private func close() {
        teardown()
        dismiss()
    }

    private func teardown() {
        tracker.shutdown()
        voice.stop()
        UIApplication.shared.isIdleTimerDisabled = false
    }
}

#Preview {
    RunFlowView(workout: AppStore.preview.nextRun)
        .environmentObject(AppStore.preview)
}
