package com.vpet.waifu.widget

import com.vpet.waifu.data.PetRepository
import com.vpet.waifu.data.WallClock
import com.vpet.waifu.domain.PetSimulation
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.PetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the home-screen widget in step with the save file.
 *
 * The widget follows the *data*, not the callers. Asking every surface — the
 * app, the bubble, the widget's own buttons, the background tick — to remember
 * to refresh is how "I pressed sleep and the widget still shows her awake"
 * happens: one path forgets, or one silently fails, and there is no second
 * chance. Observing the repository means any write from anywhere redraws the
 * widget exactly once.
 *
 * This is only half of it. A write is not the only way the widget can go
 * stale — see [com.vpet.waifu.work.PetTickWorker], which redraws on a timer
 * because she keeps living whether or not anything is writing her down.
 */
@Singleton
class WidgetSync @Inject constructor(
    private val repository: PetRepository,
    private val refresher: WidgetRefresher,
    private val simulation: PetSimulation,
    private val clock: WallClock,
) {
    fun start(scope: CoroutineScope): Job = scope.launch {
        repository.snapshot
            .map { stored ->
                val now = clock.nowMillis()
                widgetKey(simulation.advanceTo(stored, now), now)
            }
            .distinctUntilChanged()
            .collect { refresher.refresh() }
    }
}

/**
 * What the widget draws.
 *
 * Her resting state, with the five-second reactions — eating, being petted,
 * celebrating — deliberately left out. Nothing pushes a redraw to a home screen
 * on a five-second deadline, so honouring them would only mean catching her
 * mid-bite and leaving her there until the next refresh, minutes later. The
 * reactions belong on the surfaces that can actually animate them: the app and
 * the floating bubble.
 */
internal fun widgetState(snapshot: PetSnapshot, nowMillis: Long): PetState =
    snapshot.copy(emote = null, emoteUntil = 0L).state(nowMillis)

/**
 * What the widget draws, as one comparable value.
 *
 * The widget is the character and nothing else now, so this is exactly her
 * animation state — no bars, no wallet, nothing that can change without
 * changing the picture. That makes it both the most responsive key possible
 * (anything you can see redraws immediately) and the cheapest: a point of
 * hunger, or a coin landing every minute of a shift, no longer marshals ten PNG
 * frames to the launcher to produce an identical image.
 *
 * The snapshot handed in must already be advanced to [nowMillis]: the stored
 * row is only ever as fresh as the last write, and the widget renders the
 * advanced world.
 */
internal fun widgetKey(advanced: PetSnapshot, nowMillis: Long): String =
    widgetState(advanced, nowMillis).name
