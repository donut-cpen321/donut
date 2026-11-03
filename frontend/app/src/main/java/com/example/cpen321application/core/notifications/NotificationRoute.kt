package com.example.cpen321application.core.notifications

import com.example.cpen321application.navigation.Screen

// Key of the game id in the push's data, and in the intent that opens the app when it is tapped
const val EXTRA_GAME_ID = "gameId"

/**
 * Where a tapped push notification takes the user
 * @param gameId the game from the notification, null if the app was not opened from one
 * @return the route to open, null to stay where the app starts
 */
fun notificationRoute(gameId: String?): String? {
    if (gameId == null) {
        return null
    }
    // TODO: open the game screen for gameId
    return Screen.Matchmaking.route
}
