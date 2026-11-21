package com.donut.app.core.notifications

import com.donut.app.navigation.Screen

// Key of the game id in the push's data, and in the intent that opens the app when it is tapped
const val EXTRA_GAME_ID = "gameId"

// Key of the kind of push, in the same places. Match pushes don't set it
const val EXTRA_NOTIFICATION_TYPE = "type"

// The type of a push inviting the user to a friend's game
const val GAME_INVITE_TYPE = "gameInvite"

// The type of a push asking the user to confirm the score another player reported
const val SCORE_REPORTED_TYPE = "scoreReported"

// The pushes that open their game's screen when tapped
private val GAME_SCREEN_TYPES = setOf(GAME_INVITE_TYPE, SCORE_REPORTED_TYPE)

/**
 * @param data the push's data
 * @return whether the push is about a matched game (found, accepted or cancelled): it has a game id and no type
 */
fun isMatchPush(data: Map<String, String>): Boolean =
    data[EXTRA_GAME_ID] != null && data[EXTRA_NOTIFICATION_TYPE] == null

/**
 * Where a tapped push notification takes the user
 * @param gameId the game from the notification, null if the app was not opened from one
 * @param type the kind of notification, null for a match
 * @return the route to open, null to stay where the app starts
 */
fun notificationRoute(gameId: String?, type: String? = null): String? {
    if (gameId == null) {
        return null
    }
    if (type in GAME_SCREEN_TYPES) {
        return Screen.GameDetail.createRoute(gameId)
    }
    // TODO: open the game screen for gameId
    return Screen.Matchmaking.route
}
