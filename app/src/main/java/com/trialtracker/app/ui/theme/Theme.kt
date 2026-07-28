package com.trialtracker.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * One dark palette, one accent. Cards sit a tone above the background and are
 * separated by a 1px border instead of elevation shadows.
 */
object TT {
    val Background = Color(0xFF08080B)
    val Surface = Color(0xFF131317)
    val SurfaceHigh = Color(0xFF1A1A20)
    val Border = Color(0xFF232329)
    val TextPrimary = Color(0xFFF3F3F6)
    val TextSecondary = Color(0xFF8B8B95)
    val TextTertiary = Color(0xFF5C5C66)

    val Accent = Color(0xFF8B5CF6)
    val Orange = Color(0xFFFB7C3C)
    val Green = Color(0xFF34D399)
    val Blue = Color(0xFF3B82F6)
    val Yellow = Color(0xFFFACC15)
    val Pink = Color(0xFFEC4899)
    val Red = Color(0xFFF43F5E)
}

private val DarkColors = darkColorScheme(
    primary = TT.Accent,
    onPrimary = Color.White,
    background = TT.Background,
    onBackground = TT.TextPrimary,
    surface = TT.Surface,
    onSurface = TT.TextPrimary,
    surfaceVariant = TT.SurfaceHigh,
    onSurfaceVariant = TT.TextSecondary,
    outline = TT.Border,
    error = TT.Red,
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal),
    labelSmall = TextStyle(fontSize = 10.5.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold),
)

@Composable
fun TrialTrackerTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // The design is dark-only on purpose — a light variant would need a second
    // palette for the accent tints and is not part of the MVP.
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AppTypography,
        content = content,
    )
}
