package com.example.cpen321application.feature.createtournament

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cpen321application.core.data.model.Court
import com.example.cpen321application.core.data.model.Format
import com.example.cpen321application.core.data.repository.CourtsRepository
import com.example.cpen321application.core.data.repository.TournamentsRepository
import com.example.cpen321application.core.network.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * The form to create a tournament: name, format, start time and court
 * @param tournamentsRepository the /tournaments routes
 * @param courtsRepository the /courts route, for the court picker
 */
class CreateTournamentViewModel(
    private val tournamentsRepository: TournamentsRepository,
    private val courtsRepository: CourtsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(CreateTournamentUiState())
    val uiState: StateFlow<CreateTournamentUiState> = _uiState.asStateFlow()

    init {
        loadCourts()
    }

    /**
     * @param name what the user typed, cut to MAX_NAME_LENGTH characters
     */
    fun updateName(name: String) {
        _uiState.update { it.copy(name = name.take(MAX_NAME_LENGTH), errorMessage = null) }
    }

    fun selectFormat(format: Format) {
        _uiState.update { it.copy(format = format) }
    }

    fun selectStartsAt(startsAt: Instant) {
        _uiState.update { it.copy(startsAt = startsAt, errorMessage = null) }
    }

    fun selectCourt(court: Court) {
        _uiState.update { it.copy(court = court, errorMessage = null) }
    }

    fun loadCourts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCourts = true, courtsErrorMessage = null) }
            courtsRepository.getCourts()
                .onSuccess { courts ->
                    _uiState.update { it.copy(isLoadingCourts = false, courts = courts) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoadingCourts = false, courtsErrorMessage = error.toUserMessage()) }
                }
        }
    }

    /**
     * Creates the tournament with the signed-in user registered
     * @param onCreated called with the new tournament's ID, to open its screen
     */
    fun create(onCreated: (tournamentId: String) -> Unit) {
        val state = _uiState.value
        val startsAt = state.startsAt
        val court = state.court
        if (!state.canSubmit || startsAt == null || court == null) {
            return
        }

        // The backend checks this too, but the user can fix it without a round trip
        if (!startsAt.isAfter(Instant.now())) {
            _uiState.update { it.copy(errorMessage = "Pick a start time in the future.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            tournamentsRepository.createTournament(state.name.trim(), state.format, startsAt, court.id)
                .onSuccess { tournamentId ->
                    _uiState.update { it.copy(isSubmitting = false) }
                    onCreated(tournamentId)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = error.toUserMessage()) }
                }
        }
    }
}
