package com.example.cpen321application.feature.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cpen321application.core.auth.AuthSession
import com.example.cpen321application.core.data.repository.LeaderboardRepository
import com.example.cpen321application.core.data.repository.LeaderboardRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LeaderboardViewModel(
    private val repository: LeaderboardRepository = LeaderboardRepositoryImpl(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(LeaderboardUiState())
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    /**
     * Loads the top players and the signed-in user's own entry
     * @param session the signed-in user's session, nothing is loaded if null
     */
    fun load(session: AuthSession?) {
        if (session == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.getLeaderboard(session)
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            entries = response.leaderboard,
                            me = response.me,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Failed to load leaderboard",
                        )
                    }
                }
        }
    }
}
