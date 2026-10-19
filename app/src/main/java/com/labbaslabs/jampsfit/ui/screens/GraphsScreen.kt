package com.labbaslabs.jampsfit.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labbaslabs.jampsfit.WatchState
import com.labbaslabs.jampsfit.database.HealthEntry
import com.labbaslabs.jampsfit.database.HistoryPoint
import com.labbaslabs.jampsfit.food.calculateBasalCalories
import com.labbaslabs.jampsfit.food.dayProgress
import com.labbaslabs.jampsfit.ui.components.SleekCard
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@Composable
fun GraphsScreen(state: WatchState, scrollState: ScrollState = rememberScrollState()) {
    var selectedSubTab by remember { mutableIntStateOf(0) }
    var dailyCount by remember { mutableIntStateOf(14) }
    var weeklyCount by remember { mutableIntStateOf(8) }
    var monthlyCount by remember { mutableIntStateOf(12) }
    var totalTimeframeIndex by remember { mutableIntStateOf(TOTAL_TIMEFRAMES.lastIndex) }
    val subTabs = listOf("Last 24h", "Today", "Daily", "Weekly", "Monthly", "Total")

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        SecondaryScrollableTabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = Color.Transparent,
            divider = {},
            edgePadding = 0.dp
        ) {
            subTabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedSubTab == index,
                    onClick = { selectedSubTab = index },
                    text = { Text(text = title, style = MaterialTheme.typography.labelLarge) }
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (selectedSubTab) {
                0 -> HistoryGraphs(title = "Last 24 Hours", stats = state.last24hStats, timeFormat = "HH:00", state = state)
                1 -> TodayGraphs(state)
                2 -> HistoryRangeGraphs(
                    title = "Daily History",
                    unit = "days",
                    stats = state.dailyStats,
                    selectedCount = dailyCount,
                    onSelectedCountChange = { dailyCount = it },
                    minimumRange = 365,
                    timeFormat = "dd MMM",
                    state = state
                )
                3 -> HistoryRangeGraphs(
                    title = "Weekly History",
                    unit = "weeks",
                    stats = state.weeklyStats,
                    selectedCount = weeklyCount,
                    onSelectedCountChange = { weeklyCount = it },
                    minimumRange = 52,
                    timeFormat = "'W'w",
                    state = state
                )
                4 -> HistoryRangeGraphs(
                    title = "Monthly History",
                    unit = "months",
                    stats = state.monthlyStats,
                    selectedCount = monthlyCount,
                    onSelectedCountChange = { monthlyCount = it },
                    minimumRange = 60,
                    timeFormat = "MMM yyyy",
                    state = state
                )
                5 -> TotalGraphs(state, totalTimeframeIndex) { totalTimeframeIndex = it }
            }
        }
    }
}

@Composable
fun TodayGraphs(state: WatchState) {
    val timeFormat = "HH:mm"

    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val todayEnd = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    Text(text = "Live Trends", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

    SleekGraphCard(
        title = "Heart Rate (HR)",
        dataPoints = state.heartRateHistory,
        currentValue = state.heartRate?.let { "$it bpm" },
        color = Color(0xFFE91E63),
        timeFormat = timeFormat,
        startTime = todayStart,
        endTime = todayEnd
    )
    HistoryBloodPressureCard(state.bpHistory, timeFormat, todayStart, todayEnd)
    SleekGraphCard(
        title = "Battery (%)", 
        dataPoints = state.batteryHistory.filter { it.value > 0 }, 
        currentValue = state.battery?.let { "$it%" }, 
        color = Color(0xFF4CAF50),
        timeFormat = timeFormat,
        forceZeroMin = true,
        fixedMax = 100f,
        startTime = todayStart,
        endTime = todayEnd
    )
    SleekGraphCard(
        title = "Steps", 
        dataPoints = state.stepsHistory, 
        currentValue = state.steps?.toString(), 
        color = Color(0xFF03A9F4),
        timeFormat = timeFormat,
        forceZeroMin = true,
        startTime = todayStart,
        endTime = todayEnd
    )
    SleekGraphCard(
        title = "Activity Count", 
        dataPoints = state.activityHistory.filter { it.value > 0 }, 
        currentValue = state.activityCount?.toString(), 
        color = Color(0xFF8BC34A),
        timeFormat = timeFormat,
        forceZeroMin = true,
        startTime = todayStart,
        endTime = todayEnd
    )
    SleekGraphCard(
        title = "Distance (m)", 
        dataPoints = state.distanceHistory.filter { it.value > 0 }, 
        currentValue = state.distance?.let { "${it}m" }, 
        color = Color(0xFF2196F3),
        timeFormat = timeFormat,
        forceZeroMin = true,
        startTime = todayStart,
        endTime = todayEnd
    )
    val totalCalories = state.caloriesHistory.toTotalCalories(
        dailyBasalCalories = calculateBasalCalories(state),
        periodMode = CaloriePeriodMode.DayProgress
    )

    SleekGraphCard(
        title = "Calories (Moving)", 
        dataPoints = state.caloriesHistory.filter { it.value > 0 }, 
        currentValue = state.calories?.let { "$it kcal" }, 
        color = Color(0xFFFF9800),
        timeFormat = timeFormat,
        forceZeroMin = true,
        startTime = todayStart,
        endTime = todayEnd
    )
    SleekGraphCard(
        title = "Calories (Total)",
        dataPoints = totalCalories,
        currentValue = totalCalories.lastOrNull()?.value?.let { "$it kcal" },
        color = Color(0xFFFF5722),
        timeFormat = timeFormat,
        forceZeroMin = true,
        startTime = todayStart,
        endTime = todayEnd
    )
    SleekGraphCard(
        title = "SpO2 (%)", 
        dataPoints = state.spo2History, 
        currentValue = state.spo2?.let { "$it%" }, 
        color = Color(0xFF00BCD4),
        timeFormat = timeFormat,
        startTime = todayStart,
        endTime = todayEnd
    )
    
    SleepTimelineCard(state)
}

@Composable
private fun HistoryRangeGraphs(
    title: String,
    unit: String,
    stats: List<HealthEntry>,
    selectedCount: Int,
    onSelectedCountChange: (Int) -> Unit,
    minimumRange: Int,
    timeFormat: String,
    state: WatchState
) {
    val availableCount = stats.size
    val maximumCount = maxOf(availableCount, minimumRange)
    val displayedCount = selectedCount.coerceIn(1, maximumCount)
    val displayedStats = fillHistoryPeriods(stats, displayedCount, unit)

    Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    SleekCard {
        Text(
            text = "Show $displayedCount $unit",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (availableCount == 0) "No recorded $unit yet." else "$availableCount recorded $unit available; missing periods are shown as zero.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        if (maximumCount > 1) {
            Slider(
                value = displayedCount.toFloat(),
                onValueChange = { onSelectedCountChange(it.roundToInt()) },
                valueRange = 1f..maximumCount.toFloat(),
                steps = maximumCount - 2
            )
        }
    }
    HistoryBarGraphs(title = title, stats = displayedStats, timeFormat = timeFormat, state = state, showTitle = false)
}

private fun fillHistoryPeriods(stats: List<HealthEntry>, count: Int, unit: String): List<HealthEntry> {
    val periodStarts = stats.associateBy { periodStart(it.timestamp, unit) }
    val cursor = Calendar.getInstance().apply {
        timeInMillis = periodStart(stats.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis(), unit)
    }
    return buildList(count) {
        repeat(count) {
            val timestamp = cursor.timeInMillis
            add(periodStarts[timestamp] ?: HealthEntry(timestamp = timestamp))
            when (unit) {
                "weeks" -> cursor.add(Calendar.WEEK_OF_YEAR, -1)
                "months" -> cursor.add(Calendar.MONTH, -1)
                else -> cursor.add(Calendar.DATE, -1)
            }
        }
    }.asReversed()
}

private fun periodStart(timestamp: Long, unit: String): Long = Calendar.getInstance().apply {
    timeInMillis = timestamp
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    when (unit) {
        "weeks" -> {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        "months" -> set(Calendar.DAY_OF_MONTH, 1)
    }
}.timeInMillis

@Composable
fun HistoryBarGraphs(
    title: String,
    stats: List<com.labbaslabs.jampsfit.database.HealthEntry>,
    timeFormat: String,
    state: WatchState,
    showTitle: Boolean = true
) {
    if (showTitle) Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

    if (stats.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            Text(text = "No history data available yet.", color = Color.Gray)
        }
    } else {
        SleekBarChartCard(
            title = "Heart Rate (HR)",
            dataPoints = stats.map { com.labbaslabs.jampsfit.database.HistoryPoint(it.heartRate ?: 0, it.timestamp) },
            currentValue = stats.lastOrNull { (it.heartRate ?: 0) > 0 }?.heartRate?.toString(),
            color = Color(0xFFE91E63),
            timeFormat = timeFormat
        )
        HistoryBloodPressureCard(stats, timeFormat)

        SleekBarChartCard(
            title = "Steps (Total)",
            dataPoints = stats.map { com.labbaslabs.jampsfit.database.HistoryPoint(it.steps ?: 0, it.timestamp) },
            currentValue = stats.lastOrNull()?.steps?.toString(),
            color = Color(0xFF03A9F4),
            timeFormat = timeFormat,
            forceZeroMin = true
        )

        SleekBarChartCard(
            title = "Distance (Total)",
            dataPoints = stats.map { com.labbaslabs.jampsfit.database.HistoryPoint(it.distance ?: 0, it.timestamp) },
            currentValue = stats.lastOrNull { (it.distance ?: 0) > 0 }?.distance?.toString()?.plus("m"),
            color = Color(0xFF2196F3),
            timeFormat = timeFormat,
            forceZeroMin = true
        )

        SleekBarChartCard(
            title = "Calories (Moving)",
            dataPoints = stats.map { com.labbaslabs.jampsfit.database.HistoryPoint(it.calories ?: 0, it.timestamp) },
            currentValue = stats.lastOrNull { (it.calories ?: 0) > 0 }?.calories?.toString()?.plus(" kcal"),
            color = Color(0xFFFF9800),
            timeFormat = timeFormat,
            forceZeroMin = true
        )

        val periodMode = when {
            title.contains("Weekly", ignoreCase = true) -> CaloriePeriodMode.Week
            title.contains("Monthly", ignoreCase = true) -> CaloriePeriodMode.Month
            else -> CaloriePeriodMode.FullDay
        }
        val totalCalories = stats
            .map { com.labbaslabs.jampsfit.database.HistoryPoint(it.calories ?: 0, it.timestamp) }
            .toTotalCalories(calculateBasalCalories(state), periodMode)
        SleekBarChartCard(
            title = "Calories (Total)",
            dataPoints = totalCalories,
            currentValue = totalCalories.lastOrNull()?.value?.toString()?.plus(" kcal"),
            color = Color(0xFFFF5722),
            timeFormat = timeFormat,
            forceZeroMin = true
        )

        SleekBarChartCard(
            title = "SpO2 (Avg)",
            dataPoints = stats.map { com.labbaslabs.jampsfit.database.HistoryPoint(it.spo2 ?: 0, it.timestamp) },
            currentValue = stats.lastOrNull { (it.spo2 ?: 0) > 0 }?.spo2?.toString()?.plus("%"),
            color = Color(0xFF00BCD4),
            timeFormat = timeFormat
        )

        SleekBarChartCard(
            title = "Activity Count",
            dataPoints = stats.map { com.labbaslabs.jampsfit.database.HistoryPoint(it.activityCount ?: 0, it.timestamp) },
            currentValue = stats.lastOrNull { (it.activityCount ?: 0) > 0 }?.activityCount?.toString(),
            color = Color(0xFF8BC34A),
            timeFormat = timeFormat,
            forceZeroMin = true
        )

        SleekBarChartCard(
            title = "Battery (Avg)",
            dataPoints = stats.map { com.labbaslabs.jampsfit.database.HistoryPoint(it.battery ?: 0, it.timestamp) },
            currentValue = stats.lastOrNull { (it.battery ?: 0) > 0 }?.battery?.toString()?.plus("%"),
            color = Color(0xFF4CAF50),
            timeFormat = timeFormat,
            forceZeroMin = true,
            fixedMax = 100f
        )
        
        if (stats.any { (it.sleepMinutes ?: 0) > 0 }) {
            SleekCard {
                Text(text = "Sleep History (Minutes)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendItem(label = "Deep", color = Color(0xFF311B92))
                    LegendItem(label = "Light", color = Color(0xFF7E57C2))
                }
                Spacer(modifier = Modifier.height(16.dp))

                SleekBarChartCard(
                    title = "Total Sleep",
                    dataPoints = stats.map { com.labbaslabs.jampsfit.database.HistoryPoint(it.sleepMinutes ?: 0, it.timestamp) },
                    currentValue = stats.lastOrNull { (it.sleepMinutes ?: 0) > 0 }?.sleepMinutes?.let { "${it / 60}h ${it % 60}m" },
                    color = Color(0xFF9C27B0),
                    timeFormat = timeFormat,
                    forceZeroMin = true
                )
            }
        }
    }
}

@Composable
fun HistoryGraphs(title: String, stats: List<com.labbaslabs.jampsfit.database.HealthEntry>, timeFormat: String, state: WatchState) {
    Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    val bloodPressureWindowStart = System.currentTimeMillis() - 24 * 60 * 60_000L
    val bloodPressureStats = state.bpHistory.filter { it.timestamp >= bloodPressureWindowStart }
    val sharedStart = if (title.contains("24 Hours", ignoreCase = true)) bloodPressureWindowStart else stats.firstOrNull()?.timestamp
    val sharedEnd = if (title.contains("24 Hours", ignoreCase = true)) System.currentTimeMillis() else stats.lastOrNull()?.timestamp
    SleekGraphCard(
        title = "Heart Rate (HR)",
        dataPoints = stats.map { entry ->
            com.labbaslabs.jampsfit.database.HistoryPoint(entry.heartRate ?: 0, entry.timestamp)
        },
        currentValue = stats.lastOrNull { (it.heartRate ?: 0) > 0 }?.heartRate?.toString(),
        color = Color(0xFFE91E63),
        timeFormat = timeFormat,
        startTime = sharedStart,
        endTime = sharedEnd
    )
    val chartStart = if (title.contains("24 Hours", ignoreCase = true)) bloodPressureWindowStart else stats.firstOrNull()?.timestamp
    val chartEnd = if (title.contains("24 Hours", ignoreCase = true)) System.currentTimeMillis() else stats.lastOrNull()?.timestamp
    HistoryBloodPressureCard(bloodPressureStats, "HH:mm", chartStart, chartEnd)

    if (stats.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            Text(text = "No history data available yet.", color = Color.Gray)
        }
    } else {
        // Calculate hourly calorie burn if we are looking at the 24h view
        if (title.contains("24 Hours", ignoreCase = true)) {
            val hourlyCalories = mutableListOf<com.labbaslabs.jampsfit.database.HistoryPoint>()
            for (i in stats.indices) {
                val current = stats[i].calories ?: 0
                val previous = if (i > 0) stats[i - 1].calories ?: 0 else 0
                
                // If current < previous, it likely means a day rollover (calories reset to 0 at midnight)
                val burn = if (current >= previous) current - previous else current
                hourlyCalories.add(com.labbaslabs.jampsfit.database.HistoryPoint(burn, stats[i].timestamp))
            }
            
            SleekGraphCard(
                title = "Calories Burned (per hour)",
                dataPoints = hourlyCalories,
                currentValue = hourlyCalories.lastOrNull()?.value?.toString()?.plus(" kcal"),
                color = Color(0xFFFF5722),
                timeFormat = timeFormat,
                startTime = sharedStart,
                endTime = sharedEnd,
                forceZeroMin = true
            )
        }

        SleekGraphCard(
            title = "Battery (%)",
            dataPoints = stats.map { com.labbaslabs.jampsfit.database.HistoryPoint(it.battery ?: 0, it.timestamp) }.filter { it.value > 0 },
            currentValue = stats.lastOrNull { (it.battery ?: 0) > 0 }?.battery?.toString()?.plus("%"),
            color = Color(0xFF4CAF50),
            timeFormat = timeFormat,
            startTime = sharedStart,
            endTime = sharedEnd,
            forceZeroMin = true,
            fixedMax = 100f
        )

        SleekGraphCard(
            title = if (title.contains("24 Hours", ignoreCase = true)) "Steps (Hourly Max)" else "Steps (Max)",
            dataPoints = stats.map { com.labbaslabs.jampsfit.database.HistoryPoint(it.steps ?: 0, it.timestamp) }.filter { it.value > 0 },
            currentValue = stats.lastOrNull { (it.steps ?: 0) > 0 }?.steps?.toString(),
            color = Color(0xFF03A9F4),
            timeFormat = timeFormat,
            startTime = sharedStart,
            endTime = sharedEnd,
            forceZeroMin = true
        )

        SleekGraphCard(
            title = "Activity Count",
            dataPoints = stats.map { com.labbaslabs.jampsfit.database.HistoryPoint(it.activityCount ?: 0, it.timestamp) }.filter { it.value > 0 },
            currentValue = stats.lastOrNull { (it.activityCount ?: 0) > 0 }?.activityCount?.toString(),
            color = Color(0xFF8BC34A),
            timeFormat = timeFormat,
            startTime = sharedStart,
            endTime = sharedEnd,
            forceZeroMin = true
        )

        SleekGraphCard(
            title = "Distance (m)",
            dataPoints = stats.map { com.labbaslabs.jampsfit.database.HistoryPoint(it.distance ?: 0, it.timestamp) }.filter { it.value > 0 },
            currentValue = stats.lastOrNull { (it.distance ?: 0) > 0 }?.distance?.toString()?.plus("m"),
            color = Color(0xFF2196F3),
            timeFormat = timeFormat,
            startTime = sharedStart,
            endTime = sharedEnd,
            forceZeroMin = true
        )

        SleekGraphCard(
            title = "Calories (Moving)",
            dataPoints = stats.map { com.labbaslabs.jampsfit.database.HistoryPoint(it.calories ?: 0, it.timestamp) }.filter { it.value > 0 },
            currentValue = stats.lastOrNull { (it.calories ?: 0) > 0 }?.calories?.toString()?.plus(" kcal"),
            color = Color(0xFFFF9800),
            timeFormat = timeFormat,
            startTime = sharedStart,
            endTime = sharedEnd,
            forceZeroMin = true
        )

        val totalCalories = stats
            .map { com.labbaslabs.jampsfit.database.HistoryPoint(it.calories ?: 0, it.timestamp) }
            .toTotalCalories(calculateBasalCalories(state), CaloriePeriodMode.DayProgress)
        SleekGraphCard(
            title = "Calories (Total)",
            dataPoints = totalCalories,
            currentValue = totalCalories.lastOrNull()?.value?.toString()?.plus(" kcal"),
            color = Color(0xFFFF5722),
            timeFormat = timeFormat,
            startTime = sharedStart,
            endTime = sharedEnd,
            forceZeroMin = true
        )

        SleekGraphCard(
            title = "SpO2 (Avg)",
            dataPoints = stats.map { entry -> 
                com.labbaslabs.jampsfit.database.HistoryPoint(entry.spo2 ?: 0, entry.timestamp)
            },
            currentValue = stats.lastOrNull { (it.spo2 ?: 0) > 0 }?.spo2?.toString(),
            color = Color(0xFF00BCD4),
            timeFormat = timeFormat,
            startTime = sharedStart,
            endTime = sharedEnd
        )

        if (title.contains("24 Hours", ignoreCase = true)) {
            SleepTimelineCard(state)
        }
        HistorySleepCard(stats, timeFormat)
    }
}

private data class YearlyHealthTotal(
    val year: Int,
    val steps: Int,
    val distance: Int,
    val movingCalories: Int,
    val sleepMinutes: Int,
    val activeDays: Int
)

@Composable
private fun TotalGraphs(state: WatchState, requestedTimeframeIndex: Int, onRequestedTimeframeIndexChange: (Int) -> Unit) {
    val allDailyStats = state.dailyStats
    val now = System.currentTimeMillis()
    val selectedTimeframeIndex = requestedTimeframeIndex.coerceIn(TOTAL_TIMEFRAMES.indices)
    val selectedTimeframe = TOTAL_TIMEFRAMES[selectedTimeframeIndex]
    val dailyStats = selectedTimeframe.startTime(now)?.let { startTime ->
        allDailyStats.filter { entry -> entry.timestamp >= startTime }
    } ?: allDailyStats
    val stepGoal = state.stepGoalSetting ?: 10_000
    val totalSteps = dailyStats.sumOf { it.steps ?: 0 }
    val totalDistance = dailyStats.sumOf { it.distance ?: 0 }
    val totalMovingCalories = dailyStats.sumOf { it.calories ?: 0 }
    val totalSleep = dailyStats.sumOf { it.sleepMinutes ?: 0 }
    val activityDays = dailyStats.count { entry ->
        listOf(entry.steps, entry.distance, entry.calories, entry.sleepMinutes, entry.heartRate, entry.spo2).any { it != null && it > 0 }
    }
    val goalDays = dailyStats.count { (it.steps ?: 0) >= stepGoal }
    val bestDay = dailyStats.maxByOrNull { it.steps ?: 0 }
    val averageHeartRate = dailyStats.mapNotNull { it.heartRate?.takeIf { value -> value > 0 } }.average()
    val averageSleep = dailyStats.mapNotNull { it.sleepMinutes?.takeIf { value -> value > 0 } }.average()
    val averageSteps = if (activityDays == 0) 0 else totalSteps / activityDays
    val averageMovingCalories = if (activityDays == 0) 0 else totalMovingCalories / activityDays
    val estimatedDailyEnergyNeed = calculateBasalCalories(state) + averageMovingCalories
    val bloodPressureReadings = dailyStats.mapNotNull { entry ->
        val systolic = entry.systolic?.takeIf { it > 0 }
        val diastolic = entry.diastolic?.takeIf { it > 0 }
        if (systolic != null && diastolic != null) systolic to diastolic else null
    }
    val averageBloodPressure = bloodPressureReadings.takeIf { it.isNotEmpty() }?.let { readings ->
        "${readings.map { it.first }.average().roundToInt()} / ${readings.map { it.second }.average().roundToInt()}"
    } ?: "--"
    val yearlyTotals = remember(dailyStats) { yearlyTotals(dailyStats) }

    Text(
        text = if (selectedTimeframe.isTotal) "Your Total Journey" else "Your Last ${selectedTimeframe.label}",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
    SleekCard {
        Text(
            text = "Timeframe: ${selectedTimeframe.label}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Choose 1 day, 1 week, monthly ranges, or all recorded history.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        Slider(
            value = selectedTimeframeIndex.toFloat(),
            onValueChange = { onRequestedTimeframeIndexChange(it.roundToInt()) },
            valueRange = 0f..TOTAL_TIMEFRAMES.lastIndex.toFloat(),
            steps = (TOTAL_TIMEFRAMES.size - 2).coerceAtLeast(0)
        )
    }
    if (dailyStats.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            Text(text = "Your milestones will appear here as health data is recorded.", color = Color.Gray)
        }
        return
    }

    SleekCard {
        Text(text = "Every recorded day adds up.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            text = "$activityDays active days • ${formatDistance(totalDistance)} travelled • ${formatDuration(totalSleep)} asleep",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
    }

    TotalMetricRow(
        leftLabel = "Total steps",
        leftValue = formatNumber(totalSteps),
        rightLabel = "Goal days",
        rightValue = "$goalDays / $activityDays"
    )
    TotalMetricRow(
        leftLabel = "Distance",
        leftValue = formatDistance(totalDistance),
        rightLabel = "Moving calories",
        rightValue = "${formatNumber(totalMovingCalories)} kcal"
    )
    TotalMetricRow(
        leftLabel = "Average steps/day",
        leftValue = formatNumber(averageSteps),
        rightLabel = "Average moving kcal/day",
        rightValue = "${formatNumber(averageMovingCalories)} kcal"
    )
    TotalMetricRow(
        leftLabel = "Average HR",
        leftValue = if (averageHeartRate.isNaN()) "--" else "${averageHeartRate.roundToInt()} bpm",
        rightLabel = "Average sleep",
        rightValue = if (averageSleep.isNaN()) "--" else formatDuration(averageSleep.roundToInt())
    )
    TotalMetricRow(
        leftLabel = "Average BP (sys/dia)",
        leftValue = averageBloodPressure,
        rightLabel = "Estimated energy need/day",
        rightValue = "${formatNumber(estimatedDailyEnergyNeed)} kcal"
    )
    bestDay?.takeIf { (it.steps ?: 0) > 0 }?.let { entry ->
        SleekCard {
            Text(text = "Personal best day", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(text = "${formatNumber(entry.steps ?: 0)} steps", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF03A9F4))
            Text(text = SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(entry.timestamp)), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
    StepDistributionCard(dailyStats, stepGoal)

    Text(text = "Cumulative progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    SleekBarChartCard(
        title = "Cumulative Steps",
        dataPoints = cumulativePoints(dailyStats) { it.steps ?: 0 },
        currentValue = formatNumber(totalSteps),
        color = Color(0xFF03A9F4),
        timeFormat = "MMM yy",
        forceZeroMin = true
    )
    SleekBarChartCard(
        title = "Cumulative Distance (km)",
        dataPoints = cumulativePoints(dailyStats) { (it.distance ?: 0) / 1_000 },
        currentValue = formatDistance(totalDistance),
        color = Color(0xFF2196F3),
        timeFormat = "MMM yy",
        forceZeroMin = true
    )
    SleekBarChartCard(
        title = "Cumulative Moving Calories",
        dataPoints = cumulativePoints(dailyStats) { it.calories ?: 0 },
        currentValue = "${formatNumber(totalMovingCalories)} kcal",
        color = Color(0xFFFF9800),
        timeFormat = "MMM yy",
        forceZeroMin = true
    )

    if (yearlyTotals.isNotEmpty()) {
        Text(text = "Year by year", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        SleekBarChartCard(
            title = "Steps by Year",
            dataPoints = yearlyTotals.map { HistoryPoint(it.steps, yearTimestamp(it.year)) },
            currentValue = formatNumber(yearlyTotals.last().steps),
            color = Color(0xFF03A9F4),
            timeFormat = "yyyy",
            forceZeroMin = true
        )
        SleekBarChartCard(
            title = "Distance by Year (km)",
            dataPoints = yearlyTotals.map { HistoryPoint(it.distance / 1_000, yearTimestamp(it.year)) },
            currentValue = formatDistance(yearlyTotals.last().distance),
            color = Color(0xFF2196F3),
            timeFormat = "yyyy",
            forceZeroMin = true
        )
        SleekBarChartCard(
            title = "Sleep by Year (hours)",
            dataPoints = yearlyTotals.map { HistoryPoint(it.sleepMinutes / 60, yearTimestamp(it.year)) },
            currentValue = formatDuration(yearlyTotals.last().sleepMinutes),
            color = Color(0xFF9C27B0),
            timeFormat = "yyyy",
            forceZeroMin = true
        )
    }
}

@Composable
private fun StepDistributionCard(stats: List<HealthEntry>, stepGoal: Int) {
    val buckets = listOf(
        "Below 25%" to stats.count { (it.steps ?: 0) < stepGoal / 4 },
        "Building (25–74%)" to stats.count { (it.steps ?: 0) in stepGoal / 4 until (stepGoal * 3) / 4 },
        "Nearly there (75–99%)" to stats.count { (it.steps ?: 0) in (stepGoal * 3) / 4 until stepGoal },
        "Goal reached (100–149%)" to stats.count { (it.steps ?: 0) in stepGoal until (stepGoal * 3) / 2 },
        "Power day (150%+)" to stats.count { (it.steps ?: 0) >= (stepGoal * 3) / 2 }
    )
    val totalDays = stats.size.coerceAtLeast(1)

    SleekCard {
        Text(text = "Step-day distribution", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(text = "How your recorded days compare with your ${formatNumber(stepGoal)}-step goal", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        buckets.forEach { (label, days) ->
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = label, style = MaterialTheme.typography.bodySmall)
                Text(text = "$days day${if (days == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            LinearProgressIndicator(
                progress = { days.toFloat() / totalDays },
                modifier = Modifier.fillMaxWidth(),
                color = when (label) {
                    "Below 25%" -> Color(0xFFF44336)
                    "Building (25–74%)" -> Color(0xFFFF9800)
                    "Nearly there (75–99%)" -> Color(0xFFFFC107)
                    "Goal reached (100–149%)" -> Color(0xFF4CAF50)
                    else -> Color(0xFF03A9F4)
                }
            )
        }
    }
}

@Composable
private fun TotalMetricRow(leftLabel: String, leftValue: String, rightLabel: String, rightValue: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        TotalMetricCard(leftLabel, leftValue, Modifier.weight(1f))
        TotalMetricCard(rightLabel, rightValue, Modifier.weight(1f))
    }
}

@Composable
private fun TotalMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    SleekCard(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

private fun cumulativePoints(stats: List<HealthEntry>, value: (HealthEntry) -> Int): List<HistoryPoint> {
    var total = 0
    return stats.map { entry ->
        total += value(entry)
        HistoryPoint(total, entry.timestamp)
    }
}

private fun yearlyTotals(stats: List<HealthEntry>): List<YearlyHealthTotal> = stats
    .groupBy { entry -> Calendar.getInstance().apply { timeInMillis = entry.timestamp }.get(Calendar.YEAR) }
    .map { (year, entries) ->
        YearlyHealthTotal(
            year = year,
            steps = entries.sumOf { it.steps ?: 0 },
            distance = entries.sumOf { it.distance ?: 0 },
            movingCalories = entries.sumOf { it.calories ?: 0 },
            sleepMinutes = entries.sumOf { it.sleepMinutes ?: 0 },
            activeDays = entries.count { (it.steps ?: 0) > 0 }
        )
    }
    .sortedBy { it.year }

private fun yearTimestamp(year: Int): Long = Calendar.getInstance().apply {
    clear()
    set(year, Calendar.JANUARY, 1)
}.timeInMillis

private fun formatNumber(value: Int): String = String.format(Locale.getDefault(), "%,d", value)

private fun formatDistance(meters: Int): String = String.format(Locale.getDefault(), "%.1f km", meters / 1_000f)

private fun formatDuration(minutes: Int): String = "${minutes / 60}h ${minutes % 60}m"

private data class TotalTimeframe(val label: String, val days: Int? = null, val months: Int? = null) {
    val isTotal: Boolean get() = days == null && months == null

    fun startTime(now: Long): Long? = when {
        days != null -> now - days * DAY_MILLIS
        months != null -> Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.MONTH, -months)
        }.timeInMillis
        else -> null
    }
}

private val TOTAL_TIMEFRAMES = listOf(
    TotalTimeframe("1 day", days = 1),
    TotalTimeframe("1 week", days = 7),
    * (1..12).map { months -> TotalTimeframe("$months ${if (months == 1) "month" else "months"}", months = months) }.toTypedArray(),
    TotalTimeframe("Total time")
)

private const val DAY_MILLIS = 24L * 60L * 60L * 1_000L

@Composable
fun HistoryBloodPressureCard(
    stats: List<com.labbaslabs.jampsfit.database.HealthEntry>,
    timeFormat: String,
    startTime: Long? = null,
    endTime: Long? = null
) {
    val bpData = stats.filter { (it.systolic ?: 0) > 0 && (it.diastolic ?: 0) > 0 }
    val timeRangeStart = startTime ?: bpData.firstOrNull()?.timestamp ?: 0L
    val timeRangeEnd = endTime ?: bpData.lastOrNull()?.timestamp ?: timeRangeStart + 1L
    val timeRange = (timeRangeEnd - timeRangeStart).coerceAtLeast(1L).toFloat()

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = Color.Gray, fontSize = 10.sp)
    val sdf = remember { SimpleDateFormat(timeFormat, Locale.getDefault()) }

    var touchedEntry by remember { mutableStateOf<com.labbaslabs.jampsfit.database.HealthEntry?>(null) }
    var touchX by remember { mutableFloatStateOf(0f) }

    SleekCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(text = "Blood Pressure (BP)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                if (touchedEntry != null) {
                    Text(
                        text = sdf.format(Date(touchedEntry!!.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
            val displayEntry = touchedEntry ?: bpData.lastOrNull()
            Text(
                text = displayEntry?.let { "${it.systolic}/${it.diastolic}" } ?: "--/--",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF5722)
            )
        }
        if (bpData.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                Text(text = "Waiting for data...", color = Color.Gray)
            }
            return@SleekCard
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem(label = "Systolic", color = Color(0xFFFF5722))
            LegendItem(label = "Diastolic", color = Color(0xFF3F51B5))
        }
        Spacer(modifier = Modifier.height(16.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(bpData) {
                    detectDragGestures(
                        onDragStart = { offset -> touchX = offset.x },
                        onDrag = { change, _ -> touchX = change.position.x; change.consume() },
                        onDragEnd = { touchedEntry = null },
                        onDragCancel = { touchedEntry = null }
                    )
                }
                .pointerInput(bpData) {
                    detectTapGestures(onPress = { offset -> touchX = offset.x; tryAwaitRelease(); touchedEntry = null })
                }
        ) {
            val leftPadding = 45.dp.toPx()
            val rightPadding = 20.dp.toPx()
            val bottomPadding = 30.dp.toPx()
            val graphWidth = size.width - leftPadding - rightPadding
            val graphHeight = size.height - bottomPadding
            
            val maxVal = (bpData.maxOf { it.systolic ?: 0 }.toFloat()).coerceAtLeast(140f)
            val minVal = (bpData.minOf { it.diastolic ?: 0 }.toFloat()).coerceAtMost(60f)
            val range = (maxVal - minVal).coerceAtLeast(1f)

            // Draw Y axis labels and grid
            val ySteps = 4
            for (i in 0..ySteps) {
                val yVal = minVal + (range * i / ySteps)
                val yPos = graphHeight - (graphHeight * i / ySteps)
                
                drawText(
                    textMeasurer = textMeasurer,
                    text = yVal.toInt().toString(),
                    style = labelStyle,
                    topLeft = Offset(0f, (yPos - 10.dp.toPx()).coerceAtLeast(0f))
                )
                drawLine(
                    color = Color.Gray.copy(alpha = 0.1f),
                    start = Offset(leftPadding, yPos),
                    end = Offset(size.width - rightPadding, yPos),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Draw X axis labels
            if (bpData.isNotEmpty()) {
                fun xFor(timestamp: Long): Float = leftPadding + ((timestamp - timeRangeStart).toFloat() / timeRange) * graphWidth
                val labelTimes = (0..4).map { step ->
                    timeRangeStart + (timeRangeEnd - timeRangeStart) * step / 4
                }

                labelTimes.forEachIndexed { index, labelTime ->
                    val xPos = xFor(labelTime)
                    val timeStr = sdf.format(Date(labelTime))
                    
                    val labelWidth = textMeasurer.measure(timeStr, labelStyle).size.width.toFloat()
                    val xOffset = when {
                        index == 4 -> -labelWidth
                        index == 0 -> 0f
                        else -> -labelWidth / 2f
                    }
                    
                    drawText(
                        textMeasurer = textMeasurer,
                        text = timeStr,
                        style = labelStyle,
                        topLeft = Offset((xPos + xOffset).coerceIn(leftPadding, size.width - labelWidth), graphHeight + 5.dp.toPx())
                    )
                }
            }
            
            if (bpData.size == 1) {
                val entry = bpData[0]
                val sysY = graphHeight - ((entry.systolic?.toFloat() ?: 0f) - minVal) / range * graphHeight
                val diaY = graphHeight - ((entry.diastolic?.toFloat() ?: 0f) - minVal) / range * graphHeight
                val x = leftPadding + ((entry.timestamp - timeRangeStart).toFloat() / timeRange) * graphWidth
                drawCircle(color = Color(0xFFFF5722), radius = 5.dp.toPx(), center = Offset(x, sysY))
                drawCircle(color = Color(0xFF3F51B5), radius = 5.dp.toPx(), center = Offset(x, diaY))
            } else {
                val sysPoints = bpData.mapIndexed { i, entry ->
                    Offset(
                        x = leftPadding + ((entry.timestamp - timeRangeStart).toFloat() / timeRange) * graphWidth,
                        y = graphHeight - ((entry.systolic?.toFloat() ?: 0f) - minVal) / range * graphHeight
                    )
                }
                val diaPoints = bpData.mapIndexed { i, entry ->
                    Offset(
                        x = leftPadding + ((entry.timestamp - timeRangeStart).toFloat() / timeRange) * graphWidth,
                        y = graphHeight - ((entry.diastolic?.toFloat() ?: 0f) - minVal) / range * graphHeight
                    )
                }
                val sysPath = smoothQuadraticPath(sysPoints)
                val diaPath = smoothQuadraticPath(diaPoints)
                drawPath(sysPath, color = Color(0xFFFF5722), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(diaPath, color = Color(0xFF3F51B5), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            // Handle Touch
            if (touchX in leftPadding..(leftPadding + graphWidth) && bpData.isNotEmpty()) {
                val index = ((touchX - leftPadding) / graphWidth * (bpData.size - 1)).roundToInt().coerceIn(0, bpData.size - 1)
                val entry = bpData[index]
                touchedEntry = entry
                
                val x = leftPadding + ((entry.timestamp - timeRangeStart).toFloat() / timeRange) * graphWidth
                val sysY = graphHeight - ((entry.systolic?.toFloat() ?: 0f) - minVal) / range * graphHeight
                val diaY = graphHeight - ((entry.diastolic?.toFloat() ?: 0f) - minVal) / range * graphHeight
                
                drawLine(
                    color = Color.Gray.copy(alpha = 0.5f),
                    start = Offset(x, 0f),
                    end = Offset(x, graphHeight),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
                drawCircle(Color.White, radius = 5.dp.toPx(), center = Offset(x, sysY))
                drawCircle(Color(0xFFFF5722), radius = 3.dp.toPx(), center = Offset(x, sysY))
                drawCircle(Color.White, radius = 5.dp.toPx(), center = Offset(x, diaY))
                drawCircle(Color(0xFF3F51B5), radius = 3.dp.toPx(), center = Offset(x, diaY))
            }

            drawLine(Color.Gray.copy(alpha = 0.3f), Offset(leftPadding, 0f), Offset(leftPadding, graphHeight), 2.dp.toPx())
            drawLine(Color.Gray.copy(alpha = 0.3f), Offset(leftPadding, graphHeight), Offset(size.width - rightPadding, graphHeight), 2.dp.toPx())
        }
    }
}

@Composable
fun SleepTimelineCard(state: WatchState) {
    if (state.sleepSegments.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = Color.Gray, fontSize = 9.sp)

    var touchedSegment by remember { mutableStateOf<com.labbaslabs.jampsfit.SleepSegment?>(null) }
    var touchX by remember { mutableFloatStateOf(0f) }

    SleekCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Sleep Stages (Timeline)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            if (touchedSegment != null) {
                Text(
                    text = "${formatMinutesOfDay(touchedSegment!!.startMinutes)} - ${formatMinutesOfDay(touchedSegment!!.endMinutes)}: ${touchedSegment!!.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LegendItem(label = "Awake", color = Color(0xFFFFEB3B))
            LegendItem(label = "REM", color = Color(0xFFB39DDB))
            LegendItem(label = "Light", color = Color(0xFF7E57C2))
            LegendItem(label = "Deep", color = Color(0xFF311B92))
        }
        Spacer(modifier = Modifier.height(16.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .pointerInput(state.sleepSegments) {
                    detectDragGestures(
                        onDragStart = { offset -> touchX = offset.x },
                        onDrag = { change, _ -> touchX = change.position.x; change.consume() },
                        onDragEnd = { touchedSegment = null },
                        onDragCancel = { touchedSegment = null }
                    )
                }
                .pointerInput(state.sleepSegments) {
                    detectTapGestures(onPress = { offset -> touchX = offset.x; tryAwaitRelease(); touchedSegment = null })
                }
        ) {
            val leftPadding = 40.dp.toPx()
            val bottomPadding = 25.dp.toPx()
            val graphHeight = size.height - bottomPadding
            val graphWidth = size.width - leftPadding

            val start = state.sleepSegments.first().startMinutes.toFloat()
            val end = state.sleepSegments.last().endMinutes.toFloat()
            val totalRange = (end - start).coerceAtLeast(1f)

            // Draw Y-axis labels
            val yLabels = listOf("Awake", "REM", "Light", "Deep")
            yLabels.forEachIndexed { index, label ->
                val yPos = (index.toFloat() / (yLabels.size - 1)) * (graphHeight * 0.7f) + (graphHeight * 0.15f)
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    style = labelStyle,
                    topLeft = Offset(0f, yPos - 6.dp.toPx())
                )
                drawLine(
                    color = Color.Gray.copy(alpha = 0.1f),
                    start = Offset(leftPadding, yPos),
                    end = Offset(size.width, yPos),
                    strokeWidth = 1.dp.toPx()
                )
            }

            if (totalRange > 0) {
                val path = Path()
                var first = true

                state.sleepSegments.forEach { segment ->
                    val xStart = leftPadding + ((segment.startMinutes - start) / totalRange) * graphWidth
                    val xEnd = leftPadding + ((segment.endMinutes - start) / totalRange) * graphWidth
                    val width = xEnd - xStart

                    val yIndex = when (segment.stateId) {
                        0x00 -> 0 // Awake
                        0x03 -> 1 // REM
                        0x01 -> 2 // Light
                        0x02 -> 3 // Deep
                        else -> 2
                    }
                    val yPos = (yIndex.toFloat() / (yLabels.size - 1)) * (graphHeight * 0.7f) + (graphHeight * 0.15f)

                    val color = when (segment.stateId) {
                        0x00 -> Color(0xFFFFEB3B)
                        0x03 -> Color(0xFFB39DDB)
                        0x01 -> Color(0xFF7E57C2)
                        0x02 -> Color(0xFF311B92)
                        else -> Color.Gray
                    }

                    // Handle Touch
                    if (touchX in xStart..xEnd) {
                        touchedSegment = segment
                    }

                    drawRect(
                        color = if (touchedSegment == segment) color else color.copy(alpha = 0.7f),
                        topLeft = Offset(xStart, yPos - 10.dp.toPx()),
                        size = Size(width.coerceAtLeast(1f), 20.dp.toPx())
                    )

                    // Draw connecting lines for hypnogram feel
                    if (first) {
                        path.moveTo(xStart, yPos)
                        first = false
                    } else {
                        path.lineTo(xStart, yPos)
                    }
                    path.lineTo(xEnd, yPos)
                }
                
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.3f),
                    style = Stroke(width = 1.dp.toPx())
                )

                // Draw X-axis labels (Time)
                val labelCount = 4
                for (i in 0..labelCount) {
                    val minutes = start + (totalRange * i / labelCount)
                    val xPos = leftPadding + (i.toFloat() / labelCount) * graphWidth
                    val timeStr = "%02d:%02d".format((minutes.toInt() / 60) % 24, minutes.toInt() % 60)
                    val layoutResult = textMeasurer.measure(timeStr, labelStyle)
                    val labelWidth = layoutResult.size.width.toFloat()
                    
                    val xOffset = if (i == labelCount) -labelWidth else if (i == 0) 0f else -labelWidth / 2f
                    
                    drawText(
                        textMeasurer = textMeasurer,
                        text = timeStr,
                        style = labelStyle,
                        topLeft = Offset((xPos + xOffset).coerceIn(leftPadding, size.width - labelWidth), graphHeight + 6.dp.toPx())
                    )
                }
            }
            
            // Axis lines
            drawLine(Color.Gray.copy(alpha = 0.3f), Offset(leftPadding, 0f), Offset(leftPadding, graphHeight), 2.dp.toPx())
            drawLine(Color.Gray.copy(alpha = 0.3f), Offset(leftPadding, graphHeight), Offset(size.width, graphHeight), 2.dp.toPx())
        }
    }
}

@Composable
fun SleepDistributionCard(state: WatchState) {
    SleekCard {
        Text(text = "Sleep Distribution", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        if (state.sleepMinutes == null || state.sleepMinutes == 0) {
            Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                Text(text = "Waiting for data...", color = Color.Gray)
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth().height(150.dp).padding(vertical = 8.dp)) {
                val total = state.sleepMinutes.toFloat()
                val deep = (state.deepSleepMinutes ?: 0).toFloat()
                val light = (state.lightSleepMinutes ?: 0).toFloat()
                val awake = (total - deep - light).coerceAtLeast(0f)

                SleepBar(weight = deep / total, color = Color(0xFF311B92), label = "Deep")
                SleepBar(weight = light / total, color = Color(0xFF7E57C2), label = "Light")
                if (awake > 0) {
                    SleepBar(weight = awake / total, color = Color(0xFFFFEB3B), label = "Awake")
                }
            }
        }
    }
}

@Composable
fun HistorySleepCard(stats: List<com.labbaslabs.jampsfit.database.HealthEntry>, timeFormat: String) {
    val sleepData = stats.filter { (it.sleepMinutes ?: 0) > 0 }
    if (sleepData.isEmpty()) return

    SleekCard {
        Text(text = "Sleep History (Minutes)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem(label = "Deep", color = Color(0xFF311B92))
            LegendItem(label = "Light", color = Color(0xFF7E57C2))
        }
        Spacer(modifier = Modifier.height(16.dp))

        SleekGraphCard(
            title = "Total Sleep",
            dataPoints = sleepData.map { com.labbaslabs.jampsfit.database.HistoryPoint(it.sleepMinutes ?: 0, it.timestamp) },
            currentValue = sleepData.lastOrNull()?.sleepMinutes?.let { "${it / 60}h ${it % 60}m" },
            color = Color(0xFF9C27B0),
            timeFormat = timeFormat
        )
    }
}

enum class CaloriePeriodMode {
    DayProgress,
    FullDay,
    Week,
    Month
}

private fun List<com.labbaslabs.jampsfit.database.HistoryPoint>.toTotalCalories(
    dailyBasalCalories: Int,
    periodMode: CaloriePeriodMode
): List<com.labbaslabs.jampsfit.database.HistoryPoint> {
    return map { point ->
        val basal = when (periodMode) {
            CaloriePeriodMode.DayProgress -> (dailyBasalCalories * dayProgress(point.timestamp)).roundToInt()
            CaloriePeriodMode.FullDay -> dailyBasalCalories
            CaloriePeriodMode.Week -> dailyBasalCalories * 7
            CaloriePeriodMode.Month -> dailyBasalCalories * daysInMonth(point.timestamp)
        }
        com.labbaslabs.jampsfit.database.HistoryPoint(point.value + basal, point.timestamp)
    }
}

private fun daysInMonth(timestamp: Long): Int {
    return Calendar.getInstance().apply { timeInMillis = timestamp }.getActualMaximum(Calendar.DAY_OF_MONTH)
}

private fun formatMinutesOfDay(minutes: Int): String {
    return "%02d:%02d".format((minutes / 60) % 24, minutes % 60)
}

private fun smoothQuadraticPath(points: List<Offset>): Path {
    return Path().apply {
        if (points.isEmpty()) return@apply

        moveTo(points.first().x, points.first().y)
        if (points.size == 1) return@apply

        for (i in 1 until points.size) {
            val previous = points[i - 1]
            val current = points[i]
            if (i == points.lastIndex) {
                quadraticTo(previous.x, previous.y, current.x, current.y)
            } else {
                val midPoint = Offset(
                    x = (previous.x + current.x) / 2f,
                    y = (previous.y + current.y) / 2f
                )
                quadraticTo(previous.x, previous.y, midPoint.x, midPoint.y)
            }
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

@Composable
fun RowScope.SleepBar(weight: Float, color: Color, label: String) {
    if (weight > 0) {
        Column(modifier = Modifier.fillMaxHeight().weight(weight).padding(horizontal = 2.dp)) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f).background(color, RoundedCornerShape(4.dp)))
            Text(text = label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
fun SleekBarChartCard(
    title: String, 
    dataPoints: List<com.labbaslabs.jampsfit.database.HistoryPoint>, 
    currentValue: String?, 
    color: Color,
    timeFormat: String = "dd MMM",
    forceZeroMin: Boolean = false,
    fixedMax: Float? = null
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = Color.Gray, fontSize = 10.sp)
    val sdf = remember { SimpleDateFormat(timeFormat, Locale.getDefault()) }

    var touchedPoint by remember { mutableStateOf<com.labbaslabs.jampsfit.database.HistoryPoint?>(null) }
    var touchX by remember { mutableFloatStateOf(0f) }

    SleekCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                if (touchedPoint != null) {
                    Text(
                        text = sdf.format(Date(touchedPoint!!.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
            Text(
                text = touchedPoint?.value?.toString() ?: currentValue ?: "--", 
                style = MaterialTheme.typography.titleMedium, 
                fontWeight = FontWeight.Bold, 
                color = if (touchedPoint != null) color.copy(alpha = 0.8f) else color
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        if (dataPoints.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                Text(text = "Waiting for data...", color = Color.Gray)
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .pointerInput(dataPoints) {
                        detectDragGestures(
                            onDragStart = { offset -> touchX = offset.x },
                            onDrag = { change, _ -> touchX = change.position.x; change.consume() },
                            onDragEnd = { touchedPoint = null },
                            onDragCancel = { touchedPoint = null }
                        )
                    }
                    .pointerInput(dataPoints) {
                        detectTapGestures(onPress = { offset -> touchX = offset.x; tryAwaitRelease(); touchedPoint = null })
                    }
            ) {
                val leftPadding = 45.dp.toPx()
                val rightPadding = 20.dp.toPx()
                val bottomPadding = 30.dp.toPx()
                val graphWidth = size.width - leftPadding - rightPadding
                val graphHeight = size.height - bottomPadding
                
                val currentMax = fixedMax ?: (dataPoints.maxOfOrNull { it.value }?.toFloat() ?: 100f).coerceAtLeast(1f)
                val currentMin = if (forceZeroMin) 0f else (dataPoints.minOfOrNull { it.value }?.toFloat() ?: 0f)
                val range = (currentMax - currentMin).coerceAtLeast(1f)
                
                // Draw Y axis labels and grid
                val ySteps = 4
                for (i in 0..ySteps) {
                    val yVal = currentMin + (range * i / ySteps)
                    val yPos = graphHeight - (graphHeight * i / ySteps)
                    
                    drawText(
                        textMeasurer = textMeasurer,
                        text = yVal.toInt().toString(),
                        style = labelStyle,
                        topLeft = Offset(0f, (yPos - 10.dp.toPx()).coerceAtLeast(0f))
                    )
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.1f),
                        start = Offset(leftPadding, yPos),
                        end = Offset(size.width - rightPadding, yPos),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Draw X axis labels (5 labels)
                if (dataPoints.isNotEmpty()) {
                    val indices = if (dataPoints.size >= 5) {
                        listOf(0, dataPoints.size / 4, dataPoints.size / 2, 3 * dataPoints.size / 4, dataPoints.size - 1)
                    } else if (dataPoints.size >= 3) {
                        listOf(0, dataPoints.size / 2, dataPoints.size - 1)
                    } else if (dataPoints.size == 2) {
                        listOf(0, 1)
                    } else {
                        listOf(0)
                    }
                    
                    indices.forEach { index ->
                        val point = dataPoints[index]
                        val xPos = if (dataPoints.size == 1) leftPadding + graphWidth / 2f
                                   else leftPadding + (index.toFloat() / (dataPoints.size.coerceAtLeast(2) - 1)) * graphWidth
                        val timeStr = sdf.format(Date(point.timestamp))
                        
                        val labelWidth = textMeasurer.measure(timeStr, labelStyle).size.width.toFloat()
                        val xOffset = when {
                            index == dataPoints.size - 1 -> -labelWidth
                            index == 0 -> 0f
                            else -> -labelWidth / 2f
                        }

                        drawText(
                            textMeasurer = textMeasurer,
                            text = timeStr,
                            style = labelStyle,
                            topLeft = Offset((xPos + xOffset).coerceIn(leftPadding, size.width - labelWidth), graphHeight + 5.dp.toPx())
                        )
                    }
                }

                // Draw Bars
                val barSpacing = 4.dp.toPx()
                val barWidth = if (dataPoints.isNotEmpty()) {
                    (graphWidth / dataPoints.size) - barSpacing
                } else {
                    graphWidth / 2f
                }

                dataPoints.forEachIndexed { i, point ->
                    val x = leftPadding + (i.toFloat() / dataPoints.size) * graphWidth + barSpacing / 2f
                    val h = ((point.value.toFloat() - currentMin) / range) * graphHeight
                    val y = graphHeight - h

                    if (h > 0) {
                        val isSelected = touchedPoint == point
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(color, color.copy(alpha = 0.6f))
                            ),
                            topLeft = Offset(x, y),
                            size = Size(barWidth.coerceAtLeast(1f), h),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                            alpha = if (touchedPoint != null && !isSelected) 0.3f else 1.0f
                        )
                    }
                }

                // Handle Touch
                if (touchX in leftPadding..(leftPadding + graphWidth) && dataPoints.isNotEmpty()) {
                    val index = ((touchX - leftPadding) / graphWidth * dataPoints.size).toInt().coerceIn(0, dataPoints.size - 1)
                    val point = dataPoints[index]
                    touchedPoint = point
                    
                    val x = leftPadding + (index.toFloat() / dataPoints.size) * graphWidth + barSpacing / 2f + barWidth / 2f
                    drawLine(
                        color = color.copy(alpha = 0.5f),
                        start = Offset(x, 0f),
                        end = Offset(x, graphHeight),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                }
                
                // Draw Axis lines
                drawLine(Color.Gray.copy(alpha = 0.3f), Offset(leftPadding, 0f), Offset(leftPadding, graphHeight), 2.dp.toPx())
                drawLine(Color.Gray.copy(alpha = 0.3f), Offset(leftPadding, graphHeight), Offset(size.width - rightPadding, graphHeight), 2.dp.toPx())
            }
        }
    }
}

@Composable
fun SleekGraphCard(
    title: String, 
    dataPoints: List<com.labbaslabs.jampsfit.database.HistoryPoint>, 
    currentValue: String?, 
    color: Color,
    timeFormat: String = "HH:mm",
    forceZeroMin: Boolean = false,
    fixedMax: Float? = null,
    startTime: Long? = null,
    endTime: Long? = null
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = Color.Gray, fontSize = 10.sp)
    val sdf = remember { SimpleDateFormat(timeFormat, Locale.getDefault()) }
    
    var touchedPoint by remember { mutableStateOf<com.labbaslabs.jampsfit.database.HistoryPoint?>(null) }
    var touchX by remember { mutableFloatStateOf(0f) }

    SleekCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                if (touchedPoint != null) {
                    Text(
                        text = sdf.format(Date(touchedPoint!!.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
            Text(
                text = touchedPoint?.value?.toString() ?: currentValue ?: "--",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (touchedPoint != null) color.copy(alpha = 0.8f) else color
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        if (dataPoints.isEmpty() && startTime == null) {
            Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                Text(text = "Waiting for data...", color = Color.Gray)
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .pointerInput(dataPoints, startTime, endTime) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                touchX = offset.x
                            },
                            onDrag = { change, _ ->
                                touchX = change.position.x
                                change.consume()
                            },
                            onDragEnd = { touchedPoint = null },
                            onDragCancel = { touchedPoint = null }
                        )
                    }
                    .pointerInput(dataPoints, startTime, endTime) {
                        detectTapGestures(
                            onPress = { offset ->
                                touchX = offset.x
                                tryAwaitRelease()
                                touchedPoint = null
                            }
                        )
                    }
            ) {
                val leftPadding = 45.dp.toPx()
                val rightPadding = 20.dp.toPx()
                val bottomPadding = 30.dp.toPx()
                val graphWidth = size.width - leftPadding - rightPadding
                val graphHeight = size.height - bottomPadding
                
                val validPoints = dataPoints.filter { it.value > 0 || (forceZeroMin && it.value >= 0) }
                val currentMax = fixedMax ?: (validPoints.maxOfOrNull { it.value }?.toFloat() ?: 100f).coerceAtLeast(1f)
                val currentMin = if (forceZeroMin) 0f else (validPoints.minOfOrNull { it.value }?.toFloat() ?: 0f)
                val range = (currentMax - currentMin).coerceAtLeast(1f)
                
                // Draw Y axis labels and grid
                val ySteps = 4
                for (i in 0..ySteps) {
                    val yVal = currentMin + (range * i / ySteps)
                    val yPos = graphHeight - (graphHeight * i / ySteps)
                    
                    drawText(
                        textMeasurer = textMeasurer,
                        text = yVal.toInt().toString(),
                        style = labelStyle,
                        topLeft = Offset(0f, (yPos - 10.dp.toPx()).coerceAtLeast(0f))
                    )
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.1f),
                        start = Offset(leftPadding, yPos),
                        end = Offset(size.width - rightPadding, yPos),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Draw X axis labels (5 labels)
                val timeRangeStart = startTime ?: dataPoints.firstOrNull()?.timestamp ?: 0L
                val timeRangeEnd = endTime ?: dataPoints.lastOrNull()?.timestamp ?: 0L
                val timeRange = (timeRangeEnd - timeRangeStart).toFloat().coerceAtLeast(1f)

                val labelTimes = listOf(
                    timeRangeStart,
                    timeRangeStart + (timeRangeEnd - timeRangeStart) / 4,
                    timeRangeStart + (timeRangeEnd - timeRangeStart) / 2,
                    timeRangeStart + 3 * (timeRangeEnd - timeRangeStart) / 4,
                    timeRangeEnd
                )
                labelTimes.forEachIndexed { index, time ->
                    val xPos = leftPadding + ((time - timeRangeStart).toFloat() / timeRange) * graphWidth
                    val timeStr = sdf.format(Date(time))
                    val labelWidth = textMeasurer.measure(timeStr, labelStyle).size.width.toFloat()
                    val xOffset = when(index) {
                        4 -> -labelWidth
                        0 -> 0f
                        else -> -labelWidth / 2f
                    }
                    drawText(
                        textMeasurer = textMeasurer,
                        text = timeStr,
                        style = labelStyle,
                        topLeft = Offset((xPos + xOffset).coerceIn(leftPadding, size.width - labelWidth), graphHeight + 5.dp.toPx())
                    )
                }

                val pointsToDraw = dataPoints.filter { it.value > 0 || (forceZeroMin && it.value >= 0) }
                val drawPoints = pointsToDraw.map { point ->
                    Offset(
                        x = leftPadding + ((point.timestamp - timeRangeStart).toFloat() / timeRange) * graphWidth,
                        y = graphHeight - ((point.value.toFloat() - currentMin) / range) * graphHeight
                    )
                }

                val path = smoothQuadraticPath(drawPoints)
                
                if (drawPoints.size == 1) {
                    drawCircle(color = color, radius = 5.dp.toPx(), center = drawPoints[0])
                } else if (drawPoints.isNotEmpty()) {
                    // Draw glow
                    drawPath(
                        path = path,
                        color = color.copy(alpha = 0.3f),
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    // Draw main line
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    
                    if (pointsToDraw.isNotEmpty()) {
                        val firstX = leftPadding + ((pointsToDraw.first().timestamp - timeRangeStart).toFloat() / timeRange) * graphWidth
                        val lastX = leftPadding + ((pointsToDraw.last().timestamp - timeRangeStart).toFloat() / timeRange) * graphWidth

                        val fillPath = Path().apply {
                            addPath(path)
                            lineTo(lastX, graphHeight)
                            lineTo(firstX, graphHeight)
                            close()
                        }
                        drawPath(fillPath, brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.2f), Color.Transparent)))
                    }
                }
                
                // Handle Touch
                if (touchX in leftPadding..(leftPadding + graphWidth) && pointsToDraw.isNotEmpty()) {
                    val closestPoint = pointsToDraw.minByOrNull { 
                        val x = leftPadding + ((it.timestamp - timeRangeStart).toFloat() / timeRange) * graphWidth
                        kotlin.math.abs(x - touchX)
                    }
                    
                    if (closestPoint != null) {
                        touchedPoint = closestPoint
                        val x = leftPadding + ((closestPoint.timestamp - timeRangeStart).toFloat() / timeRange) * graphWidth
                        val y = graphHeight - ((closestPoint.value.toFloat() - currentMin) / range) * graphHeight
                        
                        drawLine(
                            color = color.copy(alpha = 0.5f),
                            start = Offset(x, 0f),
                            end = Offset(x, graphHeight),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 6.dp.toPx(),
                            center = Offset(x, y)
                        )
                        drawCircle(
                            color = color,
                            radius = 4.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }

                // Draw Axis lines
                drawLine(Color.Gray.copy(alpha = 0.3f), Offset(leftPadding, 0f), Offset(leftPadding, graphHeight), 2.dp.toPx())
                drawLine(Color.Gray.copy(alpha = 0.3f), Offset(leftPadding, graphHeight), Offset(size.width - rightPadding, graphHeight), 2.dp.toPx())
            }
        }
    }
}
