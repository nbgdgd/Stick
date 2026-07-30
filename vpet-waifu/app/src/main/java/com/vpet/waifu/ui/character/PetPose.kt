package com.vpet.waifu.ui.character

import com.vpet.waifu.domain.PetState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** How the eyes are drawn this frame. */
enum class EyeShape { OPEN, HAPPY_ARC, SLEEPING, HEART, SPARKLE, HALF_LIDDED, FOCUSED }

/** How the mouth is drawn this frame. */
enum class MouthShape { SMILE, BIG_SMILE, CAT, WAVY, SMALL_O, FLAT, CHEWING }

/** The object she is holding or sitting at. */
enum class Prop { BOWL, LAPTOP, BOOK, CONTROLLER, PILLOW, TRAY, BAG, MIC, NOTEBOOK, BOOKSTACK, LECTURE }

/**
 * Which prop a job puts in her hands.
 *
 * Every occupation used to reuse the laptop, so the café, the shop floor and
 * the idol stage were all one scene with a different caption. Now each job has
 * its own pantomime.
 */
fun workPropFor(occupationId: String?): Prop? = when (occupationId) {
    "cafe" -> Prop.TRAY
    "shop" -> Prop.BAG
    "office" -> Prop.LAPTOP
    "idol" -> Prop.MIC
    "school" -> Prop.NOTEBOOK
    "course" -> Prop.LECTURE
    "university" -> Prop.BOOKSTACK
    else -> null
}

/** Floating decoration around her. */
enum class ParticleKind { HEARTS, SPARKLES, SLEEP_Z, SWEAT, NOTES, CRUMBS, COINS, CODE, STEAM }

/**
 * Everything the renderer needs for one frame.
 *
 * The pose is a plain value: [PetPoseFactory] turns `(state, seconds)` into one,
 * `PetArt` turns one into pixels. Nothing in between holds state, which is why
 * the same code can drive a 60 fps Compose canvas and a single frozen frame
 * rasterised for the home-screen widget.
 */
data class PetPose(
    val timeSeconds: Float = 0f,
    /** Chest rise, -1..1. */
    val breath: Float = 0f,
    /** 0 = wide open, 1 = shut. */
    val blink: Float = 0f,
    val headTiltDegrees: Float = 0f,
    val headBob: Float = 0f,
    val bodyBounce: Float = 0f,
    val bodyLean: Float = 0f,
    /** Twin-tail sway in degrees; the tails lag behind the head. */
    val hairSwayDegrees: Float = 0f,
    val ahogeDegrees: Float = 0f,
    /** Shoulder angles in degrees. 0 hangs straight down, positive lifts forward. */
    val leftArmDegrees: Float = 8f,
    val rightArmDegrees: Float = -8f,
    val leftElbowDegrees: Float = 10f,
    val rightElbowDegrees: Float = -10f,
    val eyes: EyeShape = EyeShape.OPEN,
    val mouth: MouthShape = MouthShape.SMILE,
    /** Pupil offset, -1..1 in each axis. */
    val lookX: Float = 0f,
    val lookY: Float = 0f,
    val blushAlpha: Float = 0.45f,
    /** Eyebrow shape: -1 determined, 0 neutral, +1 worried. */
    val browWorry: Float = 0f,
    val prop: Prop? = null,
    /** 0..1 progress of whatever the prop is animating (fork to mouth, page turn). */
    val propProgress: Float = 0f,
    val particles: ParticleKind? = null,
    /**
     * Drives the floating decoration, 0..1 per cycle.
     *
     * Separate from [timeSeconds] so the widget's flipbook can hand it an exact
     * fraction of the loop; particles driven straight off the clock would jump
     * back to their start once per cycle.
     */
    val particlePhase: Float = 0f,
    /** Slumped posture for tired/hungry states. */
    val droop: Float = 0f,
)

/**
 * Turns a [PetState] and a clock into a pose.
 *
 * Everything is driven by sines of the elapsed time, so animations loop
 * seamlessly and never need to be started, stopped or kept in sync — asking for
 * the pose at time *t* always gives the same answer.
 */
object PetPoseFactory {

    fun pose(state: PetState, seconds: Float, workProp: Prop? = null): PetPose =
        pose(state, seconds, idleBase(seconds, state), workProp)

    /**
     * The same, but on a caller-supplied base.
     *
     * States build on the shared idle motion — some add to it, some replace it —
     * so the only reliable way to make a state loop is to hand it a base that
     * already does, rather than trying to repair the result afterwards.
     */
    private fun pose(state: PetState, seconds: Float, base: PetPose, workProp: Prop? = null): PetPose {
        return when (state) {
            PetState.IDLE -> base
            PetState.HAPPY -> happy(base, seconds)
            PetState.HUNGRY -> hungry(base, seconds)
            PetState.TIRED -> tired(base, seconds)
            PetState.SLEEPING -> sleeping(base, seconds)
            PetState.EATING -> eating(base, seconds)
            PetState.LOVED -> loved(base, seconds)
            PetState.WORKING -> when (workProp) {
                Prop.TRAY -> serving(base, seconds)
                Prop.BAG -> clerking(base, seconds)
                Prop.MIC -> performing(base, seconds)
                else -> working(base, seconds)
            }
            PetState.STUDYING -> when (workProp) {
                Prop.NOTEBOOK -> writing(base, seconds)
                Prop.LECTURE -> attending(base, seconds)
                Prop.BOOKSTACK -> studying(base, seconds).copy(prop = Prop.BOOKSTACK)
                else -> studying(base, seconds)
            }
            PetState.PLAYING -> playing(base, seconds)
            PetState.CELEBRATING -> celebrating(base, seconds)
        }
    }

    // Breathing and blinking are shared by every state that has its eyes open.
    private fun idleBase(t: Float, state: PetState): PetPose {
        val breath = sin(t * BREATH_SPEED)
        return PetPose(
            timeSeconds = t,
            breath = breath,
            blink = blinkCurve(t, state),
            headTiltDegrees = sin(t * 0.55f) * 2.5f,
            headBob = sin(t * BREATH_SPEED + 0.4f) * 1.6f,
            bodyBounce = breath * 1.2f,
            hairSwayDegrees = sin(t * 0.55f - 0.7f) * 5f,
            ahogeDegrees = sin(t * 1.6f) * 12f,
            leftArmDegrees = 8f + sin(t * 0.6f) * 3f,
            rightArmDegrees = -8f - sin(t * 0.6f + 0.5f) * 3f,
            lookX = sin(t * 0.31f) * 0.35f,
            lookY = sin(t * 0.23f) * 0.15f,
            particlePhase = t * 0.35f,
        )
    }

    /**
     * A blink is rare and fast: mostly zero, with a sharp 0→1→0 spike every few
     * seconds, plus a double-blink on some cycles so it doesn't feel metronomic.
     */
    private fun blinkCurve(t: Float, state: PetState): Float {
        if (state == PetState.SLEEPING) return 1f
        val cycle = 4.3f
        val phase = (t % cycle) / cycle
        val spike = spike(phase, center = 0.06f, width = 0.035f)
        val second = if (((t / cycle).toInt() % 3) == 0) spike(phase, 0.13f, 0.03f) else 0f
        return max(spike, second).coerceIn(0f, 1f)
    }

    private fun spike(phase: Float, center: Float, width: Float): Float {
        val d = abs(phase - center)
        return if (d > width) 0f else (1f - d / width).pow(0.6f)
    }

    private fun happy(base: PetPose, t: Float): PetPose {
        val hop = abs(sin(t * 3.1f)).pow(1.6f)
        return base.copy(
            bodyBounce = base.bodyBounce - hop * 6f,
            headBob = base.headBob - hop * 3f,
            hairSwayDegrees = sin(t * 3.1f - 0.9f) * 12f,
            leftArmDegrees = 26f + hop * 18f,
            rightArmDegrees = -26f - hop * 18f,
            eyes = if (base.blink > 0.5f) EyeShape.HAPPY_ARC else EyeShape.SPARKLE,
            mouth = MouthShape.BIG_SMILE,
            blushAlpha = 0.6f,
            particles = ParticleKind.SPARKLES,
        )
    }

    private fun hungry(base: PetPose, t: Float): PetPose {
        // A slow slump with an occasional stomach-rumble shiver.
        val rumble = spike((t % 5f) / 5f, 0.5f, 0.08f)
        return base.copy(
            droop = 0.7f,
            headBob = base.headBob + 3f + rumble * sin(t * 40f) * 1.5f,
            bodyLean = 2f + rumble * sin(t * 38f) * 2f,
            leftArmDegrees = 4f,
            rightArmDegrees = -4f,
            leftElbowDegrees = 55f,
            rightElbowDegrees = -55f,
            eyes = EyeShape.HALF_LIDDED,
            mouth = MouthShape.WAVY,
            lookY = 0.5f,
            blushAlpha = 0.25f,
            browWorry = 0.9f,
            particles = ParticleKind.SWEAT,
        )
    }

    private fun tired(base: PetPose, t: Float): PetPose {
        val nod = sin(t * 0.9f)
        return base.copy(
            droop = 1f,
            headTiltDegrees = 6f + nod * 4f,
            headBob = base.headBob + 5f + max(0f, nod) * 3f,
            leftArmDegrees = 2f,
            rightArmDegrees = -2f,
            eyes = EyeShape.HALF_LIDDED,
            mouth = MouthShape.FLAT,
            lookY = 0.4f,
            blushAlpha = 0.2f,
            browWorry = 0.7f,
            particles = ParticleKind.SLEEP_Z,
        )
    }

    private fun sleeping(base: PetPose, t: Float): PetPose {
        val slow = sin(t * 0.8f)
        return base.copy(
            breath = slow,
            blink = 1f,
            droop = 1f,
            // Enough amplitude to actually read as breathing. At a point and a
            // half of rise the only thing moving on the whole screen was the
            // stream of Zs, which made her look like a still image with a
            // sticker floating over it.
            headTiltDegrees = 15f + slow * 2f,
            headBob = 8f + slow * 3f,
            bodyBounce = slow * 3.5f,
            hairSwayDegrees = slow * 4f,
            ahogeDegrees = slow * 5f,
            leftArmDegrees = 0f,
            rightArmDegrees = 0f,
            leftElbowDegrees = 5f,
            rightElbowDegrees = -5f,
            eyes = EyeShape.SLEEPING,
            mouth = MouthShape.SMALL_O,
            blushAlpha = 0.4f,
            browWorry = 0.35f,
            prop = Prop.PILLOW,
            particles = ParticleKind.SLEEP_Z,
        )
    }

    private fun eating(base: PetPose, t: Float): PetPose {
        // The fork rides up to her mouth, she chews, it goes back down.
        val cycle = (t % 1.6f) / 1.6f
        val lift = if (cycle < 0.45f) cycle / 0.45f else 1f - (cycle - 0.45f) / 0.55f
        val chewing = cycle in 0.4f..0.7f
        return base.copy(
            headBob = base.headBob + 1f,
            leftArmDegrees = 45f,
            rightArmDegrees = 20f + lift * 70f,
            leftElbowDegrees = 87f,
            rightElbowDegrees = -50f - lift * 65f,
            eyes = if (chewing) EyeShape.HAPPY_ARC else EyeShape.OPEN,
            mouth = if (chewing) MouthShape.CHEWING else MouthShape.SMILE,
            lookY = 0.3f,
            blushAlpha = 0.55f,
            prop = Prop.BOWL,
            propProgress = lift,
            particles = ParticleKind.CRUMBS,
        )
    }

    private fun loved(base: PetPose, t: Float): PetPose {
        val sway = sin(t * 2.4f)
        return base.copy(
            headTiltDegrees = sway * 7f,
            bodyBounce = base.bodyBounce - abs(sway) * 2.5f,
            hairSwayDegrees = sway * 10f,
            leftArmDegrees = 34f,
            rightArmDegrees = -34f,
            leftElbowDegrees = 70f,
            rightElbowDegrees = -70f,
            eyes = EyeShape.HEART,
            mouth = MouthShape.CAT,
            blushAlpha = 0.85f,
            particles = ParticleKind.HEARTS,
        )
    }

    private fun working(base: PetPose, t: Float): PetPose {
        // Typing: the forearms alternate in small fast strokes.
        val typeL = sin(t * 9f)
        val typeR = sin(t * 9f + 1.7f)
        return base.copy(
            // Rides the shared (already looping) tilt rather than adding a
            // slow oscillator of its own, which would not be a harmonic of
            // the typing frequency.
            headTiltDegrees = 4f + base.headTiltDegrees * 0.6f,
            headBob = base.headBob + 4f,
            // A small nod on the beat: her head dips fractionally as the
            // strokes land, which is what sells the arms as actually hitting
            // something rather than waving over it.
            bodyLean = 4f + typeL * 0.6f,
            leftArmDegrees = 46f,
            rightArmDegrees = -46f,
            leftElbowDegrees = 78f + typeL * 5f,
            rightElbowDegrees = -78f - typeR * 5f,
            eyes = EyeShape.FOCUSED,
            mouth = MouthShape.FLAT,
            lookY = 0.55f,
            blushAlpha = 0.3f,
            browWorry = -0.35f,
            prop = Prop.LAPTOP,
            propProgress = (typeL + 1f) / 2f,
            // What she is making, and what it is paying, in one stream.
            particles = ParticleKind.CODE,
        )
    }

    /**
     * The café: a tray held high on the left palm, a little bow of the head
     * to an invisible customer on the beat of the sway.
     */
    private fun serving(base: PetPose, t: Float): PetPose {
        val sway = sin(t * 2.2f)
        val bow = spike(loop(t, TWO_PI / 2.2f), 0.3f, 0.14f)
        return base.copy(
            headTiltDegrees = sway * 4f + bow * 8f,
            headBob = base.headBob + bow * 2.5f,
            bodyLean = sway * 2f,
            hairSwayDegrees = sin(t * 2.2f - 0.7f) * 7f,
            leftArmDegrees = 108f,
            rightArmDegrees = -14f - sway * 5f,
            leftElbowDegrees = 18f,
            rightElbowDegrees = -26f,
            eyes = if (base.blink > 0.5f) EyeShape.HAPPY_ARC else EyeShape.OPEN,
            mouth = MouthShape.SMILE,
            blushAlpha = 0.55f,
            prop = Prop.TRAY,
            propProgress = (sway + 1f) / 2f,
            particles = ParticleKind.STEAM,
        )
    }

    /**
     * The shop floor: a paper bag carried in both hands, hefted a little on
     * each step of a cheerful side-to-side rock.
     */
    private fun clerking(base: PetPose, t: Float): PetPose {
        val rock = sin(t * 2f)
        val heft = abs(sin(t * 2f)).pow(1.5f)
        return base.copy(
            headTiltDegrees = rock * 5f,
            bodyLean = rock * 3f,
            bodyBounce = base.bodyBounce - heft * 2f,
            hairSwayDegrees = sin(t * 2f - 0.8f) * 8f,
            // Folded up to the belly, not hanging at the hips: the angles below
            // land both hands on the rim of the bag at (±16, 158) in art space,
            // which is what makes it read as carried rather than as a box
            // floating over her skirt.
            leftArmDegrees = 30f,
            rightArmDegrees = -30f,
            leftElbowDegrees = 131f + heft * 7f,
            rightElbowDegrees = -131f - heft * 7f,
            eyes = EyeShape.OPEN,
            mouth = MouthShape.CAT,
            blushAlpha = 0.5f,
            prop = Prop.BAG,
            propProgress = heft,
            particles = ParticleKind.SPARKLES,
        )
    }

    /**
     * The idol stage: mic in the right hand at her mouth, the free arm thrown
     * up to the crowd, everything bouncing on the same beat.
     */
    private fun performing(base: PetPose, t: Float): PetPose {
        val beat = abs(sin(t * 1.2f)).pow(1.4f)
        val sway = sin(t * 1.2f)
        return base.copy(
            headTiltDegrees = sway * 7f,
            bodyLean = sway * 3f,
            bodyBounce = base.bodyBounce - beat * 7f,
            hairSwayDegrees = sin(t * 1.2f - 0.9f) * 14f,
            ahogeDegrees = sin(t * 2.4f) * 16f,
            leftArmDegrees = 138f + sway * 14f,
            // Elbow out to the side, forearm folded back across: the singer's
            // carry, which puts the hand just under her jaw so the mic lands at
            // her mouth instead of at her hip.
            rightArmDegrees = -100f + sway * 5f,
            leftElbowDegrees = 12f,
            rightElbowDegrees = -200f - beat * 6f,
            eyes = EyeShape.SPARKLE,
            mouth = MouthShape.BIG_SMILE,
            blushAlpha = 0.6f,
            prop = Prop.MIC,
            propProgress = beat,
            particles = ParticleKind.NOTES,
        )
    }

    /**
     * School: an open notebook and a pencil that actually scribbles — the
     * wiggle rides a harmonic of the six-second page cycle so the widget's
     * loop still closes.
     */
    private fun writing(base: PetPose, t: Float): PetPose {
        val scribble = sin(t * (TWO_PI * 7f / 6f))
        val turn = spike((t % 6f) / 6f, 0.5f, 0.06f)
        return base.copy(
            headTiltDegrees = 7f,
            headBob = base.headBob + 3f,
            bodyLean = 2f,
            leftArmDegrees = 38f,
            rightArmDegrees = -44f,
            leftElbowDegrees = 68f,
            rightElbowDegrees = -74f - scribble * 4f,
            eyes = EyeShape.FOCUSED,
            mouth = MouthShape.SMALL_O,
            lookY = 0.6f,
            lookX = 0.2f + scribble * 0.1f,
            blushAlpha = 0.35f,
            browWorry = -0.2f,
            prop = Prop.NOTEBOOK,
            propProgress = (scribble + 1f) / 2f + turn,
            particles = null,
        )
    }

    /**
     * The online course: headphones on, nodding along to a lecture.
     *
     * The office and the course both put her at a laptop, which made two of the
     * seven jobs the same picture — the exact complaint the props were added to
     * fix. Same desk, different *person*: she is listening here, not typing, so
     * the head nods on the beat of the talk, the hands rest, and the headphones
     * say at a glance which of the two this is.
     */
    private fun attending(base: PetPose, t: Float): PetPose {
        // A nod every two seconds, a bigger one of agreement every eight, and
        // a slow rock over the whole eight — every frequency a harmonic of the
        // loop, so the widget's flipbook still closes.
        val nod = sin(t * (TWO_PI / 2f))
        val slow = sin(t * (TWO_PI / 8f))
        val agree = spike((t % 8f) / 8f, 0.5f, 0.09f)
        return base.copy(
            headTiltDegrees = slow * 4f,
            headBob = base.headBob + nod * 2.2f + agree * 4f,
            bodyLean = slow * 1.6f,
            hairSwayDegrees = sin(t * (TWO_PI / 8f) - 0.6f) * 6f,
            leftArmDegrees = 34f,
            rightArmDegrees = -40f,
            leftElbowDegrees = 74f,
            rightElbowDegrees = -78f,
            eyes = EyeShape.FOCUSED,
            mouth = MouthShape.SMILE,
            lookY = 0.35f,
            lookX = slow * 0.18f,
            blushAlpha = 0.4f,
            prop = Prop.LECTURE,
            propProgress = (nod + 1f) / 2f,
            particles = ParticleKind.NOTES,
        )
    }

    private fun studying(base: PetPose, t: Float): PetPose {
        // Reading: eyes track across the page, a page turns every few seconds.
        // Five sweeps per page, so the eyes land back where they started.
        val scan = ((t * (5f / 6f)) % 1f)
        val turn = spike((t % 6f) / 6f, 0.5f, 0.06f)
        return base.copy(
            headTiltDegrees = 8f,
            headBob = base.headBob + 3f,
            bodyLean = 2f,
            leftArmDegrees = 40f,
            rightArmDegrees = -40f,
            leftElbowDegrees = 72f,
            rightElbowDegrees = -72f - turn * 30f,
            eyes = EyeShape.FOCUSED,
            mouth = MouthShape.FLAT,
            lookX = -0.5f + scan,
            lookY = 0.6f,
            blushAlpha = 0.3f,
            browWorry = -0.2f,
            prop = Prop.BOOK,
            propProgress = turn,
            particles = null,
        )
    }

    private fun playing(base: PetPose, t: Float): PetPose {
        val mash = sin(t * 11f)
        val lean = sin(t * 2.2f)
        return base.copy(
            headTiltDegrees = lean * 8f,
            bodyLean = lean * 4f,
            bodyBounce = base.bodyBounce - abs(sin(t * 4.4f)) * 2f,
            leftArmDegrees = 55f,
            rightArmDegrees = -55f,
            leftElbowDegrees = 130f + mash * 6f,
            rightElbowDegrees = -130f - mash * 6f,
            eyes = EyeShape.SPARKLE,
            mouth = MouthShape.BIG_SMILE,
            lookY = 0.35f,
            blushAlpha = 0.5f,
            prop = Prop.CONTROLLER,
            propProgress = (mash + 1f) / 2f,
            particles = ParticleKind.NOTES,
        )
    }

    private fun celebrating(base: PetPose, t: Float): PetPose {
        val jump = abs(sin(t * 3.6f)).pow(1.4f)
        return base.copy(
            bodyBounce = base.bodyBounce - jump * 11f,
            headBob = base.headBob - jump * 4f,
            hairSwayDegrees = sin(t * 3.6f - 1.1f) * 16f,
            leftArmDegrees = 100f + jump * 30f,
            rightArmDegrees = -100f - jump * 30f,
            leftElbowDegrees = 20f,
            rightElbowDegrees = -20f,
            eyes = EyeShape.SPARKLE,
            mouth = MouthShape.BIG_SMILE,
            blushAlpha = 0.65f,
            particles = ParticleKind.COINS,
        )
    }

    private const val BREATH_SPEED = 1.9f

    // --- widget loop ---------------------------------------------------------

    /**
     * The period of each state's own dominant motion, in seconds.
     *
     * A home-screen widget animates by flipping through a fixed set of
     * pre-rendered frames, so the frames have to form a seamless loop. Sampling
     * a state over a whole number of *its own* cycles is what makes the last
     * frame join back onto the first.
     */
    /**
     * How long one cycle of a state's motion takes.
     *
     * Internal rather than private so the widget's seam test can ask for the
     * period it is about to be looped at: each job's pantomime runs on its own
     * beat now, and a test that guessed 2.5 seconds for all of them would pass
     * while the tray job jolted once a cycle on a real home screen.
     */
    internal fun periodSeconds(state: PetState, workProp: Prop? = null): Float = when (state) {
        // Idle is entirely the shared base, which already loops.
        PetState.IDLE -> 2.5f
        PetState.HAPPY -> TWO_PI / 3.1f
        PetState.HUNGRY -> 5f
        PetState.TIRED -> TWO_PI / 0.9f
        PetState.SLEEPING -> TWO_PI / 0.8f
        PetState.EATING -> 1.6f
        PetState.LOVED -> TWO_PI / 2.4f
        // Each job's pantomime has its own dominant beat.
        PetState.WORKING -> when (workProp) {
            Prop.TRAY -> TWO_PI / 2.2f
            Prop.BAG -> TWO_PI / 2f
            Prop.MIC -> TWO_PI / 1.2f
            else -> TWO_PI / 9f
        }
        PetState.STUDYING -> when (workProp) {
            Prop.LAPTOP -> TWO_PI / 9f
            // The lecture: the nod runs at 2s and the nod-of-agreement at 8,
            // so eight seconds is the shortest window both close in.
            Prop.LECTURE -> 8f
            else -> 6f
        }
        // The lean is the slowest thing she does while playing; the mash at
        // 11 and the bounce at 4.4 are both multiples of it.
        PetState.PLAYING -> TWO_PI / 2.2f
        PetState.CELEBRATING -> TWO_PI / 3.6f
    }

    /**
     * Frame [index] of [frameCount] in a seamless loop lasting about
     * [loopSeconds].
     *
     * The state is sampled over a whole number of its own cycles, chosen to sit
     * closest to the requested loop length, so fast motions (typing, button
     * mashing) still run at their natural speed rather than being stretched
     * across the whole loop. Blinking is replaced by a single pulse per loop —
     * the free-running blink would otherwise be cut in half at the seam.
     */
    fun widgetLoopFrame(
        state: PetState,
        index: Int,
        frameCount: Int,
        loopSeconds: Float = 2.4f,
        workProp: Prop? = null,
    ): PetPose {
        val phase = index.toFloat() / frameCount.coerceAtLeast(1)
        val period = periodSeconds(state, workProp)
        val cycles = max(1f, (loopSeconds / period).roundToInt().toFloat())
        val t = phase * period * cycles
        val turns = TWO_PI * phase

        // Every shared oscillator is rewritten as a harmonic of the loop, so it
        // meets itself at the seam. The state is then applied on top and loops
        // too, because the window is a whole number of its own cycles.
        val base = idleBase(t, state).copy(
            timeSeconds = t,
            breath = sin(turns),
            bodyBounce = sin(turns) * 1.2f,
            headBob = sin(turns + 0.4f) * 1.6f,
            headTiltDegrees = sin(turns) * 2.5f,
            hairSwayDegrees = sin(turns - 0.7f) * 5f,
            ahogeDegrees = sin(2f * turns) * 12f,
            lookX = sin(turns) * 0.35f,
            lookY = sin(turns + 1.1f) * 0.15f,
            particlePhase = phase,
            // One deliberate blink per loop; the free-running one would be cut
            // in half at the seam.
            blink = spike(phase, 0.42f, 0.05f),
        )
        return pose(state, t, base, workProp)
    }

    private const val TWO_PI = (2.0 * PI).toFloat()
}

/** Shared helper for the renderer: a value that loops smoothly over [period]. */
internal fun loop(t: Float, period: Float): Float = (t % period) / period

internal fun wave(t: Float, speed: Float, phase: Float = 0f): Float =
    sin(t * speed + phase)

internal fun coswave(t: Float, speed: Float, phase: Float = 0f): Float =
    cos(t * speed + phase)

internal const val DEG: Float = (PI / 180.0).toFloat()
