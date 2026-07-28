package com.trialtracker.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Configuration
import com.trialtracker.app.work.CatalogSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TrialTrackerApp : Application(), Configuration.Provider {

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
