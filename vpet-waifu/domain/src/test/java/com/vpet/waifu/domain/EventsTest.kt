package com.vpet.waifu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DAY = Events.MILLIS_PER_DAY
private const val MIN = PetSimulation.MS_PER_MINUTE

/** A day with no event, and one with a known one. */
private val QUIET_DAY = 1_700_373_600_000L

/**
 * Something happening.
 *
 * The hard requirement is not variety but *agreement*: four drivers advance
 * this world and any of them can be the first to reach a given day. An event
 * drawn from a random source would fire twice, or differ between the widget and
 * the app, depending on who looked first.
 */
class EventsTest {

    private val sim = PetSimulation()

    private fun at(millis: Long) = PetSnapshot(
        stats = PetStats(80f, 80f, 70f),
        lastTickAt = millis,
        lastInteractionAt = millis,
    )

    /** The first day at or after [from] that carries [kind]. */
    private fun dayWith(kind: EventKind, from: Long = Events.dayOf(QUIET_DAY)): Long =
        generateSequence(from) { it + 1 }.first { Events.forDay(it) == kind }

    @Test
    fun `a day always produces the same event, however often it is asked`() {
        val day = Events.dayOf(QUIET_DAY) + 3

        val answers = List(50) { Events.forDay(day) }.distinct()

        assertEquals(1, answers.size)
    }

    @Test
    fun `different days produce different events`() {
        val start = Events.dayOf(QUIET_DAY)
        val seen = (0 until 400).mapNotNull { Events.forDay(start + it) }.toSet()

        assertEquals("every kind should turn up inside a year", EventKind.entries.toSet(), seen)
    }

    @Test
    fun `most days are ordinary`() {
        val start = Events.dayOf(QUIET_DAY)
        val eventful = (0 until 1_000).count { Events.forDay(start + it) != null }

        // Often enough to be worth checking in for, rare enough to still feel
        // like something happened.
        assertTrue("$eventful days in 1000", eventful in 200..450)
    }

    @Test
    fun `the day's event lands on the pet and stays for the day`() {
        val day = dayWith(EventKind.LUCKY_DAY)
        val morning = day * DAY + 6 * 60 * MIN

        val after = sim.advanceTo(at(morning - 30 * MIN), morning)

        assertNotNull(after.event)
        assertEquals(EventKind.LUCKY_DAY, after.event?.kind)
        // Still the same event an hour later — it does not re-roll every tick.
        assertEquals(after.event, sim.advanceTo(after, morning + 60 * MIN).event)
    }

    @Test
    fun `the event clears when the day does`() {
        val day = dayWith(EventKind.LUCKY_DAY)
        val morning = day * DAY + 6 * 60 * MIN
        val lucky = sim.advanceTo(at(morning - 30 * MIN), morning)
        assertNotNull(lucky.event)

        val nextQuiet = generateSequence(day + 1) { it + 1 }.first { Events.forDay(it) == null }
        val later = sim.advanceTo(lucky, nextQuiet * DAY + 6 * 60 * MIN)

        assertNull("yesterday's event should not still be running", later.event)
    }

    @Test
    fun `a lucky day really does pay more`() {
        val cafe = Occupations.byId("cafe")!!
        val luckyDay = dayWith(EventKind.LUCKY_DAY)
        val quietDay = generateSequence(luckyDay + 1) { it + 1 }.first { Events.forDay(it) == null }

        fun earn(day: Long): Int {
            val start = day * DAY + 6 * 60 * MIN
            val begun = sim.advanceTo(at(start - MIN), start)
            return sim.advanceTo(
                sim.startOccupation(begun, cafe, start),
                start + (cafe.durationMinutes + 2) * MIN,
            ).progress.money
        }

        assertTrue("lucky ${earn(luckyDay)} vs quiet ${earn(quietDay)}", earn(luckyDay) > earn(quietDay))
    }

    @Test
    fun `a cold makes her tire faster`() {
        val coldDay = dayWith(EventKind.COLD)
        val quietDay = generateSequence(coldDay + 1) { it + 1 }.first { Events.forDay(it) == null }

        fun energyAfterTwoHours(day: Long): Float {
            val start = day * DAY + 6 * 60 * MIN
            val begun = sim.advanceTo(at(start - MIN), start).copy(stats = PetStats(80f, 100f, 70f))
            return sim.advanceTo(begun, start + 120 * MIN).stats.energy
        }

        assertTrue(energyAfterTwoHours(coldDay) < energyAfterTwoHours(quietDay))
    }

    @Test
    fun `the letter pays once, not every tick`() {
        val day = dayWith(EventKind.LETTER)
        val morning = day * DAY + 6 * 60 * MIN
        val start = at(morning - MIN).copy(progress = PetProgress(0, 0))

        val arrived = sim.advanceTo(start, morning)
        assertEquals(EventKind.LETTER.instantMoney(), arrived.progress.money)

        val hoursLater = sim.advanceTo(arrived, morning + 5 * 60 * MIN)
        assertEquals("it paid again", arrived.progress.money, hoursLater.progress.money)
    }

    @Test
    fun `an event can be read and then stops being news`() {
        val day = dayWith(EventKind.LUCKY_DAY)
        val morning = day * DAY + 6 * 60 * MIN
        val lucky = sim.advanceTo(at(morning - MIN), morning)

        assertFalse(lucky.event!!.acknowledged)
        val read = sim.acknowledgeEvent(lucky, morning)
        assertTrue(read.event!!.acknowledged)

        // Reading it does not cancel what it does.
        assertEquals(lucky.modifiers(), read.modifiers())
    }

    @Test
    fun `catching up across several days lands on today's event, not last week's`() {
        val start = QUIET_DAY
        val eightDaysLater = start + 8 * DAY

        val after = sim.advanceTo(at(start), eightDaysLater)

        assertEquals(Events.forDay(Events.dayOf(eightDaysLater)), after.event?.kind)
    }
}
