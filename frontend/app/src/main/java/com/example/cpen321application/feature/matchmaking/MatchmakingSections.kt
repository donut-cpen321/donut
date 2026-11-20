package com.example.cpen321application.feature.matchmaking

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cpen321application.R
import com.example.cpen321application.core.ui.components.ChipStyle
import com.example.cpen321application.core.ui.components.DonutButton
import com.example.cpen321application.core.ui.components.FormLabel
import com.example.cpen321application.core.ui.components.IconBubble
import com.example.cpen321application.core.ui.components.SegmentedControl
import com.example.cpen321application.core.ui.components.StatusChip
import com.example.cpen321application.core.ui.components.StickerSurface
import com.example.cpen321application.core.ui.components.TennisBall
import com.example.cpen321application.core.ui.theme.DonutTheme
import com.example.cpen321application.core.ui.theme.StickerBorderWidth
import com.example.cpen321application.core.ui.theme.StickerShadowOffset

/** A step of "how it works": its icon, its text and which colour tile it sits on */
private data class Step(@DrawableRes val icon: Int, val text: String, val tile: Int)

private val HOW_IT_WORKS = listOf(
    Step(R.drawable.ic_location_on_filled, "Pick a spot", 0),
    Step(R.drawable.ic_group_filled, "Singles or doubles", 1),
    Step(R.drawable.ic_emoji_events_filled, "Players at your level", 2),
)

// The court drawing's own grid, as in the design: a 240 x 110 court in a -2 -8 244 126 view box
private const val COURT_VIEW_X = -2f
private const val COURT_VIEW_Y = -8f
private const val COURT_VIEW_W = 244f
private const val COURT_VIEW_H = 126f
private const val COURT_LINE_ALPHA = 0.85f

@Composable
internal fun PlayHeader() {
    Column(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "Hey there,",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Ready for a hit?",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() }
        )
    }
}

/**
 * A grass-green sticker with court lines and a bouncing ball: "On court in about an hour".
 * Decorative apart from the chip.
 */
@Composable
internal fun CourtCard() {
    val colors = MaterialTheme.colorScheme
    StickerSurface(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(112.dp),
        color = colors.secondary,
    ) {
        CourtLines(modifier = Modifier.fillMaxSize())
        TennisBall(
            size = 34.dp,
            bouncing = true,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 42.dp, top = 26.dp)
        )
        StatusChip(
            text = "On court in about an hour",
            style = ChipStyle.TOURNAMENT,
            icon = R.drawable.ic_bolt_filled,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 12.dp)
        )
    }
}

// The court from above in white, scaled to fit the card's height
@Composable
private fun CourtLines(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.clearAndSetSemantics {}) {
        // The drawing is 20 dp wider and 12 dp taller than the card, so its edges are cut off
        val width = size.width + 20.dp.toPx()
        val height = size.height + 12.dp.toPx()
        val scale = minOf(width / COURT_VIEW_W, height / COURT_VIEW_H)
        val left = (size.width - COURT_VIEW_W * scale) / 2 - COURT_VIEW_X * scale
        val top = (size.height - COURT_VIEW_H * scale) / 2 - COURT_VIEW_Y * scale
        fun point(x: Float, y: Float) = Offset(left + x * scale, top + y * scale)
        val line = Color.White.copy(alpha = COURT_LINE_ALPHA)
        val thin = 1.5f * scale
        drawRect(line, topLeft = point(1f, 1f), size = Size(238f * scale, 108f * scale), style = Stroke(thin))
        listOf(14.5f, 95.5f).forEach { y -> drawLine(line, point(1f, y), point(239f, y), thin) }
        listOf(56f, 184f).forEach { x -> drawLine(line, point(x, 14.5f), point(x, 95.5f), thin) }
        drawLine(line, point(56f, 55f), point(184f, 55f), thin)
        drawLine(line, point(1f, 55f), point(7f, 55f), thin)
        drawLine(line, point(233f, 55f), point(239f, 55f), thin)
        // The net
        drawLine(line, point(120f, -6f), point(120f, 116f), 3f * scale)
    }
}

// Three soft tiles: where, singles or doubles, your level
@Composable
internal fun HowItWorksSteps() {
    val colors = MaterialTheme.colorScheme
    val tiles = listOf(
        colors.primaryContainer to colors.onPrimaryContainer,
        colors.secondaryContainer to colors.onSecondaryContainer,
        colors.tertiaryContainer to colors.onTertiaryContainer,
    )
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HOW_IT_WORKS.forEach { step ->
            val (container, content) = tiles[step.tile]
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(container, RoundedCornerShape(16.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(painterResource(step.icon), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                Text(text = step.text, style = MaterialTheme.typography.labelMedium, color = content)
            }
        }
    }
}

/**
 * A question above a section, e.g. "Where do you want to play?"
 */
@Composable
internal fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    FormLabel(text = text, modifier = modifier.padding(start = 16.dp, end = 20.dp, top = 6.dp, bottom = 8.dp))
}

@Composable
internal fun LocationSection(
    uiState: MatchmakingUiState,
    onUseCurrentLocation: () -> Unit,
    onEnterAddress: () -> Unit
) {
    Eyebrow("Where do you want to play?")
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LocationOptionCard(
            icon = R.drawable.ic_my_location,
            selectedIcon = R.drawable.ic_my_location_filled,
            text = uiState.textFor(LocationOption.CURRENT_LOCATION),
            selected = uiState.selectedOption == LocationOption.CURRENT_LOCATION,
            enabled = !uiState.isSearching,
            onClick = onUseCurrentLocation
        )
        LocationOptionCard(
            icon = R.drawable.ic_edit,
            selectedIcon = R.drawable.ic_location_on_filled,
            text = uiState.textFor(LocationOption.ADDRESS),
            selected = uiState.selectedOption == LocationOption.ADDRESS,
            enabled = !uiState.isSearching,
            onClick = onEnterAddress
        )
    }
    uiState.errorMessage?.let { ErrorMessage(it) }
}

// A 20 dp card per option; the picked one becomes a sticker with a clay icon bubble and a green check
@Composable
private fun LocationOptionCard(
    @DrawableRes icon: Int,
    @DrawableRes selectedIcon: Int,
    text: OptionText,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    StickerSurface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.selected = selected },
        shape = RoundedCornerShape(20.dp),
        onClick = onClick,
        role = Role.RadioButton,
        enabled = enabled,
        shadowOffset = if (selected) StickerShadowOffset else 0.dp,
        border = if (selected) DonutTheme.colors.stickerBorder else colors.outlineVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconBubble(
                icon = if (selected) selectedIcon else icon,
                container = if (selected) colors.primary else colors.primaryContainer,
                content = if (selected) colors.onPrimary else colors.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                // A picked place can have a long name
                Text(
                    text = text.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(text = text.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            RadioCheck(selected = selected)
        }
    }
}

// A 24 dp circle that fills with grass and shows a check when picked. Clicks go to the whole card.
@Composable
private fun RadioCheck(selected: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(24.dp)
            .then(
                if (selected) {
                    Modifier
                        .background(colors.secondary, CircleShape)
                        .border(StickerBorderWidth, DonutTheme.colors.stickerBorder, CircleShape)
                } else {
                    Modifier.border(2.dp, colors.outlineVariant, CircleShape)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = colors.onSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
internal fun FormatSection(selected: MatchFormat, enabled: Boolean, onSelected: (MatchFormat) -> Unit) {
    Eyebrow("Singles or doubles?", modifier = Modifier.padding(top = 10.dp))
    SegmentedControl(
        options = MatchFormat.entries,
        selected = selected,
        label = { it.label },
        onSelect = onSelected,
        enabled = enabled,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
internal fun FindMatchBar(uiState: MatchmakingUiState, onFindMatch: () -> Unit, onLeaveQueue: () -> Unit) {
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (uiState.isSearching) {
            QueueStatus(
                title = searchingText(uiState.location),
                format = uiState.format,
                nearby = uiState.nearby,
                leaveEnabled = !uiState.isUpdatingQueue,
                onLeaveQueue = onLeaveQueue
            )
        } else {
            DonutButton(
                text = "Find me a match",
                onClick = onFindMatch,
                enabled = uiState.canFindMatch,
                icon = R.drawable.ic_sports_tennis_filled,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = criteriaText(uiState.format),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Surface(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Text(
            text = message,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
