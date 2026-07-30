package com.vpet.waifu

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.vpet.waifu.work.PetTickWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VPetApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

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
    }
}
