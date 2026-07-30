package com.vpet.waifu.domain

/**
 * What an upgrade actually changes.
 *
 * Every field is a plain multiplier or a flat bonus applied on top of
 * [PetTuning], so an upgrade is a value rather than a special case in the
 * simulation.
 */
data class UpgradeEffect(
    /** Multiplies the rate hunger drains at. Below 1 is an improvement. */
    val hungerDecay: Float = 1f,
    /** Multiplies the rate energy drains at while she is awake. */
    val energyDecay: Float = 1f,
    /** Multiplies how fast sleeping restores energy. */
    val sleepSpeed: Float = 1f,
    /** Multiplies wages. */
    val pay: Float = 1f,
    /** Multiplies EXP from studying. */
    val study: Float = 1f,
    /** Multiplies the mood a round of the mini-game is worth. */
    val play: Float = 1f,
    /** Multiplies how fast being ignored drags her mood down. Below 1 is kinder. */
    val neglect: Float = 1f,
) {
    operator fun times(other: UpgradeEffect) = UpgradeEffect(
        hungerDecay = hungerDecay * other.hungerDecay,
        energyDecay = energyDecay * other.energyDecay,
        sleepSpeed = sleepSpeed * other.sleepSpeed,
        pay = pay * other.pay,
        study = study * other.study,
        play = play * other.play,
        neglect = neglect * other.neglect,
    )

    companion object {
        val NONE = UpgradeEffect()
    }
}

/** What kind of thing it is, for grouping in the shop. */
enum class UpgradeKind { ROOM, GEAR, OUTFIT }

/**
 * Something bought once and kept.
 *
 * This is the answer to "what am I working towards". Consumables cannot be
 * that: a cake is eaten, a gift's mood bonus drains back out within the hour,
 * and once you can afford every one of them the economy has nothing left to
 * ask of you. An upgrade is owned, changes the game permanently, and costs
 * several shifts' worth of work.
 */
data class Upgrade(
    val id: String,
    val kind: UpgradeKind,
    val price: Int,
    val requiredLevel: Int = 1,
    val effect: UpgradeEffect = UpgradeEffect.NONE,
    /** Outfits carry no effect; they change how she is drawn. */
    val palette: String? = null,
) {
    fun isUnlocked(level: Int): Boolean = level >= requiredLevel
}

/**
 * The catalog.
 *
 * Prices climb far past what the consumable shop ever asked for — the last one
 * is roughly thirty idol shifts — because the level curve runs to thirty and
 * everything else in the game was bought out by level nine.
 */
object Upgrades {

    val ROOM: List<Upgrade> = listOf(
        Upgrade(
            "fridge", UpgradeKind.ROOM, price = 1_400, requiredLevel = 3,
            effect = UpgradeEffect(hungerDecay = 0.85f),
        ),
        Upgrade(
            "bed", UpgradeKind.ROOM, price = 2_600, requiredLevel = 5,
            effect = UpgradeEffect(sleepSpeed = 1.35f),
        ),
        Upgrade(
            "console", UpgradeKind.ROOM, price = 4_200, requiredLevel = 7,
            effect = UpgradeEffect(play = 1.5f),
        ),
        Upgrade(
            "cat", UpgradeKind.ROOM, price = 7_500, requiredLevel = 12,
            effect = UpgradeEffect(neglect = 0.6f),
        ),
    )

    val GEAR: List<Upgrade> = listOf(
        Upgrade(
            "coffee_machine", UpgradeKind.GEAR, price = 1_900, requiredLevel = 4,
            effect = UpgradeEffect(energyDecay = 0.85f),
        ),
        Upgrade(
            "laptop", UpgradeKind.GEAR, price = 5_000, requiredLevel = 8,
            effect = UpgradeEffect(pay = 1.2f),
        ),
        Upgrade(
            "textbooks", UpgradeKind.GEAR, price = 6_500, requiredLevel = 10,
            effect = UpgradeEffect(study = 1.3f),
        ),
        Upgrade(
            "studio", UpgradeKind.GEAR, price = 18_000, requiredLevel = 16,
            effect = UpgradeEffect(pay = 1.45f, study = 1.15f),
        ),
    )

    /** Cosmetic only. Deliberately not gated on anything but money. */
    val OUTFITS: List<Upgrade> = listOf(
        Upgrade("outfit_uniform", UpgradeKind.OUTFIT, price = 0, palette = "uniform"),
        Upgrade("outfit_cocoa", UpgradeKind.OUTFIT, price = 900, palette = "cocoa"),
        Upgrade("outfit_mint", UpgradeKind.OUTFIT, price = 2_200, palette = "mint"),
        Upgrade("outfit_sakura", UpgradeKind.OUTFIT, price = 4_800, palette = "sakura"),
        Upgrade("outfit_midnight", UpgradeKind.OUTFIT, price = 9_000, palette = "midnight"),
        Upgrade("outfit_gold", UpgradeKind.OUTFIT, price = 25_000, palette = "gold"),
    )

    val ALL: List<Upgrade> = ROOM + GEAR + OUTFITS

    /** The outfit she starts in, which is always owned and always free. */
    const val DEFAULT_OUTFIT = "outfit_uniform"

    fun byId(id: String?): Upgrade? = ALL.firstOrNull { it.id == id }

    /** Everything the given ids add up to. Unknown ids are ignored. */
    fun effectOf(ownedIds: Set<String>): UpgradeEffect =
        ownedIds.mapNotNull { byId(it)?.effect }
            .fold(UpgradeEffect.NONE) { acc, effect -> acc * effect }

    /** Total cost of owning everything — what the whole game is worth. */
    val totalCost: Int = ALL.sumOf { it.price }
}
