package com.bluetoothsphere.android.blekit.gatt

/**
 * This class represents the possible Phy types
 */
enum class PhyType(val value: Int, val mask: Int) {
    /**
     * A Physical Layer (PHY) connection of 1 mbit. Compatible with Bluetooth 4.0, 4.1, 4.2 and 5.0
     */
    LE_1M(1, 1),

    /**
     * A Physical Layer (PHY) connection of 2 mbit. Requires Bluetooth 5
     */
    LE_2M(2, 2),

    /**
     * A Physical Layer (PHY) connection with long range. Requires Bluetooth 5
     */
    LE_CODED(3, 4),

    /**
     * Unknown Phy Type. Not to be used.
     */
    UNKNOWN_PHY_TYPE(-1, -1);

    companion object {
        fun fromValue(value: Int): PhyType {
            for (type in values()) {
                if (type.value == value) return type
            }
            return UNKNOWN_PHY_TYPE
        }
    }
}
