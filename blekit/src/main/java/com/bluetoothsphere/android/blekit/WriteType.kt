package com.bluetoothsphere.android.blekit

import android.bluetooth.BluetoothGattCharacteristic

enum class WriteType(val writeType: Int, val property: Int) {
    /**
     * Write characteristic and requesting acknowledgement by the remote peripheral
     */
    WITH_RESPONSE(
        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
        BluetoothGattCharacteristic.PROPERTY_WRITE
    ),

    /**
     * Write characteristic without requiring a response by the remote peripheral
     */
    WITHOUT_RESPONSE(
        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
    ),

    /**
     * Write characteristic including authentication signature
     */
    SIGNED(
        BluetoothGattCharacteristic.WRITE_TYPE_SIGNED,
        BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE
    )
}
