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
import com.vpet.waifu.domain.PetState
import com.vpet.waifu.ui.character.PetSkin
import com.vpet.waifu.ui.character.Prop
import com.vpet.waifu.ui.character.SpritePacks
import com.vpet.waifu.ui.character.workPropFor
import com.vpet.waifu.ui.components.PetStage
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
 * The character on the stage, one picture per clip.
 *
 * Split out from the rest of the shots because it is slow — every frame here
 * also renders the quantised room behind her, which is a full offscreen bitmap
 * pass, and Robolectric does it in software. Six minutes a shot is fine for
 * something run by hand after the art changes and unbearable in a suite you
 * want to re-run while iterating on a dialog.
 *
 * Runs only with `-Pstoreshots=1`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ru-rRU-w411dp-h891dp-xxhdpi")
class SpriteStageShotTest {

    @Test
    fun `render her clips`() {
        if (System.getProperty("storeshots") == null) return
        val pack = SpritePacks.load(
            androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            "anya",
        ) ?: return

        // Every state and every job, so the per-job animations can be seen to
        // be different pictures rather than the generic loop with a different
        // caption — and the two ways she sleeps can be told apart.
        val shots = listOf(
            Triple("sprite-01-idle", PetState.IDLE, null),
            Triple("sprite-02-cafe", PetState.WORKING, workPropFor("cafe")),
            Triple("sprite-03-shop", PetState.WORKING, workPropFor("shop")),
            Triple("sprite-04-office", PetState.WORKING, workPropFor("office")),
            Triple("sprite-05-idol", PetState.WORKING, workPropFor("idol")),
            Triple("sprite-06-school", PetState.STUDYING, workPropFor("school")),
            Triple("sprite-07-course", PetState.STUDYING, workPropFor("course")),
            Triple("sprite-08-university", PetState.STUDYING, workPropFor("university")),
            Triple("sprite-09-sleeping", PetState.SLEEPING, null),
            Triple("sprite-10-sleeping-bed", PetState.SLEEPING, Prop.PILLOW),
            Triple("sprite-11-eating", PetState.EATING, null),
            Triple("sprite-12-playing", PetState.PLAYING, null),
            Triple("sprite-13-sick", PetState.SICK, null),
            Triple("sprite-14-celebrating", PetState.CELEBRATING, null),
        )

        shots.forEach { (name, state, prop) ->
            shoot(name) {
                PetStage(
                    state = state,
                    skin = PetSkin.Sheet(pack),
                    workProp = prop,
                    night = state == PetState.SLEEPING,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
            }
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
        val widthPx = (411 * density).toInt()
        val heightPx = (340 * density).toInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, widthPx, heightPx)
        shadowOf(Looper.getMainLooper()).idle()

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File("build/store-shots").apply { mkdirs() }
        File("build/store-shots/$name.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        controller.destroy()
    }
}
