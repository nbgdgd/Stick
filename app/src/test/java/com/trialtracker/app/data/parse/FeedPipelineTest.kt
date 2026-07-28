package com.trialtracker.app.data.parse

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trialtracker.app.data.model.Deal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * End-to-end over a real response: `app/src/test/resources/googleplaydeals_sample.xml`
 * is an untouched capture of https://www.reddit.com/r/googleplaydeals/new/.rss.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FeedPipelineTest {

    private val xml: String by lazy {
        checkNotNull(javaClass.classLoader?.getResourceAsStream("googleplaydeals_sample.xml"))
            .bufferedReader()
            .use { it.readText() }
    }

    @Test
    fun `reads every entry of the feed`() {
        val entries = AtomFeedParser.parse(xml)
        assertEquals(12, entries.size)
        val first = entries.first()
        assertTrue(first.title.isNotBlank())
        assertTrue(first.contentHtml.contains("play.google.com"))
        assertTrue(first.updated.matches(Regex("""\d{4}-\d{2}-\d{2}T.*""")))
    }

    @Test
    fun `maps entries into deals with a package name and a real date`() {
        val deals = AtomFeedParser.parse(xml)
            .mapIndexedNotNull { index, entry ->
                RedditDealMapper.map(entry, "googleplaydeals", index)
            }

        assertTrue("expected most entries to map, got ${deals.size}", deals.size >= 10)
        deals.forEach { deal ->
            assertTrue("package missing in $deal", deal.packageName.contains('.'))
            assertTrue(deal.appName.isNotBlank())
            assertEquals(Deal.TYPE_DISCOUNT, deal.type)
            assertTrue(deal.discountPercent in 1..100)
            assertTrue(deal.lastVerifiedDate.matches(Regex("""\d{4}-\d{2}-\d{2}""")))
            assertTrue(deal.deepLink.startsWith("http"))
            assertEquals("googleplaydeals", deal.source)
        }
    }

    @Test
    fun `a free promo is stored as a full discount`() {
        val deals = AtomFeedParser.parse(xml)
            .mapIndexedNotNull { i, e -> RedditDealMapper.map(e, "googleplaydeals", i) }
        val free = deals.filter { it.discountPercent == 100 }
        assertTrue("sample should contain a free promo", free.isNotEmpty())
        free.forEach { assertTrue(it.title.contains("Бесплатно")) }
    }

    @Test
    fun `ids are unique so Room upserts never collide`() {
        val deals = AtomFeedParser.parse(xml)
            .mapIndexedNotNull { i, e -> RedditDealMapper.map(e, "googleplaydeals", i) }
        assertEquals(deals.size, deals.map { it.id }.toSet().size)
        assertFalse(deals.any { it.id.isBlank() })
    }
}
