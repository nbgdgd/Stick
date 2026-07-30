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
    val outcomeOccupationId: String? = null,
    val outcomeKind: String? = null,
    val outcomeMoney: Int = 0,
    val outcomeExp: Int = 0,
    val outcomeQuality: String? = null,
    val outcomeCancelled: Boolean = false,
    val outcomeAt: Long = 0,
    val emote: String? = null,
    val emoteUntil: Long = 0,
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
)

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
