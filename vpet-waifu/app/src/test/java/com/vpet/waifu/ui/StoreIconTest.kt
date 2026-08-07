package com.vpet.waifu.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.res.ResourcesCompat
import com.vpet.waifu.R
import com.vpet.waifu.domain.PetState
import com.vpet.waifu.ui.character.PetFigure
import com.vpet.waifu.ui.character.PetSkin
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
 * The raw material for the store icon.
 *
 * The listing needs a square icon at every size from 32 up to 512, and the one
 * thing it must not be is a drawing of the character made for the store: the
 * girl on the icon has to be the girl the app actually opens with, or the page
 * is advertising something else. So she is rendered here from the live rig —
 * the same code the home screen runs — at a size big enough that every listing
 * size can be resampled down from it.
 *
 * Two files come out, both oversized and both cropped later by
 * `tools/icons/store_icon.py`:
 *
 *  - `icon-figure.png`, the character alone on transparency;
 *  - `icon-launcher.png`, the adaptive launcher icon as the system composes it,
 *    kept so the two can be compared rather than assumed to match.
 *
 * Runs only with `-Pstoreshots=1`, like the screenshots next door.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ru-rRU-w411dp-h891dp-xxhdpi")
class StoreIconTest {

    @Test
    fun `render the icon source art`() {
        if (System.getProperty("storeshots") == null) return

        // 200:280 is the classic rig's own field; drawing it at its own aspect
        // means no letterboxing to reason about when cropping the head out.
        render("icon-figure", FIGURE_WIDTH, FIGURE_HEIGHT) {
            PetFigure(
                skin = PetSkin.Classic,
                state = PetState.IDLE,
                modifier = Modifier.fillMaxSize(),
            )
        }

        renderLauncher("icon-launcher", MASTER)
    }

    /** One Compose tree, drawn at an exact pixel size onto transparency. */
    private fun render(name: String, width: Int, height: Int, content: @Composable () -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val view = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { VPetTheme { Box(Modifier.fillMaxSize()) { content() } } }
        }
        activity.setContentView(view)
        shadowOf(Looper.getMainLooper()).idle()

        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        shadowOf(Looper.getMainLooper()).idle()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        write(name, bitmap)
        controller.destroy()
    }

    /** The launcher icon itself, background and foreground, unmasked. */
    private fun renderLauncher(name: String, size: Int) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val drawable = ResourcesCompat.getDrawable(activity.resources, R.mipmap.ic_launcher, null)!!
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
        /** 512 is the largest size the listing asks for; 4x it resamples clean. */
        const val MASTER = 2048
        const val FIGURE_WIDTH = 2048
        const val FIGURE_HEIGHT = 2867 // 2048 * 280 / 200
    }
}
