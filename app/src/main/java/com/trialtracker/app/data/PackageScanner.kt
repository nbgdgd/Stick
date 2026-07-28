package com.trialtracker.app.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.trialtracker.app.data.model.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Finds which of the catalog's apps are actually on this device.
 *
 * Deliberately narrow: on Android 11+ package visibility hides other apps unless
 * they are declared in `<queries>`, and `QUERY_ALL_PACKAGES` is a policy-sensitive
 * permission Google Play rejects without a strong justification. Since matching
 * only happens against known catalog packages, probing those packages one by one
 * with [PackageManager.getApplicationInfo] in a try/catch is all that is needed.
 *
 * [scanAll] is the opt-in path behind the "show all apps" setting. Even then the
 * platform only returns what this app is allowed to see, which is exactly the
 * declared packages plus anything the user launched us from.
 */
class PackageScanner(private val context: Context) {

    suspend fun scanCatalogPackages(packageNames: Collection<String>): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            packageNames.distinct().mapNotNull { pkg -> lookup(pm, pkg) }
        }

    suspend fun scanAll(includeSystem: Boolean): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        runCatching {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { includeSystem || !it.isSystemApp() }
                .map { info -> info.toInstalledApp(pm) }
        }.getOrDefault(emptyList())
    }

    private fun lookup(pm: PackageManager, pkg: String): InstalledApp? = try {
        val info = pm.getApplicationInfo(pkg, 0)
        info.toInstalledApp(pm)
    } catch (e: PackageManager.NameNotFoundException) {
        // Either the app is not installed, or it is not visible to us. Both mean
        // "no match" — there is nothing to recover from and nothing to log.
        null
    } catch (e: SecurityException) {
        null
    }

    private fun ApplicationInfo.toInstalledApp(pm: PackageManager): InstalledApp {
        val firstInstall = runCatching {
            pm.getPackageInfo(packageName, 0).firstInstallTime
        }.getOrDefault(0L)
        return InstalledApp(
            packageName = packageName,
            label = loadLabel(pm).toString(),
            firstInstallTime = firstInstall,
            isSystem = isSystemApp(),
        )
    }

    private fun ApplicationInfo.isSystemApp(): Boolean =
        (flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
}
