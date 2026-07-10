package com.stick.app.ui

import java.util.Locale

/** Human-readable formatting helpers shared by the detail screens. */
object Formatters {
    fun bytes(size: Long): String {
        if (size < 1024) return "$size B"
        val kb = size / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.2f MB", mb)
    }

    fun duration(ms: Long): String {
        val seconds = ms / 1000.0
        return String.format(Locale.US, "%.2f s", seconds)
    }

    fun fps(fps: Float): String = String.format(Locale.US, "%.0f", fps)
}
