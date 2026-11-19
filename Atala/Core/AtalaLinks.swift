import Foundation

/// The legal pages of Atala, as published on the web.
enum AtalaLinks {
    static let privacyString = "https://vikstudios.com/atala"
    static let termsString = "https://vikstudios.com/atala/terms"

    static var privacy: URL? { URL(string: privacyString) }
    static var terms: URL? { URL(string: termsString) }
}
