package com.bluetoothsphere.android.blekit.gatt

import android.bluetooth.le.ScanCallback

/**
 * This class represents the possible scan failure reasons
 */
enum class ScanFailure(val value: Int) {
    /**
     * Failed to start scan as BLE scan with the same settings is already started by the app.
     */
    ALREADY_STARTED(ScanCallback.SCAN_FAILED_ALREADY_STARTED),

    /**
     * Failed to start scan as app cannot be registered.
     */
    APPLICATION_REGISTRATION_FAILED(ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED),

    /**
     * Failed to start scan due an internal error
     */
    INTERNAL_ERROR(ScanCallback.SCAN_FAILED_INTERNAL_ERROR),

    /**
     * Failed to start power optimized scan as this feature is not supported.
     */
    FEATURE_UNSUPPORTED(ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED),

    /**
     * Failed to start scan as it is out of hardware resources.
     */
    OUT_OF_HARDWARE_RESOURCES(5),

    /**
     * Failed to start scan as application tries to scan too frequently.
     */
    SCANNING_TOO_FREQUENTLY(6), UNKNOWN(-1);

    companion object {
        fun fromValue(value: Int): ScanFailure {
            for (type in values()) {
                if (type.value == value) {
                    return type
                }
            }
            return UNKNOWN
        }
    }
}
