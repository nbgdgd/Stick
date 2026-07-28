package com.trialtracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.trialtracker.app.data.model.Deal
import com.trialtracker.app.data.model.DealUi
import com.trialtracker.app.data.Settings
import com.trialtracker.app.ui.screens.CategoriesScreen
import com.trialtracker.app.ui.screens.DealListScreen
import com.trialtracker.app.ui.screens.HomeScreen
import com.trialtracker.app.ui.screens.SettingsScreen
import com.trialtracker.app.ui.theme.TT
import com.trialtracker.app.ui.theme.TrialTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the main screens on the JVM and writes PNGs to `app/build/screenshots`.
 * Not an assertion test — it exists so the layout can be eyeballed against the
 * design without an emulator in the loop.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class HomeScreenRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun renderHome() = capture("home") {
        HomeScreen(
            state = sampleState(),
            onOpenCategory = {},
            onOpenSearch = {},
            onOpenDeal = {},
            onToggleFavorite = {},
            onRefresh = {},
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        )
    }

    @Test
    fun renderTrials() = capture("trials") {
        DealListScreen(
            state = sampleState(),
            category = Category.TRIALS,
            onOpenDeal = {},
            onToggleFavorite = {},
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        )
    }

    @Test
    fun renderCategories() = capture("categories") {
        CategoriesScreen(
            state = sampleState(),
            onOpenCategory = {},
            onOpenApps = {},
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        )
    }

    @Test
    fun renderSettings() = capture("settings") {
        SettingsScreen(
            state = sampleState(),
            onNameChange = {},
            onShowSystemAppsChange = {},
            onNotificationsChange = {},
            onIntervalChange = {},
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        )
    }

    private fun capture(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        // Drive the clock by hand so a screen with a running animation still settles.
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            TrialTrackerTheme {
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxSize().background(TT.Background),
                ) { content() }
            }
        }
        composeRule.mainClock.advanceTimeBy(600)
        val file = File("build/screenshots/$name.png")
        file.parentFile?.mkdirs()
        composeRule.onRoot().captureRoboImage(file.path)
    }

    private fun sampleState(): UiState {
        val deals = listOf(
            deal("netflix", "Netflix", "com.netflix.mediaclient", "trial", "30 дней бесплатно",
                "Популярные фильмы и сериалы без рекламы.", "€13,99/мес", "#E50914", "N", 96),
            deal("spotify", "Spotify Premium", "com.spotify.music", "trial", "3 месяца бесплатно",
                "Музыка без рекламы, оффлайн и без ограничений.", "€10,99/мес", "#1DB954", "S", 98),
            deal("youtube", "YouTube Premium", "com.google.android.youtube", "discount", "Скидка 50%",
                "Смотрите без рекламы, в оффлайне и в фоне.", "€6,99/мес вместо €13,99",
                "#FF0033", "▶", 97, discount = 50),
            deal("duolingo", "Duolingo Super", "com.duolingo", "trial", "14 дней бесплатно",
                "Без рекламы, безлимит жизней и разбор ошибок.", "€12,99/мес", "#58CC02", "D", 92),
            deal("canva", "Canva Pro", "com.canva.editor", "trial", "30 дней бесплатно",
                "Премиум-шаблоны, фоны и удаление фона.", "€11,99/мес", "#00C4CC", "C", 90),
            deal("nord", "NordVPN", "com.nordvpn.android", "discount", "Скидка 70%",
                "VPN на 10 устройств и блокировка трекеров.", "€3,39/мес вместо €12,99",
                "#4687FF", "◈", 84, discount = 70),
        )
        return UiState(
            deals = deals,
            settings = Settings(userName = "Oleg", onboarded = true, lastSyncAt = 1_753_000_000_000L),
        )
    }

    @Suppress("LongParameterList")
    private fun deal(
        id: String,
        name: String,
        pkg: String,
        type: String,
        title: String,
        description: String,
        priceAfter: String,
        color: String,
        glyph: String,
        popularity: Int,
        discount: Int = 0,
    ) = DealUi(
        deal = Deal(
            id = id,
            packageName = pkg,
            appName = name,
            title = title,
            type = type,
            duration = "",
            description = description,
            priceAfter = priceAfter,
            discountPercent = discount,
            deepLink = "https://example.com",
            lastVerifiedDate = "2026-07-18",
            brandColor = color,
            glyph = glyph,
            popularity = popularity,
        ),
        installed = false,
        favorite = false,
    )
}
