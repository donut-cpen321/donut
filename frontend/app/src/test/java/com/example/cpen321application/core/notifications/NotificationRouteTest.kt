package com.example.cpen321application.core.notifications

import com.example.cpen321application.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationRouteTest {
    // Input: the app was opened from a match notification
    // Expected behavior: go to the screen for the match
    // Expected output: Matchmaking (until the game screen exists)
    @Test
    fun matchNotificationOpensMatchmaking() {
        assertEquals(Screen.Matchmaking.route, notificationRoute("game-1"))
    }

    // Input: the app was opened normally, without a notification
    // Expected behavior: stay where the app starts
    // Expected output: null
    @Test
    fun noNotificationStays() {
        assertNull(notificationRoute(null))
    }
}
