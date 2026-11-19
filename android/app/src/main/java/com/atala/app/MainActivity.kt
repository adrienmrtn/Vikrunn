package com.atala.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.atala.app.ui.root.AtalaRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Content goes edge to edge under white bars with dark icons (the app is light only).
        val bars = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        val store = (application as AtalaApplication).store
        setContent { AtalaRoot(store) }
    }
}
