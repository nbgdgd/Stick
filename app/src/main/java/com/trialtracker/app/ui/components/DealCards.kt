package com.trialtracker.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.trialtracker.app.data.model.Deal
import com.trialtracker.app.data.model.DealUi
import com.trialtracker.app.ui.badgeLabel
import com.trialtracker.app.ui.parseColor
import com.trialtracker.app.ui.rememberAppIcon
import com.trialtracker.app.ui.theme.TT

/**
 * The app icon — the only coloured element on a card.
 *
 * Three tiers, in order: the real launcher icon when the app is installed, the
 * Play CDN icon when it is not (catalog entries carry the URL, feed entries reuse
 * the post's preview image), and finally a brand-tinted letter tile so a card is
 * never blank.
 */
@Composable
fun AppGlyph(deal: DealUi, size: Int = 52, corner: Int = 15) {
    val brand = parseColor(deal.deal.brandColor, TT.Accent)
    val localIcon by rememberAppIcon(deal.deal.packageName, deal.installed)
    val shape = RoundedCornerShape(corner.dp)
    val remoteUrl = remember(deal.deal.iconUrl, size) {
        sizedIconUrl(deal.deal.iconUrl, size)
    }

    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(shape)
            .background(brand.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        // The letter sits underneath and shows through until (or unless) an image
        // resolves, so there is no empty square while the icon downloads.
        Text(
            text = deal.deal.glyph.ifBlank { deal.deal.appName.take(1) },
            color = brand,
            fontSize = (size * 0.42f).sp,
            fontWeight = FontWeight.Bold,
        )

        val bitmap = localIcon
        when {
            bitmap != null -> Image(
                bitmap = bitmap,
                contentDescription = deal.deal.appName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size.dp).clip(shape),
            )
            remoteUrl != null -> AsyncImage(
                model = remoteUrl,
                contentDescription = deal.deal.appName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size.dp).clip(shape),
            )
        }
    }
}

/**
 * Play's image CDN takes the size as a URL suffix, so we ask for exactly what the
 * tile needs instead of downloading a full-resolution icon.
 */
private fun sizedIconUrl(url: String, sizeDp: Int): String? {
    if (url.isBlank()) return null
    if (!url.startsWith("http")) return null
    val isPlayCdn = url.contains("googleusercontent.com")
    if (!isPlayCdn || url.substringAfterLast('/').contains('=')) return url
    return "$url=s${(sizeDp * 3).coerceAtMost(384)}"
}

@Composable
private fun OfferBadge(deal: DealUi) {
    val isTrial = deal.deal.isTrial
    Pill(
        text = deal.deal.badgeLabel(),
        tint = if (isTrial) TT.Accent else TT.Pink,
        background = (if (isTrial) TT.Accent else TT.Pink).copy(alpha = 0.22f),
    )
}

@Composable
private fun FavoriteButton(deal: DealUi, onToggle: () -> Unit) {
    Icon(
        imageVector = if (deal.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
        contentDescription = if (deal.favorite) "Убрать из избранного" else "В избранное",
        tint = if (deal.favorite) TT.Pink else TT.TextTertiary,
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable { onToggle() }
            .padding(4.dp),
    )
}

@Composable
private fun VerifiedLine(date: String, verifiedBy: String) {
    // Always visible: promo terms go stale fast and the user needs to see both how
    // old the information is and whether the app confirmed it itself.
    val auto = verifiedBy == Deal.VERIFIED_AUTO
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (auto) Icons.Rounded.Verified else Icons.Rounded.Schedule,
            contentDescription = null,
            tint = if (auto) TT.Green else TT.TextTertiary,
            modifier = Modifier.size(11.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "проверено ${formatVerified(date)}",
            color = if (auto) TT.Green.copy(alpha = 0.8f) else TT.TextTertiary,
            fontSize = 10.5.sp,
        )
    }
}

/** Carousel card — the "Рекомендовано для тебя" row on the home screen. */
@Composable
fun DealCardCompact(
    deal: DealUi,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SurfaceCard(modifier = modifier.width(188.dp).height(258.dp), onClick = onClick) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                AppGlyph(deal, size = 50)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OfferBadge(deal)
                    Spacer(Modifier.width(2.dp))
                    FavoriteButton(deal, onToggleFavorite)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = deal.deal.appName,
                style = MaterialTheme.typography.titleMedium,
                color = TT.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = deal.deal.title,
                color = if (deal.deal.isTrial) TT.Accent else TT.Pink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = deal.deal.description,
                style = MaterialTheme.typography.bodySmall,
                color = TT.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            VerifiedLine(deal.deal.lastVerifiedDate, deal.deal.verifiedBy)
            if (deal.deal.priceAfter.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(TT.SurfaceHigh)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = if (deal.deal.isTrial) "После: ${deal.deal.priceAfter}" else deal.deal.priceAfter,
                        color = TT.TextSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Wide row card — category screens, search results and the favourites list. */
@Composable
fun DealRow(
    deal: DealUi,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SurfaceCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(14.dp)) {
            AppGlyph(deal, size = 54)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = deal.deal.appName,
                        style = MaterialTheme.typography.titleMedium,
                        color = TT.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OfferBadge(deal)
                    Spacer(Modifier.width(2.dp))
                    FavoriteButton(deal, onToggleFavorite)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = deal.deal.title,
                    color = if (deal.deal.isTrial) TT.Accent else TT.Pink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = deal.deal.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TT.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (deal.installed) {
                        Pill("Установлено", TT.Green, bold = false)
                        Spacer(Modifier.width(8.dp))
                    }
                    if (deal.deal.priceAfter.isNotBlank()) {
                        Text(
                            text = if (deal.deal.isTrial) "После: ${deal.deal.priceAfter}" else deal.deal.priceAfter,
                            color = TT.TextSecondary,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
                Spacer(Modifier.height(7.dp))
                VerifiedLine(deal.deal.lastVerifiedDate, deal.deal.verifiedBy)
            }
        }
    }
}
