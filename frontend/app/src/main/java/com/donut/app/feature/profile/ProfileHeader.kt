package com.donut.app.feature.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.donut.app.R
import com.donut.app.core.ui.components.ChipStyle
import com.donut.app.core.ui.components.DonutCard
import com.donut.app.core.ui.components.PlayerAvatar
import com.donut.app.core.ui.components.StatusChip
import com.donut.app.core.ui.ratingChangeText
import com.donut.app.core.ui.theme.DonutTextStyles

// The area under the rating line is the green at 10%
private const val AREA_ALPHA = 0.1f
// Space above and below the line, in rating points, so the extremes don't touch the edges
private const val TREND_PADDING = 6

/**
 * "Profile" and a settings button whose menu holds Sign out
 */
@Composable
internal fun ProfileTopBar(onSignOut: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Profile", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings")
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Sign out") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_logout), contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onSignOut()
                    },
                )
            }
        }
    }
}

/**
 * Avatar, real name and @username
 * @param name the real name, empty until the profile loads (the username is shown instead)
 */
@Composable
internal fun ProfileIdentity(name: String, username: String, avatarUrl: String? = null) {
    val title = name.ifBlank { username.ifEmpty { "User" } }
    Row(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PlayerAvatar(name = title, avatarUrl = avatarUrl, size = 56.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (name.isNotBlank() && username.isNotBlank()) {
                Text(
                    text = "@$username",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The rating, big, with the last match's change and a line of the rating over the recorded matches
 * @param lastChange the most recent match's change, null if none was recorded
 * @param trend from ratingTrend; the graph is left out when it is empty
 * @param onOpenStats opens the full stats screen; the whole card is tappable
 */
@Composable
internal fun RatingCard(rating: Int, lastChange: Int?, trend: List<Int>, onOpenStats: () -> Unit) {
    DonutCard(modifier = Modifier.padding(horizontal = 16.dp), onClick = onOpenStats) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "RATING",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (lastChange != null) {
                StatusChip(
                    text = "${ratingChangeText(lastChange)} last match",
                    style = if (lastChange < 0) ChipStyle.DOWN else ChipStyle.UP,
                    icon = if (lastChange < 0) R.drawable.ic_trending_down_filled else R.drawable.ic_trending_up_filled,
                )
            }
        }
        Text(text = rating.toString(), style = DonutTextStyles.numericLarge, modifier = Modifier.padding(top = 4.dp))
        if (trend.isNotEmpty()) {
            RatingSparkline(
                points = trend,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(vertical = 4.dp),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Last ${trend.size - 1} matches",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${trend.first()} → ${trend.last()}",
                    style = DonutTextStyles.numericSmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ViewStatsLink()
    }
}

@Composable
private fun ViewStatsLink() {
    // Only a hint that the card opens the stats screen; the card itself handles the tap
    Row(modifier = Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = "View stats", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

// An ink line with a soft green area under it and a green dot on the latest rating
@Composable
private fun RatingSparkline(points: List<Int>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.secondary
    val spoken = "Rating from ${points.first()} to ${points.last()} over ${points.size - 1} matches"
    Canvas(modifier = modifier.clearAndSetSemantics { contentDescription = spoken }) {
        val low = points.min() - TREND_PADDING
        val range = (points.max() + TREND_PADDING - low).toFloat()
        val step = size.width / (points.size - 1)
        val offsets = points.mapIndexed { index, value ->
            Offset(index * step, size.height - (value - low) / range * size.height)
        }
        val path = Path().apply {
            moveTo(offsets.first().x, offsets.first().y)
            offsets.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val area = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, color = accent.copy(alpha = AREA_ALPHA))
        drawPath(path, color = line, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color = accent, radius = 4.dp.toPx(), center = offsets.last())
    }
}

/**
 * Three numbers side by side with hairlines between them, e.g. Record · Matches · Friends
 */
@Composable
internal fun StatsRow(stats: List<Pair<String, String>>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
            .height(IntrinsicSize.Min),
    ) {
        stats.forEachIndexed { index, (value, label) ->
            if (index > 0) {
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(text = value, style = DonutTextStyles.numericMedium)
                Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}
