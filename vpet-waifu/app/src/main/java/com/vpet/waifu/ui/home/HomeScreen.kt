package com.vpet.waifu.ui.home

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.CurrencyYen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpet.waifu.R
import com.vpet.waifu.data.PetPreferences
import com.vpet.waifu.data.PetSettings
import com.vpet.waifu.domain.Dialogue
import com.vpet.waifu.domain.OccupationKind
import com.vpet.waifu.domain.OutcomeQuality
import com.vpet.waifu.domain.PetSnapshot
import com.vpet.waifu.domain.Anniversaries
import com.vpet.waifu.domain.JournalEntry
import com.vpet.waifu.domain.JournalKind
import com.vpet.waifu.domain.PetActivity
import com.vpet.waifu.domain.PetRequest
import com.vpet.waifu.domain.PetSimulation
import com.vpet.waifu.domain.RequestKind
import com.vpet.waifu.domain.BOOST_EFFECTS
import com.vpet.waifu.domain.EffectKind
import com.vpet.waifu.domain.Shop
import com.vpet.waifu.domain.ShopItem
import com.vpet.waifu.domain.Story
import com.vpet.waifu.domain.PetState
import com.vpet.waifu.domain.Progression
import com.vpet.waifu.domain.PetTuning
import com.vpet.waifu.ui.components.ActionButton
import com.vpet.waifu.ui.character.PetPalette
import com.vpet.waifu.ui.character.PetSkin
import com.vpet.waifu.ui.character.headTopPx
import com.vpet.waifu.ui.components.STAGE_FLOOR_INSET
import com.vpet.waifu.ui.components.STAGE_SKY_BAND
import com.vpet.waifu.ui.character.workPropFor
import com.vpet.waifu.ui.components.EffectBar
import com.vpet.waifu.ui.components.EffectChip
import com.vpet.waifu.ui.components.EventCard
import com.vpet.waifu.ui.components.GainPop
import com.vpet.waifu.ui.components.LevelRing
import com.vpet.waifu.ui.components.MoneyPill
import com.vpet.waifu.ui.components.OutlineButton
import com.vpet.waifu.ui.components.IconTile
import com.vpet.waifu.ui.components.PanelCard
import com.vpet.waifu.ui.components.PetStage
import com.vpet.waifu.ui.components.PrimaryButton
import com.vpet.waifu.ui.components.StatBarTrack
import com.vpet.waifu.ui.components.StatRow
import com.vpet.waifu.ui.components.SpeechBubble
import com.vpet.waifu.ui.components.StatusChip
import com.vpet.waifu.ui.dialogueRes
import com.vpet.waifu.ui.formatRemaining
import com.vpet.waifu.ui.occupationIcon
import com.vpet.waifu.ui.chapterTitleRes
import com.vpet.waifu.ui.occupationTint
import com.vpet.waifu.ui.formatMinutes
import com.vpet.waifu.ui.occupationArtRes
import com.vpet.waifu.ui.shopItemArtRes
import com.vpet.waifu.ui.shopItemIcon
import com.vpet.waifu.ui.shopItemNameRes
import com.vpet.waifu.ui.shopItemTint
import com.vpet.waifu.ui.isRealMorning
import com.vpet.waifu.ui.isRealNight
import com.vpet.waifu.ui.occupationNameRes
import com.vpet.waifu.ui.stateLabelRes
import com.vpet.waifu.ui.theme.Accents
import com.vpet.waifu.ui.theme.StatColors
import com.vpet.waifu.ui.theme.Surfaces
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin

/**
 * Her room: the animated character, what she is up to, her stats, and the care
 * actions that do not cost money.
 */
@Composable
fun HomeScreen(
    snapshot: PetSnapshot,
    simulation: PetSimulation,
    tuning: PetTuning,
    nowMillis: Long,
    settings: PetSettings,
    wallet: Int,
    walletSettled: Boolean,
    onOpenSettings: () -> Unit,
    onNameChange: (String) -> Unit,
    onFeed: () -> Unit,
    onPet: () -> Unit,
    onTapPet: () -> Unit,
    onCoinLanded: () -> Unit,
    onExpLanded: () -> Unit,
    onToggleSleep: () -> Unit,
    onCancelOccupation: () -> Unit,
    onDismissEvent: () -> Unit,
    onBuy: (ShopItem) -> Unit,
    onAcknowledgeStory: () -> Unit,
    onAcknowledgeDaily: () -> Unit,
    onSeen: () -> Unit,
    skin: PetSkin = PetSkin.Modern,
    modifier: Modifier = Modifier,
) {
    val state = snapshot.state(nowMillis, tuning)
    val line = remember(snapshot, nowMillis / 5_000L) {
        Dialogue.lineFor(snapshot, nowMillis, tuning)
    }
    // "Доброе утро" beats the ordinary line: the first visit of a morning is a
    // moment, and the room knowing the real clock only matters if she does too.
    val morningGreeting = isRealMorning(nowMillis) &&
        nowMillis - snapshot.lastInteractionAt >= 6L * 60 * 60 * 1000 &&
        snapshot.activity == PetActivity.AWAKE && !snapshot.isSick

    // The clicker: tapping her dips her on her feet, sprays a burst of stars
    // at the finger, and clicks — instantly, on the press, because a clicker
    // that answers late is a clicker that feels broken.
    val scope = rememberCoroutineScope()
    val petScale = remember { Animatable(1f) }
    val bursts = remember { mutableStateListOf<TapBurst>() }
    var burstId by remember { mutableLongStateOf(0L) }

    // Everything the coin flight needs to aim: where the stage and the wallet
    // actually are, in one shared coordinate space.
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    var stageBounds by remember { mutableStateOf(Rect.Zero) }
    var pillCenter by remember { mutableStateOf(Offset.Zero) }
    var ringCenter by remember { mutableStateOf(Offset.Zero) }
    var walletBumps by remember { mutableIntStateOf(0) }
    var ringBumps by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.positionInRoot() },
    ) {
    // Naming lives in a dialog off the header now — and doubles as the
    // onboarding step: a pet without a name asks for one on first visit.
    var naming by rememberSaveable { mutableStateOf(false) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(settings.petName) {
        if (settings.petName.isBlank() && !askedOnce) {
            askedOnce = true
            naming = true
        }
    }
    if (naming) {
        NameDialog(
            current = settings.petName,
            onSave = { onNameChange(it); naming = false },
            onDismiss = { naming = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(
            snapshot = snapshot,
            petName = settings.petName,
            wallet = wallet,
            walletSettled = walletSettled,
            walletBump = walletBumps,
            ringBump = ringBumps,
            onPillPositioned = { pillCenter = it },
            onRingPositioned = { ringCenter = it },
            onRename = { naming = true },
            onOpenSettings = onOpenSettings,
        )

        BoxWithConstraints(
            modifier = Modifier.onGloballyPositioned { stageBounds = it.boundsInRoot() },
        ) {
            // Where this particular character's head ends up, so the bubble can
            // sit just above it instead of at a fixed height that suits one of
            // the three and looks detached on the other two.
            val density = LocalDensity.current
            val bubbleTop = with(density) {
                val boxWidth = maxWidth.toPx()
                val boxHeight = (STAGE_HEIGHT - STAGE_SKY_BAND - STAGE_FLOOR_INSET).toPx()
                val head = skin.headTopPx(boxWidth, boxHeight)
                (STAGE_SKY_BAND.toPx() + head).toDp()
            }

            PetStage(
                state = state,
                height = STAGE_HEIGHT,
                palette = PetPalette.forOutfit(snapshot.outfit, classic = skin == PetSkin.Classic),
                characterScale = petScale.value,
                workProp = workPropFor(snapshot.occupation?.id),
                decor = snapshot.owned,
                night = state == PetState.SLEEPING || isRealNight(nowMillis),
                theme = snapshot.theme,
                skin = skin,
            )
            // The tap layer sits over the room but under the chips and bubble.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(snapshot.acceptsPat) {
                        if (!snapshot.acceptsPat) return@pointerInput
                        detectTapGestures { offset ->
                            onTapPet()
                            bursts += TapBurst(
                                id = burstId++,
                                center = offset,
                                mood = simulation.patMood(snapshot, nowMillis).roundToInt(),
                                exp = simulation.patExp(snapshot, nowMillis),
                            )
                            scope.launch {
                                petScale.snapTo(0.955f)
                                petScale.animateTo(
                                    1f,
                                    spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = 700f,
                                    ),
                                )
                            }
                        }
                    },
            )
            bursts.forEach { burst ->
                key(burst.id) {
                    SparkleBurst(burst = burst, onDone = { bursts.remove(burst) })
                }
            }
            // Bottom-left, over the floor: the one region of the room that
            // never has her or the furniture behind text.
            StatusChip(
                text = stringResource(stateLabelRes(state)),
                dot = statusDot(state),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
            )
            // She talks — in the sky band above her head. The first cut hung
            // the bubble at her eye level and it sat straight across her face.
            SpeechBubble(
                text = if (morningGreeting) {
                    stringResource(pickMorning(nowMillis))
                } else {
                    stringResource(dialogueRes(line))
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    // Anchored to her head, then pulled up by the bubble's own
                    // height so its tail lands just above her rather than
                    // across her face. Floored at 6dp so a tall character
                    // cannot push it off the top of the stage.
                    .padding(
                        // Floored so a tall character cannot push it off the
                        // stage, and capped so a short one cannot drag it down
                        // among the furniture: past a third of the way down it
                        // stops reading as speech and starts reading as a card
                        // lying on the floor.
                        top = (bubbleTop - BUBBLE_LIFT).coerceIn(6.dp, STAGE_HEIGHT * 0.32f),
                        start = 16.dp,
                        end = 16.dp,
                    ),
            )
            // Every minute she is on the clock, what she just earned floats up.
            // Top-right corner: visible, and never over her.
            snapshot.session?.let { session ->
                val isWork = snapshot.occupation?.kind == OccupationKind.WORK
                GainPop(
                    total = if (isWork) session.paidOut else session.paidExp,
                    label = if (isWork) "¥" else "EXP",
                    tint = if (isWork) StatColors.Money else StatColors.Exp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 84.dp, end = 18.dp),
                )
            }
        }

        // What happened while nobody was looking — shown once per return.
        // Today's reward for turning up, and the streak it belongs to.
        if (snapshot.pendingDaily > 0) {
            DailyCard(snapshot, onAcknowledgeDaily)
        }

        AwayRecap(snapshot, settings.lastSeenAt, nowMillis, onSeen)

        // A round number of days together is a small holiday.
        if (Anniversaries.daysTogether(snapshot.bornAt, nowMillis) in Anniversaries.MILESTONES) {
            AnniversaryCard(Anniversaries.daysTogether(snapshot.bornAt, nowMillis))
        }

        // A completed chapter is the best news the screen can carry.
        if (snapshot.storyChapter > snapshot.storySeen) {
            StoryBanner(snapshot, onAcknowledgeStory)
        }

        // Illness outranks everything else on the board: it is the one state
        // that blocks half the game until the player acts.
        if (snapshot.isSick) {
            SickCard(snapshot, onBuy)
        }

        snapshot.request?.takeIf { nowMillis < it.until }?.let { request ->
            RequestCard(request, nowMillis)
        }

        snapshot.event?.takeIf { !it.acknowledged }?.let { event ->
            EventCard(event, onDismissEvent)
        }

        AnimatedVisibility(visible = snapshot.isBusy) {
            SessionCard(snapshot, simulation, nowMillis, onCancelOccupation)
        }

        ActiveBoosts(snapshot, nowMillis)

        StatsCard(snapshot)
        ProgressCard(snapshot, simulation, tuning)
        CareRow(snapshot, simulation, tuning, nowMillis, onFeed, onPet, onToggleSleep)
        Spacer(Modifier.height(4.dp))
    }

    // Wages arrive as actual coins: they pop out of the room and arc up into
    // the wallet, which hops as each one lands.
    val fromHer = {
        Offset(
            stageBounds.left - overlayOrigin.x + stageBounds.width * 0.5f,
            stageBounds.top - overlayOrigin.y + stageBounds.height * 0.62f,
        )
    }
    CoinFlights(
        // The wallet itself, not just the shift's tally: the tip jar pays every
        // three seconds whether or not she is on the clock, and every coin that
        // reaches the wallet should be a coin you watched get there.
        walletTotal = snapshot.progress.money,
        start = fromHer,
        end = { pillCenter - overlayOrigin },
        onArrive = {
            walletBumps++
            onCoinLanded()
        },
        modifier = Modifier.matchParentSize(),
    )
    // And the same for what studying pays in.
    //
    // Without this a study session looked like it paid *money*: the tip jar's
    // coins flew past every few seconds while the EXP it was actually earning
    // crept up a digit at a time inside a ring the size of a thumbnail. Now the
    // thing she is earning is the thing you watch arrive.
    StarFlights(
        expTotal = snapshot.progress.exp,
        start = fromHer,
        end = { ringCenter - overlayOrigin },
        onArrive = {
            ringBumps++
            onExpLanded()
        },
        modifier = Modifier.matchParentSize(),
    )
    }
}

/**
 * The boosts currently running, each with its countdown.
 *
 * They are bought to be felt *right now* — mid-shift, mid-lesson — so the
 * receipt lives on the main screen where the shift is, not back in the shop.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveBoosts(snapshot: PetSnapshot, nowMillis: Long) {
    // Penalties belong here as much as boosts do. The advance's hunger surge
    // and the all-nighter's crash were invisible: her energy would fall off a
    // cliff and the screen that could have explained it showed nothing.
    val running = snapshot.effects.filter { it.isActive(nowMillis) }
    if (running.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        running.forEach { effect ->
            val penalty = effect.kind !in BOOST_EFFECTS
            // Only for the icon and the colour now: the length comes from the
            // record itself, so an earned buff with no item behind it draws a
            // bar like everything else instead of a full one that never moves.
            val item = Shop.ALL.firstOrNull { it.effect == effect.kind }
            val minutesLeft = (((effect.expiresAt - nowMillis) + 59_999L) / 60_000L).toInt()
            EffectBar(
                icon = when {
                    penalty -> Icons.Rounded.WarningAmber
                    item != null -> shopItemIcon(item.id)
                    else -> earnedEffectIcon(effect.kind)
                },
                label = stringResource(effectLabelRes(effect.kind)),
                remaining = formatMinutes(minutesLeft),
                fraction = effect.fractionLeft(nowMillis),
                tint = when {
                    penalty -> Accents.Danger
                    item != null -> shopItemTint(item.id)
                    else -> Accents.Bright
                },
                penalty = penalty,
            )
        }
    }
}

/**
 * What an effect is doing, in the player's words.
 *
 * The item's own name was what these used to say — "Сверхурочный пропуск",
 * which is what you bought, not what is happening. What is happening is that
 * the wages are half again as big, and that is the thing worth reading.
 */
@StringRes
private fun effectLabelRes(kind: EffectKind): Int = when (kind) {
    EffectKind.HASTE -> R.string.effect_running_haste
    EffectKind.OVERTIME -> R.string.effect_running_overtime
    EffectKind.FOCUS -> R.string.effect_running_focus
    EffectKind.SECOND_WIND -> R.string.effect_running_second_wind
    EffectKind.GOOD_VIBES -> R.string.effect_running_good_vibes
    EffectKind.HUNGER_SURGE -> R.string.effect_running_hunger_surge
    EffectKind.EXHAUSTION -> R.string.effect_running_exhaustion
    EffectKind.DISCOUNT -> R.string.effect_running_discount
    EffectKind.STASIS -> R.string.effect_running_stasis
}

/** The two that have no shop item to borrow an icon from. */
private fun earnedEffectIcon(kind: EffectKind): ImageVector = when (kind) {
    EffectKind.DISCOUNT -> Icons.Rounded.Sell
    else -> Icons.Rounded.AcUnit
}

private fun statusDot(state: PetState): Color = when (state) {
    PetState.WORKING, PetState.STUDYING -> StatColors.Exp
    PetState.HUNGRY -> StatColors.Hunger
    PetState.TIRED, PetState.SLEEPING -> StatColors.Energy
    else -> Accents.Bright
}

@Composable
private fun Header(
    snapshot: PetSnapshot,
    petName: String,
    wallet: Int,
    walletSettled: Boolean,
    walletBump: Int,
    ringBump: Int,
    onPillPositioned: (Offset) -> Unit,
    onRingPositioned: (Offset) -> Unit,
    onRename: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LevelRing(
            exp = snapshot.progress.exp,
            bump = ringBump,
            modifier = Modifier.onGloballyPositioned { onRingPositioned(it.boundsInRoot().center) },
        )
        Spacer(Modifier.width(12.dp))
        // Her name is the edit affordance: tap it to rename her.
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onRename),
        ) {
            Text(
                text = petName.ifBlank { stringResource(R.string.name_hint) },
                style = MaterialTheme.typography.titleLarge,
                color = if (petName.isBlank()) Accents.TextMuted else Accents.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.level_label, snapshot.level),
                style = MaterialTheme.typography.bodyMedium,
                color = Accents.TextMuted,
            )
        }
        MoneyPill(
            amount = wallet,
            settled = walletSettled,
            bump = walletBump,
            modifier = Modifier.onGloballyPositioned { onPillPositioned(it.boundsInRoot().center) },
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Surfaces.Tile)
                .border(1.dp, Surfaces.CardBorder, RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.settings_title),
                tint = Accents.TextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Naming her — the dialog behind a tap on her name, and the first-run ask. */
@Composable
private fun NameDialog(
    current: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable(current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surfaces.Card,
        titleContentColor = Accents.Text,
        shape = RoundedCornerShape(24.dp),
        title = { Text(stringResource(R.string.name_title)) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= PetPreferences.MAX_NAME_LENGTH) draft = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.name_hint)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Accents.Text,
                    unfocusedTextColor = Accents.Text,
                    focusedBorderColor = Accents.Primary,
                    unfocusedBorderColor = Surfaces.Divider,
                    cursorColor = Accents.Bright,
                ),
            )
        },
        confirmButton = {
            PrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = { onSave(draft.trim()) },
                enabled = draft.isNotBlank(),
            )
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionCard(
    snapshot: PetSnapshot,
    simulation: PetSimulation,
    nowMillis: Long,
    onCancel: () -> Unit,
) {
    val session = snapshot.session ?: return
    val occupation = snapshot.occupation ?: return

    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(occupationTint(occupation.id).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(occupationArtRes(occupation.id)),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(occupationNameRes(occupation.id)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Accents.Text,
                    )
                    Text(
                        text = stringResource(
                            R.string.session_remaining,
                            formatRemaining(session.remainingMillis(nowMillis)),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Accents.TextMuted,
                    )
                }
                Spacer(Modifier.width(10.dp))
                // Wages land every minute now, so the running total belongs on
                // the card — otherwise the only sign she is being paid is the
                // wallet quietly ticking up somewhere else on the screen.
                EffectChip(
                    icon = if (occupation.kind == OccupationKind.WORK) Icons.Rounded.CurrencyYen else Icons.Rounded.Star,
                    text = if (occupation.kind == OccupationKind.WORK) {
                        "+${session.paidOut} ¥"
                    } else {
                        "+${session.paidExp} EXP"
                    },
                    tint = if (occupation.kind == OccupationKind.WORK) StatColors.Money else StatColors.Exp,
                )
            }
            Spacer(Modifier.height(14.dp))
            StatBarTrack(fraction = session.progress(nowMillis), color = Accents.Bright, height = 7.dp)
            Spacer(Modifier.height(12.dp))
            // Wages are recomputed from her CURRENT mood every simulated
            // minute, so a pat mid-shift is worth real money for every minute
            // that is left. The game never said so.
            val quality = simulation.currentQuality(snapshot)
            val isWork = snapshot.occupation?.kind == OccupationKind.WORK
            // The real number, boosts included. It used to be a fixed label per
            // mood band, so an overtime pass raised the wages in the simulation
            // while this line went on reading exactly as it had before the
            // purchase — the one place that could have shown the boost working
            // was the place saying it was not.
            val total = if (isWork) {
                simulation.payMultiplierNow(snapshot, nowMillis)
            } else {
                simulation.studyMultiplierNow(snapshot, nowMillis)
            }
            val mood = simulation.currentPayMultiplier(snapshot)
            val boosted = total > mood + 0.01f
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                EffectChip(
                    icon = Icons.Rounded.Favorite,
                    text = stringResource(
                        if (isWork) R.string.shift_pay_total else R.string.shift_exp_total,
                        formatMultiplier(total),
                    ),
                    tint = if (boosted) StatColors.Money else payChipTint(quality),
                )
                // Named separately when a boost is in it, so the bigger number
                // has a reason attached rather than being a number that moved.
                if (boosted) {
                    EffectChip(
                        icon = Icons.Rounded.Bolt,
                        text = stringResource(
                            R.string.shift_pay_from_boost,
                            formatMultiplier(mood),
                            formatMultiplier(total / mood),
                        ),
                        tint = Accents.Bright,
                    )
                }
            }
            if (!boosted && quality != OutcomeQuality.GREAT) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.shift_pay_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Accents.TextDim,
                )
            }
            Spacer(Modifier.height(14.dp))
            // Full width and on its own line: beside the title it fought the
            // job name for space and both ended up truncated.
            OutlineButton(
                text = stringResource(R.string.action_call_home),
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Level and passive income, in one compact card.
 *
 * Facts live in chips, not in paragraphs; the mechanics behind them sit behind
 * the "?" so the card itself stays one glance long.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgressCard(snapshot: PetSnapshot, simulation: PetSimulation, tuning: PetTuning) {
    val exp = snapshot.progress.exp
    val (earned, needed) = Progression.levelProgress(exp)
    val maxed = snapshot.level >= Progression.MAX_LEVEL
    var info by rememberSaveable { mutableStateOf(false) }
    if (info) {
        InfoDialog(
            title = stringResource(R.string.progress_title),
            body = stringResource(R.string.progress_info, formatRate(tuning.workExpPerMinute)),
            onDismiss = { info = false },
        )
    }

    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    tint = StatColors.Exp,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.exp_title, snapshot.level),
                    style = MaterialTheme.typography.titleMedium,
                    color = Accents.Text,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (maxed) "" else stringResource(R.string.exp_progress, earned, needed),
                    style = MaterialTheme.typography.labelMedium,
                    color = Accents.TextMuted,
                )
                Spacer(Modifier.width(8.dp))
                InfoButton(onClick = { info = true })
            }
            StatBarTrack(
                fraction = if (maxed) 1f else earned.toFloat() / needed,
                color = StatColors.Exp,
                height = 7.dp,
            )
            Text(
                text = if (maxed) {
                    stringResource(R.string.exp_maxed)
                } else {
                    stringResource(R.string.exp_next_level, needed - earned, snapshot.level + 1)
                },
                style = MaterialTheme.typography.bodySmall,
                color = Accents.TextMuted,
            )

            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.tip_jar_title),
                style = MaterialTheme.typography.titleMedium,
                color = Accents.Text,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                EffectChip(
                    icon = Icons.Rounded.CurrencyYen,
                    text = stringResource(R.string.passive_rate, simulation.passivePerMinute(snapshot)),
                    tint = StatColors.Money,
                )
                EffectChip(
                    icon = Icons.Rounded.Savings,
                    text = stringResource(R.string.passive_cap, simulation.passiveDailyCap(snapshot)),
                    tint = StatColors.Money,
                )
            }
            Text(
                text = stringResource(R.string.passive_paused_busy),
                style = MaterialTheme.typography.bodySmall,
                color = Accents.TextDim,
            )
        }
    }
}

/** "0.5", not "0.5000001" — one decimal, and no trailing ".0". */
private fun formatRate(value: Float): String {
    val rounded = kotlin.math.round(value * 10f) / 10f
    return if (rounded == rounded.toInt().toFloat()) rounded.toInt().toString() else rounded.toString()
}

/** The small "?" that opens a card's detail dialog. */
@Composable
private fun InfoButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(Surfaces.Tile)
            .border(1.dp, Surfaces.CardBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
            contentDescription = null,
            tint = Accents.TextMuted,
            modifier = Modifier.size(15.dp),
        )
    }
}

/** Where the mechanics hide: one dialog per "?", body text only. */
@Composable
private fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surfaces.Card,
        titleContentColor = Accents.Text,
        textContentColor = Accents.TextMuted,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            PrimaryButton(text = stringResource(R.string.action_ok), onClick = onDismiss)
        },
    )
}

@Composable
private fun StatsCard(snapshot: PetSnapshot) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatRow(
                icon = Icons.Rounded.Restaurant,
                label = stringResource(R.string.stat_hunger),
                value = snapshot.stats.hunger,
                color = StatColors.Hunger,
            )
            StatRow(
                icon = Icons.Rounded.Bolt,
                label = stringResource(R.string.stat_energy),
                value = snapshot.stats.energy,
                color = StatColors.Energy,
            )
            StatRow(
                icon = Icons.Rounded.Favorite,
                label = stringResource(R.string.stat_mood),
                value = snapshot.stats.mood,
                color = StatColors.Mood,
            )
        }
    }
}

@Composable
private fun CareRow(
    snapshot: PetSnapshot,
    simulation: PetSimulation,
    tuning: PetTuning,
    nowMillis: Long,
    onFeed: () -> Unit,
    onPet: () -> Unit,
    onToggleSleep: () -> Unit,
) {
    // The buttons used to be the only actions in the game that said nothing
    // about what they were worth, while tapping her directly floated a number.
    var feeds by remember { mutableIntStateOf(0) }
    var pats by remember { mutableIntStateOf(0) }
    val fedGain = tuning.feedHunger.roundToInt()
    val patGain = simulation.patMood(snapshot, nowMillis).roundToInt()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
            ActionButton(
                icon = Icons.Rounded.Restaurant,
                label = stringResource(R.string.action_feed),
                tint = StatColors.Hunger,
                enabled = snapshot.canFeed(tuning),
                onClick = { feeds++; onFeed() },
                modifier = Modifier.fillMaxWidth(),
            )
            ValuePop(trigger = feeds, text = "+$fedGain", tint = StatColors.Hunger)
        }
        ActionButton(
            icon = if (snapshot.isSleeping) Icons.Rounded.WbSunny else Icons.Rounded.Bedtime,
            label = stringResource(
                if (snapshot.isSleeping) R.string.action_wake else R.string.action_sleep,
            ),
            tint = StatColors.Energy,
            enabled = !snapshot.isBusy,
            onClick = onToggleSleep,
            modifier = Modifier.weight(1f),
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
            ActionButton(
                icon = Icons.Rounded.Favorite,
                label = stringResource(R.string.action_pet),
                tint = StatColors.Mood,
                enabled = snapshot.acceptsInteraction,
                onClick = { pats++; onPet() },
                modifier = Modifier.fillMaxWidth(),
            )
            ValuePop(trigger = pats, text = "+$patGain", tint = StatColors.Mood)
        }
    }
}

/** A value floating off a button that just paid out. */
@Composable
private fun ValuePop(trigger: Int, text: String, tint: Color) {
    if (trigger <= 0) return
    val rise = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) { rise.animateTo(1f, tween(900, easing = LinearOutSlowInEasing)) }
    if (rise.value >= 1f) return
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = tint.copy(alpha = 1f - rise.value),
        modifier = Modifier.padding(top = 2.dp).graphicsLayer {
            translationY = -26.dp.toPx() * rise.value
        },
    )
}

/**
 * Turning up today, and the run of days it continues.
 *
 * The cheapest retention mechanic in the genre and the game had none of it:
 * a missed day cost nothing and a kept one gave nothing.
 */
@Composable
private fun DailyCard(snapshot: PetSnapshot, onDismiss: () -> Unit) {
    val comeback = snapshot.journal.lastOrNull()?.kind == JournalKind.COMEBACK
    PanelCard(
        modifier = Modifier.fillMaxWidth(),
        color = StatColors.Money.copy(alpha = 0.10f),
        border = StatColors.Money.copy(alpha = 0.45f),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Savings,
                    contentDescription = null,
                    tint = StatColors.Money,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (comeback) R.string.comeback_title else R.string.daily_title,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = Accents.Text,
                    )
                    Text(
                        text = if (comeback) {
                            stringResource(R.string.comeback_body)
                        } else {
                            stringResource(R.string.daily_streak_reward, snapshot.streakDays, snapshot.pendingDaily)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Accents.TextMuted,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlineButton(
                text = stringResource(R.string.action_nice),
                onClick = onDismiss,
                tint = StatColors.Money,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@StringRes
private fun payChipRes(quality: OutcomeQuality): Int = when (quality) {
    OutcomeQuality.GREAT -> R.string.shift_pay_great
    OutcomeQuality.GOOD -> R.string.shift_pay_good
    OutcomeQuality.POOR -> R.string.shift_pay_poor
    OutcomeQuality.BAD -> R.string.shift_pay_bad
}

private fun payChipTint(quality: OutcomeQuality): Color = when (quality) {
    OutcomeQuality.GREAT -> StatColors.Money
    OutcomeQuality.GOOD -> Accents.TextMuted
    else -> Accents.Danger
}

/** "1.95", "1.5" — no trailing zero on a whole multiplier. */
private fun formatMultiplier(value: Float): String {
    val rounded = kotlin.math.round(value * 100f) / 100f
    return if (rounded % 1f == 0f) rounded.toInt().toString() else "%.2f".format(rounded).trimEnd('0').trimEnd('.')
}

/** The stage's own height, shared with whatever has to line up inside it. */
private val STAGE_HEIGHT = 320.dp

/**
 * How far above her head the bubble hangs.
 *
 * A two-line bubble is about sixty points tall, and its tail wants to be just
 * clear of her hair rather than touching it.
 */
private val BUBBLE_LIFT = 66.dp

/** One tap's worth of stars, at the finger. */
private data class TapBurst(val id: Long, val center: Offset, val mood: Int, val exp: Int)

/**
 * What a tap on her actually looks like.
 *
 * Sparks alone were the wrong answer: "the clicks are useless" was the report,
 * and a shower of neutral glitter says *something happened* without saying
 * *what*. Hearts rise out of the touch point and the mood the pat was worth
 * floats up as a number beside them, so a tap now states its own value.
 */
@Composable
private fun SparkleBurst(burst: TapBurst, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 780, easing = LinearOutSlowInEasing))
        onDone()
    }
    val label = if (burst.mood > 0) "+${burst.mood}" else null
    // Sitting with her while she studies is worth something on its own.
    val expLabel = if (burst.exp > 0) "+${burst.exp} EXP" else null
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.SemiBold,
        color = StatColors.Mood,
    )
    val expStyle = MaterialTheme.typography.labelLarge.copy(
        fontWeight = FontWeight.SemiBold,
        color = StatColors.Exp,
    )

    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        if (p <= 0f || p >= 1f) return@Canvas
        val fade = (1f - p) * (1f - p)

        // Four hearts fanning up and out from the finger.
        repeat(4) { i ->
            val own = ((p - i * 0.10f) / (1f - i * 0.10f)).coerceIn(0f, 1f)
            if (own <= 0f) return@repeat
            val spread = ((burst.id * 37 + i * 53) % 7).toInt() - 3
            val at = burst.center + Offset(
                spread * 9.dp.toPx() * own + sin(own * 6f + i) * 4.dp.toPx(),
                -own * 62.dp.toPx(),
            )
            val grow = if (own < 0.18f) own / 0.18f else 1f - (own - 0.18f) * 0.35f
            val alpha = ((1f - own) * (1f - own)).coerceIn(0f, 1f)
            drawTapHeart(at, 9.dp.toPx() * grow, StatColors.Mood.copy(alpha = alpha * 0.95f))
        }

        // Two sparks for shine — kept, just no longer doing the whole job.
        repeat(2) { i ->
            val angle = (burst.id * 41 + i * 150 + 13).toFloat() * (PI.toFloat() / 180f)
            val reach = 12.dp.toPx() + 40.dp.toPx() * p
            val at = burst.center + Offset(cos(angle) * reach, sin(angle) * reach * 0.8f)
            drawSpark(at, 3.5.dp.toPx(), Accents.Bright.copy(alpha = fade))
        }

        // The number the pat was worth.
        val rise = 20.dp.toPx() + 44.dp.toPx() * p
        if (label != null) {
            drawText(
                textLayoutResult = measurer.measure(label, style),
                color = StatColors.Mood.copy(alpha = fade),
                topLeft = burst.center + Offset(14.dp.toPx(), -rise),
            )
        }
        if (expLabel != null) {
            drawText(
                textLayoutResult = measurer.measure(expLabel, expStyle),
                color = StatColors.Exp.copy(alpha = fade),
                topLeft = burst.center + Offset(14.dp.toPx(), -rise + 20.dp.toPx()),
            )
        }
    }
}

/** A plump heart — the tap's own, drawn a shade rounder than the particles'. */
private fun DrawScope.drawTapHeart(center: Offset, radius: Float, color: Color) {
    if (radius <= 0f) return
    val path = Path().apply {
        moveTo(center.x, center.y + radius * 0.9f)
        cubicTo(
            center.x - radius * 1.7f, center.y - radius * 0.3f,
            center.x - radius * 0.6f, center.y - radius * 1.4f,
            center.x, center.y - radius * 0.35f,
        )
        cubicTo(
            center.x + radius * 0.6f, center.y - radius * 1.4f,
            center.x + radius * 1.7f, center.y - radius * 0.3f,
            center.x, center.y + radius * 0.9f,
        )
        close()
    }
    drawPath(path, color)
}

/** A four-point twinkle — two crossed teardrops read cleaner than a star. */
private fun DrawScope.drawSpark(center: Offset, radius: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x + radius * 0.2f, center.y - radius * 0.2f, center.x + radius, center.y)
        quadraticTo(center.x + radius * 0.2f, center.y + radius * 0.2f, center.x, center.y + radius)
        quadraticTo(center.x - radius * 0.2f, center.y + radius * 0.2f, center.x - radius, center.y)
        quadraticTo(center.x - radius * 0.2f, center.y - radius * 0.2f, center.x, center.y - radius)
        close()
    }
    drawPath(path, color)
}

/**
 * Money, visibly travelling.
 *
 * Watches the wallet; every coin of an increase becomes a little gold disc that
 * arcs from the room up into the pill, staggered so a batch reads as a stream
 * rather than a clump. Capped per batch — a two-hour payout landing at once
 * must not carpet-bomb the screen.
 *
 * Spending is deliberately silent here: the shop has its own sound, and coins
 * flying *out* of the wallet on every purchase would read as a loss animation
 * in a game whose whole feedback vocabulary is "gold moving means good".
 */
@Composable
private fun CoinFlights(
    walletTotal: Int,
    start: () -> Offset,
    end: () -> Offset,
    onArrive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previous = remember { mutableIntStateOf(walletTotal) }
    val coins = remember { mutableStateListOf<FlyingCoin>() }
    var nextCoin by remember { mutableLongStateOf(0L) }

    LaunchedEffect(walletTotal) {
        val delta = walletTotal - previous.intValue
        previous.intValue = walletTotal
        if (delta <= 0) return@LaunchedEffect
        repeat(minOf(delta, 5)) { i -> coins += FlyingCoin(nextCoin++, i * 240L) }
    }

    Box(modifier) {
        coins.forEach { coin ->
            key(coin.id) {
                CoinSprite(
                    coin = coin,
                    start = start,
                    end = end,
                    onDone = {
                        coins.remove(coin)
                        onArrive()
                    },
                )
            }
        }
    }
}

/**
 * EXP, visibly travelling.
 *
 * The coin flight's twin, aimed at the level ring instead of the wallet.
 * Batched harder: a finished study session can land two hundred EXP at once,
 * and two hundred stars is a screen wipe, not a celebration.
 */
@Composable
private fun StarFlights(
    expTotal: Int,
    start: () -> Offset,
    end: () -> Offset,
    onArrive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previous = remember { mutableIntStateOf(expTotal) }
    val stars = remember { mutableStateListOf<FlyingCoin>() }
    var nextStar by remember { mutableLongStateOf(0L) }

    LaunchedEffect(expTotal) {
        val delta = expTotal - previous.intValue
        previous.intValue = expTotal
        if (delta <= 0) return@LaunchedEffect
        // One star per point up to three, then a fixed handful for the rest, so
        // a one-point tick and a whole semester both read at a glance.
        val count = if (delta <= 3) delta else 4 + minOf(2, delta / 60)
        repeat(count) { i -> stars += FlyingCoin(nextStar++, i * 190L) }
    }

    Box(modifier) {
        stars.forEach { star ->
            key(star.id) {
                CoinSprite(
                    coin = star,
                    start = start,
                    end = end,
                    kind = FlightKind.STAR,
                    onDone = {
                        stars.remove(star)
                        onArrive()
                    },
                )
            }
        }
    }
}

private enum class FlightKind { COIN, STAR }

private data class FlyingCoin(val id: Long, val delayMillis: Long)

@Composable
private fun CoinSprite(
    coin: FlyingCoin,
    start: () -> Offset,
    end: () -> Offset,
    onDone: () -> Unit,
    kind: FlightKind = FlightKind.COIN,
) {
    val progress = remember { Animatable(0f) }
    var launched by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(coin.delayMillis)
        launched = true
        progress.animateTo(1f, tween(durationMillis = 640, easing = FastOutSlowInEasing))
        onDone()
    }
    if (!launched) return

    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        val s = start()
        val e = end()
        if (e == Offset.Zero) return@Canvas
        // A quadratic arc bowed upward and sideways, like a tossed coin.
        val lift = (coin.id % 3 - 1) * 30.dp.toPx()
        val control = Offset((s.x + e.x) / 2f + lift, minOf(s.y, e.y) - 90.dp.toPx())
        val inv = 1f - p
        val pos = Offset(
            inv * inv * s.x + 2 * inv * p * control.x + p * p * e.x,
            inv * inv * s.y + 2 * inv * p * control.y + p * p * e.y,
        )
        val r = 7.dp.toPx() * (1f - p * 0.35f)
        when (kind) {
            FlightKind.COIN -> {
                drawCircle(StatColors.Money, radius = r, center = pos)
                drawCircle(Color(0xFFB8860B), radius = r, center = pos, style = Stroke(width = r * 0.22f))
                drawCircle(Color.White.copy(alpha = 0.5f), radius = r * 0.3f, center = pos - Offset(r * 0.3f, r * 0.35f))
            }
            FlightKind.STAR -> {
                drawSpark(pos, r * 1.6f, StatColors.Exp)
                drawSpark(pos, r * 0.7f, Color.White.copy(alpha = 0.85f))
            }
        }
    }
}

/**
 * She is ill. The card explains what happened, what it blocks, and sells the
 * cure on the spot — sending the player to hunt for the medicine through the
 * shop while she stands there shivering would be pure friction.
 */
@Composable
private fun SickCard(snapshot: PetSnapshot, onBuy: (ShopItem) -> Unit) {
    val medicine = Shop.byId(Shop.MEDICINE_ID) ?: return
    PanelCard(
        modifier = Modifier.fillMaxWidth(),
        color = Accents.Danger.copy(alpha = 0.10f),
        border = Accents.Danger.copy(alpha = 0.5f),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.art_medicine),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.sick_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Accents.Text,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = stringResource(R.string.sick_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = Accents.TextMuted,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = stringResource(R.string.sick_buy_medicine, medicine.price),
                onClick = { onBuy(medicine) },
                enabled = snapshot.progress.canAfford(medicine.price),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Her wish, with the thing named, pictured, and the window shown. */
@Composable
private fun RequestCard(request: PetRequest, nowMillis: Long) {
    val (icon, tint, line) = when (request.kind) {
        RequestKind.FOOD -> {
            val name = stringResource(shopItemNameRes(request.itemId ?: ""))
            Triple(
                shopItemIcon(request.itemId ?: ""),
                shopItemTint(request.itemId ?: ""),
                stringResource(R.string.request_food, name),
            )
        }
        RequestKind.GIFT -> {
            val name = stringResource(shopItemNameRes(request.itemId ?: ""))
            Triple(
                shopItemIcon(request.itemId ?: ""),
                shopItemTint(request.itemId ?: ""),
                stringResource(R.string.request_gift, name),
            )
        }
        RequestKind.PLAY -> Triple(
            Icons.Rounded.SportsEsports,
            Color(0xFFF477B8),
            stringResource(R.string.request_play),
        )
    }
    val art = request.itemId?.let { painterResource(shopItemArtRes(it)) }

    PanelCard(
        modifier = Modifier.fillMaxWidth(),
        color = tint.copy(alpha = 0.08f),
        border = tint.copy(alpha = 0.45f),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon = icon, tint = tint, art = art, size = 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.request_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = tint,
                )
                Text(
                    text = line,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Accents.Text,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.request_window,
                        formatRemaining(request.until - nowMillis),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Accents.TextMuted,
                )
            }
        }
    }
}

/** A chapter just completed: the title, the reward, and a proud OK. */
@Composable
private fun StoryBanner(snapshot: PetSnapshot, onAcknowledge: () -> Unit) {
    // The chapter that finished is the one *before* the current pointer.
    val done = Story.CHAPTERS.getOrNull(snapshot.storyChapter - 1) ?: return
    PanelCard(
        modifier = Modifier.fillMaxWidth(),
        color = StatColors.Exp.copy(alpha = 0.10f),
        border = StatColors.Exp.copy(alpha = 0.5f),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.AutoStories,
                    contentDescription = null,
                    tint = StatColors.Exp,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.story_chapter_done),
                        style = MaterialTheme.typography.labelMedium,
                        color = StatColors.Exp,
                    )
                    Text(
                        text = stringResource(chapterTitleRes(done.id)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Accents.Text,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (done.rewardMoney > 0) {
                    EffectChip(
                        icon = Icons.Rounded.CurrencyYen,
                        text = stringResource(R.string.story_reward_money, done.rewardMoney),
                        tint = StatColors.Money,
                    )
                }
                if (done.rewardOutfit != null) {
                    EffectChip(
                        icon = Icons.Rounded.Checkroom,
                        text = stringResource(R.string.story_reward_outfit),
                        tint = Color(0xFFB39CE8),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlineButton(
                text = stringResource(R.string.action_ok),
                onClick = onAcknowledge,
                tint = StatColors.Exp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@StringRes
private fun pickMorning(nowMillis: Long): Int {
    val variants = intArrayOf(R.string.say_morning_0, R.string.say_morning_1, R.string.say_morning_2)
    return variants[((nowMillis / 60_000L) % variants.size).toInt()]
}

/**
 * The diary since the player last looked, shown once.
 *
 * The window is captured on first composition and held: the ticker keeps
 * writing entries while the card is open, and a recap that grows as you read
 * it never lets you finish reading.
 */
@Composable
private fun AwayRecap(
    snapshot: PetSnapshot,
    lastSeenAt: Long,
    nowMillis: Long,
    onSeen: () -> Unit,
) {
    var dismissed by remember { mutableStateOf(false) }
    val since = remember { lastSeenAt }
    val awayLongEnough = remember { since > 0L && nowMillis - since >= 90L * 60 * 1000 }
    val entries = remember { snapshot.journal.filter { it.at > since }.takeLast(8).asReversed() }
    if (dismissed || !awayLongEnough || entries.isEmpty()) return

    PanelCard(
        modifier = Modifier.fillMaxWidth(),
        border = Accents.Primary.copy(alpha = 0.45f),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.AutoStories,
                    contentDescription = null,
                    tint = Accents.Bright,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.recap_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Accents.Text,
                )
            }
            Spacer(Modifier.height(10.dp))
            entries.forEach { entry ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 3.dp),
                ) {
                    val (icon, tint) = journalLook(entry)
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = journalLine(entry),
                        style = MaterialTheme.typography.bodySmall,
                        color = Accents.TextMuted,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlineButton(
                text = stringResource(R.string.action_ok),
                onClick = {
                    dismissed = true
                    onSeen()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun journalLook(entry: JournalEntry) = when (entry.kind) {
    JournalKind.SHIFT_DONE -> Icons.Rounded.CurrencyYen to StatColors.Money
    JournalKind.LESSON_DONE -> Icons.Rounded.Star to StatColors.Exp
    JournalKind.FELL_SICK -> Icons.Rounded.Healing to Accents.Danger
    JournalKind.RECOVERED -> Icons.Rounded.Healing to Color(0xFF54E070)
    JournalKind.WISH_EXPIRED -> Icons.Rounded.Favorite to Accents.TextDim
    JournalKind.EVENT -> Icons.Rounded.AutoAwesome to Accents.Bright
    JournalKind.GOAL_DONE -> Icons.Rounded.Check to StatColors.Money
    JournalKind.ANNIVERSARY -> Icons.Rounded.Cake to StatColors.Mood
    JournalKind.DAILY -> Icons.Rounded.CurrencyYen to StatColors.Money
    JournalKind.COMEBACK -> Icons.Rounded.Favorite to StatColors.Mood
}

@Composable
private fun journalLine(entry: JournalEntry): String = when (entry.kind) {
    JournalKind.SHIFT_DONE -> stringResource(
        R.string.recap_shift,
        stringResource(occupationNameRes(entry.detail ?: "")),
        entry.amount,
    )
    JournalKind.LESSON_DONE -> stringResource(
        R.string.recap_lesson,
        stringResource(occupationNameRes(entry.detail ?: "")),
        entry.amount,
    )
    JournalKind.FELL_SICK -> stringResource(R.string.recap_fell_sick)
    JournalKind.RECOVERED -> stringResource(R.string.recap_recovered)
    JournalKind.WISH_EXPIRED -> stringResource(R.string.recap_wish_expired)
    JournalKind.EVENT -> stringResource(R.string.recap_event)
    JournalKind.GOAL_DONE -> stringResource(R.string.recap_goal, entry.amount)
    JournalKind.ANNIVERSARY -> stringResource(R.string.recap_anniversary, entry.amount)
    JournalKind.DAILY -> stringResource(R.string.daily_reward, entry.amount)
    JournalKind.COMEBACK -> stringResource(R.string.comeback_body)
}

/** A round number of days together — a small holiday, no button needed. */
@Composable
private fun AnniversaryCard(days: Int) {
    PanelCard(
        modifier = Modifier.fillMaxWidth(),
        color = StatColors.Mood.copy(alpha = 0.10f),
        border = StatColors.Mood.copy(alpha = 0.5f),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Cake,
                contentDescription = null,
                tint = StatColors.Mood,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.anniversary_title, days),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Accents.Text,
                )
                Text(
                    text = stringResource(R.string.anniversary_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = Accents.TextMuted,
                )
            }
        }
    }
}

