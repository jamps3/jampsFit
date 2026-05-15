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
    
    @Query("DELETE FROM health_data")
    suspend fun deleteAll()
}
