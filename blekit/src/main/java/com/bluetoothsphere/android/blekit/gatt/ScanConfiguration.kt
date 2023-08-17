package com.bluetoothsphere.android.blekit.gatt

import android.bluetooth.le.ScanSettings

internal class ScanConfiguration {
    var currentScanMode: ScanMode? = null
    var currentAutoConnectScanMode: ScanMode? = null

    val scanSettings: ScanSettings
        get() = currentScanMode?.let {
            getScanSettings(it)
        } ?: getScanSettings(ScanMode.LOW_LATENCY)

    val autoConnectScanSettings: ScanSettings
        get() = currentAutoConnectScanMode?.let {
            getScanSettings(it)
        } ?: getScanSettings(ScanMode.LOW_POWER)

    private fun getScanSettings(scanMode: ScanMode): ScanSettings {
        return ScanSettings.Builder()
            .setScanMode(scanMode.value)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
            .setReportDelay(0L)
            .build()
    }
}
