package com.vpet.waifu.domain

/**
 * EXP → level. A quadratic curve: each level costs a bit more than the last, so
 * early study sessions land a level quickly and later ones are a real project.
 */
object Progression {

    const val MAX_LEVEL = 30

    /** Total EXP needed to *reach* [level]. Level 1 is free. */
    fun expForLevel(level: Int): Int {
        val l = level.coerceIn(1, MAX_LEVEL)
        return 40 * (l - 1) * l
    }

    fun levelForExp(exp: Int): Int {
        var level = 1
        while (level < MAX_LEVEL && exp >= expForLevel(level + 1)) level++
        return level
    }

    /** Progress inside the current level, as (earned, needed). */
    fun levelProgress(exp: Int): Pair<Int, Int> {
        val level = levelForExp(exp)
        if (level >= MAX_LEVEL) return 1 to 1
        val floor = expForLevel(level)
        return (exp - floor) to (expForLevel(level + 1) - floor)
    }
}

/** Money and EXP — the two things activities pay out in. */
data class PetProgress(
    val money: Int = START_MONEY,
    val exp: Int = 0,
) {
    val level: Int get() = Progression.levelForExp(exp)

    fun plus(money: Int = 0, exp: Int = 0): PetProgress = PetProgress(
        money = (this.money + money).coerceAtLeast(0),
        exp = (this.exp + exp).coerceAtLeast(0),
    )

    fun canAfford(price: Int): Boolean = money >= price

    companion object {
        /** Enough for a couple of meals before the first shift pays out. */
        const val START_MONEY = 150
    }
}
