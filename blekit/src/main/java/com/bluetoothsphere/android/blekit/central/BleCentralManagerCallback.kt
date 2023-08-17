package com.bluetoothsphere.android.blekit.central

import android.bluetooth.le.ScanResult
import com.bluetoothsphere.android.blekit.gatt.HciStatus
import com.bluetoothsphere.android.blekit.gatt.ScanFailure
import com.bluetoothsphere.android.blekit.peripheral.BlePeripheral

/**
 * Callbacks for the BleCentralManager class
 */
abstract class BleCentralManagerCallback {
    /**
     * The peripheral is connecting
     *
     * @param peripheral the peripheral that is connecting
     */
    open fun onConnecting(peripheral: BlePeripheral) {}

    /**
     * Successfully connected with a peripheral.
     *
     * @param peripheral the peripheral that was connected.
     */
    open fun onConnected(peripheral: BlePeripheral) {}

    /**
     * Connecting with the peripheral has failed.
     *
     * @param peripheral the peripheral for which the connection was attempted
     * @param status the status code for the connection failure
     */
    open fun onConnectionFailed(peripheral: BlePeripheral, status: HciStatus) {}

    /**
     * Peripheral is disconnecting
     *
     * @param peripheral the peripheral we are trying to disconnect
     */
    open fun onDisconnecting(peripheral: BlePeripheral) {}

    /**
     * Peripheral disconnected
     *
     * @param peripheral the peripheral that disconnected.
     * @param status the status code for the disconnection
     */
    open fun onDisconnected(peripheral: BlePeripheral, status: HciStatus) {}

    /**
     * Discovered a peripheral
     *
     * @param peripheral the peripheral that was found
     * @param scanResult the scanResult describing the peripheral
     */
    open fun onDiscovered(peripheral: BlePeripheral, scanResult: ScanResult) {}

    /**
     * Scanning failed
     *
     * @param scanFailure the status code for the scanning failure
     */
    open fun onScanFailed(scanFailure: ScanFailure) {}

    /**
     * Bluetooth adapter status changed
     *
     * @param state the current status code for the adapter
     */
    open fun onBluetoothAdapterStateChanged(state: Int) {}

    /**
     * NULL class to deal with nullability
     */
    internal class NULL : BleCentralManagerCallback()
}
