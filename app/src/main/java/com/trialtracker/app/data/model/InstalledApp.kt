package com.trialtracker.app.data.model

/** An app found on the device. Never leaves the device — see PackageScanner. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val firstInstallTime: Long,
    val isSystem: Boolean,
)

/** A deal joined with the on-device state the UI needs to render it. */
data class DealUi(
    val deal: Deal,
    val installed: Boolean,
    val favorite: Boolean,
    val installedLabel: String? = null,
)
