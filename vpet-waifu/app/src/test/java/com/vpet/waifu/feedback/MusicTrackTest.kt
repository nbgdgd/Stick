package com.vpet.waifu.feedback

import com.vpet.waifu.domain.PetState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Which track plays over which moment.
 *
 * The mapping is the design: sleeping dims the soundtrack, a shift has its own
 * rhythm, and being hungry or exhausted turns the room wistful. If a state is
 * ever added without a decision here, the exhaustive `when` fails to compile —
 * these tests pin the decisions themselves.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MusicTrackTest {

    @Test
    fun `what she does picks the track`() {
        assertEquals(MusicTrack.NIGHT, MusicTrack.forState(PetState.SLEEPING))
        assertEquals(MusicTrack.WORK, MusicTrack.forState(PetState.WORKING))
        assertEquals(MusicTrack.STUDY, MusicTrack.forState(PetState.STUDYING))
        assertEquals(MusicTrack.GAME, MusicTrack.forState(PetState.PLAYING))
    }

    @Test
    fun `suffering turns the room wistful`() {
        assertEquals(MusicTrack.SAD, MusicTrack.forState(PetState.HUNGRY))
        assertEquals(MusicTrack.SAD, MusicTrack.forState(PetState.TIRED))
    }

    @Test
    fun `everything else is the room theme`() {
        listOf(
            PetState.IDLE, PetState.HAPPY, PetState.EATING,
            PetState.LOVED, PetState.CELEBRATING,
        ).forEach { state ->
            assertEquals("$state", MusicTrack.HOME, MusicTrack.forState(state))
        }
    }
}
