package com.trialtracker.app.data.parse

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `app/src/test/resources/vendor_page_snippets.json` holds untouched text taken
 * from the services' live pricing pages, including the two that state no trial.
 */
class TrialTextExtractorTest {

    private val pages: Map<String, String> by lazy {
        val raw = checkNotNull(javaClass.classLoader?.getResourceAsStream("vendor_page_snippets.json"))
            .bufferedReader().use { it.readText() }
        Json.parseToJsonElement(raw).jsonObject.mapValues { (_, value) ->
            value.jsonObject.getValue("text").jsonPrimitive.content
        }
    }

    private fun daysOn(page: String) = TrialTextExtractor.extract(pages.getValue(page))?.days

    @Test
    fun `reads the trial length off real pricing pages`() {
        assertEquals(14, daysOn("headspace"))
        assertEquals(30, daysOn("yandex_plus"))
        assertEquals(7, daysOn("coursera"))
        assertEquals(30, daysOn("strava"))
        assertEquals(7, daysOn("grammarly"))
    }

    @Test
    fun `reports nothing for services that advertise no trial`() {
        assertNull(daysOn("netflix_no_trial"))
        assertNull(daysOn("telegram_no_trial"))
    }

    @Test
    fun `keeps the phrase it read the length from`() {
        val finding = TrialTextExtractor.extract(pages.getValue("coursera"))!!
        assertTrue(finding.phrase.contains("7", ignoreCase = true))
        assertTrue(finding.phrase.contains("trial", ignoreCase = true))
        assertTrue(finding.occurrences >= 1)
    }

    @Test
    fun `prefers the length a page repeats most`() {
        // Headspace advertises 14 days in its plan copy and mentions 7 once.
        val finding = TrialTextExtractor.extract(pages.getValue("headspace"))!!
        assertEquals(14, finding.days)
    }

    @Test
    fun `ties go to the shorter trial`() {
        val text = "Start your 30-day free trial. Or take a 7-day free trial instead."
        assertEquals(7, TrialTextExtractor.extract(text)!!.days)
    }

    @Test
    fun `normalises weeks and months to days`() {
        assertEquals(30, TrialTextExtractor.extract("Try Premium free for 1 month")!!.days)
        assertEquals(14, TrialTextExtractor.extract("2 weeks free")!!.days)
        assertEquals(30, TrialTextExtractor.extract("30 дней бесплатно")!!.days)
        assertEquals(7, TrialTextExtractor.extract("пробный период 7 дней")!!.days)
    }

    @Test
    fun `ignores templates, reminders and yearly plans`() {
        // Picsart ships an untranslated placeholder in its page bundle.
        assertNull(TrialTextExtractor.extract("Choose a plan to start after your {{number}}-day trial"))
        // Grammarly's "2 days before trial ends" is a reminder, not an offer.
        assertNull(TrialTextExtractor.extract("Email reminder 2 days before trial ends."))
        assertNull(TrialTextExtractor.extract("Save with a 365 days free plan"))
    }

    @Test
    fun `flattens markup and script payloads before matching`() {
        val html = """<div class="x"><script>{"cta":"Start 7-day Free Trial"}</script></div>"""
        val finding = TrialTextExtractor.extract(TrialTextExtractor.htmlToText(html))
        assertEquals(7, finding!!.days)
    }

    @Test
    fun `rejects long free periods that are bundled into an annual plan`() {
        // Both were live false positives: a bonus on a yearly plan, not a trial.
        assertNull(TrialTextExtractor.extract("Get 3 months of free service with a 12-month plan"))
        assertNull(TrialTextExtractor.extract("6 months FREE when you sign up for a year"))
        // A long period the page actually calls a trial still counts.
        assertEquals(90, TrialTextExtractor.extract("90-day free trial")!!.days)
        assertEquals(60, TrialTextExtractor.extract("60 дней бесплатно")!!.days)
    }

    @Test
    fun `formats durations with the right Russian plural`() {
        assertEquals("1 день бесплатно", TrialTextExtractor.humanize(1))
        assertEquals("3 дня бесплатно", TrialTextExtractor.humanize(3))
        assertEquals("7 дней бесплатно", TrialTextExtractor.humanize(7))
        assertEquals("14 дней бесплатно", TrialTextExtractor.humanize(14))
        assertEquals("1 месяц бесплатно", TrialTextExtractor.humanize(30))
        assertEquals("2 месяца бесплатно", TrialTextExtractor.humanize(60))
    }
}
