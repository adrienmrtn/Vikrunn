import SwiftUI

/// The list of languages, with the current one ticked. Used in onboarding and in the profile.
struct LanguageList: View {
    @Binding var selection: String

    var body: some View {
        VStack(spacing: 10) {
            ForEach(AppLanguage.all, id: \.code) { language in
                let selected = language.code == selection
                Button {
                    Haptics.tap()
                    selection = language.code
                } label: {
                    HStack(spacing: 12) {
                        Text(language.name)
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(Theme.ink)
                        Spacer(minLength: 8)
                        Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                            .font(.system(size: 22, weight: .semibold))
                            .foregroundStyle(selected ? Theme.green : Theme.controlBorder)
                    }
                    .padding(.horizontal, 16)
                    .frame(height: 56)
                    .background(selected ? Theme.greenTint : Color.white,
                                in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .stroke(selected ? Theme.green : Theme.controlBorder, lineWidth: 1.5)
                    )
                }
                .buttonStyle(.plain)
                .environment(\.layoutDirection, .leftToRight)
            }
        }
    }
}

/// Right after the welcome: checks the language before anything is asked. The screen itself
/// follows the pick straight away; the rest of the app does when the user continues.
struct OnboardingLanguageScreen: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var nav: OnboardingNavigator
    @State private var selection = AppLanguage.current

    var body: some View {
        OnboardingScaffold(title: L("First, let's make sure you have the right language"),
                           buttonTitle: L("Continue"),
                           onContinue: submit) {
            LanguageList(selection: $selection)
        }
        .onChange(of: selection) { _, code in
            AppLanguage.chosen = code
        }
    }

    private func submit() {
        AppLanguage.chosen = selection
        nav.next(store)
        LanguageModel.shared.refresh()
    }
}

#Preview("Language") {
    OnboardingLanguageScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}
