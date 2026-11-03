package com.example.cpen321application

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.example.cpen321application.core.notifications.EXTRA_GAME_ID
import com.example.cpen321application.core.notifications.createMatchChannel
import com.example.cpen321application.core.notifications.notificationRoute
import com.example.cpen321application.core.ui.theme.CPEN321ApplicationTheme
import com.example.cpen321application.navigation.MainScreen

class MainActivity : ComponentActivity() {
    // The game from a tapped push notification, null once it has been opened
    private val notificationGameId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createMatchChannel(this)
        if (savedInstanceState == null) {
            notificationGameId.value = intent.getStringExtra(EXTRA_GAME_ID)
        }
        setContent {
            CPEN321ApplicationTheme {
                MainScreen(
                    serverClientId = BuildConfig.GOOGLE_CLIENT_ID,
                    notificationRoute = notificationRoute(notificationGameId.value),
                    onNotificationHandled = { notificationGameId.value = null }
                )
            }
        }
    }

    // A push tapped while the app is already open
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        notificationGameId.value = intent.getStringExtra(EXTRA_GAME_ID)
    }
}
