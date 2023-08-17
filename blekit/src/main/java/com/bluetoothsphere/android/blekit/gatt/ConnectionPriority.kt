package com.bluetoothsphere.android.blekit.gatt

import android.bluetooth.BluetoothGatt

enum class ConnectionPriority(val value: Int) {
    /**
     * Use the connection parameters recommended by the Bluetooth SIG.
     * This is the default value if no connection parameter update
     * is requested.
     */
    BALANCED(BluetoothGatt.CONNECTION_PRIORITY_BALANCED),

    /**
     * Request a high priority, low latency connection.
     * An application should only request high priority connection parameters to transfer large
     * amounts of data over LE quickly. Once the transfer is complete, the application should
     * request BALANCED connection parameters to reduce energy use.
     */
    HIGH(BluetoothGatt.CONNECTION_PRIORITY_HIGH),

    /**
     * Request low power, reduced data rate connection parameters.
     */
    LOW_POWER(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER);
}
