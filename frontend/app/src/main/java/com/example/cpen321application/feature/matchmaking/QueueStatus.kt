package com.example.cpen321application.feature.matchmaking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cpen321application.R
import com.example.cpen321application.core.ui.components.ChipStyle
import com.example.cpen321application.core.ui.components.DonutCard
import com.example.cpen321application.core.ui.components.StatusChip
import com.example.cpen321application.core.ui.components.TennisBall
import com.example.cpen321application.core.ui.components.UnderlinedTextButton
import kotlinx.coroutines.delay

private const val TICK_MS = 1000L

// The criteria chips, in the order criteriaChips gives them: distance, rating, start time
private val CRITERIA_LOOKS = listOf(
    ChipStyle.DATE to R.drawable.ic_location_on_filled,
    ChipStyle.OPEN to R.drawable.ic_leaderboard_filled,
    ChipStyle.BOOKED to R.drawable.ic_schedule_filled,
)

/**
 * Shown instead of Find me a match while waiting in the queue, as a sticker: a bouncing ball,
 * the time waited, a map of the players searching nearby, the rules of a match as coloured chips,
 * and Leave the queue
 * @param title e.g. "Finding a match near you"
 * @param nearby the players searching nearby, null until the first load
 * @param leaveEnabled false while a join or leave is on its way to the backend
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QueueStatus(
    title: String,
    format: MatchFormat,
    nearby: NearbyPlayers?,
    leaveEnabled: Boolean,
    onLeaveQueue: () -> Unit
) {
    DonutCard(sticker = true, contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TennisBall(bouncing = true)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Usually a few minutes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = elapsedText(rememberSecondsInQueue()),
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum")
            )
        }
        QueueMap(nearby = nearby, modifier = Modifier.padding(top = 12.dp))
        Text(
            text = nearbyText(nearby),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
        FlowRow(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            criteriaChips(format).forEachIndexed { index, text ->
                val (style, icon) = CRITERIA_LOOKS.getOrElse(index) { ChipStyle.DONE to null }
                StatusChip(text = text, style = style, icon = icon)
            }
        }
        UnderlinedTextButton(
            text = "Leave the queue",
            onClick = onLeaveQueue,
            enabled = leaveEnabled,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

// Counted on the phone from when the card first showed; survives rotation, not an app restart
@Composable
private fun rememberSecondsInQueue(): Long {
    val joinedAt = rememberSaveable { System.currentTimeMillis() }
    var seconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(joinedAt) {
        while (true) {
            seconds = (System.currentTimeMillis() - joinedAt) / TICK_MS
            delay(TICK_MS)
        }
    }
    return seconds
}
