package com.vpet.waifu.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Stat colors, shared by the panel bars and the app screen. */
object StatColors {
    val Hunger = Color(0xFFF2994A)
    val Energy = Color(0xFF4FC3F7)
    val Mood = Color(0xFFBA68C8)
}

private val Violet = Color(0xFF7B5EA7)
private val VioletLight = Color(0xFFD6C6F0)
private val Blush = Color(0xFFE38AAE)

private val LightColors = lightColorScheme(
    primary = Violet,
    secondary = Blush,
    tertiary = Color(0xFF4FA5A5),
)

private val DarkColors = darkColorScheme(
    primary = VioletLight,
    secondary = Color(0xFFF4B0CB),
    tertiary = Color(0xFF8ED3D3),
)

/**
 * The theme is deliberately *not* dynamic-color aware: the bubble floats over
 * arbitrary apps, and a fixed palette keeps her readable no matter what is
 * behind her.
 */
@Composable
fun VPetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
