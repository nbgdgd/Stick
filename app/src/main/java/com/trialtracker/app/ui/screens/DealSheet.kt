package com.trialtracker.app.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trialtracker.app.data.model.DealUi
import com.trialtracker.app.ui.components.AppGlyph
import com.trialtracker.app.ui.components.Pill
import com.trialtracker.app.ui.components.formatVerified
import com.trialtracker.app.ui.theme.TT

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealSheet(
    deal: DealUi,
    onDismiss: () -> Unit,
    onOpenLink: (String) -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TT.Surface,
        contentColor = TT.TextPrimary,
        dragHandle = null,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppGlyph(deal, size = 58, corner = 17)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        deal.deal.appName,
                        style = MaterialTheme.typography.titleLarge,
                        color = TT.TextPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        deal.deal.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = TT.TextTertiary,
                    )
                }
                Icon(
                    imageVector = if (deal.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                    tint = if (deal.favorite) TT.Pink else TT.TextTertiary,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onToggleFavorite() }
                        .padding(7.dp),
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(
                    text = if (deal.deal.isTrial) "FREE TRIAL" else "-${deal.deal.discountPercent}%",
                    tint = if (deal.deal.isTrial) TT.Accent else TT.Pink,
                )
                if (deal.installed) Pill("Установлено", TT.Green, bold = false)
                if (deal.deal.duration.isNotBlank()) {
                    Pill(deal.deal.duration, TT.TextSecondary, bold = false)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                deal.deal.title,
                style = MaterialTheme.typography.titleLarge,
                color = if (deal.deal.isTrial) TT.Accent else TT.Pink,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                deal.deal.description,
                style = MaterialTheme.typography.bodyMedium,
                color = TT.TextSecondary,
            )

            Spacer(Modifier.height(16.dp))
            InfoRow("Стоимость после акции", deal.deal.priceAfter)
            InfoRow("Условия проверены", formatVerified(deal.deal.lastVerifiedDate))
            InfoRow(
                "Тип",
                if (deal.deal.isTrial) "Пробная подписка" else "Скидка",
            )

            Spacer(Modifier.height(14.dp))
            Text(
                text = "Условия акций отличаются по регионам и меняются без предупреждения. " +
                    "Сверьтесь с сайтом сервиса перед оплатой.",
                style = MaterialTheme.typography.bodySmall,
                color = TT.TextTertiary,
            )

            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(TT.Accent)
                    .clickable { onOpenLink(deal.deal.deepLink) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Перейти к предложению",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TT.TextTertiary)
        Text(value, style = MaterialTheme.typography.bodySmall, color = TT.TextPrimary)
    }
}
