import SwiftUI

struct OnboardingReview: Identifiable {
    let id: Int
    let text: String
    let author: String
    /// Photo of the author in the asset catalog, shown with their agreement.
    let photo: String
}

// Reviews supplied by the team. They must come from real users: keep the wording they gave.
enum OnboardingSampleReviews {
    static let reviews: [OnboardingReview] = [
        OnboardingReview(id: 1,
                         text: "I finally ran 10 km under 45 minutes with Atala. It helped so much.",
                         author: "Marie", photo: AtalaPhotos.woman),
        OnboardingReview(id: 2,
                         text: "Atala helped me lose 10 kg in just a few months. I strongly recommend it.",
                         author: "Thomas", photo: AtalaPhotos.man),
        OnboardingReview(id: 3,
                         text: "A friend recommended Atala. I didn't know you could have a coach this good at this price.",
                         author: "Luis", photo: AtalaPhotos.trio),
    ]
}

/// Screen 23: what runners say.
struct OnboardingReviewsScreen: View {
    /// 1 photos and stars, then one stage per review, then the button.
    @State private var stage = 0

    private static var delays: [Double] {
        [0.15] + Array(repeating: 0.3, count: OnboardingSampleReviews.reviews.count) + [0.35]
    }

    var body: some View {
        OnboardingScaffold(title: "Runners love their Atala coach",
                           buttonsVisible: stage >= Self.delays.count) {
            ratingHeader
                .onboardingStaged(0, stage: stage)
            VStack(spacing: 12) {
                ForEach(Array(OnboardingSampleReviews.reviews.enumerated()), id: \.element.id) { index, review in
                    reviewCard(review)
                        .onboardingStaged(index + 1, stage: stage)
                }
            }
        }
        .onboardingStageSequence($stage, delays: Self.delays)
    }

    private func stars(size: CGFloat) -> some View {
        HStack(spacing: 3) {
            ForEach(0..<5, id: \.self) { _ in
                Image(systemName: "star.fill")
                    .font(.system(size: size))
                    .foregroundStyle(Theme.yellow)
            }
        }
        .accessibilityHidden(true)
    }

    /// Runners in motion above the reviews, five stars underneath.
    private var ratingHeader: some View {
        VStack(spacing: 14) {
            OnboardingPhotoStrip(names: AtalaPhotos.strip(for: nil), tileWidth: 104, height: 132, speed: 16)
                .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            stars(size: 20)
        }
        .frame(maxWidth: .infinity)
    }

    private func reviewCard(_ review: OnboardingReview) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            stars(size: 13)
            Text(review.text)
                .font(.system(size: 16))
                .foregroundStyle(Theme.ink)
                .lineSpacing(4)
                .fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 10) {
                Circle()
                    .fill(Theme.greenTint)
                    .frame(width: 44, height: 44)
                    .overlay(alignment: .top) {
                        Image(review.photo)
                            .resizable()
                            .scaledToFill()
                    }
                    .clipShape(Circle())
                    .accessibilityHidden(true)
                Text(review.author)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.body)
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 22)
        .accessibilityElement(children: .combine)
    }
}

#Preview("Reviews") {
    OnboardingReviewsScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}
