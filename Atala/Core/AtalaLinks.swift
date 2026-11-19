import Foundation

/// The legal pages of Atala, as published on the web.
enum AtalaLinks {
    static let privacyString = "https://vikstudios.com/atala"
    static let termsString = "https://vikstudios.com/atala/terms"

    static let supportEmail = "support@vikstudios.com"

    /// Opens a new e-mail to the support address, with the subject already filled in.
    static var supportMail: URL? {
        var parts = URLComponents()
        parts.scheme = "mailto"
        parts.path = supportEmail
        parts.queryItems = [URLQueryItem(name: "subject", value: "Atala support")]
        return parts.url
    }

    static var privacy: URL? { URL(string: privacyString) }
    static var terms: URL? { URL(string: termsString) }
}
