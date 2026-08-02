package com.vpet.waifu.ui.shop

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.CurrencyYen
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.RamenDining
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.ui.unit.dp
import com.vpet.waifu.R
import com.vpet.waifu.domain.EffectKind
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.PurchaseBlock
import com.vpet.waifu.domain.Shop
import com.vpet.waifu.domain.ShopCategory
import com.vpet.waifu.domain.ShopItem
import com.vpet.waifu.domain.Upgrade
import com.vpet.waifu.domain.UpgradeKind
import com.vpet.waifu.domain.Upgrades
import com.vpet.waifu.ui.components.EffectChip
import com.vpet.waifu.ui.components.IconTile
import com.vpet.waifu.ui.components.LevelBadge
import com.vpet.waifu.ui.components.MoneyPill
import com.vpet.waifu.ui.components.OutlineButton
import com.vpet.waifu.ui.components.PanelCard
import com.vpet.waifu.ui.components.ScreenTitle
import com.vpet.waifu.ui.components.SectionHeader
import com.vpet.waifu.ui.formatMinutes
import com.vpet.waifu.ui.character.PetPalette
import com.vpet.waifu.ui.shopItemArtRes
import com.vpet.waifu.ui.shopItemIcon
import com.vpet.waifu.ui.shopItemTint
import com.vpet.waifu.ui.upgradeArtRes
import com.vpet.waifu.ui.upgradeTint
import com.vpet.waifu.ui.upgradeIcon
import com.vpet.waifu.ui.upgradeNameRes
import com.vpet.waifu.ui.shopItemNameRes
import com.vpet.waifu.ui.theme.Accents
import com.vpet.waifu.ui.theme.StatColors
import com.vpet.waifu.ui.theme.Surfaces
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The shop. Buying applies the item immediately — there is no inventory, so the
 * loop stays "earn, spend, watch her react" instead of "manage a bag".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShopScreen(
    snapshot: PetSnapshot,
    nowMillis: Long,
    wallet: Int,
    walletSettled: Boolean,
    onBuy: (ShopItem) -> Unit,
    onBuyUpgrade: (Upgrade) -> Unit,
    onWear: (String) -> Unit,
    onApplyTheme: (String) -> Unit,
    onCategoryTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Every category header and every item tile is also a place to pat her:
    // a tap is worth a point or two of mood, a click and a puff of hearts. She
    // has to be free to notice, so it is off while she is asleep or on a shift.
    val patting = snapshot.acceptsPat
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenTitle(stringResource(R.string.tab_shop)) {
                MoneyPill(amount = wallet, settled = walletSettled)
            }
        }

        // One banner instead of the same red line on every food card: why the
        // food shelf is closed, said once, at the top, with the reopen time.
        if (!snapshot.acceptsInteraction) {
            item { BusyBanner(snapshot) }
        }

        section(R.string.section_food, Icons.Rounded.RamenDining, Color(0xFFF2A65A), Shop.FOOD, snapshot, nowMillis, onBuy, onCategoryTap, patting)
        section(R.string.section_gifts, Icons.Rounded.CardGiftcard, Color(0xFFF477B8), Shop.GIFTS, snapshot, nowMillis, onBuy, onCategoryTap, patting)
        section(R.string.section_pills, Icons.Rounded.Medication, Color(0xFFE8756A), Shop.PILLS, snapshot, nowMillis, onBuy, onCategoryTap, patting)
        item {
            Text(
                text = stringResource(R.string.pills_warning),
                style = MaterialTheme.typography.bodySmall,
                color = Accents.TextDim,
            )
        }
        // The one shelf that stays open while she is on a shift — that is the
        // entire point of it, so it sits right where a dragging shift sends you.
        section(R.string.section_boosts, Icons.Rounded.RocketLaunch, Color(0xFFE6710B), Shop.BOOSTS, snapshot, nowMillis, onBuy, onCategoryTap, patting)
        section(R.string.section_comfort, Icons.Rounded.Weekend, Color(0xFF7FD1E8), Shop.CARE, snapshot, nowMillis, onBuy, onCategoryTap, patting)

        // Everything above is eaten within the hour. Everything below is kept,
        // which is what makes the money worth earning in the first place.
        upgrades(R.string.section_room, Icons.Rounded.Chair, Color(0xFF7FD1E8), Upgrades.ROOM, snapshot, onBuyUpgrade, onWear, onCategoryTap, patting)
        upgrades(R.string.section_gear, Icons.Rounded.Handyman, Color(0xFFF0C860), Upgrades.GEAR, snapshot, onBuyUpgrade, onWear, onCategoryTap, patting)
        upgrades(R.string.section_outfits, Icons.Rounded.Checkroom, Color(0xFFB39CE8), Upgrades.OUTFITS, snapshot, onBuyUpgrade, onWear, onCategoryTap, patting)
        upgrades(R.string.section_themes, Icons.Rounded.Weekend, Color(0xFFE8A15C), Upgrades.THEMES, snapshot, onBuyUpgrade, onApplyTheme, onCategoryTap, patting)
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.upgrades(
    titleRes: Int,
    icon: ImageVector,
    tint: Color,
    items: List<Upgrade>,
    snapshot: PetSnapshot,
    onBuy: (Upgrade) -> Unit,
    onWear: (String) -> Unit,
    onCategoryTap: () -> Unit,
    patting: Boolean,
) {
    stickyHeader {
        Box(modifier = Modifier.fillMaxWidth().background(Surfaces.Screen)) {
            SectionHeaderRow(icon, tint, titleRes, onCategoryTap, patting)
        }
    }
    items(items, key = { it.id }) { upgrade ->
        UpgradeCard(upgrade, snapshot, onBuy, onWear, onCategoryTap, patting, modifier = Modifier.animateItem())
    }
}

/**
 * One permanent purchase.
 *
 * Deliberately shaped like the consumable card so the shop reads as one list,
 * but the button says three different things: buy it, wear it, or — once it is
 * hers and doing its job — nothing at all.
 */
@Composable
private fun UpgradeCard(
    upgrade: Upgrade,
    snapshot: PetSnapshot,
    onBuy: (Upgrade) -> Unit,
    onWear: (String) -> Unit,
    onCategoryTap: () -> Unit,
    patting: Boolean,
    modifier: Modifier = Modifier,
) {
    val owned = snapshot.owns(upgrade.id)
    val unlocked = upgrade.isUnlocked(snapshot.level)
    val outfit = upgrade.kind == UpgradeKind.OUTFIT
    val theme = upgrade.kind == UpgradeKind.THEME
    val worn = (outfit && snapshot.outfit == upgrade.id) || (theme && snapshot.theme == upgrade.id)
    // An outfit tile is tinted with the outfit's own ribbon colour, so the six
    // wardrobe entries read as six different clothes rather than six hangers.
    val tint = when {
        outfit -> PetPalette.forOutfit(upgrade.id).ribbon
        else -> upgradeTint(upgrade.id)
    }

    Box(modifier = modifier.fillMaxWidth()) {
        PanelCard(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (unlocked || owned) 1f else 0.45f),
            border = if (owned) tint.copy(alpha = 0.45f) else Surfaces.CardBorder,
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(
                    icon = upgradeIcon(upgrade.id),
                    tint = tint,
                    art = upgradeArtRes(upgrade.id)?.let { painterResource(it) },
                    onTap = onCategoryTap,
                    tapEnabled = patting && (unlocked || owned),
                )
                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(upgradeNameRes(upgrade.id)),
                        style = MaterialTheme.typography.titleMedium,
                        color = Accents.Text,
                    )
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        EffectChip(
                            icon = if (outfit) Icons.Rounded.AutoAwesome else Icons.AutoMirrored.Rounded.TrendingUp,
                            text = stringResource(upgradeEffectRes(upgrade)),
                            tint = tint,
                        )
                        if (owned) {
                            EffectChip(
                                icon = Icons.Rounded.Check,
                                text = stringResource(R.string.upgrade_owned),
                                tint = tint,
                            )
                        }
                    }
                }

                Spacer(Modifier.width(10.dp))
                when {
                    worn -> EffectChip(
                        icon = Icons.Rounded.Checkroom,
                        text = stringResource(R.string.action_worn),
                        tint = tint,
                    )
                    owned && (outfit || theme) -> OutlineButton(
                        text = stringResource(R.string.action_wear),
                        onClick = { onWear(upgrade.id) },
                        tint = tint,
                        minWidth = 84.dp,
                    )
                    owned -> Unit
                    else -> OutlineButton(
                        text = "${upgrade.price} \u00A5",
                        onClick = { onBuy(upgrade) },
                        enabled = snapshot.canBuy(upgrade),
                        tint = StatColors.Money,
                        minWidth = 92.dp,
                    )
                }
            }
        }
        if (!unlocked && !owned) {
            LevelBadge(
                level = upgrade.requiredLevel,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
            )
        }
    }
}

@StringRes
private fun upgradeEffectRes(upgrade: Upgrade): Int {
    val e = upgrade.effect
    return when {
        upgrade.kind == UpgradeKind.THEME -> R.string.effect_theme
        upgrade.kind == UpgradeKind.OUTFIT -> R.string.effect_cosmetic
        e.hungerDecay < 1f -> R.string.effect_hunger_slower
        e.energyDecay < 1f -> R.string.effect_energy_slower
        e.sleepSpeed > 1f -> R.string.effect_sleep_faster
        e.pay > 1f -> R.string.effect_pay_more
        e.study > 1f -> R.string.effect_study_more
        e.play > 1f -> R.string.effect_play_more
        e.neglect < 1f -> R.string.effect_neglect_less
        else -> R.string.effect_cosmetic
    }
}

/** Why food is off: on a shift until HH:MM, or asleep. Said once, up top. */
@Composable
private fun BusyBanner(snapshot: PetSnapshot) {
    val endsAt = snapshot.session?.endsAt
    val text = if (endsAt != null) {
        val time = remember(endsAt) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(endsAt))
        }
        stringResource(R.string.shop_busy_banner, time)
    } else {
        stringResource(R.string.shop_asleep_banner)
    }
    PanelCard(
        modifier = Modifier.fillMaxWidth(),
        color = Accents.Primary.copy(alpha = 0.10f),
        border = Accents.Primary.copy(alpha = 0.35f),
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.NightsStay,
                contentDescription = null,
                tint = Accents.Bright,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = Accents.Text,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.section(
    titleRes: Int,
    icon: ImageVector,
    tint: Color,
    items: List<ShopItem>,
    snapshot: PetSnapshot,
    nowMillis: Long,
    onBuy: (ShopItem) -> Unit,
    onCategoryTap: () -> Unit,
    patting: Boolean,
) {
    // Sticky, on an opaque strip: while a section scrolls, its name stays
    // pinned so the list always says where you are.
    stickyHeader {
        Box(modifier = Modifier.fillMaxWidth().background(Surfaces.Screen)) {
            SectionHeaderRow(icon, tint, titleRes, onCategoryTap, patting)
        }
    }
    items(items, key = { it.id }) { item ->
        // Cards slide into place when a level-up unlocks one mid-list.
        ShopCard(item, snapshot, nowMillis, onBuy, onCategoryTap, patting, modifier = Modifier.animateItem())
    }
}

@Composable
private fun SectionHeaderRow(
    icon: ImageVector,
    tint: Color,
    titleRes: Int,
    onCategoryTap: () -> Unit,
    patting: Boolean,
) {
    SectionHeader(
        icon = icon,
        title = stringResource(titleRes),
        tint = tint,
        onTap = onCategoryTap,
        tapEnabled = patting,
    )
}

@Composable
private fun ShopCard(
    item: ShopItem,
    snapshot: PetSnapshot,
    nowMillis: Long,
    onBuy: (ShopItem) -> Unit,
    onCategoryTap: () -> Unit,
    patting: Boolean,
    modifier: Modifier = Modifier,
) {
    val unlocked = item.isUnlocked(snapshot.level)
    val enabled = snapshot.canBuy(item, nowMillis)
    val block = snapshot.blockedBy(item, nowMillis)
    val tint = shopItemTint(item.id)

    Box(modifier = modifier.fillMaxWidth()) {
        PanelCard(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (unlocked) 1f else 0.45f),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(
                    icon = shopItemIcon(item.id),
                    tint = tint,
                    art = painterResource(shopItemArtRes(item.id)),
                    onTap = onCategoryTap,
                    tapEnabled = patting && unlocked,
                )
                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(shopItemNameRes(item.id)),
                        style = MaterialTheme.typography.titleMedium,
                        color = Accents.Text,
                    )
                    Spacer(Modifier.height(7.dp))
                    EffectChips(item)
                    // States a card still has to explain itself: an effect that
                    // has not run out, or medicine sold only to the sick. One
                    // quiet line — the busy and level cases are handled by the
                    // banner and the badge.
                    val blocker = when (block) {
                        PurchaseBlock.STILL_PAYING -> stringResource(
                            if (item.category == ShopCategory.BOOST) R.string.boost_already_running
                            else R.string.still_paying_it_off,
                        )
                        PurchaseBlock.NOT_SICK -> stringResource(R.string.she_is_healthy)
                        PurchaseBlock.ALREADY_TODAY -> stringResource(R.string.day_off_used)
                        else -> null
                    }
                    if (blocker != null) {
                        Spacer(Modifier.height(7.dp))
                        Text(
                            text = blocker,
                            style = MaterialTheme.typography.labelMedium,
                            color = Accents.TextMuted,
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))
                // The price holds its column whatever the state; unavailability
                // reads as a muted price, not as red text of the same size.
                OutlineButton(
                    text = "${item.price} ¥",
                    onClick = { onBuy(item) },
                    enabled = enabled,
                    tint = StatColors.Money,
                    minWidth = 84.dp,
                )
            }
        }
        if (!unlocked) {
            LevelBadge(
                level = item.requiredLevel,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
            )
        }
    }
}

/**
 * What the item does, one chip per effect.
 *
 * Chips wrap onto a second line rather than being squeezed, because an energy
 * drink has four things to say and a rice ball has two.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EffectChips(item: ShopItem) {
    val chips = buildList {
        if (item.hunger != 0f) {
            add(Triple(Icons.Rounded.Restaurant, "${signed(item.hunger)} ${label(R.string.stat_hunger)}", StatColors.Hunger))
        }
        if (item.energy != 0f) {
            add(Triple(Icons.Rounded.Bolt, "${signed(item.energy)} ${label(R.string.stat_energy)}", StatColors.Energy))
        }
        if (item.mood != 0f) {
            add(Triple(Icons.Rounded.Favorite, "${signed(item.mood)} ${label(R.string.stat_mood)}", StatColors.Mood))
        }
        if (item.money != 0) add(Triple(Icons.Rounded.CurrencyYen, "+${item.money} ¥", StatColors.Money))
        if (item.exp != 0) add(Triple(Icons.Rounded.Star, "+${item.exp} EXP", StatColors.Exp))
        // Matched exhaustively including null: `effect` lives in another module,
        // so Kotlin will not smart-cast the property to non-null.
        when (item.effect) {
            EffectKind.HUNGER_SURGE -> add(
                Triple(Icons.Rounded.WarningAmber, label(R.string.effect_hunger_surge, formatMinutes(item.effectMinutes)), Accents.Danger),
            )
            EffectKind.EXHAUSTION -> add(
                Triple(Icons.Rounded.WarningAmber, label(R.string.effect_exhaustion, formatMinutes(item.effectMinutes)), Accents.Danger),
            )
            // The boosts are the good kind of timer, so they get the item's own
            // colour rather than the warning amber the pills wear.
            EffectKind.HASTE -> add(
                Triple(Icons.Rounded.RocketLaunch, label(R.string.effect_haste, formatMinutes(item.effectMinutes)), shopItemTint(item.id)),
            )
            EffectKind.OVERTIME -> add(
                Triple(Icons.Rounded.CurrencyYen, label(R.string.effect_overtime, formatMinutes(item.effectMinutes)), shopItemTint(item.id)),
            )
            EffectKind.FOCUS -> add(
                Triple(Icons.Rounded.Star, label(R.string.effect_focus, formatMinutes(item.effectMinutes)), shopItemTint(item.id)),
            )
            EffectKind.SECOND_WIND -> add(
                Triple(Icons.Rounded.Bolt, label(R.string.effect_second_wind, formatMinutes(item.effectMinutes)), shopItemTint(item.id)),
            )
            EffectKind.GOOD_VIBES -> add(
                Triple(Icons.Rounded.Favorite, label(R.string.effect_good_vibes, formatMinutes(item.effectMinutes)), shopItemTint(item.id)),
            )
            null -> Unit
        }
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chips.forEach { (icon, text, tint) -> EffectChip(icon, text, tint) }
    }
}

@Composable
private fun label(resId: Int): String = stringResource(resId).lowercase()

@Composable
private fun label(resId: Int, arg: String): String = stringResource(resId, arg)

private fun signed(value: Float): String {
    val rounded = value.roundToInt()
    return if (rounded >= 0) "+$rounded" else "$rounded"
}
