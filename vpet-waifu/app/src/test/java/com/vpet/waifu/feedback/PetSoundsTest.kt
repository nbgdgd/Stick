package com.vpet.waifu.feedback

import android.content.Context
import android.media.SoundPool
import android.os.VibratorManager
import androidx.test.core.app.ApplicationProvider
import com.vpet.waifu.R
import com.vpet.waifu.data.PetPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSoundPool

/**
 * The feedback pipeline, end to end.
 *
 * This shipped "working" once already — every call site was correct and the
 * user heard nothing, because the vibration pulses were below what a motor can
 * render and the first tap of a session raced the sample decoder and was
 * silently dropped. These tests pin the pipeline itself: a cue asked for is a
 * cue delivered, before and after the load completes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PetSoundsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var sounds: PetSounds
    private lateinit var scope: CoroutineScope

    /** The pool is private to the class under test; reflection reads it out. */
    private lateinit var lastPool: SoundPool

    private val shadowPool: ShadowSoundPool
        get() = shadowOf(lastPool)

    @Before
    fun setUp() {
        // The DataStore delegate is a singleton per classloader, so a toggle
        // switched off by one test would leak into the next. Start level.
        runBlocking {
            PetPreferences(context).setSoundEnabled(true)
            PetPreferences(context).setHapticsEnabled(true)
        }
        sounds = PetSounds(context, PetPreferences(context))
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        sounds.start(scope)
        lastPool = PetSounds::class.java.getDeclaredField("pool")
            .apply { isAccessible = true }
            .get(sounds) as SoundPool
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun finishLoading() {
        Cue.entries.forEach { shadowPool.notifyResourceLoaded(it.resId, true) }
    }

    /**
     * Waits for a DataStore write to reach the collector inside [PetSounds].
     *
     * The preference lands on another dispatcher, so asserting immediately
     * after the write races it and fails on the old value.
     */
    @Suppress("UNCHECKED_CAST")
    private fun awaitSetting(field: String, expected: Boolean) {
        val flow = PetSounds::class.java.getDeclaredField(field)
            .apply { isAccessible = true }
            .get(sounds) as kotlinx.coroutines.flow.MutableStateFlow<Boolean>
        val deadline = System.currentTimeMillis() + 5_000
        while (flow.value != expected) {
            check(System.currentTimeMillis() < deadline) { "$field never became $expected" }
            Thread.sleep(5)
        }
    }

    @Test
    fun `a cue plays once its sample is loaded`() {
        finishLoading()

        sounds.play(Cue.COIN)

        assertTrue(shadowPool.wasResourcePlayed(R.raw.sfx_coin))
    }

    @Test
    fun `the first tap of a cold start is parked and fired, not dropped`() {
        // No samples loaded yet — this is the first second after process start.
        sounds.play(Cue.TAP)
        assertFalse(shadowPool.wasResourcePlayed(R.raw.sfx_tap))

        finishLoading()

        assertTrue(
            "the pre-load cue was silently dropped",
            shadowPool.wasResourcePlayed(R.raw.sfx_tap),
        )
    }

    @Test
    fun `a cue buzzes the vibrator with a perceptible pulse`() {
        finishLoading()
        val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator

        sounds.play(Cue.TAP)

        val shadow = shadowOf(vibrator)
        assertTrue("the motor never moved", shadow.milliseconds > 0)
        // The whole point of the fix: pulses under ~20ms do not physically
        // register on a phone motor.
        assertTrue("pulse too short to feel: ${shadow.milliseconds}ms", shadow.milliseconds >= 20)
    }

    @Test
    fun `switching sound off silences the pool but not the motor`() = runBlocking {
        finishLoading()
        PetPreferences(context).setSoundEnabled(false)
        awaitSetting("soundOn", false)

        sounds.play(Cue.COIN)

        assertFalse(shadowPool.wasResourcePlayed(R.raw.sfx_coin))
        val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
        assertTrue(shadowOf(vibrator).milliseconds > 0)
    }

    @Test
    fun `switching haptics off stills the motor but not the pool`() = runBlocking {
        finishLoading()
        PetPreferences(context).setHapticsEnabled(false)
        awaitSetting("hapticsOn", false)

        sounds.play(Cue.COIN)

        assertTrue(shadowPool.wasResourcePlayed(R.raw.sfx_coin))
        val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
        assertTrue(shadowOf(vibrator).milliseconds == 0L)
    }
}
