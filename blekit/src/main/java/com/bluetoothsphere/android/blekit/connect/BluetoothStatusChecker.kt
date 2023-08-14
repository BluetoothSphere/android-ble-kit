package com.bluetoothsphere.android.blekit.connect

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.bluetoothsphere.android.blekit.Logger

internal class BluetoothStatusChecker(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?
) {
    companion object {
        private val TAG = BluetoothStatusChecker::class.java.simpleName
    }

    fun bleNotReady(): Boolean {
        if (isBleSupported) {
            if (isBluetoothEnabled) {
                return !permissionsGranted()
            }
        }
        return true
    }

    val isBleSupported: Boolean
        get() {
            if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                return true
            }
            Logger.e(TAG, "BLE not supported")
            return false
        }

    /**
     * Check if Bluetooth is enabled
     *
     * @return true is Bluetooth is enabled, otherwise false
     */
    val isBluetoothEnabled: Boolean
        get() {
            if (bluetoothAdapter?.isEnabled == true) {
                return true
            }
            Logger.e(TAG, "Bluetooth disabled")
            return false
        }

    val requiredPermissions: Array<String>
        get() {
            val targetSdkVersion = context.applicationInfo.targetSdkVersion
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && targetSdkVersion >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else if (targetSdkVersion >= Build.VERSION_CODES.Q) {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            } else arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

    fun permissionsGranted(): Boolean {
        return getMissingPermissions().isEmpty()
    }

    fun getMissingPermissions(): Array<String> {
        val missingPermissions: MutableList<String> = ArrayList()
        for (requiredPermission in requiredPermissions) {
            if (context.checkSelfPermission(requiredPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(requiredPermission)
            }
        }
        return missingPermissions.toTypedArray()
    }
}
