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
import com.labbaslabs.jampsfit.database.CandyEntity
import com.labbaslabs.jampsfit.database.DEFAULT_DANCING_EVENT_NAME
import com.labbaslabs.jampsfit.database.DEFAULT_FESTIVAL_NAME
import com.labbaslabs.jampsfit.database.DEFAULT_WATCH_EXERCISE_NAME
import com.labbaslabs.jampsfit.database.EVENT_TYPE_DANCING
import com.labbaslabs.jampsfit.database.EVENT_TYPE_WATCH_EXERCISE
import com.labbaslabs.jampsfit.database.EventEntity
import com.labbaslabs.jampsfit.database.FestivalEntity
import com.labbaslabs.jampsfit.database.FoodEntity
import com.labbaslabs.jampsfit.database.FoodRoles
import com.labbaslabs.jampsfit.database.FoodSources
import com.labbaslabs.jampsfit.database.HealthEntry
import com.labbaslabs.jampsfit.database.HealthSyncQueueEntry
import com.labbaslabs.jampsfit.database.toCandy
import com.labbaslabs.jampsfit.database.toEvent
import com.labbaslabs.jampsfit.database.toFood
import com.labbaslabs.jampsfit.database.toFestival
import com.labbaslabs.jampsfit.database.toHealthEntry
import com.labbaslabs.jampsfit.database.toJson
import com.labbaslabs.jampsfit.database.toMeal
import com.labbaslabs.jampsfit.database.toSupplement
import com.labbaslabs.jampsfit.database.HistoryPoint
import com.labbaslabs.jampsfit.database.MealEntity
import com.labbaslabs.jampsfit.database.SupplementEntity
import com.labbaslabs.jampsfit.database.SupplementEntryEntity
import com.labbaslabs.jampsfit.database.defaultFoods
import com.labbaslabs.jampsfit.database.defaultSupplements
import com.labbaslabs.jampsfit.events.summarizeEvent
import com.labbaslabs.jampsfit.workout.inferLatestWorkout
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
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
    val lastWatchSeenTime: Long? = null,
    val firmwareVersion: String? = null,
    val watchFaceId: Int? = null,
    val connectionStatus: String = "Disconnected",
    val connectionDetail: String? = null,
    val reconnectAttempt: Int = 0,
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
    val autoFetchSleep: Boolean = true,
    val stepFetchIntervalMinutes: Int = 60,
    val autoHeartRateIntervalMinutes: Int = 0,
    val autoHeartRateReactivationMinutes: Int = 0,
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
    val pendingHealthSyncCount: Int = 0,
    val lastHealthSyncError: String? = null,
    val lastHealthSyncTime: Long? = null,
    val healthRetentionDays: Int = 180,
    val activityHistory: List<HistoryPoint> = emptyList(),
    val last24hStats: List<HealthEntry> = emptyList(),
    val dailyStats: List<HealthEntry> = emptyList(),
    val weeklyStats: List<HealthEntry> = emptyList(),
    val monthlyStats: List<HealthEntry> = emptyList(),
    val activeEvent: EventEntity? = null,
    val recentEvents: List<EventEntity> = emptyList(),
    val candies: List<CandyEntity> = emptyList(),
    val meals: List<MealEntity> = emptyList(),
    val supplements: List<SupplementEntity> = emptyList(),
    val supplementEntries: List<SupplementEntryEntity> = emptyList(),
    val festivals: List<FestivalEntity> = emptyList(),
    val selectedFestivalId: Long? = null,
    val foods: List<FoodEntity> = emptyList(),
    val eatShowHome: Boolean = true,
    val eatShowStore: Boolean = true,
    val eatShowFastFood: Boolean = false,
    val appliedMealCalories: Int = 0,
    val eatCaloriesIncremental: Boolean = false,
    val calorieBaseline: Int = 0,
    val hrReminderEnabled: Boolean = false,
    val hrReminderIntervalMinutes: Int = 60,
    val doubleConfirmationsEnabled: Boolean = false,
    val shoppingListCheckedIds: Set<Long> = emptySet(),
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
    private val initialMealDay = mealDayKey()
    private val initialLastHealthSyncTime = prefs.getLong("lastHealthSyncTime", 0L).takeIf { it > 0L }
    private val initialEatCaloriesIncremental = prefs.getBoolean("eatCaloriesIncremental", false)
    private val initialAppliedMealCalories = prefs.getInt("appliedMealCalories", 0)
        .takeIf { initialEatCaloriesIncremental || prefs.getString("appliedMealCaloriesDay", initialMealDay) == initialMealDay }
        ?: 0
    private var calorieCarryOffset = prefs.getInt("calorieCarryOffset", 0)
    private var lastRawCalories = prefs.getInt("lastRawCalories", 0)
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scanner get() = adapter?.bluetoothLeScanner

    private val _state = MutableStateFlow(WatchState(
        autoStart = prefs.getBoolean("autoStart", true),
        autoConnect = prefs.getBoolean("autoConnect", true),
        autoFetchSteps = prefs.getBoolean("autoFetchSteps", false),
        autoFetchBattery = prefs.getBoolean("autoFetchBattery", false),
        autoFetchSleep = prefs.getBoolean("autoFetchSleep", true),
        stepFetchIntervalMinutes = prefs.getInt("stepFetchIntervalMinutes", 60),
        autoHeartRateIntervalMinutes = prefs.getInt("autoHeartRateIntervalMinutes", 0),
        autoHeartRateReactivationMinutes = prefs.getInt("autoHeartRateReactivationMinutes", 0),
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
        quickViewEndMinute = prefs.getInt("quickViewEndMinute", 0),
        eatShowHome = prefs.getBoolean("eatShowHome", true),
        eatShowStore = prefs.getBoolean("eatShowStore", true),
        eatShowFastFood = prefs.getBoolean("eatShowFastFood", false),
        appliedMealCalories = initialAppliedMealCalories,
        eatCaloriesIncremental = initialEatCaloriesIncremental,
        calorieBaseline = prefs.getInt("calorieBaseline", 0),
        hrReminderEnabled = prefs.getBoolean("hrReminderEnabled", false),
        hrReminderIntervalMinutes = prefs.getInt("hrReminderIntervalMinutes", 60),
        doubleConfirmationsEnabled = prefs.getBoolean("doubleConfirmationsEnabled", false),
        shoppingListCheckedIds = prefs.getStringSet("shoppingListCheckedIds", emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet(),
        lastHealthSyncTime = initialLastHealthSyncTime
        ,healthRetentionDays = prefs.getInt("healthRetentionDays", 180)
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
    private var autoSleepFetchJob: Job? = null
    private var hrReminderJob: Job? = null
    private var autoHeartRateReactivationJob: Job? = null
    private val heavyObserverJobs = mutableListOf<Job>()
    private var appForegrounded = false
    private var reconnectAttempt = 0
    private var activeEventSummaryJob: Job? = null
    private var lastEventSummaryTime = 0L
    private var lastPersistedHealthEntry: HealthEntry? = null
    private var sleepFetchedDayKey: String? = null
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
        seedDefaultSupplements()
        seedDefaultFestival()
        observeHistory()
        observeHealthSyncQueue()
        startHealthSyncWorker()
        observeEvents()
        observeFoods()
        cleanupSeenNotifications()
        checkFullScreenIntentPermission()
        updateDebugLog("WatchManager build: modular-decoder-v2")
    }

    private fun handleDecodedResult(result: ProtocolDecoder.DecodedResult) {
        when (result) {
            is ProtocolDecoder.DecodedResult.Battery -> {
                reconnectAttempt = 0
                _state.update { it.copy(battery = result.level, lastWatchSeenTime = System.currentTimeMillis()) }
                saveToDb(battery = result.level)
                updateDebugLog("Battery: ${result.level}%")
            }
            is ProtocolDecoder.DecodedResult.HeartRate -> {
                reconnectAttempt = 0
                _state.update { it.copy(heartRate = result.bpm, lastWatchSeenTime = System.currentTimeMillis()) }
                saveToDb(heartRate = result.bpm)
                persistWatchExerciseFromHeartRate()
                updateDebugLog("Heart Rate: ${result.bpm} bpm")
            }
            is ProtocolDecoder.DecodedResult.SpO2 -> {
                _state.update { it.copy(spo2 = result.percent, lastWatchSeenTime = System.currentTimeMillis()) }
                saveToDb(spo2 = result.percent)
                updateDebugLog("SpO2: ${result.percent}%")
            }
            is ProtocolDecoder.DecodedResult.BloodPressure -> {
                _state.update { it.copy(systolic = result.systolic, diastolic = result.diastolic, lastWatchSeenTime = System.currentTimeMillis()) }
                saveToDb(systolic = result.systolic, diastolic = result.diastolic)
                updateDebugLog("BP: ${result.systolic}/${result.diastolic}")
            }
            is ProtocolDecoder.DecodedResult.Activity -> {
                if (rememberRecentPayload(result.seq.toString(), recentActivityPayloads, recentActivityPayloadSet)) return
                val calories = adjustedCalories(result.calories)
                _state.update { it.copy(activityCount = result.activityCount, distance = result.distance, calories = calories, lastWatchSeenTime = System.currentTimeMillis()) }
                saveToDb(activityCount = result.activityCount, distance = result.distance, calories = result.calories)
                updateDebugLog("Activity: seq=${result.seq} count=${result.activityCount} dist=${result.distance}m cal=$calories")
            }
            is ProtocolDecoder.DecodedResult.DailyTotals -> {
                val calories = adjustedCalories(result.calories)
                _state.update { it.copy(steps = result.steps, distance = result.distance, calories = calories, lastWatchSeenTime = System.currentTimeMillis()) }
                saveToDb(steps = result.steps, distance = result.distance, calories = result.calories)
                updateDebugLog("Daily Totals: steps=${result.steps} dist=${result.distance}m cal=$calories")
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
                saveToDb(sleepMinutes = result.total, deepSleepMinutes = result.deep, lightSleepMinutes = result.light, sleepSegments = result.segments)
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
            is ProtocolDecoder.DecodedResult.WatchExerciseSummary -> {
                persistWatchExerciseSummary(result)
                updateDebugLog("Watch exercise: type=${result.sportType} duration=${result.durationSeconds}s kcal=${result.calories}")
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
            val retentionDays = prefs.getInt("healthRetentionDays", 180).coerceIn(7, 3650)
            val retentionThreshold = System.currentTimeMillis() - (retentionDays.toLong() * 24 * 60 * 60 * 1000)
            healthDao.cleanupOldNotifications(oneMonthAgo)
            healthDao.cleanupOldHealthData(retentionThreshold)
            healthDao.trimUnknownPackets(500)
        }
    }

    private fun observeHistory() {
        managerScope.launch { healthDao.getHeartRateHistory().collect { h -> _state.update { it.copy(heartRateHistory = h.reversed()) } } }
        managerScope.launch {
            healthDao.observeLatestSleepSegments().collect { entry ->
                entry?.sleepSegmentsJson?.let { encoded ->
                    _state.update { it.copy(sleepSegments = decodeSleepSegments(encoded)) }
                }
            }
        }
    }

    private fun observeHealthSyncQueue() {
        managerScope.launch {
            healthDao.observePendingHealthSync().collect { count ->
                _state.update { it.copy(pendingHealthSyncCount = count) }
            }
        }
    }

    private fun startHealthSyncWorker() {
        managerScope.launch {
            while (isActive) {
                val queued = healthDao.getNextQueuedHealth(System.currentTimeMillis())
                if (queued == null) {
                    delay(2_000L)
                    continue
                }
                try {
                    healthDao.drainQueuedHealth(queued)
                    lastPersistedHealthEntry = queued.toHealthEntry()
                    val syncedAt = System.currentTimeMillis()
                    prefs.edit { putLong("lastHealthSyncTime", syncedAt) }
                    _state.update { it.copy(lastHealthSyncError = null, lastHealthSyncTime = syncedAt) }
                } catch (error: Exception) {
                    val attempts = queued.attempts + 1
                    val delayMs = (1L shl attempts.coerceAtMost(8)) * 1_000L
                    val message = error.message ?: error::class.simpleName ?: "Unknown database error"
                    healthDao.retryQueuedHealth(queued.id, attempts, System.currentTimeMillis() + delayMs, message)
                    _state.update { it.copy(lastHealthSyncError = message) }
                    delay(delayMs.coerceAtMost(60_000L))
                }
            }
        }
    }

    private fun startHeavyObservers() {
        if (heavyObserverJobs.isNotEmpty()) return
        heavyObserverJobs += managerScope.launch { healthDao.getBatteryHistory().collect { h -> _state.update { it.copy(batteryHistory = h.reversed()) } } }
        heavyObserverJobs += managerScope.launch { healthDao.getSpO2History().collect { h -> _state.update { it.copy(spo2History = h.reversed()) } } }
        heavyObserverJobs += managerScope.launch { healthDao.getBloodPressureHistory().collect { h -> _state.update { it.copy(bpHistory = h.reversed()) } } }
        heavyObserverJobs += managerScope.launch { healthDao.getStepsHistory().collect { h -> _state.update { it.copy(stepsHistory = h.reversed()) } } }
        heavyObserverJobs += managerScope.launch { healthDao.getDistanceHistory().collect { h -> _state.update { it.copy(distanceHistory = h.reversed()) } } }
        heavyObserverJobs += managerScope.launch { healthDao.getActivityHistory().collect { h -> _state.update { it.copy(activityHistory = h.reversed()) } } }
        heavyObserverJobs += managerScope.launch { healthDao.getCaloriesHistory().collect { h -> _state.update { it.copy(caloriesHistory = h.reversed()) } } }
        heavyObserverJobs += managerScope.launch { healthDao.getLast24hStats().collect { h -> _state.update { it.copy(last24hStats = h.reversed()) } } }
        heavyObserverJobs += managerScope.launch { healthDao.getDailyStats().collect { h -> _state.update { it.copy(dailyStats = h.reversed()) } } }
        heavyObserverJobs += managerScope.launch { healthDao.getWeeklyStats().collect { h -> _state.update { it.copy(weeklyStats = h.reversed()) } } }
        heavyObserverJobs += managerScope.launch { healthDao.getMonthlyStats().collect { h -> _state.update { it.copy(monthlyStats = h.reversed()) } } }
        heavyObserverJobs += managerScope.launch { healthDao.getAllUnknownPackets().collect { h -> _state.update { it.copy(unknownMessages = h) } } }
    }

    private fun stopHeavyObservers() {
        heavyObserverJobs.forEach { it.cancel() }
        heavyObserverJobs.clear()
    }

    fun setAppForegrounded(foregrounded: Boolean) {
        if (appForegrounded == foregrounded) return
        appForegrounded = foregrounded
        if (foregrounded) {
            startHeavyObservers()
        } else {
            stopHeavyObservers()
        }
        restartAutoStepFetch()
        restartAutoSyncTime()
        restartAutoSleepFetch()
        restartAutoHeartRateReactivation()
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
            eventDao.observeCandies().collect { candies ->
                _state.update { it.copy(candies = candies) }
            }
        }
        managerScope.launch {
            eventDao.observeMeals().collect { meals ->
                _state.update { it.copy(meals = meals) }
            }
        }
        managerScope.launch {
            eventDao.observeSupplements().collect { supplements ->
                _state.update { it.copy(supplements = supplements) }
            }
        }
        managerScope.launch {
            eventDao.observeSupplementEntries().collect { entries ->
                _state.update { it.copy(supplementEntries = entries) }
            }
        }
        managerScope.launch {
            eventDao.observeFestivals().collect { festivals ->
                _state.update { state ->
                    state.copy(
                        festivals = festivals,
                        selectedFestivalId = state.selectedFestivalId
                            ?.takeIf { selected -> festivals.any { it.id == selected } }
                            ?: festivals.firstOrNull { it.isActive }?.id
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
                val id = eventDao.insertFestival(FestivalEntity(isActive = true, createdAt = now, updatedAt = now))
                eventDao.setActiveFestival(id, now)
                _state.update { it.copy(selectedFestivalId = id) }
            }
        }
    }

    private fun seedDefaultFoods() {
        managerScope.launch {
            val defaults = defaultFoods()
            if (foodDao.countFoods() == 0) {
                foodDao.insertAll(defaults)
                return@launch
            }

            defaults.forEach { food ->
                val existing = foodDao.getFood(food.source, food.role, food.name)
                if (existing == null) {
                    foodDao.insert(food)
                } else if (!existing.isCustom && existing != food.copy(id = existing.id)) {
                    foodDao.update(food.copy(id = existing.id))
                }
            }
        }
    }

    private fun seedDefaultSupplements() {
        managerScope.launch {
            val defaults = defaultSupplements()
            if (eventDao.countSupplements() == 0) {
                defaults.forEach { eventDao.insertSupplement(it) }
                return@launch
            }

            defaults.forEach { supplement ->
                if (eventDao.getSupplementByName(supplement.name) == null) {
                    eventDao.insertSupplement(supplement)
                }
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
            val festivalId = currentTargetFestivalId(now)
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
                    name = "Event $count",
                    isActive = true,
                    createdAt = now,
                    updatedAt = now
                )
            )
            eventDao.setActiveFestival(id, now)
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

    fun setFestivalActive(id: Long) {
        managerScope.launch {
            if (eventDao.getFestival(id) == null) return@launch
            val now = System.currentTimeMillis()
            eventDao.setActiveFestival(id, now)
            _state.update { it.copy(selectedFestivalId = id) }
            updateDebugLog("Festival $id activated")
        }
    }

    fun deleteFestival(id: Long) {
        managerScope.launch {
            val festival = eventDao.getFestival(id) ?: return@launch
            eventDao.detachEventsFromFestival(id)
            eventDao.detachCandiesFromFestival(id)
            eventDao.detachMealsFromFestival(id)
            eventDao.detachSupplementEntriesFromFestival(id)
            eventDao.deleteFestival(id)
            val replacement = eventDao.getActiveFestival() ?: eventDao.getNewestFestival()
            replacement?.let {
                if (festival.isActive) eventDao.setActiveFestival(it.id, System.currentTimeMillis())
            }
            _state.update { it.copy(selectedFestivalId = replacement?.id) }
            updateDebugLog("Festival $id deleted")
        }
    }

    fun stopActiveEvent() {
        managerScope.launch {
            val activeEvents = eventDao.getActiveEventsOnce()
            val active = activeEvents.firstOrNull() ?: run {
                updateDebugLog("No active dancing event to stop")
                return@launch
            }
            val now = System.currentTimeMillis()
            _state.update { it.copy(activeEvent = null) }
            refreshEventSummary(active.copy(endTime = now), now)
            activeEvents.drop(1).forEach { event ->
                refreshEventSummary(event.copy(endTime = now), now)
            }
            updateDebugLog("Dancing event stopped id=${active.id}")
        }
    }

    fun attachEventToSelectedFestival(eventId: Long) {
        managerScope.launch {
            val now = System.currentTimeMillis()
            val festivalId = currentTargetFestivalId(now)
            eventDao.attachEventToFestival(eventId, festivalId, now)
            updateDebugLog("Event $eventId attached to festival $festivalId")
        }
    }

    fun attachEventToFestival(eventId: Long, festivalId: Long) {
        managerScope.launch {
            if (eventDao.getFestival(festivalId) == null) return@launch
            eventDao.attachEventToFestival(eventId, festivalId, System.currentTimeMillis())
            updateDebugLog("Event $eventId moved to festival $festivalId")
        }
    }

    fun deleteEvent(eventId: Long) {
        managerScope.launch {
            eventDao.deleteEvent(eventId)
            updateDebugLog("Event $eventId deleted")
        }
    }

    fun addCandy(name: String, size: Int, hours: Int) {
        managerScope.launch {
            val now = System.currentTimeMillis()
            val festivalId = currentTargetFestivalId(now)
            eventDao.insertCandy(
                CandyEntity(
                    festivalId = festivalId,
                    name = name.trim().ifBlank { "Candy" }.take(32),
                    startTime = now,
                    endTime = now + hours.coerceIn(0, 99) * 60L * 60L * 1000L,
                    size = size.coerceIn(0, 9_999),
                    createdAt = now
                )
            )
            updateDebugLog("Candy recorded: ${name.trim().ifBlank { "Candy" }}")
        }
    }

    fun deleteCandy(id: Long) {
        managerScope.launch {
            eventDao.deleteCandy(id)
            updateDebugLog("Candy $id deleted")
        }
    }

    fun addMeal(name: String, type: String, calories: Int, details: String) {
        managerScope.launch {
            val now = System.currentTimeMillis()
            val festivalId = currentTargetFestivalId(now)
            eventDao.insertMeal(
                MealEntity(
                    festivalId = festivalId,
                    name = name.trim().ifBlank { "Meal" }.take(48),
                    type = type.trim().ifBlank { "Meal" }.take(24),
                    calories = calories.coerceIn(0, 100_000),
                    details = details.trim().take(1_000),
                    createdAt = now
                )
            )
            updateDebugLog("Meal saved: ${name.trim().ifBlank { "Meal" }}")
        }
    }

    fun deleteMeal(id: Long) {
        managerScope.launch {
            eventDao.deleteMeal(id)
            updateDebugLog("Meal $id deleted")
        }
    }

    fun saveSupplement(supplement: SupplementEntity) {
        managerScope.launch {
            val sanitized = supplement.sanitized()
            if (sanitized.id == 0L) {
                eventDao.insertSupplement(sanitized)
            } else {
                eventDao.updateSupplement(sanitized)
            }
        }
    }

    fun takeSupplement(id: Long) {
        managerScope.launch {
            val supplement = eventDao.getSupplement(id) ?: return@launch
            val now = System.currentTimeMillis()
            val festivalId = currentTargetFestivalId(now)
            eventDao.insertSupplementEntry(
                SupplementEntryEntity(
                    festivalId = festivalId,
                    supplementId = supplement.id,
                    name = supplement.name,
                    amountMg = supplement.selectedAmountMg.coerceAtLeast(1),
                    takenAt = now
                )
            )
            updateDebugLog("Supplement taken: ${supplement.name} ${supplement.selectedAmountMg}mg")
        }
    }

    fun deleteSupplementEntry(id: Long) {
        managerScope.launch {
            eventDao.deleteSupplementEntry(id)
            updateDebugLog("Supplement entry $id deleted")
        }
    }

    fun reorderSupplements(ids: List<Long>) {
        managerScope.launch {
            val now = System.currentTimeMillis()
            ids.forEachIndexed { index, id ->
                eventDao.updateSupplementOrder(id, index, now)
            }
        }
    }

    private suspend fun currentTargetFestivalId(now: Long): Long {
        return eventDao.getActiveFestival()?.id
            ?: _state.value.selectedFestivalId?.takeIf { eventDao.getFestival(it) != null }
            ?: eventDao.getNewestFestival()?.id
            ?: eventDao.insertFestival(FestivalEntity(isActive = true, createdAt = now, updatedAt = now)).also {
                eventDao.setActiveFestival(it, now)
            }
    }

    private fun persistWatchExerciseFromHeartRate() {
        managerScope.launch {
            val workout = inferLatestWorkout(_state.value) ?: return@launch
            val now = System.currentTimeMillis()
            if (now - workout.endTime > 2 * 60_000L) return@launch
            val festivalId = currentTargetFestivalId(now)
            val existing = eventDao.findOpenEventByType(EVENT_TYPE_WATCH_EXERCISE)
                ?: eventDao.findEventOverlapping(EVENT_TYPE_WATCH_EXERCISE, workout.startTime, workout.endTime, 2 * 60_000L)
                ?: eventDao.findEventNearStart(EVENT_TYPE_WATCH_EXERCISE, workout.startTime, 15_000L)
            val startTime = existing?.startTime?.let { minOf(it, workout.startTime) } ?: workout.startTime
            val endTime = if (now - workout.endTime <= 90_000L) null else workout.endTime
            val durationSeconds = maxOf(
                existing?.durationSeconds ?: 0,
                ((workout.endTime - startTime) / 1000L).toInt().coerceAtLeast(workout.durationSeconds)
            )
            val event = EventEntity(
                id = existing?.id ?: 0L,
                festivalId = existing?.festivalId ?: festivalId,
                type = EVENT_TYPE_WATCH_EXERCISE,
                name = DEFAULT_WATCH_EXERCISE_NAME,
                startTime = startTime,
                endTime = endTime,
                durationSeconds = durationSeconds,
                heartRateSamples = maxOf(existing?.heartRateSamples ?: 0, workout.sampleCount),
                averageBpm = workout.averageBpm,
                minBpm = minOf(existing?.minBpm ?: workout.minBpm, workout.minBpm),
                maxBpm = maxOf(existing?.maxBpm ?: workout.maxBpm, workout.maxBpm),
                estimatedWorkoutCalories = maxOf(existing?.estimatedWorkoutCalories ?: 0, workout.estimatedCalories),
                lastUpdatedTime = now
            )
            if (existing == null) eventDao.insert(event) else eventDao.update(event)
        }
    }

    private fun persistWatchExerciseSummary(summary: ProtocolDecoder.DecodedResult.WatchExerciseSummary) {
        managerScope.launch {
            val now = System.currentTimeMillis()
            val startTime = now - summary.durationSeconds * 1000L
            val festivalId = currentTargetFestivalId(now)
            val existing = eventDao.findOpenEventByType(EVENT_TYPE_WATCH_EXERCISE)
                ?: eventDao.findEventOverlapping(EVENT_TYPE_WATCH_EXERCISE, startTime, now, 2 * 60_000L)
                ?: eventDao.findEventNearStart(EVENT_TYPE_WATCH_EXERCISE, startTime, 30_000L)
            val eventStartTime = existing?.startTime?.let { minOf(it, startTime) } ?: startTime
            val event = EventEntity(
                id = existing?.id ?: 0L,
                festivalId = existing?.festivalId ?: festivalId,
                type = EVENT_TYPE_WATCH_EXERCISE,
                name = "$DEFAULT_WATCH_EXERCISE_NAME ${summary.sportType}",
                startTime = eventStartTime,
                endTime = now,
                durationSeconds = maxOf(summary.durationSeconds, ((now - eventStartTime) / 1000L).toInt()),
                stepDelta = summary.steps,
                distanceDelta = summary.distance,
                calorieDelta = summary.calories,
                heartRateSamples = maxOf(existing?.heartRateSamples ?: 0, if (summary.averageBpm != null) 1 else 0),
                averageBpm = summary.averageBpm ?: existing?.averageBpm,
                minBpm = listOfNotNull(existing?.minBpm, summary.minBpm).minOrNull(),
                maxBpm = listOfNotNull(existing?.maxBpm, summary.maxBpm).maxOrNull(),
                estimatedWorkoutCalories = existing?.estimatedWorkoutCalories ?: 0,
                lastUpdatedTime = now
            )
            if (existing == null) eventDao.insert(event) else eventDao.update(event)
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
        autoSleepFetchJob?.cancel()
        hrReminderJob?.cancel()
        autoHeartRateReactivationJob?.cancel()
        activeEventSummaryJob?.cancel()
        stopHeavyObservers()
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

    suspend fun exportDataToJson(): String {
        val root = JSONObject().put("format", "jampsFit-backup-v1").put("createdAt", System.currentTimeMillis())
        root.put("health", JSONArray(healthDao.getAllEntriesList().map { it.toJson() }))
        root.put("foods", JSONArray(foodDao.observeFoods().first().map { it.toJson() }))
        root.put("festivals", JSONArray(eventDao.observeFestivals().first().map { it.toJson() }))
        root.put("events", JSONArray(eventDao.observeRecentEvents().first().map { it.toJson() }))
        root.put("meals", JSONArray(eventDao.observeMeals().first().map { it.toJson() }))
        root.put("candies", JSONArray(eventDao.observeCandies().first().map { it.toJson() }))
        root.put("supplements", JSONArray(eventDao.observeSupplements().first().map { it.toJson() }))
        return root.toString()
    }

    suspend fun importDataFromJson(json: String): Int {
        val root = JSONObject(json)
        require(root.optString("format") == "jampsFit-backup-v1") { "Unsupported backup format" }
        root.optJSONArray("health")?.let { array ->
            for (i in 0 until array.length()) healthDao.insert(array.getJSONObject(i).toHealthEntry())
        }
        root.optJSONArray("foods")?.let { array ->
            val foods = buildList { for (i in 0 until array.length()) add(array.getJSONObject(i).toFood()) }
            foodDao.insertAll(foods)
        }
        root.optJSONArray("festivals")?.let { array ->
            eventDao.insertFestivals(buildList { for (i in 0 until array.length()) add(array.getJSONObject(i).toFestival()) })
        }
        root.optJSONArray("events")?.let { array ->
            eventDao.insertEvents(buildList { for (i in 0 until array.length()) add(array.getJSONObject(i).toEvent()) })
        }
        root.optJSONArray("meals")?.let { array ->
            eventDao.insertMeals(buildList { for (i in 0 until array.length()) add(array.getJSONObject(i).toMeal()) })
        }
        root.optJSONArray("candies")?.let { array ->
            eventDao.insertCandies(buildList { for (i in 0 until array.length()) add(array.getJSONObject(i).toCandy()) })
        }
        root.optJSONArray("supplements")?.let { array ->
            eventDao.insertSupplements(buildList { for (i in 0 until array.length()) add(array.getJSONObject(i).toSupplement()) })
        }
        return root.keys().asSequence().count()
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
        autoStepFetchJob = managerScope.launch {
            while (isActive) {
                queryCurrentSteps()
                val interval = _state.value.stepFetchIntervalMinutes.coerceIn(5, 1440) * 60_000L
                delay(if (appForegrounded) interval else maxOf(interval, 30 * 60_000L))
            }
        }
    }

    private fun restartAutoSyncTime() {
        autoSyncTimeJob?.cancel(); if (!_state.value.autoSyncTime || !_state.value.isConnected) return
        autoSyncTimeJob = managerScope.launch {
            while (isActive) {
                syncTime()
                val interval = _state.value.syncTimeIntervalHours.coerceIn(1, 24) * 3600_000L
                delay(if (appForegrounded) interval else maxOf(interval, 12 * 3600_000L))
            }
        }
    }

    private fun restartAutoSleepFetch() {
        autoSleepFetchJob?.cancel()
        if (!_state.value.autoFetchSleep || !_state.value.isConnected) return
        autoSleepFetchJob = managerScope.launch {
            while (isActive) {
                val today = mealDayKey()
                val shouldFetch = appForegrounded || sleepFetchedDayKey != today || isLikelySleepWakeWindow()
                if (shouldFetch) {
                    querySleepBoundaries()
                    sleepFetchedDayKey = today
                }
                delay(if (appForegrounded) 30 * 60_000L else 6 * 3600_000L)
            }
        }
    }

    private fun restartHrReminder() {
        hrReminderJob?.cancel()
        if (!_state.value.hrReminderEnabled || !_state.value.isConnected) return
        hrReminderJob = managerScope.launch {
            while (isActive) {
                val intervalMs = _state.value.hrReminderIntervalMinutes.coerceIn(15, 720) * 60_000L
                delay(intervalMs)
                val lastHeartRateTime = _state.value.heartRateHistory.maxOfOrNull { it.timestamp } ?: 0L
                if (_state.value.isConnected && System.currentTimeMillis() - lastHeartRateTime >= intervalMs) {
                    sendNotification(
                        title = "Measure HR",
                        text = "No heart-rate measurement for ${_state.value.hrReminderIntervalMinutes} min",
                        ignoreDuplicate = true,
                        forceMirrored = true
                    )
                }
            }
        }
    }

    private fun restartAutoHeartRateReactivation() {
        autoHeartRateReactivationJob?.cancel()
        val reactivationMinutes = _state.value.autoHeartRateReactivationMinutes
        if (!_state.value.isConnected || reactivationMinutes <= 0) return
        val intervalMinutes = _state.value.autoHeartRateIntervalMinutes
        if (intervalMinutes !in listOf(5, 10)) return
        val commandCode = autoHeartRateIntervalCode(intervalMinutes) ?: return
        autoHeartRateReactivationJob = managerScope.launch {
            val intervalMs = reactivationMinutes * 60_000L
            while (isActive) {
                delay(if (appForegrounded) intervalMs else maxOf(intervalMs, 6 * 3600_000L))
                if (_state.value.isConnected && _state.value.autoHeartRateIntervalMinutes == intervalMinutes) {
                    sendFee2NativeRaw(nativePacket(0x1F, commandCode))
                    updateDebugLog("Auto HR ${intervalMinutes}m re-sent")
                }
            }
        }
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
        val code = autoHeartRateIntervalCode(minutes) ?: return
        sendFee2NativeRaw(nativePacket(0x1F, code)); prefs.edit { putInt("autoHeartRateIntervalMinutes", minutes) }; _state.update { it.copy(autoHeartRateIntervalMinutes = minutes) }
        restartAutoHeartRateReactivation()
    }

    private fun autoHeartRateIntervalCode(minutes: Int): Int? = when (minutes) { 0 -> 0x00; 5 -> 0x01; 10 -> 0x02; else -> null }

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
    fun toggleAutoFetchSleep(e: Boolean) { prefs.edit { putBoolean("autoFetchSleep", e) }; _state.update { it.copy(autoFetchSleep = e) }; restartAutoSleepFetch() }
    fun updateStepFetchInterval(m: Int) { prefs.edit { putInt("stepFetchIntervalMinutes", m) }; _state.update { it.copy(stepFetchIntervalMinutes = m) }; restartAutoStepFetch() }
    fun updateAutoHeartRateReactivationInterval(minutes: Int) {
        val safe = if (minutes in listOf(0, 15, 30, 60, 120, 180, 240, 300, 360)) minutes else return
        prefs.edit { putInt("autoHeartRateReactivationMinutes", safe) }
        _state.update { it.copy(autoHeartRateReactivationMinutes = safe) }
        restartAutoHeartRateReactivation()
    }
    fun toggleNotifications(e: Boolean) { prefs.edit { putBoolean("notificationsEnabled", e) }; _state.update { it.copy(notificationsEnabled = e) } }
    fun toggleIgnoreDuplicates(e: Boolean) { prefs.edit { putBoolean("ignoreDuplicateNotifications", e) }; _state.update { it.copy(ignoreDuplicateNotifications = e) } }
    fun toggleLegacyCallNotifications(e: Boolean) { prefs.edit { putBoolean("useLegacyCallNotifications", e) }; _state.update { it.copy(useLegacyCallNotifications = e) } }
    fun toggleHrReminder(enabled: Boolean) { prefs.edit { putBoolean("hrReminderEnabled", enabled) }; _state.update { it.copy(hrReminderEnabled = enabled) }; restartHrReminder() }
    fun updateHrReminderInterval(minutes: Int) { val safe = minutes.coerceIn(15, 720); prefs.edit { putInt("hrReminderIntervalMinutes", safe) }; _state.update { it.copy(hrReminderIntervalMinutes = safe) }; restartHrReminder() }
    fun toggleDoubleConfirmations(enabled: Boolean) { prefs.edit { putBoolean("doubleConfirmationsEnabled", enabled) }; _state.update { it.copy(doubleConfirmationsEnabled = enabled) } }
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
    fun updateEatSourceFilters(showHome: Boolean, showStore: Boolean, showFastFood: Boolean) {
        prefs.edit {
            putBoolean("eatShowHome", showHome)
            putBoolean("eatShowStore", showStore)
            putBoolean("eatShowFastFood", showFastFood)
        }
        _state.update { it.copy(eatShowHome = showHome, eatShowStore = showStore, eatShowFastFood = showFastFood) }
    }
    fun applyMealCalories(calories: Int) {
        val total = (_state.value.appliedMealCalories + calories.coerceAtLeast(0)).coerceIn(0, 100_000)
        prefs.edit {
            putInt("appliedMealCalories", total)
            putString("appliedMealCaloriesDay", mealDayKey())
        }
        _state.update { it.copy(appliedMealCalories = total) }
    }
    fun updateEatCaloriesIncremental(enabled: Boolean) {
        prefs.edit {
            putBoolean("eatCaloriesIncremental", enabled)
            putString("appliedMealCaloriesDay", mealDayKey())
        }
        if (!enabled) {
            calorieCarryOffset = 0
            prefs.edit { putInt("calorieCarryOffset", 0) }
        }
        _state.update { it.copy(eatCaloriesIncremental = enabled, calories = if (enabled) it.calories else lastRawCalories) }
    }
    fun resetAppliedMealCalories() {
        prefs.edit {
            putInt("appliedMealCalories", 0)
            putString("appliedMealCaloriesDay", mealDayKey())
        }
        _state.update { it.copy(appliedMealCalories = 0) }
    }
    fun resetCalorieBaseline() {
        val baseline = _state.value.calories ?: _state.value.caloriesHistory.maxOfOrNull { it.value } ?: 0
        prefs.edit { putInt("calorieBaseline", baseline) }
        _state.update { it.copy(calorieBaseline = baseline) }
    }
    private fun adjustedCalories(rawCalories: Int): Int {
        if (!_state.value.eatCaloriesIncremental) {
            lastRawCalories = rawCalories
            calorieCarryOffset = 0
            prefs.edit { putInt("lastRawCalories", rawCalories); putInt("calorieCarryOffset", 0) }
            return rawCalories
        }
        if (lastRawCalories > 0 && rawCalories + 25 < lastRawCalories) {
            calorieCarryOffset = (calorieCarryOffset + lastRawCalories).coerceIn(0, 1_000_000)
            prefs.edit { putInt("calorieCarryOffset", calorieCarryOffset) }
            updateDebugLog("Calorie counter rollover preserved: +$lastRawCalories kcal")
        }
        lastRawCalories = rawCalories
        prefs.edit { putInt("lastRawCalories", rawCalories) }
        return (calorieCarryOffset + rawCalories).coerceIn(0, 1_000_000)
    }
    fun setShoppingListChecked(id: Long, checked: Boolean) {
        val checkedIds = if (checked) _state.value.shoppingListCheckedIds + id else _state.value.shoppingListCheckedIds - id
        prefs.edit { putStringSet("shoppingListCheckedIds", checkedIds.map { it.toString() }.toSet()) }
        _state.update { it.copy(shoppingListCheckedIds = checkedIds) }
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

    private fun SupplementEntity.sanitized(): SupplementEntity {
        val safeSingleDose = singleDoseMg.coerceIn(1, 50_000)
        val safeMaxDaily = maxDailyMg.coerceIn(safeSingleDose, 100_000)
        return copy(
            name = name.trim().ifBlank { "Supplement" }.take(32),
            dailyTargetMg = dailyTargetMg.coerceIn(1, safeMaxDaily),
            singleDoseMg = safeSingleDose,
            selectedAmountMg = selectedAmountMg.coerceIn(1, safeMaxDaily),
            stepMg = stepMg.coerceIn(1, safeMaxDaily),
            maxDailyMg = safeMaxDaily,
            sortOrder = sortOrder.coerceIn(0, 10_000),
            rampStartMg = rampStartMg.coerceIn(1, safeMaxDaily),
            rampTargetMg = rampTargetMg.coerceIn(1, 100_000),
            rampDays = rampDays.coerceIn(0, 3650),
            rampStartedAt = rampStartedAt?.coerceAtLeast(0L),
            updatedAt = System.currentTimeMillis()
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
        override fun onScanResult(ct: Int, res: ScanResult) {
            if (_state.value.isConnected) {
                stopScan()
                return
            }
            if (res.device.name?.contains(TARGET_NAME, true) == true) {
                stopScan()
                connectToDevice(res.device)
            }
        }
        override fun onScanFailed(err: Int) {
            val detail = "Bluetooth scan failed (code $err)"
            updateDebugLog(detail)
            _state.update { it.copy(connectionStatus = "Disconnected", connectionDetail = detail, reconnectAttempt = reconnectAttempt) }
            scheduleReconnect(1500)
        }
    }
    fun startScan() {
        userRequestedDisconnect = false
        reconnectJob?.cancel()
        connectWatchdogJob?.cancel()
        if (_state.value.isConnected) {
            stopScan()
            _state.update { it.copy(connectionStatus = "Connected") }
            updateDebugLog("Scan skipped; watch is already connected.")
            return
        }
        if (adapter?.isEnabled != true) {
            _state.update { it.copy(isConnected = false, connectionStatus = "Bluetooth off") }
            updateDebugLog("Bluetooth is off; waiting to reconnect.")
            scheduleReconnect(nextReconnectDelay())
            return
        }
        stopScan()
        _state.update { it.copy(connectionStatus = "Scanning...", connectionDetail = "Looking for ${TARGET_NAME}", reconnectAttempt = reconnectAttempt) }
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
        autoSleepFetchJob?.cancel()
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
        _state.update { it.copy(connectionStatus = "Connecting...", connectionDetail = "Connecting to ${device.name ?: device.address}", reconnectAttempt = reconnectAttempt, deviceName = device.name) }
        bluetoothGatt?.close()
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M_MASK)
        startConnectWatchdog()
    }
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, s: Int, ns: Int) {
            if (ns == BluetoothProfile.STATE_CONNECTED) { reconnectAttempt = 0; connectWatchdogJob?.cancel(); _state.update { it.copy(isConnected = true, connectionStatus = "Connected", connectionDetail = "Watch link established", reconnectAttempt = 0, lastWatchSeenTime = System.currentTimeMillis()) }; gatt.discoverServices() }
            else if (ns == BluetoothProfile.STATE_DISCONNECTED) { bluetoothGatt = null; autoSleepFetchJob?.cancel(); hrReminderJob?.cancel(); autoHeartRateReactivationJob?.cancel(); val detail = "Watch connection lost"; _state.update { it.copy(isConnected = false, connectionStatus = "Disconnected", connectionDetail = detail, reconnectAttempt = reconnectAttempt) }; updateDebugLog(detail); synchronized(operationQueue) { operationQueue.clear(); isOperating = false }; gatt.close(); scheduleReconnect() }
        }
        override fun onServicesDiscovered(gatt: BluetoothGatt, s: Int) { if (s == BluetoothGatt.GATT_SUCCESS) setupChannels(gatt) }
        private fun setupChannels(gatt: BluetoothGatt) {
            gatt.services.forEach { s -> s.characteristics.forEach { c ->
                if (c.uuid != SKIP_NOTIFY_CHAR && (c.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0) {
                    gatt.setCharacteristicNotification(c, true)
                    c.getDescriptor(CLIENT_CONFIG_DESCRIPTOR)?.let { enqueueOperation(GattOperation.WriteDescriptor(it, if ((c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) }
                }
                if (c.uuid == BATTERY_CHAR) enqueueOperation(GattOperation.ReadCharacteristic(c))
            } }; restartAutoStepFetch(); restartAutoSyncTime(); restartAutoSleepFetch(); restartHrReminder(); restartAutoHeartRateReactivation()
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

    fun markBluetoothOff() {
        stopScan()
        reconnectJob?.cancel()
        _state.update { it.copy(isConnected = false, connectionStatus = "Bluetooth off") }
        if (!userRequestedDisconnect && _state.value.autoConnect) {
            scheduleReconnect(10_000)
        }
    }

    fun onBluetoothTurnedOn() {
        if (userRequestedDisconnect || !_state.value.autoConnect || _state.value.isConnected) return
        updateDebugLog("Bluetooth turned on; reconnecting.")
        reconnectAttempt = 0
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
                    scheduleReconnect(nextReconnectDelay())
                }
            }
        }
    }
    private fun connectKnownDeviceOrScan() {
        if (_state.value.isConnected) return
        val address = prefs.getString(LAST_DEVICE_ADDRESS_KEY, null)
        val knownDevice = address?.let { runCatching { adapter?.getRemoteDevice(it) }.getOrNull() }
        if (knownDevice != null) {
            updateDebugLog("Reconnecting to remembered watch $address")
            connectToDevice(knownDevice)
        } else {
            startScan()
        }
    }
    private fun startScanWatchdog() { scanWatchdogJob?.cancel(); scanWatchdogJob = managerScope.launch { delay(if (appForegrounded) 12_000L else 60_000L); if (!_state.value.isConnected && _state.value.connectionStatus == "Scanning...") scheduleReconnect(nextReconnectDelay()) } }
    private fun startConnectWatchdog() {
        connectWatchdogJob?.cancel()
        connectWatchdogJob = managerScope.launch {
            delay(15_000)
            if (!_state.value.isConnected && _state.value.connectionStatus == "Connecting...") {
                val detail = "Connection timed out; retrying"
                _state.update { it.copy(connectionStatus = "Disconnected", connectionDetail = detail, reconnectAttempt = reconnectAttempt) }
                updateDebugLog("Connect timed out; scanning again.")
                bluetoothGatt?.close()
                bluetoothGatt = null
                scheduleReconnect(nextReconnectDelay())
            }
        }
    }

    private fun nextReconnectDelay(): Long {
        reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(12)
        _state.update { it.copy(reconnectAttempt = reconnectAttempt) }
        return when {
            appForegrounded && reconnectAttempt <= 5 -> 3_000L
            reconnectAttempt <= 5 -> 30_000L
            reconnectAttempt <= 10 -> 2 * 60_000L
            else -> 10 * 60_000L
        }
    }
    private fun saveToDb(battery: Int? = null, heartRate: Int? = null, spo2: Int? = null, systolic: Int? = null, diastolic: Int? = null, steps: Int? = null, activityCount: Int? = null, distance: Int? = null, calories: Int? = null, sleepMinutes: Int? = null, deepSleepMinutes: Int? = null, lightSleepMinutes: Int? = null, sleepSegments: List<SleepSegment>? = null) {
        managerScope.launch {
            val entry = HealthEntry(battery = battery, heartRate = heartRate, spo2 = spo2, systolic = systolic, diastolic = diastolic, steps = steps, activityCount = activityCount, distance = distance, calories = calories, sleepMinutes = sleepMinutes, deepSleepMinutes = deepSleepMinutes, lightSleepMinutes = lightSleepMinutes, sleepSegmentsJson = sleepSegments?.let(::encodeSleepSegments))
            if (shouldSkipHealthEntry(entry)) return@launch
            val key = healthSyncKey(entry)
            healthDao.enqueueHealth(HealthSyncQueueEntry(timestamp = entry.timestamp, battery = entry.battery, heartRate = entry.heartRate, spo2 = entry.spo2, systolic = entry.systolic, diastolic = entry.diastolic, steps = entry.steps, activityCount = entry.activityCount, distance = entry.distance, calories = entry.calories, sleepMinutes = entry.sleepMinutes, deepSleepMinutes = entry.deepSleepMinutes, lightSleepMinutes = entry.lightSleepMinutes, sleepSegmentsJson = entry.sleepSegmentsJson, dedupeKey = key))
            scheduleActiveEventSummary()
        }
    }

    private fun healthSyncKey(entry: HealthEntry): String = listOf(
        entry.timestamp / 10_000L, entry.battery, entry.heartRate, entry.spo2,
        entry.systolic, entry.diastolic, entry.steps, entry.activityCount,
        entry.distance, entry.calories, entry.sleepMinutes, entry.deepSleepMinutes,
        entry.lightSleepMinutes, entry.sleepSegmentsJson
    ).joinToString(":")

    private fun encodeSleepSegments(segments: List<SleepSegment>): String = segments.joinToString(";") {
        listOf(it.startMinutes, it.endMinutes, it.stateId, it.label.replace(";", "")).joinToString(",")
    }

    private fun decodeSleepSegments(encoded: String): List<SleepSegment> = encoded.split(';').mapNotNull { row ->
        val parts = row.split(',', limit = 4)
        if (parts.size < 4) null else SleepSegment(
            startMinutes = parts[0].toIntOrNull() ?: return@mapNotNull null,
            endMinutes = parts[1].toIntOrNull() ?: return@mapNotNull null,
            stateId = parts[2].toIntOrNull() ?: return@mapNotNull null,
            label = parts[3]
        )
    }

    fun retryPendingHealthSync() {
        managerScope.launch {
            healthDao.retryPendingHealthSync(System.currentTimeMillis())
            _state.update { it.copy(lastHealthSyncError = null) }
            updateDebugLog("Health sync retry requested.")
        }
    }

    fun updateHealthRetentionDays(days: Int) {
        val safe = days.coerceIn(7, 3650)
        prefs.edit { putInt("healthRetentionDays", safe) }
        _state.update { it.copy(healthRetentionDays = safe) }
    }

    private fun shouldSkipHealthEntry(entry: HealthEntry): Boolean {
        val last = lastPersistedHealthEntry ?: return false
        if (entry.heartRate != null || entry.sleepMinutes != null || entry.spo2 != null || entry.systolic != null || entry.diastolic != null) return false
        return entry.battery == last.battery &&
            entry.steps == last.steps &&
            entry.activityCount == last.activityCount &&
            entry.distance == last.distance &&
            entry.calories == last.calories
    }

    private fun scheduleActiveEventSummary(force: Boolean = false) {
        val now = System.currentTimeMillis()
        val interval = if (appForegrounded) 30_000L else 5 * 60_000L
        if (!force && now - lastEventSummaryTime < interval) return
        if (activeEventSummaryJob?.isActive == true) return
        activeEventSummaryJob = managerScope.launch {
            if (!force) delay(2_000L)
            eventDao.getActiveEventOnce()?.let { refreshEventSummary(it, System.currentTimeMillis()) }
            lastEventSummaryTime = System.currentTimeMillis()
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

    private fun isLikelySleepWakeWindow(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour in 5..12
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
    private fun mealDayKey(): String {
        val calendar = Calendar.getInstance()
        return "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
    }
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
