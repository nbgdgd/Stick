package com.vpet.waifu.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Work
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vpet.waifu.R
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import com.vpet.waifu.domain.Bond
import com.vpet.waifu.domain.Focus
import com.vpet.waifu.domain.GoalKind
import com.vpet.waifu.domain.WeeklyGoals
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.Story
import com.vpet.waifu.domain.UpgradeKind
import com.vpet.waifu.domain.Upgrades
import com.vpet.waifu.ui.achievementsFor
import com.vpet.waifu.ui.bondNameRes
import com.vpet.waifu.ui.character.PetPalette
import com.vpet.waifu.ui.chapterGoalRes
import com.vpet.waifu.ui.chapterTitleRes
import com.vpet.waifu.ui.components.EffectChip
import com.vpet.waifu.ui.components.PanelCard
import com.vpet.waifu.ui.components.ScreenTitle
import com.vpet.waifu.ui.components.SectionHeader
import com.vpet.waifu.ui.components.StatBarTrack
import com.vpet.waifu.ui.focusDescRes
import com.vpet.waifu.ui.focusIcon
import com.vpet.waifu.ui.focusNameRes
import com.vpet.waifu.ui.focusTint
import com.vpet.waifu.ui.theme.Accents
import com.vpet.waifu.ui.theme.StatColors
import com.vpet.waifu.ui.theme.Surfaces
import com.vpet.waifu.ui.upgradeNameRes

/**
 * Her page: the bond, the story, the path, the trophies, the wardrobe, and the
 * life you have lived together in numbers.
 *
 * Everything the game *keeps* lives here. The other four tabs are about what is
 * happening right now; this one is the proof that any of it added up to
 * something — which is precisely what the game had nowhere to show before.
 */
@Composable
fun ProfileScreen(
    snapshot: PetSnapshot,
    petName: String,
    nowMillis: Long,
    onChooseFocus: (Focus) -> Unit,
    onWear: (String) -> Unit,
    onCategoryTap: () -> Unit,
    modifier: Modifier = Modifier,
    /** When each trophy landed, where the app was running to see it. */
    trophyDates: Map<String, Long> = emptyMap(),
) {
    val patting = snapshot.acceptsPat
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenTitle(petName.ifBlank { stringResource(R.string.tab_her) }) {
                EffectChip(
                    icon = Icons.Rounded.Favorite,
                    text = stringResource(bondNameRes(snapshot.bondLevel)),
                    tint = StatColors.Mood,
                )
            }
        }

        // The hierarchy: where the story stands, then the bond, then the
        // numbers. Everything actionable-later comes after.
        item {
            SectionHeader(
                Icons.AutoMirrored.Rounded.MenuBook,
                stringResource(R.string.story_title),
                tint = Accents.Bright,
                onTap = onCategoryTap,
                tapEnabled = patting,
            )
        }
        item { StoryCard(snapshot) }

        item { BondCard(snapshot) }

        item {
            SectionHeader(
                Icons.Rounded.AutoAwesome,
                stringResource(R.string.stats_title),
                tint = androidx.compose.ui.graphics.Color(0xFF7FD1E8),
                onTap = onCategoryTap,
                tapEnabled = patting,
            )
        }
        item { StatsCard(snapshot, nowMillis) }

        item { WeeklyGoalCard(snapshot) }
        item { FocusCard(snapshot, onChooseFocus) }

        item {
            SectionHeader(
                Icons.Rounded.EmojiEvents,
                stringResource(R.string.ach_title),
                tint = com.vpet.waifu.ui.theme.StatColors.Money,
                onTap = onCategoryTap,
                tapEnabled = patting,
            )
        }
        item { AchievementsCard(snapshot, trophyDates) }

        // The wardrobe earns its place on the page only once there is a
        // choice to make — one default outfit is not a wardrobe.
        if (Upgrades.OUTFITS.count { snapshot.owns(it.id) } >= 2) {
            item {
                SectionHeader(
                    Icons.Rounded.Checkroom,
                    stringResource(R.string.wardrobe_title),
                    tint = androidx.compose.ui.graphics.Color(0xFFB39CE8),
                    onTap = onCategoryTap,
                    tapEnabled = patting,
                )
            }
            item { WardrobeCard(snapshot, onWear) }
        }
    }
}

/**
 * The week's goal: the repeating heartbeat that outlives the story.
 *
 * One card, three states — locked with the unlock condition named, in
 * progress with a bar, and done with the promise of the next one.
 */
@Composable
private fun WeeklyGoalCard(snapshot: PetSnapshot) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Flag,
                    contentDescription = null,
                    tint = StatColors.Money,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.goal_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Accents.Text,
                    modifier = Modifier.weight(1f),
                )
            }

            if (!WeeklyGoals.unlocked(snapshot)) {
                Text(
                    text = stringResource(R.string.goal_locked, WeeklyGoals.UNLOCK_CHAPTER),
                    style = MaterialTheme.typography.bodySmall,
                    color = Accents.TextDim,
                )
                return@Column
            }

            val kind = WeeklyGoals.kindFor(snapshot.goalWeek)
            val target = WeeklyGoals.targetFor(kind, snapshot.level)
            val done = WeeklyGoals.progress(snapshot)
            Text(
                text = when (kind) {
                    GoalKind.SHIFTS -> stringResource(R.string.goal_shifts, target)
                    GoalKind.LESSONS -> stringResource(R.string.goal_lessons, target)
                    GoalKind.GAMES -> stringResource(R.string.goal_games, target)
                    GoalKind.EARN -> stringResource(R.string.goal_earn, target)
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Accents.Text,
            )
            StatBarTrack(
                fraction = if (target <= 0) 1f else done.toFloat() / target,
                color = StatColors.Money,
                height = 7.dp,
            )
            Row {
                Text(
                    text = if (snapshot.goalRewarded) {
                        stringResource(R.string.goal_done)
                    } else {
                        stringResource(
                            R.string.goal_reward,
                            WeeklyGoals.rewardFor(kind, snapshot.level),
                            WeeklyGoals.BOND_REWARD,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (snapshot.goalRewarded) StatColors.Exp else Accents.TextMuted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.goal_progress, done, target),
                    style = MaterialTheme.typography.labelMedium,
                    color = Accents.TextMuted,
                )
            }
        }
    }
}

/** The number that only grows, and how today contributed to it. */
@Composable
private fun BondCard(snapshot: PetSnapshot) {
    val (earned, needed) = Bond.levelProgress(snapshot.bondPoints)
    val maxed = snapshot.bondLevel >= Bond.MAX_LEVEL

    PanelCard(modifier = Modifier.fillMaxWidth(), border = StatColors.Mood.copy(alpha = 0.35f)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = StatColors.Mood,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.bond_level_label, snapshot.bondLevel),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Accents.Text,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (maxed) "" else stringResource(R.string.bond_progress, earned, needed),
                    style = MaterialTheme.typography.labelMedium,
                    color = Accents.TextMuted,
                )
            }
            StatBarTrack(
                fraction = if (maxed) 1f else earned.toFloat() / needed,
                color = StatColors.Mood,
                height = 7.dp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(bondNameRes(snapshot.bondLevel)),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = StatColors.Mood,
                    modifier = Modifier.weight(1f),
                )
                EffectChip(
                    icon = Icons.Rounded.Favorite,
                    text = stringResource(R.string.bond_today, snapshot.bondToday, Bond.DAILY_CAP),
                    tint = StatColors.Mood,
                )
            }
            // The sources as icons, the rule as one quiet line.
            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf(
                    Icons.Rounded.Restaurant,
                    Icons.Rounded.Favorite,
                    Icons.Rounded.CardGiftcard,
                    Icons.Rounded.Work,
                    Icons.Rounded.SportsEsports,
                ).forEach { source ->
                    Icon(
                        imageVector = source,
                        contentDescription = null,
                        tint = Accents.TextMuted,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = stringResource(R.string.bond_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Accents.TextDim,
                )
            }
        }
    }
}

/**
 * The fork. Three options while it is open, one line forever after.
 *
 * There is deliberately no confirmation dialog: the card says "once, for good"
 * in plain words, and a second gate teaches players that warnings are noise.
 */
@Composable
private fun FocusCard(snapshot: PetSnapshot, onChoose: (Focus) -> Unit) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Route,
                    contentDescription = null,
                    tint = Accents.Bright,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.focus_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Accents.Text,
                )
            }

            val chosen = snapshot.focus
            when {
                chosen != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = focusIcon(chosen),
                        contentDescription = null,
                        tint = focusTint(chosen),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.focus_chosen, stringResource(focusNameRes(chosen))),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Accents.Text,
                        )
                        Text(
                            text = stringResource(focusDescRes(chosen)),
                            style = MaterialTheme.typography.bodySmall,
                            color = Accents.TextMuted,
                        )
                    }
                }

                snapshot.level < Focus.UNLOCK_LEVEL -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = Accents.TextDim,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.focus_locked, Focus.UNLOCK_LEVEL),
                        style = MaterialTheme.typography.bodySmall,
                        color = Accents.TextDim,
                    )
                }

                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.focus_pick_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = StatColors.Hunger,
                    )
                    Focus.entries.forEach { option ->
                        FocusOption(option, onChoose)
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusOption(option: Focus, onChoose: (Focus) -> Unit) {
    val tint = focusTint(option)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onChoose(option) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = focusIcon(option),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = stringResource(focusNameRes(option)),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Accents.Text,
            )
            Text(
                text = stringResource(focusDescRes(option)),
                style = MaterialTheme.typography.bodySmall,
                color = Accents.TextMuted,
            )
        }
    }
}

/** All chapters: done, current goal, and a discreet veil over the future. */
@Composable
private fun StoryCard(snapshot: PetSnapshot) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = if (Story.isComplete(snapshot.storyChapter)) {
                    stringResource(R.string.story_complete)
                } else {
                    stringResource(
                        R.string.story_progress,
                        snapshot.storyChapter + 1,
                        Story.CHAPTERS.size,
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Accents.Text,
            )
            Story.CHAPTERS.forEachIndexed { index, chapter ->
                val done = index < snapshot.storyChapter
                val current = index == snapshot.storyChapter
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    done -> StatColors.Exp.copy(alpha = 0.2f)
                                    current -> Accents.Primary.copy(alpha = 0.2f)
                                    else -> Surfaces.Tile
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (done) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = StatColors.Exp,
                                modifier = Modifier.size(14.dp),
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (current) Accents.Text else Accents.TextDim,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.alpha(if (done || current) 1f else 0.45f)) {
                        Text(
                            text = stringResource(chapterTitleRes(chapter.id)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                            color = Accents.Text,
                        )
                        // The goal is only spelled out for the chapter in
                        // progress: the past needs no instructions and the
                        // future stays a little mysterious.
                        if (current) {
                            Text(
                                text = stringResource(chapterGoalRes(chapter.id)),
                                style = MaterialTheme.typography.bodySmall,
                                color = Accents.TextMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Collapsed by default: the next three trophies within reach, plus the tally.
 * The full cabinet unfolds on demand instead of wallpapering the screen.
 */
@OptIn(ExperimentalLayoutApi::class)

/** A trophy's date, short enough to sit on a chip. */
@Composable
private fun shortDate(millis: Long): String {
    val format = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    return remember(millis) { format.format(Date(millis)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AchievementsCard(snapshot: PetSnapshot, trophyDates: Map<String, Long>) {
    val trophies = achievementsFor(snapshot)
    val earnedCount = trophies.count { it.earned }
    var expanded by rememberSaveable { mutableStateOf(false) }
    // Each family is listed easy-to-hard, so the first unearned entries are
    // the nearest ones to unlocking.
    // "Nearest" by how much of the condition is done, not by declaration
    // order — a brand-new save used to be shown "fifty shifts" as a goal.
    val next = trophies.filter { !it.earned }.sortedByDescending { it.fraction }.take(3)
    val recent = trophies.filter { it.earned }.takeLast(2)
    val shown = if (expanded) trophies else recent + next

    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.ach_progress, earnedCount, trophies.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = Accents.TextMuted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (expanded) {
                        stringResource(R.string.ach_hide)
                    } else {
                        stringResource(R.string.ach_show_all, trophies.size)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Accents.Primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { expanded = !expanded }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                shown.forEach { trophy ->
                    Box(modifier = Modifier.alpha(if (trophy.earned) 1f else 0.55f)) {
                        EffectChip(
                            icon = trophy.icon,
                            text = when {
                                // The day it landed, when the app was there to
                                // see it. Silence rather than a guess for the
                                // ones earned before dates were recorded — an
                                // invented day in somebody's history is worse
                                // than an absent one.
                                trophy.earned -> {
                                    val at = trophyDates[trophy.titleRes.toString()]
                                    if (at != null) {
                                        "${stringResource(trophy.titleRes)} · ${shortDate(at)}"
                                    } else {
                                        stringResource(trophy.titleRes)
                                    }
                                }
                                else -> "${stringResource(trophy.titleRes)} · ${trophy.current}/${trophy.target}"
                            },
                            tint = if (trophy.earned) trophy.tint else Accents.TextMuted,
                        )
                    }
                }
            }
        }
    }
}

/** Owned outfits, wearable with one tap. The shop still sells; this shows off. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WardrobeCard(snapshot: PetSnapshot, onWear: (String) -> Unit) {
    val outfits = Upgrades.OUTFITS.filter { snapshot.owns(it.id) }
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        // Six of these do not fit a phone: the last two — one of them the
        // 25 000 ¥ gown — used to be clipped off the card and untappable.
        FlowRow(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            outfits.forEach { outfit ->
                val worn = snapshot.outfit == outfit.id
                val ribbon = PetPalette.forOutfit(outfit.id).ribbon
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (worn) ribbon.copy(alpha = 0.15f) else Surfaces.Tile)
                        .border(
                            width = if (worn) 1.5.dp else 1.dp,
                            color = if (worn) ribbon else Surfaces.TileBorder,
                            shape = RoundedCornerShape(14.dp),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = !worn,
                        ) { onWear(outfit.id) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(ribbon),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(upgradeNameRes(outfit.id)),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (worn) Accents.Text else Accents.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsCard(snapshot: PetSnapshot, nowMillis: Long) {
    val born = if (snapshot.bornAt > 0) snapshot.bornAt else nowMillis
    val days = ((nowMillis - born) / (24L * 60 * 60 * 1000)).toInt() + 1
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatLine(stringResource(R.string.stat_days_together), "$days")
            StatLine(stringResource(R.string.stat_total_earned), "${snapshot.totalEarned} ¥")
            StatLine(stringResource(R.string.stat_shifts), "${snapshot.shiftsWorked}")
            StatLine(stringResource(R.string.stat_lessons), "${snapshot.lessonsDone}")
            StatLine(stringResource(R.string.stat_games), "${snapshot.gamesPlayed}")
            StatLine(stringResource(R.string.stat_meals), "${snapshot.mealsFed}")
            StatLine(stringResource(R.string.stat_gifts), "${snapshot.giftsGiven}")
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Accents.TextMuted,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Accents.Text,
        )
    }
}
