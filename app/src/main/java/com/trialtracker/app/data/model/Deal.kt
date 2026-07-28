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
    val glyph: String = "",
    val popularity: Int = 0,
) {
    val isTrial: Boolean get() = type.equals(TYPE_TRIAL, ignoreCase = true)

    companion object {
        const val TYPE_TRIAL = "trial"
        const val TYPE_DISCOUNT = "discount"
    }
}

@Serializable
data class DealCatalog(
    val version: Int = 1,
    @SerialName("generated_at") val generatedAt: String = "",
    val notice: String = "",
    val deals: List<Deal> = emptyList(),
)
