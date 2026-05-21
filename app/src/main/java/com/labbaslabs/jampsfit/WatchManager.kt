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
    val isServiceRunning: Boolean = false,
    val sleepMinutes: Int? = null,
    val deepSleepMinutes: Int? = null,
    val lightSleepMinutes: Int? = null,
    val isFindingPhone: Boolean = false,
    val volumeSteps: Int = 1,
    val batteryHistory: List<com.labbaslabs.jampsfit.database.HistoryPoint> = emptyList(),
    val heartRateHistory: List<com.labbaslabs.jampsfit.database.HistoryPoint> = emptyList(),
    val spo2History: List<com.labbaslabs.jampsfit.database.HistoryPoint> = emptyList(),
    val bpHistory: List<com.labbaslabs.jampsfit.database.HealthEntry> = emptyList(),
    val stepsHistory: List<com.labbaslabs.jampsfit.database.HistoryPoint> = emptyList(),
    val distanceHistory: List<com.labbaslabs.jampsfit.database.HistoryPoint> = emptyList(),
    val caloriesHistory: List<com.labbaslabs.jampsfit.database.HistoryPoint> = emptyList(),
    val activityHistory: List<com.labbaslabs.jampsfit.database.HistoryPoint> = emptyList(),
    val last24hStats: List<HealthEntry> = emptyList(),
    val dailyStats: List<HealthEntry> = emptyList(),
    val weeklyStats: List<HealthEntry> = emptyList(),
    val monthlyStats: List<HealthEntry> = emptyList(),
    val alarmSettings: List<WatchAlarm> = emptyList(),
    val stepGoalSetting: Int? = null,
    val autoLockSecondsSetting: Int? = null,
    val writeUuidShort: String = "6387",
    val protocolHeader: String = "FE EA 20",
    val payloadLengthOnly: Boolean = false,
)

data class WatchAlarm(
    val slot: Int,
    val enabled: Boolean,
    val mode: Int,
    val hour: Int,
    val minute: Int,
    val repeatMask: Int
)

@SuppressLint("MissingPermission")
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
        volumeSteps = prefs.getInt("volumeSteps", 1),
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
    private var userRequestedDisconnect = false
    private var reconnectJob: Job? = null
    private var scanWatchdogJob: Job? = null
    private var logBuffer = mutableListOf<String>()
    private var lastActivitySeq: Int? = null
    private val recentActivityPayloads = ArrayDeque<String>()
    private val recentActivityPayloadSet = mutableSetOf<String>()
    private val recentFee1Payloads = ArrayDeque<String>()
    private val recentFee1PayloadSet = mutableSetOf<String>()

    init {
        observeHistory()
    }

    private fun observeHistory() {
        managerScope.launch {
            healthDao.getBatteryHistory().collect { history ->
                _state.update { it.copy(batteryHistory = history.reversed()) }
            }
        }
        managerScope.launch {
            healthDao.getHeartRateHistory().collect { history ->
                _state.update { it.copy(heartRateHistory = history.reversed()) }
            }
        }
        managerScope.launch {
            healthDao.getSpO2History().collect { history ->
                _state.update { it.copy(spo2History = history.reversed()) }
            }
        }
        managerScope.launch {
            healthDao.getBloodPressureHistory().collect { history ->
                _state.update { it.copy(bpHistory = history.reversed()) }
            }
        }
        managerScope.launch {
            healthDao.getStepsHistory().collect { history ->
                _state.update { it.copy(stepsHistory = history.reversed()) }
            }
        }
        managerScope.launch {
            healthDao.getDistanceHistory().collect { history ->
                _state.update { it.copy(distanceHistory = history.reversed()) }
            }
        }
        managerScope.launch {
            healthDao.getActivityHistory().collect { history ->
                _state.update { it.copy(activityHistory = history.reversed()) }
            }
        }
        managerScope.launch {
            healthDao.getCaloriesHistory().collect { history ->
                _state.update { it.copy(caloriesHistory = history.reversed()) }
            }
        }
        managerScope.launch {
            healthDao.getLast24hStats().collect { stats ->
                _state.update { it.copy(last24hStats = stats.reversed()) }
            }
        }
        managerScope.launch {
            healthDao.getDailyStats().collect { stats ->
                _state.update { it.copy(dailyStats = stats.reversed()) }
            }
        }
        managerScope.launch {
            healthDao.getWeeklyStats().collect { stats ->
                _state.update { it.copy(weeklyStats = stats.reversed()) }
            }
        }
        managerScope.launch {
            healthDao.getMonthlyStats().collect { stats ->
                _state.update { it.copy(monthlyStats = stats.reversed()) }
            }
        }
        managerScope.launch {
            healthDao.getAllUnknownPackets().collect { history ->
                _state.update { it.copy(unknownMessages = history) }
            }
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
        if (!_state.value.isConnected) {
            updateDebugLog("Find My Watch skipped: watch is not connected.")
            return
        }
        val packet = nativePacket(0x61)
        enqueueOperation(GattOperation.WriteCharacteristic(FEE2_WRITE, packet))
        updateDebugLog("Find My Watch via FEE2: ${packet.toHexString()}")
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
        val message = listOf(title.trim(), text.trim())
            .filter { it.isNotBlank() }
            .joinToString(": ")
            .ifBlank { "jampsFit" }
        sendNativeNotification41(message, maxBytes = 238, logLabel = "mirrored")
    }

    fun sendNotificationProbe(kind: String) {
        if (!_state.value.isConnected) {
            updateDebugLog("Notification probe skipped: watch is not connected.")
            return
        }
        when (kind) {
            "legacy-short" -> sendNotification("jampsFit", "Legacy short", 0x08, 0x01)
            "legacy-call" -> sendNotification("Call", "Ada Lovelace", 0x08, 0x02)
            "20-08-type1" -> sendNativeNotification08("jampsFit", "Type 1 short", 0x01, checksum = false)
            "20-08-type2" -> sendNativeNotification08("Phone", "Type 2 message", 0x02, checksum = false)
            "20-08-type3" -> sendNativeNotification08("SMS", "Type 3 message", 0x03, checksum = false)
            "20-08-type5" -> sendNativeNotification08("App", "Type 5 message", 0x05, checksum = false)
            "20-08-csum1" -> sendNativeNotification08("jampsFit", "Checksum type 1", 0x01, checksum = true)
            "20-08-csum3" -> sendNativeNotification08("SMS", "Checksum type 3", 0x03, checksum = true)
            "20-41-tiny" -> sendNativeNotification41("jampsFit tiny 41")
            "20-41-len20" -> sendNativeNotification41("Len20 abcdefghijklmn", maxBytes = 20, logLabel = "len20")
            "20-41-len40" -> sendNativeNotification41("Len40 abcdefghijklmnopqrstuvwxyz ABCDEFGHIJ", maxBytes = 40, logLabel = "len40")
            "20-41-len60" -> sendNativeNotification41("Len60 abcdefghijklmnopqrstuvwxyz ABCDEFGHIJKLMNOPQRSTUVWXYZ 1234567890", maxBytes = 60, logLabel = "len60")
            "20-41-len80" -> sendNativeNotification41("Len80 abcdefghijklmnopqrstuvwxyz ABCDEFGHIJKLMNOPQRSTUVWXYZ 1234567890 notification test tail", maxBytes = 80, logLabel = "len80")
            "20-41-len120" -> sendNativeNotification41("Len120 abcdefghijklmnopqrstuvwxyz ABCDEFGHIJKLMNOPQRSTUVWXYZ 1234567890 notification test tail with extra words for watch limit probing", maxBytes = 120, logLabel = "len120")
            "20-41-len160" -> sendNativeNotification41("Len160 abcdefghijklmnopqrstuvwxyz ABCDEFGHIJKLMNOPQRSTUVWXYZ 1234567890 notification test tail with extra words for watch limit probing and still more plain ascii content", maxBytes = 160, logLabel = "len160")
            "20-41-len200" -> sendNativeNotification41("Len200 abcdefghijklmnopqrstuvwxyz ABCDEFGHIJKLMNOPQRSTUVWXYZ 1234567890 notification test tail with extra words for watch limit probing and still more plain ascii content for a larger direct packet boundary test", maxBytes = 200, logLabel = "len200")
            "20-41-marker40" -> sendNativeNotification41(buildMarkerMessage(40), maxBytes = 40, logLabel = "marker40")
            "20-41-marker60" -> sendNativeNotification41(buildMarkerMessage(60), maxBytes = 60, logLabel = "marker60")
            "20-41-marker80" -> sendNativeNotification41(buildMarkerMessage(80), maxBytes = 80, logLabel = "marker80")
            "20-41-marker100" -> sendNativeNotification41(buildMarkerMessage(100), maxBytes = 100, logLabel = "marker100")
            "20-41-marker140" -> sendNativeNotification41(buildMarkerMessage(140), maxBytes = 140, logLabel = "marker140")
            "20-41-marker180" -> sendNativeNotification41(buildMarkerMessage(180), maxBytes = 180, logLabel = "marker180")
            "20-41-marker220" -> sendNativeNotification41(buildMarkerMessage(220), maxBytes = 220, logLabel = "marker220")
            "20-41-marker232" -> sendNativeNotification41(buildMarkerMessage(232), maxBytes = 232, logLabel = "marker232")
            "20-41-marker236" -> sendNativeNotification41(buildMarkerMessage(236), maxBytes = 236, logLabel = "marker236")
            "20-41-marker238" -> sendNativeNotification41(buildMarkerMessage(238), maxBytes = 238, logLabel = "marker238")
            "20-41-marker239" -> sendNativeNotification41(buildMarkerMessage(239), maxBytes = 239, logLabel = "marker239")
            "20-41-marker240" -> sendNativeNotification41(buildMarkerMessage(240), maxBytes = 240, logLabel = "marker240")
            "20-41-marker249" -> sendNativeNotification41(buildMarkerMessage(249), maxBytes = 249, logLabel = "marker249")
            else -> updateDebugLog("Unknown notification probe: $kind")
        }
    }

    private fun sendNativeNotification08(title: String, text: String, type: Int, checksum: Boolean) {
        val titleBytes = title.take(18).toByteArray(Charsets.UTF_8)
        val textBytes = text.take(40).toByteArray(Charsets.UTF_8)
        val payload = mutableListOf<Int>()
        payload.add(type and 0xFF)
        payload.add(titleBytes.size)
        titleBytes.forEach { payload.add(it.toInt() and 0xFF) }
        payload.add(textBytes.size)
        textBytes.forEach { payload.add(it.toInt() and 0xFF) }
        val base = nativePacket(0x08, *payload.toIntArray())
        val packet = if (checksum) base.withTrailingSumChecksum() else base
        enqueueOperation(GattOperation.WriteCharacteristic(FEE2_WRITE, packet))
        updateDebugLog("Notification probe 20/08 type=$type checksum=$checksum -> ${packet.toHexString()}")
    }

    private fun sendNativeNotification41(message: String, maxBytes: Int = 40, logLabel: String = "tiny") {
        val textBytes = message.toByteArray(Charsets.UTF_8).copyOfRangeSafe(0, maxBytes.coerceIn(1, 249))
        val payload = IntArray(1 + textBytes.size)
        payload[0] = 0x80
        textBytes.forEachIndexed { index, byte -> payload[index + 1] = byte.toInt() and 0xFF }
        val packet = nativePacket(0x41, *payload)
        enqueueOperation(GattOperation.WriteCharacteristic(FEE2_WRITE, packet))
        updateDebugLog("Notification 20/41 $logLabel bytes=${textBytes.size} -> ${packet.toHexString()}")
    }

    private fun buildMarkerMessage(length: Int): String {
        val safeLength = length.coerceAtLeast(8)
        val prefix = "M$safeLength "
        val suffix = " END"
        val bodyLength = (safeLength - prefix.length - suffix.length).coerceAtLeast(0)
        val digits = buildString {
            while (this.length < bodyLength) append("0123456789")
        }.take(bodyLength)
        return prefix + digits + suffix
    }

    fun sendExperimentalNotification() {
        updateDebugLog("Experimental notification disabled: use Android notification mirroring path; this button rebooted the watch.")
    }

    fun prepareDaFitSession() {
        updateDebugLog("Find prep disabled: the 84/B4 cluster rebooted this watch outside Da Fit startup state.")
    }

    fun prepareAndFindWatch() {
        updateDebugLog("Prep + Find disabled: Find prep rebooted this watch outside Da Fit startup state.")
    }

    fun sendStartupPreamblePhase1() {
        updateDebugLog("Startup phase 1 disabled: old test wrote Da Fit FEE2 traffic to the wrong characteristic.")
    }

    fun sendStartupPreamblePhase2() {
        updateDebugLog("Startup phase 2 disabled: old test wrote Da Fit FEE2 traffic to the wrong characteristic.")
    }

    private suspend fun sendFindPrepCluster() {
        sendNativeRaw(nativePacket(0x84))
        delay(180)
        sendNativeRaw(nativePacket(0xB4, 0x00))
        delay(180)
        sendNativeRaw(nativePacket(0xB4, 0x12))
        delay(180)
        sendNativeRaw(nativePacket(0xB4, 0x10))
        delay(180)
        sendNativeRaw(nativePacket(0xB4, 0x20))
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

    fun setAutoLockSeconds(seconds: Int) {
        if (!_state.value.isConnected) {
            updateDebugLog("Auto-lock test skipped: watch is not connected.")
            return
        }
        val safeSeconds = seconds.coerceIn(5, 60)
        val packet = nativePacket(0x7D, safeSeconds)
        enqueueOperation(GattOperation.WriteCharacteristic(FEE2_WRITE, packet))
        _state.update { it.copy(autoLockSecondsSetting = safeSeconds) }
        updateDebugLog("Auto-lock via FEE2: ${safeSeconds}s -> ${packet.toHexString()}")
    }

    fun setStepGoal(goal: Int) {
        if (!_state.value.isConnected) {
            updateDebugLog("Step-goal test skipped: watch is not connected.")
            return
        }
        val safeGoal = (goal / 1000).coerceIn(2, 35) * 1000
        val packet = nativePacket(0x16, 0x00, (safeGoal shr 8) and 0xFF, safeGoal and 0xFF)
        enqueueOperation(GattOperation.WriteCharacteristic(FEE2_WRITE, packet))
        _state.update { it.copy(stepGoalSetting = safeGoal) }
        updateDebugLog("Step goal via FEE2: $safeGoal -> ${packet.toHexString()}")
    }

    fun setWeatherCity(city: String) {
        if (!_state.value.isConnected) {
            updateDebugLog("Weather test skipped: watch is not connected.")
            return
        }
        val safeCity = city.trim().ifBlank { "Joensuu" }.take(12)
        managerScope.launch {
            updateDebugLog("Weather city via FEE2 '$safeCity' sequence starting...")
            val cityLower = safeCity.lowercase(Locale.US)
            val cityAscii = cityLower.toByteArray(Charsets.UTF_8)
            val cityUtf16 = cityLower.toByteArray(Charsets.UTF_16LE)
            val displayAscii = safeCity.toByteArray(Charsets.UTF_8)

            sendFee2NativeRaw(nativePacket(0xB9, 0x19, 0x00))
            delay(180)
            sendFee2NativeRaw(nativePacket(0x43, 0x00, 0x01, 0x07, 0x00, 0x20, 0x00, 0x20, 0x00, 0x20, 0x00, *cityUtf16.map { it.toInt() and 0xFF }.toIntArray()))
            delay(180)
            sendFee2NativeRaw(nativePacket(0x42, 0x03, 0x0E, 0x07, 0x00, 0x0E, 0x06, 0x03, 0x13, 0x0A, 0x03, 0x10, 0x0C, 0x00, 0x0F, 0x0A, 0x03, 0x0D, 0x09, 0x03, 0x09, 0x07))
            delay(180)
            sendFee2NativeRaw(nativePacket(0xB5, 0x00, 0x01, 0x07, 0x00, 0x00, 0x03, 0x38, 0x15, 0x39, *cityAscii.map { it.toInt() and 0xFF }.toIntArray()))
            delay(180)
            val cityPacket = nativePacket(0x45, *displayAscii.map { it.toInt() and 0xFF }.toIntArray())
            sendFee2NativeRaw(cityPacket)
            updateDebugLog("Weather city via FEE2 '$safeCity' sent -> ${cityPacket.toHexString()}")
        }
    }

    fun sendWeatherForecastSample() {
        if (!_state.value.isConnected) {
            updateDebugLog("Weather forecast test skipped: watch is not connected.")
            return
        }
        managerScope.launch {
            updateDebugLog("Weather forecast sample via FEE2 starting...")
            sendFee2NativeRaw(nativePacket(0xB9, 0x19, 0x00))
            delay(180)
            // Captures suggest command 0x42 is seven forecast triples: condition, high C, low C.
            val forecastPacket = nativePacket(
                0x42,
                0x00, 0x1C, 0x12,
                0x01, 0x1A, 0x10,
                0x02, 0x18, 0x0E,
                0x03, 0x16, 0x0C,
                0x04, 0x14, 0x0A,
                0x05, 0x12, 0x08,
                0x06, 0x10, 0x06
            )
            sendFee2NativeRaw(forecastPacket)
            updateDebugLog("Weather forecast sample via FEE2 sent -> ${forecastPacket.toHexString()}")
        }
    }

    fun sendWeatherCurrentProbe(kind: String) {
        if (!_state.value.isConnected) {
            updateDebugLog("Weather current probe skipped: watch is not connected.")
            return
        }
        val packet = when (kind) {
            "43-cold" -> nativePacket(0x43, 0x00, 0x01, 0x07, 0x00, 0x05, 0x00, 0x03, 0x00, 0xFF, 0xFF)
            "43-warm" -> nativePacket(0x43, 0x00, 0x01, 0x07, 0x00, 0x17, 0x00, 0x15, 0x00, 0x0F, 0x00)
            "b5-warm" -> nativePacket(0xB5, 0x00, 0x01, 0x07, 0x00, 0x00, 0x03, 0x17, 0x15, 0x0F, 0x6A, 0x6F, 0x65, 0x6E, 0x73, 0x75, 0x75)
            else -> {
                updateDebugLog("Unknown weather current probe: $kind")
                return
            }
        }
        sendFee2NativeRaw(packet)
        updateDebugLog("Weather current probe $kind via FEE2 -> ${packet.toHexString()}")
    }

    fun sendGadgetbridgeProbe(kind: String) {
        if (!_state.value.isConnected) {
            updateDebugLog("Gadgetbridge probe skipped: watch is not connected.")
            return
        }
        val packet = when (kind) {
            "get-alarms" -> nativePacket(0x21)
            "get-step-goal" -> nativePacket(0x26)
            "get-auto-lock" -> nativePacket(0x8D)
            "heartbeat-64" -> nativePacket(0x64)
            "time-12h" -> nativePacket(0x17, 0x00)
            "time-24h" -> nativePacket(0x17, 0x01)
            "quick-view-off" -> nativePacket(0x18, 0x00)
            "quick-view-on" -> nativePacket(0x18, 0x01)
            "auto-hr-10m" -> nativePacket(0x1F, 0x02)
            "auto-hr-5m" -> nativePacket(0x1F, 0x01)
            "move-reminder-on" -> nativePacket(0x1D, 0x01)
            "move-reminder-off" -> nativePacket(0x1D, 0x00)
            "b9-ecard-config" -> nativePacket(0xB9, 0x12, 0x00, 0x02)
            "b9-ecard-content" -> nativePacket(0xB9, 0x12, 0x00, 0x03)
            "b9-weather-19" -> nativePacket(0xB9, 0x19, 0x00)
            "hr-6d-query" -> nativePacket(0x6D)
            "hr-6d-stop" -> nativePacket(0x6D, 0x00)
            "steps-33-00" -> nativePacket(0x33, 0x00)
            "steps-33-01" -> nativePacket(0x33, 0x01)
            "steps-33-02" -> nativePacket(0x33, 0x02)
            "steps-59-00" -> nativePacket(0x59, 0x00)
            "steps-59-01" -> nativePacket(0x59, 0x01)
            "steps-59-02" -> nativePacket(0x59, 0x02)
            "steps-59-03" -> nativePacket(0x59, 0x03)
            "steps-10-59-00" -> legacyPacket(0x59, 0x00)
            "steps-10-59-01" -> legacyPacket(0x59, 0x01)
            "steps-10-59-02" -> legacyPacket(0x59, 0x02)
            "steps-10-59-03" -> legacyPacket(0x59, 0x03)
            else -> {
                updateDebugLog("Unknown Gadgetbridge probe: $kind")
                return
            }
        }
        sendFee2NativeRaw(packet)
        updateDebugLog("Gadgetbridge probe $kind via FEE2 -> ${packet.toHexString()}")
    }

    fun sendWeightCandidate(weightTenthsKg: Int) {
        if (!_state.value.isConnected) {
            updateDebugLog("Weight test skipped: watch is not connected.")
            return
        }
        updateDebugLog("Weight write disabled until we capture a second known value.")
    }

    fun setAlarm1Enabled(enabled: Boolean) {
        setAlarm(0, enabled, 7, 15, 0)
    }

    fun setAlarm(slot: Int, enabled: Boolean, hour: Int, minute: Int, repeatMask: Int) {
        if (!_state.value.isConnected) {
            updateDebugLog("Alarm write skipped: watch is not connected.")
            return
        }
        val safeSlot = slot.coerceIn(0, 2)
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        val safeRepeatMask = repeatMask and 0x7F
        val enabledByte = if (enabled) 0x01 else 0x00
        val mode = when (safeRepeatMask) {
            0x00 -> 0x00
            0x7F -> 0x01
            else -> 0x02
        }
        val packet = nativePacket(
            0x11,
            safeSlot,
            enabledByte,
            mode,
            safeHour,
            safeMinute,
            if (safeRepeatMask == 0x00) 0xB5 else 0x00,
            if (safeRepeatMask == 0x00) 0x11 else 0x00,
            safeRepeatMask
        )
        enqueueOperation(GattOperation.WriteCharacteristic(FEE2_WRITE, packet))
        _state.update {
            val alarms = it.alarmSettings.filterNot { alarm -> alarm.slot == safeSlot } +
                WatchAlarm(safeSlot, enabled, mode, safeHour, safeMinute, safeRepeatMask)
            it.copy(alarmSettings = alarms.sortedBy { alarm -> alarm.slot })
        }
        updateDebugLog("Alarm ${safeSlot + 1} via FEE2: ${if (enabled) "on" else "off"} ${"%02d:%02d".format(safeHour, safeMinute)} repeat=0x${"%02X".format(safeRepeatMask)} -> ${packet.toHexString()}")
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

    private fun sendFee2NativeRaw(bytes: ByteArray) {
        enqueueOperation(GattOperation.WriteCharacteristic(FEE2_WRITE, bytes))
    }

    private fun nativePacket(cmd: Int, vararg payload: Int): ByteArray {
        val packet = ByteArray(5 + payload.size)
        packet[0] = 0xFE.toByte()
        packet[1] = 0xEA.toByte()
        packet[2] = 0x20.toByte()
        packet[3] = packet.size.toByte()
        packet[4] = cmd.toByte()
        payload.forEachIndexed { index, value -> packet[5 + index] = (value and 0xFF).toByte() }
        return packet
    }

    private fun legacyPacket(cmd: Int, vararg payload: Int): ByteArray {
        val packet = ByteArray(5 + payload.size)
        packet[0] = 0xFE.toByte()
        packet[1] = 0xEA.toByte()
        packet[2] = 0x10.toByte()
        packet[3] = (packet.size - 1).toByte()
        packet[4] = cmd.toByte()
        payload.forEachIndexed { index, value -> packet[5 + index] = (value and 0xFF).toByte() }
        return packet
    }

    private fun buildDaFitTimestampPacket(): ByteArray {
        val now = ((System.currentTimeMillis() + TimeZone.getDefault().getOffset(System.currentTimeMillis())) / 1000).toInt()
        return nativePacket(
            0x31,
            now and 0xFF,
            (now shr 8) and 0xFF,
            (now shr 16) and 0xFF,
            (now shr 24) and 0xFF,
            0x08
        )
    }

    private fun ByteArray.toHexString(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    private fun ByteArray.withTrailingSumChecksum(): ByteArray {
        val packet = copyOf(size + 1)
        packet[3] = packet.size.toByte()
        packet[packet.lastIndex] = (packet.dropLast(1).sumOf { it.toInt() and 0xFF } and 0xFF).toByte()
        return packet
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
        val cmd = when (type) { "Heart Rate" -> 0x6D; "SpO2" -> 0x6B; "Blood Pressure" -> 0x69; else -> return }
        updateDebugLog("$type app-start disabled: FE EA 20 06 ${"%02X".format(cmd)} 01 reboots this watch. Start the measurement on the watch; jampsFit will listen for FEE3 results.")
    }

    fun stopMeasurement() {
        val type = _state.value.activeMeasurement ?: return
        updateDebugLog("$type app-stop ignored: app-origin measurement commands are disabled to avoid watch reboot.")
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

    fun updateVolumeSteps(steps: Int) {
        val s = steps.coerceIn(1, 5)
        prefs.edit { putInt("volumeSteps", s) }
        _state.update { it.copy(volumeSteps = s) }
    }

    fun setFindingPhone(active: Boolean) {
        _state.update { it.copy(isFindingPhone = active) }
    }

    fun setServiceRunning(running: Boolean) {
        _state.update { it.copy(isServiceRunning = running) }
    }

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
                val errorCode = when (operation) {
                    is GattOperation.WriteDescriptor -> gatt.writeDescriptor(operation.descriptor, operation.value)
                    is GattOperation.WriteCharacteristic -> {
                        var found: BluetoothGattCharacteristic? = null
                        val short = (operation.charUuid?.toString()?.substring(4, 8) ?: _state.value.writeUuidShort).lowercase()
                        for (s in gatt.services) {
                            for (c in s.characteristics) {
                                if (c.uuid.toString().substring(4, 8).lowercase() == short) {
                                    found = c
                                    break
                                }
                            }
                            if (found != null) break
                        }
                        found?.let {
                            gatt.writeCharacteristic(it, operation.value, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                        } ?: BluetoothStatusCodes.ERROR_UNKNOWN
                    }
                    is GattOperation.ReadCharacteristic -> {
                        if (gatt.readCharacteristic(operation.characteristic)) BluetoothStatusCodes.SUCCESS else BluetoothStatusCodes.ERROR_UNKNOWN
                    }
                }
                if (errorCode != BluetoothStatusCodes.SUCCESS) {
                    synchronized(operationQueue) { isOperating = false }
                    doNextOperation()
                }
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name
            if (name?.contains(TARGET_NAME, ignoreCase = true) == true) { stopScan(); connectToDevice(result.device) }
        }

        override fun onScanFailed(errorCode: Int) {
            updateDebugLog("Scan failed: $errorCode; retrying...")
            scheduleReconnect(delayMs = 1500)
        }
    }

    fun startScan() {
        userRequestedDisconnect = false
        reconnectJob?.cancel()
        stopScan()
        _state.update { it.copy(connectionStatus = "Scanning...") }
        scanner?.startScan(null, android.bluetooth.le.ScanSettings.Builder().setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
        startScanWatchdog()
    }
    fun stopScan() {
        scanWatchdogJob?.cancel()
        scanWatchdogJob = null
        scanner?.stopScan(scanCallback)
    }
    fun disconnect() {
        userRequestedDisconnect = true
        reconnectJob?.cancel()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        stopScan()
        _state.update { it.copy(isConnected = false, connectionStatus = "Disconnected") }
    }
    private fun connectToDevice(device: BluetoothDevice) {
        userRequestedDisconnect = false
        stopScan()
        lastConnectedDevice = device
        _state.update { it.copy(connectionStatus = "Connecting...", deviceName = device.name) }
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) { isConfigured = false; _state.update { it.copy(isConnected = true, connectionStatus = "Connected") }; gatt.discoverServices() }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                bluetoothGatt = null
                isConfigured = false
                _state.update { it.copy(isConnected = false, connectionStatus = "Disconnected") }
                synchronized(operationQueue) { operationQueue.clear(); isOperating = false }
                gatt.close()
                scheduleReconnect()
            }
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
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            status: Int
        ) {
            updateDebugLog("Write ${c.uuid.toString().substring(4, 8)} status=$status")
            synchronized(operationQueue) { isOperating = false }
            doNextOperation()
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
        if (uuid == BATTERY_CHAR && data.size == 1) {
            updateDebugLog(msg)
            return
        }
        if (uuid == FEE3_NOTIFY && isKnownFee3Packet(data)) {
            updateDebugLog(msg)
            return
        }
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
        val formattedMsg = "[$timestamp] $msg"
        managerScope.launch {
            healthDao.insertUnknown(com.labbaslabs.jampsfit.database.UnknownPacket(message = formattedMsg))
        }
    }

    fun clearUnknownPackets() {
        managerScope.launch {
            healthDao.deleteAllUnknown()
        }
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
            HEART_RATE_CHAR -> parseStandardHeartRate(data)?.let {
                if (it > 0) {
                    _state.update { s -> s.copy(heartRate = it) }
                    saveToDb(heartRate = it)
                }
            }
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
                parseFee3Packet(uuid, data)
                parseWrappedActivityPacket(data)
            }
        }
    }

    private fun scheduleReconnect(delayMs: Long = 3000) {
        if (userRequestedDisconnect || !_state.value.autoConnect) return
        reconnectJob?.cancel()
        reconnectJob = managerScope.launch {
            updateDebugLog("Auto-connect retry in ${delayMs / 1000.0}s.")
            delay(delayMs)
            if (!_state.value.isConnected && _state.value.autoConnect && !userRequestedDisconnect) {
                reconnectOrScan()
            }
        }
    }

    private fun reconnectOrScan() {
        val last = lastConnectedDevice
        if (last != null) {
            updateDebugLog("Auto-connect: trying last watch directly.")
            connectToDevice(last)
        } else {
            startScan()
        }
    }

    private fun startScanWatchdog() {
        scanWatchdogJob?.cancel()
        scanWatchdogJob = managerScope.launch {
            delay(12000)
            if (!_state.value.isConnected && _state.value.connectionStatus == "Scanning..." && !userRequestedDisconnect) {
                updateDebugLog("Scan watchdog: restarting scan.")
                startScan()
            }
        }
    }

    private fun saveToDb(battery: Int? = null, heartRate: Int? = null, spo2: Int? = null, systolic: Int? = null, diastolic: Int? = null, steps: Int? = null, activityCount: Int? = null, distance: Int? = null, calories: Int? = null) {
        managerScope.launch { healthDao.insert(HealthEntry(battery = battery, heartRate = heartRate, spo2 = spo2, systolic = systolic, diastolic = diastolic, steps = steps, activityCount = activityCount, distance = distance, calories = calories)) }
    }

    private fun parseStandardHeartRate(data: ByteArray): Int? {
        if (data.isEmpty()) return null
        return if ((data[0].toInt() and 0x01) != 0) (if (data.size < 3) null else (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8))
        else (if (data.size < 2) null else data[1].toInt() and 0xFF)
    }

    private fun extractHeartRateCandidate(data: ByteArray, start: Int): Int? {
        for (index in start until data.size) {
            val candidate = data[index].toInt() and 0xFF
            if (candidate in 30..220) return candidate
        }
        return null
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

    private fun isKnownFee3Packet(data: ByteArray): Boolean {
        if (!startsWith(data, byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x20)) || data.size < 5) return false
        return when (data[4].toInt() and 0xFF) {
            0x21, 0x26, 0x33, 0x59, 0x5A, 0x64, 0x66, 0x67, 0x69, 0x6B, 0x6D, 0x8D, 0xA4 -> true
            else -> false
        }
    }

    private fun parseFee3Packet(uuid: UUID, data: ByteArray): Boolean {
        if (uuid != FEE3_NOTIFY || !isKnownFee3Packet(data)) return false
        when (data[4].toInt() and 0xFF) {
            0x21 -> {
                parseAlarmQueryResponse(data)
            }
            0x26 -> {
                parseStepGoalResponse(data)
            }
            0x33 -> {
                if (data.size >= 15) parseDailyTotalsPacket(data) else updateDebugLog("Daily totals response too short: ${data.toHexString()}")
            }
            0x59 -> {
                if (data.size >= 54) parseHourlyActivityPacket(data) else updateDebugLog("Activity bucket response too short: ${data.toHexString()}")
            }
            0x5A -> {
                parseDeviceInfoPacket(data)
            }
            0x64 -> {
                _state.update { it.copy(lastRemoteEvent = "Watch Command 0x64") }
                updateDebugLog("Remote event: Watch Command 0x64 (unmapped)")
            }
            0x8D -> {
                val seconds = data.getOrNull(5)?.toInt()?.and(0xFF)
                if (seconds != null) {
                    _state.update { it.copy(autoLockSecondsSetting = seconds) }
                    updateDebugLog("Auto-lock response: ${seconds}s")
                } else {
                    updateDebugLog("Auto-lock response: empty")
                }
            }
            0x6D -> {
                val hr = extractHeartRateCandidate(data, start = 5)
                if (hr != null) {
                    _state.update { it.copy(heartRate = hr) }
                    saveToDb(heartRate = hr)
                    updateDebugLog("Manual HR: $hr bpm payload=${data.copyOfRange(5, data.size).toHexString()}")
                } else if (data.size > 5) {
                    updateDebugLog("Manual HR response without bpm payload=${data.copyOfRange(5, data.size).toHexString()}")
                }
            }
            0x6B -> {
                if (data.size > 5) {
                    val spo2 = data[5].toInt() and 0xFF
                    if (spo2 > 0) {
                        _state.update { it.copy(spo2 = spo2) }
                        saveToDb(spo2 = spo2)
                        updateDebugLog("Manual SpO2: $spo2%")
                    }
                }
            }
            0x69 -> {
                if (data.size > 7) {
                    val systolic = data[6].toInt() and 0xFF
                    val diastolic = data[7].toInt() and 0xFF
                    _state.update { it.copy(systolic = systolic, diastolic = diastolic) }
                    saveToDb(systolic = systolic, diastolic = diastolic)
                    updateDebugLog("Manual BP: $systolic/$diastolic")
                }
            }
            0x66 -> {
                _state.update { it.copy(lastRemoteEvent = "Wrist Shake / Shutter") }
                updateDebugLog("Remote event: Wrist Shake / Shutter")
                managerScope.launch { delay(100); _state.update { it.copy(lastRemoteEvent = null) } }
            }
            0x67 -> {
                val event = when (data.getOrNull(5)?.toInt()?.and(0xFF)) {
                    0x01 -> "Previous Track"
                    0x02 -> "Next Track"
                    0x06 -> "Play/Pause"
                    else -> null
                }
                if (event != null) {
                    _state.update { it.copy(lastRemoteEvent = event) }
                    updateDebugLog("Remote event: $event")
                    managerScope.launch { delay(100); _state.update { it.copy(lastRemoteEvent = null) } }
                }
            }
            0xA4 -> {
                if (data.size > 6) {
                    val enabled = data[5].toInt() == 0x01
                    updateDebugLog("Power save: ${if (enabled) "enabled" else "disabled"}")
                }
            }
        }
        return true
    }

    private fun parseAlarmQueryResponse(data: ByteArray) {
        if (data.size <= 5) {
            updateDebugLog("Alarm query response: empty")
            return
        }
        val payload = data.copyOfRange(5, data.size)
        val records = payload.size / 8
        if (records == 0) {
            updateDebugLog("Alarm query response payload=${payload.toHexString()}")
            return
        }
        val alarms = mutableListOf<WatchAlarm>()
        val decoded = (0 until records).joinToString("; ") { index ->
            val offset = index * 8
            val slot = payload[offset].toInt() and 0xFF
            val enabled = (payload[offset + 1].toInt() and 0xFF) == 1
            val mode = payload[offset + 2].toInt() and 0xFF
            val hour = payload[offset + 3].toInt() and 0xFF
            val minute = payload[offset + 4].toInt() and 0xFF
            val repeat = payload[offset + 7].toInt() and 0xFF
            alarms.add(WatchAlarm(slot, enabled, mode, hour, minute, repeat))
            "slot=${slot + 1} ${if (enabled) "on" else "off"} ${"%02d:%02d".format(hour, minute)} mode=$mode repeat=0x${"%02X".format(repeat)}"
        }
        _state.update { it.copy(alarmSettings = alarms.sortedBy { alarm -> alarm.slot }) }
        updateDebugLog("Alarm query response: $decoded")
    }

    private fun parseStepGoalResponse(data: ByteArray) {
        val payload = if (data.size > 5) data.copyOfRange(5, data.size) else byteArrayOf()
        val goal = when {
            payload.size >= 4 && payload[0] == 0x00.toByte() -> (payload[1].toInt() and 0xFF) or ((payload[2].toInt() and 0xFF) shl 8)
            payload.size >= 3 && payload[0] == 0x00.toByte() -> ((payload[1].toInt() and 0xFF) shl 8) or (payload[2].toInt() and 0xFF)
            payload.size >= 2 -> ((payload[payload.size - 2].toInt() and 0xFF) shl 8) or (payload[payload.size - 1].toInt() and 0xFF)
            else -> null
        }
        if (goal != null) {
            _state.update { it.copy(stepGoalSetting = goal) }
            updateDebugLog("Step goal response: $goal payload=${payload.toHexString()}")
        } else {
            updateDebugLog("Step goal response payload=${payload.toHexString()}")
        }
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
        saveToDb(activityCount = activityCount, distance = distance, calories = calories)
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
        if (dayOffset == 0x00) {
            _state.update { it.copy(steps = steps, distance = distance, calories = calories) }
            saveToDb(steps = steps, distance = distance, calories = calories)
            updateDebugLog("Daily totals[$dayOffset]: steps=$steps distance=${distance}m calories=$calories")
        } else {
            updateDebugLog("Daily totals candidate[$dayOffset]: value=$steps distance=${distance}m calories=$calories")
        }
    }

    private fun parseHourlyActivityPacket(b: ByteArray) {
        val bucket = b[5].toInt() and 0xFF
        var steps = 0
        var distance = 0
        var calories = 0
        val nonZeroRecords = mutableListOf<String>()
        var offset = 6
        var record = 0
        while (offset + 5 < b.size) {
            val recordSteps = readUInt16LE(b, offset)
            val recordDistance = readUInt16LE(b, offset + 2)
            val recordCalories = readUInt16LE(b, offset + 4)
            steps += recordSteps
            distance += recordDistance
            calories += recordCalories
            if (recordSteps != 0 || recordDistance != 0 || recordCalories != 0) {
                nonZeroRecords.add("$record:$recordSteps/$recordDistance/$recordCalories")
            }
            offset += 6
            record += 1
        }
        val detail = if (nonZeroRecords.isEmpty()) "none" else nonZeroRecords.joinToString(", ")
        if (bucket == 0x00) {
            val stepsDown = steps
            val stepsUp = distance
            val stepsOther = calories
            val totalSteps = stepsUp + stepsDown + stepsOther
            updateDebugLog("Activity buckets[0] candidate: stepsDown=$stepsDown stepsUp=$stepsUp stepsOther=$stepsOther totalSteps=$totalSteps records=$detail")
        } else {
            updateDebugLog("Activity buckets[$bucket]: stepsCandidate=$steps distance=${distance}m calories=$calories records=$detail")
        }
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
