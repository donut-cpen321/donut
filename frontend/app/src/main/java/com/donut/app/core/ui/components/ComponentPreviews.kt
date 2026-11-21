package com.donut.app.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.donut.app.R
import com.donut.app.core.data.model.Forecast
import com.donut.app.core.data.model.MatchOutcome
import com.donut.app.core.ui.theme.CPEN321ApplicationTheme

// Android Studio previews of the shared components, each in the light and the dark theme

@Preview(name = "Light", widthDp = 360)
@Preview(name = "Dark", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class LightDarkPreviews

@Composable
private fun PreviewFrame(content: @Composable () -> Unit) {
    CPEN321ApplicationTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content()
            }
        }
    }
}

@LightDarkPreviews
@Composable
fun ChipsAndBadgesPreview() = PreviewFrame {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusChip("1 spot left", ChipStyle.OPEN)
        StatusChip("You're playing", ChipStyle.YOU)
        StatusChip("Booked", ChipStyle.BOOKED)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusChip("Completed", ChipStyle.DONE)
        StatusChip("Cancelled", ChipStyle.CANCELLED)
        StatusChip("Tournament", ChipStyle.TOURNAMENT, icon = R.drawable.ic_star_filled)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutcomeBadge(MatchOutcome.WIN)
        OutcomeBadge(MatchOutcome.LOSS)
        RatingDelta(14)
        RatingDelta(-6)
    }
}

@LightDarkPreviews
@Composable
fun PlayersAndWeatherPreview() = PreviewFrame {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Alex Rivera", "Priya Nair", "Kenji Watanabe", "Maya Chen").forEach { PlayerAvatar(it) }
    }
    PlayerSlots(names = listOf("Sofia Alvarez"), capacity = 2)
    WeatherPill(Forecast(temperature = 11.0, description = "few clouds", precipitationChance = 0.1))
}

@LightDarkPreviews
@Composable
fun ControlsPreview() = PreviewFrame {
    SegmentedControl(listOf("Singles", "Doubles", "Either"), "Doubles", { it }, {})
    UnderlineTabs(titles = listOf("History", "Friends", "Equipment"), selectedIndex = 0, onSelect = {}, badges = mapOf(1 to 2))
    MessageBanner(message = "Friend request sent", isError = false, onDismiss = {})
}

@LightDarkPreviews
@Composable
fun CardsPreview() = PreviewFrame {
    DonutCard(onClick = {}) {
        Text("Kitsilano Beach Park", style = MaterialTheme.typography.titleMedium)
        Text("Singles · 1 of 2 players", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Scoreboard(
        title = "Final · Singles",
        sides = listOf(
            ScoreboardSide("Alex Rivera", "Alex Rivera", listOf(6, 7), listOf(4, 5)),
            ScoreboardSide("Jonah Brooks", "Jonah Brooks", listOf(4, 5), listOf(6, 7)),
        ),
        footer = { RatingChangeFooter(before = 1573, after = 1587) },
    )
}

@LightDarkPreviews
@Composable
fun BrandPreview() = PreviewFrame {
    Wordmark(withMark = true)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RingSpinner()
        DonutMark()
    }
}
