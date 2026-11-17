import Foundation
import CoreLocation

// MARK: - Location delegate

extension RunTracker: CLLocationManagerDelegate {
    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let fixes: [RunFix] = locations.map { location in
            RunFix(lat: location.coordinate.latitude,
                   lon: location.coordinate.longitude,
                   accuracy: location.horizontalAccuracy,
                   timestamp: location.timestamp)
        }
        Task { @MainActor [weak self] in
            self?.ingest(fixes)
        }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status: CLAuthorizationStatus = manager.authorizationStatus
        Task { @MainActor [weak self] in
            self?.authorizationChanged(status)
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // Temporary failures are expected (no fix yet); the signal row already reflects them.
    }
}

// MARK: - Fixes, distance and pace

extension RunTracker {
    /// Simulator only: synthetic progress when no location arrives.
    static var simulationAllowed: Bool {
        #if targetEnvironment(simulator)
        return true
        #else
        return false
        #endif
    }

    func enableBackgroundUpdates(_ enabled: Bool) {
        manager.allowsBackgroundLocationUpdates = enabled
        manager.showsBackgroundLocationIndicator = enabled
    }

    func refreshAuthorization(_ status: CLAuthorizationStatus) {
        switch status {
        case .denied, .restricted:
            if !isSimulating { gps = .denied }
        case .notDetermined:
            if gps == .denied { gps = .unknown }
        default:
            if gps == .denied { gps = .unknown }
        }
    }

    func authorizationChanged(_ status: CLAuthorizationStatus) {
        refreshAuthorization(status)
        if status == .authorizedWhenInUse || status == .authorizedAlways {
            if state != .finished { manager.startUpdatingLocation() }
        }
    }

    func ingest(_ fixes: [RunFix]) {
        guard state != .finished else { return }
        for fix in fixes { ingest(fix) }
        refreshProgress()
    }

    private func ingest(_ fix: RunFix) {
        // Ignore invalid and cached fixes.
        guard fix.accuracy >= 0, abs(fix.timestamp.timeIntervalSinceNow) < 15 else { return }
        lastFixDate = Date()
        isSimulating = false
        accuracy = fix.accuracy
        gps = Self.quality(for: fix.accuracy)

        guard state == .running, fix.accuracy <= 30 else { return }
        let location = CLLocation(latitude: fix.lat, longitude: fix.lon)
        guard let previous = lastPoint, let previousTime = lastPointTime else {
            // First point after a start or a resume: nothing to measure yet.
            lastPoint = location
            lastPointTime = fix.timestamp
            appendRoute(location, time: fix.timestamp, force: true)
            return
        }
        let seconds = fix.timestamp.timeIntervalSince(previousTime)
        guard seconds > 0 else { return }
        let metres = location.distance(from: previous)
        // Standing still: GPS jitter must not add distance.
        guard metres >= 1.5 else { return }
        // Faster than 9 m/s (about 1:50 per km) is a GPS jump, not a runner.
        guard metres / seconds <= 9 else { return }
        lastPoint = location
        lastPointTime = fix.timestamp
        addDistance(metres, at: location, time: fix.timestamp)
    }

    static func quality(for accuracy: Double) -> RunGPSQuality {
        if accuracy <= 12 { return .good }
        if accuracy <= 30 { return .fair }
        return .poor
    }

    /// Marks the signal as lost when nothing arrived for a while.
    func refreshSignalAge() {
        guard !isSimulating, gps != .denied, gps != .unknown else { return }
        if let lastFixDate, Date().timeIntervalSince(lastFixDate) > 12 {
            gps = .unknown
            accuracy = nil
        }
    }

    private func addDistance(_ metres: Double, at location: CLLocation, time: Date) {
        distance += metres
        paceSamples.append((time: activeTime, distance: distance))
        updatePace()
        appendRoute(location, time: time, force: false)
    }

    /// Pace over the last ~14 seconds of running.
    private func updatePace() {
        let now = activeTime
        paceSamples.removeAll { now - $0.time > 14 }
        guard let first = paceSamples.first, let last = paceSamples.last else { return }
        let seconds = last.time - first.time
        let metres = last.distance - first.distance
        guard seconds >= 3, metres >= 4 else { return }
        let pace = seconds / metres * 1000
        // Slower than 20:00 per km is walking around, not a pace worth showing.
        let rounded = pace < 1200 ? Int(pace.rounded()) : 0
        if rounded != currentPace { currentPace = rounded }
    }

    /// Keeps one route point every ~8 metres so the saved run stays light.
    private func appendRoute(_ location: CLLocation, time: Date, force: Bool) {
        if !force, let lastRoutePoint, location.distance(from: lastRoutePoint) < 8 { return }
        lastRoutePoint = location
        route.append(RoutePoint(lat: location.coordinate.latitude,
                                lon: location.coordinate.longitude,
                                time: time))
    }
}

// MARK: - Simulator mode

extension RunTracker {
    /// In the simulator, pretends the signal is good when no location shows up.
    func scheduleSimulatedSignal() {
        guard Self.simulationAllowed else { return }
        Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            guard let self, self.lastFixDate == nil, self.state != .finished else { return }
            self.isSimulating = true
            self.gps = .good
            self.accuracy = 5
        }
    }

    /// Feeds synthetic progress (one call per second) when no real location arrives.
    func simulateIfNeeded() {
        guard Self.simulationAllowed, state == .running else { return }
        let now = Date()
        if let lastFixDate, now.timeIntervalSince(lastFixDate) < 3 {
            lastSimulationDate = nil
            return
        }
        guard let previous = lastSimulationDate else {
            lastSimulationDate = now
            isSimulating = true
            gps = .good
            accuracy = 5
            return
        }
        lastSimulationDate = now
        let seconds = min(2, now.timeIntervalSince(previous))
        guard seconds > 0 else { return }

        let pace = Double(simulatedPace()) * Double.random(in: 0.97...1.03)
        let metres = seconds * 1000 / max(120, pace)

        // Runs around a 250 m circle so the summary has a route to draw.
        let radius = 250.0
        let angle = (distance + metres) / radius
        let baseLat = 48.8606
        let baseLon = 2.3376
        let lat = baseLat + radius * cos(angle) / 111_320
        let lon = baseLon + radius * sin(angle) / (111_320 * cos(baseLat * Double.pi / 180))
        addDistance(metres, at: CLLocation(latitude: lat, longitude: lon), time: now)
    }

    private func simulatedPace() -> Int {
        guard let step = currentStep else { return 345 }
        if let fast = step.paceFast, let slow = step.paceSlow { return (fast + slow) / 2 }
        switch step.kind {
        case .work: return 270
        case .recovery: return 420
        case .warmup, .cooldown: return 370
        default: return 345
        }
    }
}
