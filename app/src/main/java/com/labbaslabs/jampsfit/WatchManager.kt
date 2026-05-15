package com.labbaslabs.jampsfit

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
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
    val firmwareVersion: String? = null,
    val watchFaceId: Int? = null,
    val connectionStatus: String = "Disconnected",
    val debugLog: String = "Wait for scan...",
    val unknownMessages: List<String> = emptyList(),
    val lastRemoteEvent: String? = null,
    val shutterAction: String = "Camera",
    val musicAction: String = "Media",
    val playPauseAction: String = "Play/Pause",
    val nextAction: String = "Next Track",
    val prevAction: String = "Previous Track",
    val autoStart: Boolean = false,
    val autoConnect: Boolean = false,
    val batteryThreshold: Int = 15,
    val batteryEstimation: String? = null,
    val notificationsEnabled: Boolean = false,
    val weatherCity: String = "London",
    val weatherTemp: Int = 20,
    val weatherCondition: String = "Sunny",
    val activeMeasurement: String? = null,
    val sleepMinutes: Int? = null,
    val deepSleepMinutes: Int? = null,
    val lightSleepMinutes: Int? = null,
    val writeUuidShort: String = "6387",
    val protocolHeader: String = "FE EA 10",
    val requestMtu: Boolean = false,
    val payloadLengthOnly: Boolean = false
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
        prevAction = prefs.getString("prevAction", "Previous Track") ?: "Previous Track",
        firmwareVersion = prefs.getString("firmwareVersion", null),
        notificationsEnabled = prefs.getBoolean("notificationsEnabled", false)
    ))
    val state = _state.asStateFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var lastConnectedDevice: BluetoothDevice? = null
    private var reconnectCount = 0
    private val maxReconnectAttempts = 5

    private val operationQueue: Queue<GattOperation> = LinkedList()
    private var isOperating = false
    private var lastOpTime = 0L

    private fun checkQueueTimeout() {
        if (isOperating && System.currentTimeMillis() - lastOpTime > 2500) {
            updateDebugLog("Queue stuck - auto-recovering...")
            isOperating = false
            doNextOperation()
        }
    }

    fun clearQueue() {
        synchronized(operationQueue) {
            operationQueue.clear()
            isOperating = false
            updateDebugLog("Queue manually cleared")
        }
    }

    sealed class GattOperation {
        class WriteDescriptor(val descriptor: BluetoothGattDescriptor, val value: ByteArray) : GattOperation()
        class WriteCharacteristic(val charUuid: UUID?, val value: ByteArray) : GattOperation()
        class ReadCharacteristic(val characteristic: BluetoothGattCharacteristic) : GattOperation()
    }

    companion object {
        private const val TAG = "WatchManager"
        private const val TARGET_NAME = "TANK M1"
        private val BATTERY_CHAR = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        private val HEART_RATE_CHAR = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        private val FEE1_CHAR = UUID.fromString("0000fee1-0000-1000-8000-00805f9b34fb")
        private val FEE2_WRITE = UUID.fromString("0000fee2-0000-1000-8000-00805f9b34fb")
        private val FEE3_NOTIFY = UUID.fromString("0000fee3-0000-1000-8000-00805f9b34fb")
        private val DATA_CHAR_UUID = UUID.fromString("00006387-3c17-d293-8e48-14fe2e4da212")
        private val CLIENT_CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    fun findWatch() {
        // Use the exact 15-byte command from the snoop log which used the 20-series header
        val bytes = byteArrayOf(
            0xFE.toByte(), 0xEA.toByte(), 0x20.toByte(), 0x0F.toByte(), 0x00.toByte(),
            0x36.toByte(), 0x00.toByte(), 0xDA.toByte(), 0x01.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x11.toByte(), 0x03.toByte(), 0x20.toByte(), 0x31.toByte()
        )
        // Corrected: Use 6387 pipe (DATA_CHAR_UUID)
        enqueueOperation(GattOperation.WriteCharacteristic(DATA_CHAR_UUID, bytes))
        updateDebugLog("Finding watch (Native Snoop 6387)...")
    }

    fun syncTime() {
        // Aligned with the user's working sequence: FE EA 10 09 31 [BIG_ENDIAN_TS] 08
        // Fix: Use Local Time seconds to correct the 3-hour lag
        val tz = TimeZone.getDefault()
        val now = (System.currentTimeMillis() + tz.getOffset(System.currentTimeMillis())) / 1000
        
        val payload = ByteArray(5)
        payload[0] = ((now shr 24) and 0xFF).toByte()
        payload[1] = ((now shr 16) and 0xFF).toByte()
        payload[2] = ((now shr 8) and 0xFF).toByte()
        payload[3] = (now and 0xFF).toByte()
        payload[4] = 0x08.toByte()

        val packet = ByteArray(10)
        packet[0] = 0xFE.toByte()
        packet[1] = 0xEA.toByte()
        packet[2] = 0x10.toByte()
        packet[3] = 0x09.toByte() // TotalLen - 1
        packet[4] = 0x31.toByte() // Cmd
        System.arraycopy(payload, 0, packet, 5, 5)

        enqueueOperation(GattOperation.WriteCharacteristic(FEE2_WRITE, packet))
        updateDebugLog("Syncing clock (Local Big Endian)...")
    }

    fun queryHealth() {
        // Command 0x2F from your working Python script
        val packet = byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x10.toByte(), 0x04.toByte(), 0x2F.toByte())
        enqueueOperation(GattOperation.WriteCharacteristic(FEE2_WRITE, packet))
        updateDebugLog("Querying sensors (0x2F)...")
    }

    fun sendNotification(title: String, text: String, cmd: Int = 0x08, type: Int = 0x01) {
        val titleBytes = title.take(15).toByteArray(Charsets.UTF_8)
        val textBytes = text.take(15).toByteArray(Charsets.UTF_8)
        val payload = ByteArray(1 + 1 + titleBytes.size + 1 + textBytes.size)
        payload[0] = type.toByte()
        payload[1] = titleBytes.size.toByte()
        System.arraycopy(titleBytes, 0, payload, 2, titleBytes.size)
        payload[2 + titleBytes.size] = textBytes.size.toByte()
        System.arraycopy(textBytes, 0, payload, 3 + titleBytes.size, textBytes.size)
        enqueueOperation(GattOperation.WriteCharacteristic(null, formatPacket(cmd.toByte(), payload)))
    }

    private fun formatPacket(cmd: Byte, payload: ByteArray): ByteArray {
        val headerParts = _state.value.protocolHeader.split(" ")
        val is10Series = headerParts.getOrNull(2) == "10"
        
        val totalLen = 5 + payload.size
        val packet = ByteArray(totalLen)
        
        packet[0] = headerParts[0].toInt(16).toByte()
        packet[1] = headerParts[1].toInt(16).toByte()
        packet[2] = headerParts[2].toInt(16).toByte()
        
        // 10-series uses "Last Index" (Len-1), 20-series uses "Total Count" (Len)
        packet[3] = (if (is10Series) totalLen - 1 else totalLen).toByte()
        packet[4] = cmd
        System.arraycopy(payload, 0, packet, 5, payload.size)
        return packet
    }

    fun sendRawTest(hex: String, useAltChar: Boolean = false) {
        val bytes = if (hex == "01 01") {
            formatPacket(0x5A.toByte(), byteArrayOf(0x00.toByte()))
        } else {
            val raw = hex.split(" ").filter { it.isNotBlank() }.map { it.toInt(16).toByte() }.toByteArray()
            if (raw.size < 20) {
                val padded = ByteArray(20) { 0 }
                System.arraycopy(raw, 0, padded, 0, raw.size)
                padded
            } else raw
        }
        enqueueOperation(GattOperation.WriteCharacteristic(if (useAltChar) DATA_CHAR_UUID else null, bytes))
        updateDebugLog("TX -> ${bytes.take(6).joinToString("") { "%02X".format(it) }}...")
    }

    fun readBattery() {
        val gatt = bluetoothGatt ?: return
        for (s in gatt.services) {
            val c = s.getCharacteristic(BATTERY_CHAR)
            if (c != null) {
                enqueueOperation(GattOperation.ReadCharacteristic(c))
                return
            }
        }
    }

    fun startMeasurement(type: String) {
        val cmd = when (type) {
            "Heart Rate" -> 0x6D.toByte()
            "SpO2" -> 0x6B.toByte()
            "Blood Pressure" -> 0x69.toByte()
            else -> return
        }
        enqueueOperation(GattOperation.WriteCharacteristic(null, formatPacket(cmd, byteArrayOf(0x01.toByte()))))
        _state.update { it.copy(activeMeasurement = type) }
    }

    fun stopMeasurement() {
        val type = _state.value.activeMeasurement ?: return
        val cmd = when (type) {
            "Heart Rate" -> 0x6D.toByte()
            "SpO2" -> 0x6B.toByte()
            "Blood Pressure" -> 0x69.toByte()
            else -> return
        }
        enqueueOperation(GattOperation.WriteCharacteristic(null, formatPacket(cmd, byteArrayOf(0x00.toByte()))))
        _state.update { it.copy(activeMeasurement = null) }
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
            "Play/Pause" -> { prefs.edit { putString("playPauseAction", action) }; _state.update { it.copy(playPauseAction = action) } }
            "Next" -> { prefs.edit { putString("nextAction", action) }; _state.update { it.copy(nextAction = action) } }
            "Previous" -> { prefs.edit { putString("prevAction", action) }; _state.update { it.copy(prevAction = action) } }
        }
    }

    fun toggleAutoStart(enabled: Boolean) { prefs.edit { putBoolean("autoStart", enabled) }; _state.update { it.copy(autoStart = enabled) } }
    fun toggleAutoConnect(enabled: Boolean) { prefs.edit { putBoolean("autoConnect", enabled) }; _state.update { it.copy(autoConnect = enabled) } }
    fun toggleNotifications(enabled: Boolean) { prefs.edit { putBoolean("notificationsEnabled", enabled) }; _state.update { it.copy(notificationsEnabled = enabled) } }
    fun updateBatteryThreshold(threshold: Int) { prefs.edit { putInt("batteryThreshold", threshold) }; _state.update { it.copy(batteryThreshold = threshold) } }
    fun updateProtocol(header: String, uuid: String, mtu: Boolean, payload: Boolean) { _state.update { it.copy(protocolHeader = header, writeUuidShort = uuid, requestMtu = mtu, payloadLengthOnly = payload) } }

    private var logBuffer = mutableListOf<String>()

    private fun updateDebugLog(msg: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = "[$timestamp] $msg"
        logBuffer.add(entry)
        if (logBuffer.size > 100) logBuffer.removeAt(0)
        
        _state.update { s ->
            s.copy(debugLog = logBuffer.joinToString("\n"))
        }
    }

    private fun enqueueOperation(operation: GattOperation) {
        checkQueueTimeout()
        synchronized(operationQueue) { operationQueue.add(operation); if (!isOperating) doNextOperation() }
    }

    private fun doNextOperation() {
        synchronized(operationQueue) {
            if (isOperating) return
            val operation = operationQueue.poll() ?: return
            isOperating = true
            lastOpTime = System.currentTimeMillis()
            managerScope.launch {
                val gatt = bluetoothGatt ?: run { synchronized(operationQueue) { isOperating = false }; return@launch }
                val success = when (operation) {
                    is GattOperation.WriteDescriptor -> { operation.descriptor.value = operation.value; gatt.writeDescriptor(operation.descriptor) }
                    is GattOperation.WriteCharacteristic -> {
                        var found: BluetoothGattCharacteristic? = null
                        val short = (operation.charUuid?.toString()?.substring(4, 8) ?: _state.value.writeUuidShort).lowercase()
                        for (s in gatt.services) { for (c in s.characteristics) { if (c.uuid.toString().substring(4, 8).lowercase() == short) { found = c; break } }; if (found != null) break }
                        found?.let { it.value = operation.value; it.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE; gatt.writeCharacteristic(it) } ?: false
                    }
                    is GattOperation.ReadCharacteristic -> gatt.readCharacteristic(operation.characteristic)
                }
                if (!success) { synchronized(operationQueue) { isOperating = false }; doNextOperation() }
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name
            if (name?.contains(TARGET_NAME, ignoreCase = true) == true) { stopScan(); connectToDevice(result.device) }
        }
    }

    fun startScan() { _state.update { it.copy(connectionStatus = "Scanning...") }; scanner?.startScan(null, android.bluetooth.le.ScanSettings.Builder().setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback) }
    fun stopScan() { scanner?.stopScan(scanCallback) }
    fun disconnect() { bluetoothGatt?.disconnect(); bluetoothGatt?.close(); bluetoothGatt = null; _state.update { it.copy(isConnected = false, connectionStatus = "Disconnected") } }
    private fun connectToDevice(device: BluetoothDevice) { lastConnectedDevice = device; _state.update { it.copy(connectionStatus = "Connecting...", deviceName = device.name) }; bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE) }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) { _state.update { it.copy(isConnected = true, connectionStatus = "Connected") }; gatt.discoverServices() }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) { _state.update { it.copy(isConnected = false, connectionStatus = "Disconnected") }; synchronized(operationQueue) { isOperating = false } }
        }
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (_state.value.requestMtu) { gatt.requestMtu(247) } else { setupChannels(gatt) }
            }
        }
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) { setupChannels(gatt) }

        private fun setupChannels(gatt: BluetoothGatt) {
            updateDebugLog("Configuring channels...")
            for (s in gatt.services) {
                for (c in s.characteristics) {
                    val short = c.uuid.toString().substring(4, 8).lowercase()
                    // Use FEE3 for notifications as prioritized in the working script
                    if ((c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) && (short == "fee3" || short == "6487")) {
                        gatt.setCharacteristicNotification(c, true)
                        c.getDescriptor(CLIENT_CONFIG_DESCRIPTOR)?.let { 
                            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            enqueueOperation(GattOperation.WriteDescriptor(it, it.value)) 
                        }
                    }
                    if (c.uuid == BATTERY_CHAR) enqueueOperation(GattOperation.ReadCharacteristic(c))
                }
            }
            updateDebugLog("Native pipes ready.")
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, d: BluetoothGattDescriptor, s: Int) { synchronized(operationQueue) { isOperating = false }; doNextOperation() }
        override fun onCharacteristicWrite(gatt: BluetoothGatt, c: BluetoothGattCharacteristic, s: Int) { synchronized(operationQueue) { isOperating = false }; doNextOperation() }
        override fun onCharacteristicRead(gatt: BluetoothGatt, c: BluetoothGattCharacteristic, s: Int) { if (s == BluetoothGatt.GATT_SUCCESS) handleData(c.uuid, c.value); synchronized(operationQueue) { isOperating = false }; doNextOperation() }
        override fun onCharacteristicChanged(gatt: BluetoothGatt, c: BluetoothGattCharacteristic) { handleData(c.uuid, c.value) }
    }

    private fun handleData(uuid: UUID, data: ByteArray) {
        when (uuid) {
            BATTERY_CHAR -> { val b = data[0].toInt() and 0xFF; _state.update { it.copy(battery = b) }; saveToDb(battery = b) }
            HEART_RATE_CHAR -> parseStandardHeartRate(data)?.let { _state.update { s -> s.copy(heartRate = it) }; saveToDb(heartRate = it) }
            else -> { parseActivityPacket(data); parseKospetPacket(data) }
        }
    }

    private fun saveToDb(battery: Int? = null, heartRate: Int? = null, steps: Int? = null, distance: Int? = null, calories: Int? = null) {
        managerScope.launch { healthDao.insert(HealthEntry(battery = battery, heartRate = heartRate, steps = steps, distance = distance, calories = calories)) }
    }

    private fun parseStandardHeartRate(data: ByteArray): Int? {
        if (data.isEmpty()) return null
        return if ((data[0].toInt() and 0x01) != 0) (if (data.size < 3) null else (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8))
        else (if (data.size < 2) null else data[1].toInt() and 0xFF)
    }

    private fun parseActivityPacket(data: ByteArray) {
        val b = if (data.size == 10 && data[0] == 0x07.toByte()) data.copyOfRange(1, 10) else data
        if (b.size == 9) {
            val s = (b[1].toInt() and 0xFF) or ((b[2].toInt() and 0xFF) shl 8)
            val d = (b[3].toInt() and 0xFF) or ((b[4].toInt() and 0xFF) shl 8)
            val c = (b[6].toInt() and 0xFF) or ((b[7].toInt() and 0xFF) shl 8)
            _state.update { it.copy(steps = s, distance = d, calories = c) }; saveToDb(steps = s, distance = d, calories = c)
        }
    }

    private fun parseKospetPacket(data: ByteArray) {
        val b = data
        if (b.size >= 15 && startsWith(b, byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x20, 0x0F.toByte(), 0x33.toByte(), 0x01.toByte()))) {
            val s = (b[6].toInt() and 0xFF) or ((b[7].toInt() and 0xFF) shl 8) or ((b[8].toInt() and 0xFF) shl 16)
            val d = (b[9].toInt() and 0xFF) or ((b[10].toInt() and 0xFF) shl 8) or ((b[11].toInt() and 0xFF) shl 16)
            val c = (b[12].toInt() and 0xFF) or ((b[13].toInt() and 0xFF) shl 8) or ((b[14].toInt() and 0xFF) shl 16)
            _state.update { it.copy(steps = s, distance = d, calories = c) }; saveToDb(steps = s, distance = d, calories = c)
        }
        if (b.size >= 15 && startsWith(b, byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x20, 0x1E.toByte(), 0x33.toByte(), 0x04.toByte()))) {
            _state.update { it.copy(sleepMinutes = (b[6].toInt() and 0xFF) or ((b[7].toInt() and 0xFF) shl 8), deepSleepMinutes = (b[8].toInt() and 0xFF) or ((b[9].toInt() and 0xFF) shl 8), lightSleepMinutes = (b[10].toInt() and 0xFF) or ((b[11].toInt() and 0xFF) shl 8)) }
        }
    }

    private fun startsWith(d: ByteArray, p: ByteArray): Boolean { if (d.size < p.size) return false; for (i in p.indices) if (d[i] != p[i]) return false; return true }
}
