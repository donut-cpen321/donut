package com.example.cpen321application.core.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Asks the user to confirm an action that can't be undone, like leaving or cancelling
 * @param title the question, e.g. "Leave this game?"
 * @param text what happens if the user confirms
 * @param confirmLabel the confirm button's text, e.g. "Leave"
 * @param onDismiss called when the user taps Back or outside the dialog
 * @param onConfirm called when the user taps the confirm button
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Back") }
        }
    )
}
