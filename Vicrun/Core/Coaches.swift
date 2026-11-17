import SwiftUI
import UIKit

struct Coach: Identifiable, Hashable {
    let id: Int
    let name: String
    let trait: String

    var avatarAsset: String { "coach\(id)_avatar" }
    var fullAsset: String { "coach\(id)_full" }

    static let all: [Coach] = [
        Coach(id: 1, name: "Awa", trait: "Sets the pace"),
        Coach(id: 2, name: "Thomas", trait: "Calm and precise"),
        Coach(id: 3, name: "Mei", trait: "Lively and playful"),
        Coach(id: 4, name: "Karim", trait: "Steady and reassuring"),
        Coach(id: 5, name: "Claire", trait: "Thirty years of running"),
        Coach(id: 6, name: "Idriss", trait: "Direct and motivating"),
    ]

    static func by(id: Int) -> Coach { all.first { $0.id == id } ?? all[0] }
}

/// Loads the 61 transparent idle frames of a coach (coachN_000.webp ... coachN_060.webp).
final class CoachFrameLoader: ObservableObject {
    @Published var frames: [UIImage] = []
    @Published var still: UIImage?
    private var loadedID: Int?
    static let frameCount = 61

    static func frame(_ coachID: Int, _ index: Int) -> UIImage? {
        let name = String(format: "coach%d_%03d", coachID, index)
        guard let url = Bundle.main.url(forResource: name, withExtension: "webp") else { return nil }
        return UIImage(contentsOfFile: url.path)
    }

    func loadStill(coachID: Int) {
        if still == nil { still = Self.frame(coachID, 0) }
    }

    func load(coachID: Int) {
        guard loadedID != coachID else { return }
        loadedID = coachID
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            var images: [UIImage] = []
            for i in 0..<Self.frameCount {
                if let image = Self.frame(coachID, i) { images.append(image) }
            }
            DispatchQueue.main.async {
                guard let self, self.loadedID == coachID else { return }
                self.frames = images
            }
        }
    }

    func unload() {
        loadedID = nil
        frames = []
    }
}

/// Full-body coach, breathing and waving in a ping-pong loop when `animated` is true.
struct CoachAnimationView: View {
    let coach: Coach
    var animated: Bool = true
    @StateObject private var loader = CoachFrameLoader()

    var body: some View {
        Group {
            if animated, loader.frames.count > 1 {
                TimelineView(.periodic(from: .now, by: 1.0 / 12.0)) { context in
                    Image(uiImage: loader.frames[frameIndex(at: context.date)])
                        .resizable()
                        .scaledToFit()
                }
            } else if let still = loader.still {
                Image(uiImage: still).resizable().scaledToFit()
            } else {
                Image(coach.fullAsset).resizable().scaledToFit()
            }
        }
        .accessibilityLabel(coach.name)
        .onAppear {
            loader.loadStill(coachID: coach.id)
            if animated { loader.load(coachID: coach.id) }
        }
        .onChange(of: animated) { _, isAnimated in
            if isAnimated { loader.load(coachID: coach.id) } else { loader.unload() }
        }
        .onDisappear { loader.unload() }
    }

    private func frameIndex(at date: Date) -> Int {
        let count = loader.frames.count
        guard count > 1 else { return 0 }
        let period = 2 * (count - 1)
        let tick = Int(date.timeIntervalSinceReferenceDate * 12) % period
        return tick < count ? tick : period - tick
    }
}
