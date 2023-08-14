package com.bluetoothsphere.android.blekit.connect

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.bluetoothsphere.android.blekit.Logger
import com.bluetoothsphere.android.blekit.central.BleCentralManagerCallback
import com.bluetoothsphere.android.blekit.connect.BleScanManager.Companion.SCAN_RESTART_DELAY
import com.bluetoothsphere.android.blekit.connect.BleScanManager.Companion.SCAN_TIMEOUT
import com.bluetoothsphere.android.blekit.peripheral.BlePeripheral
import com.bluetoothsphere.android.blekit.peripheral.BlePeripheralCallback
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("MissingPermission")
internal class BleConnectManager private constructor(
    private val appContext: Context,
    private val autoConnectScanSettings: ScanSettings,
    private val callBackHandler: Handler,
    private val bleCentralManagerCallback: BleCentralManagerCallback,
    private val getScannedPeripherals: () -> MutableMap<String, BlePeripheral>
) {
    internal val connectedPeripherals: MutableMap<String, BlePeripheral> = ConcurrentHashMap()

    internal val unconnectedPeripherals: MutableMap<String, BlePeripheral> = ConcurrentHashMap()

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager =
            appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bleStatusChecker: BluetoothStatusChecker by lazy {
        BluetoothStatusChecker(appContext, bluetoothAdapter)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var autoConnectScanner: BluetoothLeScanner? = null

    private val reconnectPeripheralAddresses: MutableList<String> = ArrayList()

    private val reconnectCallbacks: MutableMap<String, BlePeripheralCallback?> = ConcurrentHashMap()

    private val connectLock = Any()

    private var autoConnectRunnable: Runnable? = null

    private val pinCodes: MutableMap<String, String> = ConcurrentHashMap()

    private val connectionRetries: MutableMap<String, Int> = ConcurrentHashMap()

    companion object {
        @Volatile
        private var instance: BleConnectManager? = null
        private val TAG = BleConnectManager::class.java.simpleName
        private const val CANNOT_CONNECT_TO_PERIPHERAL_BECAUSE_BLUETOOTH_IS_OFF =
            "cannot connect to peripheral because Bluetooth is off"
        private const val MAX_CONNECTION_RETRIES = 1

        fun getInstance(
            context: Context,
            scanSettings: ScanSettings,
            callBackHandler: Handler,
            bleCentralManagerCallback: BleCentralManagerCallback,
            getScannedPeripherals: () -> MutableMap<String, BlePeripheral>
        ): BleConnectManager {
            return instance ?: synchronized(this) {
                instance ?: BleConnectManager(
                    context,
                    scanSettings,
                    callBackHandler,
                    bleCentralManagerCallback,
                    getScannedPeripherals
                ).also { instance = it }
            }
        }
    }

    fun onAdapterTurningOff() {
        reconnectPeripheralAddresses.clear()
        reconnectCallbacks.clear()
        unconnectedPeripherals.clear()

        if (isAutoScanning) {
            this.stopAutoConnectScan()
        }
        cancelAutoConnectTimer()
        autoConnectScanner = null
    }

    /**
     * Some phones, like Google/Pixel phones, don't automatically disconnect devices so this method does it manually
     */
    fun cancelAllConnectionsWhenBluetoothOff() {
        Logger.d(TAG, "disconnect all peripherals because bluetooth is off")

        // Call cancelConnection for connected peripherals
        for (peripheral in connectedPeripherals.values) {
            peripheral.disconnectWhenBluetoothOff()
        }
        connectedPeripherals.clear()

        // Call cancelConnection for unconnected peripherals
        for (peripheral in unconnectedPeripherals.values) {
            peripheral.disconnectWhenBluetoothOff()
        }
        unconnectedPeripherals.clear()

        // Clean up 'auto connect by scanning' information
        reconnectPeripheralAddresses.clear()
        reconnectCallbacks.clear()
    }

    fun createBond(peripheral: BlePeripheral, peripheralCallback: BlePeripheralCallback) {
        synchronized(connectLock) {
            if (connectedPeripherals.containsKey(peripheral.address)) {
                Logger.w(TAG, "already connected to %s'", peripheral.address)
                return
            }
            if (unconnectedPeripherals.containsKey(peripheral.address)) {
                Logger.w(TAG, "already connecting to %s'", peripheral.address)
                return
            }
            if (bluetoothAdapter?.isEnabled != true) {
                Logger.e(TAG, CANNOT_CONNECT_TO_PERIPHERAL_BECAUSE_BLUETOOTH_IS_OFF)
                return
            }

            // Check if the peripheral is cached or not. If not, issue a warning because connection may fail
            // This is because Android will guess the address type and when incorrect it will fail
            if (peripheral.isUncached) {
                Logger.w(
                    TAG,
                    "peripheral with address '%s' is not in the Bluetooth cache, hence connection may fail",
                    peripheral.address
                )
            }
            peripheral.peripheralCallback = peripheralCallback
            peripheral.createBond()
        }
    }

    fun setPinCodeForPeripheral(peripheralAddress: String, pin: String): Boolean {
        if (!BluetoothAdapter.checkBluetoothAddress(peripheralAddress)) {
            Logger.e(
                TAG,
                "%s is not a valid address. Make sure all alphabetic characters are uppercase.",
                peripheralAddress
            )
            return false
        }
        if (pin.length != 6) {
            Logger.e(TAG, "%s is not 6 digits long", pin)
            return false
        }
        pinCodes[peripheralAddress] = pin
        return true
    }

    fun startPairingPopupHack() {
        // Check if we are on a Samsung device because those don't need the hack
        val manufacturer = Build.MANUFACTURER
        if (!manufacturer.equals("samsung", ignoreCase = true)) {
            if (bleStatusChecker.bleNotReady()) return
            bluetoothAdapter?.startDiscovery()
            callBackHandler.postDelayed({
                Logger.d(TAG, "popup hack completed")
                bluetoothAdapter?.cancelDiscovery()
            }, 1000)
        }
    }

    fun removeBond(peripheralAddress: String): Boolean {
        // Get the set of bonded devices
        val bondedDevices = bluetoothAdapter?.bondedDevices ?: return true

        // See if the device is bonded
        var peripheralToUnBond: BluetoothDevice? = null
        if (bondedDevices.size > 0) {
            for (device in bondedDevices) {
                if (device.address == peripheralAddress) {
                    peripheralToUnBond = device
                }
            }
        } else {
            return true
        }

        // Try to remove the bond
        return if (peripheralToUnBond != null) {
            try {
                val method = peripheralToUnBond.javaClass.getMethod("removeBond")
                val result = method.invoke(peripheralToUnBond) as Boolean
                if (result) {
                    Logger.i(TAG, "Succesfully removed bond for '%s'", peripheralToUnBond.name)
                }
                result
            } catch (e: Exception) {
                Logger.i(TAG, "could not remove bond")
                e.printStackTrace()
                false
            }
        } else {
            true
        }
    }

    fun connect(
        peripheral: BlePeripheral,
        peripheralCallback: BlePeripheralCallback
    ) {
        synchronized(connectLock) {
            if (connectedPeripherals.containsKey(peripheral.address)) {
                Logger.w(TAG, "already connected to %s'", peripheral.address)
                return
            }

            if (unconnectedPeripherals.containsKey(peripheral.address)) {
                Logger.w(TAG, "already connecting to %s'", peripheral.address)
                return
            }

            if (bluetoothAdapter?.isEnabled != true) {
                Logger.e(TAG, CANNOT_CONNECT_TO_PERIPHERAL_BECAUSE_BLUETOOTH_IS_OFF)
                return
            }

            // Check if the peripheral is cached or not. If not, issue a warning because connection may fail
            // This is because Android will guess the address type and when incorrect it will fail
            if (peripheral.isUncached) {
                Logger.w(
                    TAG,
                    "peripheral with address '%s' is not in the Bluetooth cache, hence connection may fail",
                    peripheral.address
                )
            }

            peripheral.peripheralCallback = peripheralCallback
            getScannedPeripherals().remove(peripheral.address)
            unconnectedPeripherals[peripheral.address] = peripheral
            peripheral.connect()
        }
    }

    fun autoConnect(
        peripheral: BlePeripheral,
        peripheralCallback: BlePeripheralCallback
    ) {
        synchronized(connectLock) {
            if (connectedPeripherals.containsKey(peripheral.address)) {
                Logger.w(TAG, "already connected to %s'", peripheral.address)
                return
            }
            if (unconnectedPeripherals[peripheral.address] != null) {
                Logger.w(TAG, "already issued autoconnect for '%s' ", peripheral.address)
                return
            }
            if (bluetoothAdapter?.isEnabled != true) {
                Logger.e(TAG, CANNOT_CONNECT_TO_PERIPHERAL_BECAUSE_BLUETOOTH_IS_OFF)
                return
            }

            // Check if the peripheral is uncached and start autoConnectPeripheralByScan
            if (peripheral.isUncached) {
                Logger.d(
                    TAG,
                    "peripheral with address '%s' not in Bluetooth cache, autoconnecting by scanning",
                    peripheral.address
                )
                getScannedPeripherals().remove(peripheral.address)
                unconnectedPeripherals[peripheral.address] = peripheral
                autoConnectPeripheralByScan(peripheral.address, peripheralCallback)
                return
            }
            if (peripheral.type == PeripheralType.CLASSIC) {
                Logger.e(TAG, "peripheral does not support Bluetooth LE")
                return
            }
            peripheral.peripheralCallback = peripheralCallback
            getScannedPeripherals().remove(peripheral.address)
            unconnectedPeripherals[peripheral.address] = peripheral
            peripheral.autoConnect()
        }
    }

    fun autoConnectBatch(batch: Map<BlePeripheral, BlePeripheralCallback>) {
        if (bluetoothAdapter?.isEnabled != true) {
            Logger.e(TAG, CANNOT_CONNECT_TO_PERIPHERAL_BECAUSE_BLUETOOTH_IS_OFF)
            return
        }

        // Find the uncached peripherals and issue autoConnectPeripheral for the cached ones
        val uncachedPeripherals: MutableMap<BlePeripheral, BlePeripheralCallback?> = HashMap()
        for (peripheral in batch.keys) {
            if (peripheral.isUncached) {
                uncachedPeripherals[peripheral] = batch[peripheral]
            } else {
                autoConnect(peripheral, batch[peripheral]!!)
            }
        }

        // Add uncached peripherals to list of peripherals to scan for
        if (uncachedPeripherals.isNotEmpty()) {
            for (peripheral in uncachedPeripherals.keys) {
                val peripheralAddress = peripheral.address
                reconnectPeripheralAddresses.add(peripheralAddress)
                reconnectCallbacks[peripheralAddress] = uncachedPeripherals[peripheral]
                unconnectedPeripherals[peripheralAddress] = peripheral
            }
            scanForAutoConnectPeripherals()
        }
    }

    fun cancelConnection(peripheral: BlePeripheral) {
        // First check if we are doing a reconnection scan for this peripheral
        val peripheralAddress = peripheral.address
        if (reconnectPeripheralAddresses.contains(peripheralAddress)) {
            reconnectPeripheralAddresses.remove(peripheralAddress)
            reconnectCallbacks.remove(peripheralAddress)
            unconnectedPeripherals.remove(peripheralAddress)
            this.stopAutoConnectScan()
            Logger.d(TAG, "cancelling autoconnect for %s", peripheralAddress)
            callBackHandler.post {
                bleCentralManagerCallback.onDisconnected(
                    peripheral,
                    HciStatus.SUCCESS
                )
            }

            // If there are any devices left, restart the reconnection scan
            if (reconnectPeripheralAddresses.size > 0) {
                scanForAutoConnectPeripherals()
            }
            return
        }

        // Only cancel connections if it is a known peripheral
        if (unconnectedPeripherals.containsKey(peripheralAddress) || connectedPeripherals.containsKey(
                peripheralAddress
            )
        ) {
            peripheral.cancelConnection()
        } else {
            Logger.e(TAG, "cannot cancel connection to unknown peripheral %s", peripheralAddress)
        }
    }

    private val autoConnectScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            synchronized(this) {
                if (!isAutoScanning) return
                Logger.d(TAG, "peripheral with address '%s' found", result.device.address)
                this@BleConnectManager.stopAutoConnectScan()
                val deviceAddress = result.device.address
                val peripheral = unconnectedPeripherals[deviceAddress]
                val callback = reconnectCallbacks[deviceAddress]
                reconnectPeripheralAddresses.remove(deviceAddress)
                reconnectCallbacks.remove(deviceAddress)
                removePeripheralFromCaches(deviceAddress)

                if (peripheral != null && callback != null) {
                    connect(peripheral, callback)
                }
                if (reconnectPeripheralAddresses.size > 0) {
                    scanForAutoConnectPeripherals()
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            val scanFailure = ScanFailure.fromValue(errorCode)
            Logger.e(TAG, "autoConnect scan failed with error code %d (%s)", errorCode, scanFailure)
            this@BleConnectManager.stopAutoConnectScan()
            callBackHandler.post { bleCentralManagerCallback.onScanFailed(scanFailure) }
        }
    }

    private val isAutoScanning: Boolean
        get() = autoConnectScanner != null

    /**
     * Cancel the scan timeout timer
     */
    private fun cancelAutoConnectTimer() {
        autoConnectRunnable?.let {
            mainHandler.removeCallbacks(it)
        }
        autoConnectRunnable = null
    }

    private fun autoConnectPeripheralByScan(
        peripheralAddress: String,
        peripheralCallback: BlePeripheralCallback
    ) {
        if (reconnectPeripheralAddresses.contains(peripheralAddress)) {
            Logger.w(TAG, "peripheral already on list for reconnection")
            return
        }
        reconnectPeripheralAddresses.add(peripheralAddress)
        reconnectCallbacks[peripheralAddress] = peripheralCallback
        scanForAutoConnectPeripherals()
    }

    /**
     * Scan for peripherals that need to be autoconnected but are not cached
     */
    private fun scanForAutoConnectPeripherals() {
        if (bleStatusChecker.bleNotReady()) return
        if (autoConnectScanner != null) {
            this.stopAutoConnectScan()
        }

        autoConnectScanner = bluetoothAdapter?.bluetoothLeScanner
        if (autoConnectScanner != null) {
            val filters: MutableList<ScanFilter> = ArrayList()
            for (address in reconnectPeripheralAddresses) {
                val filter = ScanFilter.Builder()
                    .setDeviceAddress(address)
                    .build()
                filters.add(filter)
            }
            autoConnectScanner?.startScan(filters, autoConnectScanSettings, autoConnectScanCallback)
            Logger.d(
                TAG,
                "started scanning to autoconnect peripherals (" + reconnectPeripheralAddresses.size + ")"
            )
            setAutoConnectTimer()
        } else {
            Logger.e(TAG, "starting autoconnect scan failed")
        }
    }

    /**
     * Set scan timeout timer, timeout time is `SCAN_TIMEOUT`.
     * If timeout is executed the scan is stopped and automatically restarted. This is done to avoid Android 9 scan restrictions
     */
    private fun setAutoConnectTimer() {
        cancelAutoConnectTimer()
        autoConnectRunnable = Runnable {
            Logger.d(TAG, "autoconnect scan timeout, restarting scan")

            // Stop previous autoconnect scans if any
            this.stopAutoConnectScan()

            // Restart the auto connect scan and timer
            mainHandler.postDelayed(
                { scanForAutoConnectPeripherals() },
                SCAN_RESTART_DELAY.toLong()
            )
        }

        autoConnectRunnable?.let {
            mainHandler.postDelayed(it, SCAN_TIMEOUT)
        }
    }

    private fun stopAutoConnectScan() {
        cancelAutoConnectTimer()
        try {
            autoConnectScanner?.stopScan(autoConnectScanCallback)
        } catch (ignore: Exception) {
        }
        autoConnectScanner = null
        Logger.i(TAG, "auto scan stopped")
    }

    private fun removePeripheralFromCaches(peripheralAddress: String) {
        connectedPeripherals.remove(peripheralAddress)
        unconnectedPeripherals.remove(peripheralAddress)
        getScannedPeripherals().remove(peripheralAddress)
        connectionRetries.remove(peripheralAddress)
    }

    internal val internalCallback: ConnectCallback = object : ConnectCallback {
        override fun connecting(peripheral: BlePeripheral) {
            callBackHandler.post { bleCentralManagerCallback.onConnecting(peripheral) }
        }

        override fun connected(peripheral: BlePeripheral) {
            val peripheralAddress = peripheral.address
            removePeripheralFromCaches(peripheralAddress)
            connectedPeripherals[peripheralAddress] = peripheral
            callBackHandler.post { bleCentralManagerCallback.onConnected(peripheral) }
        }

        override fun connectFailed(peripheral: BlePeripheral, status: HciStatus) {
            val peripheralAddress = peripheral.address

            // Get the number of retries for this peripheral
            var nrRetries = 0
            val retries = connectionRetries[peripheralAddress]
            if (retries != null) nrRetries = retries
            removePeripheralFromCaches(peripheralAddress)

            // Retry connection or conclude the connection has failed
            if (nrRetries < MAX_CONNECTION_RETRIES && status != HciStatus.CONNECTION_FAILED_ESTABLISHMENT) {
                Logger.i(
                    TAG,
                    "retrying connection to '%s' (%s)",
                    peripheral.name,
                    peripheralAddress
                )
                nrRetries++
                connectionRetries[peripheralAddress] = nrRetries
                unconnectedPeripherals[peripheralAddress] = peripheral
                peripheral.connect()
            } else {
                Logger.i(TAG, "connection to '%s' (%s) failed", peripheral.name, peripheralAddress)
                callBackHandler.post {
                    bleCentralManagerCallback.onConnectionFailed(
                        peripheral,
                        status
                    )
                }
            }
        }

        override fun disconnecting(peripheral: BlePeripheral) {
            callBackHandler.post { bleCentralManagerCallback.onDisconnecting(peripheral) }
        }

        override fun disconnected(peripheral: BlePeripheral, status: HciStatus) {
            removePeripheralFromCaches(peripheral.address)
            callBackHandler.post { bleCentralManagerCallback.onDisconnected(peripheral, status) }
        }

        override fun getPincode(peripheral: BlePeripheral): String? {
            return pinCodes[peripheral.address]
        }
    }
}
