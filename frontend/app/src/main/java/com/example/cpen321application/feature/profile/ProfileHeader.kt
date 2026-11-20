package com.example.cpen321application.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cpen321application.R
import com.example.cpen321application.core.ui.components.DonutCard
import com.example.cpen321application.core.ui.components.PlayerAvatar
import com.example.cpen321application.core.ui.components.RatingDelta
import com.example.cpen321application.core.ui.components.RoundIconButton
import com.example.cpen321application.core.ui.components.StickerSurface
import com.example.cpen321application.core.ui.theme.DonutTextStyles
import com.example.cpen321application.core.ui.theme.DonutTheme

/**
 * "Profile" and a round settings button whose menu holds Sign out
 */
@Composable
internal fun ProfileTopBar(onSignOut: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Profile",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        Box {
            RoundIconButton(icon = R.drawable.ic_settings, contentDescription = "Settings", onClick = { showMenu = true })
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Sign out") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_logout), contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onSignOut()
                    },
                )
            }
        }
    }
}

/**
 * A peach sticker card with the avatar, real name and @username
 * @param name the real name, empty until the profile loads (the username is shown instead)
 */
@Composable
internal fun ProfileIdentity(name: String, username: String) {
    val title = name.ifBlank { username.ifEmpty { "User" } }
    DonutCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        sticker = true,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PlayerAvatar(name = title, size = 60.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (name.isNotBlank() && username.isNotBlank()) {
                    Text(
                        text = "@$username",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.alpha(USERNAME_ALPHA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// The @username is a little lighter than the name on the peach card
private const val USERNAME_ALPHA = 0.8f

/**
 * Three coloured sticker tiles: the rating (with the last match's change), the record and friends.
 * The rating and record tiles open the full stats screen; a chevron by RATING hints at it.
 * @param lastChange the most recent match's change, null if none was recorded
 * @param onOpenStats opens the full stats screen
 */
@Composable
internal fun StatTiles(rating: Int, lastChange: Int?, record: String, friends: Int, onOpenStats: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val donut = DonutTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatTile(
            label = "Rating",
            value = rating.toString(),
            color = colors.tertiaryContainer,
            weight = RATING_TILE_WEIGHT,
            onClick = onOpenStats,
            showChevron = true,
        ) {
            if (lastChange != null) {
                RatingDelta(change = lastChange, modifier = Modifier.padding(top = 2.dp))
            }
        }
        StatTile(label = "Record", value = record, color = colors.secondaryContainer, onClick = onOpenStats)
        StatTile(label = "Friends", value = friends.toString(), color = donut.infoContainer)
    }
}

// The rating tile is a little wider than the other two
private const val RATING_TILE_WEIGHT = 1.2f

// A tile with onClick opens the stats screen; showChevron puts a small arrow after its label
@Composable
private fun RowScope.StatTile(
    label: String,
    value: String,
    color: Color,
    weight: Float = 1f,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = false,
    extra: @Composable () -> Unit = {},
) {
    StickerSurface(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight(),
        shape = RoundedCornerShape(20.dp),
        color = color,
        onClick = onClick,
        onClickLabel = if (onClick != null) "View stats" else null,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = label.uppercase(), style = MaterialTheme.typography.labelSmall)
                if (showChevron) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Text(text = value, style = DonutTextStyles.numericMedium, maxLines = 1)
            extra()
        }
    }
}
