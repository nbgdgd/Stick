package com.vpet.waifu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

private const val MINUTE = PetSimulation.MS_PER_MINUTE

/**
 * Six in the morning on a day that carries no event, followed by another.
 *
 * Days now roll their own event, so an arbitrary timestamp silently puts every
 * test on whatever the calendar happened to hand it — the first draft of this
 * file sat on a lucky day and every payout came out thirty percent high. Tests
 * about events pick their own day on purpose; everything else runs on a
 * deliberately uneventful one.
 */
private const val T0 = 1_700_373_600_000L

/**
 * The balance rules.
 *
 * Expectations are written against [PetTuning] rather than against the numbers
 * it currently holds: a rebalance should change how the game feels, not break
 * the suite that proves it still works.
 */
class PetSimulationTest {

    private val sim = PetSimulation()
    private val tuning = sim.tuning

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
    fun `an awake pet loses hunger and energy at the tuned rate`() {
        val after = sim.advanceTo(snapshot(), T0 + 10 * MINUTE)

        assertEquals(80f - tuning.hungerDecayPerMinute * 10, after.stats.hunger, 0.001f)
        assertEquals(80f - tuning.energyDecayPerMinute * 10, after.stats.energy, 0.001f)
    }

    @Test
    fun `a full pet lasts hours rather than minutes`() {
        // The point of the balance pass: she should survive a working day, so
        // the game is something to check in on rather than a chore.
        val afterThreeHours = sim.advanceTo(snapshot(hunger = 100f, energy = 100f), T0 + 180 * MINUTE)

        assertTrue("hunger ${afterThreeHours.stats.hunger}", afterThreeHours.stats.hunger > 20f)
        assertTrue("energy ${afterThreeHours.stats.energy}", afterThreeHours.stats.energy > 20f)
    }

    @Test
    fun `partial minutes are kept owed rather than dropped`() {
        val after = sim.advanceTo(snapshot(), T0 + 90_000L) // 1.5 minutes

        assertEquals(80f - tuning.hungerDecayPerMinute, after.stats.hunger, 0.001f)
        // Only the whole minute was consumed; 30s is still on the clock.
        assertEquals(T0 + MINUTE, after.lastTickAt)
    }

    @Test
    fun `ticking every second never loses decay to rounding`() {
        var state = snapshot()
        // 600 one-second ticks == 10 minutes of decay, no more and no less.
        for (second in 1..600) state = sim.advanceTo(state, T0 + second * 1_000L)

        assertEquals(80f - tuning.hungerDecayPerMinute * 10, state.stats.hunger, 0.001f)
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
        val after = sim.advanceTo(before, T0 - 10 * MINUTE)

        // Everything about the pet is untouched. The one thing that does move
        // is the tip jar's anchor: a clock that has run backwards leaves it
        // pointing into the future, and it re-anchors rather than paying out
        // the negative interval.
        assertEquals(before, after.copy(passiveSince = before.passiveSince))
        assertEquals(T0 - 10 * MINUTE, after.passiveSince)
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

        assertEquals(40f + tuning.energyRecoveryPerMinute * 10, after.stats.energy, 0.001f)
        assertEquals(80f - tuning.hungerDecayPerMinute * 10, after.stats.hunger, 0.001f)
        assertTrue(after.isSleeping)
    }

    @Test
    fun `she wakes up by herself once fully rested`() {
        val asleep = sim.startSleep(snapshot(energy = 50f), T0)
        val minutesToFull = (50f / tuning.energyRecoveryPerMinute).toLong() + 1
        val after = sim.advanceTo(asleep, T0 + minutesToFull * MINUTE)

        assertFalse(after.isSleeping)
    }

    @Test
    fun `energy drains again after the automatic wake-up`() {
        val asleep = sim.startSleep(snapshot(energy = 95f), T0)
        val after = sim.advanceTo(asleep, T0 + 30 * MINUTE)

        assertFalse(after.isSleeping)
        assertTrue("she should have been draining since waking", after.stats.energy < PetStats.MAX)
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

        assertEquals(20f + tuning.feedHunger, after.stats.hunger, 0.001f)
        assertEquals(40f + tuning.feedMood, after.stats.mood, 0.001f)
    }

    @Test
    fun `feeding pays off the decay it was owed first`() {
        val after = sim.feed(snapshot(hunger = 50f), T0 + 10 * MINUTE)

        assertEquals(50f - tuning.hungerDecayPerMinute * 10 + tuning.feedHunger, after.stats.hunger, 0.001f)
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

        assertEquals(50f + tuning.petMood, after.stats.mood, 0.001f)
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

        // Target is well above 20, so mood climbs at the per-minute cap.
        assertEquals(20f + tuning.moodDriftPerMinute * 4, after.stats.mood, 0.001f)
    }

    @Test
    fun `mood never overshoots its target`() {
        val after = sim.advanceTo(snapshot(hunger = 60f, energy = 60f, mood = 59.9f), T0 + MINUTE)

        assertTrue("mood ${after.stats.mood} overshot", after.stats.mood <= 60f)
    }

    @Test
    fun `being ignored drags the mood target down`() {
        val stats = PetStats(hunger = 80f, energy = 80f, mood = 80f)
        val hour = 60f

        assertEquals(80f, sim.moodTarget(stats, minutesSinceInteraction = 0f), 0.001f)
        assertEquals(
            80f - hour / tuning.neglectMinutesPerPoint,
            sim.moodTarget(stats, hour),
            0.001f,
        )
    }

    @Test
    fun `the neglect penalty is capped`() {
        val stats = PetStats(hunger = 100f, energy = 100f, mood = 100f)
        val week = 7 * 24 * 60f

        assertEquals(100f - tuning.maxNeglectPenalty, sim.moodTarget(stats, week), 0.001f)
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
        assertEquals(T0 + cafe.durationMinutes * MINUTE, working.session?.endsAt)
        assertEquals(PetState.WORKING, working.state(T0))
    }

    @Test
    fun `wages arrive during the shift, not only at the end`() {
        val working = sim.startOccupation(snapshot(mood = 50f), cafe, T0)

        val quarter = sim.advanceTo(working, T0 + cafe.durationMinutes / 4 * MINUTE)
        val half = sim.advanceTo(working, T0 + cafe.durationMinutes / 2 * MINUTE)

        assertTrue("nothing paid a quarter in", quarter.progress.money > 0)
        assertTrue("pay should keep climbing", half.progress.money > quarter.progress.money)
        assertTrue("but not the whole shift yet", half.progress.money < cafe.payout)
        // Still on the clock — the money is not an early payout.
        assertEquals(PetActivity.WORKING, half.activity)
    }

    @Test
    fun `pay accrues smoothly rather than in one lump`() {
        var state = sim.startOccupation(snapshot(mood = 50f), cafe, T0)
        var previous = 0
        var increments = 0
        for (minute in 1..cafe.durationMinutes) {
            state = sim.advanceTo(state, T0 + minute * MINUTE)
            if (state.progress.money > previous) increments++
            previous = state.progress.money
        }

        assertTrue("only $increments payments across the shift", increments >= cafe.durationMinutes / 3)
    }

    @Test
    fun `a full shift pays what the job advertises`() {
        val working = sim.startOccupation(snapshot(mood = 50f), cafe, T0)
        val after = sim.advanceTo(working, T0 + (cafe.durationMinutes + 2) * MINUTE)

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
        val happy = sim.advanceTo(
            sim.startOccupation(snapshot(hunger = 100f, energy = 100f, mood = 100f), cafe, T0),
            T0 + (cafe.durationMinutes + 2) * MINUTE,
        )
        val sad = sim.advanceTo(
            sim.startOccupation(snapshot(mood = 25f), cafe, T0),
            T0 + (cafe.durationMinutes + 2) * MINUTE,
        )

        assertTrue(
            "happy ${happy.progress.money} should beat sad ${sad.progress.money}",
            happy.progress.money > sad.progress.money,
        )
    }

    @Test
    fun `working drains energy over exactly the length of the shift`() {
        val working = sim.startOccupation(snapshot(energy = 100f), cafe, T0)
        val after = sim.advanceTo(working, T0 + cafe.durationMinutes * MINUTE)

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
        val exhausted = snapshot(energy = tuning.minimumEnergyToWork - 1f)

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
    fun `calling her home early lets her keep what she earned`() {
        val working = sim.startOccupation(snapshot(mood = 50f), cafe, T0)
        val half = cafe.durationMinutes / 2
        val after = sim.cancelOccupation(working, T0 + half * MINUTE)

        assertEquals(PetActivity.AWAKE, after.activity)
        assertTrue(after.lastOutcome?.cancelled == true)
        assertNear(cafe.payout / 2, after.progress.money)
    }

    @Test
    fun `running out of energy on the clock ends the shift early`() {
        // Just enough to start, not enough to see it through.
        val barely = tuning.minimumEnergyToWork + 1f
        val working = sim.startOccupation(snapshot(energy = barely, mood = 50f), cafe, T0)
        val after = sim.advanceTo(working, T0 + cafe.durationMinutes * MINUTE)

        assertEquals(PetActivity.AWAKE, after.activity)
        assertTrue(after.lastOutcome?.cancelled == true)
        assertTrue("partial pay only", after.progress.money < cafe.payout)
        assertTrue("but she is paid for the time she did put in", after.progress.money > 0)
    }

    // --- study ---------------------------------------------------------------

    @Test
    fun `studying pays in EXP and not money`() {
        val studying = sim.startOccupation(snapshot(mood = 50f), school, T0)
        val after = sim.advanceTo(studying, T0 + (school.durationMinutes + 2) * MINUTE)

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
            val topped = state.copy(stats = state.stats.copy(energy = 100f, mood = 90f))
            state = sim.advanceTo(
                sim.startOccupation(topped, school, state.lastTickAt),
                state.lastTickAt + (school.durationMinutes + 2) * MINUTE,
            )
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
    fun `the energy drink is the only way to buy energy`() {
        val drink = Shop.byId("energy_drink")!!
        val tired = snapshot(energy = 30f, money = 500)

        val after = sim.buy(tired, drink, T0)

        assertEquals(30f + drink.energy, after.stats.energy, 0.001f)
        assertEquals(500 - drink.price, after.progress.money)
        // And the crash is recorded rather than hidden.
        assertTrue(after.hasEffect(EffectKind.EXHAUSTION, T0))
    }

    @Test
    fun `the energy drink does not simply replace sleeping`() {
        val drink = Shop.byId("energy_drink")!!
        val bought = sim.buy(snapshot(energy = 30f, money = 500), drink, T0)
        val slept = sim.advanceTo(sim.startSleep(snapshot(energy = 30f), T0), T0 + 30 * MINUTE)

        // Half an hour of sleep beats the can, and costs nothing.
        val drunkLater = sim.advanceTo(bought, T0 + 30 * MINUTE)
        assertTrue(
            "sleep ${slept.stats.energy} should beat the can ${drunkLater.stats.energy}",
            slept.stats.energy > drunkLater.stats.energy,
        )
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
        val later = sim.advanceTo(after, T0 + 300 * MINUTE)
        assertFalse(later.hasEffect(EffectKind.HUNGER_SURGE, T0 + 300 * MINUTE))
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

        assertEquals(
            energyAtBuy - tuning.energyDecayPerMinute * tuning.exhaustionMultiplier * 10,
            after.stats.energy,
            0.001f,
        )
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
    fun `an abandoned round does not trap her in the game forever`() {
        // The screen that started the round is gone and finishPlaying is never
        // called; without a ceiling every other action stays blocked.
        val playing = sim.startPlaying(snapshot(), T0)
        val later = sim.advanceTo(playing, T0 + (tuning.maxPlayMinutes.toLong() + 1) * MINUTE)

        assertEquals(PetActivity.AWAKE, later.activity)
        assertTrue(later.acceptsInteraction)
        assertTrue(later.canFeed())
    }

    @Test
    fun `a round still counts when the screen comes back late`() {
        val playing = sim.startPlaying(snapshot(mood = 40f), T0)
        val abandoned = sim.advanceTo(playing, T0 + (tuning.maxPlayMinutes.toLong() + 1) * MINUTE)

        val settled = sim.finishPlaying(abandoned, score = 15, nowMillis = abandoned.lastTickAt)

        assertTrue("the score should still pay out", settled.stats.mood > abandoned.stats.mood)
    }

    @Test
    fun `a better score is worth more mood`() {
        MiniGame.entries.forEach { game ->
            assertTrue("$game", game.moodGain(30) > game.moodGain(5))
            assertEquals("$game", MiniGame.MAX_MOOD, game.moodGain(100_000), 0.001f)
        }
    }

    @Test
    fun `no game in the arcade is the one worth grinding`() {
        // Three games only stay three games while none of them pays best. A
        // strong round is roughly a third of each one's theoretical maximum;
        // what they pay for that has to land within a hair of each other, or
        // the other two become decoration.
        val strong = mapOf(MiniGame.CATCH to 26, MiniGame.RHYTHM to 110, MiniGame.MEMORY to 32)
        val moods = strong.map { (game, score) -> game.moodGain(score) }
        val coins = strong.map { (game, score) -> game.coins(score) }

        assertTrue("mood spread: $moods", moods.max() - moods.min() < 4f)
        assertTrue("coin spread: $coins", coins.max() - coins.min() <= 2)
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

    @Test
    fun `the first job is affordable to start and worth more than a meal`() {
        // A new player must be able to feed her before the first wage arrives,
        // and one shift must be worth more than the snack it replaces.
        val onigiri = Shop.byId("onigiri")!!
        assertTrue(PetProgress().canAfford(onigiri.price))
        assertTrue(cafe.payout > onigiri.price)
    }

    // --- state machine -------------------------------------------------------

    @Test
    fun `the sprite state follows the stats`() {
        assertEquals(PetState.IDLE, snapshot(hunger = 80f, energy = 80f, mood = 50f).state(T0))
        assertEquals(PetState.HUNGRY, snapshot(hunger = tuning.hungryThreshold).state(T0))
        assertEquals(PetState.TIRED, snapshot(hunger = 80f, energy = tuning.tiredThreshold).state(T0))
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
        val done = sim.advanceTo(working, T0 + (cafe.durationMinutes + 2) * MINUTE)

        assertNotNull(done.lastOutcome)
        assertNull(sim.acknowledgeOutcome(done).lastOutcome)
    }

    @Test
    fun `the result reports everything the shift paid`() {
        val working = sim.startOccupation(snapshot(mood = 50f), cafe, T0)
        val done = sim.advanceTo(working, T0 + (cafe.durationMinutes + 2) * MINUTE)

        assertEquals(done.progress.money, done.lastOutcome?.money)
        assertEquals(done.progress.exp, done.lastOutcome?.exp)
    }

    // --- tuning --------------------------------------------------------------

    @Test
    fun `balance can be retuned without touching the simulation`() {
        val gentle = PetSimulation(PetTuning(hungerDecayPerMinute = 0.25f))
        val after = gentle.advanceTo(snapshot(hunger = 100f), T0 + 60 * MINUTE)

        assertEquals(85f, after.stats.hunger, 0.001f)
    }

    /** For sums that land on a rounding boundary — never for exact payouts. */
    private fun assertNear(expected: Int, actual: Int, tolerance: Int = 2) {
        assertTrue("expected about $expected but was $actual", abs(expected - actual) <= tolerance)
    }
}
