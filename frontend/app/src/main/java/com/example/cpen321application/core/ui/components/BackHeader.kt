package com.example.cpen321application.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.cpen321application.R

// A disabled icon is drawn at 38%, as in Material
private const val DISABLED_ICON_ALPHA = 0.38f

/**
 * The title row of a screen opened from another one: a round back button, then the title
 * @param title the screen's title
 * @param onBack called when the user taps the back arrow
 */
@Composable
fun BackHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RoundIconButton(icon = R.drawable.ic_arrow_back, contentDescription = "Back", onClick = onBack)
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A 44 dp round sticker button with an icon, for top bars, e.g. Settings or New game
 * @param primary fills it with clay, for the screen's main action
 * @param enabled false greys out the icon and ignores taps
 */
@Composable
fun RoundIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    StickerSurface(
        modifier = modifier.size(44.dp),
        shape = CircleShape,
        color = if (primary) colors.primary else colors.surfaceContainerLowest,
        contentColor = when {
            !enabled -> colors.onSurface.copy(alpha = DISABLED_ICON_ALPHA)
            primary -> colors.onPrimary
            else -> colors.onSurface
        },
        onClick = onClick,
        enabled = enabled,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier
                .align(Alignment.Center)
                .size(24.dp),
        )
    }
}

/**
 * A round, coloured icon bubble, e.g. the calendar before a game's time
 * @param container the bubble's fill
 * @param content the icon's colour
 * @param size the bubble's diameter; the icon is 20 dp inside 36–40 dp bubbles
 */
@Composable
fun IconBubble(
    @DrawableRes icon: Int,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
    }
}
