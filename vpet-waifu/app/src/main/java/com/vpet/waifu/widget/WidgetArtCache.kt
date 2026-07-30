package com.vpet.waifu.widget

/**
 * The last few rasterised widget images, kept in memory.
 *
 * A widget is re-provided far more often than it actually changes shape: every
 * point of hunger, every coin of a wage, every care action redraws it. What the
 * character looks like, though, depends only on her state and the size she is
 * being drawn at, so the same ten PNG frames were being encoded from scratch
 * dozens of times an hour.
 *
 * Small on purpose. Each entry is the whole loop and can approach the widget's
 * payload budget, so three of them is already about a megabyte — enough to hold
 * the state she is in, the one she just left, and one more size.
 */
internal class WidgetArtCache<V>(private val maxEntries: Int) {

    // accessOrder = true, so the eldest key is the least recently *used* rather
    // than the least recently added.
    private val entries = object : LinkedHashMap<String, V>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>): Boolean =
            size > maxEntries
    }

    /** Widgets are provided off the main thread and from several receivers. */
    @Synchronized
    fun getOrPut(key: String, produce: () -> V): V =
        entries[key] ?: produce().also { entries[key] = it }

    @Synchronized
    fun clear() = entries.clear()

    @Synchronized
    fun size(): Int = entries.size
}
