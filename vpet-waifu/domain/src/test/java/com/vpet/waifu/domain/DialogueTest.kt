package com.vpet.waifu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val M = PetSimulation.MS_PER_MINUTE
private const val NOW = 1_700_373_600_000L

/**
 * What she has to say.
 *
 * The ordering is the whole design: she has one line at a time, so whichever
 * situation wins has to be the one worth speaking about. A pet remarking on the
 * weather while she is starving is worse than one that says nothing.
 */
class DialogueTest {

    private val tuning = PetTuning()

    private fun pet(
        hunger: Float = 80f,
        energy: Float = 80f,
        mood: Float = 60f,
        activity: PetActivity = PetActivity.AWAKE,
        lastInteractionAt: Long = NOW,
    ) = PetSnapshot(
        stats = PetStats(hunger, energy, mood),
        activity = activity,
        lastTickAt = NOW,
        lastInteractionAt = lastInteractionAt,
    )

    private fun topic(snapshot: PetSnapshot, now: Long = NOW) =
        Dialogue.lineFor(snapshot, now, tuning).topic

    @Test
    fun `what she is doing beats how she feels`() {
        val starvingAtWork = pet(hunger = 2f, activity = PetActivity.WORKING)
            .copy(session = ActivitySession("cafe", NOW, NOW + 30 * M))

        assertEquals(DialogueTopic.WORKING, topic(starvingAtWork))
        assertEquals(DialogueTopic.SLEEPING, topic(pet(hunger = 2f, activity = PetActivity.SLEEPING)))
    }

    @Test
    fun `once she has been paid she mentions it`() {
        val paid = pet(activity = PetActivity.WORKING)
            .copy(session = ActivitySession("cafe", NOW, NOW + 30 * M, paidOut = 12))

        assertEquals(DialogueTopic.PAYDAY, topic(paid))
    }

    @Test
    fun `starving outranks merely hungry`() {
        assertEquals(DialogueTopic.HUNGRY, topic(pet(hunger = tuning.hungryThreshold - 1f)))
        assertEquals(DialogueTopic.STARVING, topic(pet(hunger = 3f)))
    }

    @Test
    fun `hunger outranks being tired`() {
        val both = pet(hunger = 5f, energy = 5f)

        assertEquals(DialogueTopic.STARVING, topic(both))
        assertEquals(DialogueTopic.TIRED, topic(pet(energy = 5f)))
    }

    @Test
    fun `a reaction you just caused wins over an ambient mood`() {
        val justFed = pet(mood = 95f).copy(emote = Emote.EATING, emoteUntil = NOW + 3_000)

        assertEquals(DialogueTopic.JUST_FED, topic(justFed))
    }

    @Test
    fun `she notices the same meal over and over`() {
        val bored = pet()
            .copy(emote = Emote.EATING, emoteUntil = NOW + 3_000, lastMealId = "onigiri", repeatedMeals = 4)

        assertEquals(DialogueTopic.SAME_MEAL_AGAIN, topic(bored))
    }

    @Test
    fun `she notices you coming back`() {
        assertEquals(DialogueTopic.IDLE, topic(pet(lastInteractionAt = NOW - 10 * M)))
        assertEquals(DialogueTopic.WELCOME_BACK, topic(pet(lastInteractionAt = NOW - 3 * 60 * M)))
        assertEquals(DialogueTopic.MISSED_YOU, topic(pet(lastInteractionAt = NOW - 30L * 60 * M)))
    }

    /**
     * Half a day away is *always* a hungry pet — the drain guarantees it — so
     * ranking hunger above it meant the three MISSED_YOU lines could never be
     * reached by any state the simulation can actually produce.
     */
    @Test
    fun `after half a day away she says she missed you, not that she is hungry`() {
        val backToAStarvingPet = pet(hunger = 1f, energy = 5f, lastInteractionAt = NOW - 20L * 60 * M)

        assertEquals(DialogueTopic.MISSED_YOU, topic(backToAStarvingPet))
    }

    @Test
    fun `but a long absence never talks over being ill`() {
        val backToASickPet = pet(hunger = 1f, lastInteractionAt = NOW - 20L * 60 * M)
            .copy(sickSince = NOW - 60 * M)

        assertEquals(DialogueTopic.SICK, topic(backToASickPet))
    }

    @Test
    fun `an hour and a half away still loses to an empty stomach`() {
        // WELCOME_BACK stays *below* the needs: at ninety minutes she is only
        // peckish if she was already low, and "you're back!" over a starving
        // pet is the exact tone problem the ordering above exists to avoid.
        val peckish = pet(hunger = 5f, lastInteractionAt = NOW - 3 * 60 * M)

        assertEquals(DialogueTopic.STARVING, topic(peckish))
    }

    @Test
    fun `an unread event is worth mentioning`() {
        val withEvent = pet().copy(event = PetEvent(EventKind.LETTER, Events.dayOf(NOW)))

        assertEquals(DialogueTopic.EVENT, topic(withEvent))
        // Once read, she goes back to normal conversation.
        assertNotEquals(
            DialogueTopic.EVENT,
            topic(withEvent.copy(event = withEvent.event!!.copy(seenAt = NOW))),
        )
    }

    @Test
    fun `a contented pet says so`() {
        assertEquals(DialogueTopic.CONTENT, topic(pet(hunger = 95f, energy = 95f, mood = 95f)))
    }

    @Test
    fun `the line is stable while nothing changes`() {
        val idle = pet()
        val first = Dialogue.lineFor(idle, NOW, tuning)

        // Same minute, same situation: the same words, not a slot machine
        // re-rolling on every frame.
        repeat(20) { assertEquals(first, Dialogue.lineFor(idle, NOW + it * 100L, tuning)) }
    }

    @Test
    fun `but she does not repeat herself forever`() {
        val idle = pet()
        val variants = (0 until 200)
            .map { Dialogue.lineFor(idle, NOW + it * M, tuning).variant }
            .toSet()

        assertEquals((0 until Dialogue.VARIANTS).toSet(), variants)
    }

    @Test
    fun `every topic has a variant inside the declared range`() {
        val lines = (0 until 500).map { Dialogue.lineFor(pet(), NOW + it * M, tuning) }

        assertTrue(lines.all { it.variant in 0 until Dialogue.VARIANTS })
    }
}
