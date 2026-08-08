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
 *     "SLEEPING": { "start": 45, "count": 3, "durations": [500, 500, 750] }
 *   },
 *   "occupations": { "cafe": { "start": 0, "count": 8, "fps": 6.25 } },
 *   "props":       { "PILLOW": { "start": 95, "count": 6, "fps": 4 } }
 * }
 * ```
 *
 * Only `IDLE` is required; any state without a clip of its own falls back to
 * it, so a two-row sheet is a legal pack and a nine-row one is a luxurious
 * version of the same thing.
 *
 * A cell is addressed by `start`, its index across the whole grid, so a clip
 * may run off the end of one row and into the next and the packer is free to
 * lay frames down back to back. `row` and `from` still work and mean
 * `row * columns + from`, because the first pack shipped written that way.
 *
 * `durations` gives each frame its own length in milliseconds, and is there
 * because almost every clip an artist draws holds its last frame — five frames
 * at 200ms and one at 450 is a breath. Averaged into one fps that breath
 * becomes a twitch. Where every frame really is the same length, `fps` says so
 * more briefly and means exactly the same thing.
 *
 * `occupations` and `props` are the same clips keyed by what she is *doing*
 * rather than how she is feeling: `WORKING` is a girl at work, but `cafe` is a
 * girl carrying a tray. A job with no clip of its own falls back to `WORKING`
 * or `STUDYING`, which falls back to `IDLE`, so a pack can answer as much or as
 * little of this as its artist drew.
 */
data class SpriteClip(
    /** Index of the first cell across the whole grid, rows run together. */
    val start: Int,
    val count: Int,
    val fps: Float,
    val loop: Boolean = true,
    /**
     * Per-frame lengths in milliseconds, or null to use [fps] throughout.
     *
     * Always either null or exactly [count] long — [SpritePacks] drops a
     * mismatched list rather than trusting it, because a timeline shorter than
     * its clip freezes on the last frame it can explain.
     */
    val durationsMs: List<Int>? = null,
) {
    /** How many frames past [start] is showing at [seconds] into the clip. */
    fun offsetAt(seconds: Float): Int {
        if (count <= 1) return 0
        val timeline = durationsMs
        if (timeline == null) {
            val step = (seconds * fps).toInt()
            return if (loop) step.mod(count) else min(step, count - 1)
        }
        val total = timeline.sum().toLong()
        if (total <= 0L) return 0
        val millis = (seconds * 1000f).toLong()
        var t = if (loop) millis.mod(total) else min(millis, total - 1L)
        timeline.forEachIndexed { index, length ->
            t -= length
            if (t < 0L) return index
        }
        return count - 1
    }
}

data class SpritePack(
    val id: String,
    val displayName: String,
    val sheet: ImageBitmap,
    val frameWidth: Int,
    val frameHeight: Int,
    val columns: Int,
    val clips: Map<PetState, SpriteClip>,
    /**
     * Clips keyed by the prop the job puts in her hands.
     *
     * The prop is the one thing about a shift that every surface already knows
     * — the stage, the widget and the overlay all receive it so the rig can be
     * handed a tray or a microphone — so a sheet can answer the same question
     * without a single new argument being threaded anywhere. [SpritePacks]
     * resolves the manifest's job ids into props at load, which means the
     * mapping lives in exactly one place and cannot drift from [workPropFor].
     */
    val jobs: Map<Prop, SpriteClip>,
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
    /**
     * How much of its box the frame is allowed to fill, 0..1.
     *
     * The vector rig is drawn in a 200x300 space with generous air around the
     * character; a sheet's cell is cropped tight to her. Fitting both "to the
     * box" therefore does not give them the same size at all — it gave the
     * sheet a character two and a half times wider than the drawn one, filling
     * the stage and hiding the room she is supposed to be standing in.
     *
     * The default leaves the same kind of air the rig has. A pack whose cells
     * carry their own margin should raise it.
     */
    val fit: Float,
) {
    /**
     * The clip for what she is doing, falling back until something answers.
     *
     * What she is *doing* wins over what she is: a girl on the café shift is
     * carrying a tray whether or not the shift has cheered her up, and the
     * cheer is already on her face in that clip. Below that the chain is the
     * job's own animation, then the generic one for work or study, then the
     * state, then idle — so a pack that drew nothing but an idle loop is still
     * a legal pack that never shows an empty stage.
     */
    fun clipFor(state: PetState, prop: Prop? = null): SpriteClip =
        prop?.let { jobs[it] } ?: clips[state] ?: idle

    /** Which cell of the grid is showing at [seconds]. */
    fun frameIndex(state: PetState, prop: Prop?, seconds: Float): Int {
        val clip = clipFor(state, prop)
        return clip.start + clip.offsetAt(seconds)
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

    /**
     * How much of its box a tightly cropped frame should fill by default.
     *
     * Measured against the drawn character rather than guessed: she stands
     * about a fifth of the stage wide and half of it tall, and a tight cell
     * scaled to fill the box came out at 56% and 79%.
     */
    private const val DEFAULT_FIT = 0.62f
    private val cache = mutableMapOf<String, SpritePack?>()

    /**
     * The ids of every pack the app can offer.
     *
     * Not simply the listing of `assets/pets/`. What counts as a pack is a
     * directory with a manifest in it, and asking that question directly is
     * both the honest test and the portable one: `AssetManager.list` returns
     * immediate children on a device and whole paths under Robolectric, so a
     * listing taken at face value offers the player a character called
     * "anya/spritesheet.webp" on one of the two. It also means a stray file
     * dropped in the folder is ignored rather than offered as a broken choice.
     */
    fun installedIds(context: Context): List<String> =
        runCatching { context.assets.list(ROOT)?.toList().orEmpty() }
            .getOrDefault(emptyList())
            .map { it.substringBefore('/') }
            .distinct()
            .filter { id ->
                runCatching { context.assets.open("$ROOT/$id/pet.json").close() }.isSuccess
            }
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

        fun clipOf(c: JSONObject): SpriteClip {
            val count = c.optInt("count", 1).coerceAtLeast(1)
            val durations = c.optJSONArray("durations")
                ?.let { array -> List(array.length()) { array.optInt(it) } }
                // A timeline that does not describe every frame is worse than
                // none: it would hold on whichever frame it ran out at.
                ?.takeIf { it.size == count && it.all { ms -> ms > 0 } }
            return SpriteClip(
                start = if (c.has("start")) {
                    c.getInt("start")
                } else {
                    c.optInt("row") * columns + c.optInt("from", 0)
                },
                count = count,
                fps = c.optDouble("fps", defaultFps.toDouble()).toFloat(),
                loop = c.optBoolean("loop", true),
                durationsMs = durations,
            )
        }

        val clipsJson = manifest.optJSONObject("clips") ?: JSONObject()
        val clips = mutableMapOf<PetState, SpriteClip>()
        clipsJson.keys().forEach { key ->
            val state = runCatching { PetState.valueOf(key) }.getOrNull() ?: return@forEach
            clips[state] = clipOf(clipsJson.getJSONObject(key))
        }

        // Jobs arrive named the way the game names them — "cafe", "idol" — and
        // are stored against the prop that job hands her, which is what the
        // renderers actually carry. Going through `workPropFor` rather than a
        // table of our own means a job that changes its prop changes here too.
        val jobs = mutableMapOf<Prop, SpriteClip>()
        manifest.optJSONObject("occupations")?.let { json ->
            json.keys().forEach { jobId ->
                val prop = workPropFor(jobId) ?: return@forEach
                jobs[prop] = clipOf(json.getJSONObject(jobId))
            }
        }
        // ...and anything else she holds is named by the prop directly, which
        // is how the bed gets its own way of sleeping.
        manifest.optJSONObject("props")?.let { json ->
            json.keys().forEach { name ->
                val prop = runCatching { Prop.valueOf(name) }.getOrNull() ?: return@forEach
                jobs[prop] = clipOf(json.getJSONObject(name))
            }
        }

        return SpritePack(
            id = manifest.optString("id", id),
            displayName = manifest.optString("displayName", id),
            sheet = bitmap.asImageBitmap(),
            frameWidth = frame.getInt("width"),
            frameHeight = frame.getInt("height"),
            columns = columns,
            clips = clips,
            jobs = jobs,
            // A pack with no IDLE clip still has a first cell, and one frame of
            // something is a great deal better than an empty stage.
            idle = clips[PetState.IDLE] ?: SpriteClip(start = 0, count = 1, fps = defaultFps),
            pixelateRoom = manifest.optBoolean("pixelateRoom", true),
            fit = manifest.optDouble("fit", DEFAULT_FIT.toDouble()).toFloat().coerceIn(0.1f, 1f),
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
    /** What the job put in her hands, if she is on one. */
    workProp: Prop? = null,
    /**
     * Whether to drop the pack's stage margin and fill the box instead.
     *
     * The margin exists so a tightly cropped cell comes out the same size as
     * the drawn rig when both are standing in the same room. A card that is a
     * *portrait* of her has no room and no rig to agree with, and honouring the
     * margin there just leaves her small and stuck to the bottom edge — which
     * is exactly how the character picker used to show every pack.
     */
    fillBox: Boolean = false,
) {
    // Unthrottled: a frame here is one blit of one crop, and the clip's own fps
    // decides how often the picture actually changes. Throttling this only made
    // the moment a frame flips land later than the panel could have shown it.
    val seconds = rememberPetPhaseSeconds(intervalNanos = SPRITE_FRAME_INTERVAL_NANOS)
    val srcSize = remember(pack) { IntSize(pack.frameWidth, pack.frameHeight) }

    Canvas(modifier) {
        val index = pack.frameIndex(state, workProp, seconds.floatValue)
        val col = index % pack.columns
        val row = index / pack.columns

        // Fit the frame to the box without distorting it, the way the rig's own
        // ART_WIDTH/ART_HEIGHT letterboxing does — less the pack's own margin,
        // so a tightly cropped cell does not swallow the room behind her.
        val scale = if (fillBox) {
            min(size.width / pack.frameWidth, size.height / pack.frameHeight)
        } else {
            pack.pixelScale(size.width, size.height)
        }
        val w = (pack.frameWidth * scale).roundToInt()
        val h = (pack.frameHeight * scale).roundToInt()

        drawImage(
            image = pack.sheet,
            srcOffset = IntOffset(col * pack.frameWidth, row * pack.frameHeight),
            srcSize = srcSize,
            dstOffset = IntOffset(
                ((size.width - w) / 2f).roundToInt(),
                // Standing on the floor of her box normally, centred when she
                // is filling a card and there is no floor to stand on.
                if (fillBox) ((size.height - h) / 2f).roundToInt() else (size.height - h).roundToInt(),
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
    (min(width / frameWidth, height / frameHeight) * fit).coerceAtLeast(1f)

/** Where the character's feet land, for anything that has to line up with her. */
fun SpritePack.feetOffset(width: Float, height: Float): Offset {
    val scale = min(width / frameWidth, height / frameHeight)
    return Offset(width / 2f, height - frameHeight * scale * 0.06f)
}
