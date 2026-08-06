package com.vpet.waifu.ui.earn

import androidx.annotation.StringRes
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CurrencyYen
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.RamenDining
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vpet.waifu.R
import com.vpet.waifu.domain.Chore
import com.vpet.waifu.domain.Chores
import com.vpet.waifu.domain.DailyQuest
import com.vpet.waifu.domain.FindKind
import com.vpet.waifu.domain.Finds
import com.vpet.waifu.domain.PetSimulation
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.QuestKind
import com.vpet.waifu.domain.Quests
import com.vpet.waifu.ui.components.BuffStrip
import com.vpet.waifu.ui.components.MoneyPill
import com.vpet.waifu.ui.components.PanelCard
import com.vpet.waifu.ui.components.PrimaryButton
import com.vpet.waifu.ui.components.ScreenTitle
import com.vpet.waifu.ui.components.SectionHeader
import com.vpet.waifu.ui.components.StatBarTrack
import com.vpet.waifu.ui.formatMinutes
import com.vpet.waifu.ui.theme.Accents
import com.vpet.waifu.ui.theme.StatColors
import com.vpet.waifu.ui.theme.Surfaces

/**
 * Everything that pays, in one place.
 *
 * The game grew five separate ways to earn — the tip jar, the check-in streak,
 * three daily quests, the chores, and whatever turns up around the flat — and
 * each of them lived on a different screen or on no screen at all. Spread like
 * that they are not five sources of income, they are five things a player never
 * finds. A player who opens this once should be able to answer, without
 * scrolling: what can I collect right now, and when does the rest come back.
 *
 * That second half is the reason every row here carries either a button or a
 * countdown and never just a description. A row that says "chores pay money"
 * and nothing else is documentation; a row that says "ready" or "in 12 min" is
 * a reason to come back.
 */
@Composable
fun EarnScreen(
    snapshot: PetSnapshot,
    simulation: PetSimulation,
    nowMillis: Long,
    wallet: Int,
    walletSettled: Boolean,
    onClaimQuest: (Int) -> Unit,
    onDoChore: (Chore) -> Unit,
    onClaimFind: () -> Unit,
    onCategoryTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val patting = snapshot.acceptsPat
    val quests = snapshot.questsToday()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenTitle(stringResource(R.string.tab_earn)) {
                MoneyPill(amount = wallet, settled = walletSettled)
            }
        }

        item { BuffStrip(snapshot = snapshot, nowMillis = nowMillis) }

        // Anything collectable goes first, always. It costs one tap and it is
        // already earned; making somebody scroll past six cards to find it is
        // the whole problem this screen exists to fix.
        if (Finds.isWaiting(snapshot, nowMillis)) {
            item { FindCard(snapshot, nowMillis, onClaimFind) }
        }

        item {
            SectionHeader(
                icon = Icons.Rounded.Today,
                title = stringResource(R.string.section_quests),
                tint = Color(0xFFF0C860),
                onTap = onCategoryTap,
                tapEnabled = patting,
            )
        }
        items(quests.size) { index ->
            QuestCard(
                quest = quests[index],
                progress = Quests.progress(snapshot, index),
                done = Quests.isDone(snapshot, index),
                claimed = Quests.isClaimed(snapshot, index),
                onClaim = { onClaimQuest(index) },
            )
        }

        item {
            SectionHeader(
                icon = Icons.Rounded.CleaningServices,
                title = stringResource(R.string.section_chores),
                tint = Color(0xFF7FD1E8),
                onTap = onCategoryTap,
                tapEnabled = patting,
            )
        }
        item {
            Text(
                text = stringResource(R.string.chores_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Accents.TextDim,
            )
        }
        items(
            Chores.ALL.filter { Chores.isUnlocked(it, snapshot) },
            key = { it.id },
        ) { chore ->
            ChoreCard(
                chore = chore,
                ready = Chores.isReady(chore, snapshot, nowMillis),
                readyIn = ((Chores.readyAt(chore, snapshot) - nowMillis) / 60_000L).toInt() + 1,
                onDo = { onDoChore(chore) },
            )
        }

        item {
            SectionHeader(
                icon = Icons.Rounded.AutoAwesome,
                title = stringResource(R.string.section_always),
                tint = Color(0xFFB39CE8),
                onTap = onCategoryTap,
                tapEnabled = patting,
            )
        }
        item { TipJarCard(snapshot, simulation, nowMillis) }
        item { StreakCard(snapshot, simulation) }
        if (!Finds.isWaiting(snapshot, nowMillis)) {
            item { NextFindCard(snapshot, nowMillis) }
        }
    }
}

// --- the day's three jobs ----------------------------------------------------

@Composable
private fun QuestCard(
    quest: DailyQuest,
    progress: Int,
    done: Boolean,
    claimed: Boolean,
    onClaim: () -> Unit,
) {
    PanelCard(modifier = Modifier.fillMaxWidth().alpha(if (claimed) 0.55f else 1f)) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = quest.kind.icon(),
                contentDescription = null,
                tint = if (claimed) Accents.TextDim else StatColors.Money,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(quest.kind.titleRes(), quest.target),
                    style = MaterialTheme.typography.titleSmall,
                    color = Accents.Text,
                )
                Spacer(Modifier.height(6.dp))
                // The bar rather than "2/3" alone: a target you are most of the
                // way to is a different feeling from one you have not started,
                // and two numbers side by side do not carry it.
                StatBarTrack(
                    fraction = progress.toFloat() / quest.target.coerceAtLeast(1),
                    color = StatColors.Money,
                    modifier = Modifier.fillMaxWidth(),
                    height = 6.dp,
                )
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "$progress / ${quest.target}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Accents.TextDim,
                    )
                    Reward(money = quest.money, exp = quest.exp)
                }
            }
            Spacer(Modifier.width(10.dp))
            when {
                claimed -> Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Accents.TextDim,
                    modifier = Modifier.size(22.dp),
                )
                done -> PrimaryButton(
                    text = stringResource(R.string.quest_claim),
                    onClick = onClaim,
                )
                else -> Unit
            }
        }
    }
}

// --- chores ------------------------------------------------------------------

@Composable
private fun ChoreCard(chore: Chore, ready: Boolean, readyIn: Int, onDo: () -> Unit) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = chore.icon(),
                contentDescription = null,
                tint = if (ready) Color(0xFF7FD1E8) else Accents.TextDim,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(chore.titleRes()),
                    style = MaterialTheme.typography.titleSmall,
                    color = Accents.Text,
                )
                Spacer(Modifier.height(4.dp))
                Reward(money = chore.money, exp = chore.exp)
            }
            Spacer(Modifier.width(10.dp))
            if (ready) {
                PrimaryButton(text = stringResource(R.string.chore_do), onClick = onDo)
            } else {
                Text(
                    text = formatMinutes(readyIn),
                    style = MaterialTheme.typography.labelMedium,
                    color = Accents.TextDim,
                )
            }
        }
    }
}

// --- what turned up ----------------------------------------------------------

/**
 * The find, when there is one.
 *
 * Pulsing, because it is the one thing on this screen that goes away on its
 * own: [Finds.LINGER_MINUTES] and it is gone. Everything else here waits.
 */
@Composable
private fun FindCard(snapshot: PetSnapshot, nowMillis: Long, onClaim: () -> Unit) {
    val kind = Finds.kindOf(snapshot.findReadyAt)
    val reward = Finds.rewardFor(kind, snapshot.level)
    val leavesIn = ((snapshot.findReadyAt + Finds.LINGER_MINUTES * 60_000L - nowMillis) / 60_000L)
        .toInt() + 1

    val pulse by rememberInfiniteTransition(label = "find").animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(StatColors.Money.copy(alpha = 0.12f))
            .border(1.dp, StatColors.Money.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = kind.icon(),
                contentDescription = null,
                tint = StatColors.Money,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { scaleX = pulse; scaleY = pulse },
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(kind.titleRes()),
                    style = MaterialTheme.typography.titleSmall,
                    color = Accents.Text,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Reward(money = reward.money, exp = reward.exp)
                    Text(
                        text = stringResource(R.string.find_leaves_in, formatMinutes(leavesIn)),
                        style = MaterialTheme.typography.labelSmall,
                        color = Accents.TextDim,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            PrimaryButton(text = stringResource(R.string.find_take), onClick = onClaim)
        }
    }
}

@Composable
private fun NextFindCard(snapshot: PetSnapshot, nowMillis: Long) {
    val minutes = ((snapshot.findReadyAt - nowMillis) / 60_000L).toInt() + 1
    InfoRow(
        icon = Icons.Rounded.AutoAwesome,
        title = stringResource(R.string.earn_find_title),
        detail = if (minutes > 0) {
            stringResource(R.string.earn_find_next, formatMinutes(minutes))
        } else {
            stringResource(R.string.earn_find_soon)
        },
        tint = Color(0xFFB39CE8),
    )
}

// --- the two that never stop -------------------------------------------------

@Composable
private fun TipJarCard(snapshot: PetSnapshot, simulation: PetSimulation, nowMillis: Long) {
    val left = simulation.passiveLeftToday(snapshot, nowMillis)
    val cap = simulation.passiveDailyCap(snapshot).coerceAtLeast(1)
    InfoRow(
        icon = Icons.Rounded.CurrencyYen,
        title = stringResource(R.string.earn_jar_title, simulation.passivePerMinute(snapshot)),
        // The bond line is the point of showing this at all: the jar is the one
        // income that grows purely from how long you have been around, and
        // nothing anywhere said so.
        detail = stringResource(R.string.earn_jar_detail, left, snapshot.bondLevel),
        tint = StatColors.Money,
        fraction = left.toFloat() / cap,
    )
}

@Composable
private fun StreakCard(snapshot: PetSnapshot, simulation: PetSimulation) {
    InfoRow(
        icon = Icons.Rounded.Favorite,
        title = stringResource(R.string.earn_streak_title, snapshot.streakDays),
        detail = stringResource(
            R.string.earn_streak_detail,
            simulation.dailyReward(snapshot.streakDays + 1),
        ),
        tint = StatColors.Mood,
    )
}

// --- shared bits -------------------------------------------------------------

@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    detail: String,
    tint: Color,
    fraction: Float? = null,
) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Accents.Text,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = Accents.TextDim,
                )
                if (fraction != null) {
                    Spacer(Modifier.height(6.dp))
                    StatBarTrack(fraction = fraction, color = tint, modifier = Modifier.fillMaxWidth(), height = 6.dp)
                }
            }
        }
    }
}

/** Money and EXP together, the way every payout in the game is shown. */
@Composable
private fun Reward(money: Int, exp: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (money > 0) {
            Text(
                text = "+$money ¥",
                style = MaterialTheme.typography.labelSmall,
                color = StatColors.Money,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (exp > 0) {
            Text(
                text = "+$exp EXP",
                style = MaterialTheme.typography.labelSmall,
                color = StatColors.Exp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// --- names and icons, kept out of the domain ---------------------------------

private fun QuestKind.icon(): ImageVector = when (this) {
    QuestKind.FEED -> Icons.Rounded.RamenDining
    QuestKind.SHIFT -> Icons.Rounded.Work
    QuestKind.LESSON -> Icons.Rounded.School
    QuestKind.GAME -> Icons.Rounded.SportsEsports
    QuestKind.GIFT -> Icons.Rounded.CardGiftcard
    QuestKind.EARN -> Icons.Rounded.CurrencyYen
    QuestKind.PAT -> Icons.Rounded.Favorite
}

@StringRes
private fun QuestKind.titleRes(): Int = when (this) {
    QuestKind.FEED -> R.string.quest_feed
    QuestKind.SHIFT -> R.string.quest_shift
    QuestKind.LESSON -> R.string.quest_lesson
    QuestKind.GAME -> R.string.quest_game
    QuestKind.GIFT -> R.string.quest_gift
    QuestKind.EARN -> R.string.quest_earn
    QuestKind.PAT -> R.string.quest_pat
}

private fun Chore.icon(): ImageVector = when (id) {
    "dishes" -> Icons.Rounded.LocalCafe
    "tidy" -> Icons.Rounded.CleaningServices
    "laundry" -> Icons.Rounded.LocalLaundryService
    "plant" -> Icons.Rounded.LocalFlorist
    else -> Icons.Rounded.Pets
}

@StringRes
private fun Chore.titleRes(): Int = when (id) {
    "dishes" -> R.string.chore_dishes
    "tidy" -> R.string.chore_tidy
    "laundry" -> R.string.chore_laundry
    "plant" -> R.string.chore_plant
    else -> R.string.chore_cat
}

private fun FindKind.icon(): ImageVector = when (this) {
    FindKind.COIN -> Icons.Rounded.CurrencyYen
    FindKind.BOOK -> Icons.Rounded.MenuBook
    FindKind.SNACK -> Icons.Rounded.Star
}

@StringRes
private fun FindKind.titleRes(): Int = when (this) {
    FindKind.COIN -> R.string.find_coin
    FindKind.BOOK -> R.string.find_book
    FindKind.SNACK -> R.string.find_snack
}
