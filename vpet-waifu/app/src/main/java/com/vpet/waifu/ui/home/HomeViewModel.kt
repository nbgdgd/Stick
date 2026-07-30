package com.vpet.waifu.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpet.waifu.data.PetPreferences
import com.vpet.waifu.data.PetRepository
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.PetTuning
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val snapshot: PetSnapshot? = null,
    val bubbleEnabled: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: PetRepository,
    private val preferences: PetPreferences,
    val tuning: PetTuning,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> =
        combine(repository.snapshot, preferences.bubbleEnabled) { snapshot, bubbleEnabled ->
            HomeUiState(snapshot, bubbleEnabled)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // Reopening the app is itself a tick: pay off everything owed since the
        // last time anything ran, then keep the screen honest once a minute.
        viewModelScope.launch {
            while (isActive) {
                repository.tick()
                delay(TICK_INTERVAL_MILLIS)
            }
        }
    }

    fun feed() = act { repository.feed() }

    fun pet() = act { repository.pet() }

    fun toggleSleep() = act {
        val current = repository.snapshotNow()
        if (current.isSleeping) repository.wake() else repository.startSleep()
    }

    fun setBubbleEnabled(enabled: Boolean) = act { preferences.setBubbleEnabled(enabled) }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val TICK_INTERVAL_MILLIS = 60_000L
    }
}
