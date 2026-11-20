package com.example.cpen321application.feature.profile

import com.example.cpen321application.core.data.model.Court
import com.example.cpen321application.core.data.model.Format
import com.example.cpen321application.core.data.model.Game
import com.example.cpen321application.core.data.model.GameSource
import com.example.cpen321application.core.data.model.GameStatus
import com.example.cpen321application.core.data.model.MatchOutcome
import com.example.cpen321application.core.data.model.MatchResult
import com.example.cpen321application.core.data.model.Player
import com.example.cpen321application.core.ui.historyPlaceText
import com.example.cpen321application.core.ui.setsText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class ProfileStatsTest {

    private fun result(outcome: MatchOutcome?, before: Double?, after: Double?) = MatchResult(
        game = Game(
            id = "g-$before",
            format = Format.SINGLES,
            status = GameStatus.COMPLETED,
            source = GameSource.MATCHED,
            startsAt = Instant.parse("2026-11-12T18:00:00Z"),
            court = Court(1, "Kitsilano Beach Park", 49.27, -123.15),
            players = emptyList(),
            rounds = emptyList()
        ),
        me = Player("me", "me", "Alex Rivera", after ?: 1500.0, 1, before, after),
        outcome = outcome,
        partners = emptyList(),
        opponents = emptyList(),
        myScores = listOf(6 to 4, 7 to 5),
        ratingChange = null
    )

    @Test
    fun recordCountsWinsThenLosses() {
        val history = listOf(
            result(MatchOutcome.WIN, null, null),
            result(MatchOutcome.LOSS, null, null),
            result(MatchOutcome.WIN, null, null),
            result(null, null, null)
        )

        assertEquals("2–1", recordText(history))
    }

    @Test
    fun donutSetNeedsAWonSixLove() {
        val won = result(MatchOutcome.WIN, null, null)

        assertTrue(hasDonutSet(won.copy(myScores = listOf(6 to 0, 6 to 3))))
        assertFalse(hasDonutSet(won))
        assertFalse(hasDonutSet(won.copy(outcome = MatchOutcome.LOSS, myScores = listOf(6 to 0, 3 to 6, 4 to 6))))
    }

    @Test
    fun historyShowsSetsThenCourtAndDate() {
        val match = result(MatchOutcome.WIN, null, null)

        assertEquals("6–4  7–5", setsText(match.myScores))
        // The date follows the phone's locale: "Nov 12" in en-US, "Nov. 12" in en-CA
        val date = DateTimeFormatter.ofPattern("MMM d").format(match.game.startsAt.atZone(ZoneOffset.UTC))
        assertEquals("Kitsilano Beach Park · $date", historyPlaceText(match, ZoneOffset.UTC))
    }
}
