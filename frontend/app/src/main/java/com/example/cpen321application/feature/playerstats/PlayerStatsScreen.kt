package com.example.cpen321application.feature.playerstats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cpen321application.core.network.dto.OpponentRatingBandDto
import com.example.cpen321application.core.network.dto.PlayerStatsDto
import com.example.cpen321application.core.ui.components.BackHeader
import com.example.cpen321application.core.ui.components.UnderlinedTextButton
import com.example.cpen321application.core.ui.ratingChangeText
import com.example.cpen321application.core.ui.theme.DonutTheme
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val ChartHeight = 180.dp

// Column widths of the opponent-band table, relative to each other
private const val ELO_COLUMN_WEIGHT = 1.4f
private const val MARGIN_COLUMN_WEIGHT = 1.2f

@Composable
fun PlayerStatsRoute(viewModel: PlayerStatsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    PlayerStatsScreen(uiState = uiState, onBack = onBack, onRetry = viewModel::load)
}

@Composable
fun PlayerStatsScreen(
    uiState: PlayerStatsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        BackHeader(title = "Stats", onBack = onBack)

        val stats = uiState.stats
        when {
            stats == null && uiState.isLoading -> Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            stats == null -> LoadError(message = uiState.errorMessage, onRetry = onRetry)
            else -> PlayerStatsContent(stats = stats, uiState = uiState)
        }
    }
}

@Composable
private fun LoadError(message: String?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message ?: "Your stats could not be loaded.",
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        UnderlinedTextButton(text = "Retry", onClick = onRetry)
    }
}

@Composable
private fun PlayerStatsContent(stats: PlayerStatsDto, uiState: PlayerStatsUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RatingCard(stats = stats, uiState = uiState)
        TotalsCard(stats = stats, winRatePercent = uiState.winRatePercent)
        OpponentBandsCard(bands = stats.vsOpponentRating)
    }
}

@Composable
private fun RatingCard(stats: PlayerStatsDto, uiState: PlayerStatsUiState) {
    SectionCard(title = "Elo") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatValue(label = "Current", value = stats.user?.rating?.roundToInt()?.toString() ?: "–")
            StatValue(label = "Rank", value = stats.user?.rank?.let { "#$it" } ?: "–")
            StatValue(
                label = "Change",
                value = uiState.totalRatingChange?.let { ratingChangeText(it) } ?: "–",
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (uiState.chartRatings.isEmpty()) {
            Text(
                text = "Play a rated match to start your Elo chart.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            EloChart(
                ratings = uiState.chartRatings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ChartHeight),
            )
        }
    }
}

// In tennis, points are the games won and lost, summed over every set
@Composable
private fun TotalsCard(stats: PlayerStatsDto, winRatePercent: Int?) {
    SectionCard(title = "Totals") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatValue(label = "Matches", value = stats.gamesPlayed.toString())
            StatValue(label = "Record", value = "${stats.wins}–${stats.losses}")
            StatValue(label = "Win rate", value = winRatePercent?.let { "$it%" } ?: "–")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatValue(label = "Sets played", value = stats.setsPlayed.toString())
            StatValue(label = "Sets won–lost", value = "${stats.setsWon}–${stats.setsLost}")
            StatValue(label = "Games won–lost", value = "${stats.pointsWon}–${stats.pointsLost}")
        }
    }
}

@Composable
private fun OpponentBandsCard(bands: List<OpponentRatingBandDto>) {
    SectionCard(title = "Against opponents by Elo") {
        if (bands.isEmpty()) {
            Text(
                text = "No rated matches yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            BandRow(elo = "Opponent Elo", played = "Played", record = "W–L", margin = "Avg margin", isHeader = true)
            bands.forEach { band ->
                HorizontalDivider()
                BandRow(
                    elo = "${band.minRating}–${band.maxRating - 1}",
                    played = band.games.toString(),
                    record = "${band.wins}–${band.losses}",
                    margin = marginText(band.averageMargin),
                )
            }
        }
    }
}

/**
 * The average margin of a band with one decimal and its sign, e.g. "+2.5", "−1.0" or "±0.0"
 * @param margin points won minus points lost, per match
 */
internal fun marginText(margin: Double): String {
    val text = String.format(Locale.US, "%.1f", abs(margin))
    return when {
        text == "0.0" -> "±0.0"
        margin > 0 -> "+$text"
        else -> "−$text"
    }
}

@Composable
private fun BandRow(elo: String, played: String, record: String, margin: String, isHeader: Boolean = false) {
    val style = if (isHeader) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    val marginColor = when {
        isHeader -> MaterialTheme.colorScheme.onSurfaceVariant
        margin.startsWith("+") -> DonutTheme.colors.win
        margin.startsWith("−") -> DonutTheme.colors.loss
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = elo, style = style, modifier = Modifier.weight(ELO_COLUMN_WEIGHT))
        Text(text = played, style = style, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(text = record, style = style, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(
            text = margin,
            style = style,
            color = marginColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(MARGIN_COLUMN_WEIGHT),
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun StatValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
