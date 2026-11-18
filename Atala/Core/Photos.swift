import Foundation

/// Names of the running photos of the asset catalog, and which ones to show to whom.
enum AtalaPhotos {
    static let man = "hero_today_man"
    static let woman = "hero_today_woman"
    static let group = "photo_community"
    static let trio = "photo_trio"

    /// Photos for the moving strip of the paywall, chosen from the gender given in onboarding.
    static func strip(for gender: Gender?) -> [String] {
        switch gender {
        case .man: return [man, trio, group]
        case .woman: return [woman, trio]
        case .other, .undisclosed, nil: return [trio, woman, man, group]
        }
    }
}
