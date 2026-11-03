package com.example.cpen321application.feature.scheduledgame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cpen321application.core.data.model.Tournament
import com.example.cpen321application.core.data.model.TournamentStatus
import com.example.cpen321application.core.ui.forecastText
import com.example.cpen321application.core.ui.formatStartsAt
import com.example.cpen321application.core.ui.label
import com.example.cpen321application.core.ui.tournamentPlayerCountText

/**
 * A tournament in the Games tab, marked with a "★ TOURNAMENT" badge so it stands out from the games
 * @param tournament the tournament
 * @param isRegistered whether the signed-in user is registered
 * @param onClick opens the tournament's screen
 */
@Composable
fun TournamentCard(
    tournament: Tournament,
    isRegistered: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TournamentBadge()
            Text(
                text = tournament.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(text = formatStartsAt(tournament.startsAt), style = MaterialTheme.typography.bodyMedium)
            Text(text = tournament.court.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${tournament.format.label} · ${progressText(tournament)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (tournament.forecast != null) {
                Text(
                    text = forecastText(tournament.forecast),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isRegistered) {
                Text(
                    text = "You're registered",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun TournamentBadge() {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "TOURNAMENT",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun progressText(tournament: Tournament): String =
    if (tournament.status == TournamentStatus.REGISTRATION) {
        tournamentPlayerCountText(tournament)
    } else {
        tournament.status.label
    }
