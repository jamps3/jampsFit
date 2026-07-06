package com.labbaslabs.jampsfit

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import androidx.core.content.edit
import android.util.Log
import com.labbaslabs.jampsfit.database.AppDatabase
import com.labbaslabs.jampsfit.database.DEFAULT_DANCING_EVENT_NAME
import com.labbaslabs.jampsfit.database.DEFAULT_FESTIVAL_NAME
import com.labbaslabs.jampsfit.database.EVENT_TYPE_DANCING
import com.labbaslabs.jampsfit.database.EventEntity
import com.labbaslabs.jampsfit.database.FestivalEntity
import com.labbaslabs.jampsfit.database.FoodEntity
import com.labbaslabs.jampsfit.database.FoodRoles
import com.labbaslabs.jampsfit.database.FoodSources
import com.labbaslabs.jampsfit.database.HealthEntry
import com.labbaslabs.jampsfit.database.HistoryPoint
import com.labbaslabs.jampsfit.database.defaultFoods
import com.labbaslabs.jampsfit.events.summarizeEvent
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
    val autoStart: Boolean = true,
    val autoConnect: Boolean = false,
    val autoFetchSteps: Boolean = false,
    val autoFetchBattery: Boolean = false,
    val autoFetchSleep: Boolean = false,
    val stepFetchIntervalMinutes: Int = 60,
    val autoHeartRateIntervalMinutes: Int = 0,
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
    val sleepSegments: List<SleepSegment> = emptyList(),
    val isFindingPhone: Boolean = false,
    val volumeSteps: Int = 1,
    val batteryHistory: List<HistoryPoint> = emptyList(),
    val heartRateHistory: List<HistoryPoint> = emptyList(),
    val spo2History: List<HistoryPoint> = emptyList(),
    val bpHistory: List<HealthEntry> = emptyList(),
    val stepsHistory: List<HistoryPoint> = emptyList(),
    val distanceHistory: List<HistoryPoint> = emptyList(),
    val caloriesHistory: List<HistoryPoint> = emptyList(),
    val activityHistory: List<HistoryPoint> = emptyList(),
    val last24hStats: List<HealthEntry> = emptyList(),
    val dailyStats: List<HealthEntry> = emptyList(),
    val weeklyStats: List<HealthEntry> = emptyList(),
    val monthlyStats: List<HealthEntry> = emptyList(),
    val activeEvent: EventEntity? = null,
    val recentEvents: List<EventEntity> = emptyList(),
    val festivals: List<FestivalEntity> = emptyList(),
    val selectedFestivalId: Long? = null,
    val foods: List<FoodEntity> = emptyList(),
    val alarmSettings: List<WatchAlarm> = emptyList(),
    val stepGoalSetting: Int? = null,
    val autoLockSecondsSetting: Int? = null,
    val notificationFilters: Set<String> = emptySet(),
    val discoveredApps: Map<String, String> = emptyMap(),
    val ignoreDuplicateNotifications: Boolean = true,
    val useLegacyCallNotifications: Boolean = false,
    val borderColor: Int = 0xFFFFFFFF.toInt(),
    val borderThickness: Float = 1.0f,
    val borderAlpha: Float = 0.4f,
    val profileGender: String = "Male",
    val profileHeightCm: Int = 168,
    val profileWeightKg: Float = 83f,
    val profileAgeYears: Int = 41,
    val writeUuidShort: String = "6387",
    val protocolHeader: String = "FE EA 20",
    val payloadLengthOnly: Boolean = false,
    val autoSyncAlarm: Boolean = false,
    val muteAlarmSyncNotification: Boolean = false,
    val autoSyncTime: Boolean = false,
    val syncTimeIntervalHours: Int = 4,
    val is24HourFormat: Boolean = true,
    val quickViewEnabled: Boolean = true,
    val quickViewStartHour: Int = 10,
    val quickViewStartMinute: Int = 0,
    val quickViewEndHour: Int = 22,
    val quickViewEndMinute: Int = 0,
    val hasFullScreenIntentPermission: Boolean = true,
)

data class SleepSegment(
    val startMinutes: Int,
    val endMinutes: Int,
    val stateId: Int,
    val label: String,
    val hasInternalMarkers: Boolean = false,
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
    private val eventDao = db.eventDao()
    private val foodDao = db.foodDao()
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefs = context.getSharedPreferences("jampsFitPrefs", Context.MODE_PRIVATE)
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scanner get() = adapter?.bluetoothLeScanner

    private val _state = MutableStateFlow(WatchState(
        autoStart = prefs.getBoolean("autoStart", true),
        autoConnect = prefs.getBoolean("autoConnect", true),
        autoFetchSteps = prefs.getBoolean("autoFetchSteps", false),
        autoFetchBattery = prefs.getBoolean("autoFetchBattery", false),
        autoFetchSleep = prefs.getBoolean("autoFetchSleep", false),
        stepFetchIntervalMinutes = prefs.getInt("stepFetchIntervalMinutes", 60),
        autoHeartRateIntervalMinutes = prefs.getInt("autoHeartRateIntervalMinutes", 0),
        batteryThreshold = prefs.getInt("batteryThreshold", 15),
        shutterAction = prefs.getString("shutterAction", "Camera") ?: "Camera",
        musicAction = prefs.getString("musicAction", "Media") ?: "Media",
        playPauseAction = prefs.getString("playPauseAction", "Play/Pause") ?: "Play/Pause",
        nextAction = prefs.getString("nextAction", "Next Track") ?: "Next Track",
        prevAction = prefs.getString("prevAction", "Previous Track") ?: "Previous Track",
        volumeSteps = prefs.getInt("volumeSteps", 1),
        firmwareVersion = prefs.getString("firmwareVersion", null),
        notificationsEnabled = prefs.getBoolean("notificationsEnabled", false),
        notificationFilters = prefs.getStringSet("notificationFilters", setOf("com.digibites.accubattery")) ?: emptySet(),
        discoveredApps = prefs.getStringSet("discoveredApps", emptySet())?.associate {
            val parts = it.split("|")
            (parts.getOrNull(0) ?: "") to (parts.getOrNull(1) ?: "")
        }?.filter { it.key.isNotBlank() } ?: emptyMap(),
        ignoreDuplicateNotifications = prefs.getBoolean("ignoreDuplicateNotifications", true),
        useLegacyCallNotifications = prefs.getBoolean("useLegacyCallNotifications", false),
        borderColor = prefs.getInt("borderColor", 0xFFFFFFFF.toInt()),
        borderThickness = prefs.getFloat("borderThickness", 1.0f),
        borderAlpha = prefs.getFloat("borderAlpha", 0.4f),
        profileGender = prefs.getString("profileGender", "Male") ?: "Male",
        profileHeightCm = prefs.getInt("profileHeightCm", 168),
        profileWeightKg = prefs.getFloat("profileWeightKg", 83f),
        profileAgeYears = prefs.getInt("profileAgeYears", 41),
        autoSyncAlarm = prefs.getBoolean("autoSyncAlarm", false),
        muteAlarmSyncNotification = prefs.getBoolean("muteAlarmSyncNotification", false),
        autoSyncTime = prefs.getBoolean("autoSyncTime", false),
        syncTimeIntervalHours = prefs.getInt("syncTimeIntervalHours", 4),
        is24HourFormat = prefs.getBoolean("is24HourFormat", true),
        quickViewEnabled = prefs.getBoolean("quickViewEnabled", true),
        quickViewStartHour = prefs.getInt("quickViewStartHour", 10),
        quickViewStartMinute = prefs.getInt("quickViewStartMinute", 0),
        quickViewEndHour = prefs.getInt("quickViewEndHour", 22),
        quickViewEndMinute = prefs.getInt("quickViewEndMinute", 0)
    ))
    val state = _state.asStateFlow()

    private val decoder = ProtocolDecoder { result -> handleDecodedResult(result) }
    private var bluetoothGatt: BluetoothGatt? = null
    private var lastConnectedDevice: BluetoothDevice? = null
    private val operationQueue: Queue<GattOperation> = LinkedList()
    private var isOperating = false
    private var lastOpTime = 0L
    private var userRequestedDisconnect = false
    private var reconnectJob: Job? = null
    private var scanWatchdogJob: Job? = null
    private var connectWatchdogJob: Job? = null
    private var operationWatchdogJob: Job? = null
    private var autoStepFetchJob: Job? = null
    private var autoSyncTimeJob: Job? = null
    private var logBuffer = mutableListOf<String>()
    private val recentActivityPayloads = ArrayDeque<String>()
    private val recentActivityPayloadSet = mutableSetOf<String>()
    private val recentStepBuckets = mutableMapOf<Int, StepBucketTotals>()

    private data class StepBucketTotals(
        val stepsDown: Int,
        val stepsUp: Int,
        val stepsOther: Int,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        val totalSteps: Int get() = stepsDown + stepsUp + stepsOther
    }

    init {
        seedDefaultFoods()
        seedDefaultFestival()
        observeHistory()
        observeEvents()
        observeFoods()
        cleanupSeenNotifications()
        checkFullScreenIntentPermission()
        updateDebugLog("WatchManager build: modular-decoder-v2")
    }

    private fun handleDecodedResult(result: ProtocolDecoder.DecodedResult) {
        when (result) {
            is ProtocolDecoder.DecodedResult.Battery -> {
                _state.update { it.copy(battery = result.level) }
                saveToDb(battery = result.level)
                updateDebugLog("Battery: ${result.level}%")
            }
            is ProtocolDecoder.DecodedResult.HeartRate -> {
                _state.update { it.copy(heartRate = result.bpm) }
                saveToDb(heartRate = result.bpm)
                updateDebugLog("Heart Rate: ${result.bpm} bpm")
            }
            is ProtocolDecoder.DecodedResult.SpO2 -> {
                _state.update { it.copy(spo2 = result.percent) }
                saveToDb(spo2 = result.percent)
                updateDebugLog("SpO2: ${result.percent}%")
            }
            is ProtocolDecoder.DecodedResult.BloodPressure -> {
                _state.update { it.copy(systolic = result.systolic, diastolic = result.diastolic) }
                saveToDb(systolic = result.systolic, diastolic = result.diastolic)
                updateDebugLog("BP: ${result.systolic}/${result.diastolic}")
            }
            is ProtocolDecoder.DecodedResult.Activity -> {
                if (rememberRecentPayload(result.seq.toString(), recentActivityPayloads, recentActivityPayloadSet)) return
                _state.update { it.copy(activityCount = result.activityCount, distance = result.distance, calories = result.calories) }
                saveToDb(activityCount = result.activityCount, distance = result.distance, calories = result.calories)
                updateDebugLog("Activity: seq=${result.seq} count=${result.activityCount} dist=${result.distance}m cal=${result.calories}")
            }
            is ProtocolDecoder.DecodedResult.DailyTotals -> {
                _state.update { it.copy(steps = result.steps, distance = result.distance, calories = result.calories) }
                saveToDb(steps = result.steps, distance = result.distance, calories = result.calories)
                updateDebugLog("Daily Totals: steps=${result.steps} dist=${result.distance}m cal=${result.calories}")
            }
            is ProtocolDecoder.DecodedResult.HourlyActivity -> {
                val totals = StepBucketTotals(result.stepsDown, result.stepsUp, result.stepsOther)
                recentStepBuckets[result.bucket] = totals
                val b0 = recentStepBuckets[0]; val b1 = recentStepBuckets[1]
                if (b0 != null && b1 != null && Math.abs(b0.timestamp - b1.timestamp) <= 30_000L) {
                    val currentSteps = b0.totalSteps + b1.totalSteps
                    _state.update { it.copy(steps = currentSteps) }
                    saveToDb(steps = currentSteps)
                    updateDebugLog("Current steps (bucket sum): $currentSteps")
                }
            }
            is ProtocolDecoder.DecodedResult.SleepBoundaries -> {
                _state.update { it.copy(sleepMinutes = result.total, deepSleepMinutes = result.deep, lightSleepMinutes = result.light, sleepSegments = result.segments) }
                saveToDb(sleepMinutes = result.total, deepSleepMinutes = result.deep, lightSleepMinutes = result.light)
                updateDebugLog("Sleep boundaries: total=${result.total}m deep=${result.deep}m light=${result.light}m")
            }
            is ProtocolDecoder.DecodedResult.DeviceInfo -> {
                result.name?.let { updateDebugLog("Device info: $it") }
                result.firmware?.let { fw ->
                    prefs.edit { putString("firmwareVersion", fw) }
                    _state.update { it.copy(firmwareVersion = fw) }
                    updateDebugLog("Firmware: $fw")
                }
            }
            is ProtocolDecoder.DecodedResult.AlarmSettings -> {
                _state.update { it.copy(alarmSettings = result.alarms.sortedBy { a -> a.slot }) }
                updateDebugLog("Alarms synced: ${result.alarms.size} slots")
            }
            is ProtocolDecoder.DecodedResult.StepGoal -> {
                _state.update { it.copy(stepGoalSetting = result.goal) }
                updateDebugLog("Step goal: ${result.goal}")
            }
            is ProtocolDecoder.DecodedResult.AutoLock -> {
                _state.update { it.copy(autoLockSecondsSetting = result.seconds) }
                updateDebugLog("Auto-lock: ${result.seconds}s")
            }
            is ProtocolDecoder.DecodedResult.RemoteEvent -> {
                _state.update { it.copy(lastRemoteEvent = result.event) }
                updateDebugLog("Remote event: ${result.event}")
                managerScope.launch { delay(100); _state.update { it.copy(lastRemoteEvent = null) } }
            }
            is ProtocolDecoder.DecodedResult.AutoHeartRate -> {
                _state.update { it.copy(autoHeartRateIntervalMinutes = result.minutes) }
                updateDebugLog("Auto HR: ${result.minutes}m")
            }
            is ProtocolDecoder.DecodedResult.PowerSave -> {
                updateDebugLog("Power save: ${if (result.enabled) "enabled" else "disabled"}")
            }
            is ProtocolDecoder.DecodedResult.ShutterEvent -> {
                _state.update { it.copy(lastRemoteEvent = "Shutter") }
                updateDebugLog("Remote event: Shutter")
                managerScope.launch { delay(100); _state.update { it.copy(lastRemoteEvent = null) } }
            }
            else -> {}
        }
    }

    fun checkFullScreenIntentPermission() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.canUseFullScreenIntent() ?: true
        } else true
        _state.update { it.copy(hasFullScreenIntentPermission = hasPermission) }
    }

    private fun cleanupSeenNotifications() {
        managerScope.launch {
            val oneMonthAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            val sixMonthsAgo = System.currentTimeMillis() - (180L * 24 * 60 * 60 * 1000)
            healthDao.cleanupOldNotifications(oneMonthAgo)
            healthDao.cleanupOldHealthData(sixMonthsAgo)
            healthDao.trimUnknownPackets(500)
        }
    }

    private fun observeHistory() {
        managerScope.launch { healthDao.getBatteryHistory().collect { h -> _state.update { it.copy(batteryHistory = h.reversed()) } } }
        managerScope.launch { healthDao.getHeartRateHistory().collect { h -> _state.update { it.copy(heartRateHistory = h.reversed()) } } }
        managerScope.launch { healthDao.getSpO2History().collect { h -> _state.update { it.copy(spo2History = h.reversed()) } } }
        managerScope.launch { healthDao.getBloodPressureHistory().collect { h -> _state.update { it.copy(bpHistory = h.reversed()) } } }
        managerScope.launch { healthDao.getStepsHistory().collect { h -> _state.update { it.copy(stepsHistory = h.reversed()) } } }
        managerScope.launch { healthDao.getDistanceHistory().collect { h -> _state.update { it.copy(distanceHistory = h.reversed()) } } }
        managerScope.launch { healthDao.getActivityHistory().collect { h -> _state.update { it.copy(activityHistory = h.reversed()) } } }
        managerScope.launch { healthDao.getCaloriesHistory().collect { h -> _state.update { it.copy(caloriesHistory = h.reversed()) } } }
        managerScope.launch { healthDao.getLast24hStats().collect { h -> _state.update { it.copy(last24hStats = h.reversed()) } } }
        managerScope.launch { healthDao.getDailyStats().collect { h -> _state.update { it.copy(dailyStats = h.reversed()) } } }
        managerScope.launch { healthDao.getWeeklyStats().collect { h -> _state.update { it.copy(weeklyStats = h.reversed()) } } }
        managerScope.launch { healthDao.getMonthlyStats().collect { h -> _state.update { it.copy(monthlyStats = h.reversed()) } } }
        managerScope.launch { healthDao.getAllUnknownPackets().collect { h -> _state.update { it.copy(unknownMessages = h) } } }
    }

    private fun observeEvents() {
        managerScope.launch {
            eventDao.observeActiveEvent().collect { event ->
                _state.update { it.copy(activeEvent = event) }
            }
        }
        managerScope.launch {
            eventDao.observeRecentEvents().collect { events ->
                _state.update { it.copy(recentEvents = events) }
            }
        }
        managerScope.launch {
            eventDao.observeFestivals().collect { festivals ->
                _state.update { state ->
                    state.copy(
                        festivals = festivals,
                        selectedFestivalId = state.selectedFestivalId
                            ?: festivals.maxByOrNull { it.createdAt }?.id
                    )
                }
            }
        }
        managerScope.launch {
            eventDao.getActiveEventOnce()?.let { refreshEventSummary(it, System.currentTimeMillis()) }
        }
    }

    private fun seedDefaultFestival() {
        managerScope.launch {
            if (eventDao.getNewestFestival() == null) {
                val now = System.currentTimeMillis()
                val id = eventDao.insertFestival(FestivalEntity(createdAt = now, updatedAt = now))
                _state.update { it.copy(selectedFestivalId = id) }
            }
        }
    }

    private fun seedDefaultFoods() {
        managerScope.launch {
            if (foodDao.countFoods() == 0) {
                foodDao.insertAll(defaultFoods())
            }
        }
    }

    private fun observeFoods() {
        managerScope.launch {
            foodDao.observeFoods().collect { foods ->
                _state.update { it.copy(foods = foods) }
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
        private const val LAST_DEVICE_ADDRESS_KEY = "lastDeviceAddress"
        private const val OPERATION_TIMEOUT_MS = 10_000L
        private val BATTERY_CHAR = ProtocolDecoder.UUID_BATTERY
        private val FEE1_CHAR = ProtocolDecoder.UUID_FEE1
        private val FEA1_CHAR = ProtocolDecoder.UUID_FEA1
        private val FEE2_WRITE = UUID.fromString("0000fee2-0000-1000-8000-00805f9b34fb")
        private val FEE3_NOTIFY = ProtocolDecoder.UUID_FEE3
        private val SKIP_NOTIFY_CHAR = UUID.fromString("00002a05-0000-1000-8000-00805f9b34fb")
        private val CLIENT_CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    fun findWatch() {
        if (!_state.value.isConnected) return
        enqueueOperation(GattOperation.WriteCharacteristic(FEE2_WRITE, nativePacket(0x61)))
        updateDebugLog("Find My Watch via FEE2")
    }

    fun syncTime() {
        val tz = TimeZone.getDefault()
        val now = (System.currentTimeMillis() + tz.getOffset(System.currentTimeMillis())) / 1000
        val packet = ByteArray(10).apply {
            this[0] = 0xFE.toByte()
            this[1] = 0xEA.toByte()
            this[2] = 0x10.toByte()
            this[3] = 0x09.toByte()
            this[4] = 0x31.toByte()
            this[5] = ((now shr 24) and 0xFF).toByte()
            this[6] = ((now shr 16) and 0xFF).toByte()
            this[7] = ((now shr 8) and 0xFF).toByte()
            this[8] = (now and 0xFF).toByte()
            this[9] = 0x08.toByte()
        }
        enqueueOperation(GattOperation.WriteCharacteristic(FEE2_WRITE, packet))
        updateDebugLog("Syncing clock (Big Endian FEE2)...")
    }

    fun startDancingEvent() {
        val snapshot = _state.value
        managerScope.launch {
            if (eventDao.getActiveEventOnce() != null) {
                updateDebugLog("Dancing event already active")
                return@launch
            }
            val now = System.currentTimeMillis()
            val festivalId = eventDao.getNewestFestival()?.id
                ?: eventDao.insertFestival(FestivalEntity(createdAt = now, updatedAt = now))
            val id = eventDao.insert(
                EventEntity(
                    festivalId = festivalId,
                    type = EVENT_TYPE_DANCING,
                    name = DEFAULT_DANCING_EVENT_NAME,
                    startTime = now,
                    startSteps = snapshot.steps,
                    startActivityCount = snapshot.activityCount,
                    startDistance = snapshot.distance,
                    startCalories = snapshot.calories,
                    lastUpdatedTime = now
                )
            )
            eventDao.getEvent(id)?.let { refreshEventSummary(it, now) }
            updateDebugLog("Dancing event started")
        }
    }

    fun createFestival() {
        managerScope.launch {
            val now = System.currentTimeMillis()
            val count = _state.value.festivals.size + 1
            val id = eventDao.insertFestival(
                FestivalEntity(
                    name = "Life Festival $count",
                    createdAt = now,
                    updatedAt = now
                )
            )
            _state.update { it.copy(selectedFestivalId = id) }
            updateDebugLog("Festival created")
        }
    }

    fun selectFestival(id: Long) {
        _state.update { it.copy(selectedFestivalId = id) }
    }

    fun updateFestivalName(id: Long, name: String) {
        val trimmed = name.trim().ifBlank { DEFAULT_FESTIVAL_NAME }
        managerScope.launch {
            val festival = eventDao.getFestival(id) ?: return@launch
            eventDao.updateFestival(festival.copy(name = trimmed, updatedAt = System.currentTimeMillis()))
        }
    }

    fun updateFestivalImage(id: Long, imageUri: String?) {
        managerScope.launch {
            val festival = eventDao.getFestival(id) ?: return@launch
            eventDao.updateFestival(festival.copy(imageUri = imageUri, updatedAt = System.currentTimeMillis()))
        }
    }

    fun stopActiveEvent() {
        managerScope.launch {
            val active = eventDao.getActiveEventOnce() ?: run {
                updateDebugLog("No active dancing event to stop")
                return@launch
            }
            val now = System.currentTimeMillis()
            refreshEventSummary(active.copy(endTime = now), now)
            updateDebugLog("Dancing event stopped")
        }
    }

    fun clearQueue() { synchronized(operationQueue) { operationQueue.clear(); isOperating = false; updateDebugLog("Queue cleared.") } }

    fun close() {
        userRequestedDisconnect = true
        reconnectJob?.cancel()
        scanWatchdogJob?.cancel()
        connectWatchdogJob?.cancel()
        operationWatchdogJob?.cancel()
        autoStepFetchJob?.cancel()
        autoSyncTimeJob?.cancel()
        synchronized(operationQueue) {
            operationQueue.clear()
            isOperating = false
        }
        stopScan()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        managerScope.cancel()
    }

    suspend fun exportDataToCsv(): String {
        val entries = healthDao.getAllEntriesList(); val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return "Timestamp,Time,Battery,HeartRate,SpO2,Systolic,Diastolic,Steps,ActivityCount,Distance,Calories\n" +
            entries.joinToString("\n") { e -> "${e.timestamp},${sdf.format(Date(e.timestamp))},${e.battery ?: ""},${e.heartRate ?: ""},${e.spo2 ?: ""},${e.systolic ?: ""},${e.diastolic ?: ""},${e.steps ?: ""},${e.activityCount ?: ""},${e.distance ?: ""},${e.calories ?: ""}" }
    }

    fun sendNotification(title: String, text: String, forceLegacy: Boolean = false, ignoreDuplicate: Boolean = false, legacyType: Int = 0x01, forceMirrored: Boolean = false) {
        val message = listOf(title.trim(), text.trim()).filter { it.isNotBlank() }.joinToString(": "); if (message.isBlank()) return
        managerScope.launch {
            if (_state.value.ignoreDuplicateNotifications && !forceLegacy && !ignoreDuplicate) {
                val hash = message.hashCode()
                if (healthDao.countSeenNotification(hash, System.currentTimeMillis() - (30L * 24 * 3600_000)) > 0) return@launch
                healthDao.insertSeenNotification(com.labbaslabs.jampsfit.database.SeenNotification(content_hash = hash))
            }
            if (!forceMirrored && (forceLegacy || _state.value.useLegacyCallNotifications)) sendNativeNotification08(title, text, type = legacyType)
            else sendNativeNotification41(message, maxBytes = 238)
        }
    }

    private fun sendNativeNotification08(title: String, text: String, type: Int) {
        val titleBytes = title.take(18).toByteArray(Charsets.UTF_8); val textBytes = text.take(40).toByteArray(Charsets.UTF_8)
        val payload = mutableListOf<Int>().apply { add(type and 0xFF); add(titleBytes.size); titleBytes.forEach { add(it.toInt() and 0xFF) }; add(textBytes.size); textBytes.forEach { add(it.toInt() and 0xFF) } }
        enqueueOperation(GattOperation.WriteCharacteristic(FEE2_WRITE, nativePacket(0x08, *payload.toIntArray())))
    }

    private fun sendNativeNotification41(message: String, maxBytes: Int) {
        val textBytes = message.toByteArray(Charsets.UTF_8).let { it.copyOfRange(0, it.size.coerceAtMost(maxBytes.coerceIn(1, 249))) }
        val payload = IntArray(1 + textBytes.size).apply { this[0] = 0x80; textBytes.forEachIndexed { i, b -> this[i + 1] = b.toInt() and 0xFF } }
        enqueueOperation(GattOperation.WriteCharacteristic(FEE2_WRITE, nativePacket(0x41, *payload)))
    }

    fun setAutoLockSeconds(seconds: Int) { val safe = seconds.coerceIn(5, 60); enqueueOperation(GattOperation.WriteCharacteristic(FEE2_WRITE, nativePacket(0x7D, safe))); _state.update { it.copy(autoLockSecondsSetting = safe) } }
    fun setQuickViewWindow(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        val sH = startHour.coerceIn(0, 23); val sM = startMinute.coerceIn(0, 59); val eH = endHour.coerceIn(0, 23); val eM = endMinute.coerceIn(0, 59)
        sendFee2NativeRaw(nativePacket(0x72, sH, sM, eH, eM))
        prefs.edit { putInt("quickViewStartHour", sH); putInt("quickViewStartMinute", sM); putInt("quickViewEndHour", eH); putInt("quickViewEndMinute", eM) }
        _state.update { it.copy(quickViewStartHour = sH, quickViewStartMinute = sM, quickViewEndHour = eH, quickViewEndMinute = eM) }
    }

    fun setStepGoal(goal: Int) { val safe = (goal / 1000).coerceIn(2, 35) * 1000; enqueueOperation(GattOperation.WriteCharacteristic(FEE2_WRITE, nativePacket(0x16, 0x00, (safe shr 8) and 0xFF, safe and 0xFF))); _state.update { it.copy(stepGoalSetting = safe) } }
    fun queryCurrentSteps() { managerScope.launch { sendFee2NativeRaw(nativePacket(0x59, 0x00)); delay(180); sendFee2NativeRaw(nativePacket(0x59, 0x01)) } }
    fun querySleepBoundaries() = sendFee2NativeRaw(nativePacket(0x32))

    private fun restartAutoStepFetch() {
        autoStepFetchJob?.cancel(); if (!_state.value.autoFetchSteps || !_state.value.isConnected) return
        autoStepFetchJob = managerScope.launch { val interval = _state.value.stepFetchIntervalMinutes.coerceIn(5, 1440) * 60_000L; while (isActive) { queryCurrentSteps(); delay(interval) } }
    }

    private fun restartAutoSyncTime() {
        autoSyncTimeJob?.cancel(); if (!_state.value.autoSyncTime || !_state.value.isConnected) return
        autoSyncTimeJob = managerScope.launch { val interval = _state.value.syncTimeIntervalHours.coerceIn(1, 24) * 3600_000L; while (isActive) { syncTime(); delay(interval) } }
    }

    fun setWeatherCity(city: String) {
        val safeCity = city.trim().ifBlank { "London" }.take(12)
        managerScope.launch {
            val cityAscii = safeCity.lowercase().toByteArray(Charsets.UTF_8); val cityUtf16 = safeCity.lowercase().toByteArray(Charsets.UTF_16LE); val displayAscii = safeCity.toByteArray(Charsets.UTF_8)
            sendFee2NativeRaw(nativePacket(0xB9, 0x19, 0x00)); delay(180); sendFee2NativeRaw(nativePacket(0x43, 0x00, 0x01, 0x07, 0x00, 0x20, 0x00, 0x20, 0x00, 0x20, 0x00, *cityUtf16.map { it.toInt() and 0xFF }.toIntArray())); delay(180)
            sendFee2NativeRaw(nativePacket(0x42, 0x03, 0x0E, 0x07, 0x00, 0x0E, 0x06, 0x03, 0x13, 0x0A, 0x03, 0x10, 0x0C, 0x00, 0x0F, 0x0A, 0x03, 0x0D, 0x09, 0x03, 0x09, 0x07)); delay(180)
            sendFee2NativeRaw(nativePacket(0xB5, 0x00, 0x01, 0x07, 0x00, 0x00, 0x03, 38, 15, 39, *cityAscii.map { it.toInt() and 0xFF }.toIntArray())); delay(180)
            sendFee2NativeRaw(nativePacket(0x45, *displayAscii.map { it.toInt() and 0xFF }.toIntArray()))
        }
    }

    fun sendWeatherForecastSample() {
        managerScope.launch {
            sendFee2NativeRaw(nativePacket(0xB9, 0x19, 0x00)); delay(180)
            sendFee2NativeRaw(nativePacket(0x42, 0x00, 28, 18, 0x01, 26, 16, 0x02, 24, 14, 0x03, 22, 12, 0x04, 20, 10, 0x05, 18, 8, 0x06, 16, 6))
        }
    }

    fun sendGadgetbridgeProbe(kind: String) {
        val packet = when (kind) {
            "get-alarms" -> nativePacket(0x21); "get-step-goal" -> nativePacket(0x26); "get-auto-lock" -> nativePacket(0x8D)
            "time-12h" -> { _state.update { it.copy(is24HourFormat = false) }; nativePacket(0x17, 0x00) }
            "time-24h" -> { _state.update { it.copy(is24HourFormat = true) }; nativePacket(0x17, 0x01) }
            "quick-view-off" -> { _state.update { it.copy(quickViewEnabled = false) }; nativePacket(0x18, 0x00) }
            "quick-view-on" -> { _state.update { it.copy(quickViewEnabled = true) }; nativePacket(0x18, 0x01) }
            "auto-hr-10m" -> nativePacket(0x1F, 0x02); "move-reminder-on" -> nativePacket(0x1D, 0x01); "sync-history-3d" -> { managerScope.launch { listOf(0x32, 0x33, 0x51, 0x52, 0x53).forEach { c -> (0..3).forEach { o -> sendFee2NativeRaw(nativePacket(c, o)); delay(200) } } }; nativePacket(0x64) }
            else -> return
        }
        sendFee2NativeRaw(packet)
    }

    fun setAutoHeartRateInterval(minutes: Int) {
        val code = when (minutes) { 0 -> 0x00; 5 -> 0x01; 10 -> 0x02; 15 -> 0x03; 30 -> 0x04; 60 -> 0x05; else -> return }
        sendFee2NativeRaw(nativePacket(0x1F, code)); prefs.edit { putInt("autoHeartRateIntervalMinutes", minutes) }; _state.update { it.copy(autoHeartRateIntervalMinutes = minutes) }
    }

    fun setAlarm(slot: Int, enabled: Boolean, hour: Int, minute: Int, repeatMask: Int, month: Int = 0, day: Int = 0) {
        val sS = slot.coerceIn(0, 2); val sH = hour.coerceIn(0, 23); val sM = minute.coerceIn(0, 59); val sR = repeatMask and 0x7F; val mode = if (sR != 0) 0x02 else 0x00
        enqueueOperation(GattOperation.WriteCharacteristic(FEE2_WRITE, nativePacket(0x11, sS, if (enabled) 0x01 else 0x00, mode, sH, sM, month, day, sR)))
        _state.update { val alarms = it.alarmSettings.filterNot { a -> a.slot == sS } + WatchAlarm(sS, enabled, mode, sH, sM, sR); it.copy(alarmSettings = alarms.sortedBy { a -> a.slot }) }
    }

    fun readBattery() { bluetoothGatt?.services?.forEach { s -> s.getCharacteristic(BATTERY_CHAR)?.let { enqueueOperation(GattOperation.ReadCharacteristic(it)); return } } }
    fun startMeasurement(type: String) { updateDebugLog("$type app-start disabled. Use watch UI.") }
    fun stopMeasurement() { _state.update { it.copy(activeMeasurement = null) } }
    fun updateShutterAction(a: String) { prefs.edit { putString("shutterAction", a) }; _state.update { it.copy(shutterAction = a) } }
    fun updateMusicAction(a: String) { prefs.edit { putString("musicAction", a) }; _state.update { it.copy(musicAction = a) } }
    fun updateCustomAction(b: String, a: String) { when (b) { "Play/Pause" -> { prefs.edit { putString("playPauseAction", a) }; _state.update { it.copy(playPauseAction = a) } }; "Next" -> { prefs.edit { putString("nextAction", a) }; _state.update { it.copy(nextAction = a) } }; "Previous" -> { prefs.edit { putString("prevAction", a) }; _state.update { it.copy(prevAction = a) } } } }
    fun toggleAutoStart(e: Boolean) { prefs.edit { putBoolean("autoStart", e) }; _state.update { it.copy(autoStart = e) } }
    fun toggleAutoConnect(e: Boolean) { prefs.edit { putBoolean("autoConnect", e) }; _state.update { it.copy(autoConnect = e) } }
    fun toggleAutoFetchSteps(e: Boolean) { prefs.edit { putBoolean("autoFetchSteps", e) }; _state.update { it.copy(autoFetchSteps = e) }; restartAutoStepFetch() }
    fun toggleAutoFetchBattery(e: Boolean) { prefs.edit { putBoolean("autoFetchBattery", e) }; _state.update { it.copy(autoFetchBattery = e) } }
    fun toggleAutoFetchSleep(e: Boolean) { prefs.edit { putBoolean("autoFetchSleep", e) }; _state.update { it.copy(autoFetchSleep = e) } }
    fun updateStepFetchInterval(m: Int) { prefs.edit { putInt("stepFetchIntervalMinutes", m) }; _state.update { it.copy(stepFetchIntervalMinutes = m) }; restartAutoStepFetch() }
    fun toggleNotifications(e: Boolean) { prefs.edit { putBoolean("notificationsEnabled", e) }; _state.update { it.copy(notificationsEnabled = e) } }
    fun toggleIgnoreDuplicates(e: Boolean) { prefs.edit { putBoolean("ignoreDuplicateNotifications", e) }; _state.update { it.copy(ignoreDuplicateNotifications = e) } }
    fun toggleLegacyCallNotifications(e: Boolean) { prefs.edit { putBoolean("useLegacyCallNotifications", e) }; _state.update { it.copy(useLegacyCallNotifications = e) } }
    fun addNotificationFilter(pkg: String) { val f = _state.value.notificationFilters + pkg; prefs.edit { putStringSet("notificationFilters", f) }; _state.update { it.copy(notificationFilters = f) } }
    fun removeNotificationFilter(pkg: String) { val f = _state.value.notificationFilters - pkg; prefs.edit { putStringSet("notificationFilters", f) }; _state.update { it.copy(notificationFilters = f) } }
    fun registerDiscoveredApp(p: String, n: String) { if (_state.value.discoveredApps[p] == n) return; val a = _state.value.discoveredApps + (p to n); prefs.edit { putStringSet("discoveredApps", a.map { "${it.key}|${it.value}" }.toSet()) }; _state.update { it.copy(discoveredApps = a) } }
    fun updateBatteryThreshold(t: Int) { prefs.edit { putInt("batteryThreshold", t) }; _state.update { it.copy(batteryThreshold = t) } }
    fun updateBorderColor(c: Int) { prefs.edit { putInt("borderColor", c) }; _state.update { it.copy(borderColor = c) } }
    fun updateBorderThickness(t: Float) { prefs.edit { putFloat("borderThickness", t) }; _state.update { it.copy(borderThickness = t) } }
    fun updateBorderAlpha(a: Float) { prefs.edit { putFloat("borderAlpha", a) }; _state.update { it.copy(borderAlpha = a) } }
    fun updateProfile(g: String, h: Int, w: Float, a: Int) { prefs.edit { putString("profileGender", g); putInt("profileHeightCm", h); putFloat("profileWeightKg", w); putInt("profileAgeYears", a) }; _state.update { it.copy(profileGender = g, profileHeightCm = h, profileWeightKg = w, profileAgeYears = a) } }
    fun saveFood(food: FoodEntity) {
        managerScope.launch {
            val sanitized = food.sanitized()
            if (sanitized.id == 0L) foodDao.insert(sanitized) else foodDao.update(sanitized)
        }
    }
    fun deleteFood(id: Long) { managerScope.launch { foodDao.deleteById(id) } }
    fun setFoodEnabled(id: Long, enabled: Boolean) { managerScope.launch { foodDao.setEnabled(id, enabled) } }
    fun setFoodAvailableAmount(id: Long, amount: Float?) { managerScope.launch { foodDao.setAvailableAmount(id, amount?.coerceIn(0f, 1_000f)) } }
    fun setFoodOnShoppingList(id: Long, onShoppingList: Boolean) { managerScope.launch { foodDao.setOnShoppingList(id, onShoppingList) } }
    fun markFoodBought(id: Long) {
        managerScope.launch {
            val food = foodDao.getFood(id) ?: return@launch
            foodDao.setOnShoppingList(id, false)
            if (food.source == FoodSources.HOME) {
                val amount = ((food.availableAmount ?: 0f) + food.defaultAmount).coerceIn(0f, 1_000f)
                foodDao.setAvailableAmount(food.id, amount)
                foodDao.setEnabled(food.id, true)
                return@launch
            }

            val homeFood = foodDao.getFood(FoodSources.HOME, food.role, food.name)
            if (homeFood != null) {
                val amount = ((homeFood.availableAmount ?: 0f) + food.defaultAmount).coerceIn(0f, 1_000f)
                foodDao.update(homeFood.copy(enabled = true, availableAmount = amount))
            } else {
                foodDao.insert(
                    food.copy(
                        id = 0,
                        source = FoodSources.HOME,
                        enabled = true,
                        availableAmount = food.defaultAmount,
                        isCustom = true,
                        onShoppingList = false
                    ).sanitized()
                )
            }
        }
    }
    fun updateProtocol(h: String, u: String, p: Boolean) { _state.update { it.copy(protocolHeader = h, writeUuidShort = u, payloadLengthOnly = p) } }
    fun updateVolumeSteps(s: Int) { prefs.edit { putInt("volumeSteps", s) }; _state.update { it.copy(volumeSteps = s) } }
    fun toggleAutoSyncAlarm(e: Boolean) { prefs.edit { putBoolean("autoSyncAlarm", e) }; _state.update { it.copy(autoSyncAlarm = e) } }
    fun toggleAutoSyncTime(e: Boolean) { prefs.edit { putBoolean("autoSyncTime", e) }; _state.update { it.copy(autoSyncTime = e) }; restartAutoSyncTime() }
    fun updateSyncTimeInterval(h: Int) { prefs.edit { putInt("syncTimeIntervalHours", h) }; _state.update { it.copy(syncTimeIntervalHours = h) }; restartAutoSyncTime() }
    fun toggleMuteAlarmSyncNotification(e: Boolean) { prefs.edit { putBoolean("muteAlarmSyncNotification", e) }; _state.update { it.copy(muteAlarmSyncNotification = e) } }
    fun setFindingPhone(active: Boolean) { _state.update { it.copy(isFindingPhone = active) } }
    fun setServiceRunning(r: Boolean) { _state.update { it.copy(isServiceRunning = r) } }
    private fun FoodEntity.sanitized(): FoodEntity {
        val safeSource = source.takeIf { it in FoodSources.all } ?: FoodSources.HOME
        val safeRole = role.takeIf { it in FoodRoles.all } ?: FoodRoles.CARB
        return copy(
            name = name.trim().ifBlank { "Food" }.take(48),
            source = safeSource,
            role = safeRole,
            unitLabel = unitLabel.trim().ifBlank { "portion" }.take(16),
            kcalPerUnit = kcalPerUnit.coerceIn(1, 5_000),
            defaultAmount = defaultAmount.cleanAmount(fallback = 1f, min = 0.1f, max = 100f),
            stepSize = stepSize.cleanAmount(fallback = 1f, min = 0.1f, max = 50f),
            availableAmount = if (safeSource == FoodSources.HOME) availableAmount?.cleanAmount(fallback = 0f, min = 0f, max = 1_000f) else null,
            onShoppingList = onShoppingList
        )
    }
    private fun Float.cleanAmount(fallback: Float, min: Float, max: Float): Float {
        return if (isFinite()) coerceIn(min, max) else fallback
    }
    private fun updateDebugLog(msg: String) { Log.d(TAG, msg); val t = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()); synchronized(logBuffer) { logBuffer.add("[$t] $msg"); while (logBuffer.size > 100) logBuffer.removeAt(0); _state.update { it.copy(debugLog = logBuffer.joinToString("\n")) } } }
    private fun enqueueOperation(op: GattOperation) { synchronized(operationQueue) { operationQueue.add(op); if (!isOperating) doNextOperation() } }
    private fun doNextOperation() {
        synchronized(operationQueue) {
            if (isOperating) return; val op = operationQueue.poll() ?: return; isOperating = true; lastOpTime = System.currentTimeMillis()
            startOperationWatchdog()
            managerScope.launch {
                val gatt = bluetoothGatt ?: run { finishOperation(); return@launch }
                val code = when (op) {
                    is GattOperation.WriteDescriptor -> gatt.writeDescriptor(op.descriptor, op.value)
                    is GattOperation.ReadCharacteristic -> if (gatt.readCharacteristic(op.characteristic)) BluetoothStatusCodes.SUCCESS else BluetoothStatusCodes.ERROR_UNKNOWN
                    is GattOperation.WriteCharacteristic -> {
                        val uuid = (op.charUuid?.toString()?.substring(4, 8) ?: _state.value.writeUuidShort).lowercase()
                        gatt.services.flatMap { it.characteristics }.find { it.uuid.toString().substring(4, 8).lowercase() == uuid }?.let { gatt.writeCharacteristic(it, op.value, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) } ?: BluetoothStatusCodes.ERROR_UNKNOWN
                    }
                }
                if (code != BluetoothStatusCodes.SUCCESS) finishOperation()
            }
        }
    }
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(ct: Int, res: ScanResult) { if (res.device.name?.contains(TARGET_NAME, true) == true) { stopScan(); connectToDevice(res.device) } }
        override fun onScanFailed(err: Int) { updateDebugLog("Scan failed: $err"); scheduleReconnect(1500) }
    }
    fun startScan() {
        userRequestedDisconnect = false
        reconnectJob?.cancel()
        connectWatchdogJob?.cancel()
        if (adapter?.isEnabled != true) {
            _state.update { it.copy(isConnected = false, connectionStatus = "Bluetooth off") }
            updateDebugLog("Bluetooth is off; waiting to reconnect.")
            return
        }
        stopScan()
        _state.update { it.copy(connectionStatus = "Scanning...") }
        scanner?.startScan(
            null,
            android.bluetooth.le.ScanSettings.Builder().setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            scanCallback,
        )
        startScanWatchdog()
    }
    fun stopScan() {
        scanWatchdogJob?.cancel()
        scanner?.stopScan(scanCallback)
    }

    fun disconnect() {
        userRequestedDisconnect = true
        reconnectJob?.cancel()
        connectWatchdogJob?.cancel()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        stopScan()
        _state.update { it.copy(isConnected = false, connectionStatus = "Disconnected") }
    }

    @Suppress("DEPRECATION")
    private fun connectToDevice(device: BluetoothDevice) {
        userRequestedDisconnect = false
        reconnectJob?.cancel()
        connectWatchdogJob?.cancel()
        stopScan()
        lastConnectedDevice = device
        prefs.edit { putString(LAST_DEVICE_ADDRESS_KEY, device.address) }
        _state.update { it.copy(connectionStatus = "Connecting...", deviceName = device.name) }
        bluetoothGatt?.close()
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M_MASK)
        startConnectWatchdog()
    }
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, s: Int, ns: Int) {
            if (ns == BluetoothProfile.STATE_CONNECTED) { connectWatchdogJob?.cancel(); _state.update { it.copy(isConnected = true, connectionStatus = "Connected") }; gatt.discoverServices() }
            else if (ns == BluetoothProfile.STATE_DISCONNECTED) { bluetoothGatt = null; _state.update { it.copy(isConnected = false, connectionStatus = "Disconnected") }; synchronized(operationQueue) { operationQueue.clear(); isOperating = false }; gatt.close(); scheduleReconnect() }
        }
        override fun onServicesDiscovered(gatt: BluetoothGatt, s: Int) { if (s == BluetoothGatt.GATT_SUCCESS) setupChannels(gatt) }
        private fun setupChannels(gatt: BluetoothGatt) {
            gatt.services.forEach { s -> s.characteristics.forEach { c ->
                if (c.uuid != SKIP_NOTIFY_CHAR && (c.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0) {
                    gatt.setCharacteristicNotification(c, true)
                    c.getDescriptor(CLIENT_CONFIG_DESCRIPTOR)?.let { enqueueOperation(GattOperation.WriteDescriptor(it, if ((c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) }
                }
                if (c.uuid == BATTERY_CHAR) enqueueOperation(GattOperation.ReadCharacteristic(c))
            } }; restartAutoStepFetch(); restartAutoSyncTime()
        }
        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, s: Int) { finishOperation() }
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, s: Int) { finishOperation() }
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, v: ByteArray, s: Int) { if (s == BluetoothGatt.GATT_SUCCESS) decoder.decode(c.uuid, v); finishOperation() }
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, v: ByteArray) { logIncomingPacket(c.uuid, v); decoder.decode(c.uuid, v) }
    }
    private fun logIncomingPacket(uuid: UUID, data: ByteArray) {
        val short = uuid.toString().substring(4, 8).uppercase(); val hex = data.joinToString(" ") { "%02X".format(it) }
        if (isKnownNonUnknownPacket(uuid, data)) { updateDebugLog("RX $short raw=$hex"); return }
        updateDebugLog("RX $short raw=$hex"); addUnknownMessage("RX $short raw=$hex")
    }
    private fun isKnownNonUnknownPacket(uuid: UUID, data: ByteArray): Boolean {
        if (uuid == BATTERY_CHAR || uuid == FEE3_NOTIFY || uuid == FEE1_CHAR || uuid == FEA1_CHAR) return true
        if (uuid == ProtocolDecoder.UUID_HEART_RATE) return true
        return false
    }
    private fun addUnknownMessage(msg: String) { val t = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()); managerScope.launch { healthDao.insertUnknown(com.labbaslabs.jampsfit.database.UnknownPacket(message = "[$t] $msg")) } }
    fun ensureAutoConnect() {
        if (userRequestedDisconnect || !_state.value.autoConnect || _state.value.isConnected) return
        if (_state.value.connectionStatus == "Scanning..." || _state.value.connectionStatus == "Connecting...") return
        scheduleReconnect(0)
    }

    private fun scheduleReconnect(d: Long = 3000) {
        if (userRequestedDisconnect || !_state.value.autoConnect) return
        reconnectJob?.cancel()
        reconnectJob = managerScope.launch {
            delay(d)
            if (!_state.value.isConnected) {
                if (adapter?.isEnabled == true) {
                    connectKnownDeviceOrScan()
                } else {
                    _state.update { it.copy(connectionStatus = "Bluetooth off") }
                    delay(10_000)
                    scheduleReconnect(0)
                }
            }
        }
    }
    private fun connectKnownDeviceOrScan() {
        val address = prefs.getString(LAST_DEVICE_ADDRESS_KEY, null)
        val knownDevice = address?.let { runCatching { adapter?.getRemoteDevice(it) }.getOrNull() }
        if (knownDevice != null) {
            updateDebugLog("Reconnecting to remembered watch $address")
            connectToDevice(knownDevice)
        } else {
            startScan()
        }
    }
    private fun startScanWatchdog() { scanWatchdogJob?.cancel(); scanWatchdogJob = managerScope.launch { delay(12000); if (!_state.value.isConnected && _state.value.connectionStatus == "Scanning...") startScan() } }
    private fun startConnectWatchdog() {
        connectWatchdogJob?.cancel()
        connectWatchdogJob = managerScope.launch {
            delay(15_000)
            if (!_state.value.isConnected && _state.value.connectionStatus == "Connecting...") {
                updateDebugLog("Connect timed out; scanning again.")
                bluetoothGatt?.close()
                bluetoothGatt = null
                startScan()
            }
        }
    }
    private fun saveToDb(battery: Int? = null, heartRate: Int? = null, spo2: Int? = null, systolic: Int? = null, diastolic: Int? = null, steps: Int? = null, activityCount: Int? = null, distance: Int? = null, calories: Int? = null, sleepMinutes: Int? = null, deepSleepMinutes: Int? = null, lightSleepMinutes: Int? = null) {
        managerScope.launch {
            healthDao.insert(HealthEntry(battery = battery, heartRate = heartRate, spo2 = spo2, systolic = systolic, diastolic = diastolic, steps = steps, activityCount = activityCount, distance = distance, calories = calories, sleepMinutes = sleepMinutes, deepSleepMinutes = deepSleepMinutes, lightSleepMinutes = lightSleepMinutes))
            eventDao.getActiveEventOnce()?.let { refreshEventSummary(it, System.currentTimeMillis()) }
        }
    }

    private suspend fun refreshEventSummary(event: EventEntity, endTime: Long) {
        val summaryEnd = event.endTime ?: endTime
        val entries = healthDao.getEntriesBetween(event.startTime, summaryEnd)
        eventDao.update(
            summarizeEvent(
                event = event,
                healthEntries = entries,
                endTime = endTime,
                weightKg = _state.value.profileWeightKg
            )
        )
    }
    private fun finishOperation() {
        synchronized(operationQueue) { isOperating = false }
        operationWatchdogJob?.cancel()
        doNextOperation()
    }
    private fun startOperationWatchdog() {
        operationWatchdogJob?.cancel()
        operationWatchdogJob = managerScope.launch {
            delay(OPERATION_TIMEOUT_MS)
            synchronized(operationQueue) {
                if (!isOperating || System.currentTimeMillis() - lastOpTime < OPERATION_TIMEOUT_MS) return@launch
                isOperating = false
            }
            updateDebugLog("GATT operation timed out; continuing queue.")
            doNextOperation()
        }
    }
    private fun rememberRecentPayload(k: String, q: ArrayDeque<String>, s: MutableSet<String>): Boolean = synchronized(q) { if (!s.add(k)) true else { q.addLast(k); while (q.size > 32) s.remove(q.removeFirst()); false } }
    private fun nativePacket(cmd: Int, vararg p: Int): ByteArray = ByteArray(5 + p.size).apply { this[0] = 0xFE.toByte(); this[1] = 0xEA.toByte(); this[2] = 0x20.toByte(); this[3] = size.toByte(); this[4] = cmd.toByte(); p.forEachIndexed { i, v -> this[5 + i] = (v and 0xFF).toByte() } }
    private fun sendFee2NativeRaw(b: ByteArray) = enqueueOperation(GattOperation.WriteCharacteristic(FEE2_WRITE, b))
    fun sendLegacyShortNotification(title: String, text: String) { sendNotification(title, text, forceLegacy = true) }
    fun sendLegacyCallNotification(title: String, text: String) { sendNotification(title, text, forceLegacy = true, legacyType = 0x02) }
    fun clearUnknownPackets() { managerScope.launch { healthDao.deleteAllUnknown() } }
    fun sendRawTest(hex: String, useAltChar: Boolean = false) {
        val bytes = hex.split(" ").filter { it.isNotBlank() }.map { it.toInt(16).toByte() }.toByteArray()
        enqueueOperation(GattOperation.WriteCharacteristic(if (useAltChar) ProtocolDecoder.UUID_FEE3 else null, bytes)) // Placeholder logic
    }
}
