package com.vpet.waifu.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.vpet.waifu.R
import com.vpet.waifu.data.PetPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every noise the app can make, with the buzz that goes with it.
 *
 * The haptic patterns are `(off, on, off, on…)` millisecond timings plus an
 * amplitude per segment. The first cut of this used single pulses of 8–30 ms at
 * default amplitude, which is below what a phone's motor can physically render —
 * the code ran, the motor never moved, and the feature read as absent. Every
 * pulse is now ≥25 ms and pinned to full amplitude, with the character of the
 * cue carried by the rhythm rather than by strength.
 */
enum class Cue(
    val resId: Int,
    val volume: Float,
    val buzzTimings: LongArray,
    val buzzAmplitudes: IntArray,
) {
    /**
     * A dry click. Tapping her, tapping a category, tapping anything.
     *
     * This and [COIN] were wired to each other's samples: the import matched
     * them up by filename, and the two files had been named the other way
     * round, so every tap chimed like a cash register and every coin landing
     * went *tok*. The samples were swapped on disk rather than the resource ids
     * here, so that `sfx_tap` is the file that actually sounds like a tap.
     */
    TAP(R.raw.sfx_tap, 0.85f, longArrayOf(0, 25), intArrayOf(0, 255)),
    /** A coin landing in the wallet. The bright one. */
    COIN(R.raw.sfx_coin, 1f, longArrayOf(0, 30, 50, 35), intArrayOf(0, 180, 0, 255)),
    EAT(R.raw.sfx_eat, 0.9f, longArrayOf(0, 35), intArrayOf(0, 200)),
    HAPPY(R.raw.sfx_happy, 1f, longArrayOf(0, 30, 60, 45), intArrayOf(0, 200, 0, 255)),
    /** Buying something permanent, or gaining a level. */
    FANFARE(R.raw.sfx_fanfare, 1f, longArrayOf(0, 40, 70, 40, 70, 70), intArrayOf(0, 160, 0, 210, 0, 255)),
    DENIED(R.raw.sfx_denied, 0.8f, longArrayOf(0, 60, 80, 60), intArrayOf(0, 255, 0, 255)),
}

/**
 * Short sounds and a small buzz.
 *
 * A `SoundPool` rather than `MediaPlayer`: these are all under a second, they
 * overlap constantly during the mini-game, and the pool decodes them once up
 * front so a tap never waits on IO.
 *
 * Loading is asynchronous, so the very first cue of a cold start can arrive
 * before its sample has decoded. Rather than dropping it — which made the first
 * button of every session silently mute — the cue is parked and fired from the
 * load-complete callback. Blocking the tap to wait would be the worse bug.
 */
@Singleton
class PetSounds @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: PetPreferences,
) {

    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val sampleIds = mutableMapOf<Cue, Int>()
    private val ready = mutableSetOf<Int>()
    private var pendingCue: Cue? = null

    private val soundOn = MutableStateFlow(true)
    private val hapticsOn = MutableStateFlow(true)

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }

    fun start(scope: CoroutineScope) {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                val toReplay: Cue?
                synchronized(ready) {
                    ready += sampleId
                    toReplay = pendingCue?.takeIf { sampleIds[it] == sampleId }
                    if (toReplay != null) pendingCue = null
                }
                // Sound only: the buzz already fired when the cue was asked
                // for, and a second one here would double-tap the first press
                // of every session.
                toReplay?.let(::playSample)
            } else {
                // A sample that fails to decode turns into permanent silence
                // for that cue; that must be visible in a bug report.
                Log.w(TAG, "sound sample $sampleId failed to load: status $status")
            }
        }
        Cue.entries.forEach { cue ->
            runCatching { pool.load(context, cue.resId, 1) }
                .onSuccess { sampleIds[cue] = it }
                .onFailure { Log.w(TAG, "could not load ${cue.name}", it) }
        }
        scope.launch {
            preferences.settings.collect {
                soundOn.value = it.soundEnabled
                hapticsOn.value = it.hapticsEnabled
            }
        }
    }

    fun play(cue: Cue) {
        if (soundOn.value) playSample(cue)
        if (hapticsOn.value) buzz(cue)
    }

    private fun playSample(cue: Cue) {
        val sample = sampleIds[cue]
        val loaded: Boolean = synchronized(ready) {
            val ok = sample != null && sample in ready
            if (!ok) pendingCue = cue
            ok
        }
        if (loaded && sample != null) {
            pool.play(sample, cue.volume, cue.volume, 1, 0, 1f)
        }
    }

    private fun buzz(cue: Cue) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching {
            v.vibrate(VibrationEffect.createWaveform(cue.buzzTimings, cue.buzzAmplitudes, -1))
        }.onFailure { Log.w(TAG, "vibration failed", it) }
    }

    private companion object {
        const val TAG = "PetSounds"
    }
}
