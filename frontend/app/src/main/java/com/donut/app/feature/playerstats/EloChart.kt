package com.donut.app.feature.playerstats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val LineWidth = 3.dp
private val PointRadius = 3.dp
private val GridWidth = 1.dp

/**
 * The lowest and highest rating on the chart's axis. A flat history gets a 1-point margin on
 * each side, so its line is drawn across the middle instead of on an edge.
 * @param ratings the ratings to plot, not empty
 * @return the bottom and top of the axis
 */
internal fun chartBounds(ratings: List<Double>): Pair<Double, Double> {
    val low = ratings.min()
    val high = ratings.max()
    return if (high > low) low to high else (low - 1) to (high + 1)
}

/**
 * Where a rating sits on the chart's axis
 * @return 0 at the bottom of the axis, 1 at the top
 */
internal fun chartFraction(rating: Double, bounds: Pair<Double, Double>): Float =
    ((rating - bounds.first) / (bounds.second - bounds.first)).toFloat()

/**
 * A line chart of a player's rating over their rated games, drawn on a Canvas.
 * The y-axis is labelled with the lowest and highest rating; each game is a point, oldest on the left.
 * @param ratings the ratings to plot, oldest first, at least one
 */
@Composable
fun EloChart(ratings: List<Double>, modifier: Modifier = Modifier) {
    if (ratings.isEmpty()) return
    val bounds = chartBounds(ratings)
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val description = "Elo chart from ${ratings.first().roundToInt()} to ${ratings.last().roundToInt()}"

    Row(modifier = modifier.semantics { contentDescription = description }) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = ratings.max().roundToInt().toString(), style = MaterialTheme.typography.labelSmall)
            Text(text = ratings.min().roundToInt().toString(), style = MaterialTheme.typography.labelSmall)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            val stepX = if (ratings.size > 1) size.width / (ratings.size - 1) else 0f
            val points = ratings.mapIndexed { index, rating ->
                Offset(index * stepX, size.height * (1f - chartFraction(rating, bounds)))
            }

            drawLine(gridColor, Offset(0f, 0f), Offset(size.width, 0f), GridWidth.toPx())
            drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), GridWidth.toPx())

            val path = Path()
            points.forEachIndexed { index, point ->
                if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = LineWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            points.forEach { point -> drawCircle(color = lineColor, radius = PointRadius.toPx(), center = point) }
        }
    }
}
