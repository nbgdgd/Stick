package com.vpet.waifu.data

/**
 * Wall-clock time, behind an interface purely so tests can drive the pet's world
 * forward without sleeping.
 */
fun interface WallClock {
    fun nowMillis(): Long

    companion object {
        val SYSTEM = WallClock { System.currentTimeMillis() }
    }
}
