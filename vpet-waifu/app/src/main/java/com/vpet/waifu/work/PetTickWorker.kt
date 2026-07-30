package com.vpet.waifu.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vpet.waifu.data.PetRepository
import com.vpet.waifu.widget.WidgetRefresher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Keeps the save file honest while nothing else is running.
 *
 * The worker does not itself apply one minute of decay — it asks the repository
 * to advance the world to *now*, and [com.vpet.waifu.domain.PetSimulation]
 * works out how many minutes are owed from the stored timestamp. That is what
 * lets a 15-minute job deliver a per-minute simulation.
 */
@HiltWorker
class PetTickWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: PetRepository,
    private val widgets: WidgetRefresher,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        repository.tick()
        // Unconditionally, not by way of the write.
        //
        // The widget renders the world advanced to now, so what it should show
        // drifts whether or not anything is being written down: she gets
        // hungry, she wakes up by herself, her shift ends. Hanging the redraw
        // off a database write meant that with the app closed — the only time
        // this worker runs at all — a tick that changed no stored value, or
        // changed one the picture does not depend on, left the widget frozen
        // until something else happened to write. This is the heartbeat.
        widgets.refresh()
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "pet-tick"

        /**
         * 15 minutes is WorkManager's hard floor for periodic work, so this is a
         * safety net rather than the game clock: while the overlay is up its own
         * 60-second ticker drives the pet, and reopening the app pays off
         * whatever is owed immediately. The job exists so that a pet left alone
         * with the bubble off still has a fresh, correct state whenever
         * something looks at it.
         */
        private const val INTERVAL_MINUTES = 15L

        fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<PetTickWorker>(
                INTERVAL_MINUTES, TimeUnit.MINUTES,
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
