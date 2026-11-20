package com.example.cpen321application.feature.matchmaking

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.cpen321application.R
import com.example.cpen321application.core.data.model.Game
import com.example.cpen321application.core.data.model.Player
import com.example.cpen321application.core.ui.components.ButtonStyle
import com.example.cpen321application.core.ui.components.CourtMap
import com.example.cpen321application.core.ui.components.DonutButton
import com.example.cpen321application.core.ui.components.IconBubble
import com.example.cpen321application.core.ui.components.PlayerAvatar
import com.example.cpen321application.core.ui.components.Sprinkle
import com.example.cpen321application.core.ui.components.SprinkleTone
import com.example.cpen321application.core.ui.components.Sprinkles
import com.example.cpen321application.core.ui.components.StickerSurface
import com.example.cpen321application.core.ui.formatStartTime
import com.example.cpen321application.core.ui.label
import com.example.cpen321application.core.ui.relativeStartText
import com.example.cpen321application.core.ui.theme.DonutTextStyles
import com.example.cpen321application.core.ui.theme.DonutTheme
import com.example.cpen321application.core.ui.theme.StickerBorderWidth
import com.example.cpen321application.core.ui.theme.sticker
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

// Players can accept until 30 minutes before the game starts (Product_Design §5.4)
private val ACCEPT_BEFORE_START: Duration = Duration.ofMinutes(30)
private val SHEET_SHAPE = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
private val AVATAR_SIZE = 64.dp
private const val VS_TILT = -10f

private val SPRINKLES = listOf(
    Sprinkle(40f, 26f, 30f, SprinkleTone.CLAY),
    Sprinkle(300f, 40f, -40f, SprinkleTone.GRASS),
    Sprinkle(28f, 88f, 70f, SprinkleTone.BALL),
    Sprinkle(318f, 96f, 15f, SprinkleTone.BLUE),
)

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
 * closed with Back: "It's a match!" with both sides, when and where, and I'm in! or Not this time.
 * After answering it shows Go to game (or Not this time).
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
        shape = SHEET_SHAPE,
        containerColor = MaterialTheme.colorScheme.surface,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false)
    ) {
        Box {
            Sprinkles(SPRINKLES, modifier = Modifier.fillMaxWidth().height(100.dp))
            Column(
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Versus(match = match, prompt = prompt)
                Text(
                    text = matchTitle(prompt),
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = matchSubtitle(prompt, match.format.label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                MatchDetails(match = match, prompt = prompt, distanceKm = distanceKm)
                if (errorMessage != null) {
                    Text(text = errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                MatchButtons(prompt, isAnswering, actions)
            }
        }
    }
}

// Your side against theirs: overlapping 64 dp avatar stickers with a tilted "vs" between them
@Composable
private fun Versus(match: Game, prompt: MatchPrompt) {
    val me = match.players.filter { it !in prompt.partners && it !in prompt.opponents }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy((-10).dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarStack(players = me + prompt.partners)
        StickerSurface(
            modifier = Modifier
                .zIndex(1f)
                .size(40.dp)
                .rotate(VS_TILT)
                .clearAndSetSemantics {},
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiary,
        ) {
            Text(
                text = "vs",
                style = DonutTextStyles.numericMedium.copy(fontSize = 15.sp, lineHeight = 15.sp),
                modifier = Modifier.align(Alignment.Center)
            )
        }
        AvatarStack(players = prompt.opponents)
    }
}

// In doubles each side has two players; the avatars overlap by 16 dp
@Composable
private fun AvatarStack(players: List<Player>) {
    val donut = DonutTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy((-16).dp)) {
        players.forEach { player ->
            PlayerAvatar(
                name = player.name,
                size = AVATAR_SIZE,
                modifier = Modifier.sticker(CircleShape, donut.stickerBorder, donut.stickerShadow)
            )
        }
    }
}

// When, where and what's next, each in a white row with a coloured icon bubble
@Composable
private fun MatchDetails(match: Game, prompt: MatchPrompt, distanceKm: Double?) {
    val colors = MaterialTheme.colorScheme
    val minutesToStart = Duration.between(Instant.now(), match.startsAt).toMinutes()
    val acceptBy = formatStartTime(match.startsAt.minus(ACCEPT_BEFORE_START))
    Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailRow(
            icon = R.drawable.ic_calendar_month_filled,
            bubble = colors.primaryContainer to colors.onPrimaryContainer,
            title = "${match.format.label} · ${relativeStartText(match.startsAt, LocalDate.now())}",
            subtitle = startsInText(minutesToStart)
        )
        DetailRow(
            icon = R.drawable.ic_location_on_filled,
            bubble = colors.secondaryContainer to colors.onSecondaryContainer,
            title = match.court.name,
            subtitle = distanceKm?.let { "${distanceText(it)} away" } ?: "Court"
        )
        if (!prompt.isBooked) {
            CourtMap(
                name = match.court.name,
                latitude = match.court.latitude,
                longitude = match.court.longitude,
                googlePlaceId = null,
                onTouchedChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .border(StickerBorderWidth, colors.outlineVariant, MaterialTheme.shapes.medium)
            )
        }
        DetailRow(
            icon = R.drawable.ic_timer_filled,
            bubble = colors.tertiaryContainer to colors.onTertiaryContainer,
            title = when {
                prompt.isBooked -> "Booked"
                prompt.hasAccepted -> "Waiting on the others"
                else -> "Say yes by $acceptBy"
            },
            subtitle = if (prompt.isBooked) "See you on court" else "It's booked once everyone's in"
        )
    }
}

@Composable
private fun DetailRow(@DrawableRes icon: Int, bubble: Pair<Color, Color>, title: String, subtitle: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerLowest, MaterialTheme.shapes.medium)
            .border(StickerBorderWidth, colors.outlineVariant, MaterialTheme.shapes.medium)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconBubble(icon = icon, container = bubble.first, content = bubble.second, size = 38.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun MatchButtons(prompt: MatchPrompt, isAnswering: Boolean, actions: MatchSheetActions) {
    Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // A booked game is cancelled from its own screen in Games
        if (!prompt.isBooked) {
            DonutButton(
                text = "Not this time",
                onClick = actions.onCancel,
                enabled = !isAnswering,
                style = ButtonStyle.SECONDARY,
                modifier = Modifier.weight(1f)
            )
        }
        val answered = prompt.hasAccepted || prompt.isBooked
        DonutButton(
            text = if (answered) "Go to game" else "I'm in!",
            onClick = if (answered) actions.onGoToGame else actions.onAccept,
            enabled = !isAnswering,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun MatchFellThroughDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Match fell through") },
        text = { Text("Tap Find me a match to search again.") },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}
