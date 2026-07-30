package com.vpet.waifu.widget

import com.vpet.waifu.data.PetRepository
import com.vpet.waifu.domain.PetSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Keeps the home-screen widget in step with the save file.
 *
 * The widget follows the *data*, not the callers. Asking every surface — the
 * app, the bubble, the widget's own buttons, the background tick — to remember
 * to refresh is how "I pressed sleep and the widget still shows her awake"
 * happens: one path forgets, or one silently fails, and there is no second
 * chance. Observing the repository means any write from anywhere redraws the
 * widget exactly once.
 */
@Singleton
class WidgetSync @Inject constructor(
    private val repository: PetRepository,
    private val refresher: WidgetRefresher,
) {
    fun start(scope: CoroutineScope): Job = scope.launch {
        repository.snapshot
            .map(::widgetKey)
            .distinctUntilChanged()
            .collect { refresher.refresh() }
    }
}

/**
 * Everything the widget actually shows, as one comparable value.
 *
 * Stats are rounded because that is how they are displayed: in a quiet minute
 * the underlying floats drift constantly while the widget's face does not
 * change, and redrawing for that would be pure battery cost.
 */
internal fun widgetKey(snapshot: PetSnapshot): String = listOf(
    snapshot.activity.name,
    snapshot.stats.hunger.roundToInt(),
    snapshot.stats.energy.roundToInt(),
    snapshot.stats.mood.roundToInt(),
    snapshot.level,
    snapshot.progress.money,
    snapshot.session?.occupationId ?: "-",
).joinToString("|")
