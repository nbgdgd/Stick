package com.vpet.waifu.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.vpet.waifu.domain.PetActivity
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.PetStats

/**
 * The single row that is the save file.
 *
 * There is exactly one pet, so the table is pinned to [SINGLETON_ID]; that turns
 * every write into an idempotent upsert and makes "load the game" a single
 * indexed lookup.
 */
@Entity(tableName = "pet_state")
data class PetStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val hunger: Float,
    val energy: Float,
    val mood: Float,
    /** Stored as the enum name so a new activity in a later phase is additive. */
    val activity: String,
    val lastTickAt: Long,
    val lastInteractionAt: Long,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}

fun PetStateEntity.toSnapshot(): PetSnapshot = PetSnapshot(
    // Coerced rather than validated: a row written by an older build with a
    // different balance must never crash the app on load.
    stats = PetStats.coerced(hunger, energy, mood),
    activity = runCatching { PetActivity.valueOf(activity) }.getOrDefault(PetActivity.AWAKE),
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
)
