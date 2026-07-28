package com.trialtracker.app.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.trialtracker.app.MainActivity
import com.trialtracker.app.R
import com.trialtracker.app.ServiceLocator
import com.trialtracker.app.TrialTrackerApp
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodically re-downloads the catalog and re-scans the device. When a deal shows
 * up for an app the user actually has installed, a local notification is posted.
 * Nothing is uploaded: the catalog request is a plain GET of a public JSON file.
 */
class CatalogSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = ServiceLocator.deals(applicationContext)
        val settings = ServiceLocator.settings(applicationContext).settings.first()
        val fresh = repo.refresh().getOrElse { return Result.retry() }

        if (settings.notificationsEnabled && fresh.isNotEmpty()) {
            notify(fresh.size, fresh.first().appName)
        }
        return Result.success()
    }

    private fun notify(count: Int, firstAppName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val text = if (count == 1) {
            "Новое предложение для $firstAppName"
        } else {
            "Найдено $count новых предложений для ваших приложений"
        }

        val notification = NotificationCompat.Builder(applicationContext, TrialTrackerApp.CHANNEL_DEALS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Новые пробные подписки")
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val WORK_NAME = "catalog-sync"
        private const val NOTIFICATION_ID = 1001

        fun schedule(context: Context, intervalHours: Int) {
            val request = PeriodicWorkRequestBuilder<CatalogSyncWorker>(
                intervalHours.coerceAtLeast(1).toLong(),
                TimeUnit.HOURS,
            ).setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
