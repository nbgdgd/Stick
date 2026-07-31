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
        ShopItem("onigiri", ShopCategory.FOOD, price = 20, hunger = 25f, mood = 3f),
        ShopItem("ramen", ShopCategory.FOOD, price = 50, hunger = 45f, mood = 9f),
        ShopItem("cake", ShopCategory.FOOD, price = 80, hunger = 28f, mood = 22f),
        ShopItem("bento", ShopCategory.FOOD, price = 100, hunger = 70f, mood = 14f),
        ShopItem("parfait", ShopCategory.FOOD, price = 130, requiredLevel = 5, hunger = 35f, mood = 30f),
        // The only way to buy energy. Sleep is still the cheap route — this one
        // costs money and comes with a caffeine crash — but it lets her push
        // through a shift instead of losing half an hour to a nap.
        ShopItem(
            "energy_drink", ShopCategory.FOOD, price = 110,
            hunger = 5f, energy = 45f, mood = 4f,
            effect = EffectKind.EXHAUSTION, effectMinutes = 30,
        ),
    )

    val GIFTS: List<ShopItem> = listOf(
        ShopItem("flowers", ShopCategory.GIFT, price = 130, mood = 22f),
        ShopItem("teddy", ShopCategory.GIFT, price = 230, mood = 34f),
        ShopItem("headphones", ShopCategory.GIFT, price = 420, requiredLevel = 4, mood = 48f),
        ShopItem("ring", ShopCategory.GIFT, price = 820, requiredLevel = 9, mood = 70f),
    )

    val PILLS: List<ShopItem> = listOf(
        // Net +320, paid for with a bite out of her right now and three hours
        // of doubled hunger drain after. The instant cost matters as much as
        // the timer: a debt that is purely a countdown costs nothing at all if
        // you take the next advance the moment the last one clears.
        ShopItem(
            "advance", ShopCategory.PILL, price = 160,
            money = 480, hunger = -18f,
            effect = EffectKind.HUNGER_SURGE, effectMinutes = 180,
        ),
        // A level's worth of EXP now, an exhausted evening after.
        ShopItem(
            "exp_pill", ShopCategory.PILL, price = 240, requiredLevel = 2,
            exp = 180, energy = -25f, mood = -15f,
            effect = EffectKind.EXHAUSTION, effectMinutes = 120,
        ),
        // The cure. Cheap on purpose: sickness is the *consequence* of neglect,
        // and pricing the way out of it past a neglected wallet would make the
        // hole deeper the further in you fall. The real cost was the days of
        // blocked work and draining mood before you noticed.
        ShopItem(MEDICINE_ID, ShopCategory.PILL, price = 90, mood = 5f),
    )

    /** The one item [com.vpet.waifu.domain.PetSimulation.buy] treats as a cure. */
    const val MEDICINE_ID = "medicine"

    val ALL: List<ShopItem> = FOOD + GIFTS + PILLS

    fun byId(id: String): ShopItem? = ALL.firstOrNull { it.id == id }
}
