package com.vpet.waifu.ui

import androidx.annotation.StringRes
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vpet.waifu.R
import com.vpet.waifu.domain.ActivityOutcome
import com.vpet.waifu.domain.OccupationKind
import com.vpet.waifu.domain.OutcomeQuality
import com.vpet.waifu.ui.components.EffectChip
import com.vpet.waifu.ui.theme.Accents
import com.vpet.waifu.ui.theme.StatColors
import com.vpet.waifu.ui.theme.Surfaces
import com.vpet.waifu.ui.activities.ActivitiesScreen
import com.vpet.waifu.ui.game.GameScreen
import com.vpet.waifu.ui.home.HomeScreen
import com.vpet.waifu.ui.shop.ShopScreen
import kotlinx.coroutines.delay

private enum class Tab(@StringRes val labelRes: Int, val icon: ImageVector) {
    HOME(R.string.tab_home, Icons.Default.Home),
    ACTIVITIES(R.string.tab_activities, Icons.Default.WorkOutline),
    SHOP(R.string.tab_shop, Icons.Default.Storefront),
    GAME(R.string.tab_game, Icons.Default.SportsEsports),
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

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(500)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Surfaces.Screen,
        bottomBar = { PetNavBar(tab) { tab = it } },
    ) { insets ->
        Box(modifier = Modifier.padding(insets)) {
            when (tab) {
                Tab.HOME -> HomeScreen(
                    snapshot = snapshot,
                    tuning = viewModel.tuning,
                    nowMillis = nowMillis,
                    bubbleEnabled = state.bubbleEnabled,
                    overlayPermissionGranted = overlayPermissionGranted,
                    onGrantOverlayPermission = onGrantOverlayPermission,
                    onBubbleEnabledChange = viewModel::setBubbleEnabled,
                    onFeed = viewModel::feed,
                    onPet = viewModel::pet,
                    onToggleSleep = viewModel::toggleSleep,
                    onCancelOccupation = viewModel::cancelOccupation,
                )
                Tab.ACTIVITIES -> ActivitiesScreen(
                    snapshot = snapshot,
                    simulation = viewModel.simulation,
                    tuning = viewModel.tuning,
                    nowMillis = nowMillis,
                    onStart = viewModel::startOccupation,
                    onCancel = viewModel::cancelOccupation,
                )
                Tab.SHOP -> ShopScreen(snapshot = snapshot, onBuy = viewModel::buy)
                Tab.GAME -> GameScreen(
                    snapshot = snapshot,
                    nowMillis = nowMillis,
                    onStart = viewModel::startPlaying,
                    onFinish = viewModel::finishPlaying,
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
    val tint = if (selected) Accents.Text else Accents.TextDim
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Accents.Primary.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width = 22.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) Accents.Bright else Color.Transparent),
        )
        Spacer(Modifier.height(6.dp))
        Icon(entry.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp))
        Text(
            text = stringResource(entry.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}

/** The "she's back from her shift" card. Shown once, then acknowledged away. */
@Composable
private fun OutcomeDialog(outcome: ActivityOutcome, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        },
        icon = {
            Text(
                text = occupationEmoji(outcome.occupationId),
                style = MaterialTheme.typography.headlineLarge,
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
                        EffectChip(emoji = "💰", text = "+${outcome.money}", tint = StatColors.Money)
                    }
                    if (outcome.exp > 0) {
                        EffectChip(emoji = "⭐", text = "+${outcome.exp}", tint = StatColors.Exp)
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}
