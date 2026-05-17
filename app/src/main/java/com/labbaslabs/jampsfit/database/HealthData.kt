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
    val calories: Int? = null
)

@Dao
interface HealthDao {
    @Insert
    suspend fun insert(entry: HealthEntry)

    @Query("SELECT * FROM health_data ORDER BY timestamp DESC LIMIT 100")
    fun getLatestEntries(): Flow<List<HealthEntry>>

    @Query("SELECT * FROM health_data ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<HealthEntry>>

    @Query("SELECT battery FROM health_data WHERE battery IS NOT NULL ORDER BY timestamp DESC LIMIT 100")
    fun getBatteryHistory(): Flow<List<Int>>

    @Query("SELECT heartRate FROM health_data WHERE heartRate IS NOT NULL ORDER BY timestamp DESC LIMIT 50")
    fun getHeartRateHistory(): Flow<List<Int>>

    @Query("SELECT spo2 FROM health_data WHERE spo2 IS NOT NULL ORDER BY timestamp DESC LIMIT 50")
    fun getSpO2History(): Flow<List<Int>>

    @Query("SELECT id, timestamp, systolic, diastolic FROM health_data WHERE systolic IS NOT NULL AND diastolic IS NOT NULL ORDER BY timestamp DESC LIMIT 50")
    fun getBloodPressureHistory(): Flow<List<HealthEntry>>

    @Query("SELECT steps FROM health_data WHERE steps IS NOT NULL ORDER BY timestamp DESC LIMIT 50")
    fun getStepsHistory(): Flow<List<Int>>

    @Query("SELECT distance FROM health_data WHERE distance IS NOT NULL ORDER BY timestamp DESC LIMIT 50")
    fun getDistanceHistory(): Flow<List<Int>>

    @Query("SELECT activityCount FROM health_data WHERE activityCount IS NOT NULL ORDER BY timestamp DESC LIMIT 50")
    fun getActivityHistory(): Flow<List<Int>>

    @Query("SELECT calories FROM health_data WHERE calories IS NOT NULL ORDER BY timestamp DESC LIMIT 50")
    fun getCaloriesHistory(): Flow<List<Int>>

    @Query("""
        SELECT 
            MIN(timestamp) as id,
            date(timestamp / 1000, 'unixepoch', 'localtime') as day,
            MAX(steps) as steps,
            MAX(distance) as distance,
            MAX(calories) as calories,
            AVG(heartRate) as heartRate,
            AVG(spo2) as spo2,
            0 as timestamp,
            NULL as battery,
            NULL as systolic,
            NULL as diastolic,
            NULL as activityCount
        FROM health_data 
        GROUP BY day 
        ORDER BY day DESC 
        LIMIT 30
    """)
    fun getDailyStats(): Flow<List<HealthEntry>>

    @Query("""
        SELECT 
            MIN(timestamp) as id,
            strftime('%Y-%W', timestamp / 1000, 'unixepoch', 'localtime') as week,
            MAX(steps) as steps,
            MAX(distance) as distance,
            MAX(calories) as calories,
            AVG(heartRate) as heartRate,
            AVG(spo2) as spo2,
            0 as timestamp,
            NULL as battery,
            NULL as systolic,
            NULL as diastolic,
            NULL as activityCount
        FROM health_data 
        GROUP BY week 
        ORDER BY week DESC 
        LIMIT 12
    """)
    fun getWeeklyStats(): Flow<List<HealthEntry>>

    @Query("""
        SELECT 
            MIN(timestamp) as id,
            strftime('%Y-%m', timestamp / 1000, 'unixepoch', 'localtime') as month,
            MAX(steps) as steps,
            MAX(distance) as distance,
            MAX(calories) as calories,
            AVG(heartRate) as heartRate,
            AVG(spo2) as spo2,
            0 as timestamp,
            NULL as battery,
            NULL as systolic,
            NULL as diastolic,
            NULL as activityCount
        FROM health_data 
        GROUP BY month 
        ORDER BY month DESC 
        LIMIT 12
    """)
    fun getMonthlyStats(): Flow<List<HealthEntry>>

    @Query("DELETE FROM health_data")
    suspend fun deleteAll()

    @Insert
    suspend fun insertUnknown(packet: UnknownPacket)

    @Query("SELECT message FROM unknown_packets ORDER BY timestamp ASC")
    fun getAllUnknownPackets(): Flow<List<String>>

    @Query("DELETE FROM unknown_packets")
    suspend fun deleteAllUnknown()
}

@Entity(tableName = "unknown_packets")
data class UnknownPacket(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val message: String
)
