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
 * A redraw is not free: every frame of the character's loop is marshalled to
 * the launcher over Binder, so this deliberately ignores changes too small to
 * see. What she is *doing* is exact — pressing sleep must reach the widget on
 * the next write, and that is the one thing this exists to guarantee — while
 * the bars and the wallet move in visible steps.
 *
 * Wages now land every minute of a shift, so keeping money exact here meant a
 * full redraw once a minute for two hours; a step of [MONEY_STEP] keeps the
 * number honest without paying for a redraw per coin.
 */
internal fun widgetKey(snapshot: PetSnapshot): String = listOf(
    // The FSM state, not the raw activity: it also carries hungry, tired and
    // the transient emotes, which are exactly the faces the widget draws.
    snapshot.state(snapshot.lastTickAt).name,
    (snapshot.stats.hunger / STAT_STEP).roundToInt(),
    (snapshot.stats.energy / STAT_STEP).roundToInt(),
    (snapshot.stats.mood / STAT_STEP).roundToInt(),
    snapshot.level,
    snapshot.progress.money / MONEY_STEP,
    snapshot.session?.occupationId ?: "-",
).joinToString("|")

/** About a twentieth of a bar — narrower than the bar's own rounded cap. */
private const val STAT_STEP = 5f
private const val MONEY_STEP = 25
