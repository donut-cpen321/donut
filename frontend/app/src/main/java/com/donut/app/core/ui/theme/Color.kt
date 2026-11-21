package com.donut.app.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Option A · Baseline. Generated from TOKENS in documentation/design/design-directions.html

val LightColors = lightColorScheme(
    primary = Color(0xFF111714),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE7E9E4),
    onPrimaryContainer = Color(0xFF111714),
    secondary = Color(0xFF0F7B4F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1F2E8),
    onSecondaryContainer = Color(0xFF0A3D27),
    tertiary = Color(0xFFC9EE3F),
    onTertiary = Color(0xFF1B2405),
    tertiaryContainer = Color(0xFFF0F9C8),
    onTertiaryContainer = Color(0xFF2C3A00),
    error = Color(0xFFC9372C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFCE8E6),
    onErrorContainer = Color(0xFF5C130D),
    background = Color(0xFFF7F7F4),
    onBackground = Color(0xFF111714),
    surface = Color(0xFFF7F7F4),
    onSurface = Color(0xFF111714),
    surfaceVariant = Color(0xFFEDEEE9),
    onSurfaceVariant = Color(0xFF5D6862),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F3EF),
    surfaceContainer = Color(0xFFEDEEE9),
    surfaceContainerHigh = Color(0xFFE7E8E3),
    surfaceContainerHighest = Color(0xFFE1E3DD),
    outline = Color(0xFF8E9892),
    outlineVariant = Color(0xFFE2E4DF),
    inverseSurface = Color(0xFF1A201D),
    inverseOnSurface = Color(0xFFF1F2EE),
    inversePrimary = Color(0xFFF2F4F0)
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFF2F4F0),
    onPrimary = Color(0xFF111714),
    primaryContainer = Color(0xFF2A302C),
    onPrimaryContainer = Color(0xFFF2F4F0),
    secondary = Color(0xFF46D69A),
    onSecondary = Color(0xFF00391F),
    secondaryContainer = Color(0xFF123D2A),
    onSecondaryContainer = Color(0xFFBDF0D6),
    tertiary = Color(0xFFD4F55A),
    onTertiary = Color(0xFF1B2405),
    tertiaryContainer = Color(0xFF34420A),
    onTertiaryContainer = Color(0xFFEEFBB9),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF4A0D08),
    errorContainer = Color(0xFF5A1A14),
    onErrorContainer = Color(0xFFFFDAD5),
    background = Color(0xFF0C0E0D),
    onBackground = Color(0xFFF2F4F0),
    surface = Color(0xFF0C0E0D),
    onSurface = Color(0xFFF2F4F0),
    surfaceVariant = Color(0xFF1F2421),
    onSurfaceVariant = Color(0xFFA3ADA7),
    surfaceContainerLowest = Color(0xFF131614),
    surfaceContainerLow = Color(0xFF161A18),
    surfaceContainer = Color(0xFF1B1F1D),
    surfaceContainerHigh = Color(0xFF222724),
    surfaceContainerHighest = Color(0xFF2A302C),
    outline = Color(0xFF6F7A74),
    outlineVariant = Color(0xFF272C29),
    inverseSurface = Color(0xFFF2F4F0),
    inverseOnSurface = Color(0xFF111714),
    inversePrimary = Color(0xFF111714)
)

/** Roles Material 3 has no slot for: match outcomes, live state, the ball highlight, weather, medals */
@Immutable
data class DonutColors(
    val win: Color,
    val winContainer: Color,
    val onWinContainer: Color,
    val loss: Color,
    val lossContainer: Color,
    val onLossContainer: Color,
    val live: Color,
    val highlight: Color,
    val onHighlight: Color,
    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val medal1: Color,
    val medal2: Color,
    val medal3: Color
)

val LightDonutColors = DonutColors(
    win = Color(0xFF0F7B4F),
    winContainer = Color(0xFFE1F2E8),
    onWinContainer = Color(0xFF0A3D27),
    loss = Color(0xFFC9372C),
    lossContainer = Color(0xFFFCE8E6),
    onLossContainer = Color(0xFF5C130D),
    live = Color(0xFF0F7B4F),
    highlight = Color(0xFFC9EE3F),
    onHighlight = Color(0xFF1B2405),
    info = Color(0xFF2F6FD6),
    infoContainer = Color(0xFFE6EEFB),
    onInfoContainer = Color(0xFF0E2D5E),
    medal1 = Color(0xFFD9A21B),
    medal2 = Color(0xFF9AA4A0),
    medal3 = Color(0xFFB9703F)
)

val DarkDonutColors = DonutColors(
    win = Color(0xFF46D69A),
    winContainer = Color(0xFF123D2A),
    onWinContainer = Color(0xFFBDF0D6),
    loss = Color(0xFFFF8A80),
    lossContainer = Color(0xFF4A1712),
    onLossContainer = Color(0xFFFFDAD5),
    live = Color(0xFF46D69A),
    highlight = Color(0xFFD4F55A),
    onHighlight = Color(0xFF1B2405),
    info = Color(0xFF7FB2FF),
    infoContainer = Color(0xFF15284A),
    onInfoContainer = Color(0xFFD6E4FF),
    medal1 = Color(0xFFE8C65A),
    medal2 = Color(0xFFB9C2BD),
    medal3 = Color(0xFFD39A6A)
)

val LocalDonutColors = staticCompositionLocalOf { LightDonutColors }
