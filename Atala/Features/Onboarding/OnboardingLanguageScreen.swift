import SwiftUI

/// The list of languages, each with its flag, the current one ticked. Used in onboarding and in
/// the profile.
struct LanguageList: View {
    @Binding var selection: String

    var body: some View {
        VStack(spacing: 10) {
            ForEach(AppLanguage.all, id: \.code) { language in
                LanguageRow(flag: language.flag,
                            name: language.name,
                            selected: language.code == selection,
                            action: {
                                Haptics.select()
                                selection = language.code
                            })
            }
        }
    }
}

/// One language: flag, name in that language, tick. The tick and the tint ease in and out.
private struct LanguageRow: View {
    let flag: String
    let name: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Text(flag)
                    .font(.system(size: 26))
                    .frame(width: 34)
                    .accessibilityHidden(true)
                Text(name)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                Spacer(minLength: 8)
                tick
            }
            .padding(.horizontal, 16)
            .frame(height: 56)
            .background(selected ? Theme.greenTint : Color.white,
                        in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(selected ? Theme.green : Theme.controlBorder, lineWidth: 1.5)
            )
            .contentShape(Rectangle())
            .animation(.easeInOut(duration: 0.22), value: selected)
        }
        .buttonStyle(.plain)
        .environment(\.layoutDirection, .leftToRight)
        .accessibilityLabel(name)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    /// Both marks stay in place: the empty circle fades while the tick grows into it.
    private var tick: some View {
        ZStack {
            Image(systemName: "circle")
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(Theme.controlBorder)
                .opacity(selected ? 0 : 1)
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(Theme.green)
                .scaleEffect(selected ? 1 : 0.6)
                .opacity(selected ? 1 : 0)
        }
        .animation(.spring(response: 0.3, dampingFraction: 0.7), value: selected)
        .accessibilityHidden(true)
    }
}

/// Right after the welcome: checks the language before anything is asked. The screen itself
/// follows the pick straight away (its title and button cross-fade into the new language); the
/// rest of the app does when the user continues.
struct OnboardingLanguageScreen: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var nav: OnboardingNavigator
    @State private var selection = AppLanguage.current

    /// The language is switched before the screen redraws, so its texts are read in the new one,
    /// and the redraw is animated: the texts cross-fade instead of snapping.
    private var picked: Binding<String> {
        Binding(
            get: { selection },
            set: { code in
                guard code != selection else { return }
                AppLanguage.chosen = code
                withAnimation(.easeInOut(duration: 0.25)) {
                    selection = code
                }
            }
        )
    }

    var body: some View {
        OnboardingScaffold(title: L("First, let's make sure you have the right language"),
                           buttonTitle: L("Continue"),
                           onContinue: submit) {
            LanguageList(selection: picked)
        }
    }

    private func submit() {
        AppLanguage.chosen = selection
        let changes: Bool = LanguageModel.shared.code != AppLanguage.current
        if changes, nav.saveNext(store) {
            // The app is rebuilt in the new language and the rebuilt flow opens on the next
            // screen: one cross-fade, from this screen straight to the first name.
            LanguageModel.shared.refresh()
        } else {
            nav.next(store)
        }
    }
}

#Preview("Language") {
    OnboardingLanguageScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}
