package com.vpet.waifu.domain

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * That the catalog is a set of choices rather than a ladder.
 *
 * Every tier used to pay strictly more per minute than the one before, which
 * made the newest unlock obsolete everything else — four jobs that were really
 * one job with a changing name. These tests fail the moment that creeps back.
 */
class OccupationBalanceTest {

    private val work = Occupations.WORK
    private val study = Occupations.STUDY

    @Test
    fun `no job pays the best rate and costs the least of everything`() {
        work.forEach { job ->
            val beatenOnRate = work.any { it != job && it.payPerHour > job.payPerHour }
            val beatenOnEnergy = work.any { it != job && it.energyPerMinute < job.energyPerMinute }
            val beatenOnMood = work.any { it != job && it.moodPerMinute < job.moodPerMinute }
            val beatenOnTime = work.any { it != job && it.durationMinutes < job.durationMinutes }

            assertTrue(
                "${job.id} is not worse than anything at anything — it dominates the list",
                beatenOnRate || beatenOnEnergy || beatenOnMood || beatenOnTime,
            )
        }
    }

    @Test
    fun `unlocking a job does not obsolete the one before it`() {
        // For each consecutive pair, the older one must still win at something.
        work.zipWithNext().forEach { (older, newer) ->
            val stillBest = older.payPerHour > newer.payPerHour ||
                older.energyPerMinute < newer.energyPerMinute ||
                older.moodPerMinute < newer.moodPerMinute ||
                older.durationMinutes < newer.durationMinutes

            assertTrue("${newer.id} makes ${older.id} pointless", stillBest)
        }
    }

    @Test
    fun `the hourly rate is not simply ordered by level`() {
        val byLevel = work.sortedBy { it.requiredLevel }.map { it.payPerHour }

        assertTrue(
            "pay per hour climbs monotonically with level: $byLevel",
            byLevel != byLevel.sorted(),
        )
    }

    @Test
    fun `the long jobs earn their length`() {
        // A three-hour shift has to be worth more in absolute terms than three
        // half-hour ones would be convenient, or nobody would ever start it.
        val longest = work.maxByOrNull { it.durationMinutes }!!
        val shortest = work.minByOrNull { it.durationMinutes }!!

        assertTrue(longest.payout > shortest.payout * 3)
    }

    @Test
    fun `every shift lifts her spirits, better jobs more`() {
        // Working is the idle loop the player watches; watching her mood climb
        // while the coins arrive is what makes it worth watching. The rate must
        // climb with the tier or the tiers stop meaning anything.
        work.forEach { assertTrue("${it.id} drains her mood", it.moodCost < 0f) }
        work.zipWithNext().forEach { (older, newer) ->
            assertTrue(
                "${newer.id} should lift mood faster per minute than ${older.id}",
                -newer.moodPerMinute > -older.moodPerMinute,
            )
        }
    }

    @Test
    fun `studying has the same shape`() {
        study.zipWithNext().forEach { (older, newer) ->
            val stillBest = older.payPerHour > newer.payPerHour ||
                older.energyPerMinute < newer.energyPerMinute ||
                older.moodPerMinute < newer.moodPerMinute

            assertTrue("${newer.id} makes ${older.id} pointless", stillBest)
        }
    }

    @Test
    fun `a shift costs roughly the mood it advertises`() {
        val sim = PetSimulation()
        val office = Occupations.byId("office")!!
        val start = 1_700_373_600_000L
        // Held at a stable target so the drift neither helps nor hurts: what is
        // left is the job's own cost.
        val even = PetSnapshot(
            stats = PetStats(60f, 60f, 60f),
            lastTickAt = start,
            lastInteractionAt = start,
        )

        val after = sim.advanceTo(
            sim.startOccupation(even, office, start),
            start + office.durationMinutes * PetSimulation.MS_PER_MINUTE,
        )

        val lost = 60f - after.stats.mood
        assertTrue("lost $lost of an advertised ${office.moodCost}", lost > office.moodCost / 2f)
    }
}
