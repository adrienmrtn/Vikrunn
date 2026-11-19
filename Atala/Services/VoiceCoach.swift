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
    /// When one of them has no recording on the phone, `speech` is synthesized instead.
    /// `interrupt` cuts whatever is being said (short timed cues of the guided sessions).
    func say(clips: [VoiceClip], speech: String, display: String, settings: VoiceSettings,
             interrupt: Bool = false) {
        lastCue = display
        guard settings.enabled else { return }
        let coachID = GuidedExerciseCoach.coachID
        var ids: [String] = []
        var urls: [URL] = []
        for clip in clips {
            guard let found = clip.url(coachID: coachID, avoiding: lastClipNames.last(where: { clip.matches($0) })) else {
                ids = []
                break
            }
            ids.append(found.id)
            urls.append(found.url)
        }
        guard !ids.isEmpty else {
            if interrupt {
                sayNow(speech, display: display, settings: settings)
            } else {
                say(speech, display: display, settings: settings)
            }
            return
        }
        lastClipNames = Array((lastClipNames + ids).suffix(12))
        play(clips: urls)
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
                speech: L("Session complete. Well done. Stop whenever you are ready."),
                display: L("Session complete. Well done. Stop whenever you're ready."),
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
        let pace = tracker.averagePace
        let spokenTime = RunFormat.spokenDuration(tracker.elapsed)
        var speech = count == 1 ? L("%@ kilometre. Time, %@.", count, spokenTime) : L("%@ kilometres. Time, %@.", count, spokenTime)
        var display = L("%@ km in %@.", count, Fmt.duration(tracker.elapsed))
        if pace > 0 {
            speech += " " + L("Average pace, %@ per kilometre.", Fmt.pace(pace))
            display += " " + L("Average %@ /km.", Fmt.pace(pace))
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
                    speech: L("A little too fast. Ease off towards %@ per kilometre.", target),
                    display: L("A little too fast. Ease off towards %@ /km.", target),
                    settings: settings)
            } else {
                say(clips: [.slow],
                    speech: L("A little too slow. Pick it up towards %@ per kilometre.", target),
                    display: L("A little too slow. Pick it up towards %@ /km.", target),
                    settings: settings)
            }
        } else if wasOffTarget {
            wasOffTarget = false
            show(L("Back on target. Hold this pace."))
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
