package com.vpet.waifu.di

import javax.inject.Qualifier

/**
 * A coroutine scope that lives as long as the process, for work that must
 * outlive the component that started it — e.g. persisting "the player hid the
 * bubble" while the service that observed it is being torn down.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
