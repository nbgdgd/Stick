package com.vpet.waifu.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MINUTE = PetSimulation.MS_PER_MINUTE
private const val T0 = 1_700_373_600_000L

/**
 * Her diary: everything notable that happens while nobody is looking gets a
 * line, because "what did I miss?" is a question the game must answer itself.
 */
class JournalTest {

    private val sim = PetSimulation()

    private fun snapshot(money: Int = 500) = PetSnapshot(
        stats = PetStats(80f, 80f, 70f),
        progress = PetProgress(money, 0),
        lastTickAt = T0,
        lastInteractionAt = T0,
        passiveSince = T0,
        bornAt = T0,
    )

    @Test
    fun `a finished shift writes what it paid`() {
        val cafe = Occupations.WORK.first()
        var s = sim.startOccupation(snapshot(), cafe, T0)
        s = sim.advanceTo(s, T0 + (cafe.durationMinutes + 1) * MINUTE)

        val entry = s.journal.last { it.kind == JournalKind.SHIFT_DONE }
        assertEquals(cafe.id, entry.detail)
        assertTrue("pay missing from the diary", entry.amount > 0)
    }

    @Test
    fun `falling ill and recovering are both diary lines`() {
        var s = snapshot().copy(stats = PetStats(0.5f, 80f, 70f))
        var t = T0
        repeat((sim.tuning.sickAfterRunDownMinutes + sim.tuning.sickRecoveryMinutes + 120).toInt()) {
            t += MINUTE
            s = sim.advanceTo(s, t)
        }

        assertTrue(s.journal.any { it.kind == JournalKind.FELL_SICK })
        assertTrue(s.journal.any { it.kind == JournalKind.RECOVERED })
    }

    @Test
    fun `an ignored wish leaves a line, not just a mood dip`() {
        // Full care while searching: an uncared-for pet falls ill and an ill
        // pet stops wishing, so the search would only ever time out.
        var s = snapshot()
        var t = T0
        while (s.request == null) {
            check(t < T0 + 4 * 24 * 60 * MINUTE) { "no wish in four days" }
            t += 60 * MINUTE
            s = sim.advanceTo(s, t)
            if (s.request != null) break
            if (s.isSleeping && s.stats.energy > 90f) s = sim.wake(s, t)
            if (s.stats.hunger < 50f) s = sim.feed(s, t)
            if (s.stats.energy < 40f) s = sim.startSleep(s, t)
        }
        val until = s.request!!.until
        s = sim.advanceTo(s, until + MINUTE)

        assertTrue(s.journal.any { it.kind == JournalKind.WISH_EXPIRED })
    }

    @Test
    fun `the diary is a recap, not a chronicle`() {
        var journal = emptyList<JournalEntry>()
        repeat(100) {
            journal = Journal.append(journal, JournalEntry(JournalKind.EVENT, at = T0 + it))
        }

        assertEquals(Journal.MAX_ENTRIES, journal.size)
        // …and it keeps the newest, not the oldest.
        assertEquals(T0 + 99L, journal.last().at)
    }
}
