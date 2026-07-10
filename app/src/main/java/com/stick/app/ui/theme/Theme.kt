package com.stick.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** User-facing theme choice, persisted in settings. */
enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

private val LightColors = lightColorScheme(
    primary = Purple40, secondary = PurpleGrey40, tertiary = Pink40,
)

private val DarkColors = darkColorScheme(
    primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80,
)

/** Pure-black variant for OLED screens, derived from the dark scheme. */
private val AmoledColors = DarkColors.copy(
    background = AmoledBlack,
    surface = AmoledBlack,
    surfaceVariant = AmoledSurfaceVariant,
    surfaceContainer = AmoledSurface,
    surfaceContainerLow = AmoledBlack,
    surfaceContainerLowest = AmoledBlack,
    surfaceContainerHigh = AmoledSurfaceVariant,
)

/**
 * App theme. Honors the user's [themeMode] and, when [dynamicColor] is on and the
 * device supports it (Android 12+), Material You wallpaper-based colors — while
 * still forcing pure black surfaces in AMOLED mode.
 */
@Composable
fun StickTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val amoled = themeMode == ThemeMode.AMOLED
    val context = LocalContext.current
    val supportsDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        supportsDynamic && dark -> {
            val scheme = dynamicDarkColorScheme(context)
            if (amoled) scheme.copy(background = Color.Black, surface = Color.Black) else scheme
        }
        supportsDynamic && !dark -> dynamicLightColorScheme(context)
        amoled -> AmoledColors
        dark -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = StickTypography,
        content = content,
    )
}
