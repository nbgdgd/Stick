package com.vpet.waifu.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vpet.waifu.domain.PetStats
import kotlin.math.roundToInt

/**
 * One stat as a labelled bar. Animated so a tick reads as the needle moving
 * rather than a value teleporting.
 */
@Composable
fun StatBar(
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
    val rounded = value.roundToInt()

    Column(
        modifier = modifier.semantics { contentDescription = "$label $rounded" },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = if (compact) MaterialTheme.typography.labelSmall
                else MaterialTheme.typography.labelLarge,
            )
            Text(
                text = "$rounded",
                style = if (compact) MaterialTheme.typography.labelSmall
                else MaterialTheme.typography.labelLarge,
            )
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 5.dp else 8.dp)
                .padding(top = 1.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
        )
    }
}
