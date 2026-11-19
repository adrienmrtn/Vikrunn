import Foundation

/// The language Atala speaks: the one picked in the app, otherwise the phone's, otherwise English.
enum AppLanguage {
    /// Every language the app and the coach's recorded voice exist in.
    static let supported: [String] = [
        "en", "fr", "es", "de", "it", "pt-BR", "pt-PT", "nl", "sv", "da", "no", "fi", "pl", "cs", "sk",
        "hu", "ro", "hr", "sl", "el", "tr", "ru", "ar", "he", "ja"
    ]

    private static let key = "atala.language"

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
        }
    }

    static var current: String {
        chosen ?? resolve(Locale.preferredLanguages)
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
