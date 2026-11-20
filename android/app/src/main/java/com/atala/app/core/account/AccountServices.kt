package com.atala.app.core.account

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.atala.app.core.model.WorkoutStatus
import com.atala.app.core.purchases.PurchaseService
import com.atala.app.core.reminders.RunReminders
import com.atala.app.core.store.AppStore
import com.atala.app.core.sync.SyncService
import com.atala.app.core.sync.SyncTracker
import com.atala.app.core.l10n.AppLanguage
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Process-wide wiring of the account features (what AtalaApp.swift and RootView do on the iPhone):
 * the token vault, the sync with the account at launch, on return to the foreground and after
 * every change, the push when the app is left, the session reminders, and the purchases linked
 * to the account. Started once from `AtalaApplication.onCreate`.
 */
object AccountServices {
    private val scope = MainScope()
    private val foreground = MutableStateFlow(0)
    private var started = 0

    /** [store] is read after the vault is ready: the store's file is loaded with its tokens. */
    fun start(application: Application, store: () -> AppStore) {
        AuthKeychain.init(application)
        AuthService.init(application)
        RunReminders.createChannel(application)
        val appStore = store()
        SyncService.register(appStore)
        SyncTracker.start(appStore, scope)

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                started += 1
                if (started == 1) {
                    // Launch and return to the foreground: catch up with the account (no-op for a
                    // guest, silent when offline), and look at the reminders again.
                    SyncService.resume(appStore)
                    foreground.value += 1
                }
            }

            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                if (started == 0) SyncService.pushBeforeSuspension(appStore)
            }

            override fun onActivityResumed(activity: Activity) = AuthService.noteActivityResumed()
            override fun onActivityPaused(activity: Activity) = AuthService.noteActivityPaused()
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        // Linking the purchases to the account is done by PurchaseService.observe (AtalaApplication).

        // Everything the reminders depend on: when one of these changes, they are scheduled again.
        val planKey = appStore.state.map { state ->
            val planned = state.plan?.allWorkouts.orEmpty().filter { it.status == WorkoutStatus.Planned }
            listOf(
                state.plan?.id?.toString() ?: "none",
                planned.size,
                planned.sumOf { it.date.epochSecond },
                state.answers.coachId,
                state.hasCompletedOnboarding,
            ).joinToString("|")
        }.distinctUntilChanged()
        combine(planKey, PurchaseService.isPro, PurchaseService.isInTrial, foreground, AppLanguage.code) { key, pro, trial, tick, language ->
            "$key|$pro|$trial|$tick|$language"
        }
            .distinctUntilChanged()
            .onEach { RunReminders.refresh(application, appStore.current) }
            .launchIn(scope)
    }
}
