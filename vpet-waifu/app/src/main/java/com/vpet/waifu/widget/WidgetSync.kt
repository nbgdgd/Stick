package com.vpet.waifu.widget

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.vpet.waifu.data.PetRepository
import com.vpet.waifu.data.WallClock
import com.vpet.waifu.domain.PetSimulation
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.PetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlin.math.abs
import javax.inject.Inject
import javax.inject.Singleton

/** Books a redraw for an instant in the future. An interface so tests can observe it. */
fun interface WidgetWaker {
    fun wakeIn(delayMillis: Long)
}

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
 * A write is only one of three ways the picture goes stale, and only one of the
 * three was being handled:
 *
 *  1. **Something changed her.** The repository emits; the key below decides
 *     whether the change is one you could actually see.
 *  2. **Time passed.** A shift ends, she wakes up, she gets hungry — with
 *     nothing writing anything down, so nothing emits and nothing redraws.
 *     [PetSimulation.nextVisibleChangeAt] says when the picture is next due to
 *     change and a one-shot worker is booked for exactly that; the quarter-hour
 *     heartbeat in [com.vpet.waifu.work.PetTickWorker] was far too coarse to be
 *     the answer on its own.
 *  3. **You went to look at it.** The instant the app is backgrounded is the
 *     instant the widget is about to be on screen, so it is redrawn then
 *     whether or not anything changed.
 */
@Singleton
class WidgetSync @Inject constructor(
    private val repository: PetRepository,
    private val refresher: WidgetRefresher,
    private val simulation: PetSimulation,
    private val clock: WallClock,
    private val waker: WidgetWaker,
) {
    /**
     * Both halves run under a supervisor.
     *
     * Plain sibling coroutines die together, and the two halves here have very
     * different failure modes: following writes is a database read, while the
     * backgrounding hook depends on the process lifecycle owner having been
     * initialised at all. Letting the second take the first down with it would
     * turn a missing initialiser into a widget that never updates again — the
     * exact failure this class exists to prevent.
     */
    fun start(scope: CoroutineScope): Job = scope.launch {
        supervisorScope {
            launch { followWrites() }
            launch { runCatching { redrawWhenBackgrounded() } }
        }
    }

    private suspend fun followWrites() {
        var lastKey: String? = null
        var bookedFor = 0L
        repository.snapshot.collect { stored ->
            val now = clock.nowMillis()
            val advanced = simulation.advanceTo(stored, now)

            val key = widgetKey(advanced, now)
            if (key != lastKey) {
                lastKey = key
                refresher.refresh()
            }

            // Re-book only when the target has actually moved. The app writes
            // every three seconds while it is open, and re-enqueuing a unique
            // worker on each of those is a great deal of churn for no change.
            val next = simulation.nextVisibleChangeAt(advanced, now) ?: return@collect
            if (abs(next - bookedFor) > REBOOK_SLACK_MILLIS) {
                bookedFor = next
                waker.wakeIn(next - now)
            }
        }
    }

    private suspend fun redrawWhenBackgrounded() {
        withContext(Dispatchers.Main.immediate) {
            ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    awaitCancellation()
                } finally {
                    // Leaving the app is the one moment you are guaranteed to be
                    // looking at the home screen next.
                    refresher.refresh()
                }
            }
        }
    }

    private companion object {
        /** Half a minute of drift is not worth re-enqueuing a worker for. */
        const val REBOOK_SLACK_MILLIS = 30_000L
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
    // Plus what she is wearing and which job's pantomime she is doing: the two
    // other things that change the picture without changing her state.
    "${widgetState(advanced, nowMillis).name}|${advanced.outfit}|${advanced.session?.occupationId ?: "-"}"
