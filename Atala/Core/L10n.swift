import Foundation

/// Translations. The English sentence is the key: `L("Continue")` gives it in the app's language,
/// and the English itself when a translation is missing. One file per language ships in the app,
/// `strings_<code>.json` (an object, English sentence to translation).
enum L10n {
    private static let lock = NSLock()
    private static var loaded: String?
    private static var table: [String: String] = [:]

    static func string(_ key: String) -> String {
        let language = AppLanguage.current
        if language == "en" { return key }
        lock.lock()
        defer { lock.unlock() }
        if loaded != language {
            table = load(language)
            loaded = language
        }
        return table[key] ?? key
    }

    private static func load(_ language: String) -> [String: String] {
        guard let url = Bundle.main.url(forResource: "strings_" + language, withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let decoded = try? JSONDecoder().decode([String: String].self, from: data) else { return [:] }
        return decoded
    }

    /// Puts the values in the sentence: each `%@` in turn, or `%1$@`, `%2$@` when a translation
    /// needs them in another order.
    static func fill(_ format: String, _ values: [String]) -> String {
        var result = format
        for (index, value) in values.enumerated() {
            result = result.replacingOccurrences(of: "%\(index + 1)$@", with: value)
        }
        for value in values {
            guard let range = result.range(of: "%@") else { break }
            result.replaceSubrange(range, with: value)
        }
        return result
    }
}

/// The sentence in the app's language.
func L(_ key: String) -> String {
    L10n.string(key)
}

/// The sentence in the app's language, with values in place of each `%@`.
func L(_ key: String, _ values: CustomStringConvertible...) -> String {
    L10n.fill(L10n.string(key), values.map { $0.description })
}

extension String {
    /// A translated title placed inside a sentence: lower case in English ("your tempo run"),
    /// untouched elsewhere, where capitals carry meaning (German nouns, Turkish dotted letters).
    var midSentence: String {
        AppLanguage.current == "en" ? lowercased() : self
    }
}
