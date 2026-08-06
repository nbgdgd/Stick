package com.vpet.waifu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MINUTE = PetSimulation.MS_PER_MINUTE
private const val T0 = 1_700_373_600_000L

/**
 * The day's small moment.
 *
 * The rule worth a test file of its own: **neither answer is wrong.** A
 * two-option prompt with a hidden correct choice is how a game teaches people
 * to distrust its prompts, and this is the one place the game asks a question.
 * So the pairs have to be close in total worth and genuinely different in
 * shape — which is a property of the numbers, and therefore something a test
 * can hold rather than a promise in a comment.
 */
class ScenesTest {

    private val sim = PetSimulation()

    private fun snapshot(money: Int = 2_000, exp: Int = 5_000) = PetSnapshot(
        stats = PetStats(80f, 80f, 60f),
        progress = PetProgress(money = money, exp = exp),
        lastTickAt = T0,
        lastInteractionAt = T0,
        passiveSince = T0,
        passiveDay = Events.dayOf(T0),
        passivePaidToday = 1_000_000,
    )

    /**
     * One number for an outcome, in the same currency the rest of the game
     * compares things in — see [AdvisorWeights], which is where money and EXP
     * are already weighted against each other.
     */
    private fun worth(outcome: SceneOutcome): Float {
        val w = AdvisorWeights()
        return outcome.money / w.moneyPerPoint +
            outcome.exp / w.expPerPoint +
            outcome.mood * 0.045f +
            outcome.energy * 0.04f +
            outcome.bond * 0.20f
    }

    /**
     * How far apart the two answers may be.
     *
     * The weaker reply is never worth less than sixty percent of the stronger,
     * across the whole level curve. That number is the design, not a number the
     * design happened to land on: squeezing it much tighter forces the two
     * replies to pay almost the same things, and a choice between two identical
     * outcomes is not a choice. Sixty percent is close enough that picking by
     * instinct never costs you much, and far enough apart that picking
     * deliberately is worth doing.
     */
    private val closeEnough = 0.60f

    @Test
    fun `neither answer is ever the wrong one`() {
        SceneKind.entries.forEach { scene ->
            // The whole curve, not three points on it: the failure this test
            // was written for was a pair that balanced in the middle and came
            // apart at both ends.
            listOf(1, 5, 10, 15, 20, 25, Progression.MAX_LEVEL).forEach { level ->
                val first = worth(Scenes.outcomeOf(scene, SceneOption.FIRST, level))
                val second = worth(Scenes.outcomeOf(scene, SceneOption.SECOND, level))
                val ratio = minOf(first, second) / maxOf(first, second)
                assertTrue(
                    "$scene at level $level: $first vs $second (weaker is ${(ratio * 100).toInt()}%)",
                    ratio >= closeEnough,
                )
                // Both have to be worth *something*, or "no wrong answer" is
                // true only in the sense that one of them does nothing.
                assertTrue("$scene FIRST pays nothing", first > 0.15f)
                assertTrue("$scene SECOND pays nothing", second > 0.15f)
            }
        }
    }

    @Test
    fun `the two answers are different in kind, not just in size`() {
        SceneKind.entries.forEach { scene ->
            val a = Scenes.outcomeOf(scene, SceneOption.FIRST, 10)
            val b = Scenes.outcomeOf(scene, SceneOption.SECOND, 10)
            // One of them leans on money and EXP, the other on how she feels.
            val aMaterial = a.money / 90f + a.exp / 55f
            val bMaterial = b.money / 90f + b.exp / 55f
            val aPersonal = a.mood * 0.045f + a.energy * 0.04f + a.bond * 0.20f
            val bPersonal = b.mood * 0.045f + b.energy * 0.04f + b.bond * 0.20f
            assertTrue(
                "$scene: both answers pull the same way",
                (aMaterial > bMaterial) != (aPersonal > bPersonal),
            )
        }
    }

    @Test
    fun `a scene is asked once a day and then closed`() {
        val fresh = sim.advanceTo(snapshot(), T0)
        assertNotNull("a day should carry a scene", fresh.sceneToday)

        val answered = sim.answerScene(fresh, SceneOption.FIRST, T0)
        assertNull("answering closes it", answered.sceneToday)

        // A second answer the same day changes nothing.
        val again = sim.answerScene(answered, SceneOption.SECOND, T0)
        assertEquals(answered.progress.money, again.progress.money)
        assertEquals(answered.stats.mood, again.stats.mood, 0.001f)

        // Tomorrow asks again.
        val tomorrow = T0 + 24 * 60 * MINUTE
        assertNotNull(sim.advanceTo(answered, tomorrow).sceneToday)
    }

    @Test
    fun `answering actually pays what it says it will`() {
        val fresh = sim.advanceTo(snapshot(), T0)
        val scene = fresh.sceneToday!!
        val expected = Scenes.outcomeOf(scene, SceneOption.SECOND, fresh.level)

        val after = sim.answerScene(fresh, SceneOption.SECOND, T0)

        assertEquals(fresh.progress.money + expected.money, after.progress.money)
        assertEquals(fresh.progress.exp + expected.exp, after.progress.exp)
        if (expected.bond > 0) assertTrue(after.bondPoints > fresh.bondPoints)
    }

    @Test
    fun `the scene is stable within a day and moves between days`() {
        val day = Events.dayOf(T0)
        assertEquals(Scenes.forDay(day), Scenes.forDay(day))
        // Over a fortnight it must not be the same scene every time, or the
        // "one per day" is one scene with a daily reset.
        val fortnight = (0 until 14).map { Scenes.forDay(day + it) }.toSet()
        assertTrue("only ${fortnight.size} distinct scenes in a fortnight", fortnight.size >= 3)
    }
}
