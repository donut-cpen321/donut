package com.example.cpen321application.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.example.cpen321application.core.ui.theme.DonutTheme

/** The colours sprinkles come in */
enum class SprinkleTone { CLAY, GRASS, BALL, BLUE }

/**
 * One confetti sprinkle: a 14 × 5 dp rounded bar
 * @property x where it sits on a 360 dp wide frame, in dp
 * @property y where it sits, in dp from the top (scaled like x when the frame has a design height)
 * @property degrees how it leans
 */
data class Sprinkle(val x: Float, val y: Float, val degrees: Float, val tone: SprinkleTone)

// The design frames are 360 dp wide
private const val DESIGN_WIDTH = 360f

/**
 * Clubhouse confetti: sprinkles in clay, grass, ball yellow and blue, placed as in the design and
 * spread out on wider screens. Decorative.
 * @param designHeight the height of the design's frame in dp, so y is scaled to the real height;
 * null keeps y in dp
 */
@Composable
fun Sprinkles(sprinkles: List<Sprinkle>, modifier: Modifier = Modifier, designHeight: Float? = null) {
    val colors = MaterialTheme.colorScheme
    val info = DonutTheme.colors.info
    Canvas(modifier = modifier.clearAndSetSemantics {}) {
        val scaleX = size.width / DESIGN_WIDTH.dp.toPx()
        val scaleY = designHeight?.let { size.height / it.dp.toPx() } ?: 1f
        val bar = Size(14.dp.toPx(), 5.dp.toPx())
        sprinkles.forEach { sprinkle ->
            val topLeft = Offset(sprinkle.x.dp.toPx() * scaleX, sprinkle.y.dp.toPx() * scaleY)
            val color = when (sprinkle.tone) {
                SprinkleTone.CLAY -> colors.primary
                SprinkleTone.GRASS -> colors.secondary
                SprinkleTone.BALL -> colors.tertiary
                SprinkleTone.BLUE -> info
            }
            rotate(sprinkle.degrees, pivot = topLeft + Offset(bar.width / 2, bar.height / 2)) {
                drawRoundRect(color, topLeft = topLeft, size = bar, cornerRadius = CornerRadius(3.dp.toPx()))
            }
        }
    }
}
