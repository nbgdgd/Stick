package com.vpet.waifu.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.rounded.CurrencyYen
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vpet.waifu.R
import com.vpet.waifu.domain.ActivityOutcome
import com.vpet.waifu.domain.BOOST_EFFECTS
import com.vpet.waifu.feedback.MusicTrack
import com.vpet.waifu.domain.OccupationKind
import com.vpet.waifu.domain.OutcomeQuality
import com.vpet.waifu.ui.components.EffectChip
import com.vpet.waifu.ui.occupationArtRes
import com.vpet.waifu.ui.components.HeartLayer
import com.vpet.waifu.ui.character.PetSkin
import com.vpet.waifu.ui.character.SpritePacks
import com.vpet.waifu.ui.components.LocalRefusal
import com.vpet.waifu.ui.bondNameRes
import com.vpet.waifu.ui.components.PrimaryButton
import com.vpet.waifu.ui.components.rememberHeartTapState
import com.vpet.waifu.ui.theme.Accents
import com.vpet.waifu.ui.theme.StatColors
import com.vpet.waifu.ui.theme.Surfaces
import com.vpet.waifu.ui.activities.ActivitiesScreen
import com.vpet.waifu.ui.game.GameScreen
import com.vpet.waifu.ui.home.HomeScreen
import com.vpet.waifu.ui.profile.ProfileScreen
import com.vpet.waifu.ui.settings.SettingsScreen
import com.vpet.waifu.ui.shop.ShopScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

/**
 * How often the shared clock moves when nothing is counting down.
 *
 * Five seconds because that is already the granularity of the coarsest thing
 * that reads it — the dialogue line is remembered on `nowMillis / 5000`, so a
 * faster tick could not change what it says anyway.
 */
private const val IDLE_TICK_MILLIS = 5_000L

private enum class Tab(@StringRes val labelRes: Int, val icon: ImageVector) {
    HOME(R.string.tab_home, Icons.Rounded.Home),
    ACTIVITIES(R.string.tab_activities, Icons.Rounded.Work),
    SHOP(R.string.tab_shop, Icons.Rounded.Storefront),
    GAME(R.string.tab_game, Icons.Rounded.SportsEsports),
    HER(R.string.tab_her, Icons.Rounded.Favorite),
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
    val milestone by viewModel.milestone.collectAsStateWithLifecycle()

    // Decoding a sheet is a couple of megabytes of RGBA, so it happens once
    // per selected pack and is remembered for as long as the choice stands.
    val context = LocalContext.current
    val skin = remember(state.settings.petSkin) {
        PetSkin.of(state.settings.petSkin, SpritePacks.load(context, state.settings.petSkin))
    }
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    // The wallet's count-up lives here, once, so every money pill in the app
    // shows the identical digits in the same frame. Each pill animating its
    // own copy is how three screens once showed three different balances.
    val walletShown by animateIntAsState(
        targetValue = snapshot.progress.money,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "wallet",
    )
    val walletSettled = walletShown == snapshot.progress.money

    // This clock is handed to every tab by value, so each move of it recomposes
    // the whole visible screen — thirty-odd read sites on Home alone. At one
    // second flat that is a full recomposition every second forever, which is
    // felt as a hitch once a second whatever the panel's refresh rate.
    //
    // Only three things actually need second resolution, and all three are
    // countdowns that are usually not running: a shift, a wish with a deadline,
    // and a boost with time left on it. Everything else that reads the clock is
    // coarser than that by a wide margin — the dialogue line is keyed to five
    // seconds, morning and night to the hour, the anniversary to the day. So
    // the clock ticks fast only while something is actually counting down.
    val counting = snapshot.isBusy ||
        snapshot.request != null ||
        snapshot.effects.any { it.kind in BOOST_EFFECTS && it.isActive(System.currentTimeMillis()) }
    val tickMillis = if (counting) 1_000L else IDLE_TICK_MILLIS

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(tickMillis) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(tickMillis)
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

    CompositionLocalProvider(LocalRefusal provides viewModel::refused) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Surfaces.Screen,
        bottomBar = {
            PetNavBar(
                selected = tab,
                // acceptsPat, not acceptsInteraction: cheering her on through
                // a shift is allowed everywhere else a tap lands, so the tabs
                // must not be the one place that stays mute.
                patting = snapshot.acceptsPat,
                onSelect = { tab = it },
                onCategoryTap = viewModel::categoryTap,
                onQuietTap = viewModel::uiTap,
            )
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
                    simulation = viewModel.simulation,
                    tuning = viewModel.tuning,
                    nowMillis = nowMillis,
                    settings = state.settings,
                    wallet = walletShown,
                    walletSettled = walletSettled,
                    onOpenSettings = { showSettings = true },
                    onNameChange = viewModel::setPetName,
                    onFeed = viewModel::feed,
                    onPet = viewModel::pet,
                    onTapPet = viewModel::tap,
                    onCoinLanded = viewModel::coinLanded,
                    onExpLanded = viewModel::expLanded,
                    onToggleSleep = viewModel::toggleSleep,
                    onCancelOccupation = viewModel::cancelOccupation,
                    onDismissEvent = viewModel::acknowledgeEvent,
                    onBuy = viewModel::buy,
                    onAcknowledgeStory = viewModel::acknowledgeStory,
                    onAcknowledgeDaily = viewModel::acknowledgeDaily,
                    skin = skin,
                    onSeen = viewModel::markSeen,
                )
                Tab.ACTIVITIES -> ActivitiesScreen(
                    snapshot = snapshot,
                    simulation = viewModel.simulation,
                    tuning = viewModel.tuning,
                    nowMillis = nowMillis,
                    wallet = walletShown,
                    walletSettled = walletSettled,
                    onStart = viewModel::startOccupation,
                    onCancel = viewModel::cancelOccupation,
                    onCategoryTap = viewModel::categoryTap,
                )
                Tab.SHOP -> ShopScreen(
                    snapshot = snapshot,
                    nowMillis = nowMillis,
                    wallet = walletShown,
                    walletSettled = walletSettled,
                    onBuy = viewModel::buy,
                    onBuyUpgrade = viewModel::buyUpgrade,
                    onWear = viewModel::wear,
                    onApplyTheme = viewModel::applyTheme,
                    onCategoryTap = viewModel::categoryTap,
                )
                Tab.GAME -> GameScreen(
                    snapshot = snapshot,
                    nowMillis = nowMillis,
                    onStart = viewModel::startPlaying,
                    onFinish = viewModel::finishPlaying,
                    onScored = viewModel::scored,
                    onMiss = viewModel::missed,
                    onRecord = viewModel::recordSet,
                    skin = skin,
                )
                Tab.HER -> ProfileScreen(
                    snapshot = snapshot,
                    petName = state.settings.petName,
                    nowMillis = nowMillis,
                    onChooseFocus = viewModel::chooseFocus,
                    onWear = viewModel::wear,
                    onCategoryTap = viewModel::categoryTap,
                )
            }
        }

        snapshot.lastOutcome?.let { outcome ->
            OutcomeDialog(outcome, viewModel::acknowledgeOutcome)
        }

        // The two moments the game never celebrated. A level-up used to be a
        // bar quietly draining back to empty.
        milestone?.let { reached ->
            MilestoneDialog(reached, viewModel::clearMilestone)
        }

        // Settings are a place you go, not a card at the bottom of Home.
        if (showSettings) {
            SettingsScreen(
                settings = state.settings,
                overlayPermissionGranted = overlayPermissionGranted,
                onGrantOverlayPermission = onGrantOverlayPermission,
                onBubbleEnabledChange = viewModel::setBubbleEnabled,
                onSoundChange = viewModel::setSoundEnabled,
                onMusicChange = viewModel::setMusicEnabled,
                onHapticsChange = viewModel::setHapticsEnabled,
                onNotificationsChange = viewModel::setNotificationsEnabled,
                onNameChange = viewModel::setPetName,
                onSkinChange = viewModel::setPetSkin,
                onExportSave = viewModel::exportSave,
                onImportSave = viewModel::importSave,
                onBack = { showSettings = false },
            )
        }
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
private fun PetNavBar(
    selected: Tab,
    patting: Boolean,
    onSelect: (Tab) -> Unit,
    onCategoryTap: () -> Unit,
    onQuietTap: () -> Unit,
) {
    // The bar is a category picker, so tapping it is also a pat: hearts come
    // off the tab you pressed, wherever in the bar it is.
    val hearts = rememberHeartTapState()
    // The heart layer and the tabs live in different boxes, so both are
    // measured against the window and differenced — anything else puts the
    // hearts a padding's worth off the tab that was actually pressed.
    var barOrigin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .navigationBarsPadding()
            .onGloballyPositioned { barOrigin = it.positionInRoot() },
    ) {
        Surface(
            color = Surfaces.Card,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Surfaces.CardBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Tab.entries.forEach { entry ->
                    var centre by remember { mutableStateOf(Offset.Zero) }
                    NavItem(
                        entry = entry,
                        selected = entry == selected,
                        onClick = {
                            onSelect(entry)
                            // The pat also *is* the click sound, so the two
                            // paths never double up: hearts and the pop while
                            // she can be petted, a plain click while she sleeps.
                            if (patting) {
                                onCategoryTap()
                                hearts.pop(centre)
                            } else {
                                onQuietTap()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { c ->
                                val b = c.boundsInRoot()
                                centre = Offset(b.center.x, b.top) - barOrigin
                            },
                    )
                }
            }
        }
        HeartLayer(hearts)
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
        shape = RoundedCornerShape(24.dp),
        confirmButton = {
            PrimaryButton(text = stringResource(R.string.action_ok), onClick = onDismiss)
        },
        icon = {
            Image(
                painter = painterResource(occupationArtRes(outcome.occupationId)),
                contentDescription = null,
                modifier = Modifier.size(44.dp).scale(pop),
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
                        EffectChip(icon = Icons.Rounded.CurrencyYen, text = "+${outcome.money} ¥", tint = StatColors.Money)
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

/**
 * Reaching a new level, or a new step of the bond.
 *
 * Deliberately a dialog rather than a toast: these are the two numbers the
 * whole game is built to raise, and they happened invisibly until now.
 */
@Composable
private fun MilestoneDialog(milestone: Milestone, onDismiss: () -> Unit) {
    var landed by remember { mutableStateOf(false) }
    val pop by animateFloatAsState(
        targetValue = if (landed) 1f else 0.3f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "milestone-pop",
    )
    LaunchedEffect(Unit) { landed = true }

    val level = milestone.kind == Milestone.Kind.LEVEL
    val tint = if (level) StatColors.Exp else StatColors.Mood
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surfaces.Card,
        titleContentColor = Accents.Text,
        textContentColor = Accents.TextMuted,
        shape = RoundedCornerShape(24.dp),
        confirmButton = {
            PrimaryButton(text = stringResource(R.string.action_nice), onClick = onDismiss)
        },
        icon = {
            Icon(
                imageVector = if (level) Icons.Rounded.Star else Icons.Rounded.Favorite,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(44.dp).scale(pop),
            )
        },
        title = {
            Text(stringResource(if (level) R.string.levelup_title else R.string.bond_levelup_title))
        },
        text = {
            Text(
                text = if (level) {
                    stringResource(R.string.levelup_body, milestone.value)
                } else {
                    stringResource(R.string.bond_levelup_body, stringResource(bondNameRes(milestone.value)))
                },
                style = MaterialTheme.typography.titleMedium,
                color = tint,
            )
        },
    )
}
