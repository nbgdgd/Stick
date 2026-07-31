package com.vpet.waifu.widget

import android.content.Context
import com.vpet.waifu.work.PetWakeWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The real one: a one-shot WorkManager job. */
@Singleton
class WorkManagerWidgetWaker @Inject constructor(
    @ApplicationContext private val context: Context,
) : WidgetWaker {
    override fun wakeIn(delayMillis: Long) = PetWakeWorker.scheduleAt(context, delayMillis)
}
