package com.vpet.waifu.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vpet.waifu.R
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.PurchaseBlock
import com.vpet.waifu.domain.Upgrade
import com.vpet.waifu.domain.UpgradeEffect
import com.vpet.waifu.domain.UpgradeKind
import com.vpet.waifu.domain.UpgradeStat
import com.vpet.waifu.domain.Upgrades
import com.vpet.waifu.ui.upgradeArtRes
import com.vpet.waifu.ui.upgradeNameRes
import com.vpet.waifu.ui.upgradeTint
import com.vpet.waifu.ui.theme.Accents
import com.vpet.waifu.ui.theme.StatColors
import com.vpet.waifu.ui.theme.Surfaces
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Everything she can be permanently improved with, on one panel.
 *
 * The upgrades used to be eight rows scattered across six shelves of a shop
 * that also sells cake, each row a name, a price and a sentence of prose. Two
 * things were wrong with that. Choosing between them meant holding eight
 * separate cards in your head, because nothing put them side by side; and none
 * of them said what buying it would actually *do* — "a fridge" is not a number,
 * and neither is "keeps food fresher".
 *
 * So: one board, closed by default and about as tall as a button, that opens
 * into the whole list grouped by what the thing is. Every row carries the level
 * it is at, the effect it is giving her now, the effect it would give after one
 * more purchase, and a buy button big enough to hit without aiming. Owned,
 * affordable and out-of-reach are three different colours, so the question
 * "what can I actually buy right now" is answered by glancing rather than by
 * reading eight prices against a wallet.
 *
 * It lives on the home screen because that is where the player is standing when
 * they wonder what to improve — but it opens *downwards, in the page flow*
 * rather than over the top of anything. A panel that covers the character, the
 * shift timer and her mood to sell you a fridge is the game interrupting itself.
 */
@Composable
fun UpgradeBoard(
    snapshot: PetSnapshot,
    nowMillis: Long,
    onBuy: (Upgrade) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Whether it starts open.
     *
     * Closed on the home screen, where it is one line among the things that are
     * happening; a surface whose whole job is shopping can hand this `true` and
     * skip the tap.
     */
    startExpanded: Boolean = false,
) {
    // Survives rotation and tab switches: an expanded list that folds itself up
    // every time you look at the shop is a list you stop opening.
    var expanded by rememberSaveable { mutableStateOf(startExpanded) }
    var section by rememberSaveable { mutableStateOf(UpgradeKind.ROOM.name) }

    val families = remember(snapshot.owned, snapshot.progress.money, snapshot.level) {
        Upgrades.MECHANICAL.map { family -> familyState(snapshot, family, nowMillis) }
    }
    val affordable = families.count { it.block == null }
    val ownedTiers = families.sumOf { it.tier }
    val totalTiers = families.sumOf { it.of }

    PanelCard(modifier = modifier.fillMaxWidth()) {
        Column {
            BoardHeader(
                affordable = affordable,
                ownedTiers = ownedTiers,
                totalTiers = totalTiers,
                expanded = expanded,
                onToggle = { expanded = !expanded },
            )

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                    SectionChips(
                        selected = section,
                        onSelect = { section = it },
                    )
                    Spacer(Modifier.height(10.dp))
                    families
                        .filter { it.kind.name == section }
                        .forEach { state ->
                            UpgradeRow(state = state, onBuy = onBuy)
                            Spacer(Modifier.height(8.dp))
                        }
                    Text(
                        text = stringResource(R.string.upgrades_footnote),
                        style = MaterialTheme.typography.labelSmall,
                        color = Accents.TextDim,
                    )
                }
            }
        }
    }
}

/**
 * The same rows, for a surface that has already said what they are.
 *
 * The shop's room and gear shelves used to be one card per upgrade, which with
 * tiers would have become five cards called "fridge". They are these rows now
 * instead — the shelf chip above them is the heading, so the board's own header
 * and category chips would be saying it a second time.
 */
@Composable
fun UpgradeList(
    snapshot: PetSnapshot,
    nowMillis: Long,
    kind: UpgradeKind,
    onBuy: (Upgrade) -> Unit,
    modifier: Modifier = Modifier,
) {
    val families = remember(snapshot.owned, snapshot.progress.money, snapshot.level, kind) {
        Upgrades.MECHANICAL
            .map { familyState(snapshot, it, nowMillis) }
            .filter { it.kind == kind }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        families.forEach { state ->
            UpgradeRow(state = state, onBuy = onBuy)
            Spacer(Modifier.height(10.dp))
        }
        Text(
            text = stringResource(R.string.upgrades_footnote),
            style = MaterialTheme.typography.labelSmall,
            color = Accents.TextDim,
        )
    }
}

@Composable
private fun BoardHeader(
    affordable: Int,
    ownedTiers: Int,
    totalTiers: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "board-chevron")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.upgrades_title),
                style = MaterialTheme.typography.titleMedium,
                color = Accents.Text,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                // Two numbers, because they answer two different questions:
                // how far along am I, and is there anything to do right now.
                text = stringResource(R.string.upgrades_summary, ownedTiers, totalTiers),
                style = MaterialTheme.typography.bodySmall,
                color = Accents.TextMuted,
            )
        }
        if (affordable > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(StatColors.Money.copy(alpha = 0.16f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.upgrades_ready, affordable),
                    style = MaterialTheme.typography.labelSmall,
                    color = StatColors.Money,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = stringResource(
                if (expanded) R.string.upgrades_collapse else R.string.upgrades_expand,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = Accents.Bright,
            fontWeight = FontWeight.SemiBold,
        )
        Icon(
            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = Accents.Bright,
            modifier = Modifier.size(20.dp).rotate(turn - if (expanded) 180f else 0f),
        )
    }
}

@Composable
private fun SectionChips(selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            UpgradeKind.ROOM to R.string.upgrades_section_room,
            UpgradeKind.GEAR to R.string.upgrades_section_gear,
        ).forEach { (kind, labelRes) ->
            val on = selected == kind.name
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) Accents.Primary.copy(alpha = 0.18f) else Surfaces.Tile)
                    .border(
                        1.dp,
                        if (on) Accents.Bright.copy(alpha = 0.5f) else Color.Transparent,
                        RoundedCornerShape(12.dp),
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(kind.name) },
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (on) Accents.Text else Accents.TextMuted,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * One thing, at the level it is at, with what the next level costs and buys.
 *
 * The three states get three different treatments rather than three different
 * words: finished is dimmed with a tick, buyable is outlined in the accent with
 * a solid button, and everything else is muted with the reason on the button
 * itself. A greyed-out button that does not say *why* sends the player back to
 * the shop to work it out by elimination.
 */
@Composable
private fun UpgradeRow(state: FamilyState, onBuy: (Upgrade) -> Unit) {
    val maxed = state.next == null
    val buyable = state.block == null && state.next != null
    val tint = upgradeTint(state.family)
    val border = when {
        maxed -> Surfaces.CardBorder
        buyable -> StatColors.Money.copy(alpha = 0.55f)
        else -> Surfaces.CardBorder
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (buyable) StatColors.Money.copy(alpha = 0.06f) else Surfaces.Tile)
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.14f))
                    // Owned-out things stay legible but stop competing: this is
                    // a list you scan for what is still available.
                    .alpha(if (maxed) 0.55f else 1f),
                contentAlignment = Alignment.Center,
            ) {
                upgradeArtRes(state.family)?.let { art ->
                    Image(
                        painter = painterResource(art),
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(upgradeNameRes(state.family)),
                        style = MaterialTheme.typography.titleSmall,
                        color = Accents.Text,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    TierPips(tier = state.tier, of = state.of, tint = tint)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stringResource(R.string.upgrades_level, state.tier, state.of),
                    style = MaterialTheme.typography.labelSmall,
                    color = Accents.TextDim,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        EffectLine(state)

        Spacer(Modifier.height(10.dp))
        when {
            maxed -> Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = StatColors.Money,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.upgrades_maxed),
                    style = MaterialTheme.typography.labelLarge,
                    color = StatColors.Money,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            buyable -> PrimaryButton(
                text = stringResource(R.string.upgrades_buy, state.price),
                onClick = { state.next?.let(onBuy) },
                modifier = Modifier.fillMaxWidth(),
            )

            else -> BlockedButton(state)
        }
    }
}

/** Why this one cannot be bought, on the button that would have bought it. */
@Composable
private fun BlockedButton(state: FamilyState) {
    val next = state.next ?: return
    val label = when (state.block) {
        PurchaseBlock.LEVEL -> stringResource(R.string.upgrades_locked_level, next.requiredLevel)
        PurchaseBlock.PREREQUISITE -> stringResource(R.string.upgrades_locked_previous)
        else -> stringResource(R.string.upgrades_too_dear, state.price)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Surfaces.Track)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = null,
            tint = Accents.TextDisabled,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Accents.TextDisabled,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * What she has now, and what one more purchase makes it.
 *
 * The arrow is the entire point of the row: a price next to a name asks the
 * player to take the improvement on trust, and after four tiers of diminishing
 * returns "trust me, it helps" is not a claim anybody can check.
 */
@Composable
private fun EffectLine(state: FamilyState) {
    val now = state.current.headline()
    val next = state.next?.let { (state.current * it.effect).headline() }

    if (now.isEmpty() && next == null) return

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            (next ?: now).forEachIndexed { index, (stat, after) ->
                val before = now.getOrNull(index)?.second ?: 1f
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(stat.labelRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = Accents.TextMuted,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = percent(stat, before),
                        style = MaterialTheme.typography.bodySmall,
                        color = Accents.TextDim,
                    )
                    if (next != null) {
                        Text(
                            text = " → ",
                            style = MaterialTheme.typography.bodySmall,
                            color = Accents.TextDim,
                        )
                        Text(
                            text = percent(stat, after),
                            style = MaterialTheme.typography.bodySmall,
                            color = StatColors.Money,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TierPips(tier: Int, of: Int, tint: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(of) { index ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (index < tier) tint else Surfaces.Track),
            )
        }
    }
}

/** Everything one row needs, worked out once rather than per composable. */
private data class FamilyState(
    val family: String,
    val kind: UpgradeKind,
    val tier: Int,
    val of: Int,
    val current: UpgradeEffect,
    val next: Upgrade?,
    val price: Int,
    val block: PurchaseBlock?,
)

private fun familyState(snapshot: PetSnapshot, family: String, nowMillis: Long): FamilyState {
    val tiers = Upgrades.FAMILIES[family].orEmpty()
    val next = Upgrades.nextTier(snapshot.owned, family)
    return FamilyState(
        family = family,
        kind = tiers.first().kind,
        tier = Upgrades.tierOwned(snapshot.owned, family),
        of = tiers.size,
        current = Upgrades.effectOfFamily(snapshot.owned, family),
        next = next,
        // The discounted price, so the row and the wallet agree during a sale.
        price = next?.let { snapshot.priceOf(it, nowMillis) } ?: 0,
        block = next?.let { snapshot.blockedBy(it, nowMillis) },
    )
}

private fun UpgradeStat.labelRes(): Int = when (this) {
    UpgradeStat.HUNGER -> R.string.upgrade_stat_hunger
    UpgradeStat.ENERGY -> R.string.upgrade_stat_energy
    UpgradeStat.SLEEP -> R.string.upgrade_stat_sleep
    UpgradeStat.PAY -> R.string.upgrade_stat_pay
    UpgradeStat.STUDY -> R.string.upgrade_stat_study
    UpgradeStat.PLAY -> R.string.upgrade_stat_play
    UpgradeStat.NEGLECT -> R.string.upgrade_stat_neglect
}

/**
 * A multiplier as the improvement it is.
 *
 * `hungerDecay = 0.85` is fifteen percent *less* hunger, and printing it as
 * "0.85" or as "−15%" without knowing which direction is good is how a shop
 * ends up telling the player the best fridge in the game is the worst one.
 */
private fun percent(stat: UpgradeStat, value: Float): String {
    val delta = ((if (stat.lowerIsBetter) 1f - value else value - 1f) * 100f).roundToInt()
    if (delta == 0) return "—"
    return (if (stat.lowerIsBetter) "−" else "+") + "${abs(delta)}%"
}
