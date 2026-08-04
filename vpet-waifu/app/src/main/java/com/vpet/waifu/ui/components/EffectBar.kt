package com.vpet.waifu.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vpet.waifu.ui.theme.Accents
import com.vpet.waifu.ui.theme.Surfaces

/**
 * A timed effect, shown as something visibly running.
 *
 * The chip this replaces said the name and the minutes left and then sat
 * perfectly still, and a still label is indistinguishable from a label about
 * something that has already worn off — which is exactly the doubt worth
 * removing: the whole reason to buy a boost mid-shift is that it is working
 * right now, and the only evidence of it was a number that changed once a
 * minute if you happened to be looking.
 *
 * Three things move here, and each answers a different question. The bar
 * drains, which says how much is left. A sheen sweeps along it, which says it
 * is live rather than frozen. And the icon breathes, which is what catches the
 * eye from across the screen without having to be read.
 *
 * A penalty — the hunger surge after an advance, the crash after an all-nighter
 * — gets the same treatment in the danger colour, because "why is her energy
 * falling off a cliff" deserves an answer on the same screen as the question.
 */
@Composable
fun EffectBar(
    icon: ImageVector,
    label: String,
    remaining: String,
    /** 1 when it has just started, 0 as it expires. */
    fraction: Float,
    tint: Color,
    penalty: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "effect")

    // The pulse is deliberately slow. A boost lasts half an hour; anything
    // quick enough to read as urgent would be exhausting long before then.
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val sweep by transition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(animation = tween(1_900, easing = LinearEasing)),
        label = "sweep",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                },
        )
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = Accents.Text,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = remaining,
                    style = MaterialTheme.typography.labelMedium,
                    color = tint,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // The bar: how much is left, plus a sweep that says it is live.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Surfaces.Tile),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(
                                // The sheen rides the filled part only, so it
                                // shortens with the bar instead of sliding on
                                // over an empty track.
                                colorStops = arrayOf(
                                    (sweep - 0.28f).coerceIn(0f, 1f) to tint,
                                    sweep.coerceIn(0f, 1f) to
                                        (if (penalty) Color.White else Color.White).copy(alpha = 0.55f),
                                    (sweep + 0.28f).coerceIn(0f, 1f) to tint,
                                ),
                            ),
                        ),
                )
            }
        }
    }
}
