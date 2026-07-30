package com.vpet.waifu.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpet.waifu.domain.PetStats
import com.vpet.waifu.domain.Progression
import com.vpet.waifu.ui.theme.Accents
import com.vpet.waifu.ui.theme.StatColors
import com.vpet.waifu.ui.theme.Surfaces
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
        targetValue = if (pressed && enabled) 0.955f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "press-scale",
    )
    return this
        .scale(scale)
        .then(
            if (enabled) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
            } else {
                Modifier
            },
        )
}

/** The card every panel in the app is made of: dark fill, hairline outline. */
@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    color: Color = Surfaces.Card,
    border: Color = Surfaces.CardBorder,
    radius: Dp = 20.dp,
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

/** A screen heading: a short accent bar, then the title. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Accents.Primary),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Accents.Text,
        )
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** An emoji in a rounded tile, then the section name over a short underline. */
@Composable
fun SectionHeader(emoji: String, title: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Surfaces.Tile)
                .border(1.dp, Surfaces.TileBorder, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 17.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Accents.Text,
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .size(width = 26.dp, height = 3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Accents.Primary),
            )
        }
    }
}

/**
 * The wallet: gold text inside a gold-tinted, gold-outlined pill.
 *
 * The number counts rather than jumps, which matters now that wages land every
 * minute of a shift — the pill ticking up is how the player notices she is
 * being paid while the shift is still running.
 */
@Composable
fun MoneyPill(amount: Int, modifier: Modifier = Modifier) {
    val shown by animateIntAsState(
        targetValue = amount,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "wallet",
    )
    // A gentle flash on the way up, so a payout is visible even mid-scroll.
    val glow by animateFloatAsState(
        targetValue = if (shown == amount) 0.10f else 0.24f,
        label = "wallet-glow",
    )

    Surface(
        modifier = modifier,
        color = StatColors.Money.copy(alpha = glow),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, StatColors.Money.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("💰", fontSize = 15.sp)
            Spacer(Modifier.width(7.dp))
            Text(
                text = "$shown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = StatColors.Money,
                maxLines = 1,
            )
        }
    }
}

/** A small labelled pill — an item's effect, a projected payout. */
@Composable
fun EffectChip(
    emoji: String,
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
            Text(emoji, fontSize = 12.sp)
            Spacer(Modifier.width(5.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
            )
        }
    }
}

/** The glowing rounded tile a shop item's emoji sits in. */
@Composable
fun EmojiTile(
    emoji: String,
    modifier: Modifier = Modifier,
    size: Dp = 62.dp,
    tint: Color = Accents.Primary,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(tint.copy(alpha = 0.16f), Surfaces.Tile),
                ),
            )
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size.value * 0.46f).sp)
    }
}

/**
 * One stat: a coloured badge, the name, the value out of a hundred, and a bar.
 */
@Composable
fun StatRow(
    emoji: String,
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
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f))
                    .border(1.dp, color.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, fontSize = 17.sp)
            }
            Spacer(Modifier.width(12.dp))
        } else {
            Text(emoji, fontSize = 13.sp)
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

/** The bar itself: a dark track with a gradient fill and a rounded cap. */
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
    val alpha = if (enabled) 1f else 0.35f
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .pressable(enabled, interaction, onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(Surfaces.Card)
            .border(1.dp, tint.copy(alpha = 0.35f * alpha), RoundedCornerShape(18.dp))
            .padding(vertical = 12.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.14f * alpha)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint.copy(alpha = alpha),
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tint.copy(alpha = alpha),
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
    val alpha = if (enabled) 1f else 0.4f
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .widthIn(min = minWidth)
            .pressable(enabled, interaction, onClick)
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Accents.Deep.copy(alpha = alpha),
                        Accents.Primary.copy(alpha = alpha),
                    ),
                ),
            )
            .padding(horizontal = 20.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = alpha),
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
    val alpha = if (enabled) 1f else 0.35f
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .widthIn(min = minWidth)
            .pressable(enabled, interaction, onClick)
            .clip(RoundedCornerShape(14.dp))
            .background(Surfaces.Tile.copy(alpha = alpha))
            .border(1.dp, tint.copy(alpha = 0.45f * alpha), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = tint.copy(alpha = alpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
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
fun LevelRing(exp: Int, modifier: Modifier = Modifier, size: Dp = 54.dp) {
    val level = Progression.levelForExp(exp)
    val (earned, needed) = Progression.levelProgress(exp)
    val fraction by animateFloatAsState(
        targetValue = if (needed <= 0) 1f else (earned.toFloat() / needed).coerceIn(0f, 1f),
        label = "level-ring",
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
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
                brush = Brush.sweepGradient(listOf(Accents.Deep, Accents.Bright, Accents.Deep)),
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
            fontWeight = FontWeight.Bold,
            color = Accents.Text,
        )
    }
}
