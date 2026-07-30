package com.vpet.waifu.widget

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Pushes a redraw to any placed widget. An interface so tests can observe it. */
fun interface WidgetRefresher {
    suspend fun refresh()
}

/**
 * The real one.
 *
 * Failures are logged rather than swallowed: a widget that silently stops
 * updating is exactly the bug this class exists to prevent, and a blanket
 * `runCatching` would hide it again.
 */
@Singleton
class GlanceWidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
) : WidgetRefresher {
    override suspend fun refresh() {
        try {
            PetWidget.refresh(context)
        } catch (error: Exception) {
            Log.w(TAG, "Could not refresh the pet widget", error)
        }
    }

    private companion object {
        const val TAG = "PetWidget"
    }
}
