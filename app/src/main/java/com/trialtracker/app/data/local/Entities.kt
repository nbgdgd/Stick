package com.trialtracker.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.trialtracker.app.data.model.Deal

/** Cached copy of the remote catalog so the app opens instantly and works offline. */
@Entity(tableName = "deals")
data class DealEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val type: String,
    val duration: String,
    val description: String,
    val priceAfter: String,
    val discountPercent: Int,
    val deepLink: String,
    val lastVerifiedDate: String,
    val brandColor: String,
    val iconUrl: String,
    val glyph: String,
    val popularity: Int,
    val source: String,
) {
    fun toDeal() = Deal(
        id = id,
        packageName = packageName,
        appName = appName,
        title = title,
        type = type,
        duration = duration,
        description = description,
        priceAfter = priceAfter,
        discountPercent = discountPercent,
        deepLink = deepLink,
        lastVerifiedDate = lastVerifiedDate,
        brandColor = brandColor,
        iconUrl = iconUrl,
        glyph = glyph,
        popularity = popularity,
        source = source,
    )
}

fun Deal.toEntity() = DealEntity(
    id = id,
    packageName = packageName,
    appName = appName,
    title = title,
    type = type,
    duration = duration,
    description = description,
    priceAfter = priceAfter,
    discountPercent = discountPercent,
    deepLink = deepLink,
    lastVerifiedDate = lastVerifiedDate,
    brandColor = brandColor,
    iconUrl = iconUrl,
    glyph = glyph,
    popularity = popularity,
    source = source,
)

/** Local cache of the scan result. Cleared whenever the scan runs again. */
@Entity(tableName = "installed_apps")
data class InstalledAppEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val firstInstallTime: Long,
    val isSystem: Boolean,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val dealId: String,
    val savedAt: Long,
)

/** Deal ids the user has already been notified about, so alerts never repeat. */
@Entity(tableName = "seen_deals")
data class SeenDealEntity(
    @PrimaryKey val dealId: String,
    val seenAt: Long,
)
