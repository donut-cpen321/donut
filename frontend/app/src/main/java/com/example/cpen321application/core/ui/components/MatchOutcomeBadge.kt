package com.example.cpen321application.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.core.data.model.MatchOutcome
import com.example.cpen321application.core.ui.ratingChangeText
import com.example.cpen321application.core.ui.theme.DonutTextStyles
import com.example.cpen321application.core.ui.theme.DonutTheme

// The badge leans like a stuck-on sticker
private const val BADGE_TILT = -8f

/**
 * A round "W" or "L" sticker, tilted −8°, or "—" when the score has no winner. W is grass,
 * L is clay on peach. TalkBack reads "Win" or "Loss".
 * @param outcome how the game went for the signed-in user
 * @param size the badge's diameter
 * @param textStyle the letter's style, Bricolage ExtraBold 17 by default
 */
@Composable
fun OutcomeBadge(
    outcome: MatchOutcome?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    textStyle: TextStyle = DonutTextStyles.numericMedium.copy(fontSize = 17.sp, lineHeight = 17.sp),
) {
    val colors = MaterialTheme.colorScheme
    val (letter, spoken) = when (outcome) {
        MatchOutcome.WIN -> "W" to "Win"
        MatchOutcome.LOSS -> "L" to "Loss"
        null -> "—" to "No result"
    }
    val (container, content) = when (outcome) {
        MatchOutcome.WIN -> colors.secondary to colors.onSecondary
        MatchOutcome.LOSS -> colors.primaryContainer to colors.primary
        null -> colors.surfaceContainerHigh to colors.onSurfaceVariant
    }
    StickerSurface(
        modifier = modifier
            .size(size)
            .rotate(BADGE_TILT)
            .clearAndSetSemantics { contentDescription = spoken },
        shape = CircleShape,
        color = container,
        contentColor = content,
        shadowOffset = 2.dp,
    ) {
        Text(text = letter, style = textStyle, modifier = Modifier.align(Alignment.Center))
    }
}

/**
 * A rating change like "+14" or "−6" on a pill: grass when it went up, clay when it went down
 * @param change the rounded change
 */
@Composable
fun RatingDelta(
    change: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = DonutTextStyles.numericSmall.copy(fontWeight = FontWeight.ExtraBold),
) {
    val donut = DonutTheme.colors
    val (container, content) = when {
        change > 0 -> donut.winContainer to donut.onWinContainer
        change < 0 -> donut.lossContainer to donut.onLossContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(modifier = modifier, shape = CircleShape, color = container, contentColor = content) {
        Box(modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)) {
            Text(text = ratingChangeText(change), style = style)
        }
    }
}
