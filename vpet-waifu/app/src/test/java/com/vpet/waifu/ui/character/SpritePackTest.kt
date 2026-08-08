package com.vpet.waifu.ui.character

import androidx.test.core.app.ApplicationProvider
import com.vpet.waifu.domain.Occupations
import com.vpet.waifu.domain.PetState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The pack that actually ships, checked against the sheet it points at.
 *
 * A manifest is a set of promises about a picture — that cell 117 exists, that
 * the eight frames after it are a work loop, that six durations describe six
 * frames. Every one of those can be wrong in a way that compiles, installs and
 * only shows up as a character who freezes, flickers, or turns into somebody
 * else's arm halfway through a shift. The packing script and the loader are
 * written by different hands (one Python, one Kotlin) and this is the seam
 * between them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SpritePackTest {

    private val pack by lazy {
        SpritePacks.load(ApplicationProvider.getApplicationContext(), "anya")
    }

    @Test
    fun `the installed pack loads`() {
        assertNotNull("assets/pets/anya failed to decode", pack)
        assertEquals("Anya", pack!!.displayName)
    }

    @Test
    fun `the pack is offered as a choice`() {
        // The picker builds its cards from this list, so a pack that decodes
        // but is not enumerated is a character nobody can select.
        val ids = SpritePacks.installedIds(ApplicationProvider.getApplicationContext())
        assertTrue("installed packs were $ids", "anya" in ids)
    }

    @Test
    fun `she has a clip for every mood the game can put her in`() {
        val pack = pack!!
        PetState.entries.filter { it != PetState.IDLE }.forEach { state ->
            assertNotEquals(
                "$state falls back to the idle loop",
                pack.idle,
                pack.clipFor(state),
            )
        }
    }

    @Test
    fun `every job has an animation of its own`() {
        val pack = pack!!
        val generic = setOf(pack.clipFor(PetState.WORKING), pack.clipFor(PetState.STUDYING))
        Occupations.ALL.forEach { job ->
            val prop = workPropFor(job.id)
            assertNotNull("${job.id} has no prop, so no sheet can answer for it", prop)
            val clip = pack.clipFor(PetState.WORKING, prop)
            assertTrue(
                "${job.id} plays the generic loop rather than its own",
                clip !in generic,
            )
        }
    }

    @Test
    fun `the job clips are all different pictures`() {
        val pack = pack!!
        val starts = Occupations.ALL.map { pack.clipFor(PetState.WORKING, workPropFor(it.id)).start }
        assertEquals("two jobs point at the same frames", starts.size, starts.distinct().size)
    }

    @Test
    fun `she sleeps differently once she has a bed`() {
        val pack = pack!!
        assertNotEquals(
            pack.clipFor(PetState.SLEEPING),
            pack.clipFor(PetState.SLEEPING, Prop.PILLOW),
        )
    }

    @Test
    fun `every clip is inside the sheet`() {
        val pack = pack!!
        val cells = (pack.sheet.width / pack.frameWidth) * (pack.sheet.height / pack.frameHeight)
        val clips = PetState.entries.map { "state $it" to pack.clipFor(it) } +
            Occupations.ALL.map { "job ${it.id}" to pack.clipFor(PetState.WORKING, workPropFor(it.id)) }

        clips.forEach { (name, clip) ->
            assertTrue("$name starts at ${clip.start}", clip.start >= 0)
            assertTrue("$name runs to ${clip.start + clip.count} of $cells", clip.start + clip.count <= cells)
            assertTrue("$name has no frames", clip.count >= 1)
        }
    }

    @Test
    fun `a per-frame timeline describes every frame of its clip`() {
        val pack = pack!!
        (PetState.entries.map { pack.clipFor(it) } + pack.jobs.values).forEach { clip ->
            clip.durationsMs?.let { timeline ->
                assertEquals("a timeline shorter than its clip freezes", clip.count, timeline.size)
                assertTrue("a frame with no duration never advances", timeline.all { it > 0 })
            }
        }
    }

    @Test
    fun `a clip plays every one of its frames, in order, and comes back round`() {
        val pack = pack!!
        val clip = pack.clipFor(PetState.IDLE)
        val total = clip.durationsMs!!.sum()

        // Walked in small steps rather than sampled: what this is defending
        // against is a frame that is skipped or shown twice, and a coarse walk
        // would step straight over either.
        val seen = mutableListOf<Int>()
        var t = 0
        while (t < total) {
            val offset = clip.offsetAt(t / 1000f)
            if (seen.lastOrNull() != offset) seen.add(offset)
            t += 10
        }
        assertEquals("the clip should run 0..${clip.count - 1} once", (0 until clip.count).toList(), seen)

        // …and then repeat, rather than holding on the last frame.
        assertEquals(0, clip.offsetAt(total / 1000f + 0.001f))
        assertEquals(0, clip.offsetAt(total * 3 / 1000f + 0.001f))
    }

    @Test
    fun `the held last frame really is held`() {
        // Almost every clip here ends on a long frame — that pause is the
        // breath between loops, and averaging it into an fps is what turns a
        // breath into a twitch. This checks the timeline survived the packer.
        val pack = pack!!
        val clip = pack.clipFor(PetState.IDLE)
        val timeline = clip.durationsMs!!
        assertTrue(
            "the last frame lasts ${timeline.last()} against ${timeline.first()}",
            timeline.last() > timeline.first(),
        )
    }

    @Test
    fun `nothing she is drawn on is transparent where she should be`() {
        // The GIFs were flattened over magenta and only one exact shade was
        // marked transparent, so the packer keys out the whole ramp. If that
        // ever regresses she wears a bright pink halo against a dark room, and
        // it is the kind of thing that is obvious in a screenshot and invisible
        // in a test — unless the test looks.
        val pack = pack!!
        val pixels = IntArray(pack.sheet.width * pack.sheet.height)
        pack.sheet.readPixels(pixels)

        val magenta = pixels.count { argb ->
            val a = (argb ushr 24) and 0xFF
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            a != 0 && minOf(r, b) > 30 && g < 0.6f * minOf(r, b)
        }
        assertEquals("magenta key colour survived into the sheet", 0, magenta)
    }
}
