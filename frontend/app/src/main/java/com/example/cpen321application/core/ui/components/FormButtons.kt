package com.example.cpen321application.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.cpen321application.R
import com.example.cpen321application.core.ui.theme.DonutTheme
import com.example.cpen321application.core.ui.theme.TextFieldShape

/** Height of a screen's main button, e.g. Find me a match or Schedule game */
val LargeButtonHeight = 56.dp

/** Height of a button inside a row or a header, e.g. Schedule or Join team 1 */
val SmallButtonHeight = 42.dp

// Disabled buttons: content at 38%, as in Material
private const val DISABLED_CONTENT_ALPHA = 0.38f

/** The fills of a sticker button */
enum class ButtonStyle {
    PRIMARY,    // clay: the one main action of a screen
    SECONDARY,  // white
    TONAL,      // ball yellow, e.g. Invite friends
    DANGER,     // soft red, e.g. Cancel game
}

/**
 * A Clubhouse pill button: an ink border and a solid shadow that it sinks onto when pressed.
 * Disabled, it loses the shadow and turns grey.
 * @param style its fill
 * @param height 56 dp for a screen's main actions, SmallButtonHeight inside rows
 */
@Composable
fun DonutButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.PRIMARY,
    enabled: Boolean = true,
    height: Dp = LargeButtonHeight,
    contentPadding: PaddingValues = PaddingValues(horizontal = if (height < LargeButtonHeight) 16.dp else 24.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val (container, contentColor) = if (enabled) buttonColors(style) else {
        colors.surfaceContainerHigh to colors.onSurface.copy(alpha = DISABLED_CONTENT_ALPHA)
    }
    StickerSurface(
        modifier = modifier.height(height),
        shape = CircleShape,
        color = container,
        contentColor = contentColor,
        onClick = onClick,
        enabled = enabled,
        shadowOffset = if (enabled) 3.dp else 0.dp,
        border = if (enabled) DonutTheme.colors.stickerBorder else colors.outlineVariant,
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            val textStyle = if (height < LargeButtonHeight) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge
            CompositionLocalProvider(LocalTextStyle provides textStyle) { content() }
        }
    }
}

/**
 * DonutButton with a text and an optional filled icon before it
 * @param icon e.g. R.drawable.ic_sports_tennis_filled
 */
@Composable
fun DonutButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.PRIMARY,
    enabled: Boolean = true,
    height: Dp = LargeButtonHeight,
    @DrawableRes icon: Int? = null,
) {
    DonutButton(onClick = onClick, modifier = modifier, style = style, enabled = enabled, height = height) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun buttonColors(style: ButtonStyle): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    return when (style) {
        ButtonStyle.PRIMARY -> colors.primary to colors.onPrimary
        ButtonStyle.SECONDARY -> colors.surfaceContainerLowest to colors.onSurface
        ButtonStyle.TONAL -> colors.tertiary to colors.onTertiary
        ButtonStyle.DANGER -> colors.errorContainer to colors.onErrorContainer
    }
}

/**
 * A low-key action in clay, underlined, e.g. Leave the queue or Change court
 * @param color the text's colour, clay by default
 */
@Composable
fun UnderlinedTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(textDecoration = TextDecoration.Underline),
            color = if (enabled) color else LocalContentColor.current,
        )
    }
}

/**
 * A full-width button that opens a picker, e.g. for the start time or the court of a form.
 * Drawn like a text field (18 dp corners, 1.5 dp border) with an icon bubble and a chevron.
 * @param icon shown before the text, e.g. R.drawable.ic_calendar_month
 * @param text what was picked so far, or a hint to pick something
 * @param onClick opens the picker
 */
@Composable
fun PickerButton(@DrawableRes icon: Int, text: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    StickerSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = TextFieldShape,
        onClick = onClick,
        shadowOffset = 0.dp,
        border = colors.outlineVariant,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBubble(icon = icon, container = colors.primaryContainer, content = colors.primary, size = 38.dp)
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The full-width primary pill at the bottom of a form, with a spinner while the form is sent
 * @param label the button's text, e.g. "Schedule game"
 * @param enabled whether the form can be sent
 * @param isSubmitting whether the form was sent and has no answer yet
 * @param onClick sends the form
 */
@Composable
fun SubmitButton(label: String, enabled: Boolean, isSubmitting: Boolean, onClick: () -> Unit) {
    DonutButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        if (isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = LocalContentColor.current,
                strokeWidth = 2.dp
            )
        } else {
            Text(label)
        }
    }
}

/**
 * A full-width soft-red sticker pill, for actions that can't be undone, e.g. Cancel game.
 * It should open a ConfirmDialog rather than act straight away.
 */
@Composable
fun DestructiveButton(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    DonutButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        style = ButtonStyle.DANGER,
        modifier = modifier.fillMaxWidth(),
    )
}
