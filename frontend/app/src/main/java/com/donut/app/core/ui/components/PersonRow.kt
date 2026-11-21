package com.donut.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Rows in a list: 20 dp from the screen edge, at least 56 dp tall
private val ROW_GUTTER = 20.dp
private val ROW_MIN_HEIGHT = 56.dp
private val ROW_AVATAR = 36.dp

/**
 * A person in a list: avatar, name, an optional second line, and whatever goes at the end
 * (a rating, a button). Rows are 56 dp tall and 20 dp in from the edge.
 * @param name shown as the title and used for the avatar's initials
 * @param supporting a second, muted line, e.g. "@mayac" or "Wants to be your friend"
 * @param supportingContent a second line drawn by the caller instead, e.g. a live dot and "Playing"
 * @param trailing put at the end of the row
 */
@Composable
fun PersonRow(
    name: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    avatarUrl: String? = null,
    avatarSize: Dp = ROW_AVATAR,
    supportingContent: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ROW_MIN_HEIGHT)
            .padding(horizontal = ROW_GUTTER, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlayerAvatar(name = name, avatarUrl = avatarUrl, size = avatarSize)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (supportingContent != null) {
                supportingContent()
            } else if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
    }
}

/**
 * An empty place in a team: a dashed circle where the avatar would be, then "Open spot"
 */
@Composable
fun OpenSpotRow(modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    val outline = MaterialTheme.colorScheme.outline
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ROW_MIN_HEIGHT)
            .padding(horizontal = ROW_GUTTER, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(ROW_AVATAR)
                .drawBehind {
                    val dash = 3.dp.toPx()
                    drawCircle(
                        color = outline,
                        radius = size.minDimension / 2 - 1.dp.toPx(),
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
                    )
                },
        )
        Text(
            text = "Open spot",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/**
 * A small uppercase label above a section, e.g. FORMAT or REQUESTS
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * A 4 dp bar, filled in ink, e.g. how many players have registered
 * @param filled from 0 to 1
 */
@Composable
fun FillBar(filled: Float, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier = modifier
            .height(4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(filled.coerceIn(0f, 1f))
                .background(MaterialTheme.colorScheme.onSurface, shape),
        )
    }
}

/**
 * The label above a form field, e.g. FORMAT or START TIME, with room above it
 */
@Composable
fun FormLabel(text: String) {
    SectionLabel(text = text, modifier = Modifier.padding(start = 4.dp, top = 12.dp))
}
