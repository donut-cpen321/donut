package com.example.cpen321application.feature.tournamentdetail

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cpen321application.core.data.model.Court
import com.example.cpen321application.core.data.model.Format
import com.example.cpen321application.core.data.model.GameStatus
import com.example.cpen321application.core.data.model.MatchGame
import com.example.cpen321application.core.data.model.Round
import com.example.cpen321application.core.data.model.Tournament
import com.example.cpen321application.core.data.model.TournamentCreator
import com.example.cpen321application.core.data.model.TournamentMatch
import com.example.cpen321application.core.data.model.TournamentPlayer
import com.example.cpen321application.core.data.model.TournamentStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

/**
 * Renders [TournamentDetailScreen] with injected tournaments, so the registration list, the bracket
 * and the champion banner can be checked without a backend or a signed-in account
 */
@RunWith(AndroidJUnit4::class)
class TournamentDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val now = Instant.now()
    private val court = Court(3, "Kitsilano Beach Park", 49.27, -123.15)

    private fun players(drawn: Boolean) = (1..8).map {
        // Seeds 1 to 8, named "Player 1" to "Player 8", with no team until the draw
        TournamentPlayer("id-$it", "player$it@example.com", "Player $it", 1600.0 - it * 10, if (drawn) it else null)
    }

    private fun tournament(
        status: TournamentStatus,
        players: List<TournamentPlayer>,
        matches: List<TournamentMatch> = emptyList(),
        winningTeam: Int? = null
    ) = Tournament(
        id = "t1",
        name = "Sunday Smash",
        format = Format.SINGLES,
        status = status,
        startsAt = now.plus(Duration.ofDays(1)),
        court = court,
        creator = TournamentCreator("id-1", "player1@example.com", "Player 1", 1590.0),
        capacity = 8,
        players = players,
        matches = matches,
        winningTeam = winningTeam
    )

    private fun game(id: String, team1Score: Int, team2Score: Int) =
        MatchGame(id, GameStatus.COMPLETED, now, court, listOf(Round(1, team1Score, team2Score)))

    private val playedMatches = listOf(  // a fully played bracket won by seed 1
        TournamentMatch(1, 0, 1, 8, 1, game("qf0", 6, 1)),
        TournamentMatch(1, 1, 4, 5, 4, game("qf1", 6, 2)),
        TournamentMatch(1, 2, 3, 6, 3, game("qf2", 6, 3)),
        TournamentMatch(1, 3, 2, 7, 2, game("qf3", 6, 4)),
        TournamentMatch(2, 0, 1, 4, 1, game("sf0", 7, 5)),
        TournamentMatch(2, 1, 3, 2, 2, game("sf1", 4, 6)),
        TournamentMatch(3, 0, 1, 2, 1, game("f0", 7, 6))
    )

    private fun setScreen(
        tournament: Tournament,
        username: String? = "visitor@example.com",
        callbacks: TournamentDetailCallbacks = TournamentDetailCallbacks({}, {}, {}, {})
    ) {
        val state = TournamentDetailUiState(
            tournament = tournament,
            actions = tournamentActions(tournament, username, now)
        )
        composeRule.setContent {
            TournamentDetailScreen(uiState = state, onBack = {}, onRetry = {}, callbacks = callbacks)
        }
    }

    @Test
    fun registration_showsRegistrantsAndJoinButton() {
        var joins = 0
        val open = tournament(TournamentStatus.REGISTRATION, players(drawn = false).take(3))
        setScreen(open, callbacks = TournamentDetailCallbacks(onJoin = { joins++ }, {}, {}, {}))

        composeRule.onNodeWithText("Sunday Smash").assertIsDisplayed()
        composeRule.onNodeWithText("3/8 players", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Registered players").assertIsDisplayed()
        composeRule.onNodeWithText("Player 2", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Player 3", substring = true).assertIsDisplayed()

        composeRule.onNodeWithText("Join tournament").assertIsDisplayed().performClick()
        assertEquals(1, joins)
    }

    @Test
    fun registration_creatorSeesCancelNotJoin() {
        val open = tournament(TournamentStatus.REGISTRATION, players(drawn = false).take(3))
        setScreen(open, username = "player1@example.com")

        composeRule.onNodeWithText("Cancel tournament").assertIsDisplayed()
        composeRule.onNodeWithText("Join tournament").assertDoesNotExist()
        composeRule.onNodeWithText("Leave tournament").assertDoesNotExist()
    }

    @Test
    fun inProgress_showsBracketColumnsAndTeamNames() {
        val quarterfinals = playedMatches.take(4).map { it.copy(winner = null) }
        val inProgress = tournament(TournamentStatus.IN_PROGRESS, players(drawn = true), quarterfinals)
        setScreen(inProgress)

        composeRule.onNodeWithText("Quarterfinals").assertIsDisplayed()
        // The later columns may be scrolled off to the side on a narrow screen
        composeRule.onNodeWithText("Semifinals").assertExists()
        composeRule.onNodeWithText("Final").assertExists()
        composeRule.onNodeWithText("Player 1").assertIsDisplayed()
        composeRule.onNodeWithText("Player 8").assertIsDisplayed()
        composeRule.onNodeWithText("Join tournament").assertDoesNotExist()
        composeRule.onNodeWithText("Champion").assertDoesNotExist()
    }

    @Test
    fun inProgress_tappingAPlayedMatchOpensItsGame() {
        val opened = mutableListOf<String>()
        val inProgress = tournament(TournamentStatus.IN_PROGRESS, players(drawn = true), playedMatches.take(4))
        setScreen(inProgress, callbacks = TournamentDetailCallbacks({}, {}, {}, onOpenGame = { opened.add(it) }))

        composeRule.onNodeWithText("Player 8").performClick()

        assertEquals(listOf("qf0"), opened)
    }

    @Test
    fun completed_showsChampionBanner() {
        val completed = tournament(TournamentStatus.COMPLETED, players(drawn = true), playedMatches, winningTeam = 1)
        setScreen(completed)

        composeRule.onNodeWithText("Champion").assertIsDisplayed()
        // The winner's name: once in the banner, and in the quarterfinal, semifinal and final they won
        composeRule.onAllNodesWithText("Player 1").assertCountEquals(4)
        composeRule.onNodeWithText("Completed", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Quarterfinals").assertIsDisplayed()
    }
}
