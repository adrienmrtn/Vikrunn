import Foundation

/// Public settings of the Supabase project (EU). The publishable key is public by design.
enum SupabaseConfig {
    static let urlString = "https://kjmfbogagvknyzprppdu.supabase.co"
    static let publishableKey = "sb_publishable_oaRQC-5ha2WUk5RWfTGF2w_Al28lax3"

    /// URL scheme registered by the app, used to come back from the Google sign-in page.
    static let callbackScheme = "vicrun"
    static let redirectURL = "vicrun://auth-callback"

    static var isConfigured: Bool {
        !publishableKey.isEmpty && URL(string: urlString)?.host != nil
    }
}
