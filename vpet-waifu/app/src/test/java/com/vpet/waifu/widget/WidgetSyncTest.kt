package com.vpet.waifu.widget

import com.vpet.waifu.TestPet
import com.vpet.waifu.domain.Occupations
import com.vpet.waifu.domain.PetSimulation
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
 * in exactly one widget redraw — and nothing else does.
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
        WidgetSync(
            repository = pet.repository,
            refresher = { refreshes.incrementAndGet() },
            simulation = PetSimulation(),
            clock = pet.clock,
        ).start(scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        pet.close()
    }

    private fun key(snapshot: com.vpet.waifu.domain.PetSnapshot) =
        widgetKey(snapshot, pet.clock.nowMillis)

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
    fun `sending her to work redraws the widget`() = runBlocking {
        waitUntil { refreshes.get() >= 1 }
        val before = refreshes.get()

        pet.repository.startOccupation(Occupations.WORK.first())

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

        assertNotEquals(key(awake), key(asleep))
    }

    @Test
    fun `the key is what the widget draws and nothing else`() = runBlocking {
        // The widget is the character alone now. A shift paying a coin a
        // minute, and a few points of decay, produce a picture that is pixel
        // for pixel the same — and every redraw marshals ten PNG frames to the
        // launcher, so wanting one would be pure battery cost.
        pet.repository.startOccupation(Occupations.WORK.first())
        val atStart = key(pet.repository.peek())

        pet.clock.advanceMinutes(10)
        val tenMinutesIn = key(pet.repository.peek())

        assertEquals(atStart, tenMinutesIn)
    }

    @Test
    fun `the key still notices her getting hungry`() = runBlocking {
        val fed = key(pet.repository.feed())
        // Long enough to fall past the hungry threshold — the coarsening must
        // not turn into blindness.
        pet.clock.advanceMinutes(12 * 60)
        val later = key(pet.repository.peek())

        assertNotEquals(fed, later)
    }

    @Test
    fun `the key notices a shift ending on its own`() = runBlocking {
        val cafe = Occupations.WORK.first()
        val working = key(pet.repository.startOccupation(cafe))

        pet.clock.advanceMinutes(cafe.durationMinutes + 1L)
        val done = key(pet.repository.peek())

        assertNotEquals("she clocked off and the widget never noticed", working, done)
    }
}
