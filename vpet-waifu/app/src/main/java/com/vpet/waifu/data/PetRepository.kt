package com.vpet.waifu.data

import com.vpet.waifu.data.db.PetStateDao
import com.vpet.waifu.data.db.toEntity
import com.vpet.waifu.data.db.toSnapshot
import com.vpet.waifu.domain.PetSimulation
import com.vpet.waifu.domain.PetSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place the pet's state is read and written.
 *
 * Three different callers mutate the same save file — the UI, the overlay
 * service's 60-second ticker and the WorkManager job — potentially at the same
 * instant, so every mutation is a read-modify-write serialised by a [Mutex].
 * Without it, a tick landing between a feed's read and its write would silently
 * eat the food.
 */
@Singleton
class PetRepository @Inject constructor(
    private val dao: PetStateDao,
    private val simulation: PetSimulation,
    private val clock: WallClock,
) {
    private val writeLock = Mutex()

    /**
     * The stored state, as written. It is *not* advanced to "now" — observers
     * get a fresh value because something is always ticking; see
     * [snapshotNow] when an exact up-to-date value is needed on the spot.
     */
    val snapshot: Flow<PetSnapshot> = dao.observe().map { stored ->
        stored?.toSnapshot() ?: PetSnapshot.initial(clock.nowMillis())
    }

    /** Advances the world to now and persists the result. */
    suspend fun tick(): PetSnapshot = mutate(simulation::advanceTo)

    suspend fun feed(): PetSnapshot = mutate(simulation::feed)

    suspend fun pet(): PetSnapshot = mutate(simulation::pet)

    suspend fun startSleep(): PetSnapshot = mutate(simulation::startSleep)

    suspend fun wake(): PetSnapshot = mutate(simulation::wake)

    /** Current state, decay included, without waiting for the next tick. */
    suspend fun snapshotNow(): PetSnapshot = tick()

    private suspend fun mutate(action: (PetSnapshot, Long) -> PetSnapshot): PetSnapshot =
        writeLock.withLock {
            val now = clock.nowMillis()
            val stored = dao.load()
            val current = stored?.toSnapshot() ?: PetSnapshot.initial(now)
            val next = action(current, now)
            // First run has to create the row; after that, skip no-op writes so
            // idle ticks don't wake every Flow collector once a second.
            if (stored == null || next != current) dao.save(next.toEntity())
            next
        }
}
