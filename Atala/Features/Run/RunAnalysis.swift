import Foundation
import CoreLocation

/// One kilometre of a run (the last one can be shorter).
struct RunKmSplit: Identifiable, Equatable {
    /// 1 for the first kilometre.
    let id: Int
    let distanceM: Double
    let seconds: Double

    var pace: Int { distanceM > 0 ? Int((seconds / (distanceM / 1000)).rounded()) : 0 }
    var isPartial: Bool { distanceM < 995 }
}

/// Altitude at a point of the run, for the elevation profile.
struct RunElevationPoint: Identifiable, Equatable {
    let id: Int
    let distanceM: Double
    let altitudeM: Double
}

/// Figures worked out from the recorded route: kilometre splits and elevation.
/// Everything is empty for runs without a route (entered by hand, imported from Health).
struct RunAnalysis {
    let kmSplits: [RunKmSplit]
    let elevation: [RunElevationPoint]
    /// Total climb in metres, nil when the route carries no altitude.
    let elevationGainM: Int?

    init(run: RunRecord) {
        let legs = Self.legs(of: run)
        kmSplits = Self.kmSplits(from: legs)
        let profile = Self.profile(of: run, legs: legs)
        elevation = Self.thinned(profile, to: 80)
        elevationGainM = profile.count >= 5 ? Self.gain(of: profile.map { $0.altitudeM }) : nil
    }

    var fastestSplit: RunKmSplit? {
        kmSplits.filter { !$0.isPartial && $0.pace > 0 }.min { $0.pace < $1.pace }
    }

    // MARK: Legs

    /// Distance and moving time between two consecutive route points, scaled so that their totals
    /// match the distance and duration of the run.
    private struct Leg {
        var metres: Double
        var seconds: Double
    }

    private static func legs(of run: RunRecord) -> [Leg] {
        let route = run.route
        guard route.count >= 2, run.distanceM > 0, run.durationSec > 0 else { return [] }
        var legs: [Leg] = []
        for index in 1..<route.count {
            let from = CLLocation(latitude: route[index - 1].lat, longitude: route[index - 1].lon)
            let to = CLLocation(latitude: route[index].lat, longitude: route[index].lon)
            let metres = to.distance(from: from)
            let elapsed = max(0, route[index].time.timeIntervalSince(route[index - 1].time))
            // A long gap with little distance is a pause: it must not count as running time.
            let longest = max(10, metres / 0.8)
            legs.append(Leg(metres: metres, seconds: min(elapsed, longest)))
        }
        let totalMetres = legs.reduce(0) { $0 + $1.metres }
        let totalSeconds = legs.reduce(0) { $0 + $1.seconds }
        guard totalMetres > 0, totalSeconds > 0 else { return [] }
        let distanceScale = run.distanceM / totalMetres
        let timeScale = Double(run.durationSec) / totalSeconds
        return legs.map { Leg(metres: $0.metres * distanceScale, seconds: $0.seconds * timeScale) }
    }

    // MARK: Kilometre splits

    private static func kmSplits(from legs: [Leg]) -> [RunKmSplit] {
        var splits: [RunKmSplit] = []
        var metresDone = 0.0
        var secondsDone = 0.0
        for leg in legs {
            var metres = leg.metres
            var seconds = leg.seconds
            while metres > 0.001 {
                let room = 1000 - metresDone
                if metres >= room {
                    let share = room / metres
                    let used = seconds * share
                    splits.append(RunKmSplit(id: splits.count + 1, distanceM: 1000, seconds: secondsDone + used))
                    metres -= room
                    seconds -= used
                    metresDone = 0
                    secondsDone = 0
                } else {
                    metresDone += metres
                    secondsDone += seconds
                    metres = 0
                }
            }
        }
        if metresDone >= 100 {
            splits.append(RunKmSplit(id: splits.count + 1, distanceM: metresDone, seconds: secondsDone))
        }
        return splits
    }

    // MARK: Elevation

    /// Altitude along the run, smoothed: raw GPS altitude jumps by a few metres from one fix to the next.
    private static func profile(of run: RunRecord, legs: [Leg]) -> [RunElevationPoint] {
        let route = run.route
        guard legs.count == route.count - 1 else { return [] }
        var distances: [Double] = []
        var altitudes: [Double] = []
        var travelled = 0.0
        for index in route.indices {
            if index > 0 { travelled += legs[index - 1].metres }
            if let altitude = route[index].alt {
                distances.append(travelled)
                altitudes.append(altitude)
            }
        }
        guard altitudes.count >= 5 else { return [] }
        var points: [RunElevationPoint] = []
        for index in altitudes.indices {
            let low = max(0, index - 2)
            let high = min(altitudes.count - 1, index + 2)
            let window = altitudes[low...high]
            let mean = window.reduce(0, +) / Double(window.count)
            points.append(RunElevationPoint(id: index, distanceM: distances[index], altitudeM: mean))
        }
        return points
    }

    /// Sum of the climbs, ignoring wobbles under two metres.
    private static func gain(of altitudes: [Double]) -> Int {
        guard var reference = altitudes.first else { return 0 }
        var total = 0.0
        for altitude in altitudes {
            if altitude > reference + 2 {
                total += altitude - reference
                reference = altitude
            } else if altitude < reference - 2 {
                reference = altitude
            }
        }
        return Int(total.rounded())
    }

    private static func thinned(_ points: [RunElevationPoint], to limit: Int) -> [RunElevationPoint] {
        guard points.count > limit, limit > 1 else { return points }
        var result: [RunElevationPoint] = []
        let step = Double(points.count - 1) / Double(limit - 1)
        for index in 0..<limit {
            result.append(points[Int((Double(index) * step).rounded())])
        }
        return result
    }
}
