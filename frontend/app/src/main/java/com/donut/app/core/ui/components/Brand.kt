package com.donut.app.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.donut.app.R
import com.donut.app.core.ui.theme.Geist

// The mark sits at 86% of the wordmark's cap height, 0.16 em before the word
private const val MARK_SCALE = 0.86f
private const val MARK_GAP = 0.16f

/**
 * The Donut mark: a ring cut by two tennis-ball seams (also the launcher and notification icon)
 * @param size its width and height
 * @param tint its colour, ink by default
 */
@Composable
fun DonutMark(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Icon(
        painter = painterResource(R.drawable.ic_donut_mark),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size),
    )
}

/**
 * "donut" in Geist Bold, lowercase, tight tracking
 * @param fontSize the word's size
 * @param withMark puts the mark before the word
 */
@Composable
fun Wordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 32.sp,
    withMark: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val markSize = with(LocalDensity.current) { (fontSize * MARK_SCALE).toDp() }
    val gap = with(LocalDensity.current) { (fontSize * MARK_GAP).toDp() }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        if (withMark) {
            DonutMark(size = markSize, tint = color)
        }
        Text(
            text = "donut",
            color = color,
            style = MaterialTheme.typography.displayLarge.copy(
                fontFamily = Geist,
                fontWeight = FontWeight.Bold,
                fontSize = fontSize,
                lineHeight = fontSize,
                letterSpacing = (-0.055).em,
            ),
        )
    }
}
