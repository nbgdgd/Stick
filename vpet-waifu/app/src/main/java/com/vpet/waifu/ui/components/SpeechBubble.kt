package com.vpet.waifu.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vpet.waifu.ui.theme.Accents
import com.vpet.waifu.ui.theme.Surfaces

/**
 * What she is saying, with a tail pointing down at her.
 *
 * She had no voice at all: three numbers and eleven poses, which reads as a
 * very pretty gauge cluster rather than as somebody who lives there. One line
 * at a time, so whichever situation wins has to be worth speaking about.
 *
 * The line swaps with a cross-fade rather than appearing instantly, because it
 * changes on its own — a hard cut looks like a glitch when nobody touched
 * anything.
 */
@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(220)) + scaleIn(spring(Spring.DampingRatioMediumBouncy), initialScale = 0.88f))
                .togetherWith(fadeOut(tween(140)) + scaleOut(targetScale = 0.94f))
        },
        label = "speech",
    ) { line ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Surface(
                color = Surfaces.Card,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Surfaces.CardBorder),
            ) {
                Text(
                    text = line,
                    modifier = Modifier
                        .widthIn(max = 240.dp)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Accents.Text,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                )
            }
            // The tail is drawn rather than composed so it can carry the same
            // hairline border as the bubble without a seam across the join.
            Canvas(Modifier.size(width = 18.dp, height = 9.dp)) {
                val tail = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width / 2f, size.height)
                    lineTo(size.width, 0f)
                    close()
                }
                drawPath(tail, Surfaces.Card)
                // Only the two sloping sides; the top edge is the bubble itself.
                val sides = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width / 2f, size.height)
                    lineTo(size.width, 0f)
                }
                drawPath(sides, Surfaces.CardBorder, style = Stroke(width = 1.dp.toPx()))
            }
            Spacer(Modifier.size(0.dp))
        }
    }
}
