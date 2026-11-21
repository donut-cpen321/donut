package com.donut.app.core.notifications

import com.donut.app.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRouteTest {
    // Input: the app was opened from a match notification
    // Expected behavior: go to the screen for the match
    // Expected output: Matchmaking (until the game screen exists)
    @Test
    fun matchNotificationOpensMatchmaking() {
        assertEquals(Screen.Matchmaking.route, notificationRoute("game-1"))
    }

    // Input: the app was opened from a friend's game invite
    // Expected behavior: go to the game, where the user can join it
    // Expected output: the game's screen
    @Test
    fun gameInviteOpensTheGame() {
        assertEquals(Screen.GameDetail.createRoute("game-1"), notificationRoute("game-1", GAME_INVITE_TYPE))
    }

    // Input: the app was opened from a push asking to confirm the score another player reported
    // Expected behavior: go to the game, where the user can confirm the score or suggest a correction
    // Expected output: the game's screen
    @Test
    fun scoreReportedOpensTheGame() {
        assertEquals(Screen.GameDetail.createRoute("game-1"), notificationRoute("game-1", SCORE_REPORTED_TYPE))
        assertFalse(isMatchPush(mapOf(EXTRA_GAME_ID to "game-1", EXTRA_NOTIFICATION_TYPE to SCORE_REPORTED_TYPE)))
    }

    // Input: the app was opened normally, without a notification
    // Expected behavior: stay where the app starts
    // Expected output: null
    @Test
    fun noNotificationStays() {
        assertNull(notificationRoute(null))
    }

    // Input: a push about a matched game (found, accepted or cancelled)
    // Expected behavior: the app refreshes the queue and the match pop-up
    // Expected output: true
    @Test
    fun matchPushIsAMatchPush() {
        assertTrue(isMatchPush(mapOf(EXTRA_GAME_ID to "game-1")))
    }

    // Input: a friend's game invite, which also carries a game id
    // Expected behavior: matchmaking is left alone
    // Expected output: false
    @Test
    fun inviteIsNotAMatchPush() {
        assertFalse(isMatchPush(mapOf(EXTRA_GAME_ID to "game-1", EXTRA_NOTIFICATION_TYPE to GAME_INVITE_TYPE)))
    }

    // Input: a push without a game
    // Expected behavior: matchmaking is left alone
    // Expected output: false
    @Test
    fun pushWithoutAGameIsNotAMatchPush() {
        assertFalse(isMatchPush(emptyMap()))
    }
}
