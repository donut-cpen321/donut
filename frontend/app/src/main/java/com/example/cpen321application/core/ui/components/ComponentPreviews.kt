package com.example.cpen321application.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.R
import com.example.cpen321application.core.data.model.Forecast
import com.example.cpen321application.core.data.model.MatchOutcome
import com.example.cpen321application.core.ui.theme.CPEN321ApplicationTheme

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
        StatusChip("Tournament", ChipStyle.TOURNAMENT, icon = R.drawable.ic_emoji_events_filled)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusChip("Today · 6:00 PM", ChipStyle.DATE, icon = R.drawable.ic_calendar_month_filled)
        StatusChip("#14", ChipStyle.OUTLINE, icon = R.drawable.ic_leaderboard_filled)
        DonutSetChip()
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
    MessageBanner(message = "Friend request sent!", isError = false, onDismiss = {})
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RoundIconButton(icon = R.drawable.ic_settings, contentDescription = "Settings", onClick = {})
        RoundIconButton(icon = R.drawable.ic_add, contentDescription = "New game", onClick = {}, primary = true)
        TennisBall(bouncing = true)
    }
}

@LightDarkPreviews
@Composable
fun ButtonsPreview() = PreviewFrame {
    DonutButton(text = "Find me a match", onClick = {}, icon = R.drawable.ic_sports_tennis_filled, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DonutButton(text = "Not this time", onClick = {}, style = ButtonStyle.SECONDARY, modifier = Modifier.weight(1f))
        DonutButton(text = "I'm in!", onClick = {}, modifier = Modifier.weight(1f))
    }
    DonutButton(text = "Invite friends", onClick = {}, style = ButtonStyle.TONAL, icon = R.drawable.ic_person_add_filled)
    DestructiveButton(text = "Cancel game", enabled = true, onClick = {})
    DonutButton(text = "Find me a match", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
    UnderlinedTextButton(text = "Change court", onClick = {})
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
    Wordmark(fontSize = 48.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RingSpinner()
        DonutMark(size = 40.dp)
    }
}
