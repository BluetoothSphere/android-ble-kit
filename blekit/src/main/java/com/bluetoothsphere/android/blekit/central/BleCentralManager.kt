package com.bluetoothsphere.android.blekit.central

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import com.bluetoothsphere.android.blekit.Logger
import com.bluetoothsphere.android.blekit.gatt.BleConnectManager
import com.bluetoothsphere.android.blekit.gatt.BleScanManager
import com.bluetoothsphere.android.blekit.gatt.ConnectCallback
import com.bluetoothsphere.android.blekit.gatt.ScanConfiguration
import com.bluetoothsphere.android.blekit.gatt.ScanFailure
import com.bluetoothsphere.android.blekit.gatt.ScanMode
import com.bluetoothsphere.android.blekit.gatt.Transport
import com.bluetoothsphere.android.blekit.peripheral.BlePeripheral
import com.bluetoothsphere.android.blekit.peripheral.BlePeripheralCallback
import java.util.UUID


class BleCentralManager private constructor(
    private val appContext: Context,
    private val bleCentralManagerCallback: BleCentralManagerCallback,
    private val callBackHandler: Handler
) {
    private val bluetoothAdapter: BluetoothAdapter

    private val scanConfiguration = ScanConfiguration()

    private val scanManager: BleScanManager by lazy {
        BleScanManager.getInstance(
            appContext,
            scanConfiguration.scanSettings,
            callBackHandler,
            ::onDiscovered,
            ::onScanFailed
        )
    }

    private val connectManager: BleConnectManager

    private val scannedPeripherals: MutableMap<String, BlePeripheral>
        get() = scanManager.scannedPeripherals
    private val connectedPeripherals: MutableMap<String, BlePeripheral>
        get() = connectManager.connectedPeripherals
    private val unconnectedPeripherals: MutableMap<String, BlePeripheral>
        get() = connectManager.unconnectedPeripherals
    private val internalCallback: ConnectCallback
        get() = connectManager.internalCallback
    private var transport = DEFAULT_TRANSPORT

    private val adapterStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                handleAdapterState(state)
                callBackHandler.post {
                    bleCentralManagerCallback.onBluetoothAdapterStateChanged(
                        state
                    )
                }
            }
        }
    }

    init {
        val manager =
            requireNotNull(appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager) { "cannot get BluetoothManager" }
        bluetoothAdapter = requireNotNull(manager.adapter) { "no bluetooth adapter found" }

        // Register for broadcasts on BluetoothAdapter state change
        connectManager = BleConnectManager.getInstance(
            appContext,
            scanConfiguration.autoConnectScanSettings,
            callBackHandler,
            bleCentralManagerCallback,
            ::getScannedPeripherals
        )
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        appContext.registerReceiver(adapterStateReceiver, filter)
    }

    companion object {
        @Volatile
        private var instance: BleCentralManager? = null

        private val DEFAULT_TRANSPORT = Transport.LE

        private val TAG = BleCentralManager::class.java.simpleName

        fun getInstance(
            context: Context,
            bleCentralManagerCallback: BleCentralManagerCallback,
            callBackHandler: Handler
        ): BleCentralManager {
            return instance ?: synchronized(this) {
                instance ?: BleCentralManager(
                    context.applicationContext,
                    bleCentralManagerCallback,
                    callBackHandler
                ).also { instance = it }
            }
        }
    }

    /**
     * Get the list of connected peripherals.
     *
     * @return list of connected peripherals
     */
    fun getConnectedPeripherals(): List<BlePeripheral> {
        return ArrayList(connectedPeripherals.values)
    }

    /**
     * Get a peripheral object matching the specified mac address.
     *
     * @param peripheralAddress mac address
     * @return a BlePeripheral object matching the specified mac address or null if it was not found
     */
    fun getPeripheral(peripheralAddress: String): BlePeripheral {
        if (!BluetoothAdapter.checkBluetoothAddress(peripheralAddress)) {
            val message = String.format(
                "%s is not a valid bluetooth address. Make sure all alphabetic characters are uppercase.",
                peripheralAddress
            )
            throw IllegalArgumentException(message)
        }
        return if (connectedPeripherals.containsKey(peripheralAddress)) {
            requireNotNull(connectedPeripherals[peripheralAddress])
        } else if (unconnectedPeripherals.containsKey(peripheralAddress)) {
            requireNotNull(unconnectedPeripherals[peripheralAddress])
        } else if (scannedPeripherals.containsKey(peripheralAddress)) {
            requireNotNull(scannedPeripherals[peripheralAddress])
        } else {
            val peripheral = BlePeripheral(
                appContext,
                bluetoothAdapter.getRemoteDevice(peripheralAddress),
                internalCallback,
                BlePeripheralCallback.NULL(),
                callBackHandler,
                transport
            )
            scannedPeripherals[peripheralAddress] = peripheral
            peripheral
            requireNotNull(scannedPeripherals[peripheralAddress])
        }
    }

    /**
     * Closes [BleCentralManager] and cleans up internals. BleCentralManager will not work anymore after this is called.
     */
    fun close() {

    }

    // region log
    fun setLoggingEnabled(enabled: Boolean) = run { Logger.enabled = enabled }

    fun setLogLevel(level: Int) = run { Logger.level = level }
    // endregion

    // region scanner
    /**
     * Set the default scanMode.
     *
     * @param scanMode the scanMode to set
     */
    fun setScanMode(scanMode: ScanMode) {
        scanConfiguration.currentScanMode = scanMode
    }

    /**
     * Set the default scanMode.
     *
     * @param scanMode the scanMode to set
     */
    fun setAutoConnectScanMode(scanMode: ScanMode) {
        scanConfiguration.currentAutoConnectScanMode = scanMode
    }

    /**
     * Scan for peripherals that advertise at least one of the specified service UUIDs.
     *
     * @param serviceUUIDs an array of service UUIDs
     */
    fun scanForPeripheralsWithServices(serviceUUIDs: List<UUID>) {
        scanManager.scanForPeripheralsWithServices(serviceUUIDs)
    }

    /**
     * Scan for peripherals with advertisement names containing any of the specified peripheral names.
     *
     *
     * Substring matching is used so only a partial peripheral names has to be supplied.
     *
     * @param peripheralNames array of partial peripheral names
     */
    fun scanForPeripheralsWithNames(peripheralNames: List<String>) {
        scanManager.scanForPeripheralsWithNames(peripheralNames)
    }

    /**
     * Scan for peripherals that have any of the specified peripheral mac addresses.
     *
     * @param peripheralAddresses array of peripheral mac addresses to scan for
     */
    fun scanForPeripheralsWithAddresses(peripheralAddresses: List<String>) {
        scanManager.scanForPeripheralsWithAddresses(peripheralAddresses)
    }

    /**
     * Scan for any peripheral that matches the supplied filters
     *
     * @param filters A list of ScanFilters
     */
    fun scanForPeripheralsUsingFilters(filters: List<ScanFilter>) {
        scanManager.scanForPeripheralsUsingFilters(filters)
    }

    /**
     * Scan for any peripheral that is advertising.
     */
    fun scanForPeripherals() {
        scanManager.scanForPeripherals()
    }
    // endregion

    // region pair
    /**
     * Connect to a known peripheral and bond immediately. The peripheral must have been found by scanning for this call to succeed. This method will time out in max 30 seconds on most phones and in 5 seconds on Samsung phones.
     * If the peripheral is already connected, no connection attempt will be made. This method is asynchronous and there can be only one outstanding connect.
     *
     * @param peripheral BLE peripheral to connect with
     */
    fun createBond(peripheral: BlePeripheral, peripheralCallback: BlePeripheralCallback) {
        connectManager.createBond(peripheral, peripheralCallback)
    }

    /**
     * Set a fixed PIN code for a peripheral that asks for a PIN code during bonding.
     *
     *
     * This PIN code will be used to programmatically bond with the peripheral when it asks for a PIN code. The normal PIN popup will not appear anymore.
     *
     * Note that this only works for peripherals with a fixed PIN code.
     *
     * @param peripheralAddress the address of the peripheral
     * @param pin               the 6 digit PIN code as a string, e.g. "123456"
     * @return true if the pin code and peripheral address are valid and stored internally
     */
    fun setPinCodeForPeripheral(peripheralAddress: String, pin: String): Boolean {
        return connectManager.setPinCodeForPeripheral(peripheralAddress, pin)
    }

    /**
     * Make the pairing popup appear in the foreground by doing a 1 sec discovery.
     *
     *
     * If the pairing popup is shown within 60 seconds, it will be shown in the foreground.
     */
    fun startPairingPopupHack() {
        connectManager.startPairingPopupHack()
    }

    /**
     * Remove bond for a peripheral.
     *
     * @param peripheralAddress the address of the peripheral
     * @return true if the peripheral was successfully bonded or it wasn't bonded, false if it was bonded and removing it failed
     */
    fun removeBond(peripheralAddress: String): Boolean {
        return connectManager.removeBond(peripheralAddress)
    }
    // endregion


    // region connect
    /**
     * Get the transport to be used during connection phase.
     *
     * @return transport
     */
    fun getTransport(): Transport {
        return transport
    }

    /**
     * Set the transport to be used when creating instances of [BlePeripheral].
     *
     * @param transport the Transport to set
     */
    fun setTransport(transport: Transport) {
        this.transport = transport
    }

    /**
     * Connect to a known peripheral immediately. The peripheral must have been found by scanning for this call to succeed. This method will time out in max 30 seconds on most phones and in 5 seconds on Samsung phones.
     * If the peripheral is already connected, no connection attempt will be made. This method is asynchronous and there can be only one outstanding connect.
     *
     * @param peripheral BLE peripheral to connect with
     */
    fun connect(peripheral: BlePeripheral, peripheralCallback: BlePeripheralCallback) {
        connectManager.connect(peripheral, peripheralCallback)
    }

    /**
     * Automatically connect to a peripheral when it is advertising. It is not necessary to scan for the peripheral first. This call is asynchronous and will not time out.
     *
     * @param peripheral the peripheral
     */
    fun autoConnect(peripheral: BlePeripheral, peripheralCallback: BlePeripheralCallback) {
        connectManager.autoConnect(peripheral, peripheralCallback)
    }

    /**
     * Automatically connect to a batch of peripherals.
     *
     *
     * Use this function to autoConnect to a batch of peripherals, instead of calling autoConnect on each of them.
     * Calling autoConnect on many peripherals may cause Android scanning limits to kick in, which is avoided by using autoConnectPeripheralsBatch.
     *
     * @param batch the map of peripherals and their callbacks to autoconnect to
     */
    fun autoConnectBatch(batch: Map<BlePeripheral, BlePeripheralCallback>) {
        connectManager.autoConnectBatch(batch)
    }

    /**
     * Cancel an active or pending connection for a peripheral.
     *
     * @param peripheral the peripheral
     */
    fun cancelConnection(peripheral: BlePeripheral) {
        connectManager.cancelConnection(peripheral)
    }
    // endregion

    private fun handleAdapterState(state: Int) {
        when (state) {
            BluetoothAdapter.STATE_OFF -> {
                // Check if there are any connected peripherals or connections in progress
                if (connectedPeripherals.isNotEmpty() || unconnectedPeripherals.isNotEmpty()) {
                    connectManager.cancelAllConnectionsWhenBluetoothOff()
                }
                Logger.d(TAG, "bluetooth turned off")
            }

            BluetoothAdapter.STATE_TURNING_OFF -> {
                // Disconnect connected peripherals
                for (peripheral in connectedPeripherals.values) {
                    peripheral.cancelConnection()
                }

                // Disconnect unconnected peripherals
                for (peripheral in unconnectedPeripherals.values) {
                    peripheral.cancelConnection()
                }

                // Stop all scans so that we are back in a clean state
                scanManager.onAdapterTurningOff()

                // Clean up autoconnect by scanning information
                connectManager.onAdapterTurningOff()

                Logger.d(TAG, "bluetooth turning off")
            }

            BluetoothAdapter.STATE_ON -> {
                Logger.d(TAG, "bluetooth turned on")
                scanManager.onAdapterStateOn()
            }

            BluetoothAdapter.STATE_TURNING_ON -> Logger.d(TAG, "bluetooth turning on")
        }
    }

    private fun onDiscovered(result: ScanResult) {
        val peripheral = getPeripheral(result.device.address)
        peripheral.setDevice(result.device)
        bleCentralManagerCallback.onDiscovered(peripheral, result)
    }

    private fun onScanFailed(scanFailure: ScanFailure) {
        Logger.e(TAG, "scan failed with error code %d (%s)", scanFailure.value, scanFailure)
        bleCentralManagerCallback.onScanFailed(scanFailure)
    }

    private fun getScannedPeripherals(): MutableMap<String, BlePeripheral> {
        return scannedPeripherals
    }
}
