package com.vpet.waifu.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.CurrencyYen
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vpet.waifu.domain.ActiveEffect
import com.vpet.waifu.domain.BOOST_EFFECTS
import com.vpet.waifu.domain.EffectKind
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.ui.theme.Accents
import com.vpet.waifu.ui.theme.Surfaces

/**
 * What is running, on whatever screen you are on.
 *
 * The home screen has always had a full row of draining bars, and every other
 * screen had nothing — so a boost bought to get through a shift became
 * invisible the moment you went to the shop to buy the next one. A buff you
 * cannot see is a buff you cannot tell is working, which is the complaint that
 * started all of this.
 *
 * Compact on purpose: icons and minutes, one line, no bars. The full treatment
 * belongs on the screen you go to in order to look at her; everywhere else this
 * is a reminder, not a dashboard. Tapping one says what it actually does, in
 * numbers, because "Focus" is the name of the thing you bought and not what is
 * happening to her.
 */
@Composable
fun BuffStrip(
    snapshot: PetSnapshot,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    val running = snapshot.effects.filter { it.isActive(nowMillis) }
    val lucky = snapshot.luckyGames
    if (running.isEmpty() && lucky <= 0) return

    var explaining by remember { mutableStateOf<EffectKind?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            running.forEach { effect ->
                BuffPill(
                    effect = effect,
                    nowMillis = nowMillis,
                    onTap = {
                        explaining = if (explaining == effect.kind) null else effect.kind
                    },
                )
            }
            // The one "buff" with no clock on it: it is spent in rounds, so a
            // countdown would be a lie. Shown beside the timed ones because to
            // the player it is the same kind of thing.
            if (lucky > 0) LuckyPill(lucky)
        }

        explaining?.let { kind ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = buffExplanation(kind),
                style = MaterialTheme.typography.bodySmall,
                color = Accents.TextDim,
            )
        }
    }
}

@Composable
private fun BuffPill(effect: ActiveEffect, nowMillis: Long, onTap: () -> Unit) {
    val penalty = effect.kind !in BOOST_EFFECTS
    val tint = if (penalty) Accents.Danger else Accents.Bright
    val minutes = (((effect.expiresAt - nowMillis) + 59_999L) / 60_000L).toInt()

    // A slow breath, only on the last stretch. A pill that pulses for its whole
    // half hour is wallpaper; one that starts pulsing when it is nearly gone is
    // the warning that it is nearly gone.
    val ending = effect.fractionLeft(nowMillis) < 0.2f
    val pulse by rememberInfiniteTransition(label = "buff").animateFloat(
        initialValue = if (ending) 0.9f else 1f,
        targetValue = if (ending) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(9.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            )
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = effect.kind.pillIcon(),
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(13.dp)
                .graphicsLayer { scaleX = pulse; scaleY = pulse },
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "${minutes}м",
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LuckyPill(rounds: Int) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Surfaces.Tile)
            .border(1.dp, Accents.Bright.copy(alpha = 0.35f), RoundedCornerShape(9.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Casino,
            contentDescription = null,
            tint = Accents.Bright,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "×2 · $rounds",
            style = MaterialTheme.typography.labelSmall,
            color = Accents.Bright,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun EffectKind.pillIcon(): ImageVector = when (this) {
    EffectKind.HASTE -> Icons.Rounded.RocketLaunch
    EffectKind.OVERTIME -> Icons.Rounded.CurrencyYen
    EffectKind.FOCUS -> Icons.Rounded.Star
    EffectKind.SECOND_WIND -> Icons.Rounded.Bolt
    EffectKind.GOOD_VIBES -> Icons.Rounded.Favorite
    EffectKind.DISCOUNT -> Icons.Rounded.Sell
    EffectKind.STASIS -> Icons.Rounded.AcUnit
    EffectKind.HUNGER_SURGE, EffectKind.EXHAUSTION ->
        Icons.Rounded.WarningAmber
}

/**
 * What it does, in numbers.
 *
 * Hard-coded Russian rather than string resources because each line quotes a
 * constant from [com.vpet.waifu.domain.PetTuning], and a translated string that
 * drifts from the tuning it describes is worse than no string at all. When the
 * app grows a second language these move, together with the numbers.
 */
private fun buffExplanation(kind: EffectKind): String = when (kind) {
    EffectKind.HASTE -> "Смена идёт вдвое быстрее — каждая минута считается за две."
    EffectKind.OVERTIME -> "Зарплата ×1,5, пока идёт."
    EffectKind.FOCUS -> "Опыт от учёбы ×1,5, пока идёт."
    EffectKind.SECOND_WIND -> "Энергия тратится вдвое медленнее."
    EffectKind.GOOD_VIBES -> "Настроение растёт само, пока идёт."
    EffectKind.DISCOUNT -> "Всё в магазине на 20% дешевле."
    EffectKind.STASIS -> "Голод и энергия не падают вообще."
    EffectKind.HUNGER_SURGE -> "Расплата: голод падает в 2,5 раза быстрее."
    EffectKind.EXHAUSTION -> "Расплата: энергия падает в 1,6 раза быстрее."
}
