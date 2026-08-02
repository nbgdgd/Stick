package com.vpet.waifu.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.vpet.waifu.domain.Occupations
import com.vpet.waifu.domain.PetProgress
import com.vpet.waifu.domain.PetSimulation
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.PetStats
import com.vpet.waifu.domain.PetTuning
import com.vpet.waifu.domain.Upgrades
import com.vpet.waifu.data.PetSettings
import com.vpet.waifu.ui.home.HomeScreen
import com.vpet.waifu.ui.profile.ProfileScreen
import com.vpet.waifu.ui.shop.ShopScreen
import com.vpet.waifu.ui.activities.ActivitiesScreen
import com.vpet.waifu.ui.theme.VPetTheme
import com.vpet.waifu.ui.theme.Surfaces
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Store screenshots, rendered from the real screens.
 *
 * Not a test of anything — it is the only honest way to produce listing
 * artwork from this container: the alternative is mocking up pictures of an
 * app that does not look like that. Robolectric's native graphics mode draws
 * the actual Compose tree; the clock is held still because the app is full of
 * infinite animations that would otherwise never let the frame settle.
 *
 * Writes to app/build/store-shots/ and is skipped unless -Pstoreshots is set.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ru-rRU-w411dp-h891dp-xxhdpi")
class StoreShotTest {

    private val simulation = PetSimulation()
    private val tuning = PetTuning()

    private val now = 1_785_000_000_000L

    /** A save that looks like a month of play rather than a fresh install. */
    private val snapshot = PetSnapshot(
        stats = PetStats(78f, 64f, 88f),
        progress = PetProgress(money = 4_820, exp = 6_400),
        lastTickAt = now,
        lastInteractionAt = now - 4 * 60 * 1000,
        passiveSince = now,
        owned = setOf(
            Upgrades.DEFAULT_OUTFIT, Upgrades.DEFAULT_THEME,
            "fridge", "bed", "laptop", "cat", "outfit_cocoa", "outfit_mint", "theme_cozy",
        ),
        bondPoints = 210,
        shiftsWorked = 34,
        lessonsDone = 21,
        gamesPlayed = 46,
        mealsFed = 88,
        giftsGiven = 12,
        totalEarned = 21_400,
        bornAt = now - 26L * 24 * 60 * 60 * 1000,
        storyChapter = 5,
        storySeen = 5,
        streakDays = 6,
        bestStreak = 9,
    )

    @Test
    fun `render the store screenshots`() {
        if (System.getProperty("storeshots") == null) return
        shoot("01-home") {
            HomeScreen(
                snapshot = snapshot,
                simulation = simulation,
                tuning = tuning,
                nowMillis = now,
                settings = PetSettings(petName = "Юки", lastSeenAt = now),
                wallet = snapshot.progress.money,
                walletSettled = true,
                onOpenSettings = {}, onNameChange = {}, onFeed = {}, onPet = {}, onTapPet = {},
                onCoinLanded = {}, onExpLanded = {}, onToggleSleep = {}, onCancelOccupation = {},
                onDismissEvent = {}, onBuy = {}, onAcknowledgeStory = {}, onAcknowledgeDaily = {}, onSeen = {},
            )
        }
        shoot("02-work") {
            ActivitiesScreen(
                snapshot = snapshot,
                simulation = simulation,
                tuning = tuning,
                nowMillis = now,
                wallet = snapshot.progress.money,
                walletSettled = true,
                onStart = {}, onCancel = {}, onCategoryTap = {},
            )
        }
        shoot("03-shop") {
            ShopScreen(
                snapshot = snapshot,
                nowMillis = now,
                wallet = snapshot.progress.money,
                walletSettled = true,
                onBuy = {}, onBuyUpgrade = {}, onWear = {}, onApplyTheme = {}, onCategoryTap = {},
            )
        }
        shoot("04-her") {
            ProfileScreen(
                snapshot = snapshot,
                petName = "Юки",
                nowMillis = now,
                onChooseFocus = {}, onWear = {}, onCategoryTap = {},
            )
        }
    }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val view = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                VPetTheme {
                    Box(Modifier.fillMaxSize().background(Surfaces.Screen)) { content() }
                }
            }
        }
        activity.setContentView(view)
        shadowOf(Looper.getMainLooper()).idle()

        val density = activity.resources.displayMetrics.density
        val widthPx = (WIDTH_DP * density).toInt()
        val heightPx = (HEIGHT_DP * density).toInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, widthPx, heightPx)
        shadowOf(Looper.getMainLooper()).idle()

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/store-shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        controller.destroy()
    }

    private companion object {
        const val WIDTH_DP = 411
        const val HEIGHT_DP = 891
    }
}
