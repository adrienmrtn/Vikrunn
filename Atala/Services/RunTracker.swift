import Foundation
import CoreLocation
import SwiftUI

enum RunTrackerState: Equatable {
    case idle, running, paused, finished
}

enum RunGPSQuality: Equatable {
    case unknown, denied, poor, fair, good
}

/// What the tracker tells the voice coach.
enum RunTrackerEvent {
    case stepStarted(index: Int)
    case kilometre(Int)
    case paceTick
    case sessionComplete
}

/// A location reduced to plain values, safe to hand from the delegate queue to the main actor.
struct RunFix: Sendable {
    var lat: Double
    var lon: Double
    var accuracy: Double
    var timestamp: Date
    /// Metres above sea level, nil when the fix has no usable altitude.
    var altitude: Double?
}

/// Records a run: time, distance, pace, route and the steps of a structured session.
/// No auto-pause: only the runner pauses, resumes and stops.
/// The location delegate and the simulator mode live in `RunTracker+Location.swift`.
@MainActor
final class RunTracker: NSObject, ObservableObject {
    // Published state. Properties without `private(set)` are also written by the location extension.
    @Published private(set) var state: RunTrackerState = .idle
    /// Seconds of running, pauses excluded.
    @Published private(set) var elapsed: Int = 0
    /// Metres.
    @Published var distance: Double = 0
    /// Seconds per kilometre, smoothed over the last seconds. 0 = unknown.
    @Published var currentPace: Int = 0
    @Published var gps: RunGPSQuality = .unknown
    /// Horizontal accuracy of the last fix, in metres.
    @Published var accuracy: Double?
    @Published var route: [RoutePoint] = []
    @Published var isSimulating = false

    @Published private(set) var stepIndex: Int = 0
    @Published private(set) var stepElapsed: Int = 0
    @Published private(set) var stepDistance: Double = 0
    /// True once the last step of the session has been completed (the run goes on freely).
    @Published private(set) var stepsComplete = false

    private(set) var steps: [WorkoutStep] = []
    private(set) var splits: [RunSplit] = []
    private(set) var startDate: Date?

    var onEvent: (@MainActor (RunTrackerEvent) -> Void)?

    // Location bookkeeping (used by the extension).
    let manager = CLLocationManager()
    var lastPoint: CLLocation?
    var lastPointTime: Date?
    var lastFixDate: Date?
    var lastRoutePoint: CLLocation?
    var paceSamples: [(time: TimeInterval, distance: Double)] = []
    var lastSimulationDate: Date?

    // Clock.
    private var resumedAt: Date?
    private var accumulated: TimeInterval = 0
    private var timer: Timer?
    private var stepStartTime: TimeInterval = 0
    private var stepStartDistance: Double = 0
    private var lastKilometre = 0

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = kCLDistanceFilterNone
        manager.activityType = .fitness
        manager.pausesLocationUpdatesAutomatically = false
    }

    // MARK: Derived values

    /// Exact running time, computed from dates so it stays right in the background.
    var activeTime: TimeInterval {
        if let resumedAt { return accumulated + Date().timeIntervalSince(resumedAt) }
        return accumulated
    }

    var hasSteps: Bool { !steps.isEmpty }

    var currentStep: WorkoutStep? {
        guard !stepsComplete, steps.indices.contains(stepIndex) else { return nil }
        return steps[stepIndex]
    }

    var stepRemainingSeconds: Int? {
        guard let duration = currentStep?.durationSec else { return nil }
        return max(0, duration - stepElapsed)
    }

    var stepRemainingMeters: Double? {
        guard let meters = currentStep?.distanceM else { return nil }
        return max(0, Double(meters) - stepDistance)
    }

    /// Seconds per kilometre since the start. 0 = unknown.
    var averagePace: Int {
        guard distance >= 20, elapsed > 0 else { return 0 }
        return Int(Double(elapsed) / (distance / 1000))
    }

    var canSkipStep: Bool { hasSteps && !stepsComplete }

    private var cooldownIndex: Int? {
        guard !stepsComplete else { return nil }
        for index in steps.indices where index > stepIndex && steps[index].kind == .cooldown {
            return index
        }
        return nil
    }

    var canSkipToCooldown: Bool { cooldownIndex != nil }

    // MARK: Lifecycle

    /// Sets the session to follow. Nil = free run, no steps.
    func configure(workout: Workout?) {
        guard state == .idle else { return }
        let flat: [WorkoutStep] = workout?.flatSteps ?? []
        steps = flat.filter { $0.kind != .exercise }
    }

    /// Asks the permission and starts listening, so the ready screen can show the signal.
    func prepare() {
        refreshAuthorization(manager.authorizationStatus)
        if manager.authorizationStatus == .notDetermined {
            manager.requestWhenInUseAuthorization()
        }
        manager.startUpdatingLocation()
        scheduleSimulatedSignal()
    }

    func start() {
        guard state == .idle else { return }
        let now = Date()
        startDate = now
        resumedAt = now
        accumulated = 0
        lastPoint = nil
        lastPointTime = nil
        lastSimulationDate = nil
        state = .running
        enableBackgroundUpdates(true)
        manager.startUpdatingLocation()
        startTimer()
        if hasSteps { onEvent?(.stepStarted(index: 0)) }
    }

    func pause() {
        guard state == .running else { return }
        accumulated = activeTime
        resumedAt = nil
        state = .paused
        stopTimer()
        resetPace()
        refreshClock()
    }

    func resume() {
        guard state == .paused else { return }
        resumedAt = Date()
        lastPoint = nil
        lastPointTime = nil
        lastSimulationDate = nil
        state = .running
        startTimer()
    }

    /// Ends the run. Call `makeRecord` afterwards.
    func stop() {
        guard state == .running || state == .paused else { return }
        accumulated = activeTime
        resumedAt = nil
        refreshClock()
        if hasSteps && !stepsComplete { closeCurrentStep() }
        state = .finished
        shutdown()
    }

    /// Stops the timer and the location updates (also used when the ready screen is closed).
    func shutdown() {
        stopTimer()
        manager.stopUpdatingLocation()
        enableBackgroundUpdates(false)
    }

    // MARK: Steps

    func skipStep() {
        guard canSkipStep, state == .running || state == .paused else { return }
        advance(to: stepIndex + 1)
    }

    func skipToCooldown() {
        guard let index = cooldownIndex, state == .running || state == .paused else { return }
        advance(to: index)
    }

    private func advance(to next: Int) {
        closeCurrentStep()
        stepStartTime = activeTime
        stepStartDistance = distance
        stepElapsed = 0
        stepDistance = 0
        if next >= steps.count {
            stepsComplete = true
            onEvent?(.sessionComplete)
        } else {
            stepIndex = next
            onEvent?(.stepStarted(index: next))
        }
    }

    /// Records the current step as it was actually run.
    private func closeCurrentStep() {
        guard steps.indices.contains(stepIndex) else { return }
        let step = steps[stepIndex]
        let seconds = Int((activeTime - stepStartTime).rounded())
        guard seconds >= 1 else { return }
        splits.append(RunSplit(label: step.label, kind: step.kind,
                               distanceM: max(0, distance - stepStartDistance),
                               durationSec: seconds,
                               paceFast: step.paceFast, paceSlow: step.paceSlow))
    }

    private func checkStepCompletion() {
        guard let step = currentStep else { return }
        var reached = false
        if let duration = step.durationSec, activeTime - stepStartTime >= Double(duration) { reached = true }
        if let meters = step.distanceM, distance - stepStartDistance >= Double(meters) { reached = true }
        if reached { advance(to: stepIndex + 1) }
    }

    // MARK: Clock

    private func startTimer() {
        stopTimer()
        let newTimer = Timer(timeInterval: 1, repeats: true) { [weak self] _ in
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
        simulateIfNeeded()
        refreshSignalAge()
        refreshProgress()
        onEvent?(.paceTick)
    }

    private func refreshClock() {
        let now = activeTime
        let seconds = Int(now)
        if seconds != elapsed { elapsed = seconds }
        let inStep = max(0, Int(now - stepStartTime))
        if inStep != stepElapsed { stepElapsed = inStep }
        let metres = max(0, distance - stepStartDistance)
        if metres != stepDistance { stepDistance = metres }
    }

    /// Updates the clock, the step and the kilometre counter. Called every second and after each fix.
    func refreshProgress() {
        guard state == .running else { return }
        refreshClock()
        checkStepCompletion()
        let kilometre = Int(distance / 1000)
        if kilometre > lastKilometre {
            lastKilometre = kilometre
            onEvent?(.kilometre(kilometre))
        }
        if let last = paceSamples.last, activeTime - last.time > 8, currentPace != 0 {
            currentPace = 0
        }
    }

    private func resetPace() {
        paceSamples.removeAll()
        if currentPace != 0 { currentPace = 0 }
    }

    // MARK: Result

    /// Builds the record of the finished run.
    func makeRecord(title: String, workoutID: UUID?, feeling: Feeling? = nil) -> RunRecord {
        RunRecord(workoutID: workoutID,
                  title: title,
                  date: startDate ?? Date(),
                  source: .gps,
                  distanceM: distance,
                  durationSec: Int(accumulated.rounded()),
                  avgHeartRate: nil,
                  route: route,
                  splits: splits,
                  feeling: feeling)
    }
}
