package com.example.cpen321application.feature.creategame

import com.example.cpen321application.core.data.model.Court
import com.example.cpen321application.core.data.model.Format
import java.time.Instant

data class CreateGameUiState(
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
        get() = startsAt != null && court != null && !isSubmitting
}
