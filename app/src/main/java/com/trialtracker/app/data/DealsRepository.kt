package com.trialtracker.app.data

import android.content.Context
import android.util.Log
import com.trialtracker.app.data.local.SeenDealEntity
import com.trialtracker.app.data.local.TrialDatabase
import com.trialtracker.app.data.local.toEntity
import com.trialtracker.app.data.model.Deal
import com.trialtracker.app.data.model.DealCatalog
import com.trialtracker.app.data.model.DealUi
import com.trialtracker.app.data.model.InstalledApp
import com.trialtracker.app.data.model.WatchedApp
import com.trialtracker.app.data.local.InstalledAppEntity
import com.trialtracker.app.data.remote.CatalogRemoteSource
import com.trialtracker.app.data.remote.DealFeedSource
import com.trialtracker.app.data.remote.TrialProbeSource
import com.trialtracker.app.data.parse.TrialTextExtractor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

class DealsRepository(
    private val context: Context,
    private val db: TrialDatabase,
    private val scanner: PackageScanner,
    private val settings: SettingsRepository,
    private val json: Json,
    private val remote: CatalogRemoteSource,
    private val feeds: DealFeedSource,
    private val trialProbe: TrialProbeSource,
) {

    /** Outcome of the last fetch of one source, surfaced in Settings. */
    data class FeedStatus(val label: String, val count: Int, val error: String?)

    /** Catalog notice text, filled in after the first load. */
    @Volatile
    var notice: String = ""
        private set

    /** Per-source result of the most recent [refresh], keyed by source key. */
    val feedResults: MutableMap<String, FeedStatus> = java.util.concurrent.ConcurrentHashMap()

    /** How many trials the last run confirmed against the service's own page. */
    @Volatile
    var autoVerifiedTrials: Int = 0
        private set

    /** Why the last refresh was incomplete, or null when everything succeeded. */
    @Volatile
    var lastError: String? = null
        private set

    /** Services the catalog tracks but has no confirmed offer for yet. */
    @Volatile
    var watchlist: List<WatchedApp> = emptyList()
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
            val bundled = readAssetCatalog()
            notice = bundled.notice
            watchlist = bundled.watchlist
            return@withContext
        }
        val catalog = readAssetCatalog()
        notice = catalog.notice
        watchlist = catalog.watchlist
        db.dealDao().upsert(catalog.deals.map { it.toEntity() })
    }

    private fun readAssetCatalog(): DealCatalog =
        runCatching {
            context.assets.open(ASSET_CATALOG).bufferedReader().use { it.readText() }
        }.mapCatching { json.decodeFromString(DealCatalog.serializer(), it) }
            .getOrDefault(DealCatalog())

    /**
     * Refreshes every source, then rescans the device.
     *
     * Every stage is isolated: one failing source, or one failing write, must not
     * discard the work the other stages already did. Earlier this was a single
     * try/catch around the whole method, so any hiccup surfaced as "не удалось
     * обновить каталог" and threw away results that had already been fetched.
     *
     * Returns the deals that are new *and* belong to an installed app — those are
     * the ones worth notifying about. Failures are reported in [lastError] rather
     * than swallowed.
     */
    suspend fun refresh(): Result<List<Deal>> = withContext(Dispatchers.IO) {
        val problems = mutableListOf<String>()

        stage("каталог", problems) {
            val remoteCatalog = remote.fetch().getOrElse { error(reasonOf(it)) }
            if (remoteCatalog.deals.isNotEmpty()) {
                notice = remoteCatalog.notice
                if (remoteCatalog.watchlist.isNotEmpty()) watchlist = remoteCatalog.watchlist
                val catalogDeals = remoteCatalog.deals.map { it.copy(source = Deal.SOURCE_CATALOG) }
                db.dealDao().upsert(catalogDeals.map { it.toEntity() })
                db.dealDao().deleteMissing(Deal.SOURCE_CATALOG, catalogDeals.map { it.id })
            }
        }

        feedResults.clear()
        DealFeedSource.DEFAULT_FEEDS.forEachIndexed { index, feed ->
            if (index > 0) delay(DealFeedSource.FEED_SPACING_MS)
            val result = feeds.fetch(feed)
            val parsed = result.getOrNull()
            feedResults[feed.sourceKey] = FeedStatus(
                label = feed.label,
                count = parsed?.size ?: 0,
                error = result.exceptionOrNull()?.let { reasonOf(it) },
            )
            if (parsed.isNullOrEmpty()) return@forEachIndexed
            stage(feed.label, problems) {
                db.dealDao().upsert(parsed.map { it.toEntity() })
                db.dealDao().deleteMissing(feed.sourceKey, parsed.map { it.id })
            }
        }

        var installedPackages: Set<String> = emptySet()
        stage("сканирование", problems) {
            val catalogPackages = db.dealDao().all().map { it.packageName }
            val found = scanner.scanCatalogPackages(catalogPackages)
            val extra = if (currentShowSystem()) {
                scanner.scanAll(includeSystem = true)
            } else {
                emptyList()
            }
            val merged = (found + extra).distinctBy { it.packageName }
            db.installedAppDao().clear()
            db.installedAppDao().upsert(
                merged.map {
                    InstalledAppEntity(it.packageName, it.label, it.firstInstallTime, it.isSystem)
                },
            )
            installedPackages = merged.map { it.packageName }.toSet()
        }

        stage("автопроверка триалов", problems) {
            if (settings.settings.first().autoVerifyTrials) {
                // Hard cap. Each probe can walk up to four pages, so an unbounded
                // pass could outlive a WorkManager job — whose cancellation then
                // looked like a failed refresh.
                withTimeoutOrNull(PROBE_BUDGET_MS) { verifyTrials(installedPackages) }
            }
        }

        settings.setLastSyncAt(System.currentTimeMillis())
        lastError = problems.firstOrNull()

        // The user-visible outcome: what is new for an app they actually have.
        try {
            val seen = db.seenDealDao().ids().toSet()
            val stored = db.dealDao().all()
            val fresh = stored
                .map { it.toDeal() }
                .filter { it.packageName in installedPackages && it.id !in seen }
            db.seenDealDao().mark(stored.map { SeenDealEntity(it.id, System.currentTimeMillis()) })
            Result.success(fresh)
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            // Leaving the screen cancels the refresh. That is not a failure, and
            // reporting it as one is what produced a spurious error toast.
            throw cancellation
        } catch (t: Throwable) {
            Log.w(TAG, "refresh could not compute new deals", t)
            Result.failure(t)
        }
    }

    /**
     * Runs one stage, recording why it failed instead of aborting the refresh.
     * Cancellation is rethrown — a cancelled refresh is not a failed one.
     */
    private suspend fun stage(name: String, problems: MutableList<String>, block: suspend () -> Unit) {
        try {
            block()
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            Log.w(TAG, "refresh stage '$name' failed", t)
            problems += "$name: ${reasonOf(t)}"
        }
    }

    private fun reasonOf(t: Throwable): String =
        t.message?.takeIf { it.isNotBlank() } ?: t::class.java.simpleName

    /**
     * Confirms trials by reading each service's own pricing page.
     *
     * Two kinds of target, in one budget: catalog trials whose terms have gone
     * stale, and watchlist services that have no confirmed offer at all — the
     * latter is how the catalog grows without a release. Apps the user actually
     * has installed are probed first, so the budget is spent where it shows.
     *
     * A page that says nothing changes nothing: a catalog entry keeps its
     * hand-written value and its manual label, and a watchlist entry simply is
     * not promoted.
     */
    private suspend fun verifyTrials(installedPackages: Set<String>) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        val staleTrials = db.dealDao().all()
            .map { it.toDeal() }
            .filter { it.isTrial && it.deepLink.isNotBlank() }
            .filter { it.verifiedBy != Deal.VERIFIED_AUTO || it.lastVerifiedDate < today }

        val known = db.dealDao().all().map { it.packageName }.toSet()
        val watched = watchlist
            .filter { it.packageName !in known && it.pricingUrl.isNotBlank() }
            .map { it.asProbeTarget() }

        val targets = (staleTrials + watched)
            .sortedWith(
                compareByDescending<Deal> { it.packageName in installedPackages }
                    .thenBy { it.lastVerifiedDate },
            )
            .take(MAX_PROBES_PER_RUN)

        var confirmed = 0
        for (deal in targets) {
            val result = trialProbe.probe(deal) ?: continue
            confirmed++
            db.dealDao().upsert(
                listOf(
                    deal.copy(
                        title = TrialTextExtractor.humanize(result.days),
                        duration = TrialTextExtractor.humanize(result.days).removeSuffix(" бесплатно"),
                        deepLink = result.sourceUrl,
                        lastVerifiedDate = today,
                        verifiedBy = Deal.VERIFIED_AUTO,
                        evidence = result.phrase,
                        evidenceUrl = result.sourceUrl,
                    ).toEntity(),
                ),
            )
        }
        autoVerifiedTrials = confirmed
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
        const val TAG = "DealsRepository"
        const val ASSET_CATALOG = "deals_catalog.json"
        const val MAX_PROBES_PER_RUN = 12
        const val PROBE_BUDGET_MS = 90_000L
    }
}
