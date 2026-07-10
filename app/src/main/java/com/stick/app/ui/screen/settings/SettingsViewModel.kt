package com.stick.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stick.app.data.repository.PreviewQuality
import com.stick.app.data.repository.SettingsRepository
import com.stick.app.data.repository.UserSettings
import com.stick.app.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<UserSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { repository.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { repository.setDynamicColor(enabled) }
    fun setGridLayout(grid: Boolean) = viewModelScope.launch { repository.setGridLayout(grid) }
    fun setCardScale(scale: Float) = viewModelScope.launch { repository.setCardScale(scale) }
    fun setPreviewQuality(q: PreviewQuality) = viewModelScope.launch { repository.setPreviewQuality(q) }
    fun setLanguage(tag: String) = viewModelScope.launch { repository.setLanguage(tag) }
}
