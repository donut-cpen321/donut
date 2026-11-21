package com.donut.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val TAB_GAP = 22.dp
private val UNDERLINE = 2.dp

/**
 * Left-aligned text tabs on a hairline, with a 2 dp ink underline under the selected one
 * @param titles the tabs' names, in order
 * @param selectedIndex the open tab
 * @param onSelect called with the index of the tab the user tapped
 * @param badges a count shown after a tab's name, by index, e.g. pending friend requests
 */
@Composable
fun UnderlineTabs(
    titles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badges: Map<Int, Int> = emptyMap(),
) {
    val hairline = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val width = 1.dp.toPx()
                drawLine(hairline, Offset(0f, size.height - width / 2), Offset(size.width, size.height - width / 2), width)
            }
            .padding(horizontal = 20.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(TAB_GAP),
    ) {
        titles.forEachIndexed { index, title ->
            UnderlineTab(
                title = title,
                isSelected = index == selectedIndex,
                badge = badges[index] ?: 0,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun UnderlineTab(title: String, isSelected: Boolean, badge: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val ink = colors.onSurface
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .selectable(selected = isSelected, role = Role.Tab, onClick = onClick)
            .drawBehind {
                if (isSelected) {
                    val height = UNDERLINE.toPx()
                    drawRoundRect(
                        color = ink,
                        topLeft = Offset(0f, size.height - height),
                        size = Size(size.width, height),
                        cornerRadius = CornerRadius(height),
                    )
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = if (isSelected) ink else colors.onSurfaceVariant,
        )
        if (badge > 0) {
            CountBadge(count = badge)
        }
    }
}

/**
 * A small red count, e.g. the number of pending friend requests
 */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = 18.dp)
            .widthIn(min = 18.dp)
            .background(MaterialTheme.colorScheme.error, CircleShape)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onError,
            textAlign = TextAlign.Center,
        )
    }
}
