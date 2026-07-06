package com.labbaslabs.jampsfit.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "health_data")
data class HealthEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val battery: Int? = null,
    val heartRate: Int? = null,
    val spo2: Int? = null,
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val steps: Int? = null,
    val activityCount: Int? = null,
    val distance: Int? = null,
    val calories: Int? = null,
    val sleepMinutes: Int? = null,
    val deepSleepMinutes: Int? = null,
    val lightSleepMinutes: Int? = null
)

data class HistoryPoint(
    val value: Int,
    val timestamp: Long
)

@Dao
interface HealthDao {
    @Insert
    suspend fun insert(entry: HealthEntry)

    @Query("SELECT * FROM health_data ORDER BY timestamp DESC LIMIT 100")
    fun getLatestEntries(): Flow<List<HealthEntry>>

    @Query("SELECT * FROM health_data ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<HealthEntry>>

    @Query("SELECT * FROM health_data ORDER BY timestamp ASC")
    suspend fun getAllEntriesList(): List<HealthEntry>

    @Query("SELECT * FROM health_data WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    suspend fun getEntriesBetween(startTime: Long, endTime: Long): List<HealthEntry>

    @Query("SELECT battery as value, timestamp FROM health_data WHERE battery IS NOT NULL ORDER BY timestamp DESC LIMIT 100")
    fun getBatteryHistory(): Flow<List<HistoryPoint>>

    @Query("SELECT heartRate as value, timestamp FROM health_data WHERE heartRate IS NOT NULL AND heartRate > 0 ORDER BY timestamp DESC LIMIT 1000")
    fun getHeartRateHistory(): Flow<List<HistoryPoint>>

    @Query("SELECT spo2 as value, timestamp FROM health_data WHERE spo2 IS NOT NULL AND spo2 > 0 ORDER BY timestamp DESC LIMIT 50")
    fun getSpO2History(): Flow<List<HistoryPoint>>

    @Query("SELECT * FROM health_data WHERE systolic IS NOT NULL AND diastolic IS NOT NULL ORDER BY timestamp DESC LIMIT 50")
    fun getBloodPressureHistory(): Flow<List<HealthEntry>>

    @Query("SELECT steps as value, timestamp FROM health_data WHERE steps IS NOT NULL ORDER BY timestamp DESC LIMIT 50")
    fun getStepsHistory(): Flow<List<HistoryPoint>>

    @Query("SELECT distance as value, timestamp FROM health_data WHERE distance IS NOT NULL ORDER BY timestamp DESC LIMIT 50")
    fun getDistanceHistory(): Flow<List<HistoryPoint>>

    @Query("SELECT activityCount as value, timestamp FROM health_data WHERE activityCount IS NOT NULL ORDER BY timestamp DESC LIMIT 50")
    fun getActivityHistory(): Flow<List<HistoryPoint>>

    @Query("SELECT calories as value, timestamp FROM health_data WHERE calories IS NOT NULL ORDER BY timestamp DESC LIMIT 50")
    fun getCaloriesHistory(): Flow<List<HistoryPoint>>

    @Query("""
        SELECT 
            MIN(timestamp) as id,
            MIN(timestamp) as timestamp,
            MAX(steps) as steps,
            MAX(distance) as distance,
            MAX(calories) as calories,
            AVG(NULLIF(heartRate, 0)) as heartRate,
            AVG(NULLIF(spo2, 0)) as spo2,
            MAX(battery) as battery,
            AVG(NULLIF(systolic, 0)) as systolic,
            AVG(NULLIF(diastolic, 0)) as diastolic,
            MAX(activityCount) as activityCount,
            MAX(sleepMinutes) as sleepMinutes,
            MAX(deepSleepMinutes) as deepSleepMinutes,
            MAX(lightSleepMinutes) as lightSleepMinutes
        FROM health_data 
        WHERE timestamp > (strftime('%s', 'now') - 86400) * 1000
        GROUP BY strftime('%Y-%m-%d %H', timestamp / 1000, 'unixepoch', 'localtime') 
        ORDER BY timestamp DESC
    """)
    fun getLast24hStats(): Flow<List<HealthEntry>>

    @Query("""
        SELECT 
            MIN(timestamp) as id,
            MIN(timestamp) as timestamp,
            MAX(steps) as steps,
            MAX(distance) as distance,
            MAX(calories) as calories,
            AVG(NULLIF(heartRate, 0)) as heartRate,
            AVG(NULLIF(spo2, 0)) as spo2,
            MAX(battery) as battery,
            AVG(NULLIF(systolic, 0)) as systolic,
            AVG(NULLIF(diastolic, 0)) as diastolic,
            MAX(activityCount) as activityCount,
            MAX(sleepMinutes) as sleepMinutes,
            MAX(deepSleepMinutes) as deepSleepMinutes,
            MAX(lightSleepMinutes) as lightSleepMinutes
        FROM health_data 
        GROUP BY date(timestamp / 1000, 'unixepoch', 'localtime') 
        ORDER BY timestamp DESC 
        LIMIT 30
    """)
    fun getDailyStats(): Flow<List<HealthEntry>>

    @Query("""
        SELECT 
            MIN(timestamp) as id,
            MIN(timestamp) as timestamp,
            MAX(steps) as steps,
            MAX(distance) as distance,
            MAX(calories) as calories,
            AVG(NULLIF(heartRate, 0)) as heartRate,
            AVG(NULLIF(spo2, 0)) as spo2,
            MAX(battery) as battery,
            AVG(NULLIF(systolic, 0)) as systolic,
            AVG(NULLIF(diastolic, 0)) as diastolic,
            MAX(activityCount) as activityCount,
            MAX(sleepMinutes) as sleepMinutes,
            MAX(deepSleepMinutes) as deepSleepMinutes,
            MAX(lightSleepMinutes) as lightSleepMinutes
        FROM health_data 
        GROUP BY strftime('%Y-%W', timestamp / 1000, 'unixepoch', 'localtime') 
        ORDER BY timestamp DESC 
        LIMIT 12
    """)
    fun getWeeklyStats(): Flow<List<HealthEntry>>

    @Query("""
        SELECT 
            MIN(timestamp) as id,
            MIN(timestamp) as timestamp,
            MAX(steps) as steps,
            MAX(distance) as distance,
            MAX(calories) as calories,
            AVG(NULLIF(heartRate, 0)) as heartRate,
            AVG(NULLIF(spo2, 0)) as spo2,
            MAX(battery) as battery,
            AVG(NULLIF(systolic, 0)) as systolic,
            AVG(NULLIF(diastolic, 0)) as diastolic,
            MAX(activityCount) as activityCount,
            MAX(sleepMinutes) as sleepMinutes,
            MAX(deepSleepMinutes) as deepSleepMinutes,
            MAX(lightSleepMinutes) as lightSleepMinutes
        FROM health_data
        GROUP BY strftime('%Y-%m', timestamp / 1000, 'unixepoch', 'localtime') 
        ORDER BY timestamp DESC
        LIMIT 12
    """)
    fun getMonthlyStats(): Flow<List<HealthEntry>>

    @Query("DELETE FROM health_data")
    suspend fun deleteAll()

    @Query("DELETE FROM health_data WHERE timestamp < :threshold")
    suspend fun cleanupOldHealthData(threshold: Long)

    @Insert
    suspend fun insertUnknown(packet: UnknownPacket)

    @Query("SELECT message FROM unknown_packets ORDER BY timestamp ASC")
    fun getAllUnknownPackets(): Flow<List<String>>

    @Query("DELETE FROM unknown_packets")
    suspend fun deleteAllUnknown()

    @Query("DELETE FROM unknown_packets WHERE id NOT IN (SELECT id FROM unknown_packets ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun trimUnknownPackets(limit: Int)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSeenNotification(notification: SeenNotification): Long

    @Query("SELECT COUNT(*) FROM seen_notifications WHERE content_hash = :hash AND timestamp > :since")
    suspend fun countSeenNotification(hash: Int, since: Long): Int

    @Query("DELETE FROM seen_notifications WHERE timestamp < :threshold")
    suspend fun cleanupOldNotifications(threshold: Long)
}

@Entity(tableName = "unknown_packets")
data class UnknownPacket(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val message: String
)

@Entity(tableName = "seen_notifications", indices = [Index(value = ["content_hash"])])
data class SeenNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val content_hash: Int
)
