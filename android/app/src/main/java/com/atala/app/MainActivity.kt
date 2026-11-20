package com.atala.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.atala.app.core.account.AuthService
import com.atala.app.ui.root.AtalaRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Content goes edge to edge under white bars with dark icons (the app is light only).
        val bars = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        val store = (application as AtalaApplication).store
        setContent { AtalaRoot(store) }
        // Opened by the Google sign-in page (atala://auth-callback).
        if (savedInstanceState == null) intent?.data?.let(AuthService::handleCallback)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let(AuthService::handleCallback)
    }
}
