package com.example.cpen321application

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.cpen321application.core.ui.theme.CPEN321ApplicationTheme
import com.example.cpen321application.navigation.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CPEN321ApplicationTheme {
                MainScreen(
                    apiBaseUrl = BuildConfig.API_BASE_URL,
                    serverClientId = BuildConfig.GOOGLE_CLIENT_ID
                )
            }
        }
    }
}
