import Foundation
import SwiftUI
import AVFoundation

/// Speaks the coach's cues during a run and keeps the last one for the coach bubble.
/// A coach with recorded clips (see `VoiceClip`) speaks with their own voice; the phone's
/// synthesized voice says everything else, and everything for a coach without recordings.
/// Music from other apps is lowered while the coach talks, then comes back.
@MainActor
final class VoiceCoach: NSObject, ObservableObject {
    /// Text of the last cue, shown in the coach bubble of the live screen.
    @Published private(set) var lastCue: String = ""

    private let synthesizer = AVSpeechSynthesizer()
    private let voice: AVSpeechSynthesisVoice? = VoiceCoach.bestVoice()
    private var lastDriftCue: Date = .distantPast
    private var wasOffTarget = false
    private var pending = 0

    /// Recorded clips: the one playing, the ones waiting behind it, and the last one of each line.
    private var player: AVAudioPlayer?
    private var clipQueue: [URL] = []
    private var clipsPlaying = false
    private var lastClipNames: [String] = []

    /// Seconds between two pace remarks, at least.
    private let driftInterval: TimeInterval = 30
    /// Seconds per kilometre allowed outside the target range before the coach speaks.
    private let driftTolerance = 8

    override init() {
        super.init()
        synthesizer.delegate = self
    }

    /// The most natural English voice installed on the phone: a premium or enhanced one when the
    /// user downloaded it (Settings, Accessibility, Spoken Content, Voices), the standard one otherwise.
    nonisolated private static func bestVoice() -> AVSpeechSynthesisVoice? {
        func rank(_ voice: AVSpeechSynthesisVoice) -> Int {
            var score = 0
            switch voice.quality {
            case .premium: score += 20
            case .enhanced: score += 10
            default: break
            }
            if voice.language == "en-US" { score += 2 }
            return score
        }
        let english = AVSpeechSynthesisVoice.speechVoices().filter { $0.language.hasPrefix("en") }
        if let best = english.max(by: { rank($0) < rank($1) }), rank(best) >= 10 {
            return best
        }
        return AVSpeechSynthesisVoice(language: "en-US")
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
        // Two voices at once would be unintelligible: the newer cue wins.
        stopClips()
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

    /// Shows a cue and says it with the coach's recorded voice: the clips play one after the other.
    /// When the chosen coach has no recording for one of them, `speech` is synthesized instead.
    func say(clips: [VoiceClip], speech: String, display: String, settings: VoiceSettings) {
        lastCue = display
        guard settings.enabled else { return }
        let coachID = GuidedExerciseCoach.coachID
        var names: [String] = []
        var urls: [URL] = []
        for clip in clips {
            guard let found = clip.url(coachID: coachID, avoiding: lastClipNames.last(where: { Self.sameLine($0, clip, coachID) })) else {
                say(speech, display: display, settings: settings)
                return
            }
            names.append(found.name)
            urls.append(found.url)
        }
        guard !urls.isEmpty else {
            say(speech, display: display, settings: settings)
            return
        }
        lastClipNames = Array((lastClipNames + names).suffix(12))
        play(clips: urls)
    }

    /// True when `name` is a recording of the same line as `clip` for this coach.
    private static func sameLine(_ name: String, _ clip: VoiceClip, _ coachID: Int) -> Bool {
        let other = clip.resourceName(coachID: coachID, avoiding: nil)
        guard let cut = other.lastIndex(of: "_") else { return false }
        return name.hasPrefix(other[...cut])
    }

    /// Stops talking right away (end of the run, flow closed).
    func stop() {
        if synthesizer.isSpeaking { synthesizer.stopSpeaking(at: .immediate) }
        player?.stop()
        player = nil
        clipQueue = []
        clipsPlaying = false
        pending = 0
        deactivateAudio()
    }

    // MARK: Recorded clips

    /// Plays the files one after the other. A set of clips counts as one cue for the audio session.
    private func play(clips urls: [URL]) {
        if synthesizer.isSpeaking { synthesizer.stopSpeaking(at: .immediate) }
        stopClips()
        activateAudio()
        clipQueue = urls
        clipsPlaying = true
        pending += 1
        playNextClip()
    }

    private func playNextClip() {
        while !clipQueue.isEmpty {
            let url = clipQueue.removeFirst()
            if let next = try? AVAudioPlayer(contentsOf: url) {
                next.delegate = self
                player = next
                if next.play() { return }
            }
        }
        // Nothing left (or nothing playable): the cue is over.
        player = nil
        if clipsPlaying {
            clipsPlaying = false
            utteranceEnded()
        }
    }

    /// Cuts the recorded clips without releasing the audio session (something else is about to speak).
    private func stopClips() {
        guard clipsPlaying else { return }
        player?.stop()
        player = nil
        clipQueue = []
        clipsPlaying = false
        pending = max(0, pending - 1)
    }

    fileprivate func clipEnded() {
        guard clipsPlaying else { return }
        playNextClip()
    }

    // MARK: Tracker events

    func handle(_ event: RunTrackerEvent, tracker: RunTracker, settings: VoiceSettings) {
        switch event {
        case .stepStarted(let index):
            wasOffTarget = false
            lastDriftCue = Date().addingTimeInterval(-driftInterval + 15)
            guard settings.announceSteps else { return }
            let speech = RunFormat.stepSpeech(at: index, in: tracker.steps)
            let display = RunFormat.stepCue(at: index, in: tracker.steps)
            if tracker.steps.indices.contains(index), let clip = VoiceClip.opening(tracker.steps[index].kind) {
                say(clips: [clip], speech: speech, display: display, settings: settings)
            } else {
                say(speech, display: display, settings: settings)
            }

        case .sessionComplete:
            wasOffTarget = false
            guard settings.announceSteps else { return }
            say(clips: [.complete],
                speech: "Session complete. Well done. Stop whenever you are ready.",
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
        if (1...VoiceClip.lastKilometre).contains(count) {
            // The recorded voice gives the kilometre and a word of encouragement; the time and
            // the pace stay on screen. The synthesized sentence is the fallback.
            say(clips: [.km(count), .cheer], speech: speech, display: display, settings: settings)
        } else {
            say(speech, display: display, settings: settings)
        }
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
                say(clips: [.fast],
                    speech: "A little too fast. Ease off towards \(target) per kilometre.",
                    display: "A little too fast. Ease off towards \(target) /km.",
                    settings: settings)
            } else {
                say(clips: [.slow],
                    speech: "A little too slow. Pick it up towards \(target) per kilometre.",
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

extension VoiceCoach: AVAudioPlayerDelegate {
    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor [weak self] in
            self?.clipEnded()
        }
    }

    nonisolated func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: Error?) {
        Task { @MainActor [weak self] in
            self?.clipEnded()
        }
    }
}
