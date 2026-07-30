package com.vpet.waifu.widget

import com.vpet.waifu.TestPet
import com.vpet.waifu.waitUntil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * Regression tests for "I pressed sleep in the app and the widget still shows
 * her awake".
 *
 * The fix moved the trigger off the callers and onto the data, so these assert
 * the behaviour that matters: any write to the save file, from anywhere, ends
 * in exactly one widget redraw.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetSyncTest {

    private lateinit var pet: TestPet
    private lateinit var scope: CoroutineScope
    private val refreshes = AtomicInteger(0)

    @Before
    fun setUp() {
        pet = TestPet()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        WidgetSync(pet.repository) { refreshes.incrementAndGet() }.start(scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        pet.close()
    }

    @Test
    fun `putting her to sleep redraws the widget`() = runBlocking {
        waitUntil { refreshes.get() >= 1 }
        val before = refreshes.get()

        pet.repository.startSleep()

        waitUntil { refreshes.get() > before }
        assertTrue(pet.repository.peek().isSleeping)
    }

    @Test
    fun `waking her redraws the widget again`() = runBlocking {
        // Wait for the collector to be attached first: acting before it starts
        // collapses the initial emission and the change into one.
        waitUntil { refreshes.get() >= 1 }
        pet.repository.startSleep()
        waitUntil { refreshes.get() >= 2 }
        val afterSleep = refreshes.get()

        pet.repository.wake()

        waitUntil { refreshes.get() > afterSleep }
    }

    @Test
    fun `feeding her redraws the widget`() = runBlocking {
        waitUntil { refreshes.get() >= 1 }
        val before = refreshes.get()

        pet.repository.feed()

        waitUntil { refreshes.get() > before }
    }

    @Test
    fun `a tick that changes nothing does not redraw`() = runBlocking {
        waitUntil { refreshes.get() >= 1 }
        val before = refreshes.get()

        // No time has passed, so the tick is a no-op and must not cost a redraw.
        repeat(5) { pet.repository.tick() }

        kotlinx.coroutines.delay(300)
        assertEquals(before, refreshes.get())
    }

    // --- the key itself ------------------------------------------------------

    @Test
    fun `the key distinguishes sleeping from awake`() = runBlocking {
        val awake = pet.repository.peek()
        val asleep = pet.repository.startSleep()

        assertNotEquals(widgetKey(awake), widgetKey(asleep))
    }

    @Test
    fun `the key ignores drift too small to show`() = runBlocking {
        val before = pet.repository.peek()
        // A few seconds of decay moves the floats but not the rounded numbers
        // the widget prints, so it must not trigger a redraw.
        pet.clock.nowMillis += 3_000
        val after = pet.repository.peek()

        assertEquals(widgetKey(before), widgetKey(after))
    }

    @Test
    fun `the key follows the wallet and the shift`() = runBlocking {
        val idle = pet.repository.peek()
        val working = pet.repository.startOccupation(com.vpet.waifu.domain.Occupations.WORK.first())

        assertNotEquals(widgetKey(idle), widgetKey(working))
    }

    @Test
    fun `a shift does not redraw the widget once a minute`() = runBlocking {
        // Wages land every simulated minute now. Every frame of her animation
        // is marshalled to the launcher on a redraw, so paying attention to
        // single coins would mean a full transaction a minute for two hours.
        pet.repository.startOccupation(com.vpet.waifu.domain.Occupations.WORK.first())

        val keys = mutableSetOf<String>()
        repeat(30) {
            pet.clock.nowMillis += 60_000
            keys += widgetKey(pet.repository.peek())
        }

        assertTrue("30 minutes on the clock wanted ${keys.size} redraws", keys.size <= 12)
    }

    @Test
    fun `the key still notices her getting hungry`() = runBlocking {
        val fed = pet.repository.feed()
        // Long enough for the bars to visibly move, but well short of a state
        // change — the coarsening must not turn into blindness.
        pet.clock.nowMillis += 60 * 60_000
        val later = pet.repository.peek()

        assertNotEquals(widgetKey(fed), widgetKey(later))
    }
}
