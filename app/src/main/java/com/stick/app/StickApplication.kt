package com.stick.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Application entry point; enables Hilt's generated component graph. */
@HiltAndroidApp
class StickApplication : Application()
