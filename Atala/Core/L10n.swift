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

    /// The sentence for a count. Languages with several plural forms can add variants next to a
    /// translation, under the same key followed by `|one`, `|two`, `|few` or `|many`
    /// ("%@ weeks|few"); the plain key is the form used when there is no variant.
    static func string(_ key: String, count: Int) -> String {
        let language = AppLanguage.current
        if language == "en" { return key }
        let plain = string(key)
        guard let category = pluralCategory(count, language: language) else { return plain }
        lock.lock()
        defer { lock.unlock() }
        return table[key + "|" + category] ?? plain
    }

    /// The plural form a whole number takes, nil for the general one.
    static func pluralCategory(_ count: Int, language: String) -> String? {
        let n = abs(count)
        let tens = n % 10
        let hundreds = n % 100
        switch language {
        case "ru", "hr":
            if tens == 1, hundreds != 11 { return "one" }
            if (2...4).contains(tens), !(12...14).contains(hundreds) { return "few" }
            return language == "ru" ? "many" : nil
        case "pl":
            if n == 1 { return "one" }
            if (2...4).contains(tens), !(12...14).contains(hundreds) { return "few" }
            return "many"
        case "cs", "sk":
            if n == 1 { return "one" }
            return (2...4).contains(n) ? "few" : nil
        case "sl":
            if hundreds == 1 { return "one" }
            if hundreds == 2 { return "two" }
            return (3...4).contains(hundreds) ? "few" : nil
        case "ar":
            if n == 1 { return "one" }
            if n == 2 { return "two" }
            if (3...10).contains(hundreds) { return "few" }
            return (11...99).contains(hundreds) ? "many" : nil
        case "he":
            if n == 1 { return "one" }
            return n == 2 ? "two" : nil
        case "ro":
            if n == 1 { return "one" }
            return (n == 0 || (1...19).contains(hundreds)) ? "few" : nil
        default:
            return n == 1 ? "one" : nil
        }
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
/// When one of the values is a whole number, the sentence takes the plural form for it.
func L(_ key: String, _ values: CustomStringConvertible...) -> String {
    var format = L10n.string(key)
    for value in values {
        if let count = value as? Int {
            format = L10n.string(key, count: count)
            break
        }
    }
    return L10n.fill(format, values.map { $0.description })
}

extension String {
    /// A translated title placed inside a sentence: lower case with the language's own rules
    /// ("your tempo run", Turkish dotted letters), except in German, where nouns keep their capital.
    var midSentence: String {
        AppLanguage.current == "de" ? self : lowercased(with: AppLanguage.locale)
    }
}
