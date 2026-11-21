package com.donut.app.feature.matchmaking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.donut.app.core.ui.components.ChipStyle
import com.donut.app.core.ui.components.DonutCard
import com.donut.app.core.ui.components.LargeButtonHeight
import com.donut.app.core.ui.components.RingSpinner
import com.donut.app.core.ui.components.StatusChip
import com.donut.app.core.ui.theme.DonutTextStyles
import kotlinx.coroutines.delay

private const val TICK_MS = 1000L

/**
 * Shown instead of Find a match while waiting in the queue: a spinner, the time waited, a map of the
 * players searching nearby, the rules of a match and Leave queue
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
    DonutCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RingSpinner()
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = elapsedText(rememberSecondsInQueue()),
                style = DonutTextStyles.numericSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
            modifier = Modifier.padding(top = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            criteriaChips(format).forEach { StatusChip(text = it, style = ChipStyle.OUTLINE) }
        }
        OutlinedButton(
            onClick = onLeaveQueue,
            enabled = leaveEnabled,
            modifier = Modifier
                .padding(top = 9.dp)
                .fillMaxWidth()
                .height(LargeButtonHeight)
        ) {
            Text(text = "Leave queue")
        }
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
