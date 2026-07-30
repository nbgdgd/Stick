package com.vpet.waifu.domain

enum class ShopCategory { FOOD, GIFT, PILL }

/**
 * A lingering side effect, currently only ever applied by pills.
 *
 * Effects are the "risk" half of the risk/reward pills: the payout is instant,
 * the bill arrives over the next few hours.
 */
enum class EffectKind {
    /** Hunger drains far faster — the price of a cash advance. */
    HUNGER_SURGE,

    /** Energy drains faster — the crash after an EXP pill. */
    EXHAUSTION,
}

data class ActiveEffect(val kind: EffectKind, val expiresAt: Long) {
    fun isActive(nowMillis: Long): Boolean = nowMillis < expiresAt
}

/**
 * Everything on sale. Buying applies the item immediately — there is no
 * inventory to manage, which keeps the loop "earn → spend → she reacts" tight.
 */
data class ShopItem(
    val id: String,
    val category: ShopCategory,
    val price: Int,
    val requiredLevel: Int = 1,
    val hunger: Float = 0f,
    val energy: Float = 0f,
    val mood: Float = 0f,
    val money: Int = 0,
    val exp: Int = 0,
    val effect: EffectKind? = null,
    val effectMinutes: Int = 0,
) {
    fun isUnlocked(level: Int): Boolean = level >= requiredLevel
}

object Shop {

    val FOOD: List<ShopItem> = listOf(
        ShopItem("onigiri", ShopCategory.FOOD, price = 25, hunger = 25f, mood = 3f),
        ShopItem("ramen", ShopCategory.FOOD, price = 60, hunger = 45f, mood = 9f),
        ShopItem("cake", ShopCategory.FOOD, price = 90, hunger = 28f, mood = 22f),
        ShopItem("bento", ShopCategory.FOOD, price = 110, hunger = 70f, mood = 14f),
        ShopItem("parfait", ShopCategory.FOOD, price = 140, requiredLevel = 5, hunger = 35f, mood = 30f),
    )

    val GIFTS: List<ShopItem> = listOf(
        ShopItem("flowers", ShopCategory.GIFT, price = 150, mood = 22f),
        ShopItem("teddy", ShopCategory.GIFT, price = 260, mood = 34f),
        ShopItem("headphones", ShopCategory.GIFT, price = 480, requiredLevel = 4, mood = 48f),
        ShopItem("ring", ShopCategory.GIFT, price = 900, requiredLevel = 9, mood = 70f),
    )

    val PILLS: List<ShopItem> = listOf(
        // Net +320, paid for with three hours of double hunger drain.
        ShopItem(
            "advance", ShopCategory.PILL, price = 180,
            money = 500, effect = EffectKind.HUNGER_SURGE, effectMinutes = 180,
        ),
        // A level's worth of EXP now, an exhausted evening after.
        ShopItem(
            "exp_pill", ShopCategory.PILL, price = 260, requiredLevel = 2,
            exp = 180, energy = -25f, mood = -15f,
            effect = EffectKind.EXHAUSTION, effectMinutes = 120,
        ),
    )

    val ALL: List<ShopItem> = FOOD + GIFTS + PILLS

    fun byId(id: String): ShopItem? = ALL.firstOrNull { it.id == id }
}
