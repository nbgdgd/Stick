package com.trialtracker.app

import android.content.Context
import com.trialtracker.app.data.DealsRepository
import com.trialtracker.app.data.PackageScanner
import com.trialtracker.app.data.SettingsRepository
import com.trialtracker.app.data.local.TrialDatabase
import com.trialtracker.app.data.remote.CatalogRemoteSource
import com.trialtracker.app.data.remote.DealFeedSource
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

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

    /** One connection pool for the catalog, the deal feeds and Coil's icon loads. */
    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
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
                remote = CatalogRemoteSource(json = json, client = httpClient),
                feeds = DealFeedSource(httpClient),
            ).also { repository = it }
        }
    }
}
