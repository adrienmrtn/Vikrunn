import Foundation

/// The words the coach built on the phone recognises in a message, in the app's language. They
/// add to the English lists in the code. One file per language ships in the app,
/// `coach_words_<code>.json`: an object of lists, all in lower case.
enum CoachWords {
    private static let lock = NSLock()
    private static var loaded: String?
    private static var table: [String: [String]] = [:]

    /// The list with this name, empty in English or when the language has none.
    static func list(_ name: String) -> [String] {
        let language = AppLanguage.current
        if language == "en" { return [] }
        lock.lock()
        defer { lock.unlock() }
        if loaded != language {
            table = load(language)
            loaded = language
        }
        return table[name] ?? []
    }

    private static func load(_ language: String) -> [String: [String]] {
        guard let url = Bundle.main.url(forResource: "coach_words_" + language, withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let decoded = try? JSONDecoder().decode([String: [String]].self, from: data) else { return [:] }
        return decoded
    }
}
