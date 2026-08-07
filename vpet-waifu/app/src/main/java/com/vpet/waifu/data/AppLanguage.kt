package com.vpet.waifu.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Which language the app speaks, regardless of what the phone speaks.
 *
 * Android has a per-app language picker built into Settings, but only from 13
 * onwards, and this app runs from 8. Leaving the choice to the system would
 * mean most of the people who want it cannot reach it — so the app carries its
 * own switch and applies it by wrapping the Context, which works everywhere.
 *
 * [SYSTEM] is the default and stays the default: an app that ignores the phone's
 * language on first launch is an app that guesses, and it guesses wrong for
 * anyone whose phone is set the way they want it.
 */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    RUSSIAN("ru"),
    ENGLISH("en"),
    ;

    companion object {
        fun of(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: SYSTEM

        /**
         * A copy of [base] that speaks [language].
         *
         * Called from `attachBaseContext`, which is the only hook early enough
         * that the first `stringResource` in the first composition already
         * reads the right file. Doing it later means a frame in the wrong
         * language, or a screen that only changes after a rotation.
         */
        fun wrap(base: Context, language: AppLanguage): Context {
            if (language == SYSTEM) return base
            val locale = Locale.forLanguageTag(language.tag)
            Locale.setDefault(locale)
            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)
            // setLayoutDirection too: both supported languages are LTR today,
            // but a Context configured with a locale and the wrong direction is
            // the kind of bug that only shows up when a third one is added.
            config.setLayoutDirection(locale)
            return base.createConfigurationContext(config)
        }
    }
}
