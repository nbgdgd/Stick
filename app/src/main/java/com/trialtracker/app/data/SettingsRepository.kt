package com.trialtracker.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

data class Settings(
    val userName: String = "",
    val onboarded: Boolean = false,
    val showSystemApps: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val checkIntervalHours: Int = 24,
    val autoVerifyTrials: Boolean = true,
    val lastSyncAt: Long = 0L,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val userName = stringPreferencesKey("user_name")
        val onboarded = booleanPreferencesKey("onboarded")
        val showSystemApps = booleanPreferencesKey("show_system_apps")
        val notifications = booleanPreferencesKey("notifications_enabled")
        val interval = intPreferencesKey("check_interval_hours")
        val autoVerify = booleanPreferencesKey("auto_verify_trials")
        val lastSync = longPreferencesKey("last_sync_at")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            userName = prefs[Keys.userName].orEmpty(),
            onboarded = prefs[Keys.onboarded] ?: false,
            showSystemApps = prefs[Keys.showSystemApps] ?: false,
            notificationsEnabled = prefs[Keys.notifications] ?: true,
            checkIntervalHours = prefs[Keys.interval] ?: 24,
            autoVerifyTrials = prefs[Keys.autoVerify] ?: true,
            lastSyncAt = prefs[Keys.lastSync] ?: 0L,
        )
    }

    suspend fun setUserName(value: String) = edit { it[Keys.userName] = value.trim() }
    suspend fun setOnboarded(value: Boolean) = edit { it[Keys.onboarded] = value }
    suspend fun setShowSystemApps(value: Boolean) = edit { it[Keys.showSystemApps] = value }
    suspend fun setNotificationsEnabled(value: Boolean) = edit { it[Keys.notifications] = value }
    suspend fun setCheckIntervalHours(value: Int) = edit { it[Keys.interval] = value }
    suspend fun setAutoVerifyTrials(value: Boolean) = edit { it[Keys.autoVerify] = value }
    suspend fun setLastSyncAt(value: Long) = edit { it[Keys.lastSync] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
