import SwiftUI

/// The screens of the shell that open with a staged entrance.
enum MainStagedScreen: Hashable {
    case today, plan, progress, profile
}

private struct MainStagedRevealedKey: EnvironmentKey {
    /// Visible by default, so a block used outside a staged screen simply shows.
    static let defaultValue: Bool = true
}

extension EnvironmentValues {
    /// The single flag driving the staged entrance of the blocks below it.
    var mainStagedRevealed: Bool {
        get { self[MainStagedRevealedKey.self] }
        set { self[MainStagedRevealedKey.self] = newValue }
    }
}

/// One block of a staged entrance: fades and rises in, a little after the block before it.
struct MainStagedAppear: ViewModifier {
    let index: Int
    @Environment(\.mainStagedRevealed) private var revealed
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        content
            .opacity(revealed ? 1 : 0)
            .offset(y: revealed || reduceMotion ? 0 : 16)
            .animation(animation, value: revealed)
    }

    private var animation: Animation {
        // Reduce Motion: a plain short fade, all blocks together, nothing moves.
        if reduceMotion { return .easeOut(duration: 0.2) }
        return .spring(response: 0.5, dampingFraction: 0.86).delay(Double(index) * 0.08)
    }
}

/// Plays the staged entrance of a screen once per appearance of the app shell.
/// The tabs are rebuilt on every switch, so the "already played" memory lives in `MainChrome`.
@MainActor
struct MainStagedEntrance: ViewModifier {
    let screen: MainStagedScreen
    @EnvironmentObject private var chrome: MainChrome
    @State private var revealed: Bool = false

    func body(content: Content) -> some View {
        content
            .environment(\.mainStagedRevealed, revealed || chrome.stagedScreens.contains(screen))
            .onAppear(perform: reveal)
    }

    private func reveal() {
        guard !revealed, !chrome.stagedScreens.contains(screen) else { return }
        Task { @MainActor in
            // Let the screen settle (launch, or the fade from the onboarding) before the first block moves.
            try? await Task.sleep(nanoseconds: 120_000_000)
            chrome.stagedScreens.insert(screen)
            revealed = true
        }
    }
}

extension View {
    /// Staged entrance of one block. `index` is its position in the sequence (80 ms apart).
    func mainStagedAppear(index: Int) -> some View {
        modifier(MainStagedAppear(index: index))
    }

    /// Put on the content of a screen whose blocks use `mainStagedAppear(index:)`.
    func mainStagedEntrance(_ screen: MainStagedScreen) -> some View {
        modifier(MainStagedEntrance(screen: screen))
    }
}
