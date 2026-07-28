package com.trialtracker.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.trialtracker.app.data.local.TrialDatabase
import com.trialtracker.app.data.model.Deal
import com.trialtracker.app.data.model.DealCatalog
import com.trialtracker.app.data.remote.CatalogRemoteSource
import com.trialtracker.app.data.remote.DealFeedSource
import com.trialtracker.app.data.remote.TrialProbeSource
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * A refresh must never fail as a whole. It talks to several networks and a
 * database; if any one of them can take the others down, the user gets
 * "не удалось обновить" and loses results that were already fetched.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DealsRepositoryRefreshTest {

    private lateinit var db: TrialDatabase
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, TrialDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun repository(
        catalog: CatalogRemoteSource,
        feeds: DealFeedSource,
        probe: TrialProbeSource = DeadProbe(),
    ) = DealsRepository(
        context = context,
        db = db,
        scanner = PackageScanner(context),
        settings = SettingsRepository(context),
        json = json,
        remote = catalog,
        feeds = feeds,
        trialProbe = probe,
    )

    @Test
    fun `survives every source failing at once`() = runTest {
        val repo = repository(FailingCatalog(), FailingFeeds())
        val result = repo.refresh()

        assertTrue("refresh must not fail wholesale", result.isSuccess)
        assertNotNull("the reason must be recorded", repo.lastError)
        assertTrue(repo.lastError!!.contains("каталог"))
    }

    @Test
    fun `a failing feed does not discard the catalog`() = runTest {
        val repo = repository(WorkingCatalog(), FailingFeeds())
        assertTrue(repo.refresh().isSuccess)

        val stored = db.dealDao().all()
        assertEquals(2, stored.size)
        assertTrue(stored.all { it.source == Deal.SOURCE_CATALOG })
    }

    @Test
    fun `a failing catalog does not discard the feed`() = runTest {
        val repo = repository(FailingCatalog(), WorkingFeeds())
        assertTrue(repo.refresh().isSuccess)

        val stored = db.dealDao().all()
        assertTrue("feed rows should be stored", stored.any { it.source == "googleplaydeals" })
        assertNotNull(repo.lastError)
    }

    @Test
    fun `a clean run reports no problem`() = runTest {
        val repo = repository(WorkingCatalog(), WorkingFeeds())
        assertTrue(repo.refresh().isSuccess)
        assertNull(repo.lastError)
        assertEquals(3, db.dealDao().all().size)
    }

    @Test
    fun `a source that throws instead of returning a failed Result is contained`() = runTest {
        val repo = repository(ThrowingCatalog(), WorkingFeeds())
        val result = repo.refresh()

        assertTrue(result.isSuccess)
        assertTrue(db.dealDao().all().any { it.source == "googleplaydeals" })
        assertTrue(repo.lastError!!.contains("каталог"))
    }

    // -- doubles ----------------------------------------------------------------

    private fun deal(id: String, source: String) = Deal(
        id = id,
        packageName = "com.example.$id",
        appName = id,
        title = "7 дней бесплатно",
        type = Deal.TYPE_TRIAL,
        deepLink = "https://example.com/$id",
        lastVerifiedDate = "2026-07-01",
        source = source,
    )

    private inner class WorkingCatalog : CatalogRemoteSource(json, OkHttpClient()) {
        override suspend fun fetch() = Result.success(
            DealCatalog(deals = listOf(deal("one", Deal.SOURCE_CATALOG), deal("two", Deal.SOURCE_CATALOG))),
        )
    }

    private inner class FailingCatalog : CatalogRemoteSource(json, OkHttpClient()) {
        override suspend fun fetch(): Result<DealCatalog> = Result.failure(IllegalStateException("HTTP 404"))
    }

    private inner class ThrowingCatalog : CatalogRemoteSource(json, OkHttpClient()) {
        override suspend fun fetch(): Result<DealCatalog> = throw IllegalStateException("boom")
    }

    private inner class WorkingFeeds : DealFeedSource(OkHttpClient()) {
        override suspend fun fetch(feed: Feed): Result<List<Deal>> =
            if (feed.sourceKey == "googleplaydeals") {
                Result.success(listOf(deal("feed", feed.sourceKey)))
            } else {
                Result.success(emptyList())
            }
    }

    private inner class FailingFeeds : DealFeedSource(OkHttpClient()) {
        override suspend fun fetch(feed: Feed): Result<List<Deal>> =
            Result.failure(IllegalStateException("HTTP 429"))
    }

    private class DeadProbe : TrialProbeSource(OkHttpClient()) {
        override suspend fun probe(deal: Deal): Result? = null
    }
}
