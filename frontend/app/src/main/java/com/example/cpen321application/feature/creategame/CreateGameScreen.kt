package com.example.cpen321application.feature.creategame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.cpen321application.core.data.model.Court
import com.example.cpen321application.core.data.model.Format
import com.example.cpen321application.core.ui.components.BackHeader
import com.example.cpen321application.core.ui.components.CourtPickerDialog
import com.example.cpen321application.core.ui.components.DateTimePickerDialog
import com.example.cpen321application.core.ui.formatStartsAt
import com.example.cpen321application.core.ui.label
import java.time.Instant

@Composable
fun CreateGameRoute(
    viewModel: CreateGameViewModel,
    onBack: () -> Unit,
    onCreated: (gameId: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    CreateGameScreen(
        uiState = uiState,
        onBack = onBack,
        onFormatSelected = viewModel::selectFormat,
        onStartsAtSelected = viewModel::selectStartsAt,
        onCourtSelected = viewModel::selectCourt,
        onRetryCourts = viewModel::loadCourts,
        onSubmit = { viewModel.create(onCreated) }
    )
}

@Composable
fun CreateGameScreen(
    uiState: CreateGameUiState,
    onBack: () -> Unit,
    onFormatSelected: (Format) -> Unit,
    onStartsAtSelected: (Instant) -> Unit,
    onCourtSelected: (Court) -> Unit,
    onRetryCourts: () -> Unit,
    onSubmit: () -> Unit
) {
    var showTimePicker by remember { mutableStateOf(false) }
    var showCourtPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        BackHeader(title = "Schedule a game", onBack = onBack)
        CreateGameForm(
            uiState = uiState,
            onFormatSelected = onFormatSelected,
            onPickTime = { showTimePicker = true },
            onPickCourt = { showCourtPicker = true },
            onSubmit = onSubmit
        )
    }

    if (showTimePicker) {
        DateTimePickerDialog(
            initial = uiState.startsAt,
            onDismiss = { showTimePicker = false },
            onConfirm = { startsAt ->
                showTimePicker = false
                onStartsAtSelected(startsAt)
            }
        )
    }

    if (showCourtPicker) {
        CourtPickerDialog(
            courts = uiState.courts,
            selectedCourtId = uiState.court?.id,
            isLoading = uiState.isLoadingCourts,
            errorMessage = uiState.courtsErrorMessage,
            onRetry = onRetryCourts,
            onDismiss = { showCourtPicker = false },
            onSelect = { court ->
                showCourtPicker = false
                onCourtSelected(court)
            }
        )
    }
}

@Composable
private fun CreateGameForm(
    uiState: CreateGameUiState,
    onFormatSelected: (Format) -> Unit,
    onPickTime: () -> Unit,
    onPickCourt: () -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Format", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Format.entries.forEach { format ->
                FilterChip(
                    selected = uiState.format == format,
                    onClick = { onFormatSelected(format) },
                    label = { Text(format.label) }
                )
            }
        }

        Text(text = "Start time", style = MaterialTheme.typography.titleMedium)
        PickerButton(
            icon = Icons.Default.DateRange,
            text = uiState.startsAt?.let { formatStartsAt(it) } ?: "Pick a date and time",
            onClick = onPickTime
        )

        Text(text = "Court", style = MaterialTheme.typography.titleMedium)
        PickerButton(
            icon = Icons.Default.Place,
            text = uiState.court?.name ?: "Pick a court",
            onClick = onPickCourt
        )

        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        SubmitButton(enabled = uiState.canSubmit, isSubmitting = uiState.isSubmitting, onClick = onSubmit)

        Text(
            text = "You'll be on team 1. The game shows on the feed until every spot is filled.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PickerButton(icon: ImageVector, text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(icon, contentDescription = null)
        Text(
            text = text,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        )
    }
}

@Composable
private fun SubmitButton(enabled: Boolean, isSubmitting: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Text("Schedule game")
        }
    }
}
