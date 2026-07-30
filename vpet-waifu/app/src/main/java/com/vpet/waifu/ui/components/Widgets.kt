package com.vpet.waifu.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpet.waifu.domain.PetStats
import com.vpet.waifu.domain.Progression
import com.vpet.waifu.ui.theme.Accents
import com.vpet.waifu.ui.theme.StatColors
import com.vpet.waifu.ui.theme.Surfaces
import kotlin.math.roundToInt

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

/** The wallet: gold text inside a gold-tinted, gold-outlined pill. */
@Composable
fun MoneyPill(amount: Int, modifier: Modifier = Modifier, onAdd: (() -> Unit)? = null) {
    Surface(
        modifier = modifier,
        color = StatColors.Money.copy(alpha = 0.10f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, StatColors.Money.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = if (onAdd != null) 6.dp else 14.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("💰", fontSize = 15.sp)
            Spacer(Modifier.width(7.dp))
            Text(
                text = "$amount",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = StatColors.Money,
            )
            if (onAdd != null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(StatColors.Money.copy(alpha = 0.18f))
                        .clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", color = StatColors.Money, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
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

/** An outlined action: coloured icon over coloured label. */
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
    Surface(
        modifier = modifier.then(
            if (enabled) Modifier.clickable(onClick = onClick) else Modifier,
        ),
        color = Surfaces.Card,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f * alpha)),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint.copy(alpha = alpha),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = tint.copy(alpha = alpha),
            )
        }
    }
}

/** The filled, gradient call-to-action. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val alpha = if (enabled) 1f else 0.4f
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Accents.Deep.copy(alpha = alpha),
                        Accents.Primary.copy(alpha = alpha),
                    ),
                ),
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = alpha),
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
) {
    val alpha = if (enabled) 1f else 0.35f
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Surfaces.Tile.copy(alpha = alpha))
            .border(1.dp, tint.copy(alpha = 0.45f * alpha), RoundedCornerShape(14.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = tint.copy(alpha = alpha),
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
