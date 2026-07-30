package com.vpet.waifu.ui.character

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
const val ART_WIDTH = 200f
const val ART_HEIGHT = 280f

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

fun DrawScope.drawPet(pose: PetPose, palette: PetPalette = PetPalette.Default) {
    val scale = min(size.width / ART_WIDTH, size.height / ART_HEIGHT)
    val offsetX = (size.width - ART_WIDTH * scale) / 2f
    val offsetY = (size.height - ART_HEIGHT * scale) / 2f

    withTransform({
        translate(offsetX, offsetY)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        drawCharacter(pose, palette)
    }
}

private fun DrawScope.drawCharacter(pose: PetPose, palette: PetPalette) {
    drawGroundShadow(pose)

    withTransform({ translate(pose.bodyLean, pose.bodyBounce + pose.droop * 3f) }) {
        // Bedding sits behind absolutely everything, including the hair.
        if (pose.prop == Prop.PILLOW) drawPillow(palette)

        drawBackHair(pose, palette)
        drawTwinTail(pose, palette, mirrored = false)
        drawTwinTail(pose, palette, mirrored = true)

        if (pose.prop == Prop.LAPTOP || pose.prop == Prop.BOOK) drawDesk(palette)

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

private fun DrawScope.drawGroundShadow(pose: PetPose) {
    val lift = max(0f, -pose.bodyBounce)
    val squash = 1f - (lift / 22f).coerceIn(0f, 0.45f)
    drawOval(
        color = Color(0x172B1F45),
        topLeft = Offset(CX - 40f * squash, GROUND_Y),
        size = Size(80f * squash, 13f * squash),
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
        drawOval(
            color = palette.shoe,
            topLeft = Offset(CX + dx + dy - 9.5f, LEG_BOTTOM + 2f),
            size = Size(19f, 11f),
        )
    }
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
internal fun handOf(pose: PetPose, left: Boolean): Offset =
    polar(elbowOf(pose, left), forearmAngle(pose, left), FOREARM)

/**
 * A two-segment arm. The pose gives shoulder and elbow angles where 0 points
 * straight down and positive swings outwards, which is how typing, eating and
 * cheering all reuse one piece of code.
 */
private fun DrawScope.drawArm(pose: PetPose, palette: PetPalette, left: Boolean) {
    val shoulder = shoulderOf(pose, left)
    val elbow = elbowOf(pose, left)
    val hand = handOf(pose, left)

    // Puffed sleeve in a lighter shade so the arm separates from the torso.
    drawCircle(palette.uniformShade, radius = 9.5f, center = shoulder)
    drawRoundedBar(shoulder, elbow, width = 14f, color = palette.uniform)
    drawCircle(palette.collar, radius = 6.6f, center = elbow)
    drawRoundedBar(elbow, hand, width = 11f, color = palette.skin)
    drawCircle(palette.skin, radius = 7f, center = hand)
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

        drawOval(
            color = palette.skin,
            topLeft = Offset(CX - HEAD_RX, HEAD_CY - HEAD_RY),
            size = Size(HEAD_RX * 2, HEAD_RY * 2),
        )
        // Shading where the fringe casts onto the forehead.
        drawOval(
            color = palette.skinShade.copy(alpha = 0.4f),
            topLeft = Offset(CX - 33f, HEAD_CY - 38f),
            size = Size(66f, 22f),
        )

        drawFace(pose, palette)
        drawFringe(pose, palette)
        drawAhoge(pose, palette)
    }
}

private fun DrawScope.drawFringe(pose: PetPose, palette: PetPalette) {
    val sway = pose.hairSwayDegrees * 0.12f

    // Bangs: a smooth cap with two soft points, no cusps.
    val fringe = Path().apply {
        moveTo(CX - 41f, HEAD_CY + 2f)
        cubicTo(CX - 45f, HEAD_CY - 30f, CX - 26f, HEAD_CY - 46f, CX, HEAD_CY - 46f)
        cubicTo(CX + 26f, HEAD_CY - 46f, CX + 45f, HEAD_CY - 30f, CX + 41f, HEAD_CY + 2f)
        cubicTo(CX + 38f, HEAD_CY - 12f, CX + 33f, HEAD_CY - 20f, CX + 20f + sway, HEAD_CY - 8f)
        cubicTo(CX + 13f, HEAD_CY - 22f, CX + 4f, HEAD_CY - 24f, CX - 2f + sway, HEAD_CY - 10f)
        cubicTo(CX - 9f, HEAD_CY - 24f, CX - 25f, HEAD_CY - 22f, CX - 30f + sway, HEAD_CY - 6f)
        cubicTo(CX - 34f, HEAD_CY - 18f, CX - 38f, HEAD_CY - 12f, CX - 41f, HEAD_CY + 2f)
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
    drawBrows(pose, palette)

    val blush = palette.blush.copy(alpha = pose.blushAlpha)
    drawOval(blush, Offset(CX - 39f, eyeY + 11f), Size(20f, 11f))
    drawOval(blush, Offset(CX + 19f, eyeY + 11f), Size(20f, 11f))

    drawMouth(pose, palette, Offset(CX, eyeY + 20f))
}

private fun DrawScope.drawBrows(pose: PetPose, palette: PetPalette) {
    // Thin and soft. The worry axis lifts the inner ends and drops the outer
    // ones, which is the difference between "worried" and "angry".
    // Soft and short, with the inner end sitting slightly higher than the outer
    // one by default — level or inner-low brows read as a glare.
    val y = HEAD_CY - 20f
    val innerDy = -2f - pose.browWorry * 4f
    val outerDy = pose.browWorry * 2.5f
    listOf(-1f, 1f).forEach { side ->
        val path = Path().apply {
            moveTo(CX + side * 25f, y + outerDy)
            quadraticTo(CX + side * 17f, y - 3f + outerDy * 0.3f, CX + side * 10f, y + innerDy)
        }
        drawPath(
            path,
            palette.hairShade.copy(alpha = 0.55f),
            style = Stroke(width = 2.4f, cap = StrokeCap.Round),
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
        val outer = if (mirrored) 1f else -1f
        val cornerOut = Offset(center.x + outer * 15f, center.y - 15f)
        val cornerIn = Offset(center.x - outer * 12f, center.y - 9f)
        val lash = Path().apply {
            moveTo(cornerOut.x, cornerOut.y)
            quadraticTo(center.x + outer * 2f, center.y - 21f, cornerIn.x, cornerIn.y)
            quadraticTo(center.x + outer * 3f, center.y - 14f, cornerOut.x, cornerOut.y)
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

private fun DrawScope.drawDesk(palette: PetPalette) {
    drawRoundRectPath(Rect(CX - 76f, 194f, CX + 76f, 208f), radius = 5f, color = palette.propDark)
    drawRoundRectPath(Rect(CX - 76f, 194f, CX + 76f, 198f), radius = 2f, color = palette.prop)
}

private fun DrawScope.drawPropInFront(prop: Prop, pose: PetPose, palette: PetPalette) {
    when (prop) {
        Prop.BOWL -> drawBowlAndBite(pose, palette)
        Prop.LAPTOP -> drawLaptop(pose, palette)
        Prop.BOOK -> drawBook(pose, palette)
        Prop.CONTROLLER -> drawController(pose, palette)
        Prop.PILLOW -> drawBlanket(pose, palette)
    }
}

private fun DrawScope.drawBowlAndBite(pose: PetPose, palette: PetPalette) {
    // The bowl rides in the left hand and the bite in the right, so both follow
    // the arms rather than floating at fixed coordinates.
    val bowl = handOf(pose, left = true).let { Offset(it.x, it.y - 8f) }
    val bowlPath = Path().apply {
        moveTo(bowl.x - 16f, bowl.y - 5f)
        lineTo(bowl.x + 16f, bowl.y - 5f)
        cubicTo(bowl.x + 13f, bowl.y + 12f, bowl.x - 13f, bowl.y + 12f, bowl.x - 16f, bowl.y - 5f)
        close()
    }
    drawPath(bowlPath, palette.white)
    drawOval(palette.ribbon.copy(alpha = 0.9f), Offset(bowl.x - 16f, bowl.y - 9f), Size(32f, 9f))
    drawLine(palette.ribbon, Offset(bowl.x - 14f, bowl.y + 2f), Offset(bowl.x + 14f, bowl.y + 2f), 2.2f)

    val hand = handOf(pose, left = false)
    val tip = Offset(hand.x - 7f, hand.y - 9f)
    drawLine(palette.collar, hand, tip, strokeWidth = 2.6f, cap = StrokeCap.Round)
    drawCircle(palette.accent, radius = 6f, center = tip)
    drawCircle(palette.white.copy(alpha = 0.55f), radius = 2.2f, center = Offset(tip.x - 2f, tip.y - 2f))
}

private fun DrawScope.drawLaptop(pose: PetPose, palette: PetPalette) {
    val baseY = 192f
    val keyboard = Path().apply {
        moveTo(CX - 42f, baseY)
        lineTo(CX + 42f, baseY)
        lineTo(CX + 50f, baseY + 8f)
        lineTo(CX - 50f, baseY + 8f)
        close()
    }
    drawPath(keyboard, palette.prop)

    // Screen, brightening as she types.
    val glow = 0.6f + pose.propProgress * 0.3f
    drawRoundRectPath(Rect(CX - 40f, baseY - 44f, CX + 40f, baseY), radius = 4f, color = palette.propDark)
    drawRoundRectPath(
        Rect(CX - 35f, baseY - 39f, CX + 35f, baseY - 5f),
        radius = 2.5f,
        color = palette.irisLight.copy(alpha = glow),
    )
    for (i in 0..2) {
        // Scrolls with the typing rather than the clock, so it loops with it.
        val y = baseY - 33f + i * 8f + (pose.propProgress * 8f)
        if (y > baseY - 9f) continue
        val w = 18f + ((i * 11) % 30)
        drawLine(
            color = palette.propDark.copy(alpha = 0.4f),
            start = Offset(CX - 30f, y),
            end = Offset(CX - 30f + w, y),
            strokeWidth = 2.6f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawBook(pose: PetPose, palette: PetPalette) {
    val left = handOf(pose, left = true)
    val right = handOf(pose, left = false)
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
    val left = handOf(pose, left = true)
    val right = handOf(pose, left = false)
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

private fun DrawScope.drawBlanket(pose: PetPose, palette: PetPalette) {
    val rise = pose.breath * 1.3f
    val top = 186f + rise
    val blanket = Path().apply {
        moveTo(CX - 54f, top + 20f)
        cubicTo(CX - 56f, top + 2f, CX - 30f, top - 6f, CX, top - 6f)
        cubicTo(CX + 30f, top - 6f, CX + 56f, top + 2f, CX + 54f, top + 20f)
        cubicTo(CX + 56f, 232f, CX + 40f, 240f, CX + 20f, 240f)
        lineTo(CX - 20f, 240f)
        cubicTo(CX - 40f, 240f, CX - 56f, 232f, CX - 54f, top + 20f)
        close()
    }
    drawPath(blanket, palette.iris.copy(alpha = 0.92f))
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
        else -> 4
    }
    // Whole cycles per particle phase, so a looping phase gives a looping
    // stream — a fractional rate would teleport them back once per cycle.
    val cycles = when (kind) {
        ParticleKind.COINS -> 2
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
