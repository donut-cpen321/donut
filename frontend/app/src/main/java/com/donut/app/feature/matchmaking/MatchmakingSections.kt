package com.donut.app.feature.matchmaking

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.donut.app.R
import com.donut.app.core.ui.components.LargeButtonHeight
import com.donut.app.core.ui.components.SectionLabel
import com.donut.app.core.ui.components.SegmentedControl
import com.donut.app.core.ui.theme.DonutTextStyles

private val HOW_IT_WORKS = listOf(
    "Pick where you want to play",
    "Choose singles, doubles or either",
    "Play an hour later, nearby"
)

@Composable
internal fun PlayHeader() {
    Column(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = "Find a match", style = MaterialTheme.typography.headlineLarge)
        Text(
            text = "Players at your level, courts near you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Three numbered steps side by side, each under an ink rule
@Composable
internal fun HowItWorksSteps() {
    val ink = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HOW_IT_WORKS.forEachIndexed { index, step ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .drawBehind {
                        val width = 1.5.dp.toPx()
                        drawLine(ink, Offset(0f, width / 2), Offset(size.width, width / 2), width)
                    }
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "0${index + 1}",
                    style = DonutTextStyles.numericSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = step, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * A small uppercase section label, e.g. WHERE or FORMAT
 */
@Composable
internal fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    SectionLabel(text = text, modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp))
}

@Composable
internal fun LocationSection(
    uiState: MatchmakingUiState,
    onUseCurrentLocation: () -> Unit,
    onEnterAddress: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Eyebrow("Where")
    // One inset group with a hairline between the rows, instead of two cards
    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainerLowest,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(modifier = Modifier.selectableGroup()) {
            LocationOptionRow(
                icon = R.drawable.ic_my_location,
                selectedIcon = R.drawable.ic_my_location_filled,
                text = uiState.textFor(LocationOption.CURRENT_LOCATION),
                selected = uiState.selectedOption == LocationOption.CURRENT_LOCATION,
                enabled = !uiState.isSearching,
                onClick = onUseCurrentLocation
            )
            HorizontalDivider(color = colors.outlineVariant)
            LocationOptionRow(
                icon = R.drawable.ic_search,
                selectedIcon = R.drawable.ic_search,
                text = uiState.textFor(LocationOption.ADDRESS),
                selected = uiState.selectedOption == LocationOption.ADDRESS,
                enabled = !uiState.isSearching,
                onClick = onEnterAddress
            )
        }
    }
    uiState.errorMessage?.let { ErrorMessage(it) }
}

@Composable
private fun LocationOptionRow(
    @DrawableRes icon: Int,
    @DrawableRes selectedIcon: Int,
    text: OptionText,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // The icon tile fills with ink when the option is picked
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(if (selected) colors.primary else colors.surfaceContainer, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(if (selected) selectedIcon else icon),
                contentDescription = null,
                tint = if (selected) colors.onPrimary else colors.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
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

// A 22 dp circle that fills with ink and shows a check when picked. Clicks go to the whole row.
@Composable
private fun RadioCheck(selected: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(22.dp)
            .then(
                if (selected) {
                    Modifier.background(colors.primary, CircleShape)
                } else {
                    Modifier.border(1.5.dp, colors.outline, CircleShape)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
internal fun FormatSection(selected: MatchFormat, enabled: Boolean, onSelected: (MatchFormat) -> Unit) {
    Eyebrow("Format")
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
            Button(
                onClick = onFindMatch,
                enabled = uiState.canFindMatch,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LargeButtonHeight)
            ) {
                Text(text = "Find a match")
            }
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
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Text(
            text = message,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
