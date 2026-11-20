package com.example.cpen321application.core.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Option C · Clubhouse
val DonutShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

/** Text fields: 18 dp corners (M3 would use extraSmall) */
val TextFieldShape = RoundedCornerShape(18.dp)

/** The width of a sticker's ink border, and of the plain border on rows that aren't stickers */
val StickerBorderWidth = 1.5.dp

/** How far a sticker's solid shadow sits below it */
val StickerShadowOffset = 3.dp

/**
 * The Clubhouse "sticker": 1.5 dp ink border + solid shadow offset 3 dp down, no blur.
 * Apply BEFORE background(). For the pressed state see Sticker.kt: it sinks 2 dp and the shadow
 * drops to 1 dp, on spring(dampingRatio = 0.55f, stiffness = 400f).
 * @param offset how far the shadow sits below the shape
 */
fun Modifier.sticker(
    shape: Shape,
    border: Color,
    shadow: Color,
    offset: Dp = StickerShadowOffset,
    width: Dp = StickerBorderWidth
): Modifier = this
    .drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        translate(top = offset.toPx()) { drawOutline(outline, shadow) }
    }
    .border(width, border, shape)
