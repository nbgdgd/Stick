package com.trialtracker.app.data.parse

import com.trialtracker.app.data.model.Deal
import java.util.Locale
import kotlin.math.abs

/**
 * Turns a feed entry into a [Deal].
 *
 * The Play package name is the join key with the device scan, so an entry without
 * one is dropped — there is nothing to match it against.
 */
object RedditDealMapper {

    private val playPackage = Regex("""play\.google\.com/store/apps/details\?id=([A-Za-z0-9_.]+)""")
    private val playUrl = Regex("""https://play\.google\.com/store/apps/details\?id=[A-Za-z0-9_.]+""")

    /** Reddit's cached preview of the linked Play listing — i.e. the app icon. */
    private val previewImage = Regex("""<img[^>]+src="([^"]+)"""")

    /** Roundup posts link a dozen apps at once and cannot be reduced to one deal. */
    private val roundup = Regex("""(?i)\b(weekly|megathread|roundup|multiple|bundle)\b""")

    private val palette = listOf(
        "#8B5CF6", "#34D399", "#FB7C3C", "#3B82F6",
        "#EC4899", "#FACC15", "#22D3EE", "#F43F5E",
    )

    fun map(entry: FeedEntry, sourceKey: String, rank: Int): Deal? {
        if (roundup.containsMatchIn(entry.title)) return null

        val packages = playPackage.findAll(entry.contentHtml).map { it.groupValues[1] }.toList()
        // More than one package means a list post, not a single offer.
        val packageName = packages.distinct().singleOrNull() ?: return null

        val parsed = DealTitleParser.parse(entry.title) ?: return null
        val discount = parsed.discountPercent ?: return null
        if (discount <= 0) return null

        val currency = parsed.currency
        val newPrice = parsed.newPrice ?: return null
        val oldPrice = parsed.oldPrice

        val title = if (parsed.isFree) "Бесплатно" else "Скидка $discount%"
        val priceAfter = when {
            parsed.isFree && oldPrice != null -> "Бесплатно · было ${money(currency, oldPrice)}"
            parsed.isFree -> "Бесплатно"
            oldPrice != null ->
                "${money(currency, newPrice)} вместо ${money(currency, oldPrice)}"
            else -> money(currency, newPrice)
        }

        return Deal(
            id = "$sourceKey:${entry.id.ifBlank { packageName }}",
            packageName = packageName,
            appName = parsed.name,
            title = title,
            type = Deal.TYPE_DISCOUNT,
            duration = parsed.category?.takeIf { it.isNotBlank() }.orEmpty(),
            description = entry.title.trim(),
            priceAfter = priceAfter,
            discountPercent = discount,
            deepLink = playUrl.find(entry.contentHtml)?.value
                ?: entry.link.ifBlank { "https://play.google.com/store/apps/details?id=$packageName" },
            // The feed timestamp is when the deal was actually posted, so unlike the
            // curated catalog this verification date is exact rather than manual.
            lastVerifiedDate = entry.updated.take(10),
            brandColor = palette[abs(packageName.hashCode()) % palette.size],
            iconUrl = previewImage.find(entry.contentHtml)
                ?.groupValues?.get(1)
                ?.replace("&amp;", "&")
                .orEmpty(),
            glyph = parsed.name.trim().take(1).uppercase(),
            // Newer posts rank higher; the feed is already sorted by recency.
            popularity = (100 - rank).coerceAtLeast(1),
            source = sourceKey,
        )
    }

    // Always two decimals: "$4" reads like a rounded guess next to "$9.99".
    private fun money(currency: String, value: Double): String =
        String.format(Locale.US, "%s%.2f", currency, value)
}
