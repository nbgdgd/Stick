package com.trialtracker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.trialtracker.app.data.model.DealUi
import com.trialtracker.app.ui.Category
import com.trialtracker.app.ui.UiState
import com.trialtracker.app.ui.components.DealRow
import com.trialtracker.app.ui.components.EmptyState
import com.trialtracker.app.ui.components.IconTile
import com.trialtracker.app.ui.components.Pill
import com.trialtracker.app.ui.components.SurfaceCard
import com.trialtracker.app.ui.forCategory
import com.trialtracker.app.ui.theme.TT

/** Bottom-nav "Категории" tab: every section plus the full app list. */
@Composable
fun CategoriesScreen(
    state: UiState,
    onOpenCategory: (Category) -> Unit,
    onOpenApps: () -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Категории",
                style = MaterialTheme.typography.headlineMedium,
                color = TT.TextPrimary,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 4.dp),
            )
        }
        items(Category.entries.toList(), key = { it.key }) { category ->
            CategoryRow(
                title = category.title,
                subtitle = category.subtitle,
                tint = category.tint,
                count = state.count(category),
                onClick = { onOpenCategory(category) },
                modifier = Modifier.padding(horizontal = 18.dp),
            ) {
                Icon(category.icon, null, tint = category.tint, modifier = Modifier.size(24.dp))
            }
        }
        item {
            CategoryRow(
                title = "Все приложения",
                subtitle = "Каталог и совпадения на устройстве",
                tint = TT.TextSecondary,
                count = state.installed.size,
                onClick = onOpenApps,
                modifier = Modifier.padding(horizontal = 18.dp),
            ) {
                Icon(Icons.Rounded.Apps, null, tint = TT.TextSecondary, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun CategoryRow(
    title: String,
    subtitle: String,
    tint: androidx.compose.ui.graphics.Color,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    SurfaceCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(tint = tint, size = 48, corner = 14) { icon() }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TT.TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TT.TextSecondary,
                )
            }
            Pill(count.toString(), tint)
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Rounded.ChevronRight,
                null,
                tint = TT.TextTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** One category's full list. */
@Composable
fun DealListScreen(
    state: UiState,
    category: Category,
    onOpenDeal: (DealUi) -> Unit,
    onToggleFavorite: (DealUi) -> Unit,
    contentPadding: PaddingValues,
) {
    val deals = state.deals.forCategory(category)
    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 2.dp)) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = TT.TextPrimary,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${deals.size} · ${category.subtitle}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TT.TextSecondary,
                )
            }
        }
        if (deals.isEmpty()) {
            item {
                EmptyState(
                    title = "Пока пусто",
                    subtitle = when (category) {
                        Category.FAVORITES -> "Отмечайте предложения сердечком — они появятся здесь."
                        Category.PERSONAL -> "Мы не нашли на устройстве приложений из каталога."
                        else -> "Обновите каталог, чтобы загрузить свежие предложения."
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        items(deals, key = { it.deal.id }) { deal ->
            DealRow(
                deal = deal,
                onClick = { onOpenDeal(deal) },
                onToggleFavorite = { onToggleFavorite(deal) },
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }
    }
}

/** "Все приложения": catalog entries with an explicit found / not-found marker. */
@Composable
fun AppsScreen(
    state: UiState,
    onOpenDeal: (DealUi) -> Unit,
    contentPadding: PaddingValues,
) {
    val byApp = state.deals
        .groupBy { it.deal.packageName }
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, List<DealUi>>> { it.value.first().installed }
            .thenBy { it.value.first().deal.appName })

    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 2.dp)) {
                Text(
                    text = "Все приложения",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TT.TextPrimary,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${byApp.count { it.value.first().installed }} из ${byApp.size} " +
                        "приложений каталога найдено на устройстве",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TT.TextSecondary,
                )
            }
        }
        items(byApp, key = { it.key }) { entry ->
            val first = entry.value.first()
            SurfaceCard(
                modifier = Modifier.padding(horizontal = 18.dp).fillMaxWidth(),
                onClick = { onOpenDeal(first) },
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    com.trialtracker.app.ui.components.AppGlyph(first, size = 46, corner = 13)
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = first.installedLabel ?: first.deal.appName,
                            style = MaterialTheme.typography.titleMedium,
                            color = TT.TextPrimary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = entry.key,
                            style = MaterialTheme.typography.bodySmall,
                            color = TT.TextTertiary,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (first.installed) {
                            Pill("Найдено", TT.Green, bold = false)
                        } else {
                            Pill("Не установлено", TT.TextTertiary, bold = false)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "${entry.value.size} предл.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TT.TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

/** A minimal search over the whole catalog. */
@Composable
fun SearchScreen(
    state: UiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenDeal: (DealUi) -> Unit,
    onToggleFavorite: (DealUi) -> Unit,
    contentPadding: PaddingValues,
) {
    val results = state.deals.filter {
        query.isBlank() ||
            it.deal.appName.contains(query, ignoreCase = true) ||
            it.deal.description.contains(query, ignoreCase = true) ||
            it.deal.title.contains(query, ignoreCase = true)
    }
    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(start = 18.dp, end = 18.dp)) {
                Text(
                    text = "Поиск",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TT.TextPrimary,
                )
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Приложение или предложение", color = TT.TextTertiary) },
                    singleLine = true,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = TT.Surface,
                        unfocusedContainerColor = TT.Surface,
                        focusedBorderColor = TT.Accent,
                        unfocusedBorderColor = TT.Border,
                        focusedTextColor = TT.TextPrimary,
                        unfocusedTextColor = TT.TextPrimary,
                        cursorColor = TT.Accent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Найдено: ${results.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TT.TextSecondary,
                )
            }
        }
        items(results, key = { it.deal.id }) { deal ->
            DealRow(
                deal = deal,
                onClick = { onOpenDeal(deal) },
                onToggleFavorite = { onToggleFavorite(deal) },
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }
    }
}

/** Placeholder surface reused by the notifications tab for its header block. */
@Composable
fun ScreenTitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = TT.TextPrimary)
        Spacer(Modifier.height(3.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TT.TextSecondary)
    }
}

@Composable
fun BackHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(TT.Surface)
                .clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Назад",
                tint = TT.TextPrimary,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = TT.TextPrimary)
    }
}
