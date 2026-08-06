package com.vpet.waifu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MINUTE = PetSimulation.MS_PER_MINUTE
private const val T0 = 1_700_373_600_000L

/**
 * The things to do while she is out.
 *
 * The point every test here is defending: a shift used to grey out the whole
 * app, so the longest stretch of the game was also the emptiest. Chores, the
 * arcade, and money on the outcome all have to work *with her gone*, and none
 * of them may quietly become better than the shift they are filling time
 * around.
 */
class DowntimeTest {

    private val sim = PetSimulation()
    private val shift = Occupations.byId("office")!!

    private fun snapshot(money: Int = 5_000, exp: Int = 40_000) = PetSnapshot(
        stats = PetStats(95f, 95f, 70f),
        progress = PetProgress(money = money, exp = exp),
        lastTickAt = T0,
        lastInteractionAt = T0,
        // The jar parked, so a wallet assertion measures the thing under test.
        passiveSince = T0,
        passiveDay = Events.dayOf(T0),
        passivePaidToday = 1_000_000,
    )

    // --- chores --------------------------------------------------------------

    @Test
    fun `a chore can be done while she is on a shift`() {
        val working = sim.startOccupation(snapshot(), shift, T0)
        val dishes = Chores.byId("dishes")!!

        assertTrue(Chores.isReady(dishes, working, T0))
        val after = sim.doChore(working, dishes, T0)

        assertEquals(working.progress.money + dishes.money, after.progress.money)
        assertEquals(working.progress.exp + dishes.exp, after.progress.exp)
        // …and it does not end the shift she is on.
        assertEquals(PetActivity.WORKING, after.activity)
        assertEquals(working.session?.endsAt, after.session?.endsAt)
    }

    @Test
    fun `a chore is on cooldown until its timer is up`() {
        val dishes = Chores.byId("dishes")!!
        val done = sim.doChore(snapshot(), dishes, T0)

        assertFalse(Chores.isReady(dishes, done, T0))
        // A second attempt inside the window changes nothing at all.
        assertEquals(done.progress.money, sim.doChore(done, dishes, T0 + MINUTE).progress.money)

        val later = T0 + (dishes.cooldownMinutes + 1) * MINUTE
        assertTrue(Chores.isReady(dishes, sim.advanceTo(done, later), later))
    }

    @Test
    fun `a chore about a thing you do not own is not offered`() {
        val cat = Chores.byId("cat")!!
        val bare = snapshot()

        assertFalse(cat in Chores.ready(bare, T0))
        val withCat = bare.copy(owned = bare.owned + "cat")
        assertTrue(cat in Chores.ready(withCat, T0))
    }

    @Test
    fun `the chores are worth doing and not worth grinding`() {
        // Every chore in one go, against an hour of the job they fill time
        // around. They have to be worth the tap and nowhere near the shift.
        val everything = Chores.ALL.sumOf { it.money }
        assertTrue("chores total $everything", everything < shift.payout)
        assertTrue("but not pocket lint", everything > shift.payout / 6)
    }

    // --- the arcade, mid-shift ----------------------------------------------

    @Test
    fun `the arcade is open while she is working`() {
        val working = sim.startOccupation(snapshot(), shift, T0)
        val opened = sim.startPlaying(working, T0)

        // She stays on the clock — the arcade is the player's game, not hers.
        assertEquals(PetActivity.WORKING, opened.activity)
        assertNotEquals(null, opened.session)

        val after = sim.finishPlaying(opened, MiniGame.CATCH.targetScore, T0, MiniGame.CATCH)
        assertEquals("the round must not clock her off", PetActivity.WORKING, after.activity)
        assertNotEquals(null, after.session)
        assertTrue(after.progress.money > working.progress.money)
    }

    @Test
    fun `a sick pet still does not go to the arcade`() {
        val ill = snapshot().copy(sickSince = T0 - 60 * MINUTE)

        assertEquals(ill.activity, sim.startPlaying(ill, T0).activity)
        assertNotEquals(PetActivity.PLAYING, sim.startPlaying(ill, T0).activity)
    }

    @Test
    fun `the first round of each game each day pays more`() {
        val fresh = sim.advanceTo(snapshot(), T0)
        val score = MiniGame.CATCH.targetScore

        val first = sim.finishPlaying(sim.startPlaying(fresh, T0), score, T0, MiniGame.CATCH)
        val firstPaid = first.progress.money - fresh.progress.money

        val second = sim.finishPlaying(sim.startPlaying(first, T0), score, T0, MiniGame.CATCH)
        val secondPaid = second.progress.money - first.progress.money

        assertTrue("first $firstPaid should beat second $secondPaid", firstPaid > secondPaid)
        // …and the bonus is per game, so another game is still a first.
        val other = sim.finishPlaying(
            sim.startPlaying(second, T0), MiniGame.MEMORY.targetScore, T0, MiniGame.MEMORY,
        )
        assertTrue(other.playedToday(MiniGame.MEMORY))
        assertTrue(other.playedToday(MiniGame.CATCH))
    }

    @Test
    fun `three clean rounds in a row hand over a buff`() {
        var state: PetSnapshot = sim.advanceTo(snapshot(), T0)
        val score = MiniGame.CATCH.targetScore

        repeat(Arcade.COMBO_LENGTH) {
            state = sim.finishPlaying(sim.startPlaying(state, T0), score, T0, MiniGame.CATCH)
        }

        assertTrue("the combo should grant a buff", state.hasEffect(EffectKind.GOOD_VIBES, T0))
        assertTrue("and rounds that pay double", state.luckyGames > 0)
        // The run resets, so the next buff is another three rounds away.
        assertEquals(0, state.arcadeStreak)
    }

    @Test
    fun `a poor round breaks the run`() {
        var state: PetSnapshot = sim.advanceTo(snapshot(), T0)
        state = sim.finishPlaying(sim.startPlaying(state, T0), MiniGame.CATCH.targetScore, T0, MiniGame.CATCH)
        assertEquals(1, state.arcadeStreak)

        state = sim.finishPlaying(sim.startPlaying(state, T0), 1, T0, MiniGame.CATCH)
        assertEquals(0, state.arcadeStreak)
    }

    // --- money on the outcome ------------------------------------------------

    @Test
    fun `a stake is taken now and settled on how the shift went`() {
        val working = sim.startOccupation(snapshot(), shift, T0)
        val staked = sim.stake(working, 400, T0)

        // Taken immediately: that is the cost, and it has to be felt at the
        // moment of the decision.
        assertEquals(working.progress.money - 400, staked.progress.money)
        assertEquals(400, staked.session?.stake)

        val end = T0 + (shift.durationMinutes + 2) * MINUTE
        val done = sim.advanceTo(staked, end)
        assertEquals(PetActivity.AWAKE, done.activity)

        val quality = done.lastOutcome!!.quality
        assertTrue("a cared-for shift should go well", Stakes.wins(quality))
    }

    @Test
    fun `a stake cannot exceed the cap or be placed twice`() {
        val working = sim.startOccupation(snapshot(), shift, T0)
        val cap = Stakes.maxStake(working)

        val over = sim.stake(working, cap * 10, T0)
        assertEquals(cap, over.session?.stake)

        // A second stake on the same shift is refused outright.
        val again = sim.stake(over, 100, T0)
        assertEquals(cap, again.session?.stake)
        assertEquals(over.progress.money, again.progress.money)
    }

    @Test
    fun `walking out of a shift loses the stake`() {
        val working = sim.startOccupation(snapshot(), shift, T0)
        val staked = sim.stake(working, 400, T0)
        val quit = sim.cancelOccupation(staked, T0 + 5 * MINUTE)

        assertEquals(OutcomeQuality.POOR, quit.lastOutcome?.quality)
        // The stake comes back short — that is what stops it being a free
        // option you can cancel out of the moment her mood dips.
        assertTrue(
            "returned ${quit.progress.money} vs staked-out ${staked.progress.money}",
            quit.progress.money < staked.progress.money + 400,
        )
    }

    // --- what turns up around the flat --------------------------------------

    @Test
    fun `a find is scheduled and can be picked up once`() {
        val started = sim.advanceTo(snapshot(), T0)
        val at = started.findReadyAt
        assertTrue("a find should be scheduled", at > T0)
        assertTrue(
            "and inside the window",
            at - T0 in Finds.MIN_MINUTES * MINUTE..Finds.MAX_MINUTES * MINUTE,
        )

        assertFalse(Finds.isWaiting(started, T0))
        val waiting = sim.advanceTo(started, at)
        assertTrue(Finds.isWaiting(waiting, at))

        val claimed = sim.claimFind(waiting, at)
        assertTrue(claimed.progress.money > waiting.progress.money || claimed.progress.exp > waiting.progress.exp)
        // …and it is gone, with the next one already booked.
        assertFalse(Finds.isWaiting(claimed, at))
        assertTrue(claimed.findReadyAt > at)
    }

    @Test
    fun `a find left unnoticed expires rather than waiting forever`() {
        val started = sim.advanceTo(snapshot(), T0)
        val missedBy = started.findReadyAt + (Finds.LINGER_MINUTES + 1) * MINUTE
        val later = sim.advanceTo(started, missedBy)

        assertFalse(Finds.isWaiting(later, missedBy))
        assertTrue("a new one is booked", later.findReadyAt > missedBy)
        // Claiming a find that is not there pays nothing.
        assertEquals(later.progress.money, sim.claimFind(later, missedBy).progress.money)
    }
}
