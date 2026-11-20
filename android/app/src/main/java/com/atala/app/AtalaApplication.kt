package com.atala.app

import android.app.Application
import android.content.res.Configuration
import com.atala.app.core.account.AccountServices
import com.atala.app.core.l10n.AppLanguage
import com.atala.app.core.l10n.L10n
import com.atala.app.core.purchases.PurchaseService
import com.atala.app.core.review.ReviewPromptState
import com.atala.app.core.store.AppStore
import kotlinx.coroutines.MainScope
import java.io.File

class AtalaApplication : Application() {
    /**
     * The one store of the app, alive as long as the process. The token vault is opened first,
     * whoever reads the store first: the account is loaded with its session tokens.
     */
    val store: AppStore by lazy {
        AccountServices.prepareVault(this)
        AppStore(File(filesDir, "vicrun.json"), MainScope())
    }

    override fun onCreate() {
        super.onCreate()
        // Before anything that could read the store (PurchaseService.observe below does).
        AccountServices.prepareVault(this)
        AppLanguage.init(this)
        L10n.init(assets)
        ReviewPromptState.init(this)
        // Subscriptions (RevenueCat): configured now, then linked to the signed-in account.
        PurchaseService.observe(this, store)
        AccountServices.start(this) { store }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLanguage.refresh()
    }
}
