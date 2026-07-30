package com.vpet.waifu.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
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
            soundEnabled = it[SOUND] ?: true,
            hapticsEnabled = it[HAPTICS] ?: true,
            notificationsEnabled = it[NOTIFICATIONS] ?: true,
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

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SOUND] = enabled }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[HAPTICS] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[NOTIFICATIONS] = enabled }
    }

    companion object {
        const val MAX_NAME_LENGTH = 16

        private val BUBBLE_ENABLED = booleanPreferencesKey("bubble_enabled")
        private val PET_NAME = stringPreferencesKey("pet_name")
        private val SOUND = booleanPreferencesKey("sound_enabled")
        private val HAPTICS = booleanPreferencesKey("haptics_enabled")
        private val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
    }
}
