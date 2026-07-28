package com.trialtracker.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One offer for one app.
 *
 * There is no public API that knows which apps currently run a free trial, so the
 * catalog is hand-curated JSON. Every entry therefore carries
 * [lastVerifiedDate] — the app surfaces it everywhere an offer is shown, because
 * trial terms differ by region and go stale fast.
 */
@Serializable
data class Deal(
    val id: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("app_name") val appName: String,
    val title: String,
    val type: String,
    val duration: String = "",
    val description: String = "",
    @SerialName("price_after") val priceAfter: String = "",
    @SerialName("discount_percent") val discountPercent: Int = 0,
    @SerialName("deep_link") val deepLink: String = "",
    @SerialName("last_verified_date") val lastVerifiedDate: String = "",
    @SerialName("brand_color") val brandColor: String = "#8B5CF6",
    /**
     * Play CDN base URL of the app icon, so an app that is not installed still
     * shows its real icon. A size suffix is appended at load time.
     */
    @SerialName("icon_url") val iconUrl: String = "",
    val glyph: String = "",
    val popularity: Int = 0,
    /** Which source produced this entry: the curated catalog or a live feed. */
    val source: String = SOURCE_CATALOG,
    /** How [lastVerifiedDate] was established — by hand, or by a live check. */
    @SerialName("verified_by") val verifiedBy: String = VERIFIED_MANUAL,
    /** The phrase a trial length was read from, shown as evidence in the UI. */
    val evidence: String = "",
    @SerialName("evidence_url") val evidenceUrl: String = "",
) {
    val isTrial: Boolean get() = type.equals(TYPE_TRIAL, ignoreCase = true)

    companion object {
        const val TYPE_TRIAL = "trial"
        const val TYPE_DISCOUNT = "discount"
        const val SOURCE_CATALOG = "catalog"
        /** Trials the app confirmed itself by reading a service's pricing page. */
        const val SOURCE_PROBE = "probe"
        const val VERIFIED_MANUAL = "manual"
        const val VERIFIED_AUTO = "auto"
    }
}

@Serializable
data class DealCatalog(
    val version: Int = 1,
    @SerialName("generated_at") val generatedAt: String = "",
    val notice: String = "",
    val deals: List<Deal> = emptyList(),
    /**
     * Services the catalog tracks but has no confirmed offer for. The app probes
     * their pricing pages itself, so an entry here becomes a real trial the moment
     * the service advertises one — the catalog grows without a release.
     */
    val watchlist: List<WatchedApp> = emptyList(),
)

@Serializable
data class WatchedApp(
    @SerialName("package_name") val packageName: String,
    @SerialName("app_name") val appName: String,
    val description: String = "",
    @SerialName("pricing_url") val pricingUrl: String = "",
    @SerialName("brand_color") val brandColor: String = "#8B5CF6",
    @SerialName("icon_url") val iconUrl: String = "",
    val glyph: String = "",
    val popularity: Int = 0,
) {
    /** The shape the probe needs: a deal that has everything but a confirmed offer. */
    fun asProbeTarget() = Deal(
        id = "probe:$packageName",
        packageName = packageName,
        appName = appName,
        title = "",
        type = Deal.TYPE_TRIAL,
        description = description,
        deepLink = pricingUrl,
        brandColor = brandColor,
        iconUrl = iconUrl,
        glyph = glyph,
        popularity = popularity,
        source = Deal.SOURCE_PROBE,
    )
}
