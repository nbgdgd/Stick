package com.vpet.waifu.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vpet.waifu.R
import com.vpet.waifu.domain.ActivityOutcome
import com.vpet.waifu.feedback.MusicTrack
import com.vpet.waifu.domain.OccupationKind
import com.vpet.waifu.domain.OutcomeQuality
import com.vpet.waifu.ui.components.EffectChip
import com.vpet.waifu.ui.occupationIcon
import com.vpet.waifu.ui.components.PrimaryButton
import com.vpet.waifu.ui.theme.Accents
import com.vpet.waifu.ui.theme.StatColors
import com.vpet.waifu.ui.theme.Surfaces
import com.vpet.waifu.ui.activities.ActivitiesScreen
import com.vpet.waifu.ui.game.GameScreen
import com.vpet.waifu.ui.home.HomeScreen
import com.vpet.waifu.ui.shop.ShopScreen
import kotlinx.coroutines.delay

private enum class Tab(@StringRes val labelRes: Int, val icon: ImageVector) {
    HOME(R.string.tab_home, Icons.Rounded.Home),
    ACTIVITIES(R.string.tab_activities, Icons.Rounded.Work),
    SHOP(R.string.tab_shop, Icons.Rounded.Storefront),
    GAME(R.string.tab_game, Icons.Rounded.SportsEsports),
}

/**
 * The app shell.
 *
 * A single ticking clock is hoisted here and handed to every tab, so the
 * countdown on the work card, the emote timeout on the character and the
 * "remaining" label all move together instead of each running their own timer.
 */
@Composable
fun VPetApp(
    state: PetUiState,
    viewModel: PetViewModel,
    overlayPermissionGranted: Boolean,
    onGrantOverlayPermission: () -> Unit,
) {
    val snapshot = state.snapshot ?: return
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }

    // Once a second, not twice: this drives every countdown and emote timeout in
    // the app, and a change here recomposes the whole visible tab. The shortest
    // thing it has to expire is a three-and-a-half-second emote.
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000)
        }
    }

    // The soundtrack follows the screen: the shop and the arcade have their
    // own atmosphere, everywhere else the room plays whatever fits her state.
    val musicTrack = when (tab) {
        Tab.SHOP -> MusicTrack.SHOP
        Tab.GAME -> MusicTrack.GAME
        else -> MusicTrack.forState(snapshot.state(nowMillis, viewModel.tuning))
    }
    LaunchedEffect(musicTrack) { viewModel.setMusicScene(musicTrack) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Surfaces.Screen,
        bottomBar = {
            PetNavBar(tab) {
                if (it != tab) viewModel.uiTap()
                tab = it
            }
        },
    ) { insets ->
        // Tabs slide in the direction they sit in the bar, so the four screens
        // feel like places rather than a single view swapping its contents.
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.padding(insets),
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val offset = if (forward) 1 else -1
                (
                    slideInHorizontally(tween(180)) { width -> offset * width / 5 } +
                        fadeIn(tween(140))
                    ) togetherWith (
                    slideOutHorizontally(tween(180)) { width -> -offset * width / 5 } +
                        fadeOut(tween(110))
                    )
            },
            label = "tab",
        ) { current ->
            when (current) {
                Tab.HOME -> HomeScreen(
                    snapshot = snapshot,
                    tuning = viewModel.tuning,
                    nowMillis = nowMillis,
                    settings = state.settings,
                    overlayPermissionGranted = overlayPermissionGranted,
                    onGrantOverlayPermission = onGrantOverlayPermission,
                    onBubbleEnabledChange = viewModel::setBubbleEnabled,
                    onSoundChange = viewModel::setSoundEnabled,
                    onMusicChange = viewModel::setMusicEnabled,
                    onHapticsChange = viewModel::setHapticsEnabled,
                    onNotificationsChange = viewModel::setNotificationsEnabled,
                    onNameChange = viewModel::setPetName,
                    onFeed = viewModel::feed,
                    onPet = viewModel::pet,
                    onTapPet = viewModel::tap,
                    onCoinLanded = viewModel::coinLanded,
                    onToggleSleep = viewModel::toggleSleep,
                    onCancelOccupation = viewModel::cancelOccupation,
                    onDismissEvent = viewModel::acknowledgeEvent,
                )
                Tab.ACTIVITIES -> ActivitiesScreen(
                    snapshot = snapshot,
                    simulation = viewModel.simulation,
                    tuning = viewModel.tuning,
                    nowMillis = nowMillis,
                    onStart = viewModel::startOccupation,
                    onCancel = viewModel::cancelOccupation,
                )
                Tab.SHOP -> ShopScreen(
                    snapshot = snapshot,
                    onBuy = viewModel::buy,
                    onBuyUpgrade = viewModel::buyUpgrade,
                    onWear = viewModel::wear,
                )
                Tab.GAME -> GameScreen(
                    snapshot = snapshot,
                    nowMillis = nowMillis,
                    onStart = viewModel::startPlaying,
                    onFinish = viewModel::finishPlaying,
                    onScored = viewModel::scored,
                )
            }
        }

        snapshot.lastOutcome?.let { outcome ->
            OutcomeDialog(outcome, viewModel::acknowledgeOutcome)
        }
    }
}

/**
 * The bottom bar.
 *
 * Hand-rolled rather than a `NavigationBar`: the design puts each tab in its
 * own rounded well with a marker above the selected one, which the Material
 * component's indicator does not express.
 */
@Composable
private fun PetNavBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .navigationBarsPadding(),
    ) {
        Surface(
            color = Surfaces.Card,
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(1.dp, Surfaces.CardBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Tab.entries.forEach { entry ->
                    NavItem(
                        entry = entry,
                        selected = entry == selected,
                        onClick = { onSelect(entry) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    entry: Tab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Everything that distinguishes the selected tab is animated, so the
    // marker grows and the well lights up instead of the whole bar snapping.
    val tint by animateColorAsState(
        targetValue = if (selected) Accents.Text else Accents.TextDim,
        label = "nav-tint",
    )
    val well by animateColorAsState(
        targetValue = if (selected) Accents.Primary.copy(alpha = 0.16f) else Color.Transparent,
        label = "nav-well",
    )
    val markerWidth by animateDpAsState(
        targetValue = if (selected) 22.dp else 0.dp,
        animationSpec = tween(150),
        label = "nav-marker",
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(well)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width = markerWidth, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Accents.Bright),
        )
        Spacer(Modifier.height(6.dp))
        Icon(entry.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp))
        Text(
            text = stringResource(entry.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
        )
    }
}

/** The "she's back from her shift" card. Shown once, then acknowledged away. */
@Composable
private fun OutcomeDialog(outcome: ActivityOutcome, onDismiss: () -> Unit) {
    // The emoji lands a beat after the dialog does, which is the whole reward
    // moment — everything else on this card is a number.
    var landed by remember { mutableStateOf(false) }
    val pop by animateFloatAsState(
        targetValue = if (landed) 1f else 0.4f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "outcome-pop",
    )
    LaunchedEffect(Unit) { landed = true }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surfaces.Card,
        titleContentColor = Accents.Text,
        textContentColor = Accents.TextMuted,
        shape = RoundedCornerShape(26.dp),
        confirmButton = {
            PrimaryButton(text = stringResource(R.string.action_ok), onClick = onDismiss)
        },
        icon = {
            Icon(
                imageVector = occupationIcon(outcome.occupationId),
                contentDescription = null,
                tint = Accents.Bright,
                modifier = Modifier.size(40.dp).scale(pop),
            )
        },
        title = {
            Text(
                text = stringResource(
                    if (outcome.cancelled) R.string.outcome_title_early else R.string.outcome_title,
                    stringResource(occupationNameRes(outcome.occupationId)),
                ),
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(qualityLabelRes(outcome.quality)),
                    color = qualityColor(outcome.quality),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (outcome.money > 0) {
                        EffectChip(icon = Icons.Rounded.Paid, text = "+${outcome.money}", tint = StatColors.Money)
                    }
                    if (outcome.exp > 0) {
                        EffectChip(icon = Icons.Rounded.Star, text = "+${outcome.exp}", tint = StatColors.Exp)
                    }
                }
                if (outcome.quality == OutcomeQuality.BAD) {
                    Text(
                        text = stringResource(
                            if (outcome.kind == OccupationKind.STUDY) {
                                R.string.outcome_hint_study
                            } else {
                                R.string.outcome_hint_work
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Accents.TextMuted,
                    )
                }
            }
        },
    )
}
