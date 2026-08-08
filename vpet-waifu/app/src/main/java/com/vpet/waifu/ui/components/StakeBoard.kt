package com.vpet.waifu.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vpet.waifu.R
import com.vpet.waifu.domain.ActivityOutcome
import com.vpet.waifu.domain.OutcomeQuality
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.StakeTier
import com.vpet.waifu.domain.Stakes
import com.vpet.waifu.ui.theme.Accents
import com.vpet.waifu.ui.theme.StatColors
import com.vpet.waifu.ui.theme.Surfaces
import kotlin.math.roundToInt

/**
 * The table.
 *
 * Four sizes, from a tenth of the wallet to all of it, each with its own odds
 * and its own payout — and nothing is placed without a card that spells out, in
 * money rather than in percentages, exactly what is being risked and exactly
 * what comes back. That card is the whole reason this is a component rather
 * than three buttons: the previous version offered "a quarter, a half, the
 * lot", named none of them, showed no payout, and settled for a number the
 * player had never been told.
 *
 * It is deliberately a *row of buttons plus a dialog* rather than an expanding
 * panel. The stage, the timer and her mood are what the player is looking at
 * while a shift runs; a betting board that grows over them is the game hiding
 * itself behind its own casino.
 */
@Composable
fun StakeBoard(
    snapshot: PetSnapshot,
    /**
     * The band her mood is in as things stand.
     *
     * Not the band the bet settles on — that is decided at clock-out, hours
     * away — but it is the only honest estimate available, and odds quoted with
     * no basis at all would be worse than odds quoted with a caveat.
     */
    quality: OutcomeQuality,
    onStake: (StakeTier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = snapshot.session ?: return

    if (session.stake > 0) {
        StakeRiding(session.stake, session.stakeTier ?: StakeTier.SMALL, modifier)
        return
    }

    val offers = remember(snapshot.progress.money) { Stakes.offered(snapshot) }
    if (offers.isEmpty()) return

    var confirming by remember { mutableStateOf<Pair<StakeTier, Int>?>(null) }

    Column(modifier) {
        Text(
            text = stringResource(R.string.stake_offer),
            style = MaterialTheme.typography.bodySmall,
            color = Accents.TextDim,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            offers.forEach { (tier, amount) ->
                StakeChip(
                    tier = tier,
                    amount = amount,
                    onClick = { confirming = tier to amount },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    confirming?.let { (tier, amount) ->
        StakeConfirmDialog(
            tier = tier,
            amount = amount,
            wallet = snapshot.progress.money,
            chance = tier.chance(quality),
            onConfirm = {
                confirming = null
                onStake(tier)
            },
            onDismiss = { confirming = null },
        )
    }
}

@Composable
private fun StakeChip(
    tier: StakeTier,
    amount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The dangerous sizes are coloured like danger. A row of four identical
    // buttons where the fourth one can empty the wallet is a trap, not a menu.
    val tint = if (tier.isHighRisk) Accents.Danger else StatColors.Money

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.40f), RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            // Tall enough to be hit without aiming — this is the row a player
            // taps on a bus.
            .heightIn(min = 58.dp)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(tier.labelRes()),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "$amount ¥",
            style = MaterialTheme.typography.bodyMedium,
            color = Accents.Text,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "×${tier.payoutLabel()}",
            style = MaterialTheme.typography.labelSmall,
            color = Accents.TextDim,
        )
    }
}

/**
 * Everything the bet does to the wallet, before it is placed.
 *
 * Four lines, all in money: what leaves, what comes back, what that is worth
 * net, and what is lost if it does not land. The last two are the ones that
 * were missing — a player who is only shown "+20%" is being shown the good half
 * of the arithmetic.
 */
@Composable
private fun StakeConfirmDialog(
    tier: StakeTier,
    amount: Int,
    wallet: Int,
    chance: Float,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val share = if (wallet > 0) (amount * 100f / wallet).roundToInt() else 0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surfaces.Card,
        titleContentColor = Accents.Text,
        textContentColor = Accents.TextMuted,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Icon(
                imageVector = Icons.Rounded.Casino,
                contentDescription = null,
                tint = if (tier.isHighRisk) Accents.Danger else StatColors.Money,
                modifier = Modifier.size(32.dp),
            )
        },
        title = { Text(stringResource(tier.labelRes())) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LedgerRow(stringResource(R.string.stake_line_amount), "−$amount ¥", Accents.Text)
                LedgerRow(
                    stringResource(R.string.stake_line_win),
                    "+${tier.winnings(amount)} ¥",
                    StatColors.Money,
                )
                LedgerRow(
                    stringResource(R.string.stake_line_profit),
                    "+${tier.profit(amount)} ¥",
                    StatColors.Money,
                )
                LedgerRow(
                    stringResource(R.string.stake_line_loss),
                    "−$amount ¥",
                    Accents.Danger,
                )
                LedgerRow(
                    stringResource(R.string.stake_line_chance),
                    "${(chance * 100).roundToInt()}%",
                    Accents.TextMuted,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.stake_house_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = Accents.TextDim,
                )
                if (tier.isHighRisk) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Accents.Danger.copy(alpha = 0.12f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = Accents.Danger,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(
                                if (tier == StakeTier.ALL_IN) {
                                    R.string.stake_warning_all_in
                                } else {
                                    R.string.stake_warning_big
                                },
                                share,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = Accents.Danger,
                        )
                    }
                }
            }
        },
        confirmButton = {
            PrimaryButton(text = stringResource(R.string.stake_place), onClick = onConfirm)
        },
        dismissButton = {
            OutlineButton(text = stringResource(R.string.action_cancel), onClick = onDismiss)
        },
    )
}

@Composable
private fun LedgerRow(label: String, value: String, tint: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Accents.TextMuted,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** What is on the table while the shift runs. No way to add to it or pull out. */
@Composable
private fun StakeRiding(amount: Int, tier: StakeTier, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Rounded.Casino,
            contentDescription = null,
            tint = if (tier.isHighRisk) Accents.Danger else StatColors.Money,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.stake_riding, amount, tier.winnings(amount)),
            style = MaterialTheme.typography.bodySmall,
            color = Accents.TextDim,
        )
    }
}

/**
 * How it went, on the card that already announces the shift.
 *
 * Loud on purpose, and loud in *money*: a bet that resolves into a muted line
 * of small type is a bet the player stops making. The band is the whole width
 * of the dialog, coloured for the answer, and the number on it is the change to
 * the wallet rather than the gross payout — "+3 400" when four hundred was
 * staked is a lie of omission, and "−400" when it lost is the entire point.
 */
@Composable
fun StakeResult(outcome: ActivityOutcome, modifier: Modifier = Modifier) {
    if (!outcome.hadStake) return
    val won = outcome.stakeWon
    val tint = if (won) StatColors.Money else Accents.Danger

    var landed by remember(outcome.completedAt) { mutableStateOf(false) }
    val pop by animateFloatAsState(
        targetValue = if (landed) 1f else 0.6f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "stake-pop",
    )
    LaunchedEffect(outcome.completedAt) { landed = true }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(tint.copy(alpha = 0.14f))
            .border(1.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp, horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (won) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp).scale(pop),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(
                    if (won) R.string.stake_result_won else R.string.stake_result_lost,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = tint,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(modifier = Modifier.scale(pop)) {
            Text(
                text = (if (outcome.stakeNet >= 0) "+" else "−") +
                    "${kotlin.math.abs(outcome.stakeNet)} ¥",
                style = MaterialTheme.typography.headlineSmall,
                color = tint,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.stake_result_detail, outcome.stake, outcome.stakeReturned),
            style = MaterialTheme.typography.labelSmall,
            color = Accents.TextDim,
            textAlign = TextAlign.Center,
        )
    }
}

private fun StakeTier.labelRes(): Int = when (this) {
    StakeTier.SMALL -> R.string.stake_tier_small
    StakeTier.MEDIUM -> R.string.stake_tier_medium
    StakeTier.LARGE -> R.string.stake_tier_large
    StakeTier.ALL_IN -> R.string.stake_tier_all_in
}

/** "1.9" rather than "1.90000002", without dragging a formatter in. */
private fun StakeTier.payoutLabel(): String {
    val tenths = (payout * 10).roundToInt()
    return if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
}
