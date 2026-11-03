package com.example.cpen321application.feature.createtournament

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cpen321application.core.data.model.Court
import com.example.cpen321application.core.data.model.Format
import com.example.cpen321application.core.ui.components.BackHeader
import com.example.cpen321application.core.ui.components.CourtPickerDialog
import com.example.cpen321application.core.ui.components.DateTimePickerDialog
import com.example.cpen321application.core.ui.components.PickerButton
import com.example.cpen321application.core.ui.components.SubmitButton
import com.example.cpen321application.core.ui.formatStartsAt
import com.example.cpen321application.core.ui.label
import java.time.Instant

/**
 * What the create tournament form does when the user fills it in
 * @property onNameChange called with the name as the user types it
 * @property onFormatSelected picks singles or doubles
 * @property onStartsAtSelected picks the start time of the quarterfinals
 * @property onCourtSelected picks the court every match is played on
 * @property onRetryCourts loads the courts again after an error
 * @property onSubmit creates the tournament
 */
data class CreateTournamentCallbacks(
    val onNameChange: (String) -> Unit,
    val onFormatSelected: (Format) -> Unit,
    val onStartsAtSelected: (Instant) -> Unit,
    val onCourtSelected: (Court) -> Unit,
    val onRetryCourts: () -> Unit,
    val onSubmit: () -> Unit
)

@Composable
fun CreateTournamentRoute(
    viewModel: CreateTournamentViewModel,
    onBack: () -> Unit,
    onCreated: (tournamentId: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val callbacks = remember(viewModel, onCreated) {
        CreateTournamentCallbacks(
            onNameChange = viewModel::updateName,
            onFormatSelected = viewModel::selectFormat,
            onStartsAtSelected = viewModel::selectStartsAt,
            onCourtSelected = viewModel::selectCourt,
            onRetryCourts = viewModel::loadCourts,
            onSubmit = { viewModel.create(onCreated) }
        )
    }

    CreateTournamentScreen(uiState = uiState, onBack = onBack, callbacks = callbacks)
}

@Composable
fun CreateTournamentScreen(
    uiState: CreateTournamentUiState,
    onBack: () -> Unit,
    callbacks: CreateTournamentCallbacks
) {
    var showTimePicker by remember { mutableStateOf(false) }
    var showCourtPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        BackHeader(title = "Create a tournament", onBack = onBack)
        CreateTournamentForm(
            uiState = uiState,
            callbacks = callbacks,
            onPickTime = { showTimePicker = true },
            onPickCourt = { showCourtPicker = true }
        )
    }

    if (showTimePicker) {
        DateTimePickerDialog(
            initial = uiState.startsAt,
            onDismiss = { showTimePicker = false },
            onConfirm = { startsAt ->
                showTimePicker = false
                callbacks.onStartsAtSelected(startsAt)
            }
        )
    }

    if (showCourtPicker) {
        CourtPickerDialog(
            courts = uiState.courts,
            selectedCourtId = uiState.court?.id,
            isLoading = uiState.isLoadingCourts,
            errorMessage = uiState.courtsErrorMessage,
            onRetry = callbacks.onRetryCourts,
            onDismiss = { showCourtPicker = false },
            onSelect = { court ->
                showCourtPicker = false
                callbacks.onCourtSelected(court)
            }
        )
    }
}

@Composable
private fun CreateTournamentForm(
    uiState: CreateTournamentUiState,
    callbacks: CreateTournamentCallbacks,
    onPickTime: () -> Unit,
    onPickCourt: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = uiState.name,
            onValueChange = callbacks.onNameChange,
            label = { Text("Name") },
            supportingText = { Text("${uiState.name.length}/$MAX_NAME_LENGTH") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text(text = "Format", style = MaterialTheme.typography.titleMedium)
        FormatChips(selected = uiState.format, onSelect = callbacks.onFormatSelected)

        Text(text = "Start time", style = MaterialTheme.typography.titleMedium)
        PickerButton(
            icon = Icons.Default.DateRange,
            text = uiState.startsAt?.let { formatStartsAt(it) } ?: "Pick a date and time",
            onClick = onPickTime
        )

        Text(text = "Court", style = MaterialTheme.typography.titleMedium)
        PickerButton(icon = Icons.Default.Place, text = uiState.court?.name ?: "Pick a court", onClick = onPickCourt)

        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        SubmitButton(
            label = "Create tournament",
            enabled = uiState.canSubmit,
            isSubmitting = uiState.isSubmitting,
            onClick = callbacks.onSubmit
        )

        Text(
            text = "You'll be registered automatically. Once all 8 teams are in (16 players in doubles, " +
                "paired by rating), the bracket is drawn by rating. Semifinals start 1 hour after the " +
                "quarterfinals, and the final 1 hour after that.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FormatChips(selected: Format, onSelect: (Format) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Format.entries.forEach { format ->
            FilterChip(
                selected = selected == format,
                onClick = { onSelect(format) },
                label = { Text(format.label) }
            )
        }
    }
}
