package com.vpet.waifu.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.vpet.waifu.domain.ActiveEffect
import com.vpet.waifu.domain.ActivityOutcome
import com.vpet.waifu.domain.ActivitySession
import com.vpet.waifu.domain.EffectKind
import com.vpet.waifu.domain.Emote
import com.vpet.waifu.domain.OccupationKind
import com.vpet.waifu.domain.OutcomeQuality
import com.vpet.waifu.domain.PetActivity
import com.vpet.waifu.domain.PetProgress
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.PetStats
import com.vpet.waifu.domain.EventKind
import com.vpet.waifu.domain.Focus
import com.vpet.waifu.domain.Journal
import com.vpet.waifu.domain.JournalEntry
import com.vpet.waifu.domain.JournalKind
import com.vpet.waifu.domain.MiniGame
import com.vpet.waifu.domain.PetRequest
import com.vpet.waifu.domain.RequestKind
import com.vpet.waifu.domain.Story
import com.vpet.waifu.domain.PetEvent
import com.vpet.waifu.domain.Upgrades

/**
 * The single row that is the save file.
 *
 * There is exactly one pet, so the table is pinned to [SINGLETON_ID]; that turns
 * every write into an idempotent upsert and makes "load the game" a single
 * indexed lookup. The schema is deliberately flat — enums and the effect list
 * are stored as text so that adding a job or a pill in a later phase never
 * needs a migration.
 */
@Entity(tableName = "pet_state")
data class PetStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val hunger: Float,
    val energy: Float,
    val mood: Float,
    val activity: String,
    val lastTickAt: Long,
    val lastInteractionAt: Long,
    val money: Int = PetProgress.START_MONEY,
    val exp: Int = 0,
    val sessionOccupationId: String? = null,
    val sessionStartedAt: Long = 0,
    val sessionEndsAt: Long = 0,
    val sessionAccruedPay: Float = 0f,
    val sessionPaidOut: Int = 0,
    val sessionAccruedExp: Float = 0f,
    val sessionPaidExp: Int = 0,
    /** `KIND:expiresAt` pairs, comma separated. */
    val effects: String = "",
    /** Permanently owned upgrade ids, comma separated. */
    val owned: String = Upgrades.DEFAULT_OUTFIT,
    val outfit: String = Upgrades.DEFAULT_OUTFIT,
    val theme: String = Upgrades.DEFAULT_THEME,
    val eventKind: String? = null,
    val eventDay: Long = 0,
    val eventSeenAt: Long = 0,
    val lastMealId: String? = null,
    val repeatedMeals: Int = 0,
    val outcomeOccupationId: String? = null,
    val outcomeKind: String? = null,
    val outcomeMoney: Int = 0,
    val outcomeExp: Int = 0,
    val outcomeQuality: String? = null,
    val outcomeCancelled: Boolean = false,
    val outcomeAt: Long = 0,
    val emote: String? = null,
    val emoteUntil: Long = 0,
    /** The tip jar: when it was last settled, and the fraction of a coin left over. */
    val passiveSince: Long = 0,
    val passiveBank: Float = 0f,
    val passiveDay: Long = 0,
    val passivePaidToday: Int = 0,
    /** Attachment, and the day-total pair behind its daily cap. */
    val bondPoints: Int = 0,
    val bondDay: Long = 0,
    val bondToday: Int = 0,
    /** Sickness: when she fell ill (0 = healthy) and the recent-neglect counter. */
    val sickSince: Long = 0,
    val runDownMinutes: Float = 0f,
    /** Her live wish, flattened. */
    val requestKind: String? = null,
    val requestItemId: String? = null,
    val requestUntil: Long = 0,
    val lastRequestSlot: Long = 0,
    /** Story progress, and the last chapter the player has seen. */
    val storyChapter: Int = 0,
    val storySeen: Int = 0,
    /** The path she committed to, if any. */
    val focus: String? = null,
    /** The life lived so far. */
    val shiftsWorked: Int = 0,
    val lessonsDone: Int = 0,
    val gamesPlayed: Int = 0,
    val mealsFed: Int = 0,
    val giftsGiven: Int = 0,
    val sicknessesNursed: Int = 0,
    val totalEarned: Int = 0,
    val bornAt: Long = 0,
    /** Her diary, flattened as `kind:detail:amount:at` entries joined by `|`. */
    val journal: String = "",
    /** Best round per mini-game. */
    val bestCatch: Int = 0,
    val bestRhythm: Int = 0,
    val bestMemory: Int = 0,
    /** The week's goal bookkeeping. */
    val goalWeek: Long = 0,
    val goalBaseline: Int = 0,
    val goalRewarded: Boolean = false,
    /** The largest day-count anniversary already celebrated. */
    val celebratedMilestone: Int = 0,
    /** The check-in streak, and the day it was last credited on. */
    val streakDays: Int = 0,
    val bestStreak: Int = 0,
    val lastLoginDay: Long = 0,
    /** The day the day off was last taken on, 0 for never. */
    val dayOffDay: Long = 0,
    /** What the check-in paid, until the player has been shown it. */
    val pendingDaily: Int = 0,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}

// Reads are all defensive: a row written by an older build, or one hand-edited
// by a curious user, must degrade rather than crash the app on launch.

fun PetStateEntity.toSnapshot(): PetSnapshot = PetSnapshot(
    stats = PetStats.coerced(hunger, energy, mood),
    progress = PetProgress(money = money.coerceAtLeast(0), exp = exp.coerceAtLeast(0)),
    activity = enumOr(activity, PetActivity.AWAKE),
    session = sessionOccupationId?.let {
        ActivitySession(
            occupationId = it,
            startedAt = sessionStartedAt,
            endsAt = sessionEndsAt,
            accruedPay = sessionAccruedPay,
            paidOut = sessionPaidOut,
            accruedExp = sessionAccruedExp,
            paidExp = sessionPaidExp,
        )
    },
    effects = decodeEffects(effects),
    lastOutcome = outcomeOccupationId?.let { id ->
        ActivityOutcome(
            occupationId = id,
            kind = enumOr(outcomeKind, OccupationKind.WORK),
            money = outcomeMoney,
            exp = outcomeExp,
            quality = enumOr(outcomeQuality, OutcomeQuality.GOOD),
            cancelled = outcomeCancelled,
            completedAt = outcomeAt,
        )
    },
    emote = emote?.let { enumOrNull<Emote>(it) },
    emoteUntil = emoteUntil,
    lastTickAt = lastTickAt,
    lastInteractionAt = lastInteractionAt,
    owned = decodeIds(owned) + Upgrades.DEFAULT_OUTFIT + Upgrades.DEFAULT_THEME,
    outfit = outfit.takeIf { Upgrades.byId(it) != null } ?: Upgrades.DEFAULT_OUTFIT,
    theme = theme.takeIf { Upgrades.byId(it) != null } ?: Upgrades.DEFAULT_THEME,
    event = enumOrNull<EventKind>(eventKind)?.let { PetEvent(it, eventDay, eventSeenAt) },
    lastMealId = lastMealId,
    repeatedMeals = repeatedMeals.coerceAtLeast(0),
    passiveSince = passiveSince,
    passiveBank = passiveBank.coerceIn(0f, 1f),
    passiveDay = passiveDay,
    passivePaidToday = passivePaidToday.coerceAtLeast(0),
    bondPoints = bondPoints.coerceAtLeast(0),
    bondDay = bondDay,
    bondToday = bondToday.coerceAtLeast(0),
    sickSince = sickSince.coerceAtLeast(0),
    runDownMinutes = runDownMinutes.coerceAtLeast(0f),
    request = enumOrNull<RequestKind>(requestKind)?.let { kind ->
        PetRequest(kind, requestItemId, requestUntil, lastRequestSlot)
    },
    lastRequestSlot = lastRequestSlot,
    storyChapter = storyChapter.coerceIn(0, Story.CHAPTERS.size),
    storySeen = storySeen.coerceIn(0, Story.CHAPTERS.size),
    focus = Focus.byName(focus),
    shiftsWorked = shiftsWorked.coerceAtLeast(0),
    lessonsDone = lessonsDone.coerceAtLeast(0),
    gamesPlayed = gamesPlayed.coerceAtLeast(0),
    mealsFed = mealsFed.coerceAtLeast(0),
    giftsGiven = giftsGiven.coerceAtLeast(0),
    sicknessesNursed = sicknessesNursed.coerceAtLeast(0),
    totalEarned = totalEarned.coerceAtLeast(0),
    bornAt = bornAt,
    journal = decodeJournal(journal),
    bestScores = buildMap {
        if (bestCatch > 0) put(MiniGame.CATCH, bestCatch)
        if (bestRhythm > 0) put(MiniGame.RHYTHM, bestRhythm)
        if (bestMemory > 0) put(MiniGame.MEMORY, bestMemory)
    },
    goalWeek = goalWeek,
    goalBaseline = goalBaseline.coerceAtLeast(0),
    goalRewarded = goalRewarded,
    celebratedMilestone = celebratedMilestone.coerceAtLeast(0),
    streakDays = streakDays.coerceAtLeast(0),
    bestStreak = bestStreak.coerceAtLeast(0),
    lastLoginDay = lastLoginDay.coerceAtLeast(0),
    dayOffDay = dayOffDay.coerceAtLeast(0),
    pendingDaily = pendingDaily.coerceAtLeast(0),
)

fun PetSnapshot.toEntity(): PetStateEntity = PetStateEntity(
    hunger = stats.hunger,
    energy = stats.energy,
    mood = stats.mood,
    activity = activity.name,
    lastTickAt = lastTickAt,
    lastInteractionAt = lastInteractionAt,
    money = progress.money,
    exp = progress.exp,
    sessionOccupationId = session?.occupationId,
    sessionStartedAt = session?.startedAt ?: 0,
    sessionEndsAt = session?.endsAt ?: 0,
    sessionAccruedPay = session?.accruedPay ?: 0f,
    sessionPaidOut = session?.paidOut ?: 0,
    sessionAccruedExp = session?.accruedExp ?: 0f,
    sessionPaidExp = session?.paidExp ?: 0,
    effects = encodeEffects(effects),
    outcomeOccupationId = lastOutcome?.occupationId,
    outcomeKind = lastOutcome?.kind?.name,
    outcomeMoney = lastOutcome?.money ?: 0,
    outcomeExp = lastOutcome?.exp ?: 0,
    outcomeQuality = lastOutcome?.quality?.name,
    outcomeCancelled = lastOutcome?.cancelled ?: false,
    outcomeAt = lastOutcome?.completedAt ?: 0,
    emote = emote?.name,
    emoteUntil = emoteUntil,
    owned = owned.joinToString(","),
    outfit = outfit,
    theme = theme,
    eventKind = event?.kind?.name,
    eventDay = event?.day ?: 0,
    eventSeenAt = event?.seenAt ?: 0,
    lastMealId = lastMealId,
    repeatedMeals = repeatedMeals,
    passiveSince = passiveSince,
    passiveBank = passiveBank,
    passiveDay = passiveDay,
    passivePaidToday = passivePaidToday,
    bondPoints = bondPoints,
    bondDay = bondDay,
    bondToday = bondToday,
    sickSince = sickSince,
    runDownMinutes = runDownMinutes,
    requestKind = request?.kind?.name,
    requestItemId = request?.itemId,
    requestUntil = request?.until ?: 0,
    lastRequestSlot = lastRequestSlot,
    storyChapter = storyChapter,
    storySeen = storySeen,
    focus = focus?.name,
    shiftsWorked = shiftsWorked,
    lessonsDone = lessonsDone,
    gamesPlayed = gamesPlayed,
    mealsFed = mealsFed,
    giftsGiven = giftsGiven,
    sicknessesNursed = sicknessesNursed,
    totalEarned = totalEarned,
    bornAt = bornAt,
    journal = encodeJournal(journal),
    bestCatch = bestScores[MiniGame.CATCH] ?: 0,
    bestRhythm = bestScores[MiniGame.RHYTHM] ?: 0,
    bestMemory = bestScores[MiniGame.MEMORY] ?: 0,
    goalWeek = goalWeek,
    goalBaseline = goalBaseline,
    goalRewarded = goalRewarded,
    celebratedMilestone = celebratedMilestone,
    streakDays = streakDays,
    bestStreak = bestStreak,
    lastLoginDay = lastLoginDay,
    dayOffDay = dayOffDay,
    pendingDaily = pendingDaily,
)

// The diary rides in one text column, like the effects: `kind:detail:amount:at`
// entries joined by `|`. Details are plain ids and enum names, so the
// separators can never appear inside a field.
private fun encodeJournal(journal: List<JournalEntry>): String =
    journal.joinToString("|") { "${it.kind.name}:${it.detail ?: ""}:${it.amount}:${it.at}" }

private fun decodeJournal(raw: String): List<JournalEntry> =
    raw.split('|')
        .filter { it.isNotBlank() }
        .mapNotNull { entry ->
            val parts = entry.split(':')
            if (parts.size != 4) return@mapNotNull null
            val kind = enumOrNull<JournalKind>(parts[0]) ?: return@mapNotNull null
            JournalEntry(
                kind = kind,
                detail = parts[1].ifEmpty { null },
                amount = parts[2].toIntOrNull() ?: 0,
                at = parts[3].toLongOrNull() ?: 0L,
            )
        }
        .takeLast(Journal.MAX_ENTRIES)

private fun decodeIds(raw: String): Set<String> =
    raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

private fun encodeEffects(effects: List<ActiveEffect>): String =
    effects.joinToString(",") { "${it.kind.name}:${it.expiresAt}" }

private fun decodeEffects(raw: String): List<ActiveEffect> =
    raw.split(',')
        .filter { it.isNotBlank() }
        .mapNotNull { entry ->
            val kind = enumOrNull<EffectKind>(entry.substringBefore(':')) ?: return@mapNotNull null
            val expiry = entry.substringAfter(':', "").toLongOrNull() ?: return@mapNotNull null
            ActiveEffect(kind, expiry)
        }

private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
    enumOrNull<T>(name) ?: fallback

private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
    name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
