package com.vpet.waifu.domain

/**
 * The one decision in the game that cannot be taken back.
 *
 * Everything else is strictly cumulative — every upgrade helps, every level
 * only adds — so there was never a moment of actually *choosing* anything.
 * A focus is a real fork: each path is better at one thing and measurably
 * worse at another, it is picked once, and it is permanent.
 */
enum class Focus {
    /** A career woman: work pays more, studying sticks less. */
    CAREER,

    /** A scholar: studying sticks better, work pays less. */
    SCHOLAR,

    /** A homebody: rest and play come easier; neither pay nor study excels. */
    HOMEBODY,
    ;

    companion object {
        /** She has to know herself a little before committing to a path. */
        const val UNLOCK_LEVEL = 6

        fun byName(name: String?): Focus? = entries.firstOrNull { it.name == name }
    }
}

fun Focus.effect(): UpgradeEffect = when (this) {
    Focus.CAREER -> UpgradeEffect(pay = 1.15f, study = 0.9f)
    Focus.SCHOLAR -> UpgradeEffect(study = 1.15f, pay = 0.9f)
    Focus.HOMEBODY -> UpgradeEffect(sleepSpeed = 1.15f, play = 1.2f, neglect = 0.85f, pay = 0.95f, study = 0.95f)
}
