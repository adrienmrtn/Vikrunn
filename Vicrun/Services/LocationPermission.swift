import Foundation
import CoreLocation

/// Asks the "when in use" location permission outside of a run (used by the onboarding)
/// and publishes the current answer, so screens can show what was really decided.
final class LocationPermission: NSObject, ObservableObject, CLLocationManagerDelegate {
    static let shared = LocationPermission()

    @Published private(set) var status: CLAuthorizationStatus = .notDetermined

    private var manager: CLLocationManager?

    private override init() {
        super.init()
    }

    var isAllowed: Bool {
        status == .authorizedWhenInUse || status == .authorizedAlways
    }

    var isRefused: Bool {
        status == .denied || status == .restricted
    }

    /// Reads the current permission without asking anything.
    func refresh() {
        DispatchQueue.main.async {
            let manager = self.ensureManager()
            self.status = manager.authorizationStatus
        }
    }

    func request() {
        DispatchQueue.main.async {
            let manager = self.ensureManager()
            self.status = manager.authorizationStatus
            if manager.authorizationStatus == .notDetermined {
                manager.requestWhenInUseAuthorization()
            }
        }
    }

    /// Main thread only.
    private func ensureManager() -> CLLocationManager {
        if let manager { return manager }
        let created = CLLocationManager()
        created.delegate = self
        manager = created
        return created
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let newStatus: CLAuthorizationStatus = manager.authorizationStatus
        DispatchQueue.main.async {
            self.status = newStatus
        }
    }
}
