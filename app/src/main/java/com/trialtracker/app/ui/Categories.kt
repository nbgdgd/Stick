package com.trialtracker.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.trialtracker.app.data.model.Deal
import com.trialtracker.app.data.model.DealUi
import com.trialtracker.app.ui.theme.TT

enum class Category(
    val key: String,
    val title: String,
    val subtitle: String,
    val tint: Color,
    val icon: ImageVector,
) {
    TRIALS("trials", "Free Trials", "Пробные подписки", TT.Accent, Icons.Rounded.CardGiftcard),
    BEST("best", "Лучшие предложения", "Самые выгодные акции", TT.Orange, Icons.Rounded.Whatshot),
    DISCOUNTS("discounts", "Скидки", "Снижение цен", TT.Green, Icons.Rounded.LocalOffer),
    PERSONAL("personal", "Персональные предложения", "Подобрано для тебя", TT.Blue, Icons.Rounded.Person),
    RECOMMENDED("recommended", "Рекомендуемые приложения", "Новые и интересные", TT.Yellow, Icons.Rounded.Star),
    FAVORITES("favorites", "Избранное", "Сохранённые предложения", TT.Pink, Icons.Rounded.Favorite);

    companion object {
        fun fromKey(key: String?): Category = entries.firstOrNull { it.key == key } ?: TRIALS
    }
}

/**
 * Priority rule from the spec: trials always rank above ordinary discounts, and
 * inside the trials section the freshest verification wins. Anything installed on
 * the device outranks anything that is not.
 */
fun List<DealUi>.forCategory(category: Category): List<DealUi> = when (category) {
    Category.TRIALS -> filter { it.deal.isTrial }.sortedWith(trialOrder)
    Category.BEST -> filter { it.deal.isTrial || it.deal.discountPercent >= 40 }
        .sortedWith(compareByDescending<DealUi> { it.installed }
            .thenByDescending { it.deal.isTrial }
            .thenByDescending { it.deal.popularity })
    Category.DISCOUNTS -> filter { !it.deal.isTrial }
        .sortedWith(compareByDescending<DealUi> { it.installed }
            .thenByDescending { it.deal.discountPercent })
    Category.PERSONAL -> filter { it.installed }.sortedWith(defaultOrder)
    Category.RECOMMENDED -> filter { !it.installed }
        .sortedByDescending { it.deal.popularity }
    Category.FAVORITES -> filter { it.favorite }.sortedWith(defaultOrder)
}

/** Trials first, then discounts — the ordering used on the home feed. */
val defaultOrder: Comparator<DealUi> = compareByDescending<DealUi> { it.deal.isTrial }
    .thenByDescending { it.installed }
    .thenByDescending { it.deal.lastVerifiedDate }
    .thenByDescending { it.deal.popularity }

private val trialOrder: Comparator<DealUi> = compareByDescending<DealUi> { it.installed }
    .thenByDescending { it.deal.lastVerifiedDate }
    .thenByDescending { it.deal.popularity }

fun Deal.badgeLabel(): String = if (isTrial) "FREE TRIAL" else "-$discountPercent%"
