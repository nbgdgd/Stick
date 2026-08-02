package com.vpet.waifu.ui.character

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
 * A room look, as sold in the shop.
 *
 * Wall and floor live here beside the furniture because a theme that only
 * recoloured the nightstand would still be the same room. [drawPetRoom] is
 * still handed its wall and floor colours by the caller rather than reading
 * them off the theme — the app cross-fades those between day and night, so they
 * have to be animatable values — and [com.vpet.waifu.ui.theme.StageColors]
 * takes them from here so there is one source for them.
 *
 * Only the things a decorator would choose are themed: planks, carcasses,
 * cushions, the room's own light. A book spine and a fridge stay the colour a
 * book spine and a fridge are.
 */
data class RoomTheme(
    val id: String,
    val dayTop: Color,
    val dayBottom: Color,
    val floorLight: Color,
    val nightTop: Color,
    val nightBottom: Color,
    val floorDark: Color,
    /** Planks, the nightstand's carcass, the plant pot. */
    val woodDay: Color,
    val woodNight: Color,
    /** Drawer fronts and pot rims — one step lighter than the wood. */
    val trimDay: Color,
    val trimNight: Color,
    /** Anything soft: the plush and the cushions. */
    val plushDay: Color,
    val plushNight: Color,
    /**
     * The cat.
     *
     * Pale in both dresses rather than following the plush, because at night
     * she is a small animal on a near-black floor and the only thing keeping
     * her visible is that she is lighter than it.
     */
    val furDay: Color,
    val furNight: Color,
    /** The one light the room signs itself with. */
    val glow: Color,
) {
    internal fun wood(night: Boolean) = if (night) woodNight else woodDay

    internal fun trim(night: Boolean) = if (night) trimNight else trimDay

    internal fun plush(night: Boolean) = if (night) plushNight else plushDay

    internal fun fur(night: Boolean) = if (night) furNight else furDay

    companion object {
        const val DEFAULT_ID = "theme_default"

        /**
         * What she starts with: violet, like her hair, her irises and the
         * app's accent.
         *
         * The stage briefly went cream-and-tan in a redesign that was then
         * reverted to the violet accent everywhere except here, which left
         * cream walls under lavender furniture. The warm palette was worth
         * keeping, so it is [Cozy] now — something to buy rather than a clash
         * to live with.
         */
        val Default = RoomTheme(
            id = DEFAULT_ID,
            dayTop = Color(0xFFF6EEFF),
            dayBottom = Color(0xFFE3D6F7),
            floorLight = Color(0xFFD9C9F0),
            nightTop = Color(0xFF2B2447),
            nightBottom = Color(0xFF1A1530),
            floorDark = Color(0xFF272042),
            woodDay = Color(0xFFCDB9EC),
            woodNight = Color(0xFF4A3D78),
            trimDay = Color(0xFFDDCDF5),
            trimNight = Color(0xFF5C4D91),
            plushDay = Color(0xFFCBB8F0),
            plushNight = Color(0xFF8C7BC7),
            furDay = Color(0xFFF0E4FA),
            furNight = Color(0xFFC9B4E8),
            glow = Color(0xFFFF6FA5),
        )

        private val Cozy = RoomTheme(
            id = "theme_cozy",
            dayTop = Color(0xFFFFF3E7),
            dayBottom = Color(0xFFF2DCC6),
            floorLight = Color(0xFFE3C9AE),
            nightTop = Color(0xFF3A2C28),
            nightBottom = Color(0xFF241B18),
            floorDark = Color(0xFF463429),
            woodDay = Color(0xFFC79A6B),
            woodNight = Color(0xFF6E523A),
            trimDay = Color(0xFFE0BB92),
            trimNight = Color(0xFF8A6A4C),
            plushDay = Color(0xFFF2C9A0),
            plushNight = Color(0xFFA98467),
            furDay = Color(0xFFFFF1E0),
            furNight = Color(0xFFE8CBA6),
            glow = Color(0xFFFFC46B),
        )

        // Its "day" is still dark: the whole point of the night room is that it
        // is one, so a bought theme does not switch itself off every morning.
        private val Night = RoomTheme(
            id = "theme_night",
            dayTop = Color(0xFF2E2B52),
            dayBottom = Color(0xFF1B1934),
            floorLight = Color(0xFF2A2748),
            nightTop = Color(0xFF201E3C),
            nightBottom = Color(0xFF12111F),
            floorDark = Color(0xFF211E3A),
            woodDay = Color(0xFF3E3A6B),
            woodNight = Color(0xFF332F5A),
            trimDay = Color(0xFF524D8A),
            trimNight = Color(0xFF423E75),
            plushDay = Color(0xFF6F68B8),
            plushNight = Color(0xFF5A54A0),
            furDay = Color(0xFFCFCBF0),
            furNight = Color(0xFFBDB8E8),
            glow = Color(0xFF7FE3FF),
        )

        private val Sakura = RoomTheme(
            id = "theme_sakura",
            dayTop = Color(0xFFFFF2F6),
            dayBottom = Color(0xFFFBDDE7),
            floorLight = Color(0xFFEFC9D6),
            nightTop = Color(0xFF3A2438),
            nightBottom = Color(0xFF24162A),
            floorDark = Color(0xFF3E2A3C),
            woodDay = Color(0xFFE3A6BC),
            woodNight = Color(0xFF7E4C63),
            trimDay = Color(0xFFF3C6D6),
            trimNight = Color(0xFF9C6480),
            plushDay = Color(0xFFFFD9E6),
            plushNight = Color(0xFFB88099),
            furDay = Color(0xFFFFF0F5),
            furNight = Color(0xFFE9C2D2),
            glow = Color(0xFFFF9CC0),
        )

        /** The theme an id names, falling back to the one she starts with. */
        fun forId(id: String?): RoomTheme = when (id) {
            Cozy.id -> Cozy
            Night.id -> Night
            Sakura.id -> Sakura
            else -> Default
        }
    }
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
    /**
     * Which purchased room look to dress the scene in.
     *
     * The id the shop sells, so the caller can hand its saved string straight
     * over; anything unknown falls back to the room she starts with.
     */
    theme: String = RoomTheme.DEFAULT_ID,
    /**
     * How many flat steps the wall's shading is cut into, or 0 for a gradient.
     *
     * Quantising the room to a sprite's pixel grid makes its edges chunky, but
     * a smooth vertical gradient survives the trip perfectly happily — a
     * 250-row bitmap has 250 rows to spend on it — and the wall stays the one
     * unmistakably modern thing in an otherwise pixelated picture. Banding it
     * is what actually finishes the effect.
     *
     * Done here rather than by posterising the finished bitmap because that
     * shifts every hue it touches, and the room themes are something the player
     * paid for. Stepping the same two colours keeps them exactly.
     */
    wallBands: Int = 0,
) {
    val look = RoomTheme.forId(theme)
    val body: DrawScope.() -> Unit = {
        if (wallBands > 1) {
            val step = size.height / wallBands
            for (i in 0 until wallBands) {
                drawRect(
                    color = lerp(top, bottom, i / (wallBands - 1f)),
                    topLeft = Offset(0f, i * step),
                    // A hair of overlap: exact edges leave seams of background
                    // showing between bands once the whole thing is scaled up.
                    size = Size(size.width, step + 1f),
                )
            }
        } else {
            drawRect(Brush.verticalGradient(listOf(top, bottom)))
        }

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
            drawWindow(wall, night, look)
            drawSignature(wall, night, look)
            drawShelf(wall, night, look)
            // Her gear lives on its own shelf under the window — wall-mounted,
            // so even the widget's floorless cut of the room can show it.
            drawGearShelf(wall, night, decor, look)
        }
        if (detail == RoomDetail.FULL) {
            drawPlant(wall, night, look)
            drawNightstand(wall, night, look)
            drawFloorDecor(wall, night, decor, look)
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
private fun DrawScope.drawWindow(wall: Float, night: Boolean, look: RoomTheme) {
    val x = size.width * 0.07f
    val y = wall * 0.13f
    val w = size.width * 0.27f
    val h = wall * 0.60f
    val radius = w * 0.07f
    // The night room keeps its city lit at every hour: that view is what it was
    // bought for, and a theme that switches itself off every morning is not one.
    val dark = night || look.id == "theme_night"
    val frame = if (dark) look.trim(true) else Color(0xFFFFFFFF)
    val sky = if (dark) Color(0xFF141031) else Color(0xFFBFE4FA)

    drawRoundRect(sky, Offset(x, y), Size(w, h), CornerRadius(radius, radius))

    clipPath(
        Path().apply {
            addRoundRect(RoundRect(Rect(x, y, x + w, y + h), CornerRadius(radius, radius)))
        },
    ) {
        drawCircle(
            color = if (dark) Color(0xFFF4ECD0) else Color(0xFFFFE08A),
            radius = w * 0.15f,
            center = Offset(x + w * 0.68f, y + h * 0.24f),
        )
        if (dark) {
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
                color = if (dark) Color(0xFF221C4A) else Color(0xFF9CC6E8),
                topLeft = Offset(tx, y + h - th),
                size = Size(tw, th),
            )
            // Lit windows.
            repeat(3) { row ->
                repeat(2) { col ->
                    val lit = (row + col + (left * 10).toInt()) % 3 != 0
                    if (!lit) return@repeat
                    drawRect(
                        color = if (dark) Color(0xCCFFE9A8) else Color(0x33FFFFFF),
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

/**
 * The one thing each room is recognised by from across a home screen.
 *
 * A palette swap alone is not a theme: at widget size two lilac rooms and two
 * pink ones are the same picture. Each look gets a shape of its own here.
 */
private fun DrawScope.drawSignature(wall: Float, night: Boolean, look: RoomTheme) {
    when (look.id) {
        "theme_cozy" -> drawWallLamp(wall, night, look)
        "theme_night" -> drawFairyLights(wall, look)
        "theme_sakura" -> drawSakuraBranch(wall, night, look)
        else -> drawNeonHeart(wall, night, look)
    }
}

/** A neon heart on the wall — the one light source that reads instantly. */
private fun DrawScope.drawNeonHeart(wall: Float, night: Boolean, look: RoomTheme) {
    val center = Offset(size.width * 0.79f, wall * 0.22f)
    val r = size.width * 0.045f
    val glow = if (night) 0.55f else 0.3f
    listOf(2.2f to 0.10f, 1.6f to 0.18f).forEach { (scale, alpha) ->
        drawPath(heartPath(center, r * scale), look.glow.copy(alpha = alpha * glow * 2f))
    }
    drawPath(
        heartPath(center, r),
        look.glow,
        style = Stroke(width = r * 0.28f, cap = StrokeCap.Round),
    )
}

/** The warm room's lamp: a small shade on a bracket, glowing onto the wall. */
private fun DrawScope.drawWallLamp(wall: Float, night: Boolean, look: RoomTheme) {
    val x = size.width * 0.79f
    val y = wall * 0.20f
    val w = size.width * 0.062f

    drawLine(look.wood(night), Offset(x, y - wall * 0.10f), Offset(x, y), strokeWidth = size.width * 0.010f)
    val shade = Path().apply {
        moveTo(x - w * 0.42f, y - wall * 0.055f)
        lineTo(x + w * 0.42f, y - wall * 0.055f)
        lineTo(x + w * 0.72f, y)
        lineTo(x - w * 0.72f, y)
        close()
    }
    drawPath(shade, look.wood(night))
    drawRoundRect(
        look.glow,
        Offset(x - w * 0.68f, y - wall * 0.006f),
        Size(w * 1.36f, wall * 0.016f),
        CornerRadius(wall * 0.008f, wall * 0.008f),
    )
    // The light itself: three short skirts of glow, each wider and fainter than
    // the last. One tall cone reads as a tent, not as a lamp being on.
    val reach = if (night) 0.30f else 0.22f
    listOf(1.1f to 0.13f, 1.8f to 0.09f, 2.6f to 0.06f, 3.4f to 0.04f).forEach { (spread, alpha) ->
        val spill = Path().apply {
            moveTo(x - w * 0.7f, y)
            lineTo(x + w * 0.7f, y)
            lineTo(x + w * spread, y + wall * reach)
            lineTo(x - w * spread, y + wall * reach)
            close()
        }
        drawPath(spill, look.glow.copy(alpha = alpha * (if (night) 1.5f else 1f)))
    }
}

/** The night room's garland: a sagging string of bulbs across the wall. */
private fun DrawScope.drawFairyLights(wall: Float, look: RoomTheme) {
    val sag = wall * 0.06f
    val y = wall * 0.06f
    val wire = Path().apply {
        moveTo(0f, y)
        quadraticTo(size.width * 0.5f, y + sag * 2.2f, size.width, y * 0.7f)
    }
    drawPath(wire, look.trim(true), style = Stroke(width = size.width * 0.005f))

    repeat(11) { i ->
        val t = (i + 0.5f) / 11f
        // The point on the same quadratic the wire follows, so bulbs hang off
        // the string rather than beside it.
        val bx = size.width * t
        val by = (1 - t) * (1 - t) * y + 2 * (1 - t) * t * (y + sag * 2.2f) + t * t * (y * 0.7f)
        val r = size.width * 0.011f
        drawCircle(look.glow.copy(alpha = 0.18f), radius = r * 3f, center = Offset(bx, by + r * 2f))
        drawCircle(look.glow, radius = r, center = Offset(bx, by + r * 2f))
    }
}

/** The sakura room's branch, reaching in over the window with petals falling. */
private fun DrawScope.drawSakuraBranch(wall: Float, night: Boolean, look: RoomTheme) {
    val bark = if (night) Color(0xFF5B3B4A) else Color(0xFF8D6072)
    val branch = Path().apply {
        moveTo(size.width * 1.02f, wall * 0.02f)
        cubicTo(
            size.width * 0.86f, wall * 0.10f,
            size.width * 0.74f, wall * 0.12f,
            size.width * 0.54f, wall * 0.26f,
        )
    }
    drawPath(branch, bark, style = Stroke(width = size.width * 0.013f, cap = StrokeCap.Round))
    listOf(0.80f to 0.22f, 0.66f to 0.05f).forEach { (x, dy) ->
        drawLine(
            bark,
            Offset(size.width * x, wall * (0.11f + dy * 0.3f)),
            Offset(size.width * (x - 0.05f), wall * (0.11f + dy)),
            strokeWidth = size.width * 0.008f,
            cap = StrokeCap.Round,
        )
    }

    val petal = look.glow.copy(alpha = if (night) 0.85f else 1f)
    val blossoms = listOf(
        0.96f to 0.05f, 0.86f to 0.10f, 0.78f to 0.11f,
        0.70f to 0.16f, 0.62f to 0.20f, 0.55f to 0.27f, 0.81f to 0.26f, 0.67f to 0.06f,
    )
    blossoms.forEach { (fx, fy) ->
        val c = Offset(size.width * fx, wall * fy)
        val r = size.width * 0.017f
        repeat(5) { i ->
            val a = i * 1.2566f
            drawCircle(petal, radius = r, center = Offset(c.x + sin(a) * r, c.y + kotlin.math.cos(a) * r))
        }
        drawCircle(Color(0xFFFFF3C4), radius = r * 0.5f, center = c)
    }

    // Three petals on their way down, so the branch is not a decal.
    listOf(0.50f to 0.42f, 0.60f to 0.62f, 0.44f to 0.80f).forEach { (fx, fy) ->
        drawOval(
            petal.copy(alpha = 0.75f),
            Offset(size.width * fx, wall * fy),
            Size(size.width * 0.018f, size.width * 0.012f),
        )
    }
}

/** A shelf of books. */
private fun DrawScope.drawShelf(wall: Float, night: Boolean, look: RoomTheme) {
    val left = size.width * 0.63f
    val right = size.width * 0.95f
    val y = wall * 0.45f
    val plank = look.wood(night)
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
private fun DrawScope.drawPlant(wall: Float, night: Boolean, look: RoomTheme) {
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
    drawPath(pot, look.wood(night))
    drawRoundRect(
        look.trim(night),
        Offset(x - potW * 0.54f, base - potH),
        Size(potW * 1.08f, potH * 0.22f),
        CornerRadius(potH * 0.08f, potH * 0.08f),
    )
}

/** A nightstand with a cat plush on top. */
private fun DrawScope.drawNightstand(wall: Float, night: Boolean, look: RoomTheme) {
    val cx = size.width * 0.845f
    val w = size.width * 0.19f
    val h = (size.height - wall) * 0.62f
    val topY = wall + (size.height - wall) * 0.16f
    val wood = look.wood(night)

    drawRoundRect(
        wood, Offset(cx - w / 2, topY), Size(w, h),
        CornerRadius(w * 0.10f, w * 0.10f),
    )
    repeat(2) { i ->
        drawRoundRect(
            look.trim(night),
            Offset(cx - w * 0.38f, topY + h * (0.16f + i * 0.38f)),
            Size(w * 0.76f, h * 0.26f),
            CornerRadius(w * 0.06f, w * 0.06f),
        )
    }

    // The plush.
    val catR = w * 0.30f
    val catC = Offset(cx, topY - catR * 0.85f)
    val fur = look.plush(night)
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
private fun DrawScope.drawGearShelf(wall: Float, night: Boolean, decor: Set<String>, look: RoomTheme) {
    val slots = listOf("coffee_machine", "laptop", "textbooks", "studio").filter { it in decor }
    if (slots.isEmpty()) return

    val left = size.width * 0.05f
    val right = size.width * 0.33f
    val y = wall * 0.86f
    val plank = look.wood(night)
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
                    look.plush(night),
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
                // Inverted on purpose: the mic is the one thing here that has to
                // stand *against* the plank, so it takes the shade the plank is
                // not wearing.
                drawCircle(
                    look.wood(!night),
                    radius = itemH * 0.3f,
                    center = Offset(cx, y - itemH * 0.78f),
                )
                drawCircle(
                    look.trim(false).copy(alpha = 0.8f),
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
private fun DrawScope.drawFloorDecor(wall: Float, night: Boolean, decor: Set<String>, look: RoomTheme) {
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
        drawPath(heartPath(Offset(x + w * 0.34f, topY + h * 0.6f), w * 0.1f), look.glow)
    }

    if ("bed" in decor) {
        // Her comfy corner: two plump cushions between her and the nightstand.
        val cx = size.width * 0.705f
        val w = size.width * 0.1f
        val base = wall + floorH * 0.55f
        listOf(
            look.glow to 0f,
            look.plush(false) to 1f,
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
            look.wood(true),
            Offset(x, y),
            Size(w, floorH * 0.16f),
            CornerRadius(w * 0.12f, w * 0.12f),
        )
        drawCircle(Color(0xFF56C596), radius = floorH * 0.035f, center = Offset(x + w * 0.8f, y + floorH * 0.08f))
        // The controller resting against it.
        drawRoundRect(
            look.plush(night),
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
        val fur = look.fur(night)
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
