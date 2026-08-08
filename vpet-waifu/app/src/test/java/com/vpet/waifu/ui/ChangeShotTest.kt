package com.vpet.waifu.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.vpet.waifu.R
import com.vpet.waifu.domain.ActivityOutcome
import com.vpet.waifu.domain.OccupationKind
import com.vpet.waifu.domain.Occupations
import com.vpet.waifu.domain.OutcomeQuality
import com.vpet.waifu.domain.PetProgress
import com.vpet.waifu.domain.PetSimulation
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.PetState
import com.vpet.waifu.domain.PetStats
import com.vpet.waifu.domain.StakeTier
import com.vpet.waifu.domain.Upgrades
import com.vpet.waifu.ui.character.PetSkin
import com.vpet.waifu.ui.character.SpritePacks
import com.vpet.waifu.ui.components.PetStage
import com.vpet.waifu.ui.components.StakeBoard
import com.vpet.waifu.ui.components.StakeResult
import com.vpet.waifu.ui.components.UpgradeBoard
import com.vpet.waifu.ui.character.workPropFor
import com.vpet.waifu.ui.onboarding.ChooseSkinScreen
import com.vpet.waifu.ui.theme.Surfaces
import com.vpet.waifu.ui.theme.VPetTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Pictures of everything this change touched.
 *
 * Not assertions — a screenshot cannot tell you a layout is right, only a
 * person can. What it *can* do is make the person able to look, which for a
 * character rig, a collapsible panel and a dialog is the only honest way to
 * check that they work at all. Every one of these is the real composable with
 * a real snapshot behind it, not a mock-up.
 *
 * Writes to app/build/store-shots/ and runs only with `-Pstoreshots=1`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ru-rRU-w411dp-h891dp-xxhdpi")
class ChangeShotTest {

    private val now = 1_785_000_000_000L
    private val simulation = PetSimulation()

    private fun snapshot(money: Int = 60_000, owned: Set<String> = emptySet()) = PetSnapshot(
        stats = PetStats(hunger = 78f, energy = 64f, mood = 88f),
        progress = PetProgress(money = money, exp = 120_000),
        lastTickAt = now,
        lastInteractionAt = now - 4 * 60 * 1000,
        passiveSince = now,
        owned = setOf(Upgrades.DEFAULT_OUTFIT, Upgrades.DEFAULT_THEME) + owned,
        bondPoints = 210,
        totalEarned = 210_400,
        bornAt = now - 26L * 24 * 60 * 60 * 1000,
    )

    @Test
    fun `render the changed surfaces`() {
        if (System.getProperty("storeshots") == null) return

        // The upgrade board, closed and open. Half the families part-owned, so
        // the shot carries a bought level, a buyable one and one she cannot
        // reach — the three states the panel exists to tell apart.
        val partway = snapshot(
            money = 20_000,
            owned = setOf("fridge", "fridge_2", "bed", "coffee_machine", "laptop"),
        )
        shoot("chg-01-upgrades-closed", height = 260) {
            Box(Modifier.fillMaxWidth().padding(16.dp)) {
                UpgradeBoard(snapshot = partway, nowMillis = now, onBuy = {})
            }
        }
        shoot("chg-02-upgrades-open", height = 900) {
            Box(Modifier.fillMaxWidth().padding(16.dp)) {
                ExpandedUpgradeBoard(partway, now)
            }
        }

        // The betting table, and the wallet it is a fraction of.
        val working = simulation.startOccupation(
            snapshot(money = 12_400),
            Occupations.byId("idol")!!,
            now,
        )
        shoot("chg-03-stakes", height = 260) {
            Box(Modifier.fillMaxWidth().padding(16.dp)) {
                StakeBoard(
                    snapshot = working,
                    quality = OutcomeQuality.GREAT,
                    onStake = {},
                )
            }
        }

        // How a bet reads when it lands, and when it does not.
        listOf(
            "chg-04-stake-won" to outcome(stake = 3_100, returned = StakeTier.LARGE.winnings(3_100)),
            "chg-05-stake-lost" to outcome(stake = 3_100, returned = 0),
        ).forEach { (name, result) ->
            shoot(name, height = 220) {
                Box(Modifier.fillMaxWidth().padding(16.dp)) { StakeResult(result) }
            }
        }

        // Anya, if she is installed: the stage in four of her clips, so the
        // per-job animations can be seen to be different pictures rather than
        // the generic loop with a different caption.
        SpritePacks.load(
            androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            "anya",
        )?.let { pack ->
            listOf(
                "chg-06-anya-idle" to (PetState.IDLE to null),
                "chg-07-anya-cafe" to (PetState.WORKING to "cafe"),
                "chg-08-anya-idol" to (PetState.WORKING to "idol"),
                "chg-09-anya-university" to (PetState.STUDYING to "university"),
                "chg-10-anya-sleeping" to (PetState.SLEEPING to null),
            ).forEach { (name, spec) ->
                val (state, job) = spec
                shoot(name, height = 340) {
                    PetStage(
                        state = state,
                        skin = PetSkin.Sheet(pack),
                        workProp = workPropFor(job),
                        night = state == PetState.SLEEPING,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    )
                }
            }
        }

        // The picker, which is where a pack is chosen in the first place.
        shoot("chg-11-choose", height = 891) { ChooseSkinScreen(onChosen = {}) }

        // And the icon the launcher will actually draw.
        launcherIcon("chg-12-launcher", 512)
    }

    private fun outcome(stake: Int, returned: Int) = ActivityOutcome(
        occupationId = "idol",
        kind = OccupationKind.WORK,
        money = 620,
        exp = 45,
        quality = OutcomeQuality.GREAT,
        cancelled = false,
        completedAt = now,
        stake = stake,
        stakeReturned = returned,
        stakeTier = StakeTier.LARGE,
    )

    private fun shoot(name: String, height: Int, content: @Composable () -> Unit) {
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
        val heightPx = (height * density).toInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, widthPx, heightPx)
        shadowOf(Looper.getMainLooper()).idle()

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        write(name, bitmap)
        controller.destroy()
    }

    private fun launcherIcon(name: String, size: Int) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val drawable = ResourcesCompat.getDrawable(
            controller.get().resources,
            R.mipmap.ic_launcher,
            null,
        )!!
        drawable.setBounds(0, 0, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.draw(Canvas(bitmap))
        write(name, bitmap)
        controller.destroy()
    }

    private fun write(name: String, bitmap: Bitmap) {
        val dir = File("build/store-shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    private companion object {
        const val WIDTH_DP = 411
    }
}

/** The board as a shopping surface mounts it: already open. */
@Composable
private fun ExpandedUpgradeBoard(snapshot: PetSnapshot, now: Long) {
    UpgradeBoard(snapshot = snapshot, nowMillis = now, onBuy = {}, startExpanded = true)
}
