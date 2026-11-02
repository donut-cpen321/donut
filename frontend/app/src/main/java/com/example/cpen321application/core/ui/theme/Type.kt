package com.example.cpen321application.core.ui.theme

import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Roboto Condensed ships with Android, so the scoreboard-style headings need no font download
private val Condensed = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))

private val Default = Typography()

val Typography = Typography(
    displaySmall = Default.displaySmall.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold),
    headlineLarge = Default.headlineLarge.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold),
    headlineMedium = Default.headlineMedium.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold),
    headlineSmall = Default.headlineSmall.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)
