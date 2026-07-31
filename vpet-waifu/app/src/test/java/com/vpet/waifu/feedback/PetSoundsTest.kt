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

    /**
     * The tap sample must actually be a tap, and the coin a coin.
     *
     * These two shipped wired to each other's files: the import matched them by
     * name, and the names were the wrong way round, so every button chimed like
     * a till and every coin landing went *tok*. Nothing in the code was wrong,
     * which is why nothing caught it — so the check has to be on the audio.
     *
     * A coin is a bright metallic ring: lots of high-frequency content, so lots
     * of zero crossings per second. A tap is a low dry click with a fraction of
     * that. The gap between them is enormous (about four to one), so this
     * cannot go off by accident, and it fails immediately if the two files are
     * ever swapped again.
     */
    @Test
    fun `the tap sample is duller than the coin sample`() {
        val tap = brightness(R.raw.sfx_tap)
        val coin = brightness(R.raw.sfx_coin)

        assertTrue("tap=$tap coin=$coin — the samples are swapped", coin > tap * 2)
    }

    /**
     * The three touches sound like three different things.
     *
     * The category pop *rises* in pitch, her boop *falls*, and neither may be
     * the other: pitch direction is the one property a listener can name after
     * a single hearing, so it is the one the test pins. Measured as
     * zero-crossing rate of each half of the sample.
     */
    @Test
    fun `the category pop rises while her boop falls`() {
        val (popEarly, popLate) = halves(R.raw.sfx_pop)
        val (petEarly, petLate) = halves(R.raw.sfx_pet)

        assertTrue("the pop must rise: $popEarly -> $popLate", popLate > popEarly)
        assertTrue("her boop must fall: $petEarly -> $petLate", petLate < petEarly)
    }

    @Test
    fun `the category pop is a blip, not a jingle`() {
        // It fires on every tab switch; anything long enough to overlap itself
        // turns navigation into a bell choir.
        assertTrue(seconds(R.raw.sfx_pop) <= 0.25f)
    }

    /** Zero-crossing rate of the first and second halves of a sample. */
    private fun halves(resId: Int): Pair<Float, Float> {
        val samples = pcm(resId)
        val half = samples.size / 2
        fun zcr(from: Int, until: Int): Float {
            var crossings = 0
            for (i in (from + 1) until until) {
                if ((samples[i - 1] < 0) != (samples[i] < 0)) crossings++
            }
            return crossings / ((until - from) / 44_100f)
        }
        return zcr(0, half) to zcr(half, samples.size)
    }

    @Test
    fun `a tap is short enough to survive being mashed`() {
        // The clicker fires this as fast as a finger moves, and SoundPool only
        // gives four streams. A sample longer than about half a second means
        // rapid taps cut each other off mid-ring.
        assertTrue(seconds(R.raw.sfx_tap) <= 0.55f)
    }

    /** Zero crossings per second of a 16-bit PCM raw resource. */
    private fun brightness(resId: Int): Float {
        val samples = pcm(resId)
        if (samples.isEmpty()) return 0f
        var crossings = 0
        for (i in 1 until samples.size) {
            if ((samples[i - 1] < 0) != (samples[i] < 0)) crossings++
        }
        return crossings / seconds(resId)
    }

    private fun seconds(resId: Int): Float = pcm(resId).size / 44_100f

    private fun pcm(resId: Int): ShortArray {
        val bytes = context.resources.openRawResource(resId).use { it.readBytes() }
        // Walk the RIFF chunks to the data payload rather than assuming a
        // 44-byte header — ffmpeg writes a LIST/INFO chunk before it.
        var i = 12
        while (i + 8 <= bytes.size) {
            val id = String(bytes, i, 4, Charsets.US_ASCII)
            val size = (bytes[i + 4].toInt() and 0xFF) or
                ((bytes[i + 5].toInt() and 0xFF) shl 8) or
                ((bytes[i + 6].toInt() and 0xFF) shl 16) or
                ((bytes[i + 7].toInt() and 0xFF) shl 24)
            if (id == "data") {
                val end = minOf(bytes.size, i + 8 + size)
                val out = ShortArray((end - i - 8) / 2)
                for (s in out.indices) {
                    val b = i + 8 + s * 2
                    out[s] = ((bytes[b].toInt() and 0xFF) or (bytes[b + 1].toInt() shl 8)).toShort()
                }
                return out
            }
            i += 8 + size + (size and 1)
        }
        return ShortArray(0)
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
