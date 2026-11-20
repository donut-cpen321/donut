package com.example.cpen321application.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.cpen321application.core.ui.theme.DonutTheme
import com.example.cpen321application.core.ui.theme.StickerShadowOffset

/** The padding inside a card: 14 dp top and bottom, 16 dp at the sides */
val CardPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)

/**
 * The card every list item sits on: white, 24 dp corners. Cards you can tap, and featured ones,
 * are stickers (ink border and a solid shadow); the rest get a plain 1.5 dp outlineVariant border.
 * @param onClick makes the whole card a button, null for a card that only shows something
 * @param sticker draws it as a sticker; on by default for cards you can tap
 * @param color the card's fill, white by default
 * @param contentPadding the space between the edge and the content
 */
@Composable
fun DonutCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    sticker: Boolean = onClick != null,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    contentPadding: PaddingValues = CardPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    StickerSurface(
        modifier = modifier.fillMaxWidth(),
        color = color,
        contentColor = contentColorFor(color),
        onClick = onClick,
        shadowOffset = if (sticker) StickerShadowOffset else 0.dp,
        border = if (sticker) DonutTheme.colors.stickerBorder else MaterialTheme.colorScheme.outlineVariant,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            content = content,
        )
    }
}
