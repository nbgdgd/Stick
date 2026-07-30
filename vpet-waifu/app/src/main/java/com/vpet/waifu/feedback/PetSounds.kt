package com.vpet.waifu.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.vpet.waifu.R
import com.vpet.waifu.data.PetPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Every noise the app can make. */
enum class Cue(val resId: Int, val volume: Float, val buzzMillis: Long) {
    TAP(R.raw.sfx_tap, volume = 0.5f, buzzMillis = 8),
    COIN(R.raw.sfx_coin, volume = 0.7f, buzzMillis = 14),
    EAT(R.raw.sfx_eat, volume = 0.6f, buzzMillis = 10),
    HAPPY(R.raw.sfx_happy, volume = 0.7f, buzzMillis = 18),
    /** Buying something permanent, or gaining a level. */
    FANFARE(R.raw.sfx_fanfare, volume = 0.8f, buzzMillis = 30),
    DENIED(R.raw.sfx_denied, volume = 0.5f, buzzMillis = 22),
}

/**
 * Short sounds and a small buzz.
 *
 * A `SoundPool` rather than `MediaPlayer`: these are all under a second, they
 * overlap constantly during the mini-game, and the pool decodes them once up
 * front so a tap never waits on IO.
 *
 * Loading is asynchronous and deliberately unguarded — a cue asked for before
 * its sample has finished decoding is simply dropped. Blocking a tap to wait
 * for a click sound would be a worse bug than a missing click.
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
            if (status == 0) synchronized(ready) { ready += sampleId }
        }
        Cue.entries.forEach { cue ->
            runCatching { pool.load(context, cue.resId, 1) }
                .onSuccess { sampleIds[cue] = it }
        }
        scope.launch {
            preferences.settings.collect {
                soundOn.value = it.soundEnabled
                hapticsOn.value = it.hapticsEnabled
            }
        }
    }

    fun play(cue: Cue) {
        if (soundOn.value) {
            val sample = sampleIds[cue]
            val loaded = sample != null && synchronized(ready) { sample in ready }
            if (loaded) pool.play(sample, cue.volume, cue.volume, 1, 0, 1f)
        }
        if (hapticsOn.value) buzz(cue.buzzMillis)
    }

    private fun buzz(millis: Long) {
        if (millis <= 0) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching {
            v.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
