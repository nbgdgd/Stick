package com.vpet.waifu.data

import com.vpet.waifu.data.db.PetStateDao
import com.vpet.waifu.data.db.PetStateEntity
import com.vpet.waifu.data.db.toEntity
import com.vpet.waifu.data.db.toSnapshot
import com.vpet.waifu.domain.Chore
import com.vpet.waifu.domain.Focus
import com.vpet.waifu.domain.MiniGame
import com.vpet.waifu.domain.Occupation
import com.vpet.waifu.domain.PetSimulation
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.ShopItem
import com.vpet.waifu.domain.Upgrade
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place the pet's state is read and written.
 *
 * Four different callers mutate the same save file — the UI, the overlay
 * service's ticker, the WorkManager job and the widget — potentially at the
 * same instant, so every mutation is a read-modify-write serialised by a
 * [Mutex]. Without it, a tick landing between a feed's read and its write would
 * silently eat the food.
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
     * get a fresh value because something is always ticking; use [snapshotNow]
     * when an exact up-to-date value is needed on the spot.
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

    suspend fun toggleSleep(): PetSnapshot = mutate { current, now ->
        if (current.isSleeping) simulation.wake(current, now) else simulation.startSleep(current, now)
    }

    suspend fun startOccupation(occupation: Occupation): PetSnapshot = mutate { current, now ->
        simulation.startOccupation(current, occupation, now)
    }

    suspend fun cancelOccupation(): PetSnapshot = mutate(simulation::cancelOccupation)

    suspend fun buy(item: ShopItem): PetSnapshot = mutate { current, now ->
        simulation.buy(current, item, now)
    }

    suspend fun buyUpgrade(upgrade: Upgrade): PetSnapshot = mutate { current, now ->
        simulation.buyUpgrade(current, upgrade, now)
    }

    suspend fun wear(upgradeId: String): PetSnapshot = mutate { current, now ->
        simulation.wear(current, upgradeId, now)
    }

    suspend fun applyTheme(upgradeId: String): PetSnapshot = mutate { current, now ->
        simulation.applyTheme(current, upgradeId, now)
    }

    /** The once-a-day check-in. Called when the app comes to the foreground. */
    suspend fun claimDaily(): PetSnapshot = mutate(simulation::claimDaily)

    /** Clears the check-in card once the player has seen what it paid. */
    suspend fun acknowledgeDaily(): PetSnapshot = mutate { current, _ ->
        simulation.acknowledgeDaily(current)
    }

    suspend fun acknowledgeEvent(): PetSnapshot = mutate { current, now ->
        simulation.acknowledgeEvent(current, now)
    }

    /** The day's odd jobs — see [com.vpet.waifu.domain.Quests] and [Chores]. */
    suspend fun claimQuest(index: Int): PetSnapshot = mutate { current, now ->
        simulation.claimQuest(current, index, now)
    }

    suspend fun doChore(chore: Chore): PetSnapshot = mutate { current, now ->
        simulation.doChore(current, chore, now)
    }

    suspend fun claimFind(): PetSnapshot = mutate(simulation::claimFind)

    suspend fun stake(amount: Int): PetSnapshot = mutate { current, now ->
        simulation.stake(current, amount, now)
    }

    suspend fun startPlaying(): PetSnapshot = mutate(simulation::startPlaying)

    suspend fun finishPlaying(score: Int, game: MiniGame = MiniGame.CATCH): PetSnapshot =
        mutate { current, now -> simulation.finishPlaying(current, score, now, game) }

    /** Clears the "she finished her shift" card once the player has seen it. */
    suspend fun acknowledgeOutcome(): PetSnapshot = mutate { current, _ ->
        simulation.acknowledgeOutcome(current)
    }

    /** Commits her to a path. Once, and permanently — see [com.vpet.waifu.domain.Focus]. */
    suspend fun chooseFocus(focus: Focus): PetSnapshot = mutate { current, now ->
        simulation.chooseFocus(current, focus, now)
    }

    /** Marks the newest story chapter as read. */
    suspend fun acknowledgeStory(): PetSnapshot = mutate { current, _ ->
        simulation.acknowledgeStory(current)
    }

    /** Current state, decay included, without waiting for the next tick. */
    suspend fun snapshotNow(): PetSnapshot = tick()

    /**
     * The state as it is right now, advanced in memory but **not** persisted.
     *
     * Read-only surfaces use this so that redrawing them is never a game
     * action. It also breaks a feedback loop: the widget refreshes whenever the
     * save file changes, so a widget redraw that wrote to the save file would
     * schedule another redraw.
     */
    suspend fun peek(): PetSnapshot {
        val now = clock.nowMillis()
        val stored = dao.load()?.toSnapshot() ?: PetSnapshot.initial(now)
        return simulation.advanceTo(stored, now)
    }

    // --- the save file, as a file --------------------------------------------

    /**
     * Writes the whole save to [out] — see [SaveCodec].
     *
     * The world is advanced first, so the file is the pet as she is now rather
     * than as she was at the last write; exporting mid-shift and restoring
     * tomorrow would otherwise resurrect a session that ended hours ago.
     */
    suspend fun exportSave(out: OutputStream): Result<Unit> = writeLock.withLock {
        val now = clock.nowMillis()
        val current = simulation.advanceTo(
            dao.load()?.toSnapshot() ?: PetSnapshot.initial(now),
            now,
        )
        val entity = current.toEntity()
        dao.save(entity)
        SaveCodec.exportTo(entity, out)
    }

    /**
     * Replaces the save with the one in [input].
     *
     * All or nothing: a file that fails to parse leaves the row untouched, so
     * a mis-picked file can never be the thing that ends a pet. The caller is
     * expected to have asked first — this cannot be undone.
     */
    suspend fun importSave(input: InputStream): Result<PetSnapshot> = writeLock.withLock {
        SaveCodec.importFrom(input).map { imported ->
            val restored = imported.copy(id = PetStateEntity.SINGLETON_ID)
            dao.save(restored)
            restored.toSnapshot()
        }
    }

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
