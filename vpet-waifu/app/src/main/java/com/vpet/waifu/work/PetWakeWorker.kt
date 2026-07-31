package com.vpet.waifu.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vpet.waifu.data.PetRepository
import com.vpet.waifu.widget.WidgetRefresher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * A single wake-up, aimed at the exact moment the widget's picture goes stale.
 *
 * The heartbeat in [PetTickWorker] runs on WorkManager's floor of fifteen
 * minutes, which is fine for keeping the save file honest and useless for
 * keeping a picture honest: a shift that ends at 14:03 left the widget showing
 * her at the desk until 14:15. The simulation can say when the picture is next
 * due to change — a shift ending, her waking by herself, her getting hungry —
 * so this is booked for that instant instead of hoping the heartbeat is close.
 *
 * It is not exact and does not need to be. WorkManager will slide it under Doze
 * and the heartbeat still backs it up; the difference between "usually within a
 * minute" and "up to fifteen" is the entire complaint.
 */
@HiltWorker
class PetWakeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: PetRepository,
    private val widgets: WidgetRefresher,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        repository.tick()
        widgets.refresh()
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "pet-wake"

        /**
         * Books the next wake-up, replacing any already booked.
         *
         * A floor of a minute, because the app is writing every three seconds
         * while it is open and a wake-up booked for two seconds' time is just a
         * way of asking WorkManager to do the job the foreground already does.
         */
        fun scheduleAt(context: Context, delayMillis: Long) {
            val delay = delayMillis.coerceAtLeast(MINIMUM_DELAY_MILLIS)
            val request = OneTimeWorkRequestBuilder<PetWakeWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        private const val MINIMUM_DELAY_MILLIS = 60_000L
    }
}
