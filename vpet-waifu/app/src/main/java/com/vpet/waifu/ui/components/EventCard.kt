package com.vpet.waifu.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpet.waifu.R
import com.vpet.waifu.domain.EventKind
import com.vpet.waifu.domain.PetEvent
import com.vpet.waifu.ui.eventBodyRes
import com.vpet.waifu.ui.eventEmoji
import com.vpet.waifu.ui.eventTitleRes
import com.vpet.waifu.ui.theme.Accents
import com.vpet.waifu.ui.theme.StatColors

/**
 * Today's event.
 *
 * Open the app on a Monday and on a Friday and it used to show exactly the same
 * thing. This is the one part of the screen that can be different today, so it
 * sits above everything else and is dismissed by hand rather than fading on a
 * timer — the point is that you read it.
 */
@Composable
fun EventCard(event: PetEvent, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val tint = tintFor(event.kind)

    PanelCard(
        modifier = modifier.fillMaxWidth(),
        color = tint.copy(alpha = 0.10f),
        border = tint.copy(alpha = 0.45f),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(eventEmoji(event.kind), fontSize = 21.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(eventTitleRes(event.kind)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Accents.Text,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = stringResource(eventBodyRes(event.kind)),
                        style = MaterialTheme.typography.bodySmall,
                        color = Accents.TextMuted,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlineButton(
                text = stringResource(R.string.action_ok),
                onClick = onDismiss,
                tint = tint,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun tintFor(kind: EventKind): Color = when (kind) {
    EventKind.LUCKY_DAY -> StatColors.Money
    EventKind.COLD -> Accents.Danger
    EventKind.LETTER -> StatColors.Money
    EventKind.INSPIRED -> StatColors.Exp
    EventKind.RESTLESS -> StatColors.Energy
}
