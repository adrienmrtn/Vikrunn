import Foundation
import CoreLocation

/// Asks the "when in use" location permission outside of a run (used by the onboarding).
final class LocationPermission {
    static let shared = LocationPermission()

    private var manager: CLLocationManager?

    private init() {}

    func request() {
        DispatchQueue.main.async {
            if self.manager == nil { self.manager = CLLocationManager() }
            self.manager?.requestWhenInUseAuthorization()
        }
    }
}
