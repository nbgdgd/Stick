package com.vpet.waifu.domain

/**
 * One chapter of her story: a condition to meet, and what meeting it brings.
 *
 * Conditions read the snapshot and nothing else, so a chapter can never be
 * "missed" — whatever order things happen in, the moment the state qualifies,
 * the chapter completes.
 */
data class Chapter(
    val id: String,
    val condition: (PetSnapshot) -> Boolean,
    val rewardMoney: Int = 0,
    /** An outfit gifted on completion — hers even if she could never afford it. */
    val rewardOutfit: String? = null,
)

/**
 * The arc.
 *
 * At level 1 and level 30 she used to be exactly the same person, and there was
 * nothing to play *towards* — only to keep playing. The story is a spine of
 * eight chapters from stranger to family, each a concrete goal, each with a
 * reward, ending in an actual finale. Freeplay continues after it; the story
 * simply lets the game be finishable.
 */
object Story {

    val CHAPTERS: List<Chapter> = listOf(
        // Meeting her: the first few honest acts of care.
        Chapter("meeting", { it.bondPoints >= 5 }, rewardMoney = 50),
        // Her first wage.
        Chapter("first_shift", { it.shiftsWorked >= 1 }, rewardMoney = 100),
        // Study becomes a habit.
        Chapter("diligent", { it.lessonsDone >= 3 }, rewardMoney = 150),
        // The room stops being empty.
        Chapter("settling_in", { snapshot ->
            Upgrades.ALL.count { it.kind != UpgradeKind.OUTFIT && snapshot.owns(it.id) } >= 2
        }, rewardMoney = 300),
        // The first outfit she is *given*, not sold.
        Chapter("kindred", { Bond.levelFor(it.bondPoints) >= 4 }, rewardOutfit = "outfit_cocoa"),
        // Work is a career now.
        Chapter("professional", { it.shiftsWorked >= 15 && it.level >= 10 }, rewardMoney = 800),
        // She knows who she is.
        Chapter("her_own_path", { it.focus != null && it.level >= 15 }, rewardMoney = 1_500),
        // The finale.
        Chapter(
            "finale",
            { it.level >= Progression.MAX_LEVEL && Bond.levelFor(it.bondPoints) >= 8 },
            rewardMoney = 5_000,
            rewardOutfit = "outfit_gold",
        ),
    )

    /** True once the last chapter is done — the story has an actual end. */
    fun isComplete(chapter: Int): Boolean = chapter >= CHAPTERS.size
}
