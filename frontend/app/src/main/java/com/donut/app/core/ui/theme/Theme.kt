package com.donut.app.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

// Keep the name: MainActivity.kt calls CPEN321ApplicationTheme { ... }
@Composable
fun CPEN321ApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalDonutColors provides if (darkTheme) DarkDonutColors else LightDonutColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = DonutTypography,
            shapes = DonutShapes,
            content = content
        )
    }
}

/** DonutTheme.colors.win, DonutTheme.colors.highlight, ... */
object DonutTheme {
    val colors: DonutColors
        @Composable @ReadOnlyComposable get() = LocalDonutColors.current
}
