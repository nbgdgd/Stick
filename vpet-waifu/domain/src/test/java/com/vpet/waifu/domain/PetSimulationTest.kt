package com.vpet.waifu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        money: Int = 0,
        exp: Int = 0,
        lastTickAt: Long = T0,
        lastInteractionAt: Long = T0,
    ) = PetSnapshot(
        stats = PetStats(hunger, energy, mood),
        progress = PetProgress(money, exp),
        activity = activity,
        lastTickAt = lastTickAt,
        lastInteractionAt = lastInteractionAt,
    )

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

    // --- care actions --------------------------------------------------------

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
    fun `feeding plays the eating animation for a few seconds`() {
        val after = sim.feed(snapshot(hunger = 40f), T0)

        assertEquals(PetState.EATING, after.state(T0 + 1_000))
        assertNotEquals(PetState.EATING, after.state(T0 + 30_000))
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

    // --- work ----------------------------------------------------------------

    private val cafe = Occupations.byId("cafe")!!
    private val school = Occupations.byId("school")!!

    @Test
    fun `starting a shift puts her on the clock with an end time`() {
        val working = sim.startOccupation(snapshot(), cafe, T0)

        assertEquals(PetActivity.WORKING, working.activity)
        assertEquals(T0 + 30 * MINUTE, working.session?.endsAt)
        assertEquals(PetState.WORKING, working.state(T0))
    }

    @Test
    fun `a shift pays out by itself when the timer runs out`() {
        val working = sim.startOccupation(snapshot(mood = 50f), cafe, T0)
        val after = sim.advanceTo(working, T0 + 40 * MINUTE)

        assertEquals(PetActivity.AWAKE, after.activity)
        assertNull(after.session)
        assertEquals(cafe.payout, after.progress.money)
        assertTrue("work should also teach her something", after.progress.exp > 0)
    }

    @Test
    fun `a shift finishes even if the phone was asleep for hours`() {
        val working = sim.startOccupation(snapshot(mood = 50f), cafe, T0)
        val after = sim.advanceTo(working, T0 + 8 * 60 * MINUTE)

        assertEquals(PetActivity.AWAKE, after.activity)
        assertEquals(cafe.payout, after.progress.money)
    }

    @Test
    fun `a happy pet earns more than a miserable one`() {
        // A shift is a mood drain, so "great" needs her to start topped up —
        // 30 minutes on the clock costs her about 20 points either way.
        val happy = sim.advanceTo(
            sim.startOccupation(snapshot(hunger = 100f, energy = 100f, mood = 100f), cafe, T0),
            T0 + 31 * MINUTE,
        )
        val sad = sim.advanceTo(sim.startOccupation(snapshot(mood = 25f), cafe, T0), T0 + 31 * MINUTE)

        assertTrue(
            "happy ${happy.progress.money} should beat sad ${sad.progress.money}",
            happy.progress.money > sad.progress.money,
        )
        assertEquals(OutcomeQuality.GREAT, happy.lastOutcome?.quality)
    }

    @Test
    fun `working drains energy over exactly the length of the shift`() {
        val working = sim.startOccupation(snapshot(energy = 100f), cafe, T0)
        val after = sim.advanceTo(working, T0 + 30 * MINUTE)

        assertEquals(100f - cafe.energyCost, after.stats.energy, 0.5f)
    }

    @Test
    fun `working makes her hungrier than lounging`() {
        val working = sim.advanceTo(sim.startOccupation(snapshot(), cafe, T0), T0 + 20 * MINUTE)
        val idle = sim.advanceTo(snapshot(), T0 + 20 * MINUTE)

        assertTrue(working.stats.hunger < idle.stats.hunger)
    }

    @Test
    fun `she cannot be sent to work while exhausted`() {
        val exhausted = snapshot(energy = 5f)

        assertFalse(exhausted.canStart(cafe))
        assertEquals(PetActivity.AWAKE, sim.startOccupation(exhausted, cafe, T0).activity)
    }

    @Test
    fun `locked jobs cannot be started`() {
        val office = Occupations.byId("office")!!

        assertFalse(snapshot().canStart(office))
        assertTrue(snapshot(exp = 100_000).canStart(office))
    }

    @Test
    fun `calling her home early pays for the time she put in`() {
        val working = sim.startOccupation(snapshot(mood = 50f), cafe, T0)
        val after = sim.cancelOccupation(working, T0 + 15 * MINUTE)

        assertEquals(PetActivity.AWAKE, after.activity)
        assertTrue(after.lastOutcome?.cancelled == true)
        // Half the shift at the "good" multiplier.
        val half = cafe.payout / 2
        assertTrue("got ${after.progress.money}, expected about $half", after.progress.money in (half - 3)..(half + 3))
    }

    @Test
    fun `running out of energy on the clock ends the shift early`() {
        val working = sim.startOccupation(snapshot(energy = 20f, mood = 50f), cafe, T0)
        val after = sim.advanceTo(working, T0 + 30 * MINUTE)

        assertEquals(PetActivity.AWAKE, after.activity)
        assertTrue(after.lastOutcome?.cancelled == true)
        assertTrue("partial pay only", after.progress.money < cafe.payout)
    }

    // --- study ---------------------------------------------------------------

    @Test
    fun `studying pays in EXP and not money`() {
        val studying = sim.startOccupation(snapshot(mood = 50f), school, T0)
        val after = sim.advanceTo(studying, T0 + 31 * MINUTE)

        assertEquals(0, after.progress.money)
        assertEquals(school.payout, after.progress.exp)
        assertEquals(PetState.STUDYING, studying.state(T0))
    }

    @Test
    fun `studying in a bad mood is punished harder than working in one`() {
        val badWork = sim.multiplierFor(OutcomeQuality.BAD, OccupationKind.WORK)
        val badStudy = sim.multiplierFor(OutcomeQuality.BAD, OccupationKind.STUDY)

        assertTrue("study $badStudy should be worse than work $badWork", badStudy < badWork)
    }

    @Test
    fun `enough study levels her up and unlocks the next job`() {
        var state = snapshot(mood = 90f)
        repeat(3) {
            state = sim.advanceTo(sim.startOccupation(state.copy(stats = state.stats.copy(energy = 100f, mood = 90f)), school, state.lastTickAt), state.lastTickAt + 31 * MINUTE)
        }

        assertTrue("exp ${state.progress.exp}", state.progress.exp >= Progression.expForLevel(2))
        assertTrue(state.level >= 2)
    }

    // --- shop ----------------------------------------------------------------

    @Test
    fun `buying food costs money and fills her up`() {
        val ramen = Shop.byId("ramen")!!
        val after = sim.buy(snapshot(hunger = 20f, money = 200), ramen, T0)

        assertEquals(200 - ramen.price, after.progress.money)
        assertEquals(20f + ramen.hunger, after.stats.hunger, 0.001f)
        assertEquals(PetState.EATING, after.state(T0 + 1_000))
    }

    @Test
    fun `an empty wallet buys nothing`() {
        val ramen = Shop.byId("ramen")!!
        val broke = snapshot(hunger = 20f, money = 10)

        assertFalse(broke.canBuy(ramen))
        assertEquals(broke.stats, sim.buy(broke, ramen, T0).stats)
    }

    @Test
    fun `the cash advance pays now and starves her later`() {
        val advance = Shop.byId("advance")!!
        val after = sim.buy(snapshot(money = 200), advance, T0)

        assertEquals(200 - advance.price + advance.money, after.progress.money)
        assertTrue(after.hasEffect(EffectKind.HUNGER_SURGE, T0 + 60 * MINUTE))

        val hour = sim.advanceTo(after, T0 + 60 * MINUTE)
        val normal = sim.advanceTo(snapshot(), T0 + 60 * MINUTE)
        assertTrue(
            "surge ${hour.stats.hunger} should drain faster than normal ${normal.stats.hunger}",
            hour.stats.hunger < normal.stats.hunger,
        )
    }

    @Test
    fun `effects expire on their own`() {
        val advance = Shop.byId("advance")!!
        val after = sim.buy(snapshot(money = 200), advance, T0)

        assertTrue(after.hasEffect(EffectKind.HUNGER_SURGE, T0))
        val later = sim.advanceTo(after, T0 + 200 * MINUTE)
        assertFalse(later.hasEffect(EffectKind.HUNGER_SURGE, T0 + 200 * MINUTE))
        assertTrue(later.effects.isEmpty())
    }

    @Test
    fun `the exp pill trades energy and mood for a level`() {
        val pill = Shop.byId("exp_pill")!!
        val before = snapshot(energy = 80f, mood = 80f, money = 500, exp = Progression.expForLevel(2))
        val after = sim.buy(before, pill, T0)

        assertTrue(after.progress.exp > before.progress.exp)
        assertTrue(after.stats.energy < before.stats.energy)
        assertTrue(after.stats.mood < before.stats.mood)
        assertTrue(after.hasEffect(EffectKind.EXHAUSTION, T0))
    }

    @Test
    fun `exhaustion makes energy drain faster`() {
        val pill = Shop.byId("exp_pill")!!
        val tired = sim.buy(snapshot(money = 500, exp = Progression.expForLevel(2)), pill, T0)
        val energyAtBuy = tired.stats.energy

        val after = sim.advanceTo(tired, T0 + 10 * MINUTE)

        assertEquals(energyAtBuy - 16f, after.stats.energy, 0.001f)
    }

    @Test
    fun `gifts are pure mood and locked behind levels`() {
        val ring = Shop.byId("ring")!!

        assertFalse(snapshot(money = 10_000).canBuy(ring))
        val rich = snapshot(mood = 10f, money = 10_000, exp = Progression.expForLevel(9))
        assertTrue(rich.canBuy(ring))
        assertEquals(10f + ring.mood, sim.buy(rich, ring, T0).stats.mood, 0.001f)
    }

    // --- mini-game -----------------------------------------------------------

    @Test
    fun `playing lifts mood but costs energy`() {
        val playing = sim.startPlaying(snapshot(mood = 40f, energy = 80f), T0)
        assertEquals(PetState.PLAYING, playing.state(T0))

        val after = sim.finishPlaying(playing, score = 20, nowMillis = T0)

        assertTrue(after.stats.mood > 40f)
        assertTrue(after.stats.energy < 80f)
        assertEquals(PetActivity.AWAKE, after.activity)
    }

    @Test
    fun `a better score is worth more mood`() {
        assertTrue(TapGame.moodGain(30) > TapGame.moodGain(5))
        assertEquals(32f, TapGame.moodGain(1_000), 0.001f)
    }

    // --- progression ---------------------------------------------------------

    @Test
    fun `levels need progressively more exp`() {
        val toTwo = Progression.expForLevel(2) - Progression.expForLevel(1)
        val toThree = Progression.expForLevel(3) - Progression.expForLevel(2)

        assertTrue(toThree > toTwo)
        assertEquals(1, Progression.levelForExp(0))
        assertEquals(2, Progression.levelForExp(Progression.expForLevel(2)))
    }

    @Test
    fun `level progress is reported within the current level`() {
        val (earned, needed) = Progression.levelProgress(Progression.expForLevel(2) + 10)

        assertEquals(10, earned)
        assertTrue(needed > 0)
    }

    // --- state machine -------------------------------------------------------

    @Test
    fun `the sprite state follows the stats`() {
        assertEquals(PetState.IDLE, snapshot(hunger = 80f, energy = 80f, mood = 50f).state(T0))
        assertEquals(PetState.HUNGRY, snapshot(hunger = 30f).state(T0))
        assertEquals(PetState.TIRED, snapshot(hunger = 80f, energy = 20f).state(T0))
        assertEquals(PetState.HAPPY, snapshot(hunger = 90f, energy = 90f, mood = 90f).state(T0))
    }

    @Test
    fun `sleeping and working win over how she feels`() {
        assertEquals(PetState.SLEEPING, snapshot(hunger = 0f, activity = PetActivity.SLEEPING).state(T0))
        assertEquals(PetState.WORKING, snapshot(hunger = 0f, activity = PetActivity.WORKING).state(T0))
    }

    @Test
    fun `finishing a shift leaves a result to show and it can be dismissed`() {
        val working = sim.startOccupation(snapshot(mood = 50f), cafe, T0)
        val done = sim.advanceTo(working, T0 + 31 * MINUTE)

        assertNotNull(done.lastOutcome)
        assertNull(sim.acknowledgeOutcome(done).lastOutcome)
    }

    // --- tuning --------------------------------------------------------------

    @Test
    fun `balance can be retuned without touching the simulation`() {
        val gentle = PetSimulation(PetTuning(hungerDecayPerMinute = 0.25f))
        val after = gentle.advanceTo(snapshot(hunger = 100f), T0 + 60 * MINUTE)

        assertEquals(85f, after.stats.hunger, 0.001f)
    }
}
