package com.donut.app.feature.matchmaking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.donut.app.R
import com.donut.app.core.data.model.Game
import com.donut.app.core.data.model.Player
import com.donut.app.core.ui.components.PlayerAvatar
import com.donut.app.core.ui.components.CourtMap
import com.donut.app.core.ui.components.LargeButtonHeight
import com.donut.app.core.ui.components.LiveLabel
import com.donut.app.core.ui.formatStartTime
import com.donut.app.core.ui.label
import com.donut.app.core.ui.relativeStartText
import com.donut.app.core.ui.theme.DonutTextStyles
import java.time.Duration
import java.time.LocalDate
import kotlin.math.roundToInt

// Players can accept until 30 minutes before the game starts (Product_Design §5.4)
private val ACCEPT_BEFORE_START: Duration = Duration.ofMinutes(30)
private const val PRIMARY_WEIGHT = 1.4f

/**
 * What the match sheet's buttons do
 * @property onCancel declines the match before answering, or cancels it after accepting
 */
data class MatchSheetActions(
    val onAccept: () -> Unit,
    val onCancel: () -> Unit,
    val onGoToGame: () -> Unit,
)

/**
 * The matched game waiting for the player's answer, as a bottom sheet that can't be swiped away or
 * closed with Back: Accept or Decline before answering, Go to game (or Decline) after.
 * @param distanceKm from where the player searched to the court, null when that place is not known
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchSheet(
    match: Game,
    prompt: MatchPrompt,
    distanceKm: Double?,
    isAnswering: Boolean,
    errorMessage: String?,
    actions: MatchSheetActions,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )
    ModalBottomSheet(
        onDismissRequest = {},
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false)
    ) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LiveLabel(matchTitle(prompt))
            Text(
                text = "${match.format.label} · ${relativeStartText(match.startsAt, LocalDate.now())}",
                style = MaterialTheme.typography.headlineSmall
            )
            if (!prompt.isBooked) {
                CourtSection(match = match, distanceKm = distanceKm)
            }
            Versus(match = match, prompt = prompt)
            MatchNote(match = match, prompt = prompt)
            if (errorMessage != null) {
                Text(text = errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            MatchButtons(prompt, isAnswering, actions.onAccept, actions.onCancel, actions.onGoToGame)
        }
    }
}

@Composable
private fun CourtSection(match: Game, distanceKm: Double?) {
    val shape = MaterialTheme.shapes.medium
    CourtMap(
        name = match.court.name,
        latitude = match.court.latitude,
        longitude = match.court.longitude,
        googlePlaceId = null,
        onTouchedChange = {},
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    )
    Column {
        Text(
            text = match.court.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (distanceKm != null) {
            Text(
                text = "${distanceText(distanceKm)} away",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MatchNote(match: Game, prompt: MatchPrompt) {
    val text = when {
        prompt.isBooked -> "Everyone accepted. See you on court."
        prompt.hasAccepted -> "Waiting for ${prompt.waitingFor.joinToString(", ") { it.name }} to accept."
        else -> "Accept by ${formatStartTime(match.startsAt.minus(ACCEPT_BEFORE_START))}. Everyone has to accept."
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            painter = painterResource(R.drawable.ic_timer),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MatchButtons(
    prompt: MatchPrompt,
    isAnswering: Boolean,
    onAccept: () -> Unit,
    onCancel: () -> Unit,
    onGoToGame: () -> Unit
) {
    Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // A booked game is cancelled from its own screen in Games
        if (!prompt.isBooked) {
            OutlinedButton(
                onClick = onCancel,
                enabled = !isAnswering,
                modifier = Modifier.weight(1f).height(LargeButtonHeight)
            ) { Text("Decline") }
        }
        val answered = prompt.hasAccepted || prompt.isBooked
        Button(
            onClick = if (answered) onGoToGame else onAccept,
            enabled = !isAnswering,
            modifier = Modifier.weight(PRIMARY_WEIGHT).height(LargeButtonHeight)
        ) { Text(if (answered) "Go to game" else "Accept") }
    }
}

@Composable
fun MatchFellThroughDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Match fell through") },
        text = { Text("Tap Find a match to search again.") },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}

private fun matchTitle(prompt: MatchPrompt): String = when {
    prompt.isBooked -> "Game on"
    prompt.hasAccepted -> "You're in"
    else -> "Match found"
}

// Your side against theirs: avatars, names, ratings. In doubles each side has two players.
@Composable
private fun Versus(match: Game, prompt: MatchPrompt) {
    val me = match.players.filter { it !in prompt.partners && it !in prompt.opponents }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TeamColumn(players = me + prompt.partners, firstIsMe = me.isNotEmpty(), modifier = Modifier.weight(1f))
        Text(
            text = "VS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TeamColumn(players = prompt.opponents, firstIsMe = false, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TeamColumn(players: List<Player>, firstIsMe: Boolean, modifier: Modifier = Modifier) {
    val ring = MaterialTheme.colorScheme.surfaceContainerLow
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(AVATAR_OVERLAP)) {
            players.forEach { player ->
                PlayerAvatar(
                    name = player.name,
                    avatarUrl = player.photoUrl,
                    size = 44.dp,
                    modifier = Modifier.border(2.dp, ring, CircleShape)
                )
            }
        }
        Text(
            text = players.mapIndexed { index, player -> if (firstIsMe && index == 0) "You" else player.name }
                .joinToString(" & "),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = players.joinToString(" · ") { it.rating.roundToInt().toString() },
            style = DonutTextStyles.numericSmall.copy(fontSize = 12.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val AVATAR_OVERLAP = (-10).dp
