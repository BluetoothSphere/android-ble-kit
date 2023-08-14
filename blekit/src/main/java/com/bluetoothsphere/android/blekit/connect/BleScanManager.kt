package com.bluetoothsphere.android.blekit.connect

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.bluetoothsphere.android.blekit.Logger
import com.bluetoothsphere.android.blekit.peripheral.BlePeripheral
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("MissingPermission")
internal class BleScanManager private constructor(
    private val appContext: Context,
    private val scanSettings: ScanSettings,
    private val callBackHandler: Handler,
    private val onDiscovered: (ScanResult) -> Unit,
    private val onScanFailed: (ScanFailure) -> Unit
) {
    internal val scannedPeripherals: MutableMap<String, BlePeripheral> = ConcurrentHashMap()

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager =
            appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bleStatusChecker: BluetoothStatusChecker by lazy {
        BluetoothStatusChecker(appContext, bluetoothAdapter)
    }

    @Volatile
    private var bluetoothScanner: BluetoothLeScanner? = null

    @Volatile
    private var currentCallback: ScanCallback? = null

    private var currentFilters: List<ScanFilter>? = null

    private var scanPeripheralNames = emptyList<String>()

    private val mainHandler = Handler(Looper.getMainLooper())

    private val scanLock = Any()

    private var timeoutRunnable: Runnable? = null

    companion object {
        @Volatile
        private var instance: BleScanManager? = null

        private val TAG = BleScanManager::class.java.simpleName

        const val SCAN_TIMEOUT = 180000L
        const val SCAN_RESTART_DELAY = 1000

        fun getInstance(
            context: Context,
            scanSettings: ScanSettings,
            callBackHandler: Handler,
            onDiscovered: (ScanResult) -> Unit,
            onScanFailed: (ScanFailure) -> Unit
        ): BleScanManager {
            return instance ?: synchronized(this) {
                instance ?: BleScanManager(
                    context,
                    scanSettings,
                    callBackHandler,
                    onDiscovered,
                    onScanFailed

                ).also { instance = it }
            }
        }
    }

    fun scanForPeripheralsWithServices(serviceUUIDs: List<UUID>) {
        require(serviceUUIDs.isNotEmpty()) { "at least one service UUID  must be supplied" }

        val filters: MutableList<ScanFilter> = ArrayList()
        for (serviceUUID in serviceUUIDs) {
            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(serviceUUID))
                .build()
            filters.add(filter)
        }
        startScan(filters, defaultScanCallback)
    }

    fun scanForPeripheralsWithNames(peripheralNames: List<String>) {
        require(peripheralNames.isNotEmpty()) { "at least one peripheral name must be supplied" }

        // Start the scanner with no filter because we'll do the filtering ourselves
        scanPeripheralNames = peripheralNames
        startScan(emptyList(), scanByNameCallback)
    }

    fun scanForPeripheralsWithAddresses(peripheralAddresses: List<String>) {
        require(peripheralAddresses.isNotEmpty()) { "at least one peripheral address must be supplied" }

        val filters: MutableList<ScanFilter> = ArrayList()
        for (address in peripheralAddresses) {
            if (BluetoothAdapter.checkBluetoothAddress(address)) {
                val filter = ScanFilter.Builder()
                    .setDeviceAddress(address)
                    .build()
                filters.add(filter)
            } else {
                Logger.e(
                    TAG,
                    "%s is not a valid address. Make sure all alphabetic characters are uppercase.",
                    address
                )
            }
        }
        startScan(filters, defaultScanCallback)
    }

    fun scanForPeripheralsUsingFilters(filters: List<ScanFilter>) {
        require(filters.isNotEmpty()) { "at least one scan filter must be supplied" }

        startScan(filters, defaultScanCallback)
    }

    fun scanForPeripherals() {
        startScan(emptyList(), defaultScanCallback)
    }

    /**
     * Stop scanning for peripherals.
     */
    fun stopScan() {
        synchronized(scanLock) {
            cancelTimeoutTimer()
            if (isScanning()) {
                // Note that we can't call stopScan if the adapter is off
                // On some phones like the Nokia 8, the adapter will be already off at this point
                // So add a try/catch to handle any exceptions
                try {
                    if (bluetoothScanner != null) {
                        bluetoothScanner?.stopScan(currentCallback)
                        currentCallback = null
                        currentFilters = null
                        Logger.i(TAG, "scan stopped")
                    }
                } catch (ignore: Exception) {
                    Logger.e(TAG, "caught exception in stopScan")
                }
            } else {
                Logger.i(TAG, "no scan to stop because no scan is running")
            }
            bluetoothScanner = null
            scannedPeripherals.clear()
        }
    }


    private fun startScan(filters: List<ScanFilter>, scanCallback: ScanCallback) {
        if (bleStatusChecker.bleNotReady()) return
        if (isScanning()) {
            Logger.e(TAG, "other scan still active, stopping scan")
            stopScan()
        }
        if (bluetoothScanner == null) {
            bluetoothScanner = bluetoothAdapter?.bluetoothLeScanner
        }
        if (bluetoothScanner != null) {
            setScanTimer()
            currentCallback = scanCallback
            currentFilters = filters
            bluetoothScanner?.startScan(filters, scanSettings, scanCallback)
            Logger.i(TAG, "scan started")
        } else {
            Logger.e(TAG, "starting scan failed")
        }
    }

    /**
     * Check if a scanning is active
     *
     * @return true if a scan is active, otherwise false
     */
    private fun isScanning(): Boolean = bluetoothScanner != null && currentCallback != null


    private val defaultScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            synchronized(this) { sendScanResult(result) }
        }

        override fun onScanFailed(errorCode: Int) {
            stopScan()
            sendScanFailed(ScanFailure.fromValue(errorCode))
        }
    }

    private val scanByNameCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            synchronized(this) {
                val deviceName = result.device.name ?: return
                for (name in scanPeripheralNames) {
                    if (deviceName.contains(name)) {
                        sendScanResult(result)
                        return
                    }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            stopScan()
            sendScanFailed(ScanFailure.fromValue(errorCode))
        }
    }

    /**
     * Set scan timeout timer, timeout time is `SCAN_TIMEOUT`.
     * If timeout is executed the scan is stopped and automatically restarted. This is done to avoid Android 9 scan restrictions
     */
    private fun setScanTimer() {
        cancelTimeoutTimer()
        timeoutRunnable = Runnable {
            Logger.d(TAG, "scanning timeout, restarting scan")
            val callback = currentCallback
            val filters = if (currentFilters != null) currentFilters!! else emptyList()
            stopScan()

            // Restart the scan and timer
            callBackHandler.postDelayed(
                { callback?.let { startScan(filters, it) } },
                SCAN_RESTART_DELAY.toLong()
            )
        }

        timeoutRunnable?.let {
            mainHandler.postDelayed(it, SCAN_TIMEOUT)
        }
    }

    /**
     * Cancel the scan timeout timer
     */
    private fun cancelTimeoutTimer() {
        timeoutRunnable?.let {
            mainHandler.removeCallbacks(it)
        }
        timeoutRunnable = null
    }

    private fun sendScanResult(result: ScanResult) {
        callBackHandler.post {
            if (isScanning()) {
                onDiscovered(result)
            }
        }
    }

    private fun sendScanFailed(scanFailure: ScanFailure) {
        currentCallback = null
        currentFilters = null
        cancelTimeoutTimer()
        callBackHandler.post { onScanFailed(scanFailure) }
    }

    fun onAdapterTurningOff() {
        if (isScanning()) {
            stopScan()
        }
        cancelTimeoutTimer()
        bluetoothScanner = null
    }

    fun onAdapterStateOn() {
        // On some phones like Nokia 8, this scanner may still have an older active scan from us
        // This happens when bluetooth is toggled. So make sure it is gone.
        bluetoothScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bluetoothScanner != null && currentCallback != null) {
            try {
                bluetoothScanner!!.stopScan(currentCallback)
            } catch (ignore: Exception) {
            }
        }
        currentCallback = null
        currentFilters = null
    }
}
