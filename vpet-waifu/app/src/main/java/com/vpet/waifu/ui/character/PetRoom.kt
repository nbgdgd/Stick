package com.vpet.waifu.ui.character

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.sin

/**
 * How much of the room to furnish.
 *
 * The widget lays its stat bars over the floor, so anything standing on it
 * would be read as clutter behind text; the app's stage has the whole scene to
 * itself.
 */
enum class RoomDetail {
    /** Bare walls — for a widget too small for anything but the character. */
    NONE,

    /** Window, neon sign and shelf. Nothing on the floor. */
    WALL,

    /** Everything, including the plant and the nightstand. */
    FULL,
}

/**
 * The room she lives in.
 *
 * Everything is positioned as a fraction of the canvas, so the same scene draws
 * at a 320dp stage in the app and inside a home-screen widget's bitmap. It
 * lives here rather than inside the Compose screen precisely so the widget's
 * rasteriser can produce the identical room — the widget is a snapshot of the
 * same scene, not a second design to keep in step by hand.
 */
fun DrawScope.drawPetRoom(
    top: Color,
    bottom: Color,
    floor: Color,
    night: Boolean,
    cornerRadiusPx: Float = 0f,
    /**
     * Where the wall meets the floor, as a fraction of the height.
     *
     * The widget stacks the character over the room rather than inside it, so
     * the junction has to be pulled up to just behind her feet — left at the
     * default she would appear to float halfway up the wall.
     */
    floorFraction: Float = 0.78f,
    detail: RoomDetail = RoomDetail.FULL,
    /**
     * Upgrade ids the player owns, so the money shows.
     *
     * Buying a fridge used to change a multiplier and nothing else — thousands
     * of coins that never became anything you could look at. Every owned ROOM
     * and GEAR upgrade now stands in the room, in the app and in the widget
     * alike, because the room is the receipt.
     */
    decor: Set<String> = emptySet(),
) {
    val body: DrawScope.() -> Unit = {
        drawRect(Brush.verticalGradient(listOf(top, bottom)))

        val wall = size.height * floorFraction.coerceIn(0.2f, 0.95f)
        drawRect(color = floor, topLeft = Offset(0f, wall), size = Size(size.width, size.height - wall))
        // A soft skirting line stops the floor from reading as a flat block.
        drawLine(
            color = if (night) Color(0x33FFFFFF) else Color(0x22000000),
            start = Offset(0f, wall),
            end = Offset(size.width, wall),
            strokeWidth = size.height * 0.006f,
        )

        if (detail != RoomDetail.NONE) {
            drawWindow(wall, night)
            drawNeonHeart(wall, night)
            drawShelf(wall, night)
            // Her gear lives on its own shelf under the window — wall-mounted,
            // so even the widget's floorless cut of the room can show it.
            drawGearShelf(wall, night, decor)
        }
        if (detail == RoomDetail.FULL) {
            drawPlant(wall, night)
            drawNightstand(wall, night)
            drawFloorDecor(wall, night, decor)
        }
    }

    if (cornerRadiusPx <= 0f) {
        body()
        return
    }
    // The widget's bitmap has to carry its own rounded corners: an ImageView
    // inside a RemoteViews tree cannot be clipped by its parent.
    val clip = Path().apply {
        addRoundRect(
            RoundRect(Rect(0f, 0f, size.width, size.height), CornerRadius(cornerRadiusPx, cornerRadiusPx)),
        )
    }
    clipPath(clip) { body() }
}

/** A window onto a city, with the sun or the moon over the rooftops. */
private fun DrawScope.drawWindow(wall: Float, night: Boolean) {
    val x = size.width * 0.07f
    val y = wall * 0.13f
    val w = size.width * 0.27f
    val h = wall * 0.60f
    val radius = w * 0.07f
    val frame = if (night) Color(0xFF483C74) else Color(0xFFFFFFFF)
    val sky = if (night) Color(0xFF141031) else Color(0xFFBFE4FA)

    drawRoundRect(sky, Offset(x, y), Size(w, h), CornerRadius(radius, radius))

    clipPath(
        Path().apply {
            addRoundRect(RoundRect(Rect(x, y, x + w, y + h), CornerRadius(radius, radius)))
        },
    ) {
        drawCircle(
            color = if (night) Color(0xFFF4ECD0) else Color(0xFFFFE08A),
            radius = w * 0.15f,
            center = Offset(x + w * 0.68f, y + h * 0.24f),
        )
        if (night) {
            repeat(7) { i ->
                val sx = x + w * (0.1f + 0.12f * i)
                val sy = y + h * (0.1f + 0.3f * ((sin(i * 2.3f) + 1f) / 2f))
                drawCircle(Color.White.copy(alpha = 0.75f), radius = w * 0.014f, center = Offset(sx, sy))
            }
        }
        // Rooftops: a skyline is what turns a coloured rectangle into a view.
        val towers = listOf(0.02f to 0.34f, 0.20f to 0.52f, 0.40f to 0.28f, 0.58f to 0.46f, 0.80f to 0.38f)
        towers.forEach { (left, tall) ->
            val tx = x + w * left
            val tw = w * 0.17f
            val th = h * tall
            drawRect(
                color = if (night) Color(0xFF221C4A) else Color(0xFF9CC6E8),
                topLeft = Offset(tx, y + h - th),
                size = Size(tw, th),
            )
            // Lit windows.
            repeat(3) { row ->
                repeat(2) { col ->
                    val lit = (row + col + (left * 10).toInt()) % 3 != 0
                    if (!lit) return@repeat
                    drawRect(
                        color = if (night) Color(0xCCFFE9A8) else Color(0x33FFFFFF),
                        topLeft = Offset(tx + tw * (0.18f + col * 0.42f), y + h - th + h * (0.06f + row * 0.09f)),
                        size = Size(tw * 0.22f, h * 0.05f),
                    )
                }
            }
        }
    }

    drawRoundRect(
        frame, Offset(x, y), Size(w, h),
        CornerRadius(radius, radius),
        style = Stroke(width = w * 0.05f),
    )
    drawLine(frame, Offset(x + w / 2, y), Offset(x + w / 2, y + h), strokeWidth = w * 0.04f)
}

/** A neon heart on the wall — the one light source that reads instantly. */
private fun DrawScope.drawNeonHeart(wall: Float, night: Boolean) {
    val center = Offset(size.width * 0.79f, wall * 0.22f)
    val r = size.width * 0.045f
    val glow = if (night) 0.55f else 0.3f
    listOf(2.2f to 0.10f, 1.6f to 0.18f).forEach { (scale, alpha) ->
        drawPath(heartPath(center, r * scale), Color(0xFFFF6FA5).copy(alpha = alpha * glow * 2f))
    }
    drawPath(
        heartPath(center, r),
        Color(0xFFFF6FA5),
        style = Stroke(width = r * 0.28f, cap = StrokeCap.Round),
    )
}

/** A shelf of books. */
private fun DrawScope.drawShelf(wall: Float, night: Boolean) {
    val left = size.width * 0.63f
    val right = size.width * 0.95f
    val y = wall * 0.45f
    val plank = if (night) Color(0xFF4A3D78) else Color(0xFFCDB9EC)
    drawRoundRect(
        plank,
        Offset(left, y),
        Size(right - left, wall * 0.035f),
        CornerRadius(wall * 0.015f, wall * 0.015f),
    )

    val spines = listOf(
        Color(0xFFE8577E), Color(0xFF6FC6F5), Color(0xFFF5C542),
        Color(0xFF8B5FD6), Color(0xFF5FD08A),
    )
    val bookW = (right - left) / 7f
    spines.forEachIndexed { i, colour ->
        val h = wall * (0.10f + 0.02f * ((i * 3) % 3))
        drawRoundRect(
            colour.copy(alpha = if (night) 0.85f else 1f),
            Offset(left + bookW * (0.5f + i), y - h),
            Size(bookW * 0.72f, h),
            CornerRadius(bookW * 0.12f, bookW * 0.12f),
        )
    }
}

/** A potted plant beside her. */
private fun DrawScope.drawPlant(wall: Float, night: Boolean) {
    val x = size.width * 0.10f
    val base = wall + (size.height - wall) * 0.42f
    val potW = size.width * 0.085f
    val potH = potW * 0.85f
    val leaf = if (night) Color(0xFF3FA277) else Color(0xFF56C596)

    listOf(-1f to 0.9f, 0f to 1.15f, 1f to 0.85f).forEach { (dir, tall) ->
        val tip = Offset(x + dir * potW * 0.42f, base - potH * tall * 1.5f)
        val path = Path().apply {
            moveTo(x, base - potH * 0.4f)
            quadraticTo(x + dir * potW * 0.75f, base - potH * tall * 0.9f, tip.x, tip.y)
            quadraticTo(x + dir * potW * 0.08f, base - potH * tall * 0.8f, x, base - potH * 0.4f)
            close()
        }
        drawPath(path, leaf)
    }

    val pot = Path().apply {
        moveTo(x - potW * 0.5f, base - potH)
        lineTo(x + potW * 0.5f, base - potH)
        lineTo(x + potW * 0.36f, base)
        lineTo(x - potW * 0.36f, base)
        close()
    }
    drawPath(pot, if (night) Color(0xFF6B5AA5) else Color(0xFFB79BE0))
    drawRoundRect(
        if (night) Color(0xFF7E6BBD) else Color(0xFFC9B2EE),
        Offset(x - potW * 0.54f, base - potH),
        Size(potW * 1.08f, potH * 0.22f),
        CornerRadius(potH * 0.08f, potH * 0.08f),
    )
}

/** A nightstand with a cat plush on top. */
private fun DrawScope.drawNightstand(wall: Float, night: Boolean) {
    val cx = size.width * 0.845f
    val w = size.width * 0.19f
    val h = (size.height - wall) * 0.62f
    val topY = wall + (size.height - wall) * 0.16f
    val wood = if (night) Color(0xFF4A3D78) else Color(0xFFCDB9EC)

    drawRoundRect(
        wood, Offset(cx - w / 2, topY), Size(w, h),
        CornerRadius(w * 0.10f, w * 0.10f),
    )
    repeat(2) { i ->
        drawRoundRect(
            (if (night) Color(0xFF5C4D91) else Color(0xFFDDCDF5)),
            Offset(cx - w * 0.38f, topY + h * (0.16f + i * 0.38f)),
            Size(w * 0.76f, h * 0.26f),
            CornerRadius(w * 0.06f, w * 0.06f),
        )
    }

    // The plush.
    val catR = w * 0.30f
    val catC = Offset(cx, topY - catR * 0.85f)
    val fur = if (night) Color(0xFF8C7BC7) else Color(0xFFCBB8F0)
    listOf(-1f, 1f).forEach { side ->
        val ear = Path().apply {
            moveTo(catC.x + side * catR * 0.66f, catC.y - catR * 0.42f)
            lineTo(catC.x + side * catR * 0.30f, catC.y - catR * 1.25f)
            lineTo(catC.x + side * catR * 0.08f, catC.y - catR * 0.62f)
            close()
        }
        drawPath(ear, fur)
    }
    drawCircle(fur, radius = catR, center = catC)
    listOf(-1f, 1f).forEach { side ->
        drawCircle(
            Color(0xFF3B2B52),
            radius = catR * 0.11f,
            center = Offset(catC.x + side * catR * 0.34f, catC.y - catR * 0.05f),
        )
    }
}

/**
 * The gear shelf: everything from the shop's "gear" section, on one plank
 * under the window. Appears with the first owned item so an empty shelf never
 * hangs there promising things.
 */
private fun DrawScope.drawGearShelf(wall: Float, night: Boolean, decor: Set<String>) {
    val slots = listOf("coffee_machine", "laptop", "textbooks", "studio").filter { it in decor }
    if (slots.isEmpty()) return

    val left = size.width * 0.05f
    val right = size.width * 0.33f
    val y = wall * 0.86f
    val plank = if (night) Color(0xFF4A3D78) else Color(0xFFCDB9EC)
    drawRoundRect(
        plank,
        Offset(left, y),
        Size(right - left, wall * 0.03f),
        CornerRadius(wall * 0.012f, wall * 0.012f),
    )

    val itemH = wall * 0.105f
    // Fixed positions per item, not packed: things do not shuffle sideways
    // when a new one is bought.
    val slotX = mapOf(
        "coffee_machine" to 0.065f,
        "laptop" to 0.135f,
        "textbooks" to 0.215f,
        "studio" to 0.29f,
    )
    slots.forEach { id ->
        val x = size.width * (slotX[id] ?: 0.065f)
        when (id) {
            "coffee_machine" -> {
                val w = size.width * 0.052f
                drawRoundRect(
                    if (night) Color(0xFF8C7BC7) else Color(0xFFB79BE0),
                    Offset(x, y - itemH),
                    Size(w, itemH),
                    CornerRadius(w * 0.2f, w * 0.2f),
                )
                // The spout and a little cup under it.
                drawRect(Color(0xFF3B2B52), Offset(x + w * 0.35f, y - itemH * 0.45f), Size(w * 0.3f, itemH * 0.14f))
                drawRoundRect(
                    Color(0xFFF5EFFD),
                    Offset(x + w * 0.3f, y - itemH * 0.26f),
                    Size(w * 0.4f, itemH * 0.24f),
                    CornerRadius(w * 0.1f, w * 0.1f),
                )
            }
            "laptop" -> {
                val w = size.width * 0.062f
                // Open wedge: lit screen leaning back, base flat on the plank.
                val screen = Path().apply {
                    moveTo(x + w * 0.12f, y - itemH * 0.95f)
                    lineTo(x + w * 0.82f, y - itemH * 0.95f)
                    lineTo(x + w * 0.72f, y - itemH * 0.18f)
                    lineTo(x + w * 0.02f, y - itemH * 0.18f)
                    close()
                }
                drawPath(screen, if (night) Color(0xFFAFE6FF) else Color(0xFF7FD1E8))
                drawRoundRect(
                    Color(0xFF3B2B52),
                    Offset(x, y - itemH * 0.2f),
                    Size(w, itemH * 0.2f),
                    CornerRadius(w * 0.08f, w * 0.08f),
                )
            }
            "textbooks" -> {
                listOf(
                    Color(0xFFE8577E) to 0.0f,
                    Color(0xFFF5C542) to 0.34f,
                    Color(0xFF6FC6F5) to 0.68f,
                ).forEach { (colour, dy) ->
                    drawRoundRect(
                        colour.copy(alpha = if (night) 0.85f else 1f),
                        Offset(x, y - itemH * (1f - dy) ),
                        Size(size.width * 0.05f, itemH * 0.3f),
                        CornerRadius(itemH * 0.06f, itemH * 0.06f),
                    )
                }
            }
            "studio" -> {
                val cx = x + size.width * 0.016f
                // Mic on a desk stand, with a little pop shield ring.
                drawLine(Color(0xFF3B2B52), Offset(cx, y), Offset(cx, y - itemH * 0.55f), strokeWidth = size.width * 0.006f)
                drawCircle(
                    if (night) Color(0xFF8C7BC7) else Color(0xFF6B5CA5),
                    radius = itemH * 0.3f,
                    center = Offset(cx, y - itemH * 0.78f),
                )
                drawCircle(
                    Color(0xFFCBAEF7).copy(alpha = 0.8f),
                    radius = itemH * 0.19f,
                    center = Offset(cx, y - itemH * 0.8f),
                )
            }
        }
    }
}

/**
 * The floor pieces: the mini-fridge, the cushion corner with a console, and
 * the cat — each standing exactly where its shop card promised a home.
 */
private fun DrawScope.drawFloorDecor(wall: Float, night: Boolean, decor: Set<String>) {
    val floorH = size.height - wall

    if ("fridge" in decor) {
        val x = size.width * 0.185f
        val w = size.width * 0.115f
        val topY = wall - floorH * 0.30f
        val h = floorH * 0.92f
        val bodyColor = if (night) Color(0xFF9DB8E8) else Color(0xFFD7E8FA)
        drawRoundRect(bodyColor, Offset(x, topY), Size(w, h), CornerRadius(w * 0.16f, w * 0.16f))
        // Door split, handle, and a heart magnet.
        drawLine(
            if (night) Color(0xFF5C6FA0) else Color(0xFFA9C0DE),
            Offset(x, topY + h * 0.38f),
            Offset(x + w, topY + h * 0.38f),
            strokeWidth = h * 0.02f,
        )
        drawRoundRect(
            Color(0xFF3B2B52),
            Offset(x + w * 0.74f, topY + h * 0.14f),
            Size(w * 0.08f, h * 0.16f),
            CornerRadius(w * 0.04f, w * 0.04f),
        )
        drawPath(heartPath(Offset(x + w * 0.34f, topY + h * 0.6f), w * 0.1f), Color(0xFFFF6FA5))
    }

    if ("bed" in decor) {
        // Her comfy corner: two plump cushions between her and the nightstand.
        val cx = size.width * 0.705f
        val w = size.width * 0.1f
        val base = wall + floorH * 0.55f
        listOf(
            Color(0xFFF2A0C8) to 0f,
            Color(0xFFB79BE0) to 1f,
        ).forEach { (colour, level) ->
            drawRoundRect(
                if (night) colour.copy(alpha = 0.75f) else colour,
                Offset(cx - w / 2, base - floorH * 0.24f * (level + 1)),
                Size(w, floorH * 0.26f),
                CornerRadius(w * 0.3f, w * 0.3f),
            )
        }
    }

    if ("console" in decor) {
        // The little box and its controller, at the nightstand's foot.
        val x = size.width * 0.755f
        val w = size.width * 0.07f
        val y = wall + floorH * 0.72f
        drawRoundRect(
            if (night) Color(0xFF483C74) else Color(0xFF6B5CA5),
            Offset(x, y),
            Size(w, floorH * 0.16f),
            CornerRadius(w * 0.12f, w * 0.12f),
        )
        drawCircle(Color(0xFF56C596), radius = floorH * 0.035f, center = Offset(x + w * 0.8f, y + floorH * 0.08f))
        // The controller resting against it.
        drawRoundRect(
            if (night) Color(0xFF8C7BC7) else Color(0xFFCBB8F0),
            Offset(x - w * 0.5f, y + floorH * 0.04f),
            Size(w * 0.44f, floorH * 0.12f),
            CornerRadius(w * 0.14f, w * 0.14f),
        )
    }

    if ("cat" in decor) {
        // A real cat — distinct from the nightstand's plush — sitting by the
        // fridge like they always do.
        val cx = size.width * 0.155f
        val base = wall + floorH * 0.66f
        val r = size.width * 0.045f
        val fur = if (night) Color(0xFFC9B4E8) else Color(0xFFF0E4FA)
        // The tail first, curled around the body.
        drawLine(
            fur,
            Offset(cx + r * 0.9f, base - r * 0.2f),
            Offset(cx + r * 1.9f, base - r * 1.1f),
            strokeWidth = r * 0.42f,
            cap = StrokeCap.Round,
        )
        // Sitting body: a pear shape.
        drawCircle(fur, radius = r, center = Offset(cx, base - r * 0.75f))
        drawCircle(fur, radius = r * 0.72f, center = Offset(cx, base - r * 1.9f))
        listOf(-1f, 1f).forEach { side ->
            val ear = Path().apply {
                moveTo(cx + side * r * 0.62f, base - r * 2.25f)
                lineTo(cx + side * r * 0.32f, base - r * 2.95f)
                lineTo(cx + side * r * 0.08f, base - r * 2.35f)
                close()
            }
            drawPath(ear, fur)
        }
        // Closed contented eyes and a tiny nose.
        val eyeY = base - r * 1.92f
        listOf(-1f, 1f).forEach { side ->
            drawLine(
                Color(0xFF3B2B52),
                Offset(cx + side * r * 0.38f - r * 0.12f, eyeY),
                Offset(cx + side * r * 0.38f + r * 0.12f, eyeY - r * 0.1f),
                strokeWidth = r * 0.09f,
                cap = StrokeCap.Round,
            )
        }
        drawCircle(Color(0xFFF98FAE), radius = r * 0.09f, center = Offset(cx, base - r * 1.72f))
    }
}

private fun heartPath(center: Offset, r: Float): Path = Path().apply {
    moveTo(center.x, center.y + r * 0.95f)
    cubicTo(
        center.x - r * 1.5f, center.y - r * 0.2f,
        center.x - r * 0.6f, center.y - r * 1.25f,
        center.x, center.y - r * 0.35f,
    )
    cubicTo(
        center.x + r * 0.6f, center.y - r * 1.25f,
        center.x + r * 1.5f, center.y - r * 0.2f,
        center.x, center.y + r * 0.95f,
    )
    close()
}
