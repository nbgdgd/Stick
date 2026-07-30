package com.vpet.waifu.ui.game

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpet.waifu.R
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.PetState
import com.vpet.waifu.domain.MiniGame
import com.vpet.waifu.ui.character.AnimatedPet
import com.vpet.waifu.ui.character.workPropFor
import com.vpet.waifu.ui.components.EffectChip
import com.vpet.waifu.ui.components.OutlineButton
import com.vpet.waifu.ui.components.PanelCard
import com.vpet.waifu.ui.components.PrimaryButton
import com.vpet.waifu.ui.components.ScreenTitle
import com.vpet.waifu.ui.components.StatBarTrack
import com.vpet.waifu.ui.theme.Accents
import com.vpet.waifu.ui.theme.StatColors
import com.vpet.waifu.ui.theme.Surfaces
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

private const val TARGET_SIZE_DP = 56
private const val TARGET_LIFETIME_MILLIS = 1_100L
private const val SPAWN_INTERVAL_MILLIS = 520L
private const val FRAME_MILLIS = 50L

/** What a target looks like: an icon and the colour it glows. */
private enum class TargetKind(val tint: Color) {
    HEART(Color(0xFFB388FF)),
    STAR(Color(0xFFFFC46B)),
    COOKIE(Color(0xFFE8A87C)),
    FLOWER(Color(0xFFF48FB1)),
    GIFT(Color(0xFFFF80AB)),
    ;

    val icon: ImageVector
        get() = when (this) {
            HEART -> Icons.Rounded.Favorite
            STAR -> Icons.Rounded.Star
            COOKIE -> Icons.Rounded.Cookie
            FLOWER -> Icons.Rounded.LocalFlorist
            GIFT -> Icons.Rounded.Redeem
        }
}

private data class Target(val id: Long, val xFraction: Float, val yFraction: Float, val kind: TargetKind)

/**
 * The arcade.
 *
 * Three games rather than one, chosen so that being good at one says nothing
 * about being good at the next: catching things where they appear is aim,
 * pressing as the rings land is timing, and repeating the order is memory. The
 * screen is a picker plus whichever board is selected; the round machinery —
 * clock, score, settling up — is shared, because every way out of a round has
 * to settle it, including the tab being switched away mid-game.
 */
@Composable
fun GameScreen(
    snapshot: PetSnapshot,
    nowMillis: Long,
    onStart: () -> Unit,
    onFinish: (Int, MiniGame) -> Unit,
    modifier: Modifier = Modifier,
    onScored: () -> Unit = {},
    onMiss: () -> Unit = {},
) {
    var game by rememberSaveable { mutableStateOf(MiniGame.CATCH) }
    var running by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }
    var secondsLeft by remember { mutableIntStateOf(game.durationSeconds) }
    var lastScore by remember { mutableStateOf<Int?>(null) }
    var lastGame by remember { mutableStateOf(game) }
    var seed by remember { mutableLongStateOf(0L) }
    val targets: SnapshotStateList<Target> = remember { emptyList<Target>().toMutableStateList() }

    // One coroutine owns the whole round: clock, spawns, and settling up.
    LaunchedEffect(running, game) {
        if (!running) return@LaunchedEffect
        score = 0
        targets.clear()
        secondsLeft = game.durationSeconds
        onStart()

        val random = Random(seed)
        var elapsed = 0L
        var nextSpawn = 0L
        var nextId = 0L
        try {
            while (elapsed < game.durationSeconds * 1000L) {
                if (game == MiniGame.CATCH && elapsed >= nextSpawn) {
                    targets += Target(
                        id = nextId++,
                        xFraction = 0.08f + random.nextFloat() * 0.84f,
                        yFraction = 0.08f + random.nextFloat() * 0.78f,
                        kind = TargetKind.entries[random.nextInt(TargetKind.entries.size)],
                    )
                    nextSpawn = elapsed + SPAWN_INTERVAL_MILLIS
                }
                delay(FRAME_MILLIS)
                elapsed += FRAME_MILLIS
                // Targets live a fixed time; ids are monotonic so the oldest expire first.
                val expiredBefore = nextId - (TARGET_LIFETIME_MILLIS / SPAWN_INTERVAL_MILLIS).toLong() - 1
                targets.removeAll { it.id <= expiredBefore }
                secondsLeft = (game.durationSeconds - elapsed / 1000L).toInt().coerceAtLeast(0)
            }
        } finally {
            // Every way out of the round settles it here — the clock running
            // down, the stop button, and, crucially, this coroutine being
            // cancelled because the tab was switched away mid-game. Without
            // that last case she stays PLAYING forever and every other action
            // in the app is locked out behind it.
            targets.clear()
            lastScore = score
            lastGame = game
            onFinish(score, game)
            running = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle(stringResource(R.string.tab_game)) {
            EffectChip(
                icon = Icons.Rounded.Favorite,
                text = "${snapshot.stats.mood.roundToInt()}",
                tint = StatColors.Mood,
            )
        }

        GameHeader(
            running = running,
            game = game,
            score = score,
            secondsLeft = secondsLeft,
            lastScore = lastScore,
            lastGame = lastGame,
            onStop = { running = false },
        )

        // The picker disappears during a round: three tappable cards over a
        // live game are three ways to abandon it by accident.
        AnimatedVisibility(visible = !running, enter = fadeIn(), exit = fadeOut()) {
            GamePicker(selected = game, onSelect = { game = it })
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Accents.Deep.copy(alpha = 0.35f), Surfaces.Screen),
                    ),
                )
                .border(1.dp, Surfaces.CardBorder, RoundedCornerShape(24.dp)),
        ) {
            val boardWidth = maxWidth
            val boardHeight = maxHeight

            // She is on stage for the two games that leave room for her. The
            // memory pads need the whole board, and cropping her to a sliver
            // behind them looked like a rendering fault rather than a choice.
            if (!(running && game == MiniGame.MEMORY)) {
                AnimatedPet(
                    state = if (running) PetState.PLAYING else snapshot.state(nowMillis),
                    workProp = workPropFor(snapshot.occupation?.id),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .height(boardHeight * 0.55f)
                        .fillMaxWidth(),
                )
            }

            if (running) {
                when (game) {
                    MiniGame.CATCH -> targets.forEach { target ->
                        key(target.id) {
                            TargetBubble(
                                target = target,
                                offsetX = boardWidth * target.xFraction - (TARGET_SIZE_DP / 2).dp,
                                offsetY = boardHeight * target.yFraction - (TARGET_SIZE_DP / 2).dp,
                                onTap = {
                                    score++
                                    onScored()
                                    targets.remove(target)
                                },
                            )
                        }
                    }

                    MiniGame.RHYTHM -> RhythmBoard(
                        running = true,
                        seed = seed,
                        durationSeconds = game.durationSeconds,
                        onScore = { score += it },
                        onHit = { if (it == Judgement.MISS) onMiss() else onScored() },
                        modifier = Modifier.fillMaxSize(),
                    )

                    MiniGame.MEMORY -> MemoryBoard(
                        running = true,
                        seed = seed,
                        onScore = { score += it },
                        onCorrect = onScored,
                        onWrong = onMiss,
                        onOutOfLives = { running = false },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            // Top of the board, not the centre: she stands in the middle, and
            // the centred button sat straight across her face with the "she is
            // busy" line under it on her chin.
            StartOverlay(
                visible = !running,
                canPlay = snapshot.acceptsInteraction,
                played = lastScore != null,
                onStart = {
                    seed = nowMillis
                    running = true
                },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 26.dp),
            )
        }
    }
}

/** Three cards; the selected one is lit. */
@Composable
private fun GamePicker(selected: MiniGame, onSelect: (MiniGame) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MiniGame.entries.forEach { entry ->
            val chosen = entry == selected
            val tint = entry.tint()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(tint.copy(alpha = if (chosen) 0.18f else 0.06f))
                    .border(
                        width = if (chosen) 1.5.dp else 1.dp,
                        color = tint.copy(alpha = if (chosen) 0.7f else 0.2f),
                        shape = RoundedCornerShape(18.dp),
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(entry) },
                    )
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = entry.icon(),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = stringResource(entry.titleRes()),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                    color = if (chosen) Accents.Text else Accents.TextDim,
                )
            }
        }
    }
}

private fun MiniGame.tint(): Color = when (this) {
    MiniGame.CATCH -> Color(0xFFF477B8)
    MiniGame.RHYTHM -> Color(0xFF7FD1E8)
    MiniGame.MEMORY -> Color(0xFFF0C860)
}

private fun MiniGame.icon(): ImageVector = when (this) {
    MiniGame.CATCH -> Icons.Rounded.Favorite
    MiniGame.RHYTHM -> Icons.Rounded.MusicNote
    MiniGame.MEMORY -> Icons.Rounded.Psychology
}

@StringRes
private fun MiniGame.titleRes(): Int = when (this) {
    MiniGame.CATCH -> R.string.game_catch
    MiniGame.RHYTHM -> R.string.game_rhythm
    MiniGame.MEMORY -> R.string.game_memory
}

@StringRes
private fun MiniGame.hintRes(): Int = when (this) {
    MiniGame.CATCH -> R.string.game_hint
    MiniGame.RHYTHM -> R.string.game_rhythm_hint
    MiniGame.MEMORY -> R.string.game_memory_hint
}

/**
 * The row above the board.
 *
 * It swaps between the live scoreboard and what the last round paid. The height
 * is fixed so the board underneath does not jump when a round starts, and it
 * lives in its own composable so `AnimatedVisibility` resolves to the plain
 * overload rather than the `ColumnScope` one.
 */
@Composable
private fun GameHeader(
    running: Boolean,
    game: MiniGame,
    score: Int,
    secondsLeft: Int,
    lastScore: Int?,
    lastGame: MiniGame,
    onStop: () -> Unit,
) {
    // A minimum rather than a fixed height: the idle hint runs to three lines
    // in Russian and a fixed 78dp cropped the last one mid-glyph.
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 78.dp)) {
        AnimatedVisibility(
            visible = running,
            enter = fadeIn() + scaleIn(initialScale = 0.94f),
            exit = fadeOut() + scaleOut(targetScale = 0.94f),
        ) {
            RoundHeader(game = game, score = score, secondsLeft = secondsLeft, onStop = onStop)
        }
        AnimatedVisibility(visible = !running, enter = fadeIn(), exit = fadeOut()) {
            IdleHeader(game = game, lastScore = lastScore, lastGame = lastGame)
        }
    }
}

/** The "start a round" call to action floating over the board. */
@Composable
private fun StartOverlay(
    visible: Boolean,
    canPlay: Boolean,
    played: Boolean,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton(
                text = stringResource(if (played) R.string.game_again else R.string.game_start),
                onClick = onStart,
                enabled = canPlay,
            )
            if (!canPlay) {
                Text(
                    text = stringResource(R.string.game_busy),
                    style = MaterialTheme.typography.bodySmall,
                    color = StatColors.Hunger,
                )
            }
        }
    }
}

/** The live scoreboard: countdown bar, score, and a way out of the round. */
@Composable
private fun RoundHeader(game: MiniGame, score: Int, secondsLeft: Int, onStop: () -> Unit) {
    // Animated so the bar drains smoothly instead of stepping once a second.
    val fraction by animateFloatAsState(
        targetValue = secondsLeft.toFloat() / game.durationSeconds,
        animationSpec = tween(durationMillis = 1_000),
        label = "round-timer",
    )
    // The score pops each time it changes — the tap needs to feel like it landed.
    var bumped by remember { mutableStateOf(false) }
    LaunchedEffect(score) {
        bumped = true
        delay(90)
        bumped = false
    }
    val pop by animateFloatAsState(
        targetValue = if (bumped) 1.22f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "score-pop",
    )

    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$score",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Accents.Text,
                        modifier = Modifier.scale(pop),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.game_seconds_left, secondsLeft),
                        style = MaterialTheme.typography.labelLarge,
                        color = Accents.TextDim,
                    )
                }
                Spacer(Modifier.height(8.dp))
                StatBarTrack(fraction, Accents.Bright, height = 6.dp)
            }
            Spacer(Modifier.width(12.dp))
            OutlineButton(
                text = stringResource(R.string.game_stop),
                onClick = onStop,
                tint = StatColors.Hunger,
            )
        }
    }
}

/** Between rounds: what the last one paid, or nothing at all before the first. */
@Composable
private fun IdleHeader(game: MiniGame, lastScore: Int?, lastGame: MiniGame) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.SportsEsports,
                contentDescription = null,
                tint = Accents.Bright,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            if (lastScore == null) {
                Text(
                    text = stringResource(game.hintRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Accents.TextDim,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.game_result, lastScore),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Accents.Text,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EffectChip(
                            icon = Icons.Rounded.Favorite,
                            text = "+${lastGame.moodGain(lastScore).roundToInt()}",
                            tint = StatColors.Mood,
                        )
                        EffectChip(
                            icon = Icons.Rounded.Paid,
                            text = "+${lastGame.coins(lastScore)}",
                            tint = StatColors.Money,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetBubble(
    target: Target,
    offsetX: Dp,
    offsetY: Dp,
    onTap: () -> Unit,
) {
    // Pop in rather than appear: a target that materialises at full size is
    // easy to miss at the edge of vision. It also breathes and fades towards
    // the end of its life, which is the only warning that it is about to go.
    var appeared by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.3f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "target-pop",
    )
    val fade by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "target-fade",
    )
    LaunchedEffect(Unit) { appeared = true }

    Box(
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .size(TARGET_SIZE_DP.dp)
            .scale(scale)
            .alpha(fade)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(target.kind.tint.copy(alpha = 0.30f), Accents.Deep.copy(alpha = 0.75f)),
                ),
            )
            .border(1.dp, target.kind.tint.copy(alpha = 0.7f), CircleShape)
            // No ripple: it lags behind a target that vanishes on the same tap.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = target.kind.icon,
            contentDescription = null,
            tint = target.kind.tint,
            modifier = Modifier.size(28.dp),
        )
    }
}
