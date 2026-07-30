package com.vpet.waifu.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vpet_prefs")

/**
 * Player-facing settings. Phase 1 only needs to remember whether the bubble
 * should be on screen — the boot receiver reads it to decide whether to bring
 * her back after a restart.
 */
@Singleton
class PetPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val bubbleEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[BUBBLE_ENABLED] ?: false }

    suspend fun setBubbleEnabled(enabled: Boolean) {
        context.dataStore.edit { it[BUBBLE_ENABLED] = enabled }
    }

    private companion object {
        val BUBBLE_ENABLED = booleanPreferencesKey("bubble_enabled")
    }
}
