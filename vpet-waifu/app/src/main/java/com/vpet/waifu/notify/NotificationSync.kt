package com.vpet.waifu.notify

import com.vpet.waifu.data.PetRepository
import com.vpet.waifu.data.WallClock
import com.vpet.waifu.domain.PetSimulation
import com.vpet.waifu.domain.PetSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches the save file and raises anything worth telling the player about.
 *
 * Built the same way as the widget's refresher and for the same reason: asking
 * every caller to remember to notify is how one path quietly stops doing it.
 * The data changes, and this decides whether that change is news.
 *
 * It compares against the *advanced* snapshot rather than the stored row,
 * because "she just became hungry" is a transition in the real world, and the
 * stored row is only ever as fresh as the last write.
 */
@Singleton
class NotificationSync @Inject constructor(
    private val repository: PetRepository,
    private val notifier: PetNotifier,
    private val simulation: PetSimulation,
    private val clock: WallClock,
) {
    fun start(scope: CoroutineScope): Job = scope.launch {
        var previous: PetSnapshot? = null
        repository.snapshot
            .map { stored -> simulation.advanceTo(stored, clock.nowMillis()) }
            .collect { snapshot ->
                notifier.notifyChanges(previous, snapshot, clock.nowMillis())
                previous = snapshot
            }
    }
}
