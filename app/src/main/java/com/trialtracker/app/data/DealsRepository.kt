package com.trialtracker.app.data

import android.content.Context
import com.trialtracker.app.data.local.SeenDealEntity
import com.trialtracker.app.data.local.TrialDatabase
import com.trialtracker.app.data.local.toEntity
import com.trialtracker.app.data.model.Deal
import com.trialtracker.app.data.model.DealCatalog
import com.trialtracker.app.data.model.DealUi
import com.trialtracker.app.data.model.InstalledApp
import com.trialtracker.app.data.local.InstalledAppEntity
import com.trialtracker.app.data.remote.CatalogRemoteSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class DealsRepository(
    private val context: Context,
    private val db: TrialDatabase,
    private val scanner: PackageScanner,
    private val settings: SettingsRepository,
    private val json: Json,
    private val remote: CatalogRemoteSource,
) {

    /** Catalog notice text, filled in after the first load. */
    @Volatile
    var notice: String = ""
        private set

    val deals: Flow<List<Deal>> = db.dealDao().observeAll().map { list ->
        list.map { it.toDeal() }
    }

    val installedApps: Flow<List<InstalledApp>> = db.installedAppDao().observeAll().map { list ->
        list.map { InstalledApp(it.packageName, it.label, it.firstInstallTime, it.isSystem) }
    }

    private val favoriteIds: Flow<Set<String>> =
        db.favoriteDao().observeIds().map { it.toSet() }

    /**
     * Everything the UI renders: each catalog deal joined with whether its app is
     * on the device and whether the user starred it.
     */
    val dealsUi: Flow<List<DealUi>> =
        combine(deals, installedApps, favoriteIds) { deals, installed, favorites ->
            match(deals, installed, favorites)
        }

    /**
     * Matching: exact package name first, then a fuzzy fallback on the app label
     * (normalised, ignoring case/spaces/punctuation) so an app that changed its
     * package still lines up with its catalog entry.
     */
    private fun match(
        deals: List<Deal>,
        installed: List<InstalledApp>,
        favorites: Set<String>,
    ): List<DealUi> {
        val byPackage = installed.associateBy { it.packageName }
        val byNormalisedLabel = installed.associateBy { normalise(it.label) }
        return deals.map { deal ->
            val exact = byPackage[deal.packageName]
            val fuzzy = exact ?: byNormalisedLabel[normalise(deal.appName)]
                ?: byNormalisedLabel.entries.firstOrNull { (label, _) ->
                    val target = normalise(deal.appName)
                    label.isNotEmpty() && target.isNotEmpty() &&
                        (label.startsWith(target) || target.startsWith(label))
                }?.value
            DealUi(
                deal = deal,
                installed = fuzzy != null,
                favorite = favorites.contains(deal.id),
                installedLabel = fuzzy?.label,
            )
        }
    }

    private fun normalise(value: String) =
        value.lowercase().filter { it.isLetterOrDigit() }

    /** Loads the bundled catalog on first run so the app is never empty. */
    suspend fun seedFromAssetsIfEmpty() = withContext(Dispatchers.IO) {
        if (db.dealDao().all().isNotEmpty()) {
            notice = readAssetCatalog().notice
            return@withContext
        }
        val catalog = readAssetCatalog()
        notice = catalog.notice
        db.dealDao().upsert(catalog.deals.map { it.toEntity() })
    }

    private fun readAssetCatalog(): DealCatalog =
        runCatching {
            context.assets.open(ASSET_CATALOG).bufferedReader().use { it.readText() }
        }.mapCatching { json.decodeFromString(DealCatalog.serializer(), it) }
            .getOrDefault(DealCatalog())

    /**
     * Pulls the remote catalog and rescans the device.
     * Returns the deals that are new *and* belong to an installed app — those are
     * the ones worth notifying about.
     */
    suspend fun refresh(): Result<List<Deal>> = withContext(Dispatchers.IO) {
        runCatching {
            val remoteCatalog = remote.fetch().getOrNull()
            if (remoteCatalog != null && remoteCatalog.deals.isNotEmpty()) {
                notice = remoteCatalog.notice
                db.dealDao().upsert(remoteCatalog.deals.map { it.toEntity() })
                db.dealDao().deleteMissing(remoteCatalog.deals.map { it.id })
            }

            val catalogPackages = db.dealDao().all().map { it.packageName }
            val showSystem = currentShowSystem()
            val found = scanner.scanCatalogPackages(catalogPackages)
            val extra = if (showSystem) scanner.scanAll(includeSystem = true) else emptyList()
            val merged = (found + extra).distinctBy { it.packageName }

            db.installedAppDao().clear()
            db.installedAppDao().upsert(
                merged.map {
                    InstalledAppEntity(it.packageName, it.label, it.firstInstallTime, it.isSystem)
                },
            )
            settings.setLastSyncAt(System.currentTimeMillis())

            val installedPackages = merged.map { it.packageName }.toSet()
            val seen = db.seenDealDao().ids().toSet()
            val fresh = db.dealDao().all()
                .map { it.toDeal() }
                .filter { it.packageName in installedPackages && it.id !in seen }
            db.seenDealDao().mark(
                db.dealDao().all().map { SeenDealEntity(it.id, System.currentTimeMillis()) },
            )
            fresh
        }
    }

    private suspend fun currentShowSystem(): Boolean = settings.settings.first().showSystemApps

    suspend fun toggleFavorite(dealId: String, favorite: Boolean) {
        if (favorite) {
            db.favoriteDao().add(dealId, System.currentTimeMillis())
        } else {
            db.favoriteDao().remove(dealId)
        }
    }

    private companion object {
        const val ASSET_CATALOG = "deals_catalog.json"
    }
}
