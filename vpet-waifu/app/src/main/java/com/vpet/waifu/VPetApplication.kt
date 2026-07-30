package com.vpet.waifu

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.vpet.waifu.di.ApplicationScope
import com.vpet.waifu.feedback.PetMusic
import com.vpet.waifu.feedback.PetSounds
import com.vpet.waifu.notify.NotificationSync
import com.vpet.waifu.widget.PetWidget
import com.vpet.waifu.widget.WidgetSync
import com.vpet.waifu.work.PetTickWorker
import kotlinx.coroutines.CoroutineScope
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VPetApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var widgetSync: WidgetSync
    @Inject lateinit var notificationSync: NotificationSync
    @Inject lateinit var sounds: PetSounds
    @Inject lateinit var music: PetMusic
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    /**
     * WorkManager is initialised on demand (the manifest removes its startup
     * provider) so that the tick worker can be constructed by Hilt and receive
     * the repository.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        PetTickWorker.ensureScheduled(this)
        // One place decides when the widget redraws, and one decides when the
        // player is told something: whenever the save file changes, whichever
        // surface changed it.
        widgetSync.start(applicationScope)
        notificationSync.start(applicationScope)
        sounds.start(applicationScope)
        music.start(applicationScope)
    }

    /**
     * The widget's cached frames are about a megabyte of PNG held purely to
     * avoid re-encoding them. That is a good trade while there is memory to
     * spare and a bad one the moment there is not, so it goes first.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) PetWidget.releaseArt()
    }
}
