package com.example.cpen321application.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CourtGreenLight,
    onPrimary = CourtGreenDeep,
    primaryContainer = CourtGreen,
    onPrimaryContainer = CourtGreenPale,
    secondary = SageLight,
    onSecondary = CourtGreenDeep,
    secondaryContainer = SageDeep,
    onSecondaryContainer = Fog,
    tertiary = BallYellow,
    onTertiary = BallYellowInk,
    background = Night,
    onBackground = Fog,
    surface = Night,
    onSurface = Fog,
    surfaceVariant = NightHigh,
    onSurfaceVariant = FogMuted,
    surfaceContainerLowest = Night,
    surfaceContainerLow = NightLow,
    surfaceContainer = NightMid,
    surfaceContainerHigh = NightHigh,
    surfaceContainerHighest = NightHighest,
    outline = Line,
    outlineVariant = SageDeep
)

private val LightColorScheme = lightColorScheme(
    primary = CourtGreen,
    onPrimary = Chalk,
    primaryContainer = CourtGreenPale,
    onPrimaryContainer = CourtGreenDeep,
    secondary = Sage,
    onSecondary = Chalk,
    secondaryContainer = SagePale,
    onSecondaryContainer = CourtGreenDeep,
    tertiary = BallYellow,
    onTertiary = BallYellowInk,
    background = Mist,
    onBackground = Ink,
    surface = Mist,
    onSurface = Ink,
    surfaceVariant = MistHigh,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Chalk,
    surfaceContainerLow = MistLow,
    surfaceContainer = MistMid,
    surfaceContainerHigh = MistHigh,
    surfaceContainerHighest = MistHighest,
    outline = Line,
    outlineVariant = LineSoft
)

@Composable
fun CPEN321ApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
