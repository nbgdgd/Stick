package com.stick.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** Type-safe route constants for the whole app. */
object Routes {
    const val LIBRARY = "library"
    const val IMPORT = "import"
    const val CATALOG = "catalog"
    const val SETTINGS = "settings"

    const val VIEWER = "viewer/{stickerId}"
    const val EDITOR = "editor/{stickerId}"
    const val EXPORT = "export/{stickerId}"

    fun viewer(stickerId: String) = "viewer/$stickerId"
    fun editor(stickerId: String) = "editor/$stickerId"
    fun export(stickerId: String) = "export/$stickerId"
}

/** Bottom-navigation top-level destinations, in display order. */
enum class TopLevelDestination(
    val route: String,
    val icon: ImageVector,
    val labelRes: Int,
) {
    LIBRARY(Routes.LIBRARY, Icons.Outlined.GridView, com.stick.app.R.string.nav_library),
    IMPORT(Routes.IMPORT, Icons.Outlined.Add, com.stick.app.R.string.nav_import),
    CATALOG(Routes.CATALOG, Icons.Outlined.Search, com.stick.app.R.string.nav_catalog),
    SETTINGS(Routes.SETTINGS, Icons.Outlined.Settings, com.stick.app.R.string.nav_settings),
}
