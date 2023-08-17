package com.bluetoothsphere.android.blekit.gatt

import com.bluetoothsphere.android.blekit.peripheral.BlePeripheral

internal interface ConnectCallback {
    /**
     * Trying to connect to [BlePeripheral]
     *
     * @param peripheral [BlePeripheral] the peripheral.
     */
    fun connecting(peripheral: BlePeripheral)

    /**
     * [BlePeripheral] has successfully connected.
     *
     * @param peripheral [BlePeripheral] that connected.
     */
    fun connected(peripheral: BlePeripheral)

    /**
     * Connecting with [BlePeripheral] has failed.
     *
     * @param peripheral [BlePeripheral] of which connect failed.
     */
    fun connectFailed(peripheral: BlePeripheral, status: HciStatus)

    /**
     * Trying to disconnect to [BlePeripheral]
     *
     * @param peripheral [BlePeripheral] the peripheral.
     */
    fun disconnecting(peripheral: BlePeripheral)

    /**
     * [BlePeripheral] has disconnected.
     *
     * @param peripheral [BlePeripheral] that disconnected.
     */
    fun disconnected(peripheral: BlePeripheral, status: HciStatus)
    fun getPincode(peripheral: BlePeripheral): String?
}
