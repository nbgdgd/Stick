package com.vpet.waifu.ui.shop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vpet.waifu.R
import com.vpet.waifu.domain.EffectKind
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.Shop
import com.vpet.waifu.domain.ShopCategory
import com.vpet.waifu.domain.ShopItem
import com.vpet.waifu.ui.components.StatChip
import com.vpet.waifu.ui.shopItemEmoji
import com.vpet.waifu.ui.shopItemNameRes
import com.vpet.waifu.ui.theme.StatColors
import kotlin.math.roundToInt

/**
 * The shop. Buying applies the item immediately — there is no inventory, so the
 * loop stays "earn, spend, watch her react" instead of "manage a bag".
 */
@Composable
fun ShopScreen(
    snapshot: PetSnapshot,
    onBuy: (ShopItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.tab_shop),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                StatChip(emoji = "💰", text = "${snapshot.progress.money}", tint = StatColors.Money)
            }
        }

        section(R.string.section_food, "🍜", Shop.FOOD, snapshot, onBuy)
        section(R.string.section_gifts, "🎁", Shop.GIFTS, snapshot, onBuy)
        section(R.string.section_pills, "💊", Shop.PILLS, snapshot, onBuy)

        item {
            Text(
                text = stringResource(R.string.pills_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    titleRes: Int,
    emoji: String,
    items: List<ShopItem>,
    snapshot: PetSnapshot,
    onBuy: (ShopItem) -> Unit,
) {
    item {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Text(emoji, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Text(
                text = androidx.compose.ui.res.stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
    items(items, key = { it.id }) { item -> ShopCard(item, snapshot, onBuy) }
}

@Composable
private fun ShopCard(item: ShopItem, snapshot: PetSnapshot, onBuy: (ShopItem) -> Unit) {
    val unlocked = item.isUnlocked(snapshot.level)
    val affordable = snapshot.progress.canAfford(item.price)
    val enabled = snapshot.canBuy(item)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = if (item.category == ShopCategory.PILL) {
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
            )
        } else {
            CardDefaults.elevatedCardColors()
        },
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (unlocked) shopItemEmoji(item.id) else "🔒",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(shopItemNameRes(item.id)),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = effectSummary(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!unlocked) {
                    Text(
                        text = stringResource(R.string.unlocks_at_level, item.requiredLevel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onBuy(item) }, enabled = enabled) {
                Text(
                    text = if (affordable || !unlocked) {
                        "${item.price} ¥"
                    } else {
                        stringResource(R.string.not_enough_money)
                    },
                )
            }
        }
    }
}

/** "+45 голод · +9 настроение · голод падает быстрее 3ч" — built from the data. */
@Composable
private fun effectSummary(item: ShopItem): String {
    val parts = buildList {
        if (item.hunger != 0f) add("+${item.hunger.roundToInt()} ${stringResource(R.string.stat_hunger).lowercase()}")
        if (item.energy != 0f) add("${signed(item.energy)} ${stringResource(R.string.stat_energy).lowercase()}")
        if (item.mood != 0f) add("${signed(item.mood)} ${stringResource(R.string.stat_mood).lowercase()}")
        if (item.money != 0) add("+${item.money} ¥")
        if (item.exp != 0) add("+${item.exp} EXP")
        // Matched exhaustively including null: `effect` lives in another module,
        // so Kotlin will not smart-cast the property to non-null after an
        // `if (… != null)` guard.
        when (item.effect) {
            EffectKind.HUNGER_SURGE ->
                add(stringResource(R.string.effect_hunger_surge, item.effectMinutes / 60))
            EffectKind.EXHAUSTION ->
                add(stringResource(R.string.effect_exhaustion, item.effectMinutes / 60))
            null -> Unit
        }
    }
    return parts.joinToString(" · ")
}

private fun signed(value: Float): String {
    val rounded = value.roundToInt()
    return if (rounded >= 0) "+$rounded" else "$rounded"
}
