package com.example.cpen321application.feature.tournamentdetail

import com.example.cpen321application.core.data.model.Tournament

data class TournamentDetailUiState(
    val tournament: Tournament? = null,
    val actions: TournamentActions? = null,  // set together with tournament
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,  // an action was sent and has no answer yet
    val errorMessage: String? = null
)
