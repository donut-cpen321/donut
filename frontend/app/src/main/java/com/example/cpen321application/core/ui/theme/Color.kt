package com.example.cpen321application.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Option C · Clubhouse. Generated from TOKENS in documentation/design/design-directions.html

val LightColors = lightColorScheme(
    primary = Color(0xFFC8481E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCCB),
    onPrimaryContainer = Color(0xFF3E1100),
    secondary = Color(0xFF2F7D4E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3EEDB),
    onSecondaryContainer = Color(0xFF0B2E19),
    tertiary = Color(0xFFD9EE3A),
    onTertiary = Color(0xFF2A2F05),
    tertiaryContainer = Color(0xFFF2F8C4),
    onTertiaryContainer = Color(0xFF2A2F05),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFBF5EC),
    onBackground = Color(0xFF2A1E17),
    surface = Color(0xFFFBF5EC),
    onSurface = Color(0xFF2A1E17),
    surfaceVariant = Color(0xFFF1E6D6),
    onSurfaceVariant = Color(0xFF6E5D50),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F0E4),
    surfaceContainer = Color(0xFFF4EADB),
    surfaceContainerHigh = Color(0xFFF1E6D6),
    surfaceContainerHighest = Color(0xFFE9DCC8),
    outline = Color(0xFF8C7A6C),
    outlineVariant = Color(0xFFE8DBC8),
    inverseSurface = Color(0xFF33281F),
    inverseOnSurface = Color(0xFFFBF1E6),
    inversePrimary = Color(0xFFFF8A5C)
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8A5C),
    onPrimary = Color(0xFF3E1100),
    primaryContainer = Color(0xFF6E2A10),
    onPrimaryContainer = Color(0xFFFFDCCB),
    secondary = Color(0xFF6FCB92),
    onSecondary = Color(0xFF003920),
    secondaryContainer = Color(0xFF1D4B30),
    onSecondaryContainer = Color(0xFFC9F0D6),
    tertiary = Color(0xFFDDF25B),
    onTertiary = Color(0xFF2A2F05),
    tertiaryContainer = Color(0xFF424B0E),
    onTertiaryContainer = Color(0xFFF2F8C4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1B1512),
    onBackground = Color(0xFFFBF1E6),
    surface = Color(0xFF1B1512),
    onSurface = Color(0xFFFBF1E6),
    surfaceVariant = Color(0xFF3A2F28),
    onSurfaceVariant = Color(0xFFCDBBAC),
    surfaceContainerLowest = Color(0xFF262019),
    surfaceContainerLow = Color(0xFF221B17),
    surfaceContainer = Color(0xFF2A221D),
    surfaceContainerHigh = Color(0xFF352B25),
    surfaceContainerHighest = Color(0xFF40352E),
    outline = Color(0xFF9C897B),
    outlineVariant = Color(0xFF40352E),
    inverseSurface = Color(0xFFFBF1E6),
    inverseOnSurface = Color(0xFF2A1E17),
    inversePrimary = Color(0xFFC8481E)
)

/** Roles Material 3 has no slot for: match outcomes, live state, the ball highlight, weather, medals, stickers */
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
    val medal3: Color,
    val stickerBorder: Color,
    val stickerShadow: Color
)

val LightDonutColors = DonutColors(
    win = Color(0xFF2F7D4E),
    winContainer = Color(0xFFD3EEDB),
    onWinContainer = Color(0xFF0B2E19),
    loss = Color(0xFFC8481E),
    lossContainer = Color(0xFFFFDCCB),
    onLossContainer = Color(0xFF3E1100),
    live = Color(0xFFC8481E),
    highlight = Color(0xFFD9EE3A),
    onHighlight = Color(0xFF2A2F05),
    info = Color(0xFF2C6FB7),
    infoContainer = Color(0xFFDCEBFA),
    onInfoContainer = Color(0xFF0B2A4A),
    medal1 = Color(0xFFF2C230),
    medal2 = Color(0xFFC9D3DC),
    medal3 = Color(0xFFE9A374),
    stickerBorder = Color(0xFF2A1E17),
    stickerShadow = Color(0xFF2A1E17)
)

val DarkDonutColors = DonutColors(
    win = Color(0xFF6FCB92),
    winContainer = Color(0xFF1D4B30),
    onWinContainer = Color(0xFFC9F0D6),
    loss = Color(0xFFFF8A5C),
    lossContainer = Color(0xFF6E2A10),
    onLossContainer = Color(0xFFFFDCCB),
    live = Color(0xFFFF8A5C),
    highlight = Color(0xFFDDF25B),
    onHighlight = Color(0xFF2A2F05),
    info = Color(0xFF8EC3FF),
    infoContainer = Color(0xFF173556),
    onInfoContainer = Color(0xFFD6E8FF),
    medal1 = Color(0xFFF2C230),
    medal2 = Color(0xFFC9D3DC),
    medal3 = Color(0xFFE9A374),
    stickerBorder = Color(0xFF5C4C42),
    stickerShadow = Color(0xFF000000)
)

val LocalDonutColors = staticCompositionLocalOf { LightDonutColors }
