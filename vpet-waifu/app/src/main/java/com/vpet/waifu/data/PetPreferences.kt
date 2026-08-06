package com.vpet.waifu.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vpet_prefs")

/** Everything the player has chosen, as one value. */
data class PetSettings(
    val bubbleEnabled: Boolean = false,
    /** Blank until she is named; the UI falls back to the app's own name. */
    val petName: String = "",
    /**
     * Which character is drawn: "" for the app's own vector rig, or the id of
     * a sprite pack installed under assets/pets/.
     */
    val petSkin: String = "",
    val soundEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    /** The last instant the player actually looked at the app. */
    val lastSeenAt: Long = 0L,
    /**
     * The shop shelf the player was last looking at.
     *
     * A preference rather than screen state: the shop is now a set of
     * categories, and someone who is saving for a theme opens it on the themes
     * six times in a row. Remembering it for the length of one process would
     * miss exactly that — the interesting case is the *next* session.
     */
    val shopCategory: String = "",
    /** …and how that shelf is sorted. */
    val shopSort: String = "",
)

/**
 * Player-facing settings.
 *
 * Separate from the save file on purpose: these are about the *player's* phone
 * — whether it may buzz, whether the bubble is on screen — rather than about
 * the pet, and none of them should ever be part of a game rule.
 */
@Singleton
class PetPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val settings: Flow<PetSettings> = context.dataStore.data.map {
        PetSettings(
            bubbleEnabled = it[BUBBLE_ENABLED] ?: false,
            petName = (it[PET_NAME] ?: "").take(MAX_NAME_LENGTH),
            petSkin = it[PET_SKIN] ?: "",
            soundEnabled = it[SOUND] ?: true,
            musicEnabled = it[MUSIC] ?: true,
            hapticsEnabled = it[HAPTICS] ?: true,
            notificationsEnabled = it[NOTIFICATIONS] ?: true,
            lastSeenAt = it[LAST_SEEN_AT] ?: 0L,
            shopCategory = it[SHOP_CATEGORY] ?: "",
            shopSort = it[SHOP_SORT] ?: "",
        )
    }

    val bubbleEnabled: Flow<Boolean> = settings.map { it.bubbleEnabled }

    suspend fun setBubbleEnabled(enabled: Boolean) {
        context.dataStore.edit { it[BUBBLE_ENABLED] = enabled }
    }

    /** Trimmed and capped: this string ends up in notifications and a widget. */
    suspend fun setPetName(name: String) {
        context.dataStore.edit { it[PET_NAME] = name.trim().take(MAX_NAME_LENGTH) }
    }

    /** Which character is drawn: "" for the vector rig, or a sprite pack id. */
    suspend fun setPetSkin(id: String) {
        context.dataStore.edit { it[PET_SKIN] = id }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SOUND] = enabled }
    }

    suspend fun setMusicEnabled(enabled: Boolean) {
        context.dataStore.edit { it[MUSIC] = enabled }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[HAPTICS] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[NOTIFICATIONS] = enabled }
    }

    /** Stamped when the app goes to the background and when the recap is read. */
    suspend fun setLastSeenAt(millis: Long) {
        context.dataStore.edit { it[LAST_SEEN_AT] = millis }
    }

    /** Which shelf the shop opens on, and how it is sorted. */
    suspend fun setShopCategory(id: String) {
        context.dataStore.edit { it[SHOP_CATEGORY] = id }
    }

    suspend fun setShopSort(id: String) {
        context.dataStore.edit { it[SHOP_SORT] = id }
    }

    companion object {
        const val MAX_NAME_LENGTH = 16

        private val BUBBLE_ENABLED = booleanPreferencesKey("bubble_enabled")
        private val PET_NAME = stringPreferencesKey("pet_name")
        private val PET_SKIN = stringPreferencesKey("pet_skin")
        private val SOUND = booleanPreferencesKey("sound_enabled")
        private val MUSIC = booleanPreferencesKey("music_enabled")
        private val HAPTICS = booleanPreferencesKey("haptics_enabled")
        private val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        private val LAST_SEEN_AT = longPreferencesKey("last_seen_at")
        private val SHOP_CATEGORY = stringPreferencesKey("shop_category")
        private val SHOP_SORT = stringPreferencesKey("shop_sort")
    }
}
