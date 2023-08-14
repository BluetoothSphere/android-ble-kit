package com.bluetoothsphere.android.blekit.peripheral

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Handler
import com.bluetoothsphere.android.blekit.connect.ConnectCallback
import com.bluetoothsphere.android.blekit.connect.PeripheralType
import com.bluetoothsphere.android.blekit.connect.Transport

@SuppressLint("MissingPermission")
class BlePeripheral internal constructor(
    private val context: Context,
    private var device: BluetoothDevice,
    private val listener: ConnectCallback,
    var peripheralCallback: BlePeripheralCallback,
    private val callbackHandler: Handler,
    val transport: Transport
) {
    private var cachedName = ""

    /**
     * Get the name of the bluetooth peripheral.
     *
     * @return name of the bluetooth peripheral
     */
    val name: String
        get() {
            val name = device.name
            if (name != null) {
                // Cache the name so that we even know it when bluetooth is switched off
                cachedName = name
                return name
            }
            return cachedName
        }

    /**
     * Get the type of the peripheral.
     *
     * @return the PeripheralType
     */
    val type: PeripheralType
        get() = PeripheralType.fromValue(device.type)

    /**
     * Get the mac address of the bluetooth peripheral.
     *
     * @return Address of the bluetooth peripheral
     */
    val address: String
        get() = device.address

    /**
     * Check if the peripheral is uncached by the Android BLE stack
     *
     * @return true if unchached, otherwise false
     */
    val isUncached: Boolean
        get() = type == PeripheralType.UNKNOWN

    fun setDevice(device: BluetoothDevice?) {
        TODO("Not yet implemented")
    }

    fun cancelConnection() {
        TODO("Not yet implemented")
    }

    fun disconnectWhenBluetoothOff() {
        TODO("Not yet implemented")
    }

    fun createBond() {
        TODO("Not yet implemented")
    }

    fun connect() {
        TODO("Not yet implemented")
    }

    fun autoConnect() {
        TODO("Not yet implemented")
    }
}
