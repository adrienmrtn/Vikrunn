package com.atala.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.atala.app.core.account.AuthService
import com.atala.app.core.run.ActiveRun
import com.atala.app.core.run.RunTrackingService
import com.atala.app.ui.root.AtalaRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Content goes edge to edge under white bars with dark icons (the app is light only).
        val bars = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        val store = (application as AtalaApplication).store
        setContent { AtalaRoot(store) }
        // Opened by the Google sign-in page (atala://auth-callback), in a new process when the
        // previous one ended while the page was open. An activity rebuilt from saved state gets
        // its callback through onNewIntent; its launch intent was already handled.
        if (savedInstanceState == null) intent?.data?.let(AuthService::handleCallback)
        // Opened from the "Run in progress" notification (the main screen also checks at launch).
        if (savedInstanceState == null && intent?.getBooleanExtra(RunTrackingService.EXTRA_OPEN_RUN, false) == true) {
            ActiveRun.requestShow()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let(AuthService::handleCallback)
        if (intent.getBooleanExtra(RunTrackingService.EXTRA_OPEN_RUN, false)) ActiveRun.requestShow()
    }

    override fun onStop() {
        super.onStop()
        // The run in progress is copied to disk when the app goes to the background.
        ActiveRun.persistCurrent()
    }
}
