package com.trialtracker.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trialtracker.app.data.model.DealUi
import com.trialtracker.app.ui.Category
import com.trialtracker.app.ui.UiState
import com.trialtracker.app.ui.components.EmptyState
import com.trialtracker.app.ui.components.DealCardCompact
import com.trialtracker.app.ui.components.DealRow
import com.trialtracker.app.ui.components.IconTile
import com.trialtracker.app.ui.components.Pill
import com.trialtracker.app.ui.components.SectionHeader
import com.trialtracker.app.ui.components.SurfaceCard
import com.trialtracker.app.ui.defaultOrder
import com.trialtracker.app.ui.forCategory
import com.trialtracker.app.ui.theme.TT

@Composable
fun HomeScreen(
    state: UiState,
    onOpenCategory: (Category) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDeal: (DealUi) -> Unit,
    onToggleFavorite: (DealUi) -> Unit,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues,
) {
    val recommended = remember(state.deals) { state.deals.sortedWith(defaultOrder).take(12) }
    val trials = state.deals.forCategory(Category.TRIALS)

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Greeting(state.greetingName, onOpenSearch) }

        item {
            ScanStatusCard(
                total = state.totalOffers,
                syncing = state.syncing,
                onRefresh = onRefresh,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }

        item {
            CategoryGrid(
                state = state,
                onOpenCategory = onOpenCategory,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }

        item {
            SectionHeader(
                title = "Рекомендовано для тебя",
                actionLabel = "Смотреть все",
                onAction = { onOpenCategory(Category.RECOMMENDED) },
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            )
        }

        item {
            if (recommended.isEmpty()) {
                EmptyState(
                    title = "Каталог пуст",
                    subtitle = "Потяните «Обновить», чтобы загрузить предложения.",
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(recommended, key = { it.deal.id }) { deal ->
                        DealCardCompact(
                            deal = deal,
                            onClick = { onOpenDeal(deal) },
                            onToggleFavorite = { onToggleFavorite(deal) },
                        )
                    }
                }
            }
        }

        if (trials.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Пробные подписки",
                    actionLabel = "Смотреть все",
                    onAction = { onOpenCategory(Category.TRIALS) },
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                )
            }
            items(trials.take(5), key = { it.deal.id }) { deal ->
                DealRow(
                    deal = deal,
                    onClick = { onOpenDeal(deal) },
                    onToggleFavorite = { onToggleFavorite(deal) },
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            }
        }
    }
}

@Composable
private fun Greeting(name: String, onOpenSearch: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = buildAnnotatedString {
                    append("Привет, ")
                    withStyle(SpanStyle(color = TT.Accent)) { append(name) }
                },
                style = MaterialTheme.typography.headlineMedium,
                color = TT.TextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Мы нашли для тебя лучшие\nпробные подписки и акции ✨",
                style = MaterialTheme.typography.bodyMedium,
                color = TT.TextSecondary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(TT.Surface)
                .clickable { onOpenSearch() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "Поиск",
                tint = TT.TextPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun ScanStatusCard(
    total: Int,
    syncing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SurfaceCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(TT.Accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ShowChart,
                    contentDescription = null,
                    tint = TT.Accent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (syncing) "Идёт анализ…" else "Анализ завершён",
                    style = MaterialTheme.typography.titleMedium,
                    color = TT.TextPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildAnnotatedString {
                        append("Найдено ")
                        withStyle(SpanStyle(color = TT.Accent, fontWeight = FontWeight.SemiBold)) {
                            append(total.toString())
                        }
                        append(" предложений")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TT.TextSecondary,
                )
            }
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(TT.SurfaceHigh)
                    .clickable(enabled = !syncing) { onRefresh() }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Обновить",
                    color = TT.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(7.dp))
                RefreshIcon(spinning = syncing)
            }
        }
    }
}

/**
 * The refresh glyph. The infinite rotation is only composed while a sync is in
 * flight — an always-running transition would keep the frame clock awake.
 */
@Composable
private fun RefreshIcon(spinning: Boolean) {
    val rotation = if (spinning) {
        val transition = rememberInfiniteTransition(label = "sync")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "angle",
        )
        angle
    } else {
        0f
    }
    Icon(
        imageVector = Icons.Rounded.Refresh,
        contentDescription = null,
        tint = TT.Accent,
        modifier = Modifier.size(17.dp).rotate(rotation),
    )
}

@Composable
private fun CategoryGrid(
    state: UiState,
    onOpenCategory: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    val categories = Category.entries
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        categories.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { category ->
                    CategoryTile(
                        category = category,
                        count = state.count(category),
                        onClick = { onOpenCategory(category) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CategoryTile(
    category: Category,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SurfaceCard(modifier = modifier.height(186.dp), onClick = onClick) {
        Column(Modifier.padding(13.dp)) {
            IconTile(tint = category.tint, size = 50, corner = 15) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = category.tint,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = category.title,
                color = TT.TextPrimary,
                fontSize = 13.5.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = category.subtitle,
                    color = TT.TextSecondary,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = TT.TextTertiary,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.height(9.dp))
            Pill(text = count.toString(), tint = category.tint)
        }
    }
}
