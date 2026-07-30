package com.vpet.waifu.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Stat colours, shared by the bars, the badges and the widget. */
object StatColors {
    val Hunger = Color(0xFFF5A623)
    val Energy = Color(0xFF4FC3F7)
    val Mood = Color(0xFFC46BE0)
    val Money = Color(0xFFF5C542)
    val Exp = Color(0xFF5FD08A)
}

/** Room backdrops behind the character, day and night. */
object StageColors {
    val DayTop = Color(0xFFF6EEFF)
    val DayBottom = Color(0xFFE3D6F7)
    val NightTop = Color(0xFF2B2447)
    val NightBottom = Color(0xFF1A1530)
    val FloorLight = Color(0xFFD9C9F0)
    val FloorDark = Color(0xFF272042)
}

/**
 * The surfaces and accents the screens are built from.
 *
 * Material's scheme does not have names for "the tile a shop item's icon sits
 * in" or "the outline that makes a card read as raised on near-black", so the
 * handful of shades the design leans on are named here instead of being spelled
 * out at each call site.
 */
object Surfaces {
    val Screen = Color(0xFF08070C)
    val Card = Color(0xFF16141D)
    val CardBorder = Color(0xFF241F31)
    val Tile = Color(0xFF1E1A28)
    val TileBorder = Color(0xFF3B2F55)
    val Elevated = Color(0xFF1C1926)
    val Divider = Color(0xFF2A2437)
    val Track = Color(0xFF2A2537)
}

object Accents {
    val Primary = Color(0xFFA855F7)
    val Bright = Color(0xFFC084FC)
    val Deep = Color(0xFF7C3AED)
    val Danger = Color(0xFFF2557A)
    val Text = Color(0xFFFFFFFF)
    val TextMuted = Color(0xFF9C93B0)
    val TextDim = Color(0xFF6E6683)
}

private val Scheme = darkColorScheme(
    primary = Accents.Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A1F42),
    onPrimaryContainer = Color(0xFFE9DDFB),
    secondary = Accents.Bright,
    tertiary = StatColors.Energy,
    background = Surfaces.Screen,
    onBackground = Accents.Text,
    surface = Surfaces.Card,
    onSurface = Accents.Text,
    surfaceVariant = Surfaces.Tile,
    onSurfaceVariant = Accents.TextMuted,
    outline = Surfaces.CardBorder,
    error = Accents.Danger,
    errorContainer = Color(0xFF3A1622),
    onErrorContainer = Color(0xFFFFD6DF),
)

/**
 * The app theme.
 *
 * Deliberately dark-only: the character and her room are the bright thing on
 * screen, and the whole layout is designed around them glowing against
 * near-black. A light variant would need a second design, not a palette swap.
 */
@Composable
fun VPetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = Typography(),
        content = content,
    )
}
