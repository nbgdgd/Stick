package com.stick.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stick.app.ui.navigation.Routes
import com.stick.app.ui.navigation.TopLevelDestination
import com.stick.app.ui.screen.catalog.CatalogScreen
import com.stick.app.ui.screen.editor.EditorScreen
import com.stick.app.ui.screen.export.ExportScreen
import com.stick.app.ui.screen.importer.ImportScreen
import com.stick.app.ui.screen.library.LibraryScreen
import com.stick.app.ui.screen.settings.SettingsScreen
import com.stick.app.ui.screen.viewer.ViewerScreen

/**
 * Root composable: a Material 3 [Scaffold] with a bottom navigation bar over a
 * [NavHost]. Detail screens (viewer/editor/export) are pushed on top and hide the
 * bar. A shared TikTok link deep-links straight to the import tab.
 */
@Composable
fun StickApp(initialSharedLink: String? = null) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val startDestination = if (initialSharedLink != null) Routes.IMPORT else Routes.LIBRARY
    val showBottomBar = TopLevelDestination.entries.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { dest ->
                        val selected = backStackEntry?.destination?.hierarchy
                            ?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(stringResource(dest.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onOpenSticker = { navController.navigate(Routes.viewer(it)) },
                )
            }
            composable(Routes.IMPORT) {
                ImportScreen(
                    initialLink = initialSharedLink,
                    onOpenSticker = { navController.navigate(Routes.viewer(it)) },
                )
            }
            composable(Routes.CATALOG) {
                CatalogScreen(
                    onOpenSticker = { navController.navigate(Routes.viewer(it)) },
                )
            }
            composable(Routes.SETTINGS) { SettingsScreen() }

            composable(
                Routes.VIEWER,
                arguments = listOf(navArgument("stickerId") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("stickerId").orEmpty()
                ViewerScreen(
                    stickerId = id,
                    onEdit = { navController.navigate(Routes.editor(id)) },
                    onExport = { navController.navigate(Routes.export(id)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Routes.EDITOR,
                arguments = listOf(navArgument("stickerId") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("stickerId").orEmpty()
                EditorScreen(
                    stickerId = id,
                    onExport = { navController.navigate(Routes.export(id)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Routes.EXPORT,
                arguments = listOf(navArgument("stickerId") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("stickerId").orEmpty()
                ExportScreen(
                    stickerId = id,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
