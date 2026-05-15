package com.labbaslabs.jampsfit

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import androidx.core.content.edit
import android.util.Log
import com.labbaslabs.jampsfit.database.AppDatabase
import com.labbaslabs.jampsfit.database.HealthEntry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.*

data class WatchState(
    val battery: Int? = null,
    val heartRate: Int? = null,
    val spo2: Int? = null,
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val steps: Int? = null,
    val distance: Int? = null,
    val calories: Int? = null,
    val isConnected: Boolean = false,
    val deviceName: String? = null,
    val connectionStatus: String = "Disconnected",
    val debugLog: String = "Wait for scan...",
    val unknownMessages: List<String> = emptyList(),
    val lastRemoteEvent: String? = null,
    val shutterAction: String = "Camera", // "Camera", "FindMyPhone", "Media", "None"
    val musicAction: String = "Media", // "Media", "Volume", "Utility", "Custom", "None"
    val playPauseAction: String = "Play/Pause",
    val nextAction: String = "Next Track",
    val prevAction: String = "Previous Track",
    val autoStart: Boolean = false,
    val autoConnect: Boolean = false,
    val batteryThreshold: Int = 15,
    val batteryEstimation: String? = null
)

@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class WatchManager(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val healthDao = db.healthDao()
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var firstBatteryLevel: Int? = null
    private var firstBatteryTime: Long = 0

    private val prefs = context.getSharedPreferences("jampsFitPrefs", Context.MODE_PRIVATE)
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scanner get() = adapter?.bluetoothLeScanner

    private val _state = MutableStateFlow(WatchState(
        autoStart = prefs.getBoolean("autoStart", false),
        autoConnect = prefs.getBoolean("autoConnect", true),
        batteryThreshold = prefs.getInt("batteryThreshold", 15),
        shutterAction = prefs.getString("shutterAction", "Camera") ?: "Camera",
        musicAction = prefs.getString("musicAction", "Media") ?: "Media",
        playPauseAction = prefs.getString("playPauseAction", "Play/Pause") ?: "Play/Pause",
        nextAction = prefs.getString("nextAction", "Next Track") ?: "Next Track",
        prevAction = prefs.getString("prevAction", "Previous Track") ?: "Previous Track"
    ))
    val state = _state.asStateFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var lastConnectedDevice: BluetoothDevice? = null
    private var reconnectCount = 0
    private val maxReconnectAttempts = 5

    private val operationQueue: Queue<GattOperation> = LinkedList()
    private var isOperating = false

    sealed class GattOperation {
        class WriteDescriptor(val descriptor: BluetoothGattDescriptor, val value: ByteArray) : GattOperation()
        class ReadCharacteristic(val characteristic: BluetoothGattCharacteristic) : GattOperation()
    }

    companion object {
        private const val TAG = "WatchManager"
        private const val TARGET_NAME = "TANK M1"
        private val BATTERY_CHAR = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        private val HEART_RATE_CHAR = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        private val FEE1_CHAR = UUID.fromString("0000fee1-0000-1000-8000-00805f9b34fb")
        private val FEA1_CHAR = UUID.fromString("0000fea1-0000-1000-8000-00805f9b34fb")
        private val SKIP_NOTIFY_CHAR = UUID.fromString("00002a05-0000-1000-8000-00805f9b34fb")
        private val CLIENT_CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    fun updateShutterAction(action: String) {
        prefs.edit { putString("shutterAction", action) }
        _state.update { it.copy(shutterAction = action) }
    }

    fun updateMusicAction(action: String) {
        prefs.edit { putString("musicAction", action) }
        _state.update { it.copy(musicAction = action) }
    }

    fun updateCustomAction(button: String, action: String) {
        when (button) {
            "Play/Pause" -> {
                prefs.edit { putString("playPauseAction", action) }
                _state.update { it.copy(playPauseAction = action) }
            }
            "Next" -> {
                prefs.edit { putString("nextAction", action) }
                _state.update { it.copy(nextAction = action) }
            }
            "Previous" -> {
                prefs.edit { putString("prevAction", action) }
                _state.update { it.copy(prevAction = action) }
            }
        }
    }

    fun toggleAutoStart(enabled: Boolean) {
        prefs.edit { putBoolean("autoStart", enabled) }
        _state.update { it.copy(autoStart = enabled) }
    }

    fun toggleAutoConnect(enabled: Boolean) {
        prefs.edit { putBoolean("autoConnect", enabled) }
        _state.update { it.copy(autoConnect = enabled) }
    }

    fun updateBatteryThreshold(threshold: Int) {
        prefs.edit { putInt("batteryThreshold", threshold) }
        _state.update { it.copy(batteryThreshold = threshold) }
    }

    private fun updateDebugLog(msg: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val newEntry = "[$timestamp] $msg"
        _state.update { s ->
            val lines = s.debugLog.lines().filter { it.isNotBlank() && it != "Wait for scan..." }
            val newLog = (lines + newEntry).takeLast(20).joinToString("\n")
            s.copy(debugLog = newLog)
        }
    }

    private fun enqueueOperation(operation: GattOperation) {
        synchronized(operationQueue) {
            operationQueue.add(operation)
            if (!isOperating) {
                doNextOperation()
            }
        }
    }

    private fun doNextOperation() {
        synchronized(operationQueue) {
            val operation = operationQueue.poll()
            if (operation == null) {
                isOperating = false
                return
            }
            isOperating = true
            val gatt = bluetoothGatt ?: run {
                isOperating = false
                return
            }

            when (operation) {
                is GattOperation.WriteDescriptor -> {
                    operation.descriptor.value = operation.value
                    gatt.writeDescriptor(operation.descriptor)
                }
                is GattOperation.ReadCharacteristic -> {
                    gatt.readCharacteristic(operation.characteristic)
                }
            }
        }
    }

    private fun extractName(result: ScanResult): String? {
        val record = result.scanRecord
        val name = record?.deviceName
        if (!name.isNullOrBlank()) return name

        record?.bytes?.let { bytes ->
            var pos = 0
            while (pos < bytes.size - 2) {
                val len = bytes[pos].toInt() and 0xFF
                if (len <= 1) break
                if (pos + len >= bytes.size) break
                val type = bytes[pos + 1].toInt() and 0xFF
                if (type == 0x08 || type == 0x09) {
                    val nameBytes = bytes.copyOfRange(pos + 2, pos + len + 1)
                    val parsedName = String(nameBytes).trim { it <= ' ' }.filter { it.code in 32..126 }
                    if (parsedName.isNotBlank()) return parsedName
                }
                pos += len + 1
            }
        }

        return try {
            result.device.name
        } catch (_: SecurityException) {
            null
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = extractName(result)
            val device = result.device
            val logMsg = if (name == null) {
                val hex = result.scanRecord?.bytes?.take(8)?.joinToString("") { "%02X".format(it) } ?: "No Data"
                "Found: Unknown (${device.address}) RSSI: ${result.rssi} Data: $hex..."
            } else {
                "Found: $name (${device.address}) RSSI: ${result.rssi}"
            }
            Log.d(TAG, logMsg)
            updateDebugLog(logMsg)
            
            if (name?.contains(TARGET_NAME, ignoreCase = true) == true) {
                stopScan()
                connectToDevice(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: $errorCode")
            _state.update { it.copy(connectionStatus = "Scan Failed: $errorCode") }
            updateDebugLog("Scan failed error: $errorCode")
        }
    }

    fun startScan() {
        val leScanner = scanner
        if (leScanner == null) {
            _state.update { it.copy(connectionStatus = "Bluetooth Error", debugLog = "Error: Bluetooth is OFF or Scanner unavailable") }
            return
        }
        _state.update { it.copy(connectionStatus = "Scanning...") }
        updateDebugLog("Scan started (Active)...")
        
        val settings = android.bluetooth.le.ScanSettings.Builder()
            .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
            
        try {
            leScanner.startScan(null, settings, scanCallback)
        } catch (e: Exception) {
            updateDebugLog("Scan start error: ${e.message}")
        }
    }

    fun stopScan() {
        try {
            scanner?.stopScan(scanCallback)
            updateDebugLog("Scan stopped")
        } catch (e: Exception) {}
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        synchronized(operationQueue) {
            operationQueue.clear()
            isOperating = false
        }
        _state.update { it.copy(isConnected = false, connectionStatus = "Disconnected") }
        updateDebugLog("Disconnected")
    }

    private fun connectToDevice(device: BluetoothDevice) {
        lastConnectedDevice = device
        _state.update { it.copy(connectionStatus = "Connecting to ${device.name}...", deviceName = device.name) }
        updateDebugLog("Connecting to ${device.address}...")
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    fun manualConnect() {
        lastConnectedDevice?.let {
            reconnectCount = 0
            connectToDevice(it)
        } ?: startScan()
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.")
                reconnectCount = 0
                _state.update { it.copy(isConnected = true, connectionStatus = "Connected") }
                updateDebugLog("GATT Connected")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.")
                _state.update { it.copy(isConnected = false, connectionStatus = "Disconnected") }
                updateDebugLog("GATT Disconnected")
                
                synchronized(operationQueue) {
                    operationQueue.clear()
                    isOperating = false
                }

                if (_state.value.autoConnect && reconnectCount < maxReconnectAttempts) {
                    reconnectCount++
                    val delayMs = 2000L * reconnectCount
                    updateDebugLog("Attempting reconnect $reconnectCount/$maxReconnectAttempts in ${delayMs/1000}s...")
                    managerScope.launch {
                        delay(delayMs)
                        lastConnectedDevice?.let { connectToDevice(it) }
                    }
                } else {
                    updateDebugLog("Max reconnect attempts reached.")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                updateDebugLog("Services discovered")
                for (service in gatt.services) {
                    for (characteristic in service.characteristics) {
                        if (characteristic.uuid == SKIP_NOTIFY_CHAR) continue
                        
                        val properties = characteristic.properties
                        val canNotify = (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                        val canIndicate = (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                        
                        if (canNotify || canIndicate) {
                            gatt.setCharacteristicNotification(characteristic, true)
                            val descriptor = characteristic.getDescriptor(CLIENT_CONFIG_DESCRIPTOR)
                            if (descriptor != null) {
                                val value = if (canNotify) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                                enqueueOperation(GattOperation.WriteDescriptor(descriptor, value))
                                updateDebugLog("Queued notify: ${characteristic.uuid.toString().take(8)}")
                            }
                        }
                        
                        // Read battery immediately if available
                        if (characteristic.uuid == BATTERY_CHAR) {
                            enqueueOperation(GattOperation.ReadCharacteristic(characteristic))
                        }
                    }
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.d(TAG, "onDescriptorWrite: ${descriptor.characteristic.uuid} status: $status")
            doNextOperation()
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleData(characteristic.uuid, characteristic.value)
            }
            doNextOperation()
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleData(characteristic.uuid, characteristic.value)
        }
    }

    private fun addUnknownMessage(msg: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = "[$timestamp] $msg"
        _state.update { s ->
            s.copy(unknownMessages = (s.unknownMessages + entry).takeLast(50))
        }
    }

    private fun handleData(uuid: UUID, data: ByteArray) {
        val rawHex = data.joinToString(" ") { "%02X".format(it) }

        when (uuid) {
            BATTERY_CHAR -> {
                if (data.isNotEmpty()) {
                    val battery = data[0].toInt() and 0xFF
                    val currentTime = System.currentTimeMillis()
                    
                    if (firstBatteryLevel == null) {
                        firstBatteryLevel = battery
                        firstBatteryTime = currentTime
                    }

                    val estimation = calculateEstimation(battery, currentTime)
                    
                    _state.update { it.copy(battery = battery, batteryEstimation = estimation) }
                    updateDebugLog("Battery: $battery% raw=$rawHex")
                    saveToDb(battery = battery)
                }
            }
            HEART_RATE_CHAR -> {
                val bpm = parseStandardHeartRate(data)
                if (bpm != null) {
                    _state.update { it.copy(heartRate = bpm) }
                    updateDebugLog("Heart rate: $bpm bpm raw=$rawHex")
                    saveToDb(heartRate = bpm)
                }
            }
            FEE1_CHAR, FEA1_CHAR -> {
                val activityMsg = parseActivityPacket(data)
                if (activityMsg != null) {
                    updateDebugLog(activityMsg)
                    val s = _state.value
                    saveToDb(steps = s.steps, distance = s.distance, calories = s.calories)
                } else {
                    val msg = "Activity unknown raw=$rawHex"
                    updateDebugLog(msg)
                    addUnknownMessage(msg)
                }
            }
            else -> {
                val kospetMsg = parseKospetPacket(data)
                if (kospetMsg != null) {
                    updateDebugLog(kospetMsg)
                    val s = _state.value
                    if (kospetMsg.contains("SpO2")) saveToDb(spo2 = s.spo2)
                    else if (kospetMsg.contains("Heart rate")) saveToDb(heartRate = s.heartRate)
                    else if (kospetMsg.contains("Blood pressure")) saveToDb(systolic = s.systolic, diastolic = s.diastolic)
                } else {
                    val msg = "Unknown packet: raw=$rawHex"
                    updateDebugLog(msg)
                    addUnknownMessage(msg)
                }
            }
        }
    }

    private fun saveToDb(
        battery: Int? = null,
        heartRate: Int? = null,
        spo2: Int? = null,
        systolic: Int? = null,
        diastolic: Int? = null,
        steps: Int? = null,
        distance: Int? = null,
        calories: Int? = null
    ) {
        managerScope.launch {
            healthDao.insert(HealthEntry(
                battery = battery,
                heartRate = heartRate,
                spo2 = spo2,
                systolic = systolic,
                diastolic = diastolic,
                steps = steps,
                distance = distance,
                calories = calories
            ))
        }
    }

    private fun parseStandardHeartRate(data: ByteArray): Int? {
        if (data.isEmpty()) return null
        val flags = data[0].toInt()
        val isUint16 = (flags and 0x01) != 0
        return if (isUint16) {
            if (data.size < 3) null
            else (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
        } else {
            if (data.size < 2) null
            else data[1].toInt() and 0xFF
        }
    }

    private fun parseActivityPacket(data: ByteArray): String? {
        var b = data
        val rawHex = b.joinToString(" ") { "%02X".format(it) }
        if (b.size == 10 && b[0] == 0x07.toByte()) {
            b = b.copyOfRange(1, b.size)
        }

        if (b.size == 9) {
            val seq = b[0].toInt() and 0xFF
            val steps = (b[1].toInt() and 0xFF) or ((b[2].toInt() and 0xFF) shl 8)
            val distance = (b[3].toInt() and 0xFF) or ((b[4].toInt() and 0xFF) shl 8)
            val calories = (b[6].toInt() and 0xFF) or ((b[7].toInt() and 0xFF) shl 8)
            _state.update { it.copy(steps = steps, distance = distance, calories = calories) }
            return "Activity live: seq=$seq steps?=$steps distance=$distance m calories=$calories kcal raw=$rawHex"
        }
        return null
    }

    private fun parseKospetPacket(data: ByteArray): String? {
        val b = data
        val rawHex = b.joinToString(" ") { "%02X".format(it) }
        
        // Music Controls: FE EA 20 06 67 ...
        if (b.size == 6 && startsWith(b, byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x20.toByte(), 0x06.toByte(), 0x67.toByte()))) {
            val action = when (b[5].toInt()) {
                0x01 -> "Previous Track"
                0x02 -> "Next Track"
                0x06 -> "Play/Pause"
                else -> "Unknown Music Action (0x%02X)".format(b[5])
            }
            _state.update { it.copy(lastRemoteEvent = action) }
            return "Remote: $action raw=$rawHex"
        }
        
        // Wrist Shake / Shutter: FE EA 20 05 66
        if (b.size == 5 && startsWith(b, byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x20.toByte(), 0x05.toByte(), 0x66.toByte()))) {
            val action = "Wrist Shake / Shutter"
            _state.update { it.copy(lastRemoteEvent = action) }
            return "Remote: $action raw=$rawHex"
        }

        // SpO2: FE EA 20 06 6B ...
        if (b.size == 6 && startsWith(b, byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x20.toByte(), 0x06.toByte(), 0x6B.toByte()))) {
            val value = b[5].toInt() and 0xFF
            if (value != 0) {
                _state.update { it.copy(spo2 = value) }
                return "SpO2: $value% raw=$rawHex"
            } else {
                return "SpO2 status/progress raw=$rawHex"
            }
        }
        // Heart rate: FE EA 20 06 6D ...
        else if (b.size == 6 && startsWith(b, byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x20.toByte(), 0x06.toByte(), 0x6D.toByte()))) {
            val value = b[5].toInt() and 0xFF
            if (value != 0) {
                _state.update { it.copy(heartRate = value) }
                return "Heart rate: $value bpm raw=$rawHex"
            } else {
                return "Measurement stopped raw=$rawHex"
            }
        }
        // Blood pressure: FE EA 20 08 69 00 ...
        else if (b.size == 8 && startsWith(b, byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x20.toByte(), 0x08.toByte(), 0x69.toByte(), 0x00.toByte()))) {
            val systolic = b[6].toInt() and 0xFF
            val diastolic = b[7].toInt() and 0xFF
            if (systolic != 0xFF && diastolic != 0xFF) {
                _state.update { it.copy(systolic = systolic, diastolic = diastolic) }
                return "Blood pressure: $systolic/$diastolic mmHg raw=$rawHex"
            } else {
                return "Blood pressure aborted/failed raw=$rawHex"
            }
        }
        return null
    }

    private fun startsWith(data: ByteArray, prefix: ByteArray): Boolean {
        if (data.size < prefix.size) return false
        for (i in prefix.indices) {
            if (data[i] != prefix[i]) return false
        }
        return true
    }

    private fun calculateEstimation(currentLevel: Int, currentTime: Long): String? {
        val firstLevel = firstBatteryLevel ?: return null
        val timeDiff = currentTime - firstBatteryTime
        val levelDiff = firstLevel - currentLevel

        if (levelDiff <= 0 || timeDiff <= 0) return "Calculating..."

        val msPerPercent = timeDiff / levelDiff
        val remainingMs = currentLevel * msPerPercent
        
        val hours = remainingMs / (1000 * 60 * 60)
        val mins = (remainingMs / (1000 * 60)) % 60
        
        return if (hours > 24) {
            "${hours / 24}d ${hours % 24}h remaining"
        } else {
            "${hours}h ${mins}m remaining"
        }
    }
}
