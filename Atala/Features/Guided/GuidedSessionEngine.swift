import Foundation
import SwiftUI

enum GuidedSide: String, Equatable {
    case left, right
    var title: String { self == .left ? "Left leg" : "Right leg" }
    var spoken: String { self == .left ? "left leg" : "right leg" }
}

enum GuidedSegmentKind: Equatable {
    case getReady, work, switchSide, rest
}

/// One timed (or rep-based) slice of the session.
struct GuidedSegment: Identifiable, Equatable {
    let id: Int
    let kind: GuidedSegmentKind
    let exerciseIndex: Int
    /// 1-based set number.
    let setNumber: Int
    /// Side being worked, or the side coming next for get-ready and switch segments.
    let side: GuidedSide?
    /// Nil for a rep-based set, which ends when the user taps "Done".
    let durationSec: Int?
    let reps: Int?

    static func build(from exercises: [GuidedPrescription]) -> [GuidedSegment] {
        var result: [GuidedSegment] = []
        func add(_ kind: GuidedSegmentKind, _ exercise: Int, _ number: Int, _ side: GuidedSide?,
                 _ duration: Int?, _ reps: Int?) {
            result.append(GuidedSegment(id: result.count, kind: kind, exerciseIndex: exercise, setNumber: number,
                                        side: side, durationSec: duration, reps: reps))
        }
        for (index, exercise) in exercises.enumerated() {
            let sides: [GuidedSide?] = exercise.perSide ? [.left, .right] : [nil]
            add(.getReady, index, 1, sides[0], GuidedTiming.getReady, nil)
            for number in 1...max(1, exercise.sets) {
                for (position, side) in sides.enumerated() {
                    if position > 0 {
                        add(.switchSide, index, number, side, GuidedTiming.switchSide, nil)
                    }
                    add(.work, index, number, side, exercise.isTimed ? exercise.holdSec : nil, exercise.reps)
                }
                if number < exercise.sets, exercise.restSec > 0 {
                    add(.rest, index, number, sides[0], exercise.restSec, nil)
                }
            }
        }
        return result
    }
}

enum GuidedSessionState: Equatable {
    case idle, running, paused, finished
}

enum GuidedSessionEvent {
    case segmentStarted(GuidedSegment)
    case halfway
    case countdown(Int)
    case finished
}

/// Drives a guided session. Every duration is measured against the clock (dates), never by counting ticks,
/// so the timers are still right when the app comes back from the background.
@MainActor
final class GuidedSessionEngine: ObservableObject {
    @Published private(set) var state: GuidedSessionState = .idle
    @Published private(set) var index: Int = 0
    /// Refreshed several times per second while running; everything shown derives from it.
    @Published private(set) var now: Date = Date()
    @Published private(set) var completedExercises: Set<Int> = []

    @Published private(set) var exercises: [GuidedPrescription] = []
    private(set) var segments: [GuidedSegment] = []
    var onEvent: ((GuidedSessionEvent) -> Void)?

    private var segmentBanked: TimeInterval = 0
    private var segmentResumedAt: Date?
    private var totalBanked: TimeInterval = 0
    private var totalResumedAt: Date?
    private var timer: Timer?
    private var halfwaySaid = false
    private var lastCountdown = 0

    func configure(exercises: [GuidedPrescription]) {
        guard state == .idle else { return }
        self.exercises = exercises
        segments = GuidedSegment.build(from: exercises)
    }

    // MARK: Reading

    var current: GuidedSegment? {
        segments.indices.contains(index) ? segments[index] : nil
    }

    var currentExercise: GuidedPrescription? {
        guard let current, exercises.indices.contains(current.exerciseIndex) else { return nil }
        return exercises[current.exerciseIndex]
    }

    var plannedSeconds: Int { exercises.reduce(0) { $0 + $1.totalSec } }

    /// Seconds spent in the current segment, pauses excluded.
    var segmentElapsed: TimeInterval {
        var value = segmentBanked
        if let segmentResumedAt { value += now.timeIntervalSince(segmentResumedAt) }
        return max(0, value)
    }

    /// Seconds of the whole session, pauses excluded.
    var totalElapsed: Int {
        var value = totalBanked
        if let totalResumedAt { value += now.timeIntervalSince(totalResumedAt) }
        return max(0, Int(value))
    }

    /// Whole seconds left in a timed segment (rounded up, so it reads 5, 4, 3, 2, 1).
    var remainingSeconds: Int? {
        guard let duration = current?.durationSec else { return nil }
        return max(0, Int((Double(duration) - segmentElapsed).rounded(.up)))
    }

    /// 0...1 for a timed segment.
    var segmentProgress: Double {
        guard let duration = current?.durationSec, duration > 0 else { return 0 }
        return min(1, max(0, segmentElapsed / Double(duration)))
    }

    /// Rep the calm tempo is at, for a rep-based set (1...reps).
    var pacedRep: Int? {
        guard let current, current.durationSec == nil, let reps = current.reps else { return nil }
        let perRep = Double(max(1, currentExercise?.info.repSec ?? 3))
        return min(reps, Int(segmentElapsed / perRep) + 1)
    }

    func firstSegmentIndex(ofExercise exercise: Int) -> Int? {
        segments.firstIndex { $0.exerciseIndex == exercise }
    }

    // MARK: Commands

    func start() {
        guard state == .idle, !segments.isEmpty else { return }
        state = .running
        totalBanked = 0
        totalResumedAt = Date()
        startTimer()
        go(to: 0)
    }

    func pause() {
        guard state == .running else { return }
        let date = Date()
        if let segmentResumedAt { segmentBanked += date.timeIntervalSince(segmentResumedAt) }
        if let totalResumedAt { totalBanked += date.timeIntervalSince(totalResumedAt) }
        segmentResumedAt = nil
        totalResumedAt = nil
        now = date
        state = .paused
        stopTimer()
    }

    func resume() {
        guard state == .paused else { return }
        let date = Date()
        segmentResumedAt = date
        totalResumedAt = date
        now = date
        state = .running
        startTimer()
    }

    /// Ends the current segment as done: "Done" on a rep set, "Skip rest", or a timer reaching zero.
    func completeSegment() {
        guard state == .running, let current else { return }
        let next = index + 1
        let leavesExercise = !segments.indices.contains(next) || segments[next].exerciseIndex != current.exerciseIndex
        if leavesExercise { completedExercises.insert(current.exerciseIndex) }
        go(to: next)
    }

    /// Skips what is left of the current exercise.
    func nextExercise() {
        guard state == .running, let current else { return }
        if let target = firstSegmentIndex(ofExercise: current.exerciseIndex + 1) {
            go(to: target)
        } else {
            finish()
        }
    }

    /// Restarts the current exercise, or goes back to the previous one when it has barely begun.
    func previousExercise() {
        guard state == .running, let current else { return }
        let first = firstSegmentIndex(ofExercise: current.exerciseIndex) ?? 0
        if index == first, current.exerciseIndex > 0,
           let target = firstSegmentIndex(ofExercise: current.exerciseIndex - 1) {
            go(to: target)
        } else {
            go(to: first)
        }
    }

    /// Stops the clock for good (flow closed).
    func shutdown() {
        stopTimer()
        if state == .running { pause() }
    }

    // MARK: Internals

    private func go(to newIndex: Int) {
        guard segments.indices.contains(newIndex) else {
            finish()
            return
        }
        let date = Date()
        index = newIndex
        segmentBanked = 0
        segmentResumedAt = state == .running ? date : nil
        halfwaySaid = false
        lastCountdown = 0
        now = date
        onEvent?(.segmentStarted(segments[newIndex]))
    }

    private func finish() {
        guard state == .running || state == .paused else { return }
        let date = Date()
        if let totalResumedAt { totalBanked += date.timeIntervalSince(totalResumedAt) }
        totalResumedAt = nil
        segmentResumedAt = nil
        now = date
        state = .finished
        stopTimer()
        onEvent?(.finished)
    }

    private func startTimer() {
        stopTimer()
        let newTimer = Timer(timeInterval: 0.2, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.tick()
            }
        }
        RunLoop.main.add(newTimer, forMode: .common)
        timer = newTimer
    }

    private func stopTimer() {
        timer?.invalidate()
        timer = nil
    }

    private func tick() {
        guard state == .running else { return }
        now = Date()
        guard let current, let duration = current.durationSec else { return }
        let remaining = Double(duration) - segmentElapsed
        if remaining <= 0 {
            completeSegment()
            return
        }
        guard current.kind == .work else { return }
        if !halfwaySaid, duration >= 20, remaining > 5, segmentElapsed >= Double(duration) / 2 {
            halfwaySaid = true
            onEvent?(.halfway)
        }
        let secondsLeft = Int(remaining.rounded(.up))
        if secondsLeft <= 3, secondsLeft >= 1, secondsLeft != lastCountdown {
            lastCountdown = secondsLeft
            onEvent?(.countdown(secondsLeft))
        }
    }
}
