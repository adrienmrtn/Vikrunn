import SwiftUI
import UIKit

private struct OnboardingWelcomeSlide: Identifiable {
    let id: Int
    /// Illustration shown until the photo is in the asset catalog.
    let illustration: String
    /// Photo of the slide ("welcome_photo_1" ... "_3"). Used as soon as it exists in the catalog.
    let photo: String
    let title: String
    let text: String

    var usesPhoto: Bool {
        UIImage(named: photo) != nil
    }

    static let all: [OnboardingWelcomeSlide] = [
        OnboardingWelcomeSlide(id: 0, illustration: "welcome_1", photo: "welcome_photo_1",
                               title: "Run like you belong",
                               text: "Atala is the coach behind runners who show up, from a first 5K to a half marathon."),
        OnboardingWelcomeSlide(id: 1, illustration: "welcome_2", photo: "welcome_photo_2",
                               title: "A plan that fits your life",
                               text: "Pick the days you can run. Your plan is built around them."),
        OnboardingWelcomeSlide(id: 2, illustration: "welcome_3", photo: "welcome_photo_3",
                               title: "A coach who tells you why",
                               text: "Every session has its reason, every question its answer."),
    ]
}

/// Screen 1: three swipeable slides, then "Get started" or "I already have an account".
struct OnboardingWelcomeScreen: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var nav: OnboardingNavigator
    @State private var page: Int? = 0
    @State private var showSignIn = false
    @State private var isRestoring = false
    @State private var signInError: String?

    var body: some View {
        GeometryReader { geo in
            VStack(spacing: 0) {
                pager(size: geo.size)
                dots
                    .padding(.horizontal, 24)
                    .padding(.top, 4)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Spacer(minLength: 8)
                buttons
            }
        }
        .overlay(alignment: .topLeading) {
            BrandLogo(style: .full, height: 26)
                .foregroundStyle(Theme.cream)
                .shadow(color: .black.opacity(0.18), radius: 6, x: 0, y: 2)
                .padding(.leading, 24)
                .padding(.top, 66)
                .allowsHitTesting(false)
        }
        .ignoresSafeArea(edges: .top)
        .background(Color.white.ignoresSafeArea())
        .sheet(isPresented: $showSignIn) {
            SignInOptionsSheet { account in
                showSignIn = false
                finishSignIn(account)
            }
        }
        .alert("We couldn't open your account", isPresented: signInErrorShown) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(signInError ?? "Please try again.")
        }
    }

    private var signInErrorShown: Binding<Bool> {
        Binding(
            get: { signInError != nil },
            set: { shown in
                if !shown { signInError = nil }
            }
        )
    }

    /// Returning user: everything saved in the account comes back and, when a plan is there, the app
    /// opens straight away. A new account simply carries on with the questions.
    private func finishSignIn(_ account: Account) {
        guard !isRestoring else { return }
        isRestoring = true
        Task { @MainActor in
            let outcome = await AuthSignInFlow.complete(account, store: store, policy: .preferAccount)
            isRestoring = false
            switch outcome {
            case .restored, .needsChoice:
                break // the root view switches to the app
            case .continued:
                nav.go(to: .firstName, forward: true, store: store)
            case .failed(let failure):
                signInError = failure.userMessage
            }
        }
    }

    private func pager(size: CGSize) -> some View {
        let imageHeight = max(220, size.height * 0.52)
        return ScrollView(.horizontal) {
            HStack(spacing: 0) {
                ForEach(OnboardingWelcomeSlide.all) { slide in
                    slideView(slide, width: size.width, imageHeight: imageHeight)
                }
            }
            .scrollTargetLayout()
        }
        .scrollTargetBehavior(.paging)
        .scrollPosition(id: $page)
        .scrollIndicators(.hidden)
        .frame(height: imageHeight + 150)
    }

    private func slideView(_ slide: OnboardingWelcomeSlide, width: CGFloat, imageHeight: CGFloat) -> some View {
        let usesPhoto = slide.usesPhoto
        return VStack(alignment: .leading, spacing: 0) {
            Image(usesPhoto ? slide.photo : slide.illustration)
                .resizable()
                .scaledToFill()
                .frame(width: width, height: imageHeight)
                .overlay(alignment: .top) {
                    // Photos only: a soft dark veil at the top keeps the cream logo legible.
                    if usesPhoto {
                        LinearGradient(colors: [Color.black.opacity(0.42), Color.black.opacity(0)],
                                       startPoint: .top, endPoint: .bottom)
                            .frame(height: min(190, imageHeight * 0.5))
                    }
                }
                .clipShape(UnevenRoundedRectangle(bottomLeadingRadius: 32, bottomTrailingRadius: 32))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 10) {
                Text(slide.title)
                    .font(Theme.title(28))
                    .foregroundStyle(Theme.ink)
                    .accessibilityAddTraits(.isHeader)
                Text(slide.text)
                    .font(.system(size: 16))
                    .foregroundStyle(Color(hex: 0x5A5A5A))
                    .lineSpacing(4)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.horizontal, 24)
            .padding(.top, 26)
            .frame(width: width, alignment: .leading)
            Spacer(minLength: 0)
        }
        .frame(width: width, height: imageHeight + 150, alignment: .top)
    }

    private var dots: some View {
        HStack(spacing: 6) {
            ForEach(OnboardingWelcomeSlide.all) { slide in
                Capsule()
                    .fill(slide.id == (page ?? 0) ? Theme.ink : Color(hex: 0xD0D0D0))
                    .frame(width: slide.id == (page ?? 0) ? 24 : 8, height: 8)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: page)
        .accessibilityElement()
        .accessibilityLabel("Slide \((page ?? 0) + 1) of \(OnboardingWelcomeSlide.all.count)")
    }

    private var buttons: some View {
        VStack(spacing: 4) {
            PrimaryButton(title: "Get started") {
                nav.next(store)
            }
            Button("I already have an account") {
                showSignIn = true
            }
            .font(.system(size: 15, weight: .semibold))
            .foregroundStyle(Theme.ink)
            .frame(maxWidth: .infinity)
            .frame(height: 48)
        }
        .padding(.horizontal, Theme.screenPadding)
        .padding(.bottom, 8)
    }
}

/// Screen 2: first name.
struct OnboardingFirstNameScreen: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var nav: OnboardingNavigator
    @FocusState private var focused: Bool

    private var trimmed: String {
        store.answers.firstName.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        OnboardingScaffold(title: "What's your first name?",
                           canContinue: !trimmed.isEmpty,
                           onContinue: submit) {
            VStack(alignment: .leading, spacing: 8) {
                TextField("First name", text: $store.answers.firstName)
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                    .textContentType(.givenName)
                    .textInputAutocapitalization(.words)
                    .autocorrectionDisabled()
                    .submitLabel(.continue)
                    .focused($focused)
                    .onSubmit(submit)
                    .padding(.horizontal, 16)
                    .frame(height: 58)
                    .background(Color.white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .stroke(focused ? Theme.ink : Theme.controlBorder, lineWidth: 1.5)
                    )
            }
        }
        .onAppear {
            if trimmed.isEmpty { focused = true }
        }
    }

    private func submit() {
        guard !trimmed.isEmpty else { return }
        store.answers.firstName = trimmed
        focused = false
        nav.next(store)
    }
}

#Preview("Welcome") {
    OnboardingWelcomeScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}

#Preview("First name") {
    OnboardingFirstNameScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}
