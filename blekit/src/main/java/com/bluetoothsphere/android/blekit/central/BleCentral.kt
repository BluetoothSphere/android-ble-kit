package com.bluetoothsphere.android.blekit.central

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import com.bluetoothsphere.android.blekit.gatt.WriteType
import com.bluetoothsphere.android.blekit.gatt.BondState
import java.util.Objects

@SuppressLint("MissingPermission")
class BleCentral internal constructor(
    private val device: BluetoothDevice
) {
    private var currentMtu = 23

    val address: String
        get() = device.address
    val name: String
        get() = if (device.name == null) "" else device.name
    val bondState: BondState
        get() = BondState.fromValue(device.bondState)


    fun createBond(): Boolean = device.createBond()

    fun setPairingConfirmation(confirmation: Boolean) = device.setPairingConfirmation(confirmation)

    /**
     * Get maximum length of byte array that can be written depending on WriteType
     *
     * This value is derived from the current negotiated MTU or the maximum characteristic length (512)
     */
    fun getMaximumWriteValueLength(writeType: WriteType): Int {
        Objects.requireNonNull(writeType, "writetype is null")
        return when (writeType) {
            WriteType.WITH_RESPONSE -> 512
            WriteType.SIGNED -> currentMtu - 15
            WriteType.WITHOUT_RESPONSE -> currentMtu - 3
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as BleCentral
        return device.address == that.device.address
    }

    override fun hashCode(): Int {
        return Objects.hash(device)
    }
}
