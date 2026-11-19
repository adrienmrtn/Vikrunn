import Foundation

/// The words the coach built on the phone recognises in a message, in the app's language. They
/// add to the English lists in the code. One file per language ships in the app,
/// `coach_words_<code>.json`: an object of lists, all in lower case.
enum CoachWords {
    private static let lock = NSLock()
    private static var loaded: String?
    private static var table: [String: [String]] = [:]
    /// True while reading a message that is known to be in English (a suggested prompt mapped
    /// back to its English text): the English lists then apply whatever the app's language.
    static var readsEnglish = false

    /// The list with this name, empty in English or when the language has none.
    static func list(_ name: String) -> [String] {
        let language = AppLanguage.current
        if language == "en" || readsEnglish { return [] }
        lock.lock()
        defer { lock.unlock() }
        if loaded != language {
            table = load(language)
            loaded = language
        }
        return table[name] ?? []
    }

    /// The words to look for: the app's language when it has a list, English otherwise. English
    /// words are left out in other languages, where they collide with ordinary words
    /// ("hard" inside the Dutch "hardlopen").
    static func pick(_ english: [String], _ name: String) -> [String] {
        let local = list(name)
        return local.isEmpty ? english : local
    }

    /// An English-only list, used while the app is in English.
    static func english(_ words: [String]) -> [String] {
        (AppLanguage.current == "en" || readsEnglish) ? words : []
    }

    private static func load(_ language: String) -> [String: [String]] {
        guard let url = Bundle.main.url(forResource: "coach_words_" + language, withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let decoded = try? JSONDecoder().decode([String: [String]].self, from: data) else { return [:] }
        return decoded
    }
}
