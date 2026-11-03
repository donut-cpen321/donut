package com.example.cpen321application

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.example.cpen321application.core.notifications.EXTRA_GAME_ID
import com.example.cpen321application.core.notifications.EXTRA_NOTIFICATION_TYPE
import com.example.cpen321application.core.notifications.createMatchChannel
import com.example.cpen321application.core.notifications.notificationRoute
import com.example.cpen321application.core.ui.theme.CPEN321ApplicationTheme
import com.example.cpen321application.navigation.MainScreen

class MainActivity : ComponentActivity() {
    // Where a tapped push notification goes, null once it has been opened
    private val notificationRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createMatchChannel(this)
        if (savedInstanceState == null) {
            notificationRoute.value = routeOf(intent)
        }
        setContent {
            CPEN321ApplicationTheme {
                MainScreen(
                    serverClientId = BuildConfig.GOOGLE_CLIENT_ID,
                    notificationRoute = notificationRoute.value,
                    onNotificationHandled = { notificationRoute.value = null }
                )
            }
        }
    }

    // A push tapped while the app is already open
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        notificationRoute.value = routeOf(intent)
    }

    private fun routeOf(intent: Intent): String? =
        notificationRoute(intent.getStringExtra(EXTRA_GAME_ID), intent.getStringExtra(EXTRA_NOTIFICATION_TYPE))
}
