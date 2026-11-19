import SwiftUI

struct ProgressTabBest: Identifiable {
    let id: String
    let title: String
    let value: String
    let detail: String
}

/// Personal bests derived from the saved runs.
enum ProgressTabStats {
    static func bests(_ runs: [RunRecord]) -> [ProgressTabBest] {
        var result: [ProgressTabBest] = []
        let valid = runs.filter { $0.distanceM > 0 && $0.durationSec > 0 }

        if let longest = valid.max(by: { $0.distanceM < $1.distanceM }) {
            result.append(ProgressTabBest(id: "longest", title: L("Longest run"),
                                          value: Fmt.km(longest.distanceKm),
                                          detail: dateText(longest)))
        }
        if let fastest = valid.filter({ $0.distanceM >= 1000 }).min(by: { $0.avgPace < $1.avgPace }) {
            result.append(ProgressTabBest(id: "pace", title: L("Fastest average pace"),
                                          value: "\(Fmt.pace(fastest.avgPace))/km",
                                          detail: "\(Fmt.km(fastest.distanceKm)) · \(dateText(fastest))"))
        }
        let distances: [(String, String, Double)] = [
            ("5k", L("Best 5 km"), 5),
            ("10k", L("Best 10 km"), 10),
            ("half", L("Best half marathon"), 21.0975),
        ]
        for (id, title, km) in distances {
            // A run slightly short of the distance (GPS drift) still counts.
            let candidates = valid.filter { $0.distanceKm >= km * 0.99 }
            if let best = candidates.min(by: { $0.avgPace < $1.avgPace }) {
                let seconds = Int((Double(best.durationSec) / best.distanceKm * km).rounded())
                result.append(ProgressTabBest(id: id, title: title,
                                              value: Fmt.duration(seconds),
                                              detail: L("At the pace of your %@ run · %@", Fmt.km(best.distanceKm), dateText(best))))
            }
        }
        if let longestTime = valid.max(by: { $0.durationSec < $1.durationSec }) {
            result.append(ProgressTabBest(id: "time", title: L("Longest time on your feet"),
                                          value: Fmt.duration(longestTime.durationSec),
                                          detail: dateText(longestTime)))
        }
        return result
    }

    static func dateText(_ run: RunRecord) -> String {
        Fmt.day(run.date, "d MMM yyyy")
    }

    static func sourceSymbol(_ source: RunSource) -> String {
        switch source {
        case .gps: return "location.fill"
        case .manual: return "square.and.pencil"
        case .health: return "heart.fill"
        }
    }

    static func sourceTitle(_ source: RunSource) -> String {
        switch source {
        case .gps: return "GPS"
        case .manual: return L("Added by hand")
        case .health: return "Apple Health"
        }
    }
}

/// Personal bests, one line each.
struct ProgressTabBestsList: View {
    let bests: [ProgressTabBest]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            MainSectionTitle(title: L("Personal bests"))
                .padding(.bottom, 4)
            if bests.isEmpty {
                Text(L("Your bests will appear here after your first run."))
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.muted)
                    .padding(.vertical, 10)
            }
            ForEach(bests) { best in
                HStack(spacing: 8) {
                    VStack(alignment: .leading, spacing: 1) {
                        Text(best.title)
                            .font(.system(size: 15))
                            .foregroundStyle(Theme.ink)
                        Text(best.detail)
                            .font(.system(size: 12))
                            .foregroundStyle(Theme.muted)
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                    }
                    Spacer(minLength: 8)
                    Text(best.value)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Theme.ink)
                }
                .frame(minHeight: 50)
                .overlay(alignment: .bottom) {
                    Rectangle().fill(Theme.hairline).frame(height: 1)
                }
                .accessibilityElement(children: .combine)
            }
        }
    }
}
