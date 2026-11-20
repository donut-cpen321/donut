package com.example.cpen321application.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.cpen321application.R
import com.example.cpen321application.core.ui.theme.BricolageGrotesque

// The donut replaces the "o": 0.6 em wide, sitting 0.035 em below the baseline
private const val MARK_SCALE = 0.6f
private const val MARK_DROP = 0.035f
private const val MARK_GAP = 0.01f

/**
 * The Donut mark in full colour: a frosted donut that is also a tennis ball (the launcher icon's
 * foreground). Decorative.
 * @param size its width and height
 */
@Composable
fun DonutMark(modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Image(
        painter = painterResource(R.drawable.ic_donut_mark),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

/**
 * "donut" in Bricolage Grotesque ExtraBold, lowercase, tracking −4%, in clay, with the donut mark
 * as the "o". TalkBack reads "donut".
 * @param fontSize the word's size
 */
@Composable
fun Wordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 32.sp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val density = LocalDensity.current
    val markSize = with(density) { (fontSize * MARK_SCALE).toDp() }
    val gap = with(density) { (fontSize * MARK_GAP).toDp() }
    val drop = with(density) { (fontSize * MARK_DROP).roundToPx() }
    val style = MaterialTheme.typography.displayLarge.copy(
        fontFamily = BricolageGrotesque,
        fontWeight = FontWeight.ExtraBold,
        fontSize = fontSize,
        lineHeight = fontSize,
        letterSpacing = (-0.04).em,
    )
    Row(modifier = modifier.clearAndSetSemantics { contentDescription = "donut" }) {
        Text(text = "d", color = color, style = style, modifier = Modifier.alignByBaseline())
        DonutMark(
            size = markSize,
            modifier = Modifier
                .padding(horizontal = gap)
                .alignBy { it.measuredHeight - drop },
        )
        Text(text = "nut", color = color, style = style, modifier = Modifier.alignByBaseline())
    }
}
