package com.example.cpen321application.feature.scheduledgame

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cpen321application.core.data.repository.GamesRepository
import com.example.cpen321application.core.data.repository.TournamentsRepository
import com.example.cpen321application.core.network.toUserMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Games tab: the feed of games and tournaments to join, and the user's own
 * @param gamesRepository the /games routes
 * @param tournamentsRepository the /tournaments routes
 * @param currentUsername the signed-in user's username, null if signed out
 */
class ScheduledGameViewModel(
    private val gamesRepository: GamesRepository,
    private val tournamentsRepository: TournamentsRepository,
    private val currentUsername: () -> String?
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScheduledGameUiState())
    val uiState: StateFlow<ScheduledGameUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun selectTab(tab: GamesTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    /**
     * Loads the feeds and the user's games and tournaments. Called every time the tab is shown,
     * so a game or tournament joined, left or changed on its screen is up to date here.
     */
    fun load() {
        // Only the latest load updates the screen
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, username = currentUsername()) }

            val feed = async { gamesRepository.getFeed() }
            val myGames = async { gamesRepository.getMyGames() }
            val tournamentFeed = async { tournamentsRepository.getFeed() }
            val myTournaments = async { tournamentsRepository.getMyTournaments() }
            val feedResult = feed.await()
            val myGamesResult = myGames.await()
            val tournamentFeedResult = tournamentFeed.await()
            val myTournamentsResult = myTournaments.await()

            // Keep showing the last lists that loaded if a request fails
            val error = listOf(feedResult, myGamesResult, tournamentFeedResult, myTournamentsResult)
                .firstNotNullOfOrNull { it.exceptionOrNull() }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    feed = feedResult.getOrDefault(it.feed),
                    myGames = myGamesResult.getOrDefault(it.myGames),
                    tournamentFeed = tournamentFeedResult.getOrDefault(it.tournamentFeed),
                    myTournaments = myTournamentsResult.getOrDefault(it.myTournaments),
                    errorMessage = error?.toUserMessage()
                )
            }
        }
    }
}
