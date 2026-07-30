package com.stick.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

private val Context.sessionDataStore by preferencesDataStore(name = "tiktok_session")

/**
 * Stores the TikTok session cookies captured by the in-app login.
 *
 * The cookie string is a credential: it lives only in the app's private DataStore
 * (not backed up, not logged) and is sent solely to tiktok.com by
 * [com.stick.stickersource.tiktok.SessionInterceptor]. "Log out" wipes it.
 */
class TikTokSessionRepository(private val context: Context) {

    /** Emits the stored cookie header, or null when signed out. */
    val cookies: Flow<String?> = context.sessionDataStore.data.map { it[KEY_COOKIES] }

    /** True once a real session cookie is present. */
    val isSignedIn: Flow<Boolean> = cookies.map { !it.isNullOrBlank() && it.contains("sessionid") }

    suspend fun save(cookieHeader: String) {
        context.sessionDataStore.edit { it[KEY_COOKIES] = cookieHeader }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.remove(KEY_COOKIES) }
    }

    /**
     * Synchronous read for the OkHttp interceptor, which runs on a background
     * network thread and cannot suspend. Reads a small preference file, so the
     * blocking cost is negligible; failures degrade to "signed out".
     */
    fun cookiesBlocking(): String? = runCatching {
        runBlocking { context.sessionDataStore.data.first()[KEY_COOKIES] }
    }.getOrNull()

    private companion object {
        val KEY_COOKIES = stringPreferencesKey("cookies")
    }
}
