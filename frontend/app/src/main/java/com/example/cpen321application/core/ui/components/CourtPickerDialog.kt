package com.example.cpen321application.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.cpen321application.core.data.model.Court

/**
 * Lists the courts to pick one
 * @param courts the courts, empty while they load
 * @param selectedCourtId the court picked so far, null for none
 * @param isLoading whether the courts are still loading
 * @param errorMessage why the courts could not be loaded, null if they could
 * @param onRetry loads the courts again after an error
 * @param onDismiss called when the user closes the dialog without picking
 * @param onSelect called with the court the user tapped
 */
@Composable
fun CourtPickerDialog(
    courts: List<Court>,
    selectedCourtId: Int?,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (Court) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a court") },
        text = {
            when {
                isLoading -> CircularProgressIndicator()
                errorMessage != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
                courts.isEmpty() -> Text("No courts yet.")
                else -> LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(courts, key = { it.id }) { court ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = court.id == selectedCourtId,
                                    onClick = { onSelect(court) },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = court.id == selectedCourtId, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(court.name)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
