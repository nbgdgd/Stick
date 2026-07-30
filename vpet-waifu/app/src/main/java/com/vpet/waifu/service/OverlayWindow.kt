package com.vpet.waifu.service

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.roundToInt

/**
 * Owns the `WindowManager` side of the bubble: the layout params, dragging, and
 * keeping the view inside the screen.
 *
 * Split out from the service so the service is only about lifecycle and state,
 * and so the fiddly geometry has one home.
 */
class OverlayWindow(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        // The only overlay type still allowed to third-party apps.
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // NOT_FOCUSABLE keeps the keyboard and back button with the app
        // underneath — the bubble must never steal input it isn't touching.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    private var view: View? = null

    val isShowing: Boolean get() = view != null

    /**
     * Adds [content] at a comfortable default position (right edge, upper
     * third) and keeps it on screen as it resizes — the view grows when the
     * panel opens, and near the bottom of the display that would otherwise push
     * the buttons out of reach.
     */
    fun show(content: View) {
        if (view != null) return

        val metrics = context.resources.displayMetrics
        params.x = (metrics.widthPixels * 0.72f).roundToInt()
        params.y = (metrics.heightPixels * 0.28f).roundToInt()

        content.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            // Re-clamping during layout would re-enter WindowManager, so defer.
            v.post { clampIntoScreen() }
        }

        windowManager.addView(content, params)
        view = content
    }

    fun moveBy(dx: Float, dy: Float) {
        val current = view ?: return
        params.x += dx.roundToInt()
        params.y += dy.roundToInt()
        clampParams(current)
        windowManager.updateViewLayout(current, params)
    }

    /** Snaps to whichever side edge is closer, the way chat heads do. */
    fun snapToNearestEdge() {
        val current = view ?: return
        val metrics = context.resources.displayMetrics
        val centerX = params.x + current.width / 2
        params.x = if (centerX < metrics.widthPixels / 2) {
            EDGE_MARGIN_PX
        } else {
            metrics.widthPixels - current.width - EDGE_MARGIN_PX
        }
        clampParams(current)
        windowManager.updateViewLayout(current, params)
    }

    fun hide() {
        val current = view ?: return
        view = null
        // The window can already be gone if the system tore it down first.
        runCatching { windowManager.removeView(current) }
    }

    private fun clampIntoScreen() {
        val current = view ?: return
        val previousX = params.x
        val previousY = params.y
        clampParams(current)
        if (previousX != params.x || previousY != params.y) {
            runCatching { windowManager.updateViewLayout(current, params) }
        }
    }

    private fun clampParams(current: View) {
        val metrics = context.resources.displayMetrics
        val maxX = (metrics.widthPixels - current.width).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - current.height).coerceAtLeast(0)
        params.x = params.x.coerceIn(0, maxX)
        params.y = params.y.coerceIn(0, maxY)
    }

    private companion object {
        const val EDGE_MARGIN_PX = 8
    }
}
