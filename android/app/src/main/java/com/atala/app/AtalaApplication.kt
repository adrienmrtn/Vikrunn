package com.atala.app

import android.app.Application
import android.content.res.Configuration
import com.atala.app.core.l10n.AppLanguage
import com.atala.app.core.l10n.L10n
import com.atala.app.core.purchases.PurchaseService
import com.atala.app.core.store.AppStore
import kotlinx.coroutines.MainScope
import java.io.File

class AtalaApplication : Application() {
    /** The one store of the app, alive as long as the process. */
    val store: AppStore by lazy { AppStore(File(filesDir, "vicrun.json"), MainScope()) }

    override fun onCreate() {
        super.onCreate()
        AppLanguage.init(this)
        L10n.init(assets)
        // Subscriptions (RevenueCat): configured now, then linked to the signed-in account.
        PurchaseService.observe(this, store)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLanguage.refresh()
    }
}
