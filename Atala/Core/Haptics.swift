import SwiftUI
import UIKit

/// One place for every vibration of the app. Call the semantic entry points (`tap`, `select`,
/// `confirm`, ...) rather than a feedback generator, so the intensities stay consistent.
/// Safe to call from any thread: the work always happens on the main thread.
enum Haptics {
    enum Kind {
        /// Light impact: ordinary buttons, navigation, chips, rows.
        case tap
        /// Selection changed: option cards, toggles, pickers, tabs, carousel.
        case select
        /// Medium impact: primary call-to-action, pause and resume.
        case confirm
        /// Heavy impact: rare, for the moments that count (start, commit, unlock).
        case strong
        /// Soft impact: something arrived by itself (coach reply, text finished).
        case soft
        /// Rigid impact: a sharp marker (lap, next step).
        case rigid
        case success
        case warning
        case error
    }

    // MARK: Semantic entry points

    static func tap() { play(.tap) }
    static func select() { play(.select) }
    static func confirm() { play(.confirm) }
    static func strong() { play(.strong) }
    static func soft() { play(.soft) }
    static func rigid() { play(.rigid) }
    static func success() { play(.success) }
    static func warning() { play(.warning) }
    static func error() { play(.error) }

    static func play(_ kind: Kind) {
        if Thread.isMainThread {
            MainActor.assumeIsolated {
                fire(kind)
            }
        } else {
            Task { @MainActor in
                fire(kind)
            }
        }
    }

    /// Wakes the Taptic Engine just before a haptic whose timing matters (countdown, carousel).
    static func prepare(_ kind: Kind) {
        if Thread.isMainThread {
            MainActor.assumeIsolated {
                warmUp(kind)
            }
        } else {
            Task { @MainActor in
                warmUp(kind)
            }
        }
    }

    // MARK: Generators

    @MainActor private static let lightImpact = UIImpactFeedbackGenerator(style: .light)
    @MainActor private static let mediumImpact = UIImpactFeedbackGenerator(style: .medium)
    @MainActor private static let heavyImpact = UIImpactFeedbackGenerator(style: .heavy)
    @MainActor private static let softImpact = UIImpactFeedbackGenerator(style: .soft)
    @MainActor private static let rigidImpact = UIImpactFeedbackGenerator(style: .rigid)
    @MainActor private static let selection = UISelectionFeedbackGenerator()
    @MainActor private static let notification = UINotificationFeedbackGenerator()

    @MainActor
    private static func fire(_ kind: Kind) {
        switch kind {
        case .tap:
            lightImpact.impactOccurred()
            lightImpact.prepare()
        case .select:
            selection.selectionChanged()
            selection.prepare()
        case .confirm:
            mediumImpact.impactOccurred()
        case .strong:
            heavyImpact.impactOccurred()
        case .soft:
            softImpact.impactOccurred()
        case .rigid:
            rigidImpact.impactOccurred()
        case .success:
            notification.notificationOccurred(.success)
        case .warning:
            notification.notificationOccurred(.warning)
        case .error:
            notification.notificationOccurred(.error)
        }
    }

    @MainActor
    private static func warmUp(_ kind: Kind) {
        switch kind {
        case .tap: lightImpact.prepare()
        case .select: selection.prepare()
        case .confirm: mediumImpact.prepare()
        case .strong: heavyImpact.prepare()
        case .soft: softImpact.prepare()
        case .rigid: rigidImpact.prepare()
        case .success, .warning, .error: notification.prepare()
        }
    }
}

/// Plays a haptic when the control is pressed. Used by the shared button styles, so every button
/// that wears them vibrates without its caller doing anything.
struct HapticOnPress: ViewModifier {
    let isPressed: Bool
    let kind: Haptics.Kind?

    func body(content: Content) -> some View {
        content.onChange(of: isPressed) { _, pressed in
            if pressed, let kind {
                Haptics.play(kind)
            }
        }
    }
}
