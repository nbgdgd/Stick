package com.trialtracker.app.data

import com.trialtracker.app.data.model.Deal
import com.trialtracker.app.data.model.DealCatalog
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the shipped catalog: it is generated data, and a broken field or a
 * duplicate id would only surface on a device otherwise.
 */
class CatalogTest {

    private val catalog: DealCatalog by lazy {
        val file = File("src/main/assets/deals_catalog.json")
        assertTrue("bundled catalog is missing", file.exists())
        Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
            .decodeFromString(DealCatalog.serializer(), file.readText())
    }

    @Test
    fun `parses with both offers and a watchlist`() {
        assertTrue("expected a sizeable catalog", catalog.deals.size >= 40)
        assertTrue("expected a watchlist to grow from", catalog.watchlist.size >= 30)
        assertTrue(catalog.notice.isNotBlank())
    }

    @Test
    fun `every offer has the fields the UI renders`() {
        catalog.deals.forEach { deal ->
            assertTrue("id blank", deal.id.isNotBlank())
            assertTrue("package looks wrong: ${deal.packageName}", deal.packageName.contains('.'))
            assertTrue("name blank for ${deal.id}", deal.appName.isNotBlank())
            assertTrue("title blank for ${deal.id}", deal.title.isNotBlank())
            assertTrue("description blank for ${deal.id}", deal.description.isNotBlank())
            assertTrue("link for ${deal.id}", deal.deepLink.startsWith("http"))
            assertTrue("icon for ${deal.id}", deal.iconUrl.startsWith("http"))
            assertTrue(
                "date for ${deal.id}",
                deal.lastVerifiedDate.matches(Regex("""\d{4}-\d{2}-\d{2}""")),
            )
            assertTrue(deal.type == Deal.TYPE_TRIAL || deal.type == Deal.TYPE_DISCOUNT)
        }
    }

    @Test
    fun `auto-verified entries carry the evidence they were confirmed by`() {
        val auto = catalog.deals.filter { it.verifiedBy == Deal.VERIFIED_AUTO }
        assertTrue("expected auto-confirmed trials", auto.size >= 20)
        auto.forEach { deal ->
            assertTrue("no evidence for ${deal.id}", deal.evidence.isNotBlank())
            assertTrue("no evidence url for ${deal.id}", deal.evidenceUrl.startsWith("http"))
            assertTrue("auto entries should be trials", deal.isTrial)
        }
    }

    @Test
    fun `discounts state how much is taken off`() {
        catalog.deals.filter { !it.isTrial }.forEach {
            assertTrue("no percent on ${it.id}", it.discountPercent > 0)
        }
    }

    @Test
    fun `ids and packages are unique`() {
        val ids = catalog.deals.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        val packages = catalog.deals.map { it.packageName }
        assertEquals(packages.size, packages.toSet().size)
    }

    @Test
    fun `watchlist has no overlap with confirmed offers`() {
        val offered = catalog.deals.map { it.packageName }.toSet()
        catalog.watchlist.forEach { watched ->
            assertFalse("${watched.packageName} is both offered and watched", watched.packageName in offered)
            assertTrue(watched.pricingUrl.startsWith("http"))
            assertTrue(watched.iconUrl.startsWith("http"))
            assertTrue(watched.appName.isNotBlank())
        }
    }
}
