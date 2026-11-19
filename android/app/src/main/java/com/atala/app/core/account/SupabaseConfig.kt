package com.atala.app.core.account

import android.net.Uri

/** Public settings of the Supabase project (EU). The publishable key is public by design. */
object SupabaseConfig {
    const val URL_STRING = "https://kjmfbogagvknyzprppdu.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_oaRQC-5ha2WUk5RWfTGF2w_Al28lax3"

    /** URL scheme registered by the app (manifest), used to come back from the Google sign-in page. */
    const val CALLBACK_SCHEME = "atala"
    const val REDIRECT_URL = "atala://auth-callback"

    val isConfigured: Boolean
        get() = PUBLISHABLE_KEY.isNotEmpty() && Uri.parse(URL_STRING).host != null
}

/** The legal pages of Atala, as published on the web (AtalaLinks.swift). */
object AtalaLinks {
    const val PRIVACY = "https://vikstudios.com/atala"
    const val TERMS = "https://vikstudios.com/atala/terms"
    const val SUPPORT_EMAIL = "support@vikstudios.com"

    /** A new e-mail to the support address, with the subject already filled in. */
    val supportMail: Uri get() = Uri.parse("mailto:$SUPPORT_EMAIL?subject=" + Uri.encode("Atala support"))

    /** The Play Store's own subscriptions page (the App Store's on the iPhone). */
    fun playSubscriptions(packageName: String): Uri =
        Uri.parse("https://play.google.com/store/account/subscriptions?package=$packageName")
}
