package com.vpet.waifu.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import androidx.annotation.RawRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.vpet.waifu.R
import com.vpet.waifu.data.PetPreferences
import com.vpet.waifu.domain.PetState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** One looping background track per situation. */
enum class MusicTrack(@RawRes val resId: Int) {
    HOME(R.raw.music_home),
    NIGHT(R.raw.music_night),
    WORK(R.raw.music_work),
    STUDY(R.raw.music_study),
    GAME(R.raw.music_game),
    SHOP(R.raw.music_shop),
    SAD(R.raw.music_sad),
    ;

    companion object {
        /**
         * The room's music for what she is doing.
         *
         * Screens with their own atmosphere (the shop, the arcade) override
         * this; everything else follows her state, so putting her to bed is
         * also what dims the soundtrack.
         */
        fun forState(state: PetState): MusicTrack = when (state) {
            PetState.SLEEPING -> NIGHT
            PetState.WORKING -> WORK
            PetState.STUDYING -> STUDY
            PetState.PLAYING -> GAME
            PetState.HUNGRY, PetState.TIRED -> SAD
            else -> HOME
        }
    }
}

/**
 * The soundtrack.
 *
 * One track at a time, chosen by whoever owns the screen, faded rather than
 * cut. Three gates sit between a request and the speaker: the app must be in
 * the foreground (a home-screen widget must never start music), the player must
 * not have switched music off, and nothing else on the phone may hold audio
 * focus — her lofi ducking out of a podcast uninvited is exactly the kind of
 * thing that gets an app uninstalled.
 */
@Singleton
class PetMusic @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: PetPreferences,
) {

    private val desired = MutableStateFlow<MusicTrack?>(null)
    private val foreground = MutableStateFlow(false)
    private val focusLost = MutableStateFlow(false)

    private var player: MediaPlayer? = null
    private var playingRes: Int = 0

    private val audioManager = context.getSystemService(AudioManager::class.java)

    private val focusRequest: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setOnAudioFocusChangeListener { change ->
                focusLost.value = when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> false
                    else -> true
                }
            }
            .build()

    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.Main) {
            // Activities only: the overlay bubble and the widget run in this
            // process too, and neither of them is a place music should play.
            ProcessLifecycleOwner.get().lifecycle.addObserver(
                LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> foreground.value = true
                        Lifecycle.Event.ON_STOP -> foreground.value = false
                        else -> Unit
                    }
                },
            )
        }

        scope.launch {
            combine(
                desired,
                foreground,
                focusLost,
                preferences.settings.map { it.musicEnabled },
            ) { track, inFront, lost, enabled ->
                if (inFront && enabled && !lost) track else null
            }
                .distinctUntilChanged()
                .collect { switchTo(it) }
        }
    }

    /** What should be playing right now; null is silence. */
    fun setScene(track: MusicTrack?) {
        desired.value = track
    }

    private suspend fun switchTo(track: MusicTrack?) = withContext(Dispatchers.Main) {
        if (track?.resId == playingRes.takeIf { it != 0 }) return@withContext

        // Fade the old track out rather than cutting it — a hard stop reads as
        // a bug, a fade reads as a scene change.
        player?.let { old ->
            runCatching {
                for (step in FADE_STEPS - 1 downTo 0) {
                    val v = BASE_VOLUME * step / FADE_STEPS
                    old.setVolume(v, v)
                    delay(FADE_MILLIS / FADE_STEPS)
                }
                old.stop()
            }
            runCatching { old.release() }
            player = null
            playingRes = 0
        }

        if (track == null) {
            runCatching { audioManager?.abandonAudioFocusRequest(focusRequest) }
            return@withContext
        }

        val granted = runCatching {
            audioManager?.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }.getOrDefault(false)
        if (!granted) return@withContext

        runCatching {
            MediaPlayer.create(context, track.resId)?.also { fresh ->
                fresh.isLooping = true
                fresh.setVolume(0f, 0f)
                fresh.start()
                player = fresh
                playingRes = track.resId
                for (step in 1..FADE_STEPS) {
                    val v = BASE_VOLUME * step / FADE_STEPS
                    fresh.setVolume(v, v)
                    delay(FADE_MILLIS / FADE_STEPS)
                }
            }
        }.onFailure { Log.w(TAG, "could not start ${track.name}", it) }
    }

    private companion object {
        const val TAG = "PetMusic"

        /** Background, not foreground: it sits under the cues and her room. */
        const val BASE_VOLUME = 0.55f
        const val FADE_MILLIS = 450L
        const val FADE_STEPS = 9
    }
}
