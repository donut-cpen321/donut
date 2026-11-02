package com.example.cpen321application.feature.leaderboard

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cpen321application.core.network.dto.LeaderboardEntryDto
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders [LeaderboardScreen] with injected players, so the UI can be checked
 * without a backend or a signed-in account
 */
@RunWith(AndroidJUnit4::class)
class LeaderboardScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun player(rank: Int, rating: Double = 2000.0 - rank * 10) = LeaderboardEntryDto(
        id = "id-$rank",
        username = "player$rank",
        name = "Player $rank",
        rating = rating,
        rank = rank,
    )

    private fun players(count: Int) = (1..count).map { player(it) }

    private fun setScreen(state: LeaderboardUiState, onRetry: () -> Unit = {}) {
        composeRule.setContent { LeaderboardScreen(uiState = state, onRetry = onRetry) }
    }

    @Test
    fun showsInjectedPlayersWithRankUsernameAndRoundedRating() {
        val entries = listOf(
            player(1, rating = 1987.6),
            player(2, rating = 1650.2),
            player(3, rating = 1500.0),
        )
        setScreen(LeaderboardUiState(entries = entries))

        composeRule.onNodeWithText("#1").assertIsDisplayed()
        composeRule.onNodeWithText("Player 1").assertIsDisplayed()
        composeRule.onNodeWithText("@player1").assertIsDisplayed()
        composeRule.onNodeWithText("1988").assertIsDisplayed()
        composeRule.onNodeWithText("1650").assertIsDisplayed()
        composeRule.onNodeWithText("#3").assertIsDisplayed()
    }

    @Test
    fun meInTopPlayers_isMarkedOnceAndNotPinned() {
        val entries = players(5)
        setScreen(LeaderboardUiState(entries = entries, me = entries[2]))

        composeRule.onNodeWithText("Player 3 (You)").assertIsDisplayed()
        composeRule.onAllNodesWithText("Player 3 (You)").assertCountEquals(1)
    }

    @Test
    fun meOutsideTopPlayers_isPinnedAtBottom() {
        val me = player(120, rating = 1210.0)
        setScreen(LeaderboardUiState(entries = players(10), me = me))

        composeRule.onNodeWithText("Player 120 (You)").assertIsDisplayed()
        composeRule.onNodeWithText("#120").assertIsDisplayed()
    }

    @Test
    fun meScrolledOutOfView_isPinnedUntilTheirRowIsVisible() {
        val entries = players(50)
        val me = entries[40]
        setScreen(LeaderboardUiState(entries = entries, me = me))

        // Row 41 is off screen, so only the pinned copy is shown
        composeRule.onNodeWithText("Player 41 (You)").assertIsDisplayed()

        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("@player41"))
        composeRule.waitForIdle()

        // Once the real row is visible the pinned copy goes away
        composeRule.onAllNodesWithText("Player 41 (You)").assertCountEquals(1)
    }

    @Test
    fun longName_stillRenders() {
        val long = player(1).copy(name = "A Very Long Player Name That Should Be Ellipsized In The Row")
        setScreen(LeaderboardUiState(entries = listOf(long)))

        // Ellipsis only shortens what is drawn, the row and its rating are still there
        composeRule.onNodeWithText(long.name).assertExists()
        composeRule.onNodeWithText("@player1").assertIsDisplayed()
        composeRule.onNodeWithText("1990").assertIsDisplayed()
    }

    @Test
    fun noPlayers_showsEmptyMessage() {
        setScreen(LeaderboardUiState(entries = emptyList()))

        composeRule.onNodeWithText("No players yet").assertIsDisplayed()
    }

    @Test
    fun loadingWithNoPlayers_showsSpinner() {
        setScreen(LeaderboardUiState(isLoading = true))

        composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()
    }

    @Test
    fun errorWithNoPlayers_showsMessageAndRetryCallsBack() {
        var retries = 0
        setScreen(LeaderboardUiState(errorMessage = "Network down"), onRetry = { retries++ })

        composeRule.onNodeWithText("Network down").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }
}
