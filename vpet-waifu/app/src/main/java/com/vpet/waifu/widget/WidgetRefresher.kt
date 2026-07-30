package com.vpet.waifu.widget

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pushes a redraw to any placed widget.
 *
 * Wrapped in a class so callers can depend on it through Hilt, and failures are
 * swallowed: a widget that cannot be reached must never take down the action
 * that triggered it.
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun refresh() {
        runCatching { PetWidget.refresh(context) }
    }
}
