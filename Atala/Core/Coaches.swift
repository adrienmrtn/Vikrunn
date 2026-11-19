import SwiftUI
import UIKit

struct Coach: Identifiable, Hashable {
    let id: Int
    let name: String
    let trait: String

    var avatarAsset: String { "coach\(id)_avatar" }
    var fullAsset: String { "coach\(id)_full" }

    /// The coaches offered for now. Identifiers are kept as they were, so saved choices still match.
    static let all: [Coach] = [
        Coach(id: 1, name: "Awa", trait: "Sets the pace"),
        Coach(id: 3, name: "Mei", trait: "Lively and playful"),
        Coach(id: 4, name: "Karim", trait: "Steady and reassuring"),
    ]

    /// Coaches set aside for now (their pictures stay in the catalog). Someone who had chosen one
    /// of them gets the first available coach. Move a line back into `all` to bring a coach back.
    static let retired: [Coach] = [
        Coach(id: 2, name: "Thomas", trait: "Calm and precise"),
        Coach(id: 5, name: "Claire", trait: "Thirty years of running"),
        Coach(id: 6, name: "Idriss", trait: "Direct and motivating"),
    ]

    static func by(id: Int) -> Coach { all.first { $0.id == id } ?? all[0] }
}

/// The static poses a coach can take. `wave` is the original full-body picture.
enum CoachPose: String, CaseIterable {
    case wave, thumbsUp, present, cheer, crossed, run
}

extension Coach {
    /// Name of the catalog image of a pose: "coachN_full" for `wave`, "coachN_pose_..." otherwise.
    func poseAsset(_ pose: CoachPose) -> String {
        switch pose {
        case .wave: return fullAsset
        case .thumbsUp: return "coach\(id)_pose_thumbsup"
        case .present: return "coach\(id)_pose_present"
        case .cheer: return "coach\(id)_pose_cheer"
        case .crossed: return "coach\(id)_pose_crossed"
        case .run: return "coach\(id)_pose_run"
        }
    }

    /// Same, but falls back to the full-body picture when the pose image is not in the catalog.
    func availablePoseAsset(_ pose: CoachPose) -> String {
        let name = poseAsset(pose)
        if name == fullAsset { return name }
        return UIImage(named: name) == nil ? fullAsset : name
    }
}

/// Full-body coach, as a still picture in the given pose.
struct CoachPoseView: View {
    let coach: Coach
    let pose: CoachPose

    init(coach: Coach, pose: CoachPose = .wave) {
        self.coach = coach
        self.pose = pose
    }

    var body: some View {
        Image(coach.availablePoseAsset(pose))
            .resizable()
            .interpolation(.high)
            .scaledToFit()
            .accessibilityLabel(coach.name)
    }
}
