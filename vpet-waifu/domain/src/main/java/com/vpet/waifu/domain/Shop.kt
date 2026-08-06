package com.vpet.waifu.domain

enum class ShopCategory { FOOD, GIFT, PILL, BOOST, CARE }

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

    // --- boosts: bought mid-shift, felt mid-shift ---------------------------

    /**
     * The session clock runs double: each real minute counts as two, so a
     * shift finishes in half the time at full pay. Speed, literally.
     */
    HASTE,

    /** Wages ×1.5 while it runs. */
    OVERTIME,

    /** Study EXP ×1.5 while it runs. */
    FOCUS,

    /** Energy drains at half rate while it runs. */
    SECOND_WIND,

    /** Mood climbs a little every minute while it runs. */
    GOOD_VIBES,

    // --- earned, not sold ---------------------------------------------------
    //
    // The three below cannot be bought at any price. A shop that sells every
    // buff is a shop that sells the answer to every problem, and the money to
    // buy it comes from the same place every time — so the boosts stop being
    // decisions and become a tax on not opening the shop. These arrive from
    // quests, from a run of good rounds in the arcade, and from what turns up
    // around the flat, which are all things you have to actually do.

    /** Shop prices cut by a fifth while it runs. */
    DISCOUNT,

    /**
     * Hunger and energy stop moving entirely.
     *
     * The buff for leaving: twenty minutes where nothing decays is worth most
     * when you are about to put the phone down, which is the one moment the
     * game previously had nothing to offer.
     */
    STASIS,
}

/** The boosts, as a set — several rules ask "is this a boost" at once. */
val BOOST_EFFECTS: Set<EffectKind> = setOf(
    EffectKind.HASTE, EffectKind.OVERTIME, EffectKind.FOCUS,
    EffectKind.SECOND_WIND, EffectKind.GOOD_VIBES,
    EffectKind.DISCOUNT, EffectKind.STASIS,
)

/** The ones no amount of money can buy — see [EffectKind.DISCOUNT]. */
val EARNED_EFFECTS: Set<EffectKind> = setOf(EffectKind.DISCOUNT, EffectKind.STASIS)

/**
 * [startedAt] exists so the bar can be drawn.
 *
 * The record used to keep only its expiry, and the UI recovered the full length
 * by searching the shop for whatever item granted this kind. That worked only
 * while every effect came from an item — the earned buffs have no item, so the
 * lookup found nothing and the drain bar had no denominator. Storing the start
 * is a byte in the save and removes a search that was never really correct: two
 * items granting the same kind at different lengths would have found the wrong
 * one all along.
 */
data class ActiveEffect(
    val kind: EffectKind,
    val expiresAt: Long,
    val startedAt: Long = 0L,
) {
    fun isActive(nowMillis: Long): Boolean = nowMillis < expiresAt

    /** How much of it is left, 1 at the start and 0 as it expires. */
    fun fractionLeft(nowMillis: Long): Float {
        val total = (expiresAt - startedAt).toFloat()
        if (startedAt <= 0L || total <= 0f) return 1f
        return ((expiresAt - nowMillis) / total).coerceIn(0f, 1f)
    }
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
            ENERGY_DRINK_ID, ShopCategory.FOOD, price = 110,
            hunger = 5f, energy = 45f, mood = 4f,
            effect = EffectKind.EXHAUSTION, effectMinutes = 30,
        ),
    )

    /**
     * The one food that is sold while she is on the clock.
     *
     * It is shelved with the food because it is something she drinks, but it
     * is a boost in everything that matters — see [PetSnapshot.canBuy] for the
     * exception that makes the comment above true.
     */
    const val ENERGY_DRINK_ID = "energy_drink"

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

    /**
     * Boosts: timed help, bought in the moment it is needed.
     *
     * Their defining property is *when* they can be bought — mid-shift,
     * mid-lesson — because a buff you can only take before committing is a
     * planning tool, and a buff you can grab when the office grind is dragging
     * is a lever. None of them stack with themselves; the price is per use.
     */
    val BOOSTS: List<ShopItem> = listOf(
        // Half the remaining shift, at full pay. The premium buff.
        ShopItem(
            "haste_shot", ShopCategory.BOOST, price = 140, requiredLevel = 3,
            effect = EffectKind.HASTE, effectMinutes = 30,
        ),
        ShopItem(
            "overtime_pass", ShopCategory.BOOST, price = 90, requiredLevel = 4,
            effect = EffectKind.OVERTIME, effectMinutes = 45,
        ),
        ShopItem(
            "focus_tea", ShopCategory.BOOST, price = 70, requiredLevel = 2,
            effect = EffectKind.FOCUS, effectMinutes = 45,
        ),
        ShopItem(
            "second_wind", ShopCategory.BOOST, price = 80,
            effect = EffectKind.SECOND_WIND, effectMinutes = 60,
        ),
        ShopItem(
            "good_vibes", ShopCategory.BOOST, price = 110, requiredLevel = 3,
            effect = EffectKind.GOOD_VIBES, effectMinutes = 60,
        ),
    )

    /**
     * A whole day given to her, and the only sink the late game can spend on
     * twice.
     *
     * Everything else permanent is bought once and then the wallet has nowhere
     * left to go; every consumable is pocket change by level fifteen. This is
     * priced at a day's serious work and can be taken once per calendar day —
     * so it stays a decision rather than a button, and the money keeps meaning
     * something after the catalogue is owned.
     */
    val CARE: List<ShopItem> = listOf(
        ShopItem(
            DAY_OFF_ID, ShopCategory.CARE, price = 6_000,
            hunger = PetStats.MAX, energy = PetStats.MAX, mood = PetStats.MAX,
        ),
    )

    /** The one item gated on the calendar rather than on level or wallet. */
    const val DAY_OFF_ID = "day_off"

    /** What [EffectKind.DISCOUNT] takes off every price in here. */
    const val DISCOUNT_RATE = 0.20f

    val ALL: List<ShopItem> = FOOD + GIFTS + PILLS + BOOSTS + CARE

    fun byId(id: String): ShopItem? = ALL.firstOrNull { it.id == id }
}
