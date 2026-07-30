package com.stick.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import dagger.hilt.android.HiltAndroidApp
import java.lang.ref.WeakReference

/** Application entry point; enables Hilt's generated component graph. */
@HiltAndroidApp
class StickApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(ActivityTracker)
    }

    /**
     * Tracks the visible activity so background components can attach a view to a
     * real window. An off-screen WebView only lays out and lazy-loads images when
     * it belongs to a window — detached, it renders nothing.
     */
    object ActivityTracker : ActivityLifecycleCallbacks {
        private var current: WeakReference<Activity>? = null

        val activity: Activity? get() = current?.get()

        override fun onActivityResumed(activity: Activity) {
            current = WeakReference(activity)
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (current?.get() === activity) current = null
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }
}
