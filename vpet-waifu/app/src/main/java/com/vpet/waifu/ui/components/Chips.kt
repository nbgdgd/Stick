package com.vpet.waifu.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vpet.waifu.domain.Progression

/** A rounded label with an emoji and a value — money, EXP, a countdown. */
@Composable
fun StatChip(
    emoji: String,
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = tint.copy(alpha = 0.16f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(emoji, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * The level badge: a ring that fills with progress towards the next level, with
 * the level number in the middle.
 */
@Composable
fun LevelRing(
    exp: Int,
    size: androidx.compose.ui.unit.Dp = 52.dp,
    modifier: Modifier = Modifier,
) {
    val level = Progression.levelForExp(exp)
    val (earned, needed) = Progression.levelProgress(exp)
    val fraction by animateFloatAsState(
        targetValue = if (needed <= 0) 1f else (earned.toFloat() / needed).coerceIn(0f, 1f),
        label = "level-ring",
    )
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * 0.11f
            val inset = stroke / 2
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(
                    this.size.width - stroke,
                    this.size.height - stroke,
                ),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = fill,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(
                    this.size.width - stroke,
                    this.size.height - stroke,
                ),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Row(horizontalArrangement = Arrangement.Center) {
            Text(
                text = "$level",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
