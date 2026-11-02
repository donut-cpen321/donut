package com.example.cpen321application.feature.gamedetail

import com.example.cpen321application.core.data.model.Court
import com.example.cpen321application.core.data.model.Game

data class GameDetailUiState(
    val game: Game? = null,
    val actions: GameActions? = null,  // set together with game
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,  // an action was sent and has no answer yet
    val errorMessage: String? = null,

    // For changing the court, loaded when the court picker opens
    val courts: List<Court> = emptyList(),
    val isLoadingCourts: Boolean = false,
    val courtsErrorMessage: String? = null
)
