package com.trialtracker.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.trialtracker.app.work.CatalogSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TrialTrackerApp : Application(), Configuration.Provider, ImageLoaderFactory {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        scope.launch {
            ServiceLocator.deals(this@TrialTrackerApp).seedFromAssetsIfEmpty()
            val settings = ServiceLocator.settings(this@TrialTrackerApp).settings.first()
            if (settings.notificationsEnabled) {
                CatalogSyncWorker.schedule(this@TrialTrackerApp, settings.checkIntervalHours)
            }
        }
    }

    /**
     * App icons are fetched from Play's CDN for apps that are not installed. They
     * never change, so they are cached on disk and reuse the shared HTTP client.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { ServiceLocator.httpClient }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("app_icons"))
                .maxSizeBytes(24L * 1024 * 1024)
                .build()
        }
        .crossfade(true)
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_DEALS,
            "Новые предложения",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Уведомления о новых пробных подписках и акциях для ваших приложений"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_DEALS = "deals"
    }
}
