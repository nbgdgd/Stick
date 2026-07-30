package com.vpet.waifu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MINUTE = PetSimulation.MS_PER_MINUTE
private const val T0 = 1_700_000_000_000L

class PetSimulationTest {

    private val sim = PetSimulation()

    private fun snapshot(
        hunger: Float = 80f,
        energy: Float = 80f,
        mood: Float = 70f,
        activity: PetActivity = PetActivity.AWAKE,
        lastTickAt: Long = T0,
        lastInteractionAt: Long = T0,
    ) = PetSnapshot(PetStats(hunger, energy, mood), activity, lastTickAt, lastInteractionAt)

    // --- decay ---------------------------------------------------------------

    @Test
    fun `awake pet loses one hunger and one energy per minute`() {
        val after = sim.advanceTo(snapshot(), T0 + 10 * MINUTE)

        assertEquals(70f, after.stats.hunger, 0.001f)
        assertEquals(70f, after.stats.energy, 0.001f)
    }

    @Test
    fun `partial minutes are kept owed rather than dropped`() {
        val after = sim.advanceTo(snapshot(), T0 + 90_000L) // 1.5 minutes

        assertEquals(79f, after.stats.hunger, 0.001f)
        // Only the whole minute was consumed; 30s is still on the clock.
        assertEquals(T0 + MINUTE, after.lastTickAt)
    }

    @Test
    fun `ticking every second never loses decay to rounding`() {
        var state = snapshot()
        // 600 one-second ticks == 10 minutes of decay, no more and no less.
        for (second in 1..600) state = sim.advanceTo(state, T0 + second * 1_000L)

        assertEquals(70f, state.stats.hunger, 0.001f)
        assertEquals(T0 + 10 * MINUTE, state.lastTickAt)
    }

    @Test
    fun `stats never fall below zero`() {
        val after = sim.advanceTo(snapshot(), T0 + 5_000 * MINUTE)

        assertEquals(0f, after.stats.hunger, 0.001f)
        assertEquals(0f, after.stats.energy, 0.001f)
        assertEquals(0f, after.stats.mood, 0.001f)
    }

    @Test
    fun `a tick in the past changes nothing`() {
        val before = snapshot()

        assertEquals(before, sim.advanceTo(before, T0 - 10 * MINUTE))
    }

    @Test
    fun `catch-up is capped but still consumes the full owed time`() {
        val threeDays = 3 * 24 * 60L
        val after = sim.advanceTo(snapshot(), T0 + threeDays * MINUTE)

        // The cap bounds the iteration count, not the clock: no debt is left over.
        assertEquals(T0 + threeDays * MINUTE, after.lastTickAt)
        assertEquals(0f, after.stats.hunger, 0.001f)
    }

    // --- sleep ---------------------------------------------------------------

    @Test
    fun `sleeping recovers energy while hunger keeps draining`() {
        val asleep = sim.startSleep(snapshot(hunger = 80f, energy = 40f), T0)
        val after = sim.advanceTo(asleep, T0 + 10 * MINUTE)

        assertEquals(60f, after.stats.energy, 0.001f)
        assertEquals(70f, after.stats.hunger, 0.001f)
        assertTrue(after.isSleeping)
    }

    @Test
    fun `she wakes up by herself once fully rested`() {
        val asleep = sim.startSleep(snapshot(energy = 50f), T0)
        // 50 energy at +2/min is full after exactly 25 minutes.
        val after = sim.advanceTo(asleep, T0 + 25 * MINUTE)

        assertFalse(after.isSleeping)
        assertEquals(PetStats.MAX, after.stats.energy, 0.001f)
    }

    @Test
    fun `energy drains again after the automatic wake-up`() {
        val asleep = sim.startSleep(snapshot(energy = 90f), T0)
        // Full at +5 min, then awake and draining for the remaining 5.
        val after = sim.advanceTo(asleep, T0 + 10 * MINUTE)

        assertFalse(after.isSleeping)
        assertEquals(95f, after.stats.energy, 0.001f)
    }

    @Test
    fun `a sleeping pet ignores feeding and petting`() {
        val asleep = sim.startSleep(snapshot(hunger = 20f, mood = 20f), T0)

        assertEquals(asleep.stats, sim.feed(asleep, T0).stats)
        assertEquals(asleep.stats, sim.pet(asleep, T0).stats)
        assertTrue(sim.feed(asleep, T0).isSleeping)
    }

    @Test
    fun `waking her up ends the sleep`() {
        val asleep = sim.startSleep(snapshot(energy = 10f), T0)
        val awake = sim.wake(asleep, T0 + 5 * MINUTE)

        assertFalse(awake.isSleeping)
        assertTrue(awake.canFeed())
    }

    // --- actions -------------------------------------------------------------

    @Test
    fun `feeding restores hunger and lifts mood`() {
        val after = sim.feed(snapshot(hunger = 20f, mood = 40f), T0)

        assertEquals(55f, after.stats.hunger, 0.001f)
        assertEquals(45f, after.stats.mood, 0.001f)
    }

    @Test
    fun `feeding pays off the decay it was owed first`() {
        val after = sim.feed(snapshot(hunger = 50f), T0 + 10 * MINUTE)

        // 50 - 10 owed + 35 fed, not 50 + 35.
        assertEquals(75f, after.stats.hunger, 0.001f)
    }

    @Test
    fun `feeding is refused when she is already full`() {
        val full = snapshot(hunger = 99f)

        assertFalse(full.canFeed())
        assertEquals(99f, sim.feed(full, T0).stats.hunger, 0.001f)
    }

    @Test
    fun `a rested pat gives the full mood bonus`() {
        val after = sim.pet(snapshot(mood = 50f, lastInteractionAt = T0 - 30 * MINUTE), T0)

        assertEquals(58f, after.stats.mood, 0.001f)
    }

    @Test
    fun `mashing the pat button gives diminishing returns`() {
        val start = snapshot(mood = 50f, lastInteractionAt = T0 - 30 * MINUTE)
        val first = sim.pet(start, T0)
        val second = sim.pet(first, T0)

        val firstGain = first.stats.mood - start.stats.mood
        val secondGain = second.stats.mood - first.stats.mood
        assertTrue("second pat should be weaker: $firstGain vs $secondGain", secondGain < firstGain)
        assertTrue("but never worthless", secondGain > 0f)
    }

    // --- mood ----------------------------------------------------------------

    @Test
    fun `mood drifts towards the average of hunger and energy`() {
        val after = sim.advanceTo(snapshot(hunger = 90f, energy = 90f, mood = 20f), T0 + 4 * MINUTE)

        // Target is well above 20, so mood climbs at the 0.5 per minute cap.
        assertEquals(22f, after.stats.mood, 0.001f)
    }

    @Test
    fun `mood never overshoots its target`() {
        val after = sim.advanceTo(snapshot(hunger = 60f, energy = 60f, mood = 59.9f), T0 + MINUTE)

        assertTrue("mood ${after.stats.mood} overshot", after.stats.mood <= 60f)
    }

    @Test
    fun `being ignored drags the mood target down`() {
        val stats = PetStats(hunger = 80f, energy = 80f, mood = 80f)

        assertEquals(80f, sim.moodTarget(stats, minutesSinceInteraction = 0f), 0.001f)
        assertEquals(76f, sim.moodTarget(stats, minutesSinceInteraction = 60f), 0.001f)
    }

    @Test
    fun `the neglect penalty is capped`() {
        val stats = PetStats(hunger = 100f, energy = 100f, mood = 100f)
        val week = 7 * 24 * 60f

        assertEquals(100f - 30f, sim.moodTarget(stats, week), 0.001f)
    }

    @Test
    fun `attention resets the neglect clock`() {
        val neglected = snapshot(lastInteractionAt = T0 - 10 * 60 * MINUTE)
        val patted = sim.pet(neglected, T0)

        assertEquals(T0, patted.lastInteractionAt)
        assertNotEquals(neglected.lastInteractionAt, patted.lastInteractionAt)
    }

    // --- state machine -------------------------------------------------------

    @Test
    fun `the sprite state follows the stats`() {
        assertEquals(PetState.IDLE, snapshot(hunger = 80f).state())
        assertEquals(PetState.HUNGRY, snapshot(hunger = 30f).state())
        assertEquals(PetState.HUNGRY, snapshot(hunger = 0f).state())
    }

    @Test
    fun `sleeping wins over hungry`() {
        assertEquals(PetState.SLEEPING, snapshot(hunger = 0f, activity = PetActivity.SLEEPING).state())
    }

    // --- tuning --------------------------------------------------------------

    @Test
    fun `balance can be retuned without touching the simulation`() {
        val gentle = PetSimulation(PetTuning(hungerDecayPerMinute = 0.25f))
        val after = gentle.advanceTo(snapshot(hunger = 100f), T0 + 60 * MINUTE)

        assertEquals(85f, after.stats.hunger, 0.001f)
    }
}
