package com.trialtracker.app.data.parse

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/** One `<entry>` of an Atom feed, reduced to the fields the mapper needs. */
data class FeedEntry(
    val id: String,
    val title: String,
    val contentHtml: String,
    val link: String,
    val updated: String,
)

/**
 * Minimal streaming Atom reader. Reddit publishes these feeds for exactly this
 * purpose, so the app reads the documented feed rather than scraping HTML.
 */
object AtomFeedParser {

    fun parse(xml: String): List<FeedEntry> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        val entries = mutableListOf<FeedEntry>()
        var id = ""
        var title = ""
        var content = ""
        var link = ""
        var updated = ""
        var inEntry = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "entry" -> {
                        inEntry = true
                        id = ""; title = ""; content = ""; link = ""; updated = ""
                    }
                    "id" -> if (inEntry) id = parser.nextTextSafe()
                    "title" -> if (inEntry) title = parser.nextTextSafe()
                    "content" -> if (inEntry) content = parser.nextTextSafe()
                    "updated" -> if (inEntry) updated = parser.nextTextSafe()
                    "link" -> if (inEntry && link.isEmpty()) {
                        link = parser.getAttributeValue(null, "href").orEmpty()
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "entry") {
                    inEntry = false
                    if (title.isNotBlank()) {
                        entries += FeedEntry(id, title, content, link, updated)
                    }
                }
            }
            event = parser.next()
        }
        return entries
    }

    /**
     * `nextText()` throws when an element holds mixed content. The feeds are
     * well-formed today, but a malformed entry must not take down the whole sync.
     */
    private fun XmlPullParser.nextTextSafe(): String = try {
        nextText().orEmpty().trim()
    } catch (e: Exception) {
        ""
    }
}
