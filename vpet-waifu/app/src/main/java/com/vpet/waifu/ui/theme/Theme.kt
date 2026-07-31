package com.vpet.waifu.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vpet.waifu.R

/** Stat colours, shared by the bars, the badges and the widget. */
object StatColors {
    val Hunger = Tokens.Semantic.Hunger
    val Energy = Tokens.Semantic.Energy
    val Mood = Tokens.Semantic.Mood
    val Money = Tokens.Semantic.Money
    val Exp = Tokens.Semantic.Exp
}

/** Room backdrops behind the character, day and night. */
object StageColors {
    val DayTop = Color(0xFFFFF3E7)
    val DayBottom = Color(0xFFF2DCC6)
    val NightTop = Color(0xFF262638)
    val NightBottom = Color(0xFF15151F)
    val FloorLight = Color(0xFFE3C9AE)
    val FloorDark = Color(0xFF2C2C40)
}

/**
 * The surfaces and accents the screens are built from.
 *
 * Material's scheme does not have names for "the tile a shop item's icon sits
 * in" or "the outline that makes a card read as raised on near-black", so the
 * handful of shades the design leans on are named here instead of being spelled
 * out at each call site. Values come from [Tokens]: a neutral grey base with
 * one raspberry accent.
 */
object Surfaces {
    val Screen = Tokens.Neutral.Bg
    val Card = Tokens.Neutral.Surface
    val CardBorder = Tokens.Neutral.Border
    val Tile = Tokens.Neutral.SurfaceHigh
    val TileBorder = Tokens.Neutral.BorderStrong
    val Elevated = Tokens.Neutral.SurfaceHigh
    val Divider = Tokens.Neutral.Border
    val Track = Tokens.Neutral.Track
}

object Accents {
    val Primary = Tokens.Semantic.Accent
    val Bright = Color(0xFFF27CA0)
    val Deep = Tokens.Semantic.AccentPressed
    val Danger = Tokens.Semantic.Danger
    val Text = Tokens.Neutral.Text
    val TextMuted = Tokens.Neutral.TextSecondary
    val TextDim = Color(0xFF8B8B96)
    val TextDisabled = Tokens.Neutral.TextDisabled
}

private val Scheme = darkColorScheme(
    primary = Accents.Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A1220),
    onPrimaryContainer = Color(0xFFFFD9E4),
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
    errorContainer = Color(0xFF3A1A12),
    onErrorContainer = Color(0xFFFFDCD2),
)

/**
 * Golos Text — one variable file, two named weights.
 *
 * Chosen because it is an OFL face designed Cyrillic-first (Paratype), so the
 * Russian UI is set in a font drawn for it rather than falling back to Roboto.
 */
@OptIn(ExperimentalTextApi::class)
private val Golos = FontFamily(
    Font(
        R.font.golos_text,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.golos_text,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
)

private fun golos(size: Int, line: Int, weight: FontWeight) = TextStyle(
    fontFamily = Golos,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
)

/**
 * The whole app on three sizes and two weights (Tokens.Type): 20/15/12,
 * Regular/SemiBold. Material styles are collapsed onto that scale so existing
 * call sites keep working without carrying their own font sizes.
 */
private val GolosTypography = Typography(
    headlineMedium = golos(20, 26, FontWeight.SemiBold),
    headlineSmall = golos(20, 26, FontWeight.SemiBold),
    titleLarge = golos(20, 26, FontWeight.SemiBold),
    titleMedium = golos(15, 21, FontWeight.SemiBold),
    titleSmall = golos(15, 21, FontWeight.SemiBold),
    bodyLarge = golos(15, 21, FontWeight.Normal),
    bodyMedium = golos(15, 21, FontWeight.Normal),
    bodySmall = golos(12, 17, FontWeight.Normal),
    labelLarge = golos(15, 20, FontWeight.SemiBold),
    labelMedium = golos(12, 16, FontWeight.SemiBold),
    labelSmall = golos(12, 16, FontWeight.Normal),
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
        typography = GolosTypography,
        content = content,
    )
}
