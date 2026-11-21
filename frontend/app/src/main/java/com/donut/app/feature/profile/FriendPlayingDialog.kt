package com.donut.app.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.donut.app.core.data.model.Format
import com.donut.app.core.network.dto.FriendDto
import com.donut.app.core.network.dto.FriendGameDto
import com.donut.app.core.ui.components.LiveLabel
import com.donut.app.core.ui.formatStartTime
import com.donut.app.core.ui.label
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException

private const val MINUTES_PER_HOUR = 60

/**
 * What a friend is playing: the live label, the format and court, and when it started, with a button that
 * opens the game
 * @param friend the friend who is playing
 * @param game their game in progress
 * @param onOpenGame opens the game's screen
 * @param onDismiss closes the dialog
 */
@Composable
fun FriendPlayingDialog(
    friend: FriendDto,
    game: FriendGameDto,
    onOpenGame: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        // The name, then "@username" right under it, like the profile header and the friends list.
        // One line each, cut short, rather than wrapping in the large title style
        title = {
            Column {
                Text(text = friend.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "@${friend.username}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LiveLabel("Playing now")
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // The dialog's text slot is muted; the game is the main fact, so it's in full ink
                    Text(
                        text = friendGameTitle(game),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    startedText(game.startedAt, Instant.now())?.let { started ->
                        Text(
                            text = started,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenGame) { Text("View game") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

/**
 * @return e.g. "Singles · Almond Park Tennis Courts"
 */
internal fun friendGameTitle(game: FriendGameDto): String {
    val format = Format.entries.firstOrNull { it.name == game.format }?.label ?: game.format
    return "$format · ${game.courtName}"
}

/**
 * @param startedAt when the game started, as the backend sends it, e.g. "2026-11-19T20:38:18.328Z"
 * @param now the current time
 * @return e.g. "Started 8:38 PM · 12 min ago", or null if the time can't be read
 */
internal fun startedText(startedAt: String, now: Instant): String? {
    val started = try {
        Instant.parse(startedAt)
    } catch (e: DateTimeParseException) {
        return null
    }
    return "Started ${formatStartTime(started)} · ${elapsedText(Duration.between(started, now))}"
}

/**
 * @return "just now" under a minute, "12 min ago", or "1 h 5 min ago"
 */
internal fun elapsedText(elapsed: Duration): String {
    val minutes = elapsed.toMinutes()
    return when {
        minutes < 1 -> "just now"
        minutes < MINUTES_PER_HOUR -> "$minutes min ago"
        else -> "${minutes / MINUTES_PER_HOUR} h ${minutes % MINUTES_PER_HOUR} min ago"
    }
}
