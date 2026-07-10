package com.stick.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.stick.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** UI preferences the settings screen exposes, all as one immutable snapshot. */
data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val gridLayout: Boolean = true,
    val cardScale: Float = 1f,
    val previewQuality: PreviewQuality = PreviewQuality.HIGH,
    val languageTag: String = "",   // empty = follow system
)

enum class PreviewQuality { LOW, MEDIUM, HIGH }

/**
 * Persists [UserSettings] with Jetpack DataStore. Exposed as a hot [Flow] so the
 * whole UI recomposes when a preference changes (e.g. switching to AMOLED).
 */
class SettingsRepository(private val context: Context) {

    val settings: Flow<UserSettings> = context.dataStore.data.map { p ->
        UserSettings(
            themeMode = p[Keys.THEME]?.let(::runCatchingTheme) ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.DYNAMIC_COLOR] ?: true,
            gridLayout = p[Keys.GRID_LAYOUT] ?: true,
            cardScale = p[Keys.CARD_SCALE] ?: 1f,
            previewQuality = p[Keys.PREVIEW_QUALITY]?.let(::runCatchingQuality) ?: PreviewQuality.HIGH,
            languageTag = p[Keys.LANGUAGE] ?: "",
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = enabled }
    suspend fun setGridLayout(grid: Boolean) = edit { it[Keys.GRID_LAYOUT] = grid }
    suspend fun setCardScale(scale: Float) = edit { it[Keys.CARD_SCALE] = scale }
    suspend fun setPreviewQuality(q: PreviewQuality) = edit { it[Keys.PREVIEW_QUALITY] = q.name }
    suspend fun setLanguage(tag: String) = edit { it[Keys.LANGUAGE] = tag }

    private suspend fun edit(
        block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit,
    ) {
        context.dataStore.edit(block)
    }

    private fun runCatchingTheme(name: String) = runCatching { ThemeMode.valueOf(name) }.getOrNull()
    private fun runCatchingQuality(name: String) = runCatching { PreviewQuality.valueOf(name) }.getOrNull()

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val GRID_LAYOUT = booleanPreferencesKey("grid_layout")
        val CARD_SCALE = floatPreferencesKey("card_scale")
        val PREVIEW_QUALITY = stringPreferencesKey("preview_quality")
        val LANGUAGE = stringPreferencesKey("language")
    }
}
