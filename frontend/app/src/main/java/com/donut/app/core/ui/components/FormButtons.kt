package com.donut.app.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.donut.app.R

/** Height of a screen's main button, e.g. Find a match or Schedule game */
val LargeButtonHeight = 52.dp

/** Height of a button inside a row or a header, e.g. Schedule or Join team 1 */
val SmallButtonHeight = 40.dp

// The outline of a destructive button is the error colour at 40%
private const val DESTRUCTIVE_OUTLINE_ALPHA = 0.4f

/**
 * A full-width button that opens a picker, e.g. for the start time or the court of a form.
 * Drawn like a text field (14 dp corners, 1 dp outline) with a chevron at the end.
 * @param icon shown before the text, e.g. R.drawable.ic_calendar_month
 * @param text what was picked so far, or a hint to pick something
 * @param onClick opens the picker
 */
@Composable
fun PickerButton(@DrawableRes icon: Int, text: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = RoundedCornerShape(14.dp),
        color = colors.surfaceContainerLowest,
        border = BorderStroke(1.dp, colors.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The full-width pill at the bottom of a form, with a spinner while the form is sent
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
            .height(LargeButtonHeight)
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Text(label)
        }
    }
}

/**
 * A full-width outlined pill in the error colour, for actions that can't be undone, e.g. Cancel game.
 * It should open a ConfirmDialog rather than act straight away.
 */
@Composable
fun DestructiveButton(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val error = MaterialTheme.colorScheme.error
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(LargeButtonHeight),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = error),
        border = BorderStroke(1.dp, error.copy(alpha = if (enabled) DESTRUCTIVE_OUTLINE_ALPHA else DISABLED_OUTLINE_ALPHA))
    ) {
        Text(text)
    }
}

private const val DISABLED_OUTLINE_ALPHA = 0.12f
