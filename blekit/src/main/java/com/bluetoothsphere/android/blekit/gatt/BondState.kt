package com.bluetoothsphere.android.blekit.gatt

import android.bluetooth.BluetoothDevice

/**
 * The class represents the various possible bond states
 */
enum class BondState(val value: Int) {
    /**
     * Indicates the remote peripheral is not bonded.
     * There is no shared link key with the remote peripheral, so communication
     * (if it is allowed at all) will be unauthenticated and unencrypted.
     */
    NONE(BluetoothDevice.BOND_NONE),

    /**
     * Indicates bonding is in progress with the remote peripheral.
     */
    BONDING(BluetoothDevice.BOND_BONDING),

    /**
     * Indicates the remote peripheral is bonded.
     * A shared link keys exists locally for the remote peripheral, so
     * communication can be authenticated and encrypted.
     */
    BONDED(BluetoothDevice.BOND_BONDED);

    companion object {
        fun fromValue(value: Int): BondState {
            for (type in values()) {
                if (type.value == value) {
                    return type
                }
            }
            return NONE
        }
    }
}
