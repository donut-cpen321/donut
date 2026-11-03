package com.example.cpen321application.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cpen321application.core.data.model.MatchOutcome

val WinColor = Color(0xFF2E7D32)
val LossColor = Color(0xFFC62828)
private val WinContainer = Color(0xFFE8F5E9)
private val LossContainer = Color(0xFFFFEBEE)

/**
 * A "WIN" or "LOSS" chip, or "—" when the score has no winner
 * @param outcome how the game went for the signed-in user
 * @param textStyle the chip's text size
 */
@Composable
fun MatchOutcomeBadge(
    outcome: MatchOutcome?,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.labelMedium,
) {
    val (label, color, container) = when (outcome) {
        MatchOutcome.WIN -> Triple("WIN", WinColor, WinContainer)
        MatchOutcome.LOSS -> Triple("LOSS", LossColor, LossContainer)
        null -> Triple("—", MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surface)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text = label, color = color, style = textStyle, fontWeight = FontWeight.Bold)
    }
}
