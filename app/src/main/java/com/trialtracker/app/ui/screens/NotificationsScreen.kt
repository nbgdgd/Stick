package com.trialtracker.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trialtracker.app.data.model.DealUi
import com.trialtracker.app.ui.UiState
import com.trialtracker.app.ui.components.AppGlyph
import com.trialtracker.app.ui.components.Pill
import com.trialtracker.app.ui.components.SurfaceCard
import com.trialtracker.app.ui.components.formatVerified
import com.trialtracker.app.ui.theme.TT

/**
 * Feed of catalog activity: the offers whose terms were verified most recently,
 * with the ones for installed apps pulled to the top — those are exactly the
 * entries the background worker would raise a notification for.
 */
@Composable
fun NotificationsScreen(
    state: UiState,
    onOpenDeal: (DealUi) -> Unit,
    contentPadding: PaddingValues,
) {
    val feed = state.deals
        .sortedWith(
            compareByDescending<DealUi> { it.installed }
                .thenByDescending { it.deal.lastVerifiedDate },
        )
        .take(30)

    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenTitle(
                title = "Уведомления",
                subtitle = if (state.settings.notificationsEnabled) {
                    "Проверяем каталог каждые ${state.settings.checkIntervalHours} ч"
                } else {
                    "Фоновая проверка выключена в настройках"
                },
            )
        }
        if (!state.settings.notificationsEnabled) {
            item {
                SurfaceCard(modifier = Modifier.padding(horizontal = 18.dp).fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.NotificationsOff,
                            null,
                            tint = TT.TextTertiary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Включите уведомления в настройках, чтобы не пропустить новые триалы.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TT.TextSecondary,
                        )
                    }
                }
            }
        }
        items(feed, key = { it.deal.id }) { deal ->
            SurfaceCard(
                modifier = Modifier.padding(horizontal = 18.dp).fillMaxWidth(),
                onClick = { onOpenDeal(deal) },
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    AppGlyph(deal, size = 44, corner = 13)
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = deal.deal.appName,
                                style = MaterialTheme.typography.titleMedium,
                                color = TT.TextPrimary,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(8.dp))
                            if (deal.installed) Pill("У вас установлено", TT.Green, bold = false)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = deal.deal.title,
                            color = if (deal.deal.isTrial) TT.Accent else TT.Pink,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = "Условия проверены ${formatVerified(deal.deal.lastVerifiedDate)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TT.TextTertiary,
                        )
                    }
                }
            }
        }
    }
}
