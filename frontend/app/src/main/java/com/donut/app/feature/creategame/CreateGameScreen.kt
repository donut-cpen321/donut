package com.donut.app.feature.creategame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.donut.app.R
import com.donut.app.core.data.model.Court
import com.donut.app.core.data.model.Format
import com.donut.app.core.ui.components.BackHeader
import com.donut.app.core.ui.components.FormLabel
import com.donut.app.core.ui.components.SegmentedControl
import com.donut.app.core.ui.components.CourtPickerDialog
import com.donut.app.core.ui.components.DateTimePickerDialog
import com.donut.app.core.ui.components.PickerButton
import com.donut.app.core.ui.components.SubmitButton
import com.donut.app.core.ui.formatStartsAt
import com.donut.app.core.ui.label
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
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FormLabel("Format")
        SegmentedControl(
            options = Format.entries,
            selected = uiState.format,
            label = { it.label },
            onSelect = onFormatSelected
        )

        FormLabel("Start time")
        PickerButton(
            icon = R.drawable.ic_calendar_month,
            text = uiState.startsAt?.let { formatStartsAt(it) } ?: "Pick a date and time",
            onClick = onPickTime
        )

        FormLabel("Court")
        PickerButton(
            icon = R.drawable.ic_location_on,
            text = uiState.court?.name ?: "Pick a court",
            onClick = onPickCourt
        )
        Spacer(modifier = Modifier.height(6.dp))

        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        SubmitButton(
            label = "Schedule game",
            enabled = uiState.canSubmit,
            isSubmitting = uiState.isSubmitting,
            onClick = onSubmit
        )

        Text(
            text = "You'll be on team 1. The game shows on the feed until every spot is filled.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
