package com.example.cpen321application.feature.createtournament

import com.example.cpen321application.core.data.model.Court
import com.example.cpen321application.core.data.model.Format
import java.time.Instant

// The backend accepts tournament names of 1 to 50 characters, after trimming
const val MAX_NAME_LENGTH = 50

data class CreateTournamentUiState(
    val name: String = "",
    val format: Format = Format.SINGLES,
    val startsAt: Instant? = null,  // null until the user picks one
    val court: Court? = null,  // null until the user picks one
    val courts: List<Court> = emptyList(),
    val isLoadingCourts: Boolean = false,
    val courtsErrorMessage: String? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
) {
    val canSubmit: Boolean
        get() = name.isNotBlank() && startsAt != null && court != null && !isSubmitting
}
