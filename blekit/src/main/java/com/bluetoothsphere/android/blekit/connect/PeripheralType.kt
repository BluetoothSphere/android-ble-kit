package com.bluetoothsphere.android.blekit.connect

import android.bluetooth.BluetoothDevice

/**
 * This class represents the possible peripheral types
 */
enum class PeripheralType(val value: Int) {
    /**
     * Unknown peripheral type, peripheral is not cached
     */
    UNKNOWN(BluetoothDevice.DEVICE_TYPE_UNKNOWN),

    /**
     * Classic - BR/EDR peripheral
     */
    CLASSIC(BluetoothDevice.DEVICE_TYPE_CLASSIC),

    /**
     * Bluetooth Low Energy peripheral
     */
    LE(BluetoothDevice.DEVICE_TYPE_LE),

    /**
     * Dual Mode - BR/EDR/LE
     */
    DUAL(BluetoothDevice.DEVICE_TYPE_DUAL);

    companion object {
        fun fromValue(value: Int): PeripheralType {
            for (type in values()) {
                if (type.value == value) {
                    return type
                }
            }
            return UNKNOWN
        }
    }
}
