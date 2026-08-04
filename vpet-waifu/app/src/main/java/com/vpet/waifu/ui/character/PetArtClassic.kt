package com.vpet.waifu.ui.character

/*
 * The character as she was before the redraw, kept as a choice rather than as
 * history.
 *
 * She is a different drawing, not a variant of the current one: rounder, two
 * and a half heads tall instead of three and a third, violet twin tails, no
 * contour anywhere. Every coordinate in here is tuned to that body, and the
 * props hang off its joints, so it is a whole rig rather than a palette swap —
 * which is why this is a copy and not a parameter.
 *
 * Kotlin scopes private top-level declarations to their file, so this rig and
 * the current one can both have a `drawFringe`; only the handful of names that
 * were public needed changing.
 */

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val TWO_PI_F = 6.2831855f

/**
 * The character renderer.
 *
 * She is drawn from parts — back hair, twin tails, legs, skirt, torso, arms,
 * head, face, props, particles — every frame, from a [PetPose]. That is what
 * makes her animate: there is no sprite sheet to author, so a new state costs a
 * pose function rather than a folder of frames, and the motion stays smooth at
 * whatever rate the display refreshes.
 *
 * All coordinates are in a fixed [ART_WIDTH] x [ART_HEIGHT] space scaled to fit
 * whatever canvas it is given, so the same code draws a 56dp bubble, a 280dp
 * portrait and the widget's bitmap.
 */
private const val CLASSIC_ART_WIDTH = 200f
private const val CLASSIC_ART_HEIGHT = 280f

private const val CX = 100f

// Head.
private const val HEAD_CY = 80f
private const val HEAD_RX = 40f
private const val HEAD_RY = 43f

// Body. The head pivots about the neck, so head motion drags the whole face.
private const val NECK_Y = 120f
private const val SHOULDER_Y = 140f
private const val SHOULDER_DX = 27f
private const val UPPER_ARM = 26f
private const val FOREARM = 25f
private const val TORSO_TOP = 128f
private const val TORSO_BOTTOM = 178f
private const val SKIRT_TOP = 170f
private const val SKIRT_BOTTOM = 196f
private const val LEG_TOP = 192f
private const val LEG_BOTTOM = 240f
private const val GROUND_Y = 252f

fun DrawScope.drawClassicPet(pose: PetPose, palette: PetPalette = PetPalette.Classic) {
    val scale = min(size.width / CLASSIC_ART_WIDTH, size.height / CLASSIC_ART_HEIGHT)
    val offsetX = (size.width - CLASSIC_ART_WIDTH * scale) / 2f
    val offsetY = (size.height - CLASSIC_ART_HEIGHT * scale) / 2f

    withTransform({
        translate(offsetX, offsetY)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        drawCharacter(pose, palette)
    }
}

private fun DrawScope.drawCharacter(pose: PetPose, palette: PetPalette) {
    // The workplace stands still while she moves in it, so it goes down before
    // the lean-and-bounce transform rather than inside it.
    pose.prop?.let { drawWorkplace(it, palette) }
    drawGroundShadow(pose)

    withTransform({ translate(pose.bodyLean, pose.bodyBounce + pose.droop * 3f) }) {
        // Bedding sits behind absolutely everything, including the hair.
        if (pose.prop == Prop.PILLOW) drawPillow(palette)

        drawBackHair(pose, palette)
        drawTwinTail(pose, palette, mirrored = false)
        drawTwinTail(pose, palette, mirrored = true)

        when (pose.prop) {
            Prop.LAPTOP, Prop.BOOK, Prop.NOTEBOOK, Prop.BOOKSTACK, Prop.LECTURE -> drawDesk(palette)
            else -> Unit
        }

        drawLegs(pose, palette)
        drawSkirt(pose, palette)
        drawTorso(pose, palette)
        drawArm(pose, palette, left = true)
        drawArm(pose, palette, left = false)
        drawHeadGroup(pose, palette)
        pose.prop?.let { drawPropInFront(it, pose, palette) }
    }

    pose.particles?.let { drawParticles(it, pose, palette) }
}

// --- silhouette --------------------------------------------------------------

/**
 * The contact shadow.
 *
 * Near-black rather than the tinted violet it used to be, and in two passes: a
 * wide soft one and a tight dark core. The old single 9%-alpha violet was
 * lighter than the night floor it was drawn on, so after dark she had no
 * shadow at all and stood a few pixels above the boards.
 */
private fun DrawScope.drawGroundShadow(pose: PetPose) {
    val lift = max(0f, -pose.bodyBounce)
    val squash = 1f - (lift / 22f).coerceIn(0f, 0.45f)
    val ink = Color(0xFF120C1E)
    drawOval(
        color = ink.copy(alpha = 0.15f * squash),
        topLeft = Offset(CX - 44f * squash, GROUND_Y - 2f),
        size = Size(88f * squash, 17f * squash),
    )
    drawOval(
        color = ink.copy(alpha = 0.34f * squash),
        topLeft = Offset(CX - 27f * squash, GROUND_Y + 2f),
        size = Size(54f * squash, 10f * squash),
    )
}

// --- hair --------------------------------------------------------------------

private fun DrawScope.drawBackHair(pose: PetPose, palette: PetPalette) {
    // Narrow enough below the head that the shoulders and arms stay outside the
    // silhouette — a wide blob here is what makes chibi characters look armless.
    val sway = pose.hairSwayDegrees * 0.3f
    val fall = Path().apply {
        moveTo(CX - 45f, HEAD_CY + 4f)
        cubicTo(CX - 50f, 120f, CX - 40f + sway, 150f, CX - 33f + sway, 176f)
        cubicTo(CX - 26f + sway, 186f, CX + 26f + sway, 186f, CX + 33f + sway, 176f)
        cubicTo(CX + 40f + sway, 150f, CX + 50f, 120f, CX + 45f, HEAD_CY + 4f)
        close()
    }
    drawPath(fall, palette.hairShade)
    drawOval(
        color = palette.hairShade,
        topLeft = Offset(CX - 46f, HEAD_CY - 47f),
        size = Size(92f, 96f),
    )
}

/**
 * One twin tail: a tapered blade with a highlight and a ribbon tie, rotated by
 * the sway so the tails trail the head instead of moving with it.
 */
private fun DrawScope.drawTwinTail(pose: PetPose, palette: PetPalette, mirrored: Boolean) {
    val side = if (mirrored) -1f else 1f
    val anchor = Offset(CX + side * 38f, HEAD_CY - 16f)
    // The far tail swings a little less, which reads as depth.
    val sway = pose.hairSwayDegrees * (if (mirrored) 0.75f else 1f)

    withTransform({
        rotate(sway * side, pivot = anchor)
        scale(side, 1f, pivot = anchor)
    }) {
        val body = Path().apply {
            moveTo(anchor.x - 2f, anchor.y - 8f)
            cubicTo(anchor.x + 30f, anchor.y - 2f, anchor.x + 39f, anchor.y + 38f, anchor.x + 27f, anchor.y + 84f)
            cubicTo(anchor.x + 23f, anchor.y + 98f, anchor.x + 13f, anchor.y + 103f, anchor.x + 6f, anchor.y + 100f)
            cubicTo(anchor.x + 17f, anchor.y + 76f, anchor.x + 16f, anchor.y + 36f, anchor.x + 2f, anchor.y + 10f)
            close()
        }
        drawPath(body, palette.hair)

        val highlight = Path().apply {
            moveTo(anchor.x + 7f, anchor.y + 8f)
            cubicTo(anchor.x + 23f, anchor.y + 20f, anchor.x + 25f, anchor.y + 50f, anchor.x + 18f, anchor.y + 76f)
            cubicTo(anchor.x + 16f, anchor.y + 54f, anchor.x + 14f, anchor.y + 26f, anchor.x + 5f, anchor.y + 14f)
            close()
        }
        drawPath(highlight, palette.hairLight.copy(alpha = 0.5f))

        drawOval(palette.ribbon, Offset(anchor.x - 7f, anchor.y - 2f), Size(18f, 12f))
        drawOval(palette.ribbon.copy(alpha = 0.6f), Offset(anchor.x - 3f, anchor.y + 1f), Size(10f, 6f))
    }
}

// --- body ---------------------------------------------------------------------

private fun DrawScope.drawLegs(pose: PetPose, palette: PetPalette) {
    val swing = wave(pose.timeSeconds, 1.9f) * 1.1f
    listOf(-13f to swing, 13f to -swing).forEach { (dx, dy) ->
        // Thigh, then an over-the-knee sock, then a shoe.
        drawRoundedBar(
            from = Offset(CX + dx, LEG_TOP),
            to = Offset(CX + dx + dy, LEG_BOTTOM - 16f),
            width = 14f,
            color = palette.skin,
        )
        drawRoundedBar(
            from = Offset(CX + dx + dy * 0.7f, LEG_BOTTOM - 20f),
            to = Offset(CX + dx + dy, LEG_BOTTOM + 4f),
            width = 14.5f,
            color = palette.sock,
        )
        drawShoe(Offset(CX + dx + dy, LEG_BOTTOM + 2f), palette)
    }
}

/**
 * A Mary Jane seen head-on: a rounded upper on a pale sole, with a heel block
 * showing behind it.
 *
 * The flat oval this replaces had no sole and no heel, so at any size above a
 * widget's it read as a puddle of ink under the sock rather than as a shoe.
 */
private fun DrawScope.drawShoe(at: Offset, palette: PetPalette) {
    // Heel first: the sole is drawn over its top edge, leaving a block below.
    drawRoundRectPath(
        Rect(at.x - 5f, at.y + 8f, at.x + 5f, at.y + 13f),
        radius = 1.4f,
        color = palette.shoe,
    )
    drawRoundRectPath(
        Rect(at.x - 9.5f, at.y - 1f, at.x + 9.5f, at.y + 9f),
        radius = 4.6f,
        color = palette.shoe,
    )
    drawRoundRectPath(
        Rect(at.x - 8.8f, at.y + 7.5f, at.x + 8.8f, at.y + 10.5f),
        radius = 1.5f,
        color = Color(0xFFE8DAC4),
    )
    // The strap. One pale line across the instep is what separates a school
    // shoe from a boot.
    drawLine(
        color = palette.collar.copy(alpha = 0.4f),
        start = Offset(at.x - 7.5f, at.y + 2f),
        end = Offset(at.x + 7.5f, at.y + 2f),
        strokeWidth = 1.5f,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawSkirt(pose: PetPose, palette: PetPalette) {
    val flare = 1f + abs(pose.bodyBounce) * 0.018f
    val path = Path().apply {
        moveTo(CX - 27f, SKIRT_TOP)
        lineTo(CX + 27f, SKIRT_TOP)
        lineTo(CX + 40f * flare, SKIRT_BOTTOM)
        lineTo(CX - 40f * flare, SKIRT_BOTTOM)
        close()
    }
    drawPath(path, palette.skirt)
    for (i in -2..2) {
        drawLine(
            color = palette.uniformShade.copy(alpha = 0.55f),
            start = Offset(CX + i * 12f, SKIRT_TOP + 1f),
            end = Offset(CX + i * 18f * flare, SKIRT_BOTTOM - 1f),
            strokeWidth = 1.8f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawTorso(pose: PetPose, palette: PetPalette) {
    val chest = pose.breath
    // Neck first, so the collar overlaps it.
    drawRoundedBar(Offset(CX, NECK_Y - 6f), Offset(CX, TORSO_TOP + 4f), width = 16f, color = palette.skinShade)

    val torso = Path().apply {
        moveTo(CX - 24f - chest, TORSO_TOP)
        cubicTo(CX - 30f - chest, TORSO_TOP + 18f, CX - 29f, TORSO_TOP + 38f, CX - 27f, TORSO_BOTTOM)
        lineTo(CX + 27f, TORSO_BOTTOM)
        cubicTo(CX + 29f, TORSO_TOP + 38f, CX + 30f + chest, TORSO_TOP + 18f, CX + 24f + chest, TORSO_TOP)
        close()
    }
    drawPath(torso, palette.uniform)

    // Sailor collar.
    val collar = Path().apply {
        moveTo(CX - 25f, TORSO_TOP - 1f)
        cubicTo(CX - 18f, TORSO_TOP - 6f, CX + 18f, TORSO_TOP - 6f, CX + 25f, TORSO_TOP - 1f)
        lineTo(CX + 20f, TORSO_TOP + 14f)
        lineTo(CX, TORSO_TOP + 25f)
        lineTo(CX - 20f, TORSO_TOP + 14f)
        close()
    }
    drawPath(collar, palette.collar)
    listOf(-1f, 1f).forEach { side ->
        drawLine(
            color = palette.hair.copy(alpha = 0.3f),
            start = Offset(CX + side * 18f, TORSO_TOP + 7f),
            end = Offset(CX, TORSO_TOP + 21f),
            strokeWidth = 1.8f,
            cap = StrokeCap.Round,
        )
    }

    // Neck ribbon.
    val knotY = TORSO_TOP + 20f
    val ribbon = Path().apply {
        moveTo(CX, knotY)
        lineTo(CX - 12f, knotY - 6f)
        lineTo(CX - 12f, knotY + 7f)
        close()
        moveTo(CX, knotY)
        lineTo(CX + 12f, knotY - 6f)
        lineTo(CX + 12f, knotY + 7f)
        close()
    }
    drawPath(ribbon, palette.ribbon)
    drawCircle(palette.ribbon, radius = 4f, center = Offset(CX, knotY))
}

// Arm joints, shared by the limb renderer and by anything she holds.

private fun shoulderOf(pose: PetPose, left: Boolean): Offset =
    Offset(CX + (if (left) -1f else 1f) * SHOULDER_DX, SHOULDER_Y + pose.droop * 3f)

private fun shoulderAngle(pose: PetPose, left: Boolean): Float =
    if (left) -pose.leftArmDegrees else -pose.rightArmDegrees

private fun forearmAngle(pose: PetPose, left: Boolean): Float =
    shoulderAngle(pose, left) + (if (left) pose.leftElbowDegrees else pose.rightElbowDegrees)

private fun elbowOf(pose: PetPose, left: Boolean): Offset =
    polar(shoulderOf(pose, left), shoulderAngle(pose, left), UPPER_ARM)

/** Where her hand ends up this frame. Props hang off this, so they never drift. */
private fun classicHandOf(pose: PetPose, left: Boolean): Offset =
    polar(elbowOf(pose, left), forearmAngle(pose, left), FOREARM)

/**
 * A two-segment arm. The pose gives shoulder and elbow angles where 0 points
 * straight down and positive swings outwards, which is how typing, eating and
 * cheering all reuse one piece of code.
 */
private fun DrawScope.drawArm(pose: PetPose, palette: PetPalette, left: Boolean) {
    val shoulder = shoulderOf(pose, left)
    val elbow = elbowOf(pose, left)
    val angle = forearmAngle(pose, left)

    // Puffed sleeve in a lighter shade so the arm separates from the torso.
    drawCircle(palette.uniformShade, radius = 9.5f, center = shoulder)
    drawRoundedBar(shoulder, elbow, width = 14f, color = palette.uniform)
    // The sleeve's own round cap is the elbow now: the bright collar-coloured
    // ball that used to sit here read as a doll's ball joint from any distance.

    // The mitten is wider than the forearm, and that step is the wrist.
    val wrist = polar(elbow, angle, FOREARM - 5.5f)
    drawRoundedBar(elbow, wrist, width = 11f, color = palette.skin)
    drawRoundedBar(wrist, polar(wrist, angle, 5f), width = 13f, color = palette.skin)
    // A thumb, always on the side facing her body: without it a hand at this
    // scale is just a dot on the end of a stick, and every prop she holds looks
    // balanced on her wrist.
    val inward = if (left) 1f else -1f
    drawRoundedBar(wrist, polar(wrist, angle + inward * 74f, 4.6f), width = 6.4f, color = palette.skin)
}

// --- head ----------------------------------------------------------------------

private fun DrawScope.drawHeadGroup(pose: PetPose, palette: PetPalette) {
    val pivot = Offset(CX, NECK_Y)
    withTransform({
        rotate(pose.headTiltDegrees, pivot = pivot)
        translate(0f, pose.headBob)
    }) {
        drawOval(palette.skinShade, Offset(CX - HEAD_RX - 5f, HEAD_CY), Size(13f, 18f))
        drawOval(palette.skinShade, Offset(CX + HEAD_RX - 8f, HEAD_CY), Size(13f, 18f))

        drawHeadShape(palette)
        // The shadow the bangs cast. It has to reach well *below* the fringe's
        // edge to be seen at all: the oval this replaces sat entirely under the
        // hair and never rendered a pixel.
        drawOval(
            color = palette.skinShade.copy(alpha = 0.3f),
            topLeft = Offset(CX - 36f, HEAD_CY - 26f),
            size = Size(72f, 32f),
        )

        drawFace(pose, palette)
        drawFringe(pose, palette)
        // Brows last. They used to go down with the face and were then buried
        // under the fringe, so the worry axis seven poses set moved nothing at
        // all; over the hair they read the way anime bangs always let them.
        drawBrows(pose, palette)
        drawAhoge(pose, palette)
    }
}

/**
 * The head: a skull that tapers to a chin rather than a plain oval.
 *
 * Same width and same height as the ellipse it replaces, so nothing else on the
 * face has to move — but the lower third pulls in to a soft point, which is the
 * whole difference between a face and an egg.
 */
private fun DrawScope.drawHeadShape(palette: PetPalette) {
    val head = Path().apply {
        moveTo(CX - HEAD_RX, HEAD_CY - 4f)
        cubicTo(
            CX - HEAD_RX, HEAD_CY - HEAD_RY * 0.78f,
            CX - HEAD_RX * 0.62f, HEAD_CY - HEAD_RY,
            CX, HEAD_CY - HEAD_RY,
        )
        cubicTo(
            CX + HEAD_RX * 0.62f, HEAD_CY - HEAD_RY,
            CX + HEAD_RX, HEAD_CY - HEAD_RY * 0.78f,
            CX + HEAD_RX, HEAD_CY - 4f,
        )
        cubicTo(
            CX + HEAD_RX, HEAD_CY + 17f,
            CX + 27f, HEAD_CY + 31f,
            CX + 11f, HEAD_CY + 39f,
        )
        cubicTo(
            CX + 5f, HEAD_CY + 43f,
            CX - 5f, HEAD_CY + 43f,
            CX - 11f, HEAD_CY + 39f,
        )
        cubicTo(
            CX - 27f, HEAD_CY + 31f,
            CX - HEAD_RX, HEAD_CY + 17f,
            CX - HEAD_RX, HEAD_CY - 4f,
        )
        close()
    }
    drawPath(head, palette.skin)
}

private fun DrawScope.drawFringe(pose: PetPose, palette: PetPalette) {
    val sway = pose.hairSwayDegrees * 0.12f

    // Bangs: a smooth cap with three soft points, no cusps.
    //
    // The lower edge sits a good ten points higher than it used to. The old
    // teeth hung to the top of the eyes and the notches between them made two
    // dark slanted wedges exactly where eyebrows belong — she scowled in every
    // state, including the happy ones. Clearing the brow line is what lets a
    // real brow be drawn there and mean something.
    val fringe = Path().apply {
        moveTo(CX - 41f, HEAD_CY + 2f)
        cubicTo(CX - 45f, HEAD_CY - 30f, CX - 26f, HEAD_CY - 46f, CX, HEAD_CY - 46f)
        cubicTo(CX + 26f, HEAD_CY - 46f, CX + 45f, HEAD_CY - 30f, CX + 41f, HEAD_CY + 2f)
        cubicTo(CX + 40f, HEAD_CY - 11f, CX + 37f, HEAD_CY - 18f, CX + 28f + sway, HEAD_CY - 17f)
        cubicTo(CX + 18f, HEAD_CY - 27f, CX + 8f, HEAD_CY - 27f, CX + 1f + sway, HEAD_CY - 18.5f)
        cubicTo(CX - 8f, HEAD_CY - 27f, CX - 20f, HEAD_CY - 27f, CX - 28f + sway, HEAD_CY - 17f)
        cubicTo(CX - 37f, HEAD_CY - 18f, CX - 40f, HEAD_CY - 11f, CX - 41f, HEAD_CY + 2f)
        close()
    }
    drawPath(fringe, palette.hair)

    // Side locks that frame the cheeks and taper to a point.
    listOf(-1f, 1f).forEach { side ->
        val lock = Path().apply {
            moveTo(CX + side * 40f, HEAD_CY - 16f)
            cubicTo(
                CX + side * 49f, HEAD_CY + 4f,
                CX + side * 45f, HEAD_CY + 30f,
                CX + side * 35f, HEAD_CY + 44f,
            )
            cubicTo(
                CX + side * 34f, HEAD_CY + 26f,
                CX + side * 35f, HEAD_CY + 6f,
                CX + side * 31f, HEAD_CY - 12f,
            )
            close()
        }
        drawPath(lock, palette.hair)
    }

    // Glossy highlight band across the bangs.
    val gloss = Path().apply {
        moveTo(CX - 27f, HEAD_CY - 30f)
        cubicTo(CX - 12f, HEAD_CY - 43f, CX + 12f, HEAD_CY - 43f, CX + 27f, HEAD_CY - 30f)
        cubicTo(CX + 12f, HEAD_CY - 36f, CX - 12f, HEAD_CY - 36f, CX - 27f, HEAD_CY - 30f)
        close()
    }
    drawPath(gloss, palette.hairLight.copy(alpha = 0.55f))
}

private fun DrawScope.drawAhoge(pose: PetPose, palette: PetPalette) {
    // The signature cowlick. A single moving strand does more for "alive" than
    // anything else on the character.
    val base = Offset(CX + 3f, HEAD_CY - 43f)
    val bend = pose.ahogeDegrees
    val path = Path().apply {
        moveTo(base.x, base.y)
        cubicTo(
            base.x + 5f + bend * 0.3f, base.y - 15f,
            base.x + 18f + bend * 0.6f, base.y - 19f,
            base.x + 20f + bend, base.y - 6f,
        )
    }
    drawPath(path, palette.hair, style = Stroke(width = 4.5f, cap = StrokeCap.Round))
}

private fun DrawScope.drawFace(pose: PetPose, palette: PetPalette) {
    val eyeY = HEAD_CY + 8f
    drawEye(Offset(CX - 17f, eyeY), pose, palette, mirrored = false)
    drawEye(Offset(CX + 17f, eyeY), pose, palette, mirrored = true)

    val blush = palette.blush.copy(alpha = pose.blushAlpha)
    drawOval(blush, Offset(CX - 39f, eyeY + 11f), Size(20f, 11f))
    drawOval(blush, Offset(CX + 19f, eyeY + 11f), Size(20f, 11f))

    // A nose at chibi scale is a hint, not a feature: one short soft stroke
    // under the eye line, off centre the way a light from the upper left would
    // put it, so the face stops being perfectly flat between eyes and mouth.
    drawLine(
        color = palette.skinShade,
        start = Offset(CX + 1f, eyeY + 7.5f),
        end = Offset(CX + 3.5f, eyeY + 10.5f),
        strokeWidth = 2.2f,
        cap = StrokeCap.Round,
    )

    drawMouth(pose, palette, Offset(CX, eyeY + 20f))
}

/**
 * The eyebrows, drawn over the bangs.
 *
 * Soft and short, with the inner end sitting slightly higher than the outer one
 * by default — level or inner-low brows read as a glare. The worry axis lifts
 * the inner ends further and drops the outer ones, which is the difference
 * between "worried" and "angry" and the only thing seven of the poses have to
 * say it with.
 */
private fun DrawScope.drawBrows(pose: PetPose, palette: PetPalette) {
    val y = HEAD_CY - 12.5f
    val innerDy = -2f - pose.browWorry * 4f
    val outerDy = pose.browWorry * 2.5f
    listOf(-1f, 1f).forEach { side ->
        val path = Path().apply {
            moveTo(CX + side * 26f, y + outerDy)
            quadraticTo(CX + side * 18f, y - 3.5f + outerDy * 0.3f, CX + side * 10f, y + innerDy)
        }
        drawPath(
            path,
            palette.hairShade.copy(alpha = 0.8f),
            style = Stroke(width = 2.6f, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawEye(
    center: Offset,
    pose: PetPose,
    palette: PetPalette,
    mirrored: Boolean,
) {
    when (pose.eyes) {
        EyeShape.HAPPY_ARC -> {
            val path = Path().apply {
                moveTo(center.x - 12f, center.y + 3f)
                quadraticTo(center.x, center.y - 13f, center.x + 12f, center.y + 3f)
            }
            drawPath(path, palette.eyeDark, style = Stroke(width = 3.8f, cap = StrokeCap.Round))
            return
        }
        EyeShape.SLEEPING -> {
            val path = Path().apply {
                moveTo(center.x - 12f, center.y - 3f)
                quadraticTo(center.x, center.y + 11f, center.x + 12f, center.y - 3f)
            }
            drawPath(path, palette.eyeDark, style = Stroke(width = 3.8f, cap = StrokeCap.Round))
            return
        }
        else -> Unit
    }

    val openness = when (pose.eyes) {
        EyeShape.HALF_LIDDED -> 0.52f
        EyeShape.FOCUSED -> 0.8f
        else -> 1f
    } * (1f - pose.blink)

    if (openness <= 0.06f) {
        drawLine(
            color = palette.eyeDark,
            start = Offset(center.x - 11f, center.y),
            end = Offset(center.x + 11f, center.y),
            strokeWidth = 3.4f,
            cap = StrokeCap.Round,
        )
        return
    }

    withTransform({ scale(1f, openness, pivot = center) }) {
        drawOval(palette.white, Offset(center.x - 12f, center.y - 15f), Size(24f, 30f))

        val iris = Offset(center.x + pose.lookX * 3f, center.y + pose.lookY * 3.5f)
        if (pose.eyes == EyeShape.HEART) {
            // A heart *replaces* the iris; drawing it over one leaves a pink blob.
            drawHeart(Offset(iris.x, iris.y + 1f), 11f, palette.ribbon)
            drawHeart(Offset(iris.x - 2f, iris.y - 1.5f), 4.5f, palette.white.copy(alpha = 0.85f))
            return@withTransform
        }
        drawCircle(palette.irisDeep, radius = 10.5f, center = iris)
        drawCircle(palette.iris, radius = 8.8f, center = Offset(iris.x, iris.y + 1.5f))
        drawCircle(palette.irisLight, radius = 5f, center = Offset(iris.x, iris.y + 4.5f))
        drawCircle(palette.eyeDark, radius = 4.4f, center = iris)

        when (pose.eyes) {
            EyeShape.SPARKLE -> {
                drawStar(Offset(iris.x - 3f, iris.y - 3f), 6.5f, palette.white)
                drawStar(Offset(iris.x + 4.5f, iris.y + 4.5f), 3.2f, palette.white)
            }
            else -> {
                val hx = if (mirrored) 3.5f else -3.5f
                drawCircle(palette.white, radius = 3.6f, center = Offset(iris.x + hx, iris.y - 4.5f))
                drawCircle(
                    palette.white.copy(alpha = 0.75f),
                    radius = 2f,
                    center = Offset(iris.x - hx * 0.6f, iris.y + 5.5f),
                )
            }
        }

        // Upper lash, as a filled crescent rather than a uniform stroke: thick
        // over the pupil and tapering to points at both corners. A constant
        // width here is what makes the eye read as a scowl.
        //
        // The inner corner sits almost level with the outer one. It used to
        // drop six points toward the nose, and two lashes converging downward
        // at the bridge are the shape of an angry brow — she glared through
        // every state on the roster, including the ones with hearts for eyes.
        val outer = if (mirrored) 1f else -1f
        val cornerOut = Offset(center.x + outer * 15f, center.y - 15f)
        val cornerIn = Offset(center.x - outer * 12f, center.y - 12.5f)
        val lash = Path().apply {
            moveTo(cornerOut.x, cornerOut.y)
            quadraticTo(center.x + outer * 3f, center.y - 22.5f, cornerIn.x, cornerIn.y)
            quadraticTo(center.x + outer * 4f, center.y - 16f, cornerOut.x, cornerOut.y)
            close()
        }
        drawPath(lash, palette.eyeDark)
    }
}

private fun DrawScope.drawMouth(pose: PetPose, palette: PetPalette, at: Offset) {
    when (pose.mouth) {
        MouthShape.SMILE -> {
            val path = Path().apply {
                moveTo(at.x - 7f, at.y - 1f)
                quadraticTo(at.x, at.y + 6f, at.x + 7f, at.y - 1f)
            }
            drawPath(path, palette.mouth, style = Stroke(width = 2.6f, cap = StrokeCap.Round))
        }
        MouthShape.BIG_SMILE -> {
            val path = Path().apply {
                moveTo(at.x - 9f, at.y - 2f)
                quadraticTo(at.x, at.y + 12f, at.x + 9f, at.y - 2f)
                close()
            }
            drawPath(path, palette.mouthInner)
            drawOval(palette.white, Offset(at.x - 6.5f, at.y - 2f), Size(13f, 3.4f))
        }
        MouthShape.CAT -> {
            val path = Path().apply {
                // ":3" — the humps dip down and the corners lift, which is the
                // opposite of the arcs used for a frown.
                moveTo(at.x - 9f, at.y - 2f)
                quadraticTo(at.x - 4.5f, at.y + 5f, at.x, at.y)
                quadraticTo(at.x + 4.5f, at.y + 5f, at.x + 9f, at.y - 2f)
            }
            drawPath(
                path, palette.mouth,
                style = Stroke(width = 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        MouthShape.WAVY -> {
            val path = Path().apply {
                moveTo(at.x - 9f, at.y)
                quadraticTo(at.x - 4.5f, at.y - 5f, at.x, at.y)
                quadraticTo(at.x + 4.5f, at.y + 5f, at.x + 9f, at.y)
            }
            drawPath(path, palette.mouth, style = Stroke(width = 2.6f, cap = StrokeCap.Round))
        }
        MouthShape.SMALL_O -> drawOval(palette.mouthInner, Offset(at.x - 4f, at.y - 4f), Size(8f, 10f))
        MouthShape.FLAT -> drawLine(
            palette.mouth,
            start = Offset(at.x - 6f, at.y + 1f),
            end = Offset(at.x + 6f, at.y + 1f),
            strokeWidth = 2.6f,
            cap = StrokeCap.Round,
        )
        MouthShape.CHEWING -> {
                // Four chews per mouthful: a multiple of the eating cycle, so the
            // jaw is closed again at the seam.
            val open = (wave(pose.timeSeconds, 15.708f) + 1f) / 2f
            drawOval(
                palette.mouthInner,
                Offset(at.x - 6f, at.y - 2f - open * 2.5f),
                Size(12f, 5f + open * 7f),
            )
        }
    }
}

// --- workplaces ------------------------------------------------------------------

/**
 * Where the job happens.
 *
 * Giving each occupation its own prop fixed her hands and left the *place*
 * alone: café, shop, office, stage, classroom and lecture hall were all her
 * bedroom with a different object in it, which is what "почему одна и та же
 * сцена" was actually about. Each job now gets a few pieces of set dressing
 * behind her instead.
 *
 * They are drawn as furniture — framed, with ends — rather than as a full-bleed
 * band, because the art box is narrower than the stage and a band would read as
 * a card floating in the middle of the room. Everything stays inside
 * [ART_WIDTH] x [ART_HEIGHT] and stays cheap: at this size a suggestion of a
 * place beats a drawing of one.
 */
private fun DrawScope.drawWorkplace(prop: Prop, palette: PetPalette) {
    when (prop) {
        Prop.TRAY -> drawCafe(palette)
        Prop.BAG -> drawShopFloor(palette)
        Prop.LAPTOP -> drawOffice(palette)
        Prop.MIC -> drawStage(palette)
        Prop.NOTEBOOK -> drawClassroom(palette)
        Prop.LECTURE -> drawStudyNook(palette)
        Prop.BOOKSTACK -> drawLibrary(palette)
        // Eating, sleeping, reading and gaming happen at home, and home is the
        // room already drawn behind her.
        else -> Unit
    }
}

/** The café: a service counter, a chalked menu and a pendant lamp. */
private fun DrawScope.drawCafe(palette: PetPalette) {
    val wood = Color(0xFFB07C4F)
    val woodDark = Color(0xFF8A5E39)

    // The lamp hangs from the ceiling, so its cord starts off the top edge.
    val lampX = 26f
    drawLine(woodDark, Offset(lampX, 0f), Offset(lampX, 30f), 1.6f)
    val shade = Path().apply {
        moveTo(lampX - 5f, 30f)
        lineTo(lampX + 5f, 30f)
        lineTo(lampX + 14f, 46f)
        lineTo(lampX - 14f, 46f)
        close()
    }
    drawPath(shade, woodDark)
    drawOval(palette.accent.copy(alpha = 0.85f), Offset(lampX - 11f, 43f), Size(22f, 7f))
    drawOval(palette.accent.copy(alpha = 0.16f), Offset(lampX - 24f, 46f), Size(48f, 40f))

    // The menu board, chalked.
    drawRoundRectPath(Rect(126f, 40f, 192f, 96f), radius = 4f, color = woodDark)
    drawRoundRectPath(Rect(130f, 44f, 188f, 92f), radius = 3f, color = Color(0xFF33302E))
    for (i in 0..3) {
        val y = 54f + i * 9f
        drawLine(
            palette.white.copy(alpha = 0.55f),
            Offset(136f, y),
            Offset(if (i == 0) 172f else 166f - i * 6f, y),
            1.4f,
            StrokeCap.Round,
        )
    }
    drawCircle(palette.white.copy(alpha = 0.5f), radius = 4f, center = Offset(178f, 82f))

    // The counter, run down to the floor so it stands on something. She is drawn
    // over it, which puts her on the customers' side of the bar with the tray.
    drawRoundRectPath(Rect(2f, 186f, 198f, GROUND_Y + 8f), radius = 3f, color = wood)
    drawRoundRectPath(Rect(0f, 180f, 200f, 190f), radius = 4f, color = woodDark)
    drawRoundRectPath(Rect(0f, 180f, 200f, 184f), radius = 2f, color = Color(0xFFD8A874))
    for (x in listOf(30f, 96f, 162f)) {
        drawLine(woodDark.copy(alpha = 0.35f), Offset(x, 192f), Offset(x, GROUND_Y + 4f), 1.6f)
    }
}

/** The shop floor: stocked shelving either side of her and a swinging price tag. */
private fun DrawScope.drawShopFloor(palette: PetPalette) {
    val unit = Color(0xFFE4DAF0)
    val shadow = palette.propDark.copy(alpha = 0.25f)
    val goods = listOf(
        Color(0xFFE8577E), Color(0xFF6FC6F5), Color(0xFFF5C542),
        Color(0xFF5FD08A), Color(0xFFB79BE0),
    )

    listOf(0f to 56f, 144f to 200f).forEachIndexed { side, (left, right) ->
        drawRoundRectPath(Rect(left, 46f, right, 210f), radius = 4f, color = unit)
        drawRoundRectPath(Rect(left + 3f, 49f, right - 3f, 207f), radius = 3f, color = shadow)
        for (row in 0..2) {
            val y = 92f + row * 42f
            drawRoundRectPath(Rect(left + 2f, y, right - 2f, y + 5f), radius = 2f, color = unit)
            // Stock: three boxes to a shelf, sizes alternating so the rows are
            // not a grid.
            for (i in 0..2) {
                val bx = left + 8f + i * ((right - left - 20f) / 3f)
                val h = 16f + ((row + i + side) % 3) * 5f
                drawRoundRectPath(
                    Rect(bx, y - h, bx + 12f, y),
                    radius = 1.6f,
                    color = goods[(row * 3 + i + side * 2) % goods.size],
                )
            }
        }
    }

    // A price tag on a string between them.
    drawLine(palette.propDark.copy(alpha = 0.5f), Offset(78f, 0f), Offset(78f, 34f), 1.2f)
    drawRoundRectPath(Rect(66f, 34f, 92f, 52f), radius = 4f, color = palette.accent)
    drawCircle(palette.white, radius = 2f, center = Offset(71f, 39f))
    drawLine(palette.propDark, Offset(74f, 47f), Offset(88f, 39f), 2f, StrokeCap.Round)
}

/** The office: a blinded window and a wall clock. */
private fun DrawScope.drawOffice(palette: PetPalette) {
    val frame = Color(0xFFD3D8E4)
    drawRoundRectPath(Rect(112f, 30f, 194f, 132f), radius = 4f, color = frame)
    drawRoundRectPath(Rect(117f, 35f, 189f, 127f), radius = 2f, color = Color(0xFFBFD9EE))
    // Slats, tighter toward the top so the blind reads as half drawn.
    for (i in 0..9) {
        val y = 39f + i * 9f
        drawLine(frame.copy(alpha = if (i < 4) 0.95f else 0.55f), Offset(117f, y), Offset(189f, y), 4.6f)
    }
    drawLine(frame, Offset(185f, 35f), Offset(185f, 118f), 1.6f)

    drawCircle(Color(0xFFE9E4F2), radius = 15f, center = Offset(30f, 62f))
    drawCircle(palette.propDark.copy(alpha = 0.25f), radius = 15f, center = Offset(30f, 62f))
    drawCircle(Color(0xFFF7F4FC), radius = 12.5f, center = Offset(30f, 62f))
    drawLine(palette.propDark, Offset(30f, 62f), Offset(30f, 54f), 1.8f, StrokeCap.Round)
    drawLine(palette.propDark, Offset(30f, 62f), Offset(37f, 65f), 1.6f, StrokeCap.Round)
}

/** The idol stage: two beams, a truss and a pair of speaker stacks. */
private fun DrawScope.drawStage(palette: PetPalette) {
    listOf(18f to 1f, 182f to -1f).forEach { (originX, dir) ->
        val beam = Path().apply {
            moveTo(originX - dir * 7f, 14f)
            lineTo(originX + dir * 7f, 14f)
            lineTo(originX + dir * 96f, 232f)
            lineTo(originX + dir * 22f, 232f)
            close()
        }
        drawPath(beam, palette.accent.copy(alpha = 0.16f))
        drawCircle(palette.accent.copy(alpha = 0.75f), radius = 7f, center = Offset(originX, 12f))
    }

    // The truss, hung on two drops rather than running off both edges: a bar cut
    // flush at the frame reads as a stray rectangle over the room behind it.
    listOf(52f, 148f).forEach { x ->
        drawLine(palette.propDark, Offset(x, 0f), Offset(x, 6f), 2f)
    }
    drawRoundRectPath(Rect(10f, 4f, 190f, 12f), radius = 4f, color = palette.propDark)
    for (i in 0..8) {
        val x = 14f + i * 20f
        drawLine(palette.prop.copy(alpha = 0.6f), Offset(x, 5f), Offset(x + 12f, 11f), 1.4f)
    }

    // Speaker stacks, standing on the floor at either side of her.
    listOf(2f, 168f).forEach { x ->
        drawRoundRectPath(Rect(x, 168f, x + 30f, 244f), radius = 3f, color = palette.propDark)
        drawCircle(palette.prop, radius = 10f, center = Offset(x + 15f, 190f))
        drawCircle(palette.propDark, radius = 4f, center = Offset(x + 15f, 190f))
        drawCircle(palette.prop, radius = 6f, center = Offset(x + 15f, 218f))
        drawCircle(palette.accent.copy(alpha = 0.8f), radius = 1.8f, center = Offset(x + 25f, 174f))
    }
}

/** School: the board at the front of the class, with its chalk tray. */
private fun DrawScope.drawClassroom(palette: PetPalette) {
    drawRoundRectPath(Rect(14f, 38f, 186f, 136f), radius = 4f, color = Color(0xFFC9A876))
    drawRoundRectPath(Rect(19f, 43f, 181f, 128f), radius = 2f, color = Color(0xFF2F5346))

    // Chalk: a heading rule and two lines of writing, on the half of the board
    // her head does not cover.
    drawLine(palette.white.copy(alpha = 0.5f), Offset(28f, 56f), Offset(66f, 56f), 1.6f, StrokeCap.Round)
    for (i in 0..2) {
        val y = 68f + i * 9f
        drawLine(palette.white.copy(alpha = 0.3f), Offset(28f, y), Offset(58f - i * 6f, y), 1.3f, StrokeCap.Round)
    }
    listOf(146f to 62f, 160f to 76f).forEach { (x, y) ->
        drawLine(palette.white.copy(alpha = 0.35f), Offset(x - 12f, y), Offset(x + 12f, y), 1.3f, StrokeCap.Round)
    }

    // The tray, a stick of chalk and the eraser.
    drawRoundRectPath(Rect(19f, 128f, 181f, 134f), radius = 2f, color = Color(0xFFB0905E))
    drawRoundRectPath(Rect(30f, 124f, 42f, 128f), radius = 1.5f, color = palette.white)
    drawRoundRectPath(Rect(156f, 122f, 172f, 128f), radius = 1.5f, color = palette.prop)
}

/** The online course: a corkboard of notes and a wall calendar at her desk. */
private fun DrawScope.drawStudyNook(palette: PetPalette) {
    drawRoundRectPath(Rect(118f, 34f, 194f, 110f), radius = 4f, color = Color(0xFF8A6A45))
    drawRoundRectPath(Rect(122f, 38f, 190f, 106f), radius = 2f, color = Color(0xFFD7B183))
    val notes = listOf(
        Triple(128f, 44f, Color(0xFFFFE07A)),
        Triple(160f, 48f, Color(0xFFA8E6C4)),
        Triple(132f, 76f, Color(0xFFFFB8CE)),
        Triple(162f, 78f, Color(0xFFB9D8FF)),
    )
    notes.forEach { (x, y, colour) ->
        drawRoundRectPath(Rect(x, y, x + 24f, y + 22f), radius = 1.5f, color = colour)
        drawLine(palette.propDark.copy(alpha = 0.3f), Offset(x + 4f, y + 9f), Offset(x + 19f, y + 9f), 1.1f)
        drawLine(palette.propDark.copy(alpha = 0.3f), Offset(x + 4f, y + 14f), Offset(x + 15f, y + 14f), 1.1f)
        drawCircle(Color(0xFFE8577E), radius = 2.2f, center = Offset(x + 12f, y + 2.5f))
    }

    drawRoundRectPath(Rect(10f, 40f, 54f, 96f), radius = 3f, color = palette.white)
    drawRoundRectPath(Rect(10f, 40f, 54f, 54f), radius = 3f, color = Color(0xFFE8577E))
    for (row in 0..2) {
        for (col in 0..3) {
            drawCircle(
                palette.propDark.copy(alpha = if (row == 1 && col == 2) 0.7f else 0.2f),
                radius = 2f,
                center = Offset(18f + col * 10f, 64f + row * 12f),
            )
        }
    }
}

/** The university: a library wall of books and a tall arched window. */
private fun DrawScope.drawLibrary(palette: PetPalette) {
    val case = Color(0xFF6B4A33)
    val caseDark = Color(0xFF503522)
    val spines = listOf(
        Color(0xFFE8577E), Color(0xFF6FC6F5), Color(0xFFF5C542),
        Color(0xFF5FD08A), Color(0xFFB79BE0), Color(0xFFF2926B),
    )

    drawRoundRectPath(Rect(112f, 20f, 198f, 200f), radius = 3f, color = case)
    drawRoundRectPath(Rect(116f, 24f, 194f, 196f), radius = 2f, color = caseDark)
    for (row in 0..3) {
        val y = 62f + row * 44f
        // Books lean and vary in height, otherwise the shelf reads as a barcode.
        var x = 120f
        var i = row
        while (x < 188f) {
            val w = 5f + (i % 3) * 2f
            val h = 26f + ((i * 5) % 4) * 3f
            drawRoundRectPath(Rect(x, y - h, x + w, y), radius = 1f, color = spines[i % spines.size])
            x += w + 1.5f
            i++
        }
        drawRoundRectPath(Rect(116f, y, 194f, y + 5f), radius = 1.5f, color = case)
    }

    val window = Path().apply {
        moveTo(8f, 150f)
        lineTo(8f, 70f)
        cubicTo(8f, 34f, 68f, 34f, 68f, 70f)
        lineTo(68f, 150f)
        close()
    }
    drawPath(window, Color(0xFFE7E1D2))
    val glass = Path().apply {
        moveTo(13f, 145f)
        lineTo(13f, 71f)
        cubicTo(13f, 41f, 63f, 41f, 63f, 71f)
        lineTo(63f, 145f)
        close()
    }
    drawPath(glass, Color(0xFFCFE4F2))
    drawLine(Color(0xFFE7E1D2), Offset(38f, 42f), Offset(38f, 145f), 3f)
    listOf(74f, 104f).forEach { y ->
        drawLine(Color(0xFFE7E1D2), Offset(13f, y), Offset(63f, y), 3f)
    }
    drawPath(window, palette.propDark.copy(alpha = 0.18f), style = Stroke(width = 2f))
}

// --- props ---------------------------------------------------------------------

private fun DrawScope.drawPillow(palette: PetPalette) {
    // Wide enough to read past the back hair, which is otherwise almost exactly
    // the size of the pillow and hides it completely.
    drawRoundRectPath(
        Rect(CX - 78f, HEAD_CY - 30f, CX + 78f, HEAD_CY + 52f),
        radius = 34f,
        color = palette.collar,
    )
    drawRoundRectPath(
        Rect(CX - 70f, HEAD_CY - 22f, CX + 70f, HEAD_CY + 44f),
        radius = 28f,
        color = palette.white.copy(alpha = 0.65f),
    )
}

/**
 * The desk she works and studies at.
 *
 * It grew legs: as a bare plank it read as a dark bar floating across her
 * thighs rather than as furniture, and every seated scene inherited that. The
 * legs sit at ±70, well outside her own legs at ±14, so nothing of her is
 * hidden by them.
 */
private fun DrawScope.drawDesk(palette: PetPalette) {
    for (side in listOf(-1f, 1f)) {
        drawRoundRectPath(
            Rect(CX + side * 74f - 5f, 206f, CX + side * 74f + 5f, GROUND_Y - 4f),
            radius = 3f,
            color = palette.propDark,
        )
    }
    // Top: the surface, its lit front edge, and a shadow under the overhang.
    drawRoundRectPath(Rect(CX - 80f, 194f, CX + 80f, 210f), radius = 5f, color = palette.propDark)
    drawRoundRectPath(Rect(CX - 80f, 194f, CX + 80f, 199f), radius = 2f, color = palette.prop)
    drawRoundRectPath(Rect(CX - 76f, 210f, CX + 76f, 214f), radius = 2f, color = palette.propDark.copy(alpha = 0.35f))
}

private fun DrawScope.drawPropInFront(prop: Prop, pose: PetPose, palette: PetPalette) {
    when (prop) {
        Prop.BOWL -> drawBowlAndBite(pose, palette)
        Prop.LAPTOP -> drawLaptop(pose, palette)
        Prop.BOOK -> drawBook(pose, palette)
        Prop.CONTROLLER -> drawController(pose, palette)
        Prop.PILLOW -> drawBlanket(pose, palette)
        Prop.TRAY -> drawTray(pose, palette)
        Prop.BAG -> drawShoppingBag(pose, palette)
        Prop.MIC -> drawMicrophone(pose, palette)
        Prop.NOTEBOOK -> drawNotebook(pose, palette)
        Prop.LECTURE -> drawLecture(pose, palette)
        Prop.BOOKSTACK -> {
            drawBookPile(palette)
            drawBook(pose, palette)
        }
    }
}

/** The café tray: balanced on the raised left palm, coffee steaming on top. */
private fun DrawScope.drawTray(pose: PetPose, palette: PetPalette) {
    val hand = classicHandOf(pose, left = true)
    val trayY = hand.y - 4f

    drawOval(palette.propDark, Offset(hand.x - 24f, trayY - 3f), Size(48f, 10f))
    drawOval(palette.collar, Offset(hand.x - 21f, trayY - 4.5f), Size(42f, 8f))

    // The cup, with a saucer and a curl of a handle.
    val cupX = hand.x + 6f
    drawOval(palette.propDark.copy(alpha = 0.5f), Offset(cupX - 10f, trayY - 5f), Size(20f, 5f))
    drawRoundRectPath(Rect(cupX - 7f, trayY - 16f, cupX + 7f, trayY - 3f), radius = 3f, color = palette.white)
    drawArcHandle(Offset(cupX + 7f, trayY - 10f), palette.white)
    drawOval(palette.prop, Offset(cupX - 5f, trayY - 15f), Size(10f, 4f))

    // A macaron for the other side of the tray, because a bare tray is sad.
    drawOval(palette.ribbon.copy(alpha = 0.9f), Offset(hand.x - 18f, trayY - 9f), Size(11f, 7f))
    drawOval(palette.blush, Offset(hand.x - 17f, trayY - 7.5f), Size(9f, 3f))
}

private fun DrawScope.drawArcHandle(at: Offset, color: Color) {
    drawArc(
        color = color,
        startAngle = -70f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(at.x - 2f, at.y - 4f),
        size = Size(8f, 8f),
        style = Stroke(width = 2.2f, cap = StrokeCap.Round),
    )
}

/**
 * The shop job: a paper bag hugged against her, hefted on the beat.
 *
 * Kraft brown rather than the off-white it started as — against white socks and
 * a white collar a pale bag was a hole in the drawing, not an object. It is
 * clasped between both hands instead of swinging from handles, because at this
 * scale a handle is two pixels and a hug reads instantly.
 */
private fun DrawScope.drawShoppingBag(pose: PetPose, palette: PetPalette) {
    val left = classicHandOf(pose, left = true)
    val right = classicHandOf(pose, left = false)
    val c = Offset((left.x + right.x) / 2f, (left.y + right.y) / 2f)
    val top = c.y

    // Handles: two short loops standing up off the rim.
    for (side in listOf(-1f, 1f)) {
        drawArc(
            color = palette.propDark,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(c.x + side * 11f - 7f, top - 11f),
            size = Size(14f, 14f),
            style = Stroke(width = 2.6f, cap = StrokeCap.Round),
        )
    }

    val bag = Path().apply {
        moveTo(c.x - 20f, top)
        lineTo(c.x + 20f, top)
        lineTo(c.x + 24f, top + 36f)
        lineTo(c.x - 24f, top + 36f)
        close()
    }
    drawPath(bag, palette.accent)
    // A folded rim and a shaded side panel so it reads as paper, not a slab.
    drawRoundRectPath(Rect(c.x - 20f, top, c.x + 20f, top + 6f), radius = 1.5f, color = palette.white.copy(alpha = 0.35f))
    val fold = Path().apply {
        moveTo(c.x + 8f, top + 6f)
        lineTo(c.x + 20f, top + 6f)
        lineTo(c.x + 24f, top + 36f)
        lineTo(c.x + 10f, top + 36f)
        close()
    }
    drawPath(fold, palette.propDark.copy(alpha = 0.16f))
    drawHeart(Offset(c.x - 4f, top + 21f), radius = 6.5f, color = palette.ribbon.copy(alpha = 0.9f))
    // Something bought peeking over the rim.
    drawCircle(palette.irisLight, radius = 5.5f, center = Offset(c.x - 9f, top - 3f))
    drawOval(palette.ribbon.copy(alpha = 0.75f), Offset(c.x + 3f, top - 8f), Size(8f, 9f))
}

/**
 * The idol stage: a mic *at her mouth*, sized to read from a widget away.
 *
 * The head is placed off the chin rather than off the hand. The hand is put
 * there by [PetPoseFactory.performing], but the head group also tilts and bobs
 * with the beat, and hanging the mic off the jaw is what keeps it at her mouth
 * through the whole swing instead of drifting toward her shoulder.
 */
private fun DrawScope.drawMicrophone(pose: PetPose, palette: PetPalette) {
    val hand = classicHandOf(pose, left = false)
    val head = Offset(CX + 24f + pose.bodyLean, HEAD_CY + 41f + pose.headBob * 0.6f)

    drawLine(palette.propDark, hand, Offset(head.x + 3f, head.y + 6f), 5f, StrokeCap.Round)
    drawCircle(palette.prop, radius = 8f, center = head)
    // The grille: a lighter cap with a cross-hatch dot.
    drawCircle(palette.irisLight.copy(alpha = 0.8f), radius = 5.5f, center = Offset(head.x - 1f, head.y - 1.5f))
    drawCircle(palette.propDark.copy(alpha = 0.35f), radius = 2f, center = Offset(head.x - 1f, head.y - 1.5f))

    // A spark hopping off the raised hand on the beat.
    if (pose.propProgress > 0.6f) {
        val flare = (pose.propProgress - 0.6f) / 0.4f
        val up = classicHandOf(pose, left = true)
        drawStar(Offset(up.x + 4f, up.y - 10f - flare * 6f), 3f + flare * 3f, palette.accent.copy(alpha = flare))
    }
}

/** School: an open notebook flat on the desk and a pencil that scribbles. */
private fun DrawScope.drawNotebook(pose: PetPose, palette: PetPalette) {
    val top = 176f
    val bottom = 197f
    // Splayed open: the near edge is wider than the spine, which is the whole
    // difference between "an open book" and "a white rectangle".
    val page = Path().apply {
        moveTo(CX - 32f, top)
        lineTo(CX + 32f, top)
        lineTo(CX + 38f, bottom)
        lineTo(CX - 38f, bottom)
        close()
    }
    drawPath(page, palette.white)
    drawPath(page, palette.propDark.copy(alpha = 0.18f), style = Stroke(width = 1.4f))
    drawLine(palette.propDark.copy(alpha = 0.5f), Offset(CX, top + 1f), Offset(CX, bottom - 1f), 2f)

    for (i in 0..3) {
        val y = top + 5f + i * 4.4f
        val spread = 26f + i * 2f
        drawLine(palette.prop.copy(alpha = 0.35f), Offset(CX - spread, y), Offset(CX - 5f, y), 1.5f)
        // The right page fills in as she writes.
        val filled = (pose.propProgress * 4f - i).coerceIn(0f, 1f)
        if (filled > 0f) {
            drawLine(
                palette.iris.copy(alpha = 0.6f),
                Offset(CX + 5f, y),
                Offset(CX + 5f + (spread - 5f) * filled, y),
                1.5f,
            )
        }
    }
    // The pencil rides the writing hand.
    val hand = classicHandOf(pose, left = false)
    val tip = Offset(CX + 14f + pose.propProgress * 9f, top + 12f)
    drawLine(palette.accent, hand, tip, 3.4f, StrokeCap.Round)
    drawCircle(palette.propDark, radius = 1.6f, center = tip)
}

/**
 * The online course: the same desk, a different person at it.
 *
 * The office and the course both sat her at a laptop, so two of the seven jobs
 * were one picture — exactly what the props were added to stop. The screen is
 * tilted toward her with a lecture playing on it rather than facing away, she
 * wears headphones, and her hands are off the keys: she is watching, not
 * typing, and that reads from a widget away.
 */
private fun DrawScope.drawLecture(pose: PetPose, palette: PetPalette) {
    val baseY = 192f
    val pulse = pose.propProgress

    // Screen light spilling out — brighter than the office's, because here the
    // screen is the point.
    drawRoundRectPath(
        Rect(CX - 48f, baseY - 54f, CX + 48f, baseY + 2f),
        radius = 9f,
        color = palette.irisLight.copy(alpha = 0.20f + pulse * 0.10f),
    )

    // The lid, seen from behind but angled: narrower on the far side, so it
    // reads as turned toward her rather than square to the camera.
    val lid = Path().apply {
        moveTo(CX - 34f, baseY - 48f)
        lineTo(CX + 40f, baseY - 44f)
        lineTo(CX + 44f, baseY)
        lineTo(CX - 38f, baseY - 2f)
        close()
    }
    drawPath(lid, palette.prop)
    val inset = Path().apply {
        moveTo(CX - 29f, baseY - 43f)
        lineTo(CX + 35f, baseY - 39f)
        lineTo(CX + 38.5f, baseY - 6f)
        lineTo(CX - 33f, baseY - 8f)
        close()
    }
    drawPath(inset, palette.propDark.copy(alpha = 0.5f))

    // A play triangle on the back of the lid: the one glyph that says "video".
    val play = Path().apply {
        moveTo(CX - 5f, baseY - 32f)
        lineTo(CX + 9f, baseY - 24f)
        lineTo(CX - 5f, baseY - 16f)
        close()
    }
    drawPath(play, palette.accent.copy(alpha = 0.65f + pulse * 0.35f))

    drawRoundRectPath(Rect(CX - 46f, baseY, CX + 46f, baseY + 6f), radius = 3f, color = palette.propDark)

    // A notepad by her elbow — she is taking notes, just not on the laptop.
    drawRoundRectPath(Rect(CX + 48f, baseY - 10f, CX + 74f, baseY), radius = 2f, color = palette.white)
    for (i in 0..1) {
        val y = baseY - 7f + i * 3.5f
        drawLine(palette.prop.copy(alpha = 0.4f), Offset(CX + 52f, y), Offset(CX + 70f, y), 1.2f)
    }

    drawHeadphones(pose, palette)
}

/**
 * Headphones, following the head.
 *
 * Drawn after the head group rather than inside it, so they sit over the hair —
 * and given the same tilt-and-bob transform, because a headband that stayed put
 * while she nodded would look like it was floating beside her.
 */
private fun DrawScope.drawHeadphones(pose: PetPose, palette: PetPalette) {
    withTransform({
        rotate(pose.headTiltDegrees, pivot = Offset(CX, NECK_Y))
        translate(0f, pose.headBob)
    }) {
        drawArc(
            color = palette.propDark,
            startAngle = 190f,
            sweepAngle = 160f,
            useCenter = false,
            topLeft = Offset(CX - 43f, HEAD_CY - 47f),
            size = Size(86f, 84f),
            style = Stroke(width = 6f, cap = StrokeCap.Round),
        )
        for (side in listOf(-1f, 1f)) {
            val x = CX + side * 41f
            drawRoundRectPath(
                Rect(x - 8f, HEAD_CY - 10f, x + 8f, HEAD_CY + 14f),
                radius = 7f,
                color = palette.prop,
            )
            drawRoundRectPath(
                Rect(x - 4.5f, HEAD_CY - 6f, x + 4.5f, HEAD_CY + 10f),
                radius = 4.5f,
                color = palette.irisLight.copy(alpha = 0.75f),
            )
        }
    }
}

/** The university pile: three fat books beside the open one. */
private fun DrawScope.drawBookPile(palette: PetPalette) {
    val deskY = 188f
    val x = CX - 44f
    drawRoundRectPath(Rect(x - 14f, deskY - 6f, x + 14f, deskY), radius = 2f, color = palette.prop)
    drawRoundRectPath(Rect(x - 12f, deskY - 12f, x + 12f, deskY - 6f), radius = 2f, color = palette.ribbon.copy(alpha = 0.8f))
    drawRoundRectPath(Rect(x - 13f, deskY - 18f, x + 13f, deskY - 12f), radius = 2f, color = palette.irisLight)
    drawLine(palette.white.copy(alpha = 0.6f), Offset(x - 10f, deskY - 3f), Offset(x + 10f, deskY - 3f), 1.2f)
}

private fun DrawScope.drawBowlAndBite(pose: PetPose, palette: PetPalette) {
    // The bowl rides in the left hand and the bite in the right, so both follow
    // the arms rather than floating at fixed coordinates.
    val bowl = classicHandOf(pose, left = true).let { Offset(it.x, it.y - 8f) }
    val bowlPath = Path().apply {
        moveTo(bowl.x - 16f, bowl.y - 5f)
        lineTo(bowl.x + 16f, bowl.y - 5f)
        cubicTo(bowl.x + 13f, bowl.y + 12f, bowl.x - 13f, bowl.y + 12f, bowl.x - 16f, bowl.y - 5f)
        close()
    }
    drawPath(bowlPath, palette.white)
    drawOval(palette.ribbon.copy(alpha = 0.9f), Offset(bowl.x - 16f, bowl.y - 9f), Size(32f, 9f))
    drawLine(palette.ribbon, Offset(bowl.x - 14f, bowl.y + 2f), Offset(bowl.x + 14f, bowl.y + 2f), 2.2f)

    val hand = classicHandOf(pose, left = false)
    val tip = Offset(hand.x - 7f, hand.y - 9f)
    drawLine(palette.collar, hand, tip, strokeWidth = 2.6f, cap = StrokeCap.Round)
    drawCircle(palette.accent, radius = 6f, center = tip)
    drawCircle(palette.white.copy(alpha = 0.55f), radius = 2.2f, center = Offset(tip.x - 2f, tip.y - 2f))
}

/**
 * The laptop, mid-keystroke — seen from *our* side.
 *
 * She faces the viewer, so the screen faces her and what we see is the back of
 * the lid. The first version drew the screen contents toward the camera with
 * the keyboard hanging under it, which read as a laptop flipped onto its face.
 * Now the geometry is honest: lid back leaning slightly away from us, screen
 * light spilling around its edges, and her forearms disappearing behind it.
 *
 * Everything here is driven by `propProgress` — the same value that swings her
 * forearms — so the glow pulses on the actual keystrokes and the whole thing
 * still loops in a whole number of typing cycles for the widget's flipbook.
 */
private fun DrawScope.drawLaptop(pose: PetPose, palette: PetPalette) {
    val baseY = 192f
    val stroke = pose.propProgress
    // 1 at either extreme of the swing: that is the instant a key bottoms out.
    val impact = abs(stroke * 2f - 1f)
    val leftDown = stroke > 0.5f

    // Screen light leaking around the lid — the tell that it is on, and the
    // only place the typing can visibly pulse from this side.
    drawRoundRectPath(
        Rect(CX - 45f, baseY - 50f, CX + 45f, baseY + 2f),
        radius = 8f,
        color = palette.irisLight.copy(alpha = 0.16f + impact * 0.10f),
    )

    // The lid back: a hair narrower at the top, because it leans toward her.
    val lid = Path().apply {
        moveTo(CX - 38f, baseY - 46f)
        lineTo(CX + 38f, baseY - 46f)
        lineTo(CX + 42f, baseY)
        lineTo(CX - 42f, baseY)
        close()
    }
    drawPath(lid, palette.prop)
    // An inset panel gives the back some depth without pretending to be a screen.
    val inset = Path().apply {
        moveTo(CX - 33f, baseY - 41f)
        lineTo(CX + 33f, baseY - 41f)
        lineTo(CX + 36.5f, baseY - 5f)
        lineTo(CX - 36.5f, baseY - 5f)
        close()
    }
    drawPath(inset, palette.propDark.copy(alpha = 0.45f))

    // A little glowing heart on the lid, brightening with each keystroke.
    drawHeart(
        Offset(CX, baseY - 24f),
        radius = 7f,
        color = palette.accent.copy(alpha = 0.55f + impact * 0.45f),
    )

    // The base, edge-on: all we see of the keyboard side is its front rim.
    drawRoundRectPath(
        Rect(CX - 46f, baseY, CX + 46f, baseY + 6f),
        radius = 3f,
        color = palette.propDark,
    )

    // The clack: a spark hops off the lid's top edge on the side of whichever
    // hand just landed, standing in for the key we cannot see.
    if (impact > 0.55f) {
        val flare = (impact - 0.55f) / 0.45f
        drawStar(
            center = Offset(CX + (if (leftDown) -26f else 26f), baseY - 50f - flare * 4f),
            radius = 2.5f + flare * 3f,
            color = palette.accent.copy(alpha = flare * 0.8f),
        )
    }
}

private fun DrawScope.drawBook(pose: PetPose, palette: PetPalette) {
    val left = classicHandOf(pose, left = true)
    val right = classicHandOf(pose, left = false)
    val c = Offset((left.x + right.x) / 2f, (left.y + right.y) / 2f - 6f)
    val spread = ((right.x - left.x) / 2f).coerceIn(26f, 44f)

    val leftPage = Path().apply {
        moveTo(c.x, c.y)
        lineTo(c.x - spread, c.y - 7f)
        lineTo(c.x - spread, c.y + 15f)
        lineTo(c.x, c.y + 21f)
        close()
    }
    val rightPage = Path().apply {
        moveTo(c.x, c.y)
        lineTo(c.x + spread, c.y - 7f)
        lineTo(c.x + spread, c.y + 15f)
        lineTo(c.x, c.y + 21f)
        close()
    }
    drawPath(leftPage, palette.white)
    drawPath(rightPage, palette.collar)

    for (i in 0..2) {
        val ly = c.y + 3f + i * 4.5f
        drawLine(palette.prop.copy(alpha = 0.45f), Offset(c.x - spread + 5f, ly), Offset(c.x - 6f, ly + 1f), 1.4f)
        drawLine(palette.prop.copy(alpha = 0.45f), Offset(c.x + 6f, ly + 1f), Offset(c.x + spread - 5f, ly), 1.4f)
    }

    if (pose.propProgress > 0.02f) {
        val turn = pose.propProgress
        val page = Path().apply {
            moveTo(c.x, c.y)
            quadraticTo(c.x + spread * (1f - turn * 2f), c.y - 22f * turn - 7f, c.x - spread * turn, c.y + 13f)
            lineTo(c.x, c.y + 21f)
            close()
        }
        drawPath(page, palette.white.copy(alpha = 0.92f))
    }
    drawLine(palette.propDark, Offset(c.x, c.y), Offset(c.x, c.y + 21f), 1.8f)
}

private fun DrawScope.drawController(pose: PetPose, palette: PetPalette) {
    val left = classicHandOf(pose, left = true)
    val right = classicHandOf(pose, left = false)
    val c = Offset((left.x + right.x) / 2f, (left.y + right.y) / 2f - 2f)

    // Pale body: a dark controller over the dark uniform vanishes.
    drawRoundRectPath(Rect(c.x - 26f, c.y - 9f, c.x + 26f, c.y + 10f), radius = 9f, color = palette.collar)
    drawCircle(palette.collar, radius = 11f, center = left)
    drawCircle(palette.collar, radius = 11f, center = right)
    drawRoundRectPath(Rect(c.x - 9f, c.y - 6f, c.x + 9f, c.y + 3f), radius = 3f, color = palette.prop.copy(alpha = 0.35f))

    drawLine(palette.propDark, Offset(c.x - 21f, c.y), Offset(c.x - 11f, c.y), 3f, StrokeCap.Round)
    drawLine(palette.propDark, Offset(c.x - 16f, c.y - 5f), Offset(c.x - 16f, c.y + 5f), 3f, StrokeCap.Round)
    val lit = pose.propProgress > 0.5f
    drawCircle(if (lit) palette.accent else palette.prop, radius = 3.2f, center = Offset(c.x + 12f, c.y - 3f))
    drawCircle(if (lit) palette.prop else palette.ribbon, radius = 3.2f, center = Offset(c.x + 20f, c.y + 3f))
}

/**
 * The duvet.
 *
 * It reaches the floor and spreads as it goes. Stopping short of her shoes with
 * vertical sides and two big rounded bottom corners made a violet tub with a
 * pair of feet under it rather than something she was sleeping in.
 */
private fun DrawScope.drawBlanket(pose: PetPose, palette: PetPalette) {
    val rise = pose.breath * 1.3f
    val top = 186f + rise
    val blanket = Path().apply {
        moveTo(CX - 54f, top + 20f)
        cubicTo(CX - 56f, top + 2f, CX - 30f, top - 6f, CX, top - 6f)
        cubicTo(CX + 30f, top - 6f, CX + 56f, top + 2f, CX + 54f, top + 20f)
        cubicTo(CX + 60f, 232f, CX + 66f, 246f, CX + 68f, GROUND_Y + 4f)
        lineTo(CX - 68f, GROUND_Y + 4f)
        cubicTo(CX - 66f, 246f, CX - 60f, 232f, CX - 54f, top + 20f)
        close()
    }
    drawPath(blanket, palette.iris)
    // Creases falling from the fold, so the duvet has cloth in it.
    listOf(-34f, -12f, 14f, 36f).forEach { dx ->
        drawLine(
            color = palette.irisDeep.copy(alpha = 0.22f),
            start = Offset(CX + dx, top + 16f),
            end = Offset(CX + dx * 1.35f, GROUND_Y + 2f),
            strokeWidth = 2f,
            cap = StrokeCap.Round,
        )
    }
    // Folded-over sheet along the top edge.
    val fold = Path().apply {
        moveTo(CX - 55f, top + 12f)
        cubicTo(CX - 52f, top - 2f, CX - 28f, top - 8f, CX, top - 8f)
        cubicTo(CX + 28f, top - 8f, CX + 52f, top - 2f, CX + 55f, top + 12f)
        cubicTo(CX + 30f, top + 4f, CX - 30f, top + 4f, CX - 55f, top + 12f)
        close()
    }
    drawPath(fold, palette.collar)
}

// --- particles -------------------------------------------------------------------

private fun DrawScope.drawParticles(kind: ParticleKind, pose: PetPose, palette: PetPalette) {
    val count = when (kind) {
        ParticleKind.SLEEP_Z -> 3
        ParticleKind.SWEAT -> 1
        ParticleKind.CODE -> 6
        ParticleKind.STEAM -> 3
        else -> 4
    }
    // Whole cycles per particle phase, so a looping phase gives a looping
    // stream — a fractional rate would teleport them back once per cycle.
    val cycles = when (kind) {
        ParticleKind.COINS -> 2
        ParticleKind.CODE -> 3
        else -> 1
    }

    repeat(count) { i ->
        val phase = ((pose.particlePhase * cycles) + i.toFloat() / count) % 1f
        val fade = sin(phase * Math.PI).toFloat().coerceIn(0f, 1f)
        if (fade <= 0.02f) return@repeat
        val drift = sin(phase * TWO_PI_F + i * 1.7f) * 8f

        when (kind) {
            ParticleKind.HEARTS -> drawHeart(
                Offset(CX + 44f + drift - i * 5f, 108f - phase * 62f),
                5.5f + fade * 3f,
                palette.ribbon.copy(alpha = fade),
            )
            ParticleKind.SPARKLES -> drawStar(
                Offset(CX - 56f + i * 34f + drift, 92f - phase * 50f),
                3.5f + fade * 4f,
                palette.accent.copy(alpha = fade),
            )
            ParticleKind.SLEEP_Z -> drawZ(
                Offset(CX + 42f + drift, 46f - phase * 40f),
                8f + phase * 7f,
                palette.iris.copy(alpha = fade * 0.9f),
            )
            ParticleKind.SWEAT -> drawSweatDrop(
                Offset(CX + 36f, HEAD_CY - 24f + phase * 8f),
                fade,
                palette,
            )
            ParticleKind.NOTES -> drawStar(
                Offset(CX - 58f + i * 38f + drift, 120f - phase * 66f),
                3.5f + fade * 3f,
                palette.irisLight.copy(alpha = fade),
            )
            ParticleKind.CRUMBS -> drawCircle(
                color = palette.accent.copy(alpha = fade * 0.8f),
                radius = 1.8f + fade,
                center = Offset(CX + 10f + drift, 116f + phase * 20f),
            )
            ParticleKind.COINS -> drawCoin(
                Offset(CX - 50f + i * 33f + drift, 120f - phase * 74f),
                5.5f,
                fade,
                palette,
            )
            // Coffee steam: soft beads rising and swelling off the cup on the
            // tray, anchored to the hand so they follow the sway.
            ParticleKind.STEAM -> {
                val cup = classicHandOf(pose, left = true)
                drawCircle(
                    color = palette.white.copy(alpha = fade * 0.7f),
                    radius = 2f + phase * 3f,
                    center = Offset(cup.x + 6f + drift * 0.4f, cup.y - 22f - phase * 26f),
                )
            }
            // Her output, leaving the screen — scraps of code, and every third
            // one a coin, because the shift pays while it runs.
            //
            // Both climb the narrow clear band immediately outside the laptop.
            // Anything routed up the middle crosses her chin and then her face,
            // and anything sent wider runs straight into a twin-tail; this is
            // the only lane on the canvas that is actually empty.
            ParticleKind.CODE -> {
                val side = if (i % 2 == 0) -1f else 1f
                val at = Offset(
                    CX + side * (16f + phase * 34f) + drift * 0.3f,
                    // A short climb in a narrow band: below it a coin sits on
                    // her fringe looking like a hair clip, above it the widget's
                    // frame crops the top off.
                    38f - phase * 26f,
                )
                if (i % 3 == 2) {
                    drawCoin(at, 6f, fade, palette)
                } else {
                    drawGlyph(i, at, 5.2f + fade * 1.6f, palette.irisLight.copy(alpha = fade * 0.95f))
                }
            }
        }
    }
}

/**
 * A scrap of code: a bracket, a slash, a pair of dots.
 *
 * Deliberately not letters — at this size a glyph is four or five pixels
 * across, and anything with real strokes turns to mush. These read as "text"
 * from the shape alone.
 */
private fun DrawScope.drawGlyph(index: Int, center: Offset, size: Float, color: Color) {
    val s = size
    val width = s * 0.34f
    when (index % 4) {
        // { }
        0 -> {
            drawLine(color, Offset(center.x - s * 0.5f, center.y - s), Offset(center.x - s, center.y), width, StrokeCap.Round)
            drawLine(color, Offset(center.x - s, center.y), Offset(center.x - s * 0.5f, center.y + s), width, StrokeCap.Round)
            drawLine(color, Offset(center.x + s * 0.5f, center.y - s), Offset(center.x + s, center.y), width, StrokeCap.Round)
            drawLine(color, Offset(center.x + s, center.y), Offset(center.x + s * 0.5f, center.y + s), width, StrokeCap.Round)
        }
        // /
        1 -> drawLine(
            color,
            Offset(center.x - s * 0.5f, center.y + s),
            Offset(center.x + s * 0.5f, center.y - s),
            width,
            StrokeCap.Round,
        )
        // < >
        2 -> {
            drawLine(color, Offset(center.x + s * 0.1f, center.y - s * 0.8f), Offset(center.x - s * 0.7f, center.y), width, StrokeCap.Round)
            drawLine(color, Offset(center.x - s * 0.7f, center.y), Offset(center.x + s * 0.1f, center.y + s * 0.8f), width, StrokeCap.Round)
            drawLine(color, Offset(center.x + s * 0.6f, center.y - s * 0.8f), Offset(center.x + s * 1.2f, center.y), width, StrokeCap.Round)
            drawLine(color, Offset(center.x + s * 1.2f, center.y), Offset(center.x + s * 0.6f, center.y + s * 0.8f), width, StrokeCap.Round)
        }
        // ;
        else -> {
            drawCircle(color, radius = width * 0.7f, center = Offset(center.x, center.y - s * 0.35f))
            drawCircle(color, radius = width * 0.7f, center = Offset(center.x, center.y + s * 0.35f))
        }
    }
}

// --- primitives --------------------------------------------------------------------

private fun DrawScope.drawRoundedBar(from: Offset, to: Offset, width: Float, color: Color) {
    drawLine(color, from, to, strokeWidth = width, cap = StrokeCap.Round)
}

private fun DrawScope.drawRoundRectPath(rect: Rect, radius: Float, color: Color) {
    val path = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(radius, radius))) }
    drawPath(path, color)
}

private fun DrawScope.drawHeart(center: Offset, radius: Float, color: Color) {
    val r = radius
    val path = Path().apply {
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
    drawPath(path, color)
}

private fun DrawScope.drawStar(center: Offset, radius: Float, color: Color) {
    // A four-point sparkle: two crossed teardrops read cleaner at small sizes
    // than a real five-pointed star.
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x + radius * 0.18f, center.y - radius * 0.18f, center.x + radius, center.y)
        quadraticTo(center.x + radius * 0.18f, center.y + radius * 0.18f, center.x, center.y + radius)
        quadraticTo(center.x - radius * 0.18f, center.y + radius * 0.18f, center.x - radius, center.y)
        quadraticTo(center.x - radius * 0.18f, center.y - radius * 0.18f, center.x, center.y - radius)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawZ(topLeft: Offset, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(topLeft.x, topLeft.y)
        lineTo(topLeft.x + size, topLeft.y)
        lineTo(topLeft.x, topLeft.y + size)
        lineTo(topLeft.x + size, topLeft.y + size)
    }
    drawPath(path, color, style = Stroke(width = size * 0.22f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawSweatDrop(at: Offset, alpha: Float, palette: PetPalette) {
    val path = Path().apply {
        moveTo(at.x, at.y - 8f)
        cubicTo(at.x + 6f, at.y + 1f, at.x + 7f, at.y + 5f, at.x + 4.5f, at.y + 7f)
        cubicTo(at.x + 1f, at.y + 10f, at.x - 4.5f, at.y + 7f, at.x - 4.5f, at.y + 3f)
        cubicTo(at.x - 4.5f, at.y - 1f, at.x - 2f, at.y - 4f, at.x, at.y - 8f)
        close()
    }
    drawPath(path, Color(0xFF8FD6F7).copy(alpha = alpha * 0.9f))
    drawCircle(palette.white.copy(alpha = alpha * 0.7f), radius = 1.6f, center = Offset(at.x - 1.3f, at.y + 3f))
}

private fun DrawScope.drawCoin(center: Offset, radius: Float, alpha: Float, palette: PetPalette) {
    // Spinning: the coin narrows and widens as it turns.
    val spin = abs(cos(center.x * 0.2f + alpha * 6f)).coerceIn(0.25f, 1f)
    drawOval(
        color = palette.accent.copy(alpha = alpha),
        topLeft = Offset(center.x - radius * spin, center.y - radius),
        size = Size(radius * 2 * spin, radius * 2),
    )
    drawOval(
        color = Color(0xFFFFE9B8).copy(alpha = alpha * 0.8f),
        topLeft = Offset(center.x - radius * spin * 0.55f, center.y - radius * 0.55f),
        size = Size(radius * 1.1f * spin, radius * 1.1f),
    )
}

private fun polar(origin: Offset, angleDegrees: Float, length: Float): Offset {
    val a = angleDegrees * DEG
    return Offset(origin.x + sin(a) * length, origin.y + cos(a) * length)
}
