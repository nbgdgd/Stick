package com.vpet.waifu.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpet.waifu.data.PetPreferences
import com.vpet.waifu.data.PetRepository
import com.vpet.waifu.domain.Occupation
import com.vpet.waifu.domain.PetSimulation
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.PetTuning
import com.vpet.waifu.domain.ShopItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PetUiState(
    val snapshot: PetSnapshot? = null,
    val bubbleEnabled: Boolean = false,
)

/**
 * One view model behind every tab.
 *
 * The tabs are views onto a single pet, so sharing the model keeps the stat
 * bars, the countdown and the wallet identical everywhere without any
 * cross-screen plumbing.
 */
@HiltViewModel
class PetViewModel @Inject constructor(
    private val repository: PetRepository,
    private val preferences: PetPreferences,
    val simulation: PetSimulation,
    val tuning: PetTuning,
) : ViewModel() {

    val uiState: StateFlow<PetUiState> =
        combine(repository.snapshot, preferences.bubbleEnabled) { snapshot, bubbleEnabled ->
            PetUiState(snapshot, bubbleEnabled)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PetUiState())

    init {
        // Opening the app is itself a tick: pay off everything owed since the
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

    fun toggleSleep() = act { repository.toggleSleep() }

    fun startOccupation(occupation: Occupation) = act { repository.startOccupation(occupation) }

    fun cancelOccupation() = act { repository.cancelOccupation() }

    fun buy(item: ShopItem) = act { repository.buy(item) }

    fun startPlaying() = act { repository.startPlaying() }

    fun finishPlaying(score: Int) = act { repository.finishPlaying(score) }

    fun acknowledgeOutcome() = act { repository.acknowledgeOutcome() }

    fun setBubbleEnabled(enabled: Boolean) = act { preferences.setBubbleEnabled(enabled) }

    // The widget is refreshed by WidgetSync observing the repository, so no
    // caller has to remember to do it.
    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        /**
         * Faster than the simulation's own minute.
         *
         * The world only ever moves in whole minutes, so a slower tick would
         * not lose anything — but a wage that landed at the top of the minute
         * would sit unnoticed for up to a minute before the screen showed it,
         * which makes paying by the minute look like paying at random. A tick
         * with nothing owed is a single indexed read and no write at all.
         */
        const val TICK_INTERVAL_MILLIS = 20_000L
    }
}
