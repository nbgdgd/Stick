package com.trialtracker.app

import android.content.Context
import com.trialtracker.app.data.DealsRepository
import com.trialtracker.app.data.PackageScanner
import com.trialtracker.app.data.SettingsRepository
import com.trialtracker.app.data.local.TrialDatabase
import com.trialtracker.app.data.remote.CatalogRemoteSource
import kotlinx.serialization.json.Json

/**
 * Hand-rolled dependency graph. The object count here is small enough that a DI
 * framework would add build time and indirection without buying anything.
 */
object ServiceLocator {

    val json: Json by lazy {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }

    @Volatile
    private var repository: DealsRepository? = null

    @Volatile
    private var settingsRepo: SettingsRepository? = null

    fun settings(context: Context): SettingsRepository = settingsRepo ?: synchronized(this) {
        settingsRepo ?: SettingsRepository(context.applicationContext).also { settingsRepo = it }
    }

    fun deals(context: Context): DealsRepository = repository ?: synchronized(this) {
        repository ?: run {
            val app = context.applicationContext
            DealsRepository(
                context = app,
                db = TrialDatabase.get(app),
                scanner = PackageScanner(app),
                settings = settings(app),
                json = json,
                remote = CatalogRemoteSource(json = json),
            ).also { repository = it }
        }
    }
}
