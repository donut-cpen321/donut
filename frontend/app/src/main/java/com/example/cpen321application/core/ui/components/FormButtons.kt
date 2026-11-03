package com.example.cpen321application.core.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * A full-width button that opens a picker, e.g. for the start time or the court of a form
 * @param icon shown before the text
 * @param text what was picked so far, or a hint to pick something
 * @param onClick opens the picker
 */
@Composable
fun PickerButton(icon: ImageVector, text: String, onClick: () -> Unit) {
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

/**
 * The full-width button at the bottom of a form, with a spinner while the form is sent
 * @param label the button's text, e.g. "Schedule game"
 * @param enabled whether the form can be sent
 * @param isSubmitting whether the form was sent and has no answer yet
 * @param onClick sends the form
 */
@Composable
fun SubmitButton(label: String, enabled: Boolean, isSubmitting: Boolean, onClick: () -> Unit) {
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
            Text(label)
        }
    }
}
