package com.donut.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.donut.app.core.ui.avatarInitials
import com.donut.app.core.ui.avatarTone
import com.donut.app.core.ui.theme.DonutTheme

// The initials take 36% of the avatar's size
private const val INITIALS_SCALE = 0.36f
private val SLOT_SIZE = 28.dp
private val SLOT_RING = 2.dp
private val SLOT_DASH = 3.dp
// Each avatar covers the last 6 dp of the one before it
private val SLOT_OVERLAP = (-10).dp

/**
 * A round avatar with the player's profile picture if available (from GET users/:userid/photo),
 * otherwise falling back to initials on one of four tones picked from their name.
 * @param name the player's name, e.g. "Alex Rivera" → "AR"
 * @param avatarUrl the profile picture URL, null/blank to use initials
 * @param size the avatar's diameter
 */
@Composable
fun PlayerAvatar(
    name: String,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    size: Dp = 40.dp,
) {
    if (!avatarUrl.isNullOrBlank()) {
        SubcomposeAsyncImage(
            model = avatarUrl,
            contentDescription = name,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .clearAndSetSemantics {},
            contentScale = ContentScale.Crop,
            loading = {
                InitialsAvatar(name = name, size = size)
            },
            error = {
                InitialsAvatar(name = name, size = size)
            },
        )
    } else {
        InitialsAvatar(name = name, modifier = modifier, size = size)
    }
}

@Composable
private fun InitialsAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val (container, content) = toneColors(avatarTone(name))
    Box(
        modifier = modifier
            .size(size)
            .background(container, CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = avatarInitials(name),
            color = content,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = (size.value * INITIALS_SCALE).sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun toneColors(tone: Int): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    val donut = DonutTheme.colors
    return when (tone) {
        1 -> colors.secondaryContainer to colors.onSecondaryContainer
        2 -> colors.tertiaryContainer to colors.onTertiaryContainer
        3 -> donut.infoContainer to donut.onInfoContainer
        else -> colors.surfaceContainerHigh to colors.onSurface
    }
}

/**
 * The players of a game as overlapping 28 dp avatars, then a dashed circle for each open spot
 * @param names the players' names
 * @param capacity how many players the game takes
 * @param ringColor the colour behind the row, drawn as a ring so the avatars read as separate
 */
@Composable
fun PlayerSlots(
    names: List<String>,
    capacity: Int,
    modifier: Modifier = Modifier,
    ringColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    avatarUrls: List<String?> = emptyList(),
) {
    val outline = MaterialTheme.colorScheme.outline
    Row(
        modifier = modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(SLOT_OVERLAP),
    ) {
        names.forEachIndexed { index, name ->
            Box(
                modifier = Modifier
                    .background(ringColor, CircleShape)
                    .padding(SLOT_RING),
            ) {
                PlayerAvatar(name = name, avatarUrl = avatarUrls.getOrNull(index), size = SLOT_SIZE)
            }
        }
        repeat((capacity - names.size).coerceAtLeast(0)) {
            Box(
                modifier = Modifier
                    .background(ringColor, CircleShape)
                    .padding(SLOT_RING)
                    .size(SLOT_SIZE)
                    .drawBehind {
                        val dash = SLOT_DASH.toPx()
                        drawCircle(
                            color = outline,
                            radius = size.minDimension / 2 - 1.dp.toPx(),
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
                            ),
                        )
                    },
            )
        }
    }
}
