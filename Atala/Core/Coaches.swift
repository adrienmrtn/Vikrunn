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

/// Plays the transparent idle frames of a coach (coachN_000.webp ... coachN_120.webp, 24 frames per second).
/// Frames stay compressed in memory and are decoded one at a time off the main thread,
/// so the animation is smooth and light.
final class CoachFramePlayer: ObservableObject {
    @Published private(set) var image: UIImage?

    static let frameCount = 121
    static let framesPerSecond = 24.0

    private var coachID: Int?
    private var frames: [Data] = []
    private var timer: Timer?
    private var tick = 0
    private var decoding = false
    private var generation = 0
    private let queue = DispatchQueue(label: "vicrun.coach.frames", qos: .userInitiated)

    deinit { timer?.invalidate() }

    private static func url(_ coachID: Int, _ index: Int) -> URL? {
        Bundle.main.url(forResource: String(format: "coach%d_%03d", coachID, index), withExtension: "webp")
    }

    private static func decode(_ data: Data) -> UIImage? {
        guard let image = UIImage(data: data) else { return nil }
        return image.preparingForDisplay() ?? image
    }

    /// Shows the first frame of the coach, without animating.
    func showStill(coachID: Int) {
        if self.coachID == coachID, image != nil {
            // Same coach, already on screen: just freeze on the current frame.
            stop()
            return
        }
        stop()
        image = nil
        self.coachID = coachID
        generation += 1
        let current = generation
        queue.async { [weak self] in
            guard let url = Self.url(coachID, 0), let data = try? Data(contentsOf: url),
                  let still = Self.decode(data) else { return }
            DispatchQueue.main.async {
                guard let self, self.generation == current else { return }
                self.image = still
            }
        }
    }

    /// Starts (or restarts for another coach) the looping animation.
    func play(coachID: Int) {
        if self.coachID == coachID, timer != nil { return }
        stop()
        if self.coachID != coachID { image = nil }
        self.coachID = coachID
        generation += 1
        let current = generation
        queue.async { [weak self] in
            var loaded: [Data] = []
            for index in 0..<Self.frameCount {
                guard let url = Self.url(coachID, index), let data = try? Data(contentsOf: url) else { break }
                loaded.append(data)
            }
            let first = loaded.first.flatMap { Self.decode($0) }
            DispatchQueue.main.async {
                guard let self, self.generation == current else { return }
                self.frames = loaded
                if let first { self.image = first }
                self.startTimer()
            }
        }
    }

    func stop() {
        timer?.invalidate()
        timer = nil
        frames = []
        tick = 0
        decoding = false
        generation += 1
    }

    private func startTimer() {
        timer?.invalidate()
        guard frames.count > 1 else { return }
        let newTimer = Timer(timeInterval: 1.0 / Self.framesPerSecond, repeats: true) { [weak self] _ in
            self?.advance()
        }
        RunLoop.main.add(newTimer, forMode: .common)
        timer = newTimer
    }

    private func advance() {
        let count = frames.count
        guard count > 1, !decoding else { return }
        // Ping-pong: forward then backward, so the loop has no visible jump.
        let period = 2 * (count - 1)
        tick = (tick + 1) % period
        let index = tick < count ? tick : period - tick
        guard index >= 0, index < count else { return }
        let data = frames[index]
        let current = generation
        decoding = true
        queue.async { [weak self] in
            let decoded = Self.decode(data)
            DispatchQueue.main.async {
                guard let self, self.generation == current else { return }
                self.decoding = false
                if let decoded { self.image = decoded }
            }
        }
    }
}

/// Full-body coach, breathing and waving in a loop when `animated` is true.
struct CoachAnimationView: View {
    let coach: Coach
    var animated: Bool = true
    @StateObject private var player = CoachFramePlayer()

    var body: some View {
        Group {
            if let image = player.image {
                Image(uiImage: image)
                    .resizable()
                    .interpolation(.high)
                    .scaledToFit()
            } else {
                Image(coach.fullAsset).resizable().scaledToFit().opacity(0.001)
            }
        }
        .accessibilityLabel(coach.name)
        .onAppear { refresh() }
        .onChange(of: animated) { _, _ in refresh() }
        .onChange(of: coach.id) { _, _ in refresh() }
        .onDisappear { player.stop() }
    }

    private func refresh() {
        if animated {
            player.play(coachID: coach.id)
        } else {
            player.showStill(coachID: coach.id)
        }
    }
}
