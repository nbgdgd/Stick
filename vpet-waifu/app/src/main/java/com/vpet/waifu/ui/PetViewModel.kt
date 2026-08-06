package com.vpet.waifu.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpet.waifu.data.PetPreferences
import com.vpet.waifu.data.PetRepository
import com.vpet.waifu.data.SaveAutoBackup
import com.vpet.waifu.data.PetSettings
import com.vpet.waifu.feedback.Cue
import com.vpet.waifu.feedback.MusicTrack
import com.vpet.waifu.feedback.PetMusic
import com.vpet.waifu.feedback.PetSounds
import com.vpet.waifu.domain.Chore
import com.vpet.waifu.domain.Focus
import com.vpet.waifu.domain.MiniGame
import com.vpet.waifu.domain.Occupation
import com.vpet.waifu.domain.PetSimulation
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.PetTuning
import com.vpet.waifu.domain.ShopItem
import com.vpet.waifu.domain.Upgrade
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

data class PetUiState(
    val snapshot: PetSnapshot? = null,
    val settings: PetSettings = PetSettings(),
)

/**
 * A threshold the pet just crossed, held until the screen has shown it.
 *
 * Levels are derived from EXP rather than stored, so nothing in the app ever
 * knew a level-up had happened: the ring silently reset and the bar drained
 * from full back to empty, which read as a punishment. This is the event that
 * was missing.
 */
data class Milestone(val kind: Kind, val value: Int) {
    enum class Kind { LEVEL, BOND }
}

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
    private val autoBackup: SaveAutoBackup,
    private val music: PetMusic,
    val simulation: PetSimulation,
    val tuning: PetTuning,
) : ViewModel() {

    val uiState: StateFlow<PetUiState> =
        combine(repository.snapshot, preferences.settings) { snapshot, settings ->
            PetUiState(snapshot, settings)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PetUiState())

    private val _milestone = MutableStateFlow<Milestone?>(null)

    /** The level or bond level she just reached, until [clearMilestone]. */
    val milestone: StateFlow<Milestone?> = _milestone

    /** Seeded on the first snapshot so opening the app is never a level-up. */
    private var lastLevel: Int? = null
    private var lastBondLevel: Int? = null

    init {
        viewModelScope.launch {
            repository.snapshot.collect { snapshot ->
                val level = snapshot.level
                val bond = snapshot.bondLevel
                if (lastLevel != null && level > lastLevel!!) {
                    _milestone.value = Milestone(Milestone.Kind.LEVEL, level)
                    sounds.play(Cue.FANFARE)
                } else if (lastBondLevel != null && bond > lastBondLevel!!) {
                    _milestone.value = Milestone(Milestone.Kind.BOND, bond)
                    sounds.play(Cue.FANFARE)
                }
                lastLevel = level
                lastBondLevel = bond
            }
        }
    }

    /** The celebration has been seen. */
    fun clearMilestone() {
        _milestone.value = null
    }

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
    fun categoryTap() = act(Cue.POP) { repository.pet() }

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

    /** The day's odd jobs: three quests, the chores, and what turns up. */
    fun claimQuest(index: Int) = act(Cue.FANFARE) { repository.claimQuest(index) }

    fun doChore(chore: Chore) = act(Cue.COIN) { repository.doChore(chore) }

    fun claimFind() = act(Cue.COIN) { repository.claimFind() }

    fun stake(amount: Int) = act(Cue.TAP) { repository.stake(amount) }

    /** The one purchase worth a fanfare: it is kept. */
    fun buyUpgrade(upgrade: Upgrade) = act(Cue.FANFARE) { repository.buyUpgrade(upgrade) }

    fun wear(upgradeId: String) = act(Cue.HAPPY) { repository.wear(upgradeId) }

    fun applyTheme(upgradeId: String) = act(Cue.HAPPY) { repository.applyTheme(upgradeId) }

    /** Called when the app comes forward: pays the streak and the comeback. */
    fun claimDaily() = act { repository.claimDaily() }

    fun acknowledgeDaily() = act(Cue.COIN) { repository.acknowledgeDaily() }

    /**
     * Writes the whole save to a file the player owns.
     *
     * The only defence a RuStore install has: there is no Google backup
     * transport on a phone without Play services, so without this a new phone
     * means a pet raised for a month is gone.
     */
    fun exportSave(out: OutputStream, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repository.exportSave(out).isSuccess
            sounds.play(if (ok) Cue.HAPPY else Cue.DENIED)
            onResult(ok)
        }
    }

    fun importSave(input: InputStream, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repository.importSave(input).isSuccess
            sounds.play(if (ok) Cue.FANFARE else Cue.DENIED)
            onResult(ok)
        }
    }

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

    /** Committing to a path is the biggest single decision in the game. */
    fun chooseFocus(focus: Focus) = act(Cue.FANFARE) { repository.chooseFocus(focus) }

    fun acknowledgeStory() = act(Cue.FANFARE) { repository.acknowledgeStory() }

    fun setBubbleEnabled(enabled: Boolean) = act(Cue.TAP) { preferences.setBubbleEnabled(enabled) }

    /**
     * Turning sound back on has to be audible, so the cue is played after the
     * preference lands rather than before it — otherwise the one switch whose
     * effect you cannot see also gives no sign it worked.
     */
    fun setSoundEnabled(enabled: Boolean) = viewModelScope.launch {
        preferences.setSoundEnabled(enabled)
        if (enabled) sounds.play(Cue.HAPPY)
    }.let { }

    fun setMusicEnabled(enabled: Boolean) = act(Cue.TAP) { preferences.setMusicEnabled(enabled) }

    /** The screen that owns the moment decides what plays over it. */
    fun setMusicScene(track: MusicTrack?) = music.setScene(track)

    fun setHapticsEnabled(enabled: Boolean) = act(Cue.TAP) { preferences.setHapticsEnabled(enabled) }

    fun setNotificationsEnabled(enabled: Boolean) = act(Cue.TAP) { preferences.setNotificationsEnabled(enabled) }

    fun setPetName(name: String) = act(Cue.HAPPY) { preferences.setPetName(name) }

    /** Swapping the character is a big enough change to be worth a fanfare. */
    fun setPetSkin(id: String) = act(Cue.FANFARE) { preferences.setPetSkin(id) }

    /**
     * Writes the off-app copy of the save.
     *
     * Called when the app goes to the background, which is both the moment the
     * state is settled and the last moment before anything can happen to the
     * install. Silent by design: a toast every time you press home would be
     * noise, and the only thing worth reporting is a restore.
     */
    fun backUpSave() {
        val out = autoBackup.openForWrite() ?: return
        viewModelScope.launch { repository.exportSave(out) }
    }

    /** True when the game is untouched and a backup is sitting there. */
    fun offersRestore(snapshot: PetSnapshot?): Boolean =
        snapshot != null && snapshot.isFresh && autoBackup.exists()

    /** Reads the off-app copy back in. */
    fun restoreBackup(onResult: (Boolean) -> Unit) {
        val input = autoBackup.openForRead()
        if (input == null) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val ok = repository.importSave(input).isSuccess
            sounds.play(if (ok) Cue.FANFARE else Cue.DENIED)
            onResult(ok)
        }
    }

    /** The player has caught up — the next recap starts from now. */
    fun markSeen() = act(Cue.TAP) { preferences.setLastSeenAt(System.currentTimeMillis()) }

    /** A press that the rules refuse — the answer to "why won't this work". */
    fun refused() = sounds.play(Cue.DENIED)

    /** A target allowed to expire in the catch game. */
    fun targetMissed() = sounds.play(Cue.DENIED)

    /** A new personal best in the arcade deserves the fanfare. */
    fun recordSet() = sounds.play(Cue.FANFARE)

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
