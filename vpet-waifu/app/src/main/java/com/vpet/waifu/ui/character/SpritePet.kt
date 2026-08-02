package com.vpet.waifu.ui.character

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.vpet.waifu.domain.PetState
import org.json.JSONObject
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A drawn character supplied as a sprite sheet instead of as the vector rig.
 *
 * The rig in [PetArt] is generated: it poses arms from angles, recolours cloth
 * per outfit and hands her a different prop for every job. A sheet cannot do
 * any of that — it is a fixed grid of finished pictures. What it *can* do is
 * look like whatever an artist drew, which the rig will never quite manage.
 *
 * So both live here side by side. A sheet, if one is installed and selected,
 * replaces the character on the stage, in the bubble, in the arcade and in the
 * widget; the rig stays the default and the fallback, and stays the only thing
 * that can wear the outfits the shop sells.
 *
 * ### The manifest
 *
 * A pack is a directory under `assets/pets/<id>/` holding the sheet and a
 * `pet.json` beside it:
 *
 * ```json
 * {
 *   "id": "example",
 *   "displayName": "Example",
 *   "spritesheetPath": "spritesheet.webp",
 *   "frame":  { "width": 192, "height": 208 },
 *   "grid":   { "columns": 8, "rows": 9 },
 *   "defaultFps": 8,
 *   "clips": {
 *     "IDLE":     { "row": 0, "from": 0, "count": 6, "fps": 6 },
 *     "SLEEPING": { "row": 5, "from": 5, "count": 3, "fps": 3 }
 *   }
 * }
 * ```
 *
 * Only `IDLE` is required; any state without a clip of its own falls back to
 * it, so a two-row sheet is a legal pack and a nine-row one is a luxurious
 * version of the same thing.
 */
data class SpriteClip(
    val row: Int,
    val from: Int,
    val count: Int,
    val fps: Float,
    val loop: Boolean = true,
)

data class SpritePack(
    val id: String,
    val displayName: String,
    val sheet: ImageBitmap,
    val frameWidth: Int,
    val frameHeight: Int,
    val columns: Int,
    val clips: Map<PetState, SpriteClip>,
    val idle: SpriteClip,
    /**
     * Whether the room behind her should be quantised to her own pixel grid.
     *
     * On by default, because the mismatch is the first thing anyone notices:
     * a character made of visible square pixels standing in a smooth vector
     * room reads as a sticker pasted onto a photograph. A pack drawn at a high
     * enough resolution not to look pixellated can turn it off.
     */
    val pixelateRoom: Boolean,
) {
    /** The clip for a state, or the idle loop if the pack does not draw it. */
    fun clipFor(state: PetState): SpriteClip = clips[state] ?: idle

    /** Which cell of the grid is showing at [seconds] for [state]. */
    fun frameIndex(state: PetState, seconds: Float): Int {
        val clip = clipFor(state)
        if (clip.count <= 1) return clip.row * columns + clip.from
        val step = (seconds * clip.fps).toInt()
        val offset = if (clip.loop) {
            step.mod(clip.count)
        } else {
            min(step, clip.count - 1)
        }
        return clip.row * columns + clip.from + offset
    }
}

/**
 * Reads every pack installed under `assets/pets/`.
 *
 * Decoding is the expensive part — a nine-row sheet is a couple of megabytes
 * of RGBA — so it happens once per pack and the result is held for the life of
 * the process. There is no eviction because there is no growth: packs ship
 * with the app, and the largest realistic set is a handful.
 */
object SpritePacks {

    private const val ROOT = "pets"
    private val cache = mutableMapOf<String, SpritePack?>()

    /** The ids of every pack the app can offer, cheapest possible call. */
    fun installedIds(context: Context): List<String> =
        runCatching { context.assets.list(ROOT)?.toList().orEmpty() }
            .getOrDefault(emptyList())
            .sorted()

    /** A pack by id, decoded once. Null if it is missing or malformed. */
    fun load(context: Context, id: String?): SpritePack? {
        if (id.isNullOrBlank()) return null
        return cache.getOrPut(id) { runCatching { decode(context, id) }.getOrNull() }
    }

    private fun decode(context: Context, id: String): SpritePack {
        val dir = "$ROOT/$id"
        val manifest = JSONObject(context.assets.open("$dir/pet.json").use { it.readBytes().decodeToString() })

        val sheetName = manifest.optString("spritesheetPath", "spritesheet.webp")
        val bitmap = context.assets.open("$dir/$sheetName").use { BitmapFactory.decodeStream(it) }
            ?: error("sheet $dir/$sheetName could not be decoded")

        val frame = manifest.getJSONObject("frame")
        val grid = manifest.getJSONObject("grid")
        val columns = grid.getInt("columns")
        val defaultFps = manifest.optDouble("defaultFps", 8.0).toFloat()

        val clipsJson = manifest.optJSONObject("clips") ?: JSONObject()
        val clips = mutableMapOf<PetState, SpriteClip>()
        clipsJson.keys().forEach { key ->
            val state = runCatching { PetState.valueOf(key) }.getOrNull() ?: return@forEach
            val c = clipsJson.getJSONObject(key)
            clips[state] = SpriteClip(
                row = c.getInt("row"),
                from = c.optInt("from", 0),
                count = c.optInt("count", 1).coerceAtLeast(1),
                fps = c.optDouble("fps", defaultFps.toDouble()).toFloat(),
                loop = c.optBoolean("loop", true),
            )
        }

        return SpritePack(
            id = manifest.optString("id", id),
            displayName = manifest.optString("displayName", id),
            sheet = bitmap.asImageBitmap(),
            frameWidth = frame.getInt("width"),
            frameHeight = frame.getInt("height"),
            columns = columns,
            clips = clips,
            // A pack with no IDLE clip still has a first cell, and one frame of
            // something is a great deal better than an empty stage.
            idle = clips[PetState.IDLE] ?: SpriteClip(row = 0, from = 0, count = 1, fps = defaultFps),
            pixelateRoom = manifest.optBoolean("pixelateRoom", true),
        )
    }
}

/**
 * The sprite equivalent of [AnimatedPet].
 *
 * Shares [rememberPetPhaseSeconds] with the rig, so both characters are driven
 * by the same frame clock: whichever one is on screen stops when the screen
 * does and costs nothing while it is not composed.
 *
 * The frame is read inside the draw lambda rather than in composition, for the
 * same reason the rig does it — a new frame invalidates the draw phase alone.
 */
@Composable
fun SpritePet(
    pack: SpritePack,
    state: PetState,
    modifier: Modifier = Modifier,
) {
    val seconds = rememberPetPhaseSeconds()
    val srcSize = remember(pack) { IntSize(pack.frameWidth, pack.frameHeight) }

    Canvas(modifier) {
        val index = pack.frameIndex(state, seconds.floatValue)
        val col = index % pack.columns
        val row = index / pack.columns

        // Fit the frame to the box without distorting it, the way the rig's own
        // ART_WIDTH/ART_HEIGHT letterboxing does.
        val scale = min(size.width / pack.frameWidth, size.height / pack.frameHeight)
        val w = (pack.frameWidth * scale).roundToInt()
        val h = (pack.frameHeight * scale).roundToInt()

        drawImage(
            image = pack.sheet,
            srcOffset = IntOffset(col * pack.frameWidth, row * pack.frameHeight),
            srcSize = srcSize,
            dstOffset = IntOffset(
                ((size.width - w) / 2f).roundToInt(),
                (size.height - h).roundToInt(),
            ),
            dstSize = IntSize(w, h),
            // These sheets are drawn at a fixed small size and then blown up to
            // fill a phone-sized stage. Bilinear smoothing turns the linework
            // to mush at that ratio; nearest keeps the edges the artist drew.
            filterQuality = FilterQuality.None,
        )
    }
}

/**
 * How many screen pixels one of the character's own pixels occupies.
 *
 * [width] and [height] are the box she is drawn into — not the whole stage, the
 * box, because [SpritePet] fits the frame inside it. Everything that wants to
 * match her pixel grid — the room behind her, above all — needs this number and
 * nothing else.
 */
fun SpritePack.pixelScale(width: Float, height: Float): Float =
    min(width / frameWidth, height / frameHeight).coerceAtLeast(1f)

/** Where the character's feet land, for anything that has to line up with her. */
fun SpritePack.feetOffset(width: Float, height: Float): Offset {
    val scale = min(width / frameWidth, height / frameHeight)
    return Offset(width / 2f, height - frameHeight * scale * 0.06f)
}
