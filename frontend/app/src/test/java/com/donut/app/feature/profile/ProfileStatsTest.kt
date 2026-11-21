package com.donut.app.feature.profile

import com.donut.app.core.data.model.Court
import com.donut.app.core.data.model.Format
import com.donut.app.core.data.model.Game
import com.donut.app.core.data.model.GameSource
import com.donut.app.core.data.model.GameStatus
import com.donut.app.core.data.model.MatchOutcome
import com.donut.app.core.data.model.MatchResult
import com.donut.app.core.data.model.Player
import com.donut.app.core.ui.historyDetailText
import com.donut.app.core.ui.setsText
import org.junit.Assert.assertEquals
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
    fun trendStartsBeforeTheOldestMatchAndEndsAtTheLatest() {
        // Most recent first, as the profile keeps it
        val history = listOf(
            result(MatchOutcome.WIN, 1573.0, 1587.4),
            result(MatchOutcome.LOSS, 1579.0, 1573.0),
            result(MatchOutcome.WIN, 1552.0, 1579.0)
        )

        assertEquals(listOf(1552, 1579, 1573, 1587), ratingTrend(history))
    }

    @Test
    fun trendSkipsMatchesWithoutARatingAndNeedsThree() {
        val history = listOf(
            result(MatchOutcome.WIN, 1573.0, 1587.0),
            result(MatchOutcome.LOSS, null, null),
            result(MatchOutcome.WIN, 1552.0, 1573.0)
        )

        assertEquals(emptyList<Int>(), ratingTrend(history))
    }

    @Test
    fun historyDetailShowsSetsCourtAndDate() {
        val match = result(MatchOutcome.WIN, null, null)

        assertEquals("6–4  7–5", setsText(match.myScores))
        // The date follows the phone's locale: "Nov 12" in en-US, "Nov. 12" in en-CA
        val date = DateTimeFormatter.ofPattern("MMM d").format(match.game.startsAt.atZone(ZoneOffset.UTC))
        assertEquals("6–4  7–5 · Kitsilano Beach Park · $date", historyDetailText(match, ZoneOffset.UTC))
    }
}
