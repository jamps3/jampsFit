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
    val activityCount: Int? = null,
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
    val protocolHeader: String = "FE EA 20",
    val payloadLengthOnly: Boolean = false
)

@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class WatchManager(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val healthDao = db.healthDao()
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
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
    private val operationQueue: Queue<GattOperation> = LinkedList()
    private var isOperating = false
    private var lastOpTime = 0L
    private var isConfigured = false
    private var logBuffer = mutableListOf<String>()
    private var lastActivitySeq: Int? = null
    private val recentActivityPayloads = ArrayDeque<String>()
    private val recentActivityPayloadSet = mutableSetOf<String>()
    private val recentFee1Payloads = ArrayDeque<String>()
    private val recentFee1PayloadSet = mutableSetOf<String>()

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
        private val FEA1_CHAR = UUID.fromString("0000fea1-0000-1000-8000-00805f9b34fb")
        private val FEE2_WRITE = UUID.fromString("0000fee2-0000-1000-8000-00805f9b34fb")
        private val FEE3_NOTIFY = UUID.fromString("0000fee3-0000-1000-8000-00805f9b34fb")
        private val DATA_CHAR_UUID = UUID.fromString("00006387-3c17-d293-8e48-14fe2e4da212")
        private val SKIP_NOTIFY_CHAR = UUID.fromString("00002a05-0000-1000-8000-00805f9b34fb")
        private val CLIENT_CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    init {
        updateDebugLog("WatchManager build: passive-listen/no-mtu")
    }

    fun findWatch() {
        // Find My Watch ALWAYS uses 20-series protocol on 6387 (15-byte wrapper)
        val bytes = ByteArray(15) { 0 }
        bytes[0] = 0xFE.toByte(); bytes[1] = 0xEA.toByte(); bytes[2] = 0x20.toByte()
        bytes[3] = 0x0F.toByte(); bytes[4] = 0x00.toByte(); bytes[5] = 0x36.toByte()
        bytes[7] = 0xDA.toByte(); bytes[8] = 0x01.toByte()
        bytes[11] = 0x11.toByte(); bytes[12] = 0x03.toByte()
        bytes[13] = 0x20.toByte(); bytes[14] = 0x31.toByte()
        enqueueOperation(GattOperation.WriteCharacteristic(DATA_CHAR_UUID, bytes))
        updateDebugLog("Finding watch (Native 6387)...")
    }

    fun syncTime() {
        // MATCH WORKING BIG ENDIAN LOGIC on FEE2
        val tz = TimeZone.getDefault()
        val now = (System.currentTimeMillis() + tz.getOffset(System.currentTimeMillis())) / 1000
        val packet = ByteArray(10)
        packet[0] = 0xFE.toByte(); packet[1] = 0xEA.toByte(); packet[2] = 0x10.toByte()
        packet[3] = 0x09.toByte(); packet[4] = 0x31.toByte()
        packet[5] = ((now shr 24) and 0xFF).toByte(); packet[6] = ((now shr 16) and 0xFF).toByte()
        packet[7] = ((now shr 8) and 0xFF).toByte(); packet[8] = (now and 0xFF).toByte()
        packet[9] = 0x08.toByte()
        enqueueOperation(GattOperation.WriteCharacteristic(FEE2_WRITE, packet))
        updateDebugLog("Syncing clock (Big Endian FEE2)...")
    }

    fun queryHealth() {
        updateDebugLog("Health query disabled; listening for watch activity packets.")
        readBattery()
    }

    fun syncHealth() {
        queryHealth()
    }

    fun clearQueue() {
        synchronized(operationQueue) { operationQueue.clear(); isOperating = false; updateDebugLog("Queue cleared.") }
    }

    fun sendNotification(title: String, text: String, cmd: Int = 0x08, type: Int = 0x01) {
        // USE STABLE FEE2 PIPE with Header 10
        val tB = title.take(15).toByteArray(Charsets.UTF_8)
        val mB = text.take(15).toByteArray(Charsets.UTF_8)
        val payload = ByteArray(1 + 1 + tB.size + 1 + mB.size)
        payload[0] = type.toByte(); payload[1] = tB.size.toByte()
        System.arraycopy(tB, 0, payload, 2, tB.size)
        payload[2 + tB.size] = mB.size.toByte()
        System.arraycopy(mB, 0, payload, 3 + tB.size, mB.size)
        
        val totalLen = 5 + payload.size
        val packet = ByteArray(totalLen)
        packet[0] = 0xFE.toByte(); packet[1] = 0xEA.toByte(); packet[2] = 0x10.toByte()
        packet[3] = (totalLen - 1).toByte(); packet[4] = cmd.toByte()
        System.arraycopy(payload, 0, packet, 5, payload.size)
        
        enqueueOperation(GattOperation.WriteCharacteristic(FEE2_WRITE, packet))
        updateDebugLog("Sent Notif (Stable FEE2).")
    }

    fun sendExperimentalNotification() {
        if (!_state.value.isConnected) {
            updateDebugLog("Experimental notification skipped: watch is not connected.")
            return
        }

        managerScope.launch {
            updateDebugLog("Experimental notification: log-derived 6387 sequence starting...")
            sendNativeRaw(byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x20.toByte(), 0x06.toByte(), 0xB4.toByte(), 0x00.toByte()))
            delay(180)
            sendNativeRaw(byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x20.toByte(), 0x06.toByte(), 0xB4.toByte(), 0x12.toByte()))
            delay(180)
            sendNativeRaw(byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x20.toByte(), 0x06.toByte(), 0xB4.toByte(), 0x10.toByte()))
            delay(180)
            sendNativeRaw(byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x20.toByte(), 0x06.toByte(), 0xB4.toByte(), 0x20.toByte()))
            delay(180)
            sendNativeRaw(byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x20.toByte(), 0x09.toByte(), 0x12.toByte(), 0xA8.toByte(), 0x4B.toByte(), 0x29.toByte(), 0x00.toByte()))
            delay(320)
            sendNativeRaw(byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x20.toByte(), 0x06.toByte(), 0xF1.toByte(), 0x00.toByte()))
            delay(180)
            sendNativeRaw(buildExperimentalNotificationPacket("jampsFit: passive data restored. Test notification."))
            updateDebugLog("Experimental notification sequence sent.")
        }
    }

    private fun buildExperimentalNotificationPacket(message: String): ByteArray {
        val maxTextBytes = 62
        val textBytes = message.toByteArray(Charsets.UTF_8).copyOfRangeSafe(0, maxTextBytes)
        val packet = ByteArray(6 + textBytes.size)
        packet[0] = 0xFE.toByte()
        packet[1] = 0xEA.toByte()
        packet[2] = 0x20.toByte()
        packet[3] = packet.size.toByte()
        packet[4] = 0x41.toByte()
        packet[5] = 0x80.toByte()
        System.arraycopy(textBytes, 0, packet, 6, textBytes.size)
        return packet
    }

    private fun ByteArray.copyOfRangeSafe(fromIndex: Int, maxLength: Int): ByteArray {
        return copyOfRange(fromIndex, size.coerceAtMost(fromIndex + maxLength))
    }

    private fun formatPacket(cmd: Byte, payload: ByteArray, forceLen: Int? = null): ByteArray {
        val headerParts = _state.value.protocolHeader.split(" ")
        val is10Series = headerParts.getOrNull(2) == "10"
        val totalLen = forceLen ?: (5 + payload.size)
        val packet = ByteArray(totalLen) { 0 }
        packet[0] = headerParts[0].toInt(16).toByte()
        packet[1] = headerParts[1].toInt(16).toByte()
        packet[2] = headerParts[2].toInt(16).toByte()
        packet[3] = (if (is10Series) totalLen - 1 else totalLen).toByte()
        packet[4] = cmd
        System.arraycopy(payload, 0, packet, 5, payload.size.coerceAtMost(totalLen - 5))
        return packet
    }

    fun sendRawTest(hex: String, useAltChar: Boolean = false) {
        val bytes = hex.split(" ").filter { it.isNotBlank() }.map { it.toInt(16).toByte() }.toByteArray()
        enqueueOperation(GattOperation.WriteCharacteristic(if (useAltChar) DATA_CHAR_UUID else null, bytes))
    }

    private fun sendNativeRaw(bytes: ByteArray) {
        enqueueOperation(GattOperation.WriteCharacteristic(DATA_CHAR_UUID, bytes))
    }

    private fun sendNativeQuery(cmd: Int, arg: Int? = null) {
        val payloadSize = if (arg == null) 0 else 1
        val packet = ByteArray(5 + payloadSize)
        packet[0] = 0xFE.toByte()
        packet[1] = 0xEA.toByte()
        packet[2] = 0x20.toByte()
        packet[3] = packet.size.toByte()
        packet[4] = cmd.toByte()
        if (arg != null) packet[5] = arg.toByte()
        enqueueOperation(GattOperation.WriteCharacteristic(DATA_CHAR_UUID, packet))
    }

    fun readBattery() {
        val gatt = bluetoothGatt ?: return
        for (s in gatt.services) {
            val c = s.getCharacteristic(BATTERY_CHAR)
            if (c != null) { enqueueOperation(GattOperation.ReadCharacteristic(c)); return }
        }
    }

    fun startMeasurement(type: String) {
        val cmd = when (type) { "Heart Rate" -> 0x6D.toByte(); "SpO2" -> 0x6B.toByte(); "Blood Pressure" -> 0x69.toByte(); else -> return }
        enqueueOperation(GattOperation.WriteCharacteristic(null, formatPacket(cmd, byteArrayOf(0x01.toByte()))))
        _state.update { it.copy(activeMeasurement = type) }
    }

    fun stopMeasurement() {
        val cmd = when (_state.value.activeMeasurement) { "Heart Rate" -> 0x6D.toByte(); "SpO2" -> 0x6B.toByte(); "Blood Pressure" -> 0x69.toByte(); else -> return }
        enqueueOperation(GattOperation.WriteCharacteristic(null, formatPacket(cmd, byteArrayOf(0x00.toByte()))))
        _state.update { it.copy(activeMeasurement = null) }
    }

    fun updateShutterAction(a: String) { prefs.edit { putString("shutterAction", a) }; _state.update { it.copy(shutterAction = a) } }
    fun updateMusicAction(a: String) { prefs.edit { putString("musicAction", a) }; _state.update { it.copy(musicAction = a) } }
    fun updateCustomAction(b: String, a: String) {
        when (b) { "Play/Pause" -> { prefs.edit { putString("playPauseAction", a) }; _state.update { it.copy(playPauseAction = a) } }
        "Next" -> { prefs.edit { putString("nextAction", a) }; _state.update { it.copy(nextAction = a) } }
        "Previous" -> { prefs.edit { putString("prevAction", a) }; _state.update { it.copy(prevAction = a) } } }
    }

    fun toggleAutoStart(e: Boolean) { prefs.edit { putBoolean("autoStart", e) }; _state.update { it.copy(autoStart = e) } }
    fun toggleAutoConnect(e: Boolean) { prefs.edit { putBoolean("autoConnect", e) }; _state.update { it.copy(autoConnect = e) } }
    fun toggleNotifications(e: Boolean) { prefs.edit { putBoolean("notificationsEnabled", e) }; _state.update { it.copy(notificationsEnabled = e) } }
    fun updateBatteryThreshold(t: Int) { prefs.edit { putInt("batteryThreshold", t) }; _state.update { it.copy(batteryThreshold = t) } }
    fun updateProtocol(h: String, u: String, m: Boolean, p: Boolean) { _state.update { it.copy(protocolHeader = h, writeUuidShort = u, payloadLengthOnly = p) } }

    private fun updateDebugLog(msg: String) {
        Log.d(TAG, msg)
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logText = synchronized(logBuffer) {
            logBuffer.add("[$timestamp] $msg")
            while (logBuffer.size > 100) logBuffer.removeAt(0)
            logBuffer.toList().joinToString("\n")
        }
        _state.update { it.copy(debugLog = logText) }
    }

    private fun checkQueueTimeout() {
        if (isOperating && System.currentTimeMillis() - lastOpTime > 2500) {
            isOperating = false; doNextOperation()
        }
    }

    private fun enqueueOperation(op: GattOperation) { 
        checkQueueTimeout()
        synchronized(operationQueue) { operationQueue.add(op); if (!isOperating) doNextOperation() } 
    }

    private fun doNextOperation() {
        synchronized(operationQueue) {
            if (isOperating) return
            val operation = operationQueue.poll() ?: return
            isOperating = true; lastOpTime = System.currentTimeMillis()
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
            if (newState == BluetoothProfile.STATE_CONNECTED) { isConfigured = false; _state.update { it.copy(isConnected = true, connectionStatus = "Connected") }; gatt.discoverServices() }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) { _state.update { it.copy(isConnected = false, connectionStatus = "Disconnected") }; synchronized(operationQueue) { isOperating = false } }
        }
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                updateDebugLog("Services discovered; skipping MTU request")
                setupChannels(gatt)
            }
        }
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            updateDebugLog("Unexpected MTU callback ignored: mtu=$mtu status=$status")
        }
        private fun setupChannels(gatt: BluetoothGatt) {
            if (isConfigured) return
            isConfigured = true; updateDebugLog("Configuring channels...")
            for (s in gatt.services) {
                updateDebugLog("Service ${s.uuid.toString().substring(4, 8).uppercase()}")
                for (c in s.characteristics) {
                    val short = c.uuid.toString().substring(4, 8).lowercase()
                    val props = mutableListOf<String>()
                    if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) props.add("WRITE")
                    if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) props.add("WRITE_NR")
                    if (c.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) props.add("READ")
                    if (c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) props.add("NOTIFY")
                    if (c.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) props.add("INDICATE")
                    updateDebugLog("Char ${short.uppercase()} [${props.joinToString(",")}]")

                    if (c.uuid == SKIP_NOTIFY_CHAR) continue

                    val canNotify = (c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                    val canIndicate = (c.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                    if (canNotify || canIndicate) {
                        gatt.setCharacteristicNotification(c, true)
                        c.getDescriptor(CLIENT_CONFIG_DESCRIPTOR)?.let {
                            val value = if (canNotify) {
                                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            } else {
                                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                            }
                            enqueueOperation(GattOperation.WriteDescriptor(it, value))
                            updateDebugLog("Queued listen ${short.uppercase()}")
                        }
                    }
                    if (c.uuid == BATTERY_CHAR) enqueueOperation(GattOperation.ReadCharacteristic(c))
                }
            }
            updateDebugLog("Channels ready; listening for watch data.")
        }
        override fun onDescriptorWrite(gatt: BluetoothGatt, d: BluetoothGattDescriptor, s: Int) {
            updateDebugLog("Listen ${d.characteristic.uuid.toString().substring(4, 8).uppercase()} status=$s")
            synchronized(operationQueue) { isOperating = false }
            doNextOperation()
        }
        override fun onCharacteristicWrite(gatt: BluetoothGatt, c: BluetoothGattCharacteristic, s: Int) { 
            val hex = c.value?.joinToString("") { "%02X".format(it) } ?: "null"
            updateDebugLog("Write ${c.uuid.toString().substring(4, 8)}: $hex")
            synchronized(operationQueue) { isOperating = false }; doNextOperation() 
        }
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(gatt: BluetoothGatt, c: BluetoothGattCharacteristic, s: Int) { 
            if (s == BluetoothGatt.GATT_SUCCESS) { managerScope.launch { handleData(c.uuid, c.value) } }
            synchronized(operationQueue) { isOperating = false }; doNextOperation() 
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                managerScope.launch { handleData(c.uuid, value) }
            }
            synchronized(operationQueue) { isOperating = false }
            doNextOperation()
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, c: BluetoothGattCharacteristic) { 
            val hex = c.value?.joinToString("") { "%02X".format(it) } ?: ""
            logIncomingPacket(c.uuid, c.value)
            managerScope.launch { handleData(c.uuid, c.value) }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            logIncomingPacket(c.uuid, value)
            managerScope.launch { handleData(c.uuid, value) }
        }
    }

    private fun logIncomingPacket(uuid: UUID, data: ByteArray) {
        val short = uuid.toString().substring(4, 8).uppercase()
        val rawHex = data.joinToString(" ") { "%02X".format(it) }
        val msg = "RX $short raw=$rawHex"
        if (uuid == FEE1_CHAR && isActivityPayload(data)) {
            rememberRecentPayload(data.toHexKey(), recentFee1Payloads, recentFee1PayloadSet)
            updateDebugLog(msg)
            return
        }
        if (uuid == FEA1_CHAR && isFea1ActivityMirror(data)) {
            return
        }
        updateDebugLog(msg)
        addUnknownMessage(msg)
    }

    private fun addUnknownMessage(msg: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _state.update { it.copy(unknownMessages = (it.unknownMessages + "[$timestamp] $msg").takeLast(100)) }
    }

    private fun handleData(uuid: UUID, data: ByteArray) {
        when (uuid) {
            BATTERY_CHAR -> {
                if (data.isNotEmpty()) {
                    val b = data[0].toInt() and 0xFF
                    _state.update { it.copy(battery = b) }
                    saveToDb(battery = b)
                    updateDebugLog("Battery: $b%")
                }
            }
            HEART_RATE_CHAR -> parseStandardHeartRate(data)?.let { _state.update { s -> s.copy(heartRate = it) }; saveToDb(heartRate = it) }
            FEE1_CHAR -> {
                if (!parseActivityPacket(data, "FEE1")) parseKospetPacket(data)
            }
            FEA1_CHAR -> {
                if (isFea1ActivityMirror(data)) {
                    handleFea1ActivityMirror(data)
                } else if (!parseActivityPacket(data, "FEA1")) {
                    parseKospetPacket(data)
                }
            }
            else -> {
                parseKospetPacket(data)
                parseWrappedActivityPacket(data)
            }
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

    private fun isFea1ActivityMirror(data: ByteArray): Boolean = data.size == 10 && data[0] == 0x07.toByte()

    private fun isActivityPayload(data: ByteArray): Boolean = data.size == 9

    private fun handleFea1ActivityMirror(data: ByteArray): Boolean {
        val b = data.copyOfRange(1, 10)
        val seq = b[0].toInt() and 0xFF
        if (hasRecentPayload(b.toHexKey(), recentFee1PayloadSet)) return true
        if (lastActivitySeq == seq) return true
        updateDebugLog("FEA1 activity mirror used because seq=$seq was not seen on FEE1.")
        return parseActivityPacket(b, "FEA1 mirror")
    }

    private fun parseActivityPacket(data: ByteArray, source: String): Boolean {
        val b = normalizeActivityPayload(data) ?: return false
        val payloadKey = b.toHexKey()
        if (isDuplicateActivityPayload(payloadKey)) return true

        val seq = b[0].toInt() and 0xFF
        val activityCount = (b[1].toInt() and 0xFF) or ((b[2].toInt() and 0xFF) shl 8)
        val distance = (b[3].toInt() and 0xFF) or ((b[4].toInt() and 0xFF) shl 8)
        val calories = (b[6].toInt() and 0xFF) or ((b[7].toInt() and 0xFF) shl 8)
        lastActivitySeq = seq
        _state.update { it.copy(activityCount = activityCount, distance = distance, calories = calories) }
        saveToDb(distance = distance, calories = calories)
        updateDebugLog("Activity live[$source]: seq=$seq activityCount=$activityCount distance=${distance}m calories=$calories")
        return true
    }

    private fun isDuplicateActivityPayload(payloadKey: String): Boolean = synchronized(recentActivityPayloads) {
        rememberRecentPayload(payloadKey, recentActivityPayloads, recentActivityPayloadSet)
    }

    private fun rememberRecentPayload(key: String, queue: ArrayDeque<String>, set: MutableSet<String>): Boolean = synchronized(queue) {
        if (!set.add(key)) return@synchronized true
        queue.addLast(key)
        while (queue.size > 32) set.remove(queue.removeFirst())
        false
    }

    private fun hasRecentPayload(key: String, set: Set<String>): Boolean {
        return synchronized(recentFee1Payloads) { key in set }
    }

    private fun parseWrappedActivityPacket(data: ByteArray): Boolean {
        if (data.size >= 14 && data[4] == 0x07.toByte()) {
            return parseActivityPacket(data.copyOfRange(5, 14), "wrapped")
        }
        return false
    }

    private fun normalizeActivityPayload(data: ByteArray): ByteArray? {
        return when {
            isFea1ActivityMirror(data) -> data.copyOfRange(1, 10)
            data.size == 9 -> data
            else -> null
        }
    }

    private fun parseKospetPacket(data: ByteArray) {
        val b = data
        if (startsWith(b, byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x20)) && b.size > 5 && b[4] == 0x5A.toByte()) {
            parseDeviceInfoPacket(b)
        }
        if (b.size >= 15 && startsWith(b, byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x20, 0x0F.toByte(), 0x33.toByte()))) {
            parseDailyTotalsPacket(b)
        }
        if (b.size >= 30 && startsWith(b, byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x20, 0x1E.toByte(), 0x33.toByte(), 0x04.toByte()))) {
            parseSleepPacket(b)
        }
        if (b.size >= 54 && startsWith(b, byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x20, 0x36.toByte(), 0x59.toByte()))) {
            parseHourlyActivityPacket(b)
        }
    }

    private fun parseDeviceInfoPacket(b: ByteArray) {
        val infoType = b[5].toInt() and 0xFF
        if (infoType == 0x00 && b.size > 6) {
            val name = String(b.copyOfRange(6, b.size)).trim { it <= ' ' }
            updateDebugLog("Device info: $name")
        } else if (infoType == 0x01 && b.size > 6) {
            val firmware = String(b.copyOfRange(6, b.size)).trim { it <= ' ' }
            prefs.edit { putString("firmwareVersion", firmware) }
            _state.update { it.copy(firmwareVersion = firmware) }
            updateDebugLog("Firmware: $firmware")
        }
    }

    private fun parseDailyTotalsPacket(b: ByteArray) {
        val dayOffset = b[5].toInt() and 0xFF
        val steps = readUInt24LE(b, 6)
        val distance = readUInt24LE(b, 9)
        val calories = readUInt24LE(b, 12)
        if (dayOffset == 0x01 || _state.value.steps == null) {
            _state.update { it.copy(steps = steps, distance = distance, calories = calories) }
            saveToDb(steps = steps, distance = distance, calories = calories)
        }
        updateDebugLog("Daily totals[$dayOffset]: steps=$steps distance=${distance}m calories=$calories")
    }

    private fun parseHourlyActivityPacket(b: ByteArray) {
        val bucket = b[5].toInt() and 0xFF
        var steps = 0
        var distance = 0
        var calories = 0
        var offset = 6
        while (offset + 5 < b.size) {
            steps += readUInt16LE(b, offset)
            distance += readUInt16LE(b, offset + 2)
            calories += readUInt16LE(b, offset + 4)
            offset += 6
        }
        if (steps > 0 && _state.value.steps == null) {
            _state.update { it.copy(steps = steps, distance = distance, calories = calories) }
            saveToDb(steps = steps, distance = distance, calories = calories)
        }
        updateDebugLog("Activity buckets[$bucket]: steps=$steps distance=${distance}m calories=$calories")
    }

    private fun parseSleepPacket(b: ByteArray) {
        var total = 0
        var deep = 0
        var light = 0
        var offset = 6
        while (offset + 2 < b.size) {
            val sleepType = b[offset].toInt() and 0xFF
            val minutes = ((b[offset + 1].toInt() and 0xFF) * 60) + (b[offset + 2].toInt() and 0xFF)
            total += minutes
            if (sleepType == 0x01) deep += minutes else light += minutes
            offset += 3
        }
        _state.update { it.copy(sleepMinutes = total, deepSleepMinutes = deep, lightSleepMinutes = light) }
        updateDebugLog("Sleep summary: total=${total}m deep=${deep}m light=${light}m")
    }

    private fun readUInt16LE(b: ByteArray, offset: Int): Int {
        if (offset + 1 >= b.size) return 0
        return (b[offset].toInt() and 0xFF) or ((b[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readUInt24LE(b: ByteArray, offset: Int): Int {
        if (offset + 2 >= b.size) return 0
        return (b[offset].toInt() and 0xFF) or ((b[offset + 1].toInt() and 0xFF) shl 8) or ((b[offset + 2].toInt() and 0xFF) shl 16)
    }

    private fun ByteArray.toHexKey(): String = joinToString("") { "%02X".format(it) }

    private fun startsWith(d: ByteArray, p: ByteArray): Boolean { if (d.size < p.size) return false; for (i in p.indices) if (d[i] != p[i]) return false; return true }
}
