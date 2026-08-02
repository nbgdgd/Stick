package com.vpet.waifu.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CurrencyYen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vpet.waifu.R
import com.vpet.waifu.domain.PetStats
import com.vpet.waifu.domain.Progression
import com.vpet.waifu.ui.theme.Accents
import com.vpet.waifu.ui.theme.StatColors
import com.vpet.waifu.ui.theme.Surfaces
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Every button in the app dips under the finger.
 *
 * The scale replaces the ripple rather than joining it: a ripple on a dark,
 * heavily rounded surface reads as a smudge, and it outlives a control that
 * disappears on the same tap.
 */
@Composable
private fun Modifier.pressable(
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) if (enabled) 0.955f else 0.985f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "press-scale",
    )
    // A disabled control used to swallow the touch whole: no dip, no sound,
    // no answer to "why won't this press?". It still refuses the action, but
    // it now admits it was pressed.
    val refuse = LocalRefusal.current
    return this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = { if (enabled) onClick() else refuse() },
        )
}

/**
 * What a refused press should do, supplied by the app shell.
 *
 * A composable in components/ cannot reach the view model, and threading an
 * "onRefused" through every button in the app would be worse than this.
 */
val LocalRefusal = staticCompositionLocalOf<() -> Unit> { {} }

/** The card every panel in the app is made of: dark fill, hairline outline. */
@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    color: Color = Surfaces.Card,
    border: Color = Surfaces.CardBorder,
    radius: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = color,
        shape = RoundedCornerShape(radius),
        border = BorderStroke(1.dp, border),
        content = content,
    )
}

/** A screen heading: just the title — the type scale does the work. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            color = Accents.Text,
        )
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** An icon in a rounded tile beside the section name. */
@Composable
fun SectionHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    tint: Color = Accents.Bright,
    onTap: (() -> Unit)? = null,
    tapEnabled: Boolean = true,
) {
    if (onTap != null) {
        HeartTap(onTap = onTap, modifier = modifier, enabled = tapEnabled) {
            SectionHeaderRow(icon, title, tint)
        }
    } else {
        SectionHeaderRow(icon, title, tint, modifier)
    }
}

@Composable
private fun SectionHeaderRow(
    icon: ImageVector,
    title: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.12f))
                .border(1.dp, tint.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Accents.Text,
        )
    }
}

/**
 * The wallet: gold text inside a gold-tinted, gold-outlined pill.
 *
 * [amount] arrives already animated — one count-up shared by every pill in the
 * app (see the wallet state in VPetApp), so two visible pills can never show
 * different digits in the same frame. The pill itself only handles the flash
 * and the hop.
 */
@Composable
fun MoneyPill(amount: Int, modifier: Modifier = Modifier, bump: Int = 0, settled: Boolean = true) {
    val shown = amount
    // A gentle flash on the way up, so a payout is visible even mid-scroll.
    val glow by animateFloatAsState(
        targetValue = if (settled) 0.10f else 0.24f,
        label = "wallet-glow",
    )
    // The hop, on a landing coin or on any rise in the wallet.
    //
    // Only the home screen throws coins at it, but the tip jar pays every three
    // seconds wherever you are, and a wallet that silently grew while you were
    // in the shop is money you did not notice arriving.
    val pop = remember { Animatable(1f) }
    val previous = remember { mutableIntStateOf(amount) }
    var rises by remember { mutableIntStateOf(0) }
    LaunchedEffect(amount) {
        if (amount > previous.intValue) rises++
        previous.intValue = amount
    }
    LaunchedEffect(bump, rises) {
        if (bump > 0 || rises > 0) {
            pop.snapTo(1.18f)
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 900f))
        }
    }

    Surface(
        modifier = modifier.scale(pop.value),
        color = StatColors.Money.copy(alpha = glow),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, StatColors.Money.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.CurrencyYen,
                contentDescription = null,
                tint = StatColors.Money,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "$shown",
                style = MaterialTheme.typography.titleMedium,
                color = StatColors.Money,
                maxLines = 1,
            )
        }
    }
}

/** A small labelled pill — an item's effect, a projected payout. */
@Composable
fun EffectChip(
    icon: ImageVector,
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Surfaces.Tile,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, Surfaces.Divider),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
            )
        }
    }
}

/**
 * The flat rounded tile a shop item's picture sits in.
 *
 * With [art] set the tile shows the item's drawn illustration at full colour;
 * the [icon]+[tint] pair is the fallback for things that have no portrait.
 */
@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 62.dp,
    tint: Color = Accents.Primary,
    art: Painter? = null,
    onTap: (() -> Unit)? = null,
    tapEnabled: Boolean = true,
) {
    if (onTap != null) {
        HeartTap(onTap = onTap, modifier = modifier, enabled = tapEnabled) {
            IconTileFace(icon, Modifier, size, tint, art)
        }
    } else {
        IconTileFace(icon, modifier, size, tint, art)
    }
}

@Composable
private fun IconTileFace(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 62.dp,
    tint: Color = Accents.Primary,
    art: Painter? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(
                painter = art,
                contentDescription = null,
                modifier = Modifier.size(size * 0.72f),
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(size * 0.48f),
            )
        }
    }
}

/**
 * One stat: a coloured badge, the name, the value out of a hundred, and a bar.
 */
@Composable
fun StatRow(
    icon: ImageVector,
    label: String,
    value: Float,
    color: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val fraction by animateFloatAsState(
        targetValue = (value / PetStats.MAX).coerceIn(0f, 1f),
        label = "stat-$label",
    )

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (!compact) {
            // The same tile grammar as IconTile and SectionHeader — one icon
            // container style across the app instead of three.
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.12f))
                    .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
        } else {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(7.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = label,
                    style = if (compact) MaterialTheme.typography.labelSmall
                    else MaterialTheme.typography.bodyLarge,
                    color = Accents.Text,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${value.roundToInt()}",
                        style = if (compact) MaterialTheme.typography.labelSmall
                        else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Accents.Text,
                    )
                    if (!compact) {
                        Text(
                            text = " /100",
                            style = MaterialTheme.typography.labelMedium,
                            color = Accents.TextDim,
                        )
                    }
                }
            }
            Spacer(Modifier.height(if (compact) 3.dp else 6.dp))
            StatBarTrack(fraction, color, height = if (compact) 5.dp else 9.dp)
        }
    }
}

/**
 * The bar itself: a dark track, a gradient fill, and a bright leading edge.
 *
 * The glowing cap is what makes a slowly filling bar read as *filling* rather
 * than as a static image that happens to be different each time you look.
 */
@Composable
fun StatBarTrack(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 9.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(Surfaces.Track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        listOf(color.copy(alpha = 0.75f), color),
                    ),
                ),
        )
        if (fraction > 0.04f) {
            Box(
                modifier = Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier = Modifier
                        .size(height)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color.White.copy(alpha = 0.85f), color.copy(alpha = 0f)),
                            ),
                        ),
                )
            }
        }
    }
}

/**
 * A care action: coloured icon above a coloured label.
 *
 * Stacked rather than side by side. Three of these share one row, and the
 * labels are words like "Погладить" — beside the icon they wrapped to two
 * lines on a narrow phone and left the row visibly ragged, with each button a
 * different height.
 */
@Composable
fun ActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Disabled is its own visual state — muted solid surfaces and grey
    // content — not the enabled button at reduced opacity.
    val content = if (enabled) tint else Accents.TextDisabled
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .pressable(enabled, interaction, onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) Surfaces.Card else Surfaces.Tile)
            .border(
                1.dp,
                if (enabled) tint.copy(alpha = 0.35f) else Surfaces.CardBorder,
                RoundedCornerShape(12.dp),
            )
            .padding(vertical = 12.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(content.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The filled, gradient call-to-action.
 *
 * [minWidth] exists so a column of these lines up: the label is a Russian verb
 * whose length varies per card, and without a floor the buttons down a list
 * each ended up a different width.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minWidth: Dp = 0.dp,
) {
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .widthIn(min = minWidth)
            .pressable(enabled, interaction, onClick)
            .clip(RoundedCornerShape(50))
            .background(
                // The one gradient the UI keeps: the main call to action.
                // Disabled is a muted solid, not the same gradient faded out.
                if (enabled) {
                    Brush.horizontalGradient(listOf(Accents.Deep, Accents.Primary))
                } else {
                    Brush.horizontalGradient(listOf(Surfaces.Tile, Surfaces.Tile))
                },
            )
            .padding(horizontal = 20.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) Color.White else Accents.TextDisabled,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** An outlined secondary action, used for prices and "call her home". */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Accents.Bright,
    minWidth: Dp = 0.dp,
) {
    val content = if (enabled) tint else Accents.TextDisabled
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .widthIn(min = minWidth)
            .pressable(enabled, interaction, onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(Surfaces.Tile)
            .border(
                1.dp,
                if (enabled) tint.copy(alpha = 0.45f) else Surfaces.CardBorder,
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A "+12 ¥" that floats up and fades every time [total] grows.
 *
 * Wages are paid a minute at a time while the shift runs, and a number quietly
 * changing in a pill somewhere is not something anyone notices. This is the
 * part you actually see: money arriving *during* the work.
 */
@Composable
fun GainPop(
    total: Int,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    // Seeded from the first value seen, so opening the screen mid-shift does
    // not fire a pop for everything earned before you got there.
    var previous by remember { mutableIntStateOf(total) }
    var gain by remember { mutableIntStateOf(0) }
    val rise = remember { Animatable(1f) }

    LaunchedEffect(total) {
        val delta = total - previous
        previous = total
        // A new session resets the counter; that is not a payout.
        if (delta <= 0) return@LaunchedEffect
        gain = delta
        rise.snapTo(0f)
        rise.animateTo(1f, animationSpec = tween(1_300, easing = LinearOutSlowInEasing))
    }

    if (gain <= 0 || rise.value >= 1f) return

    val progress = rise.value
    Surface(
        modifier = modifier.graphicsLayer {
            translationY = -52.dp.toPx() * progress
            // Snap in, drift out.
            val appearing = (progress * 7f).coerceAtMost(1f)
            alpha = appearing * (1f - progress).pow(0.55f)
            scaleX = 0.7f + 0.3f * appearing
            scaleY = scaleX
        },
        color = tint.copy(alpha = 0.16f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.5f)),
    ) {
        Text(
            text = "+$gain $label",
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
            style = MaterialTheme.typography.titleSmall,
            color = tint,
            maxLines = 1,
        )
    }
}

/**
 * The one way locked content is marked: the card underneath is dimmed and
 * this badge sits in its corner. No red warnings, no dead buttons.
 */
@Composable
fun LevelBadge(level: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Surfaces.Elevated,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, Surfaces.TileBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = Accents.TextMuted,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.level_badge, level),
                style = MaterialTheme.typography.labelMedium,
                color = Accents.TextMuted,
            )
        }
    }
}

/** The floating "what she is doing" label over the stage. */
@Composable
fun StatusChip(text: String, dot: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color(0xCC0D0B14),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, Color(0x33FFFFFF)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = Accents.Text,
            )
        }
    }
}

/** The level badge: a ring that fills towards the next level. */
@Composable
fun LevelRing(exp: Int, modifier: Modifier = Modifier, size: Dp = 54.dp, bump: Int = 0) {
    val level = Progression.levelForExp(exp)
    val (earned, needed) = Progression.levelProgress(exp)
    val fraction by animateFloatAsState(
        targetValue = if (needed <= 0) 1f else (earned.toFloat() / needed).coerceIn(0f, 1f),
        label = "level-ring",
    )
    // The hop as a star lands on it — the wallet's trick, for the currency
    // studying actually pays in.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(bump) {
        if (bump > 0) {
            pop.snapTo(1.2f)
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 900f))
        }
    }

    Box(modifier = modifier.size(size).scale(pop.value), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * 0.09f
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(
                this.size.width - stroke,
                this.size.height - stroke,
            )
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(
                color = Surfaces.Track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = Accents.Primary,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            text = "$level",
            style = MaterialTheme.typography.titleLarge,
            color = Accents.Text,
        )
    }
}
