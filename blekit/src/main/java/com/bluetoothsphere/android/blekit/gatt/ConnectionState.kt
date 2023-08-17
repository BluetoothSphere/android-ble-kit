package com.bluetoothsphere.android.blekit.gatt

/**
 * This class represents the possible connection states
 */
enum class ConnectionState(val value: Int) {
    /**
     * The peripheral is disconnected
     */
    DISCONNECTED(0),

    /**
     * The peripheral is connecting
     */
    CONNECTING(1),

    /**
     * The peripheral is connected
     */
    CONNECTED(2),

    /**
     * The peripheral is disconnecting
     */
    DISCONNECTING(3);

    companion object {
        fun fromValue(value: Int): ConnectionState {
            for (type in values()) {
                if (type.value == value) {
                    return type
                }
            }
            return DISCONNECTED
        }
    }
}
