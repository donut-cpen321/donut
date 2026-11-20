package com.example.cpen321application.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.R
import com.example.cpen321application.core.ui.avatarInitials
import com.example.cpen321application.core.ui.avatarTone
import com.example.cpen321application.core.ui.theme.DonutTextStyles
import com.example.cpen321application.core.ui.theme.DonutTheme
import com.example.cpen321application.core.ui.theme.StickerBorderWidth

// The initials take 36% of the avatar's size
private const val INITIALS_SCALE = 0.36f
private val SLOT_SIZE = 32.dp
private val SLOT_DASH = 3.dp
// Each avatar covers the last 8 dp of the one before it
private val SLOT_OVERLAP = (-8).dp

/**
 * A round avatar with the player's initials in Bricolage and an ink border, on one of four soft
 * tones picked from their name, so the same player always gets the same colour. The backend has
 * no profile pictures. Decorative: the name is always shown next to it.
 * @param name the player's name, e.g. "Alex Rivera" → "AR"
 * @param size the avatar's diameter
 */
@Composable
fun PlayerAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val (container, content) = toneColors(avatarTone(name))
    Box(
        modifier = modifier
            .size(size)
            .background(container, CircleShape)
            .border(StickerBorderWidth, DonutTheme.colors.stickerBorder, CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        val fontSize = (size.value * INITIALS_SCALE).sp
        Text(
            text = avatarInitials(name),
            color = content,
            style = DonutTextStyles.numericMedium.copy(fontSize = fontSize, lineHeight = fontSize, letterSpacing = 0.sp),
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
        else -> colors.primaryContainer to colors.onPrimaryContainer
    }
}

/**
 * The players of a game as overlapping 32 dp avatars, then a dashed circle with a + for each open spot
 * @param names the players' names
 * @param capacity how many players the game takes
 */
@Composable
fun PlayerSlots(names: List<String>, capacity: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(SLOT_OVERLAP),
    ) {
        names.forEach { name -> PlayerAvatar(name = name, size = SLOT_SIZE) }
        repeat((capacity - names.size).coerceAtLeast(0)) {
            Box(
                modifier = Modifier
                    .size(SLOT_SIZE)
                    .background(colors.surfaceContainerLowest, CircleShape)
                    .drawBehind {
                        val dash = SLOT_DASH.toPx()
                        val width = StickerBorderWidth.toPx()
                        drawCircle(
                            color = colors.outline,
                            radius = size.minDimension / 2 - width / 2,
                            style = Stroke(width = width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
