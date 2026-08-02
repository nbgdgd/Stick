package com.vpet.waifu.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vpet.waifu.domain.PetState
import com.vpet.waifu.ui.character.AnimatedPet
import com.vpet.waifu.ui.character.SpritePack
import com.vpet.waifu.ui.character.SpritePet
import com.vpet.waifu.ui.character.PetPalette
import com.vpet.waifu.ui.character.Prop
import com.vpet.waifu.ui.character.RoomDetail
import com.vpet.waifu.ui.character.RoomTheme
import com.vpet.waifu.ui.character.RoomPaint
import com.vpet.waifu.ui.character.drawPetRoom
import com.vpet.waifu.ui.character.drawPixelRoom
import com.vpet.waifu.ui.character.pixelScale
import com.vpet.waifu.ui.character.rememberPixelRoom
import com.vpet.waifu.ui.theme.StageColors

/**
 * The pet in her room.
 *
 * The backdrop is drawn rather than imported (see `drawPetRoom`), which is what
 * lets the home-screen widget show the identical scene from the same code.
 */
@Composable
fun PetStage(
    state: PetState,
    modifier: Modifier = Modifier,
    height: Dp = 300.dp,
    palette: PetPalette = PetPalette.Default,
    /** The tap bounce, anchored at her feet so she dips rather than shrinks. */
    characterScale: Float = 1f,
    workProp: Prop? = null,
    /** Owned upgrade ids — the furniture her money became. */
    decor: Set<String> = emptySet(),
    /**
     * Whether the room is in its night dress.
     *
     * Follows the player's real evening as well as her sleep, so the window
     * shows stars at the player's midnight instead of a noon sky.
     */
    night: Boolean = state == PetState.SLEEPING,
    /** The room she is living in — bought in the shop, applied here. */
    theme: String = RoomTheme.DEFAULT_ID,
    /**
     * A sprite pack to draw instead of the vector rig.
     *
     * Null keeps the rig, which is the only character that can wear the shop's
     * outfits or hold a job's prop — a sheet is a fixed set of pictures.
     */
    pack: SpritePack? = null,
) {
    val room = StageColors.forTheme(theme)
    // A pixel room cuts between day and night rather than cross-fading: every
    // frame of a colour animation is a fresh offscreen render, and a dissolve
    // is not something pixel art does anyway.
    val pixelated = pack != null && pack.pixelateRoom
    val top by animateColorAsState(
        if (night) room.nightTop else room.dayTop,
        label = "stage-top",
    )
    val bottom by animateColorAsState(
        if (night) room.nightBottom else room.dayBottom,
        label = "stage-bottom",
    )
    val floor by animateColorAsState(
        if (night) room.floorDark else room.floorLight,
        label = "stage-floor",
    )
    val paint = if (pixelated) {
        RoomPaint(
            top = if (night) room.nightTop else room.dayTop,
            bottom = if (night) room.nightBottom else room.dayBottom,
            floor = if (night) room.floorDark else room.floorLight,
            night = night,
        )
    } else {
        RoomPaint(top = top, bottom = bottom, floor = floor, night = night)
    }

    // Her bedroom's window, shelf and cat behind the café counter or the stage
    // truss is two rooms at once, so a shift normally strips the floor dressing
    // out — but the workplace scenery is drawn by the vector rig, and a sprite
    // pack has none. Stripping the room for a set that never arrives left her
    // working against a bare wall, which is worse than the ordinary room.
    val detail = if (workProp != null && pack == null) RoomDetail.WALL else RoomDetail.FULL

    // BoxWithConstraints rather than the screen width: the stage sits inside
    // the screen's padding, and the pixel grid has to be computed from the box
    // she is actually drawn in or the room's pixels come out the wrong size.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // The character's own pixel size, computed from the box she is drawn
        // into rather than from the whole stage — SpritePet fits her frame
        // inside that box, and the room has to match the grid she ends up on.
        val density = LocalDensity.current
        val stageWidthPx = with(density) { maxWidth.toPx() }
        val stageHeightPx = with(density) { maxHeight.toPx() }
        val charBoxHeightPx = with(density) { (maxHeight - 48.dp - 8.dp).toPx() }
        val pixelScale = pack?.pixelScale(stageWidthPx, charBoxHeightPx) ?: 1f

        // Called unconditionally and told to stand down with a zero scale,
        // rather than wrapped in an `if`: switching characters mid-session
        // would otherwise add and remove a composable from the tree, and a
        // fixed shape is one less thing to reason about.
        val pixelRoom = rememberPixelRoom(
            widthPx = stageWidthPx,
            heightPx = stageHeightPx,
            pixelScale = if (pixelated) pixelScale else 0f,
            paint = paint,
            detail = detail,
            decor = decor,
            theme = theme,
        )

        Canvas(Modifier.fillMaxSize()) {
            if (pixelRoom != null) {
                drawPixelRoom(pixelRoom)
            } else {
                drawPetRoom(
                    top = paint.top,
                    bottom = paint.bottom,
                    floor = paint.floor,
                    night = paint.night,
                    detail = detail,
                    decor = decor,
                    theme = theme,
                )
            }
        }

        val characterModifier = Modifier
            .fillMaxSize()
            // The top band is reserved for the speech bubble; pushing her
            // start line down keeps the bubble in the sky and off her face.
            .padding(bottom = 8.dp, top = 48.dp)
            .graphicsLayer {
                scaleX = characterScale
                scaleY = characterScale
                transformOrigin = TransformOrigin(0.5f, 0.95f)
            }

        if (pack != null) {
            SpritePet(pack = pack, state = state, modifier = characterModifier)
        } else {
            AnimatedPet(
                state = state,
                palette = palette,
                workProp = workProp,
                modifier = characterModifier,
            )
        }
    }
}
