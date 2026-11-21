package com.donut.app.feature.profile

import com.donut.app.core.network.dto.FriendDto
import com.donut.app.core.network.dto.FriendGameDto
import com.donut.app.core.network.dto.FriendRequestDto
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class FriendStatusTextTest {
    // Input: a friend from GET /friends who is in a singles game in progress
    // Expected output: the game is read from the JSON, and the row says "Playing" (next to the live dot)
    @Test
    fun playingFriendShowsTheirGame() {
        val json = """{ "id": "u2", "username": "rally", "name": "Rally Partner", "rating": 1520,
            "friendsSince": "2026-10-01T12:00:00.000Z",
            "playing": { "gameId": "game-1", "format": "SINGLES", "startedAt": "2026-11-19T20:00:00.000Z",
            "courtName": "Kitsilano Beach Park" } }"""

        val friend = Gson().fromJson(json, FriendDto::class.java)

        assertEquals("game-1", friend.playing?.gameId)
        assertEquals("Playing", friendStatusText(friend))
    }

    // Input: a friend and a request from the backend with names, and ones with no name or a blank name
    // Expected output: the real name is shown, and the username when there is no name
    @Test
    fun displayNameIsTheRealNameWithTheUsernameAsFallback() {
        val friend = Gson().fromJson(
            """{ "id": "u2", "username": "rally@example.com", "name": "Rally Partner" }""",
            FriendDto::class.java
        )
        val request = Gson().fromJson(
            """{ "id": "u3", "username": "lob@example.com", "name": "Lob Friend" }""",
            FriendRequestDto::class.java
        )

        assertEquals("Rally Partner", friend.displayName)
        assertEquals("Lob Friend", request.displayName)
        assertEquals("lob", FriendDto("u4", "lob").displayName)
        assertEquals("lob", FriendDto("u4", "lob", name = " ").displayName)
    }

    // Input: friends who aren't playing, with and without a rating
    // Expected output: their rating, or nothing
    @Test
    fun friendNotPlayingShowsTheirRating() {
        assertEquals("Rating 1520", friendStatusText(FriendDto("u2", "rally", rating = 1519.6, playing = null)))
        assertNull(friendStatusText(FriendDto("u3", "lob")))
    }

    // Input: a friend's doubles game, and a game in a format this app version doesn't know
    // Expected output: the format's label and the court; an unknown format is shown as sent
    @Test
    fun dialogTitleIsTheFormatAndCourt() {
        val doubles = FriendGameDto("game-2", "DOUBLES", "2026-11-19T20:00:00.000Z", "Stanley Park")
        val unknown = doubles.copy(format = "MIXED")

        assertEquals("Doubles · Stanley Park", friendGameTitle(doubles))
        assertEquals("MIXED · Stanley Park", friendGameTitle(unknown))
    }

    // Input: games started 30 s, 12 min, 65 min and 2 h ago
    // Expected output: "just now", minutes, then hours and minutes
    @Test
    fun elapsedTimeIsShort() {
        assertEquals("just now", elapsedText(Duration.ofSeconds(30)))
        assertEquals("12 min ago", elapsedText(Duration.ofMinutes(12)))
        assertEquals("1 h 5 min ago", elapsedText(Duration.ofMinutes(65)))
        assertEquals("2 h 0 min ago", elapsedText(Duration.ofHours(2)))
    }

    // Input: a game that started 12 minutes ago, and a start time that can't be read
    // Expected output: "Started <time> · 12 min ago", and null for the unreadable one
    @Test
    fun startedTextHasTheTimeAndHowLongAgo() {
        val startedAt = Instant.parse("2026-11-19T20:00:00Z")

        val text = startedText("2026-11-19T20:00:00Z", startedAt.plus(Duration.ofMinutes(12)))

        assertTrue(text!!.startsWith("Started "))
        assertTrue(text.endsWith(" · 12 min ago"))
        assertNull(startedText("yesterday", startedAt))
    }
}
