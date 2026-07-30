package com.vpet.waifu

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vpet.waifu.data.PetRepository
import com.vpet.waifu.data.WallClock
import com.vpet.waifu.data.db.PetDatabase
import com.vpet.waifu.domain.PetSimulation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/** A clock the tests move by hand. */
class FakeClock(var nowMillis: Long = 1_700_000_000_000L) : WallClock {
    override fun nowMillis(): Long = nowMillis

    fun advanceMinutes(minutes: Long) {
        nowMillis += minutes * 60_000L
    }
}

/** An in-memory save file wired to a controllable clock. */
class TestPet(val clock: FakeClock = FakeClock()) {
    val database: PetDatabase = Room
        .inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            PetDatabase::class.java,
        )
        .allowMainThreadQueries()
        .build()

    val repository = PetRepository(database.petStateDao(), PetSimulation(), clock)

    fun close() = database.close()

    /**
     * Works shifts until she can afford [target].
     *
     * She starts with pocket money on purpose, so anything past a cheap snack
     * has to be earned — a test that skips that is testing a state the game
     * never reaches.
     */
    suspend fun earnAtLeast(target: Int) {
        val cafe = com.vpet.waifu.domain.Occupations.WORK.first()
        repeat(MAX_SHIFTS) {
            if (repository.peek().progress.money >= target) return
            repository.feed()
            repository.startSleep()
            clock.advanceMinutes(60)
            repository.tick()
            repository.startOccupation(cafe)
            clock.advanceMinutes(cafe.durationMinutes + 1L)
            repository.tick()
        }
        error("could not earn $target — got ${repository.peek().progress.money}")
    }

    private companion object {
        const val MAX_SHIFTS = 20
    }
}

/**
 * Waits for a condition on real time.
 *
 * These tests span a Room write, its Flow emission and a collector on another
 * dispatcher, so virtual time would report success before any of it happened.
 */
suspend fun waitUntil(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
    withTimeout(timeoutMillis) {
        while (!condition()) delay(5)
    }
}
