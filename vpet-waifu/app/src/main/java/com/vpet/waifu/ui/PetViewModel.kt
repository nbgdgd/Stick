package com.vpet.waifu.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpet.waifu.data.PetPreferences
import com.vpet.waifu.data.PetRepository
import com.vpet.waifu.data.PetSettings
import com.vpet.waifu.feedback.Cue
import com.vpet.waifu.feedback.MusicTrack
import com.vpet.waifu.feedback.PetMusic
import com.vpet.waifu.feedback.PetSounds
import com.vpet.waifu.domain.MiniGame
import com.vpet.waifu.domain.Occupation
import com.vpet.waifu.domain.PetSimulation
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.PetTuning
import com.vpet.waifu.domain.ShopItem
import com.vpet.waifu.domain.Upgrade
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
    val settings: PetSettings = PetSettings(),
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
    private val sounds: PetSounds,
    private val music: PetMusic,
    val simulation: PetSimulation,
    val tuning: PetTuning,
) : ViewModel() {

    val uiState: StateFlow<PetUiState> =
        combine(repository.snapshot, preferences.settings) { snapshot, settings ->
            PetUiState(snapshot, settings)
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

    fun feed() = act(Cue.EAT) { repository.feed() }

    fun pet() = act(Cue.HAPPY) { repository.pet() }

    /**
     * The clicker: tapping her directly.
     *
     * Same game action as a pat, but with her own soft boop rather than the
     * interface click — this is the sound the player hears most in the app, so
     * it is the one that has to stay pleasant after two hundred of them.
     */
    fun tap() = act(Cue.PET_TAP) { repository.pet() }

    /**
     * Tapping a category — a tab, a section header, an item's tile.
     *
     * The same pat as tapping her directly. She is not on that screen, but the
     * app is one pet: an idle game wants every surface to be worth touching,
     * and the alternative — a tap that does nothing but navigate — is the one
     * interaction in the app that gives nothing back.
     */
    fun categoryTap() = act(Cue.TAP) { repository.pet() }

    /** A flying coin reaching the wallet. */
    fun coinLanded() = sounds.play(Cue.COIN)

    /** A flying star reaching the level ring. Studying's own arrival sound. */
    fun expLanded() = sounds.play(Cue.STAR)

    /** A dry interface click — tab switches and other chrome. */
    fun uiTap() = sounds.play(Cue.TAP)

    fun toggleSleep() = act(Cue.TAP) { repository.toggleSleep() }

    fun startOccupation(occupation: Occupation) = act(Cue.TAP) {
        repository.startOccupation(occupation)
    }

    fun cancelOccupation() = act(Cue.TAP) { repository.cancelOccupation() }

    fun buy(item: ShopItem) = act(Cue.COIN) { repository.buy(item) }

    /** The one purchase worth a fanfare: it is kept. */
    fun buyUpgrade(upgrade: Upgrade) = act(Cue.FANFARE) { repository.buyUpgrade(upgrade) }

    fun wear(upgradeId: String) = act(Cue.HAPPY) { repository.wear(upgradeId) }

    fun startPlaying() = act(Cue.TAP) { repository.startPlaying() }

    fun finishPlaying(score: Int, game: MiniGame) = act(if (score > 0) Cue.HAPPY else null) {
        repository.finishPlaying(score, game)
    }

    /** A point scored inside a round. */
    fun scored() = sounds.play(Cue.TAP)

    /** A note fumbled or a pad hit in the wrong order. */
    fun missed() = sounds.play(Cue.DENIED)

    fun acknowledgeOutcome() = act(Cue.COIN) { repository.acknowledgeOutcome() }

    fun acknowledgeEvent() = act(Cue.TAP) { repository.acknowledgeEvent() }

    fun setBubbleEnabled(enabled: Boolean) = act { preferences.setBubbleEnabled(enabled) }

    fun setSoundEnabled(enabled: Boolean) = act { preferences.setSoundEnabled(enabled) }

    fun setMusicEnabled(enabled: Boolean) = act { preferences.setMusicEnabled(enabled) }

    /** The screen that owns the moment decides what plays over it. */
    fun setMusicScene(track: MusicTrack?) = music.setScene(track)

    fun setHapticsEnabled(enabled: Boolean) = act { preferences.setHapticsEnabled(enabled) }

    fun setNotificationsEnabled(enabled: Boolean) = act { preferences.setNotificationsEnabled(enabled) }

    fun setPetName(name: String) = act { preferences.setPetName(name) }

    /**
     * Runs an action and, if it has one, makes its noise.
     *
     * The cue fires immediately rather than after the write lands: a button
     * that clicks a hundred milliseconds after you press it feels broken, and
     * the actions here do not fail — at worst they are refused by a rule, which
     * the screen shows by being disabled in the first place.
     *
     * The widget is refreshed by WidgetSync observing the repository, so no
     * caller has to remember to do that either.
     */
    private fun act(cue: Cue? = null, block: suspend () -> Unit) {
        cue?.let(sounds::play)
        viewModelScope.launch { block() }
    }

    private companion object {
        /**
         * The tip jar's own interval.
         *
         * Was twenty seconds, which was plenty for a world that only moves in
         * whole minutes. Loose change arrives every three, and it is settled by
         * the same `advanceTo` every other caller goes through, so this loop is
         * what actually pays it while the app is open — and the grace window in
         * [com.vpet.waifu.domain.PetSimulation.settlePassive] is what stops
         * anything slower from paying it in a lump afterwards.
         *
         * A tick with nothing owed is one indexed read and no write at all.
         */
        const val TICK_INTERVAL_MILLIS = 3_000L
    }
}
