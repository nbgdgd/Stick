package com.trialtracker.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.trialtracker.app.data.model.DealUi
import com.trialtracker.app.ui.screens.AppsScreen
import com.trialtracker.app.ui.screens.BackHeader
import com.trialtracker.app.ui.screens.CategoriesScreen
import com.trialtracker.app.ui.screens.DealListScreen
import com.trialtracker.app.ui.screens.DealSheet
import com.trialtracker.app.ui.screens.HomeScreen
import com.trialtracker.app.ui.screens.NotificationsScreen
import com.trialtracker.app.ui.screens.SearchScreen
import com.trialtracker.app.ui.screens.SettingsScreen
import com.trialtracker.app.ui.theme.TT

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Главная", Icons.Rounded.Home),
    CATEGORIES("categories", "Категории", Icons.Rounded.GridView),
    NOTIFICATIONS("notifications", "Уведомления", Icons.Rounded.Notifications),
    SETTINGS("settings", "Настройки", Icons.Rounded.Settings),
}

@Composable
fun AppShell(state: UiState, viewModel: AppViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
    var selectedDeal by remember { mutableStateOf<DealUi?>(null) }
    var query by remember { mutableStateOf("") }

    // Keep the sheet in sync with repository updates (e.g. favourite toggles).
    val liveDeal = selectedDeal?.let { current ->
        state.deals.firstOrNull { it.deal.id == current.deal.id } ?: current
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val statusBar = WindowInsets.statusBars.asPaddingValues()
    val navBar = WindowInsets.navigationBars.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current

    val contentPadding = PaddingValues(
        start = navBar.calculateStartPadding(layoutDirection),
        end = navBar.calculateEndPadding(layoutDirection),
        top = statusBar.calculateTopPadding() + 8.dp,
        bottom = navBar.calculateBottomPadding() + 104.dp,
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(TT.Background),
    ) {
        NavHost(
            navController = navController,
            startDestination = Tab.HOME.route,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Tab.HOME.route) {
                HomeScreen(
                    state = state,
                    onOpenCategory = { navController.navigate("category/${it.key}") },
                    onOpenSearch = { navController.navigate("search") },
                    onOpenDeal = { selectedDeal = it },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onRefresh = viewModel::refresh,
                    contentPadding = contentPadding,
                )
            }
            composable(Tab.CATEGORIES.route) {
                CategoriesScreen(
                    state = state,
                    onOpenCategory = { navController.navigate("category/${it.key}") },
                    onOpenApps = { navController.navigate("apps") },
                    contentPadding = contentPadding,
                )
            }
            composable(Tab.NOTIFICATIONS.route) {
                NotificationsScreen(
                    state = state,
                    onOpenDeal = { selectedDeal = it },
                    contentPadding = contentPadding,
                )
            }
            composable(Tab.SETTINGS.route) {
                SettingsScreen(
                    state = state,
                    sources = viewModel.sourceStatuses(),
                    onNameChange = viewModel::setUserName,
                    onShowSystemAppsChange = viewModel::setShowSystemApps,
                    onNotificationsChange = viewModel::setNotificationsEnabled,
                    onAutoVerifyChange = viewModel::setAutoVerifyTrials,
                    onIntervalChange = viewModel::setCheckInterval,
                    contentPadding = contentPadding,
                )
            }
            composable("category/{key}") { entry ->
                val category = Category.fromKey(entry.arguments?.getString("key"))
                Column {
                    Spacer(Modifier.height(statusBar.calculateTopPadding() + 8.dp))
                    BackHeader(category.title, onBack = { navController.popBackStack() })
                    DealListScreen(
                        state = state,
                        category = category,
                        onOpenDeal = { selectedDeal = it },
                        onToggleFavorite = viewModel::toggleFavorite,
                        contentPadding = PaddingValues(
                            top = 8.dp,
                            bottom = contentPadding.calculateBottomPadding(),
                        ),
                    )
                }
            }
            composable("apps") {
                Column {
                    Spacer(Modifier.height(statusBar.calculateTopPadding() + 8.dp))
                    BackHeader("Все приложения", onBack = { navController.popBackStack() })
                    AppsScreen(
                        state = state,
                        onOpenDeal = { selectedDeal = it },
                        contentPadding = PaddingValues(
                            top = 8.dp,
                            bottom = contentPadding.calculateBottomPadding(),
                        ),
                    )
                }
            }
            composable("search") {
                Column {
                    Spacer(Modifier.height(statusBar.calculateTopPadding() + 8.dp))
                    BackHeader("Поиск", onBack = { navController.popBackStack() })
                    SearchScreen(
                        state = state,
                        query = query,
                        onQueryChange = { query = it },
                        onOpenDeal = { selectedDeal = it },
                        onToggleFavorite = viewModel::toggleFavorite,
                        contentPadding = PaddingValues(
                            top = 8.dp,
                            bottom = contentPadding.calculateBottomPadding(),
                        ),
                    )
                }
            }
        }

        BottomBar(
            currentRoute = currentRoute,
            hasUnreadBadge = state.deals.any { it.installed },
            onSelect = { tab ->
                navController.navigate(tab.route) {
                    popUpTo(Tab.HOME.route) { inclusive = tab == Tab.HOME }
                    launchSingleTop = true
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = navBar.calculateBottomPadding()),
        )

        state.message?.let { message ->
            LaunchedEffect(message) {
                kotlinx.coroutines.delay(4000)
                viewModel.dismissMessage()
            }
            Snackbar(
                containerColor = TT.SurfaceHigh,
                contentColor = TT.TextPrimary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = navBar.calculateBottomPadding() + 96.dp, start = 16.dp, end = 16.dp),
            ) { Text(message, style = MaterialTheme.typography.bodySmall) }
        }
    }

    liveDeal?.let { deal ->
        DealSheet(
            deal = deal,
            onDismiss = { selectedDeal = null },
            onToggleFavorite = { viewModel.toggleFavorite(deal) },
            onOpenLink = { link ->
                if (link.isNotBlank()) {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(link))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun BottomBar(
    currentRoute: String?,
    hasUnreadBadge: Boolean,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .background(TT.Surface)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { tab ->
            val selected = currentRoute == tab.route ||
                (currentRoute?.startsWith("category/") == true && tab == Tab.CATEGORIES)
            val alpha by animateFloatAsState(if (selected) 1f else 0.55f, label = "tab")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(tab) }
                    .padding(horizontal = 10.dp),
            ) {
                Box {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (selected) TT.Accent else TT.TextSecondary.copy(alpha = alpha),
                        modifier = Modifier.size(23.dp),
                    )
                    if (tab == Tab.NOTIFICATIONS && hasUnreadBadge) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(TT.Accent),
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = tab.label,
                    color = if (selected) TT.Accent else TT.TextSecondary,
                    fontSize = 10.5.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
