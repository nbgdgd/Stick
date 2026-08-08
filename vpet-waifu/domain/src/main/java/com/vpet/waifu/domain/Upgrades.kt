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

    /**
     * The fields that are actually doing something, and by how much.
     *
     * The UI has to be able to say *what improves* without knowing which of
     * the seven fields this particular thing touches — a shop row that reads
     * "Level 3 → 4" and nothing else is a number with no meaning attached.
     * Neutral fields are left out, so a fridge reports hunger and nothing else.
     */
    fun headline(): List<Pair<UpgradeStat, Float>> = buildList {
        if (hungerDecay != 1f) add(UpgradeStat.HUNGER to hungerDecay)
        if (energyDecay != 1f) add(UpgradeStat.ENERGY to energyDecay)
        if (sleepSpeed != 1f) add(UpgradeStat.SLEEP to sleepSpeed)
        if (pay != 1f) add(UpgradeStat.PAY to pay)
        if (study != 1f) add(UpgradeStat.STUDY to study)
        if (play != 1f) add(UpgradeStat.PLAY to play)
        if (neglect != 1f) add(UpgradeStat.NEGLECT to neglect)
    }

    companion object {
        val NONE = UpgradeEffect()
    }
}

/**
 * Which number an upgrade moves.
 *
 * [lowerIsBetter] is here rather than in the UI because it is a fact about the
 * simulation, not about how it is drawn: `hungerDecay = 0.7` is a *better*
 * fridge, and a screen that renders every multiplier the same way tells the
 * player the best fridge in the game is the worst one.
 */
enum class UpgradeStat(val lowerIsBetter: Boolean) {
    HUNGER(true),
    ENERGY(true),
    SLEEP(false),
    PAY(false),
    STUDY(false),
    PLAY(false),
    NEGLECT(true),
}

/** What kind of thing it is, for grouping in the shop. */
enum class UpgradeKind { ROOM, GEAR, OUTFIT, THEME }

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
    /**
     * The thing this is a better version of.
     *
     * A fridge is one object the player keeps improving, not five different
     * fridges — so every tier of it shares a family, and the family is what
     * carries the name, the picture and the row in the shop. For a one-off
     * purchase the family is simply its own id.
     */
    val family: String = id,
    /** Which step of that family this is, counting from one. */
    val tier: Int = 1,
) {
    fun isUnlocked(level: Int): Boolean = level >= requiredLevel

    /** The tier that has to be owned before this one may be bought. */
    val requires: String? get() = if (tier <= 1) null else Upgrades.idOf(family, tier - 1)
}

/**
 * The catalog.
 *
 * Prices climb far past what the consumable shop ever asked for — the last one
 * is roughly thirty idol shifts — because the level curve runs to thirty and
 * everything else in the game was bought out by level nine.
 */
object Upgrades {

    /**
     * How many steps every mechanical upgrade has.
     *
     * Five rather than three because of what the curve is for. The first tier
     * is the one everybody buys; the last is the one almost nobody finishes,
     * and a game whose ceiling is reachable in a weekend has no ceiling. Three
     * would put the top of the tree inside the first fortnight.
     */
    const val MAX_TIER = 5

    /** The id of a family's [tier], which is the family's own name at tier 1. */
    fun idOf(family: String, tier: Int): String =
        if (tier <= 1) family else "${family}_$tier"

    /**
     * One upgrade, five times over, each step dearer and gentler than the last.
     *
     * [steps] is the *per-tier* effect, not the running total: they multiply
     * together as they are bought, which is the whole reason the later ones
     * shrink. Repeating tier one's multiplier five times would take the fridge
     * from -15% hunger to -56% and the wage gear to nearly ten times pay,
     * which is not an upgrade curve, it is a different game arriving at level
     * twenty. Each step here is worth having and worth less than the one
     * before, while the price does the opposite.
     */
    private fun tiers(
        family: String,
        kind: UpgradeKind,
        steps: List<Triple<Int, Int, UpgradeEffect>>,
    ): List<Upgrade> = steps.mapIndexed { index, (price, level, effect) ->
        Upgrade(
            id = idOf(family, index + 1),
            kind = kind,
            price = price,
            requiredLevel = level,
            effect = effect,
            family = family,
            tier = index + 1,
        )
    }

    val ROOM: List<Upgrade> =
        tiers(
            "fridge", UpgradeKind.ROOM,
            listOf(
                Triple(1_400, 3, UpgradeEffect(hungerDecay = 0.85f)),
                Triple(4_800, 8, UpgradeEffect(hungerDecay = 0.92f)),
                Triple(16_000, 12, UpgradeEffect(hungerDecay = 0.94f)),
                Triple(54_000, 16, UpgradeEffect(hungerDecay = 0.95f)),
                Triple(180_000, 19, UpgradeEffect(hungerDecay = 0.96f)),
            ),
        ) + tiers(
            "bed", UpgradeKind.ROOM,
            listOf(
                Triple(2_600, 5, UpgradeEffect(sleepSpeed = 1.35f)),
                Triple(8_800, 10, UpgradeEffect(sleepSpeed = 1.18f)),
                Triple(30_000, 14, UpgradeEffect(sleepSpeed = 1.14f)),
                Triple(100_000, 18, UpgradeEffect(sleepSpeed = 1.11f)),
                Triple(340_000, 21, UpgradeEffect(sleepSpeed = 1.09f)),
            ),
        ) + tiers(
            "console", UpgradeKind.ROOM,
            listOf(
                Triple(4_200, 7, UpgradeEffect(play = 1.5f)),
                Triple(14_000, 12, UpgradeEffect(play = 1.25f)),
                Triple(48_000, 16, UpgradeEffect(play = 1.20f)),
                Triple(160_000, 20, UpgradeEffect(play = 1.15f)),
                Triple(540_000, 23, UpgradeEffect(play = 1.12f)),
            ),
        ) + tiers(
            "cat", UpgradeKind.ROOM,
            listOf(
                Triple(7_500, 12, UpgradeEffect(neglect = 0.6f)),
                Triple(25_000, 17, UpgradeEffect(neglect = 0.85f)),
                Triple(85_000, 21, UpgradeEffect(neglect = 0.88f)),
                Triple(290_000, 25, UpgradeEffect(neglect = 0.90f)),
                Triple(980_000, 28, UpgradeEffect(neglect = 0.92f)),
            ),
        )

    val GEAR: List<Upgrade> =
        tiers(
            "coffee_machine", UpgradeKind.GEAR,
            listOf(
                Triple(1_900, 4, UpgradeEffect(energyDecay = 0.85f)),
                Triple(6_400, 9, UpgradeEffect(energyDecay = 0.92f)),
                Triple(22_000, 13, UpgradeEffect(energyDecay = 0.94f)),
                Triple(74_000, 17, UpgradeEffect(energyDecay = 0.95f)),
                Triple(250_000, 20, UpgradeEffect(energyDecay = 0.96f)),
            ),
        ) + tiers(
            "laptop", UpgradeKind.GEAR,
            listOf(
                Triple(5_000, 8, UpgradeEffect(pay = 1.2f)),
                Triple(17_000, 13, UpgradeEffect(pay = 1.12f)),
                Triple(58_000, 17, UpgradeEffect(pay = 1.10f)),
                Triple(195_000, 21, UpgradeEffect(pay = 1.08f)),
                Triple(660_000, 24, UpgradeEffect(pay = 1.07f)),
            ),
        ) + tiers(
            "textbooks", UpgradeKind.GEAR,
            listOf(
                Triple(6_500, 10, UpgradeEffect(study = 1.3f)),
                Triple(22_000, 15, UpgradeEffect(study = 1.15f)),
                Triple(74_000, 19, UpgradeEffect(study = 1.12f)),
                Triple(250_000, 23, UpgradeEffect(study = 1.10f)),
                Triple(840_000, 26, UpgradeEffect(study = 1.08f)),
            ),
        ) + tiers(
            "studio", UpgradeKind.GEAR,
            listOf(
                Triple(18_000, 16, UpgradeEffect(pay = 1.45f, study = 1.15f)),
                Triple(60_000, 21, UpgradeEffect(pay = 1.15f, study = 1.08f)),
                Triple(200_000, 25, UpgradeEffect(pay = 1.12f, study = 1.07f)),
                Triple(680_000, 29, UpgradeEffect(pay = 1.10f, study = 1.06f)),
                Triple(2_300_000, 30, UpgradeEffect(pay = 1.08f, study = 1.05f)),
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

    /**
     * The room itself, redecorated. The top of the money curve.
     *
     * Outfits top out at 25,000, which a level-twenty pet earns back inside a
     * week — after that the wallet only ever grows. These run to 80,000 and are
     * gated deep into the level curve, so there is still something to want at
     * the point where everything else is owned. Cosmetic on purpose: an
     * end-game purchase that also bought a multiplier would make the players
     * who cannot reach it play a worse game.
     */
    val THEMES: List<Upgrade> = listOf(
        Upgrade(DEFAULT_THEME, UpgradeKind.THEME, price = 0),
        Upgrade("theme_cozy", UpgradeKind.THEME, price = 20_000, requiredLevel = 8),
        Upgrade("theme_night", UpgradeKind.THEME, price = 45_000, requiredLevel = 14),
        Upgrade("theme_sakura", UpgradeKind.THEME, price = 80_000, requiredLevel = 20),
    )

    val ALL: List<Upgrade> = ROOM + GEAR + OUTFITS + THEMES

    /** The outfit she starts in, which is always owned and always free. */
    const val DEFAULT_OUTFIT = "outfit_uniform"

    /** The room she starts in — always owned and always free, like the uniform. */
    const val DEFAULT_THEME = "theme_default"

    fun byId(id: String?): Upgrade? = BY_ID[id]

    private val BY_ID: Map<String, Upgrade> = ALL.associateBy { it.id }

    /** Every tier of one thing, cheapest first. */
    val FAMILIES: Map<String, List<Upgrade>> =
        ALL.groupBy { it.family }.mapValues { (_, tiers) -> tiers.sortedBy { it.tier } }

    /** The families that do something, in the order the shop shows them. */
    val MECHANICAL: List<String> =
        (ROOM + GEAR).map { it.family }.distinct()

    /** Everything the given ids add up to. Unknown ids are ignored. */
    fun effectOf(ownedIds: Set<String>): UpgradeEffect =
        ownedIds.mapNotNull { byId(it)?.effect }
            .fold(UpgradeEffect.NONE) { acc, effect -> acc * effect }

    /** How far up [family] the given ids have already climbed; 0 for none. */
    fun tierOwned(ownedIds: Set<String>, family: String): Int =
        FAMILIES[family].orEmpty().count { ownedIds.contains(it.id) }

    /** The next step of [family] to buy, or null once it is finished. */
    fun nextTier(ownedIds: Set<String>, family: String): Upgrade? {
        val tiers = FAMILIES[family].orEmpty()
        return tiers.getOrNull(tierOwned(ownedIds, family))
    }

    /** What [family] is doing for her right now, all owned tiers folded in. */
    fun effectOfFamily(ownedIds: Set<String>, family: String): UpgradeEffect =
        FAMILIES[family].orEmpty()
            .filter { ownedIds.contains(it.id) }
            .fold(UpgradeEffect.NONE) { acc, upgrade -> acc * upgrade.effect }

    /** Total cost of owning everything — what the whole game is worth. */
    val totalCost: Int = ALL.sumOf { it.price }
}
