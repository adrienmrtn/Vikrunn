import Foundation
import SwiftUI
import AVFoundation

/// Speaks the coach's cues during a run and keeps the last one for the coach bubble.
/// Music from other apps is lowered while the coach talks, then comes back.
@MainActor
final class VoiceCoach: NSObject, ObservableObject {
    /// Text of the last cue, shown in the coach bubble of the live screen.
    @Published private(set) var lastCue: String = ""

    private let synthesizer = AVSpeechSynthesizer()
    private let voice: AVSpeechSynthesisVoice? = AVSpeechSynthesisVoice(language: "en-US")
    private var lastDriftCue: Date = .distantPast
    private var wasOffTarget = false
    private var pending = 0

    /// Seconds between two pace remarks, at least.
    private let driftInterval: TimeInterval = 30
    /// Seconds per kilometre allowed outside the target range before the coach speaks.
    private let driftTolerance = 8

    override init() {
        super.init()
        synthesizer.delegate = self
    }

    // MARK: Cues

    /// Shows a cue in the coach bubble without speaking.
    func show(_ text: String) {
        lastCue = text
    }

    /// Shows a cue and reads it aloud when the voice is on.
    func say(_ speech: String, display: String, settings: VoiceSettings) {
        lastCue = display
        guard settings.enabled, !speech.isEmpty else { return }
        activateAudio()
        let utterance = AVSpeechUtterance(string: speech)
        utterance.voice = voice
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate
        utterance.postUtteranceDelay = 0.1
        pending += 1
        synthesizer.speak(utterance)
    }

    /// Cuts whatever is being said, then speaks. For short timed cues that must not queue up
    /// (guided strength and stretching sessions).
    func sayNow(_ speech: String, display: String, settings: VoiceSettings) {
        if synthesizer.isSpeaking { synthesizer.stopSpeaking(at: .immediate) }
        say(speech, display: display, settings: settings)
    }

    /// Stops talking right away (end of the run, flow closed).
    func stop() {
        if synthesizer.isSpeaking { synthesizer.stopSpeaking(at: .immediate) }
        pending = 0
        deactivateAudio()
    }

    // MARK: Tracker events

    func handle(_ event: RunTrackerEvent, tracker: RunTracker, settings: VoiceSettings) {
        switch event {
        case .stepStarted(let index):
            wasOffTarget = false
            lastDriftCue = Date().addingTimeInterval(-driftInterval + 15)
            guard settings.announceSteps else { return }
            say(RunFormat.stepSpeech(at: index, in: tracker.steps),
                display: RunFormat.stepCue(at: index, in: tracker.steps),
                settings: settings)

        case .sessionComplete:
            wasOffTarget = false
            guard settings.announceSteps else { return }
            say("Session complete. Well done. Stop whenever you are ready.",
                display: "Session complete. Well done. Stop whenever you're ready.",
                settings: settings)

        case .kilometre(let count):
            guard settings.announceEveryKm else { return }
            announceKilometre(count, tracker: tracker, settings: settings)

        case .paceTick:
            guard settings.announcePace else { return }
            checkPace(tracker: tracker, settings: settings)
        }
    }

    private func announceKilometre(_ count: Int, tracker: RunTracker, settings: VoiceSettings) {
        let unit = count == 1 ? "kilometre" : "kilometres"
        let pace = tracker.averagePace
        var speech = "\(count) \(unit). Time, \(RunFormat.spokenDuration(tracker.elapsed))."
        var display = "\(count) km in \(Fmt.duration(tracker.elapsed))."
        if pace > 0 {
            speech += " Average pace, \(Fmt.pace(pace)) per kilometre."
            display += " Average \(Fmt.pace(pace)) /km."
        }
        say(speech, display: display, settings: settings)
    }

    /// During fast steps, tells the runner when the pace drifts out of the target range.
    private func checkPace(tracker: RunTracker, settings: VoiceSettings) {
        guard tracker.state == .running,
              let step = tracker.currentStep, step.kind == .work,
              let fast = step.paceFast, let slow = step.paceSlow,
              tracker.stepElapsed >= 20, tracker.currentPace > 0 else { return }
        let pace = tracker.currentPace
        let tooFast = pace < fast - driftTolerance
        let tooSlow = pace > slow + driftTolerance
        if tooFast || tooSlow {
            guard Date().timeIntervalSince(lastDriftCue) >= driftInterval else { return }
            lastDriftCue = Date()
            wasOffTarget = true
            let target = Fmt.pace((fast + slow) / 2)
            if tooFast {
                say("A little too fast. Ease off towards \(target) per kilometre.",
                    display: "A little too fast. Ease off towards \(target) /km.",
                    settings: settings)
            } else {
                say("A little too slow. Pick it up towards \(target) per kilometre.",
                    display: "A little too slow. Pick it up towards \(target) /km.",
                    settings: settings)
            }
        } else if wasOffTarget {
            wasOffTarget = false
            show("Back on target. Hold this pace.")
        }
    }

    // MARK: Audio session

    private func activateAudio() {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playback, mode: .voicePrompt,
                                    options: [.duckOthers, .interruptSpokenAudioAndMixWithOthers])
            try session.setActive(true)
        } catch {
            // Speaking still works with the default session; music just will not duck.
        }
    }

    private func deactivateAudio() {
        do {
            try AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        } catch {
            // Nothing to do: the session was busy or already inactive.
        }
    }

    fileprivate func utteranceEnded() {
        pending = max(0, pending - 1)
        if pending == 0 { deactivateAudio() }
    }
}

extension VoiceCoach: AVSpeechSynthesizerDelegate {
    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        Task { @MainActor [weak self] in
            self?.utteranceEnded()
        }
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        Task { @MainActor [weak self] in
            self?.utteranceEnded()
        }
    }
}
