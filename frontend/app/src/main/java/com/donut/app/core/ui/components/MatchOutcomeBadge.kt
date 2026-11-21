package com.donut.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.donut.app.core.data.model.MatchOutcome
import com.donut.app.core.ui.ratingChangeText
import com.donut.app.core.ui.theme.DonutTextStyles
import com.donut.app.core.ui.theme.DonutTheme

/**
 * A square "W" or "L" badge, or "—" when the score has no winner. TalkBack reads "Win" or "Loss".
 * @param outcome how the game went for the signed-in user
 * @param size the badge's width and height
 * @param textStyle the letter's style, Geist Mono 13 by default
 */
@Composable
fun OutcomeBadge(
    outcome: MatchOutcome?,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    textStyle: TextStyle = DonutTextStyles.numericSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
) {
    val donut = DonutTheme.colors
    val (letter, spoken) = when (outcome) {
        MatchOutcome.WIN -> "W" to "Win"
        MatchOutcome.LOSS -> "L" to "Loss"
        null -> "—" to "No result"
    }
    val (content, container) = when (outcome) {
        MatchOutcome.WIN -> donut.win to donut.winContainer
        MatchOutcome.LOSS -> donut.loss to donut.lossContainer
        null -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Box(
        modifier = modifier
            .size(size)
            .background(container, RoundedCornerShape(8.dp))
            .clearAndSetSemantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = letter, color = content, style = textStyle)
    }
}

/**
 * A rating change like "+14" or "−6" in mono, green when it went up and red when it went down
 * @param change the rounded change
 */
@Composable
fun RatingDelta(
    change: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = DonutTextStyles.numericSmall,
) {
    val donut = DonutTheme.colors
    Text(
        text = ratingChangeText(change),
        modifier = modifier,
        style = style,
        color = when {
            change > 0 -> donut.win
            change < 0 -> donut.loss
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}
