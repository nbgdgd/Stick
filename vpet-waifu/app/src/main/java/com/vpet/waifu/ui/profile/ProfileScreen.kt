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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vpet.waifu.R
import com.vpet.waifu.domain.Bond
import com.vpet.waifu.domain.Focus
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
) {
    val patting = snapshot.acceptsPat
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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

        item { BondCard(snapshot) }
        item { FocusCard(snapshot, onChooseFocus) }

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

        item {
            SectionHeader(
                Icons.Rounded.EmojiEvents,
                stringResource(R.string.ach_title),
                tint = com.vpet.waifu.ui.theme.StatColors.Money,
                onTap = onCategoryTap,
                tapEnabled = patting,
            )
        }
        item { AchievementsCard(snapshot) }

        item {
            SectionHeader(
                Icons.Rounded.Checkroom,
                stringResource(R.string.wardrobe_title),
                tint = androidx.compose.ui.graphics.Color(0xFF9B8CF0),
                onTap = onCategoryTap,
                tapEnabled = patting,
            )
        }
        item { WardrobeCard(snapshot, onWear) }

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
            Row {
                Text(
                    text = stringResource(bondNameRes(snapshot.bondLevel)),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = StatColors.Mood,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.bond_today, snapshot.bondToday, Bond.DAILY_CAP),
                    style = MaterialTheme.typography.bodySmall,
                    color = Accents.TextMuted,
                )
            }
            Text(
                text = stringResource(R.string.bond_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Accents.TextDim,
            )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AchievementsCard(snapshot: PetSnapshot) {
    val trophies = achievementsFor(snapshot)
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            trophies.forEach { trophy ->
                Box(modifier = Modifier.alpha(if (trophy.earned) 1f else 0.35f)) {
                    EffectChip(
                        icon = trophy.icon,
                        text = stringResource(trophy.titleRes),
                        tint = if (trophy.earned) trophy.tint else Accents.TextDim,
                    )
                }
            }
        }
    }
}

/** Owned outfits, wearable with one tap. The shop still sells; this shows off. */
@Composable
private fun WardrobeCard(snapshot: PetSnapshot, onWear: (String) -> Unit) {
    val outfits = Upgrades.OUTFITS.filter { snapshot.owns(it.id) }
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
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
