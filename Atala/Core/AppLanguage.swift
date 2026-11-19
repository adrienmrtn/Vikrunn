import Foundation
import SwiftUI

/// The language Atala speaks: the one picked in the app, otherwise the phone's, otherwise English.
enum AppLanguage {
    /// Every language the app and the coach's recorded voice exist in, with its own name.
    static let all: [(code: String, name: String)] = [
        ("en", "English"), ("fr", "Français"), ("es", "Español"), ("de", "Deutsch"), ("it", "Italiano"),
        ("pt-BR", "Português (Brasil)"), ("pt-PT", "Português (Portugal)"), ("nl", "Nederlands"),
        ("sv", "Svenska"), ("da", "Dansk"), ("no", "Norsk"), ("fi", "Suomi"), ("pl", "Polski"),
        ("cs", "Čeština"), ("sk", "Slovenčina"), ("hu", "Magyar"), ("ro", "Română"), ("hr", "Hrvatski"),
        ("sl", "Slovenščina"), ("el", "Ελληνικά"), ("tr", "Türkçe"), ("ru", "Русский"),
        ("ar", "العربية"), ("he", "עברית"), ("ja", "日本語")
    ]
    static let supported: [String] = all.map { $0.code }

    private static let key = "atala.language"
    private static let lock = NSLock()
    private static var cached: String?

    /// The language picked in the app, nil while the phone's language decides.
    static var chosen: String? {
        get {
            guard let code = UserDefaults.standard.string(forKey: key), supported.contains(code) else { return nil }
            return code
        }
        set {
            if let newValue, supported.contains(newValue) {
                UserDefaults.standard.set(newValue, forKey: key)
            } else {
                UserDefaults.standard.removeObject(forKey: key)
            }
            lock.lock()
            cached = nil
            lock.unlock()
        }
    }

    /// The language in use. Read very often (every translated string), so it is kept in memory.
    static var current: String {
        lock.lock()
        defer { lock.unlock() }
        if let cached { return cached }
        let code = chosen ?? resolve(Locale.preferredLanguages)
        cached = code
        return code
    }

    /// For dates, numbers and the synthesized voice.
    static var locale: Locale {
        Locale(identifier: localeIdentifier)
    }

    static var localeIdentifier: String {
        let code = current
        switch code {
        case "en": return "en_US"
        case "pt-BR": return "pt_BR"
        case "pt-PT": return "pt_PT"
        case "no": return "nb_NO"
        default: return code
        }
    }

    /// The language's English name, for instructions sent to the coach service.
    static var englishName: String {
        let names: [String: String] = [
            "en": "English", "fr": "French", "es": "Spanish", "de": "German", "it": "Italian",
            "pt-BR": "Brazilian Portuguese", "pt-PT": "European Portuguese", "nl": "Dutch", "sv": "Swedish",
            "da": "Danish", "no": "Norwegian", "fi": "Finnish", "pl": "Polish", "cs": "Czech", "sk": "Slovak",
            "hu": "Hungarian", "ro": "Romanian", "hr": "Croatian", "sl": "Slovenian", "el": "Greek",
            "tr": "Turkish", "ru": "Russian", "ar": "Arabic", "he": "Hebrew", "ja": "Japanese"
        ]
        return names[current] ?? "English"
    }

    static var isRightToLeft: Bool {
        let code = current
        return code == "ar" || code == "he"
    }

    /// First supported language in a list of language tags ("fr-FR", "pt-PT", "nb-NO").
    static func resolve(_ preferred: [String]) -> String {
        for tag in preferred {
            let parts = tag.split(separator: "-").map { String($0) }
            guard let first = parts.first else { continue }
            let code = first.lowercased()
            if code == "pt" { return parts.contains("PT") ? "pt-PT" : "pt-BR" }
            if code == "nb" || code == "nn" { return "no" }
            if code == "iw" { return "he" }
            if supported.contains(code) { return code }
        }
        return "en"
    }
}

/// Tells the interface that the language changed: the root view is rebuilt in the new one.
@MainActor
final class LanguageModel: ObservableObject {
    static let shared = LanguageModel()
    @Published private(set) var code: String = AppLanguage.current

    /// Picks a language and redraws the whole app in it.
    func choose(_ code: String) {
        AppLanguage.chosen = code
        refresh()
    }

    /// Redraws the app after `AppLanguage.chosen` was set directly.
    func refresh() {
        let current = AppLanguage.current
        if current != code { code = current }
    }
}
