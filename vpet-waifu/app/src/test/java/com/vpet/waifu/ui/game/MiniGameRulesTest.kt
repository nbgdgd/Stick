package com.vpet.waifu.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules behind the two new games, without the pixels.
 *
 * Both are timing- and state-machine-shaped, which is exactly the sort of thing
 * that looks right on screen and is quietly wrong: a beat map that runs off the
 * end of the round, a judgement window that overlaps its neighbour, a sequence
 * that stops growing. None of that is visible in a screenshot.
 */
class MiniGameRulesTest {

    // --- rhythm --------------------------------------------------------------

    @Test
    fun `the chart fills the round and stops at the bell`() {
        val beats = beatMap(seed = 7, durationSeconds = 28)

        assertTrue("only ${beats.size} notes in 28 seconds", beats.size > 35)
        assertTrue("a note lands after the round ends", beats.all { it.atMillis < 28_000 })
        assertTrue("the first note gives no time to react", beats.first().atMillis >= LEAD_IN_MILLIS)
    }

    @Test
    fun `the chart only ever moves forwards`() {
        val beats = beatMap(seed = 11, durationSeconds = 28)

        beats.zipWithNext().forEach { (a, b) ->
            assertTrue("notes out of order at ${a.atMillis}", b.atMillis > a.atMillis)
        }
    }

    @Test
    fun `the same seed charts the same round`() {
        assertEquals(beatMap(42, 28), beatMap(42, 28))
    }

    @Test
    fun `it speeds up as the round goes on`() {
        val beats = beatMap(seed = 3, durationSeconds = 28)
        val gaps = beats.zipWithNext { a, b -> b.atMillis - a.atMillis }
        val early = gaps.take(8).average()
        val late = gaps.takeLast(8).average()

        assertTrue("early=$early late=$late — the ramp is flat", late < early * 0.8)
    }

    @Test
    fun `every note lands on a lane that exists`() {
        assertTrue(beatMap(9, 28).all { it.lane in 0..2 })
    }

    @Test
    fun `the judgement windows nest, and late is judged like early`() {
        assertEquals(Judgement.PERFECT, judge(0))
        assertEquals(Judgement.PERFECT, judge(-80))
        assertEquals(Judgement.PERFECT, judge(80))
        assertEquals(Judgement.GREAT, judge(120))
        assertEquals(Judgement.GREAT, judge(-120))
        assertEquals(Judgement.GOOD, judge(200))
        assertEquals(Judgement.MISS, judge(400))
        assertEquals(Judgement.MISS, judge(-400))
    }

    @Test
    fun `a better verdict is worth more`() {
        assertTrue(Judgement.PERFECT.points > Judgement.GREAT.points)
        assertTrue(Judgement.GREAT.points > Judgement.GOOD.points)
        assertEquals(0, Judgement.MISS.points)
    }

    @Test
    fun `the combo bonus needs a real streak`() {
        assertEquals(0, comboBonus(0))
        assertEquals(0, comboBonus(9))
        assertEquals(1, comboBonus(10))
        assertEquals(1, comboBonus(40))
    }

    @Test
    fun `a perfect round is worth about what the payout curve expects`() {
        // MiniGame.RHYTHM's coefficients assume a strong round is around 110.
        // If the chart or the scoring drifts, that assumption goes stale
        // silently and the rhythm game quietly becomes the best-paid one.
        val beats = beatMap(seed = 5, durationSeconds = 28)
        val flawless = beats.indices.sumOf { i ->
            Judgement.PERFECT.points + comboBonus(i + 1)
        }

        assertTrue("a flawless round scores $flawless", flawless in 120..260)
    }

    // --- memory --------------------------------------------------------------

    @Test
    fun `sequences grow and then stop growing`() {
        assertEquals(3, sequenceLength(0))
        assertEquals(4, sequenceLength(1))
        assertEquals(8, sequenceLength(5))
        assertEquals("a nine-pad sequence is unwatchable", 8, sequenceLength(40))
    }

    @Test
    fun `the show speeds up but never becomes a flicker`() {
        assertTrue(showMillis(8) < showMillis(3))
        assertTrue("too fast to see", showMillis(80) >= 230)
    }

    @Test
    fun `a longer sequence is worth more`() {
        assertTrue(sequenceScore(sequenceLength(4)) > sequenceScore(sequenceLength(0)))
    }

    @Test
    fun `the show phase fits inside a round`() {
        // Worst case: the longest sequence, shown at its own pace, plus the
        // lead-in. If this ever exceeded the round the last sequence could not
        // be answered at all.
        val length = sequenceLength(40)
        val show = 520 + length * (showMillis(length) + 170)

        assertTrue("the show alone takes ${show}ms", show < 30_000 / 2)
    }
}
