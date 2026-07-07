package com.labbaslabs.jampsfit.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.Ignore
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlin.math.max

const val EVENT_TYPE_DANCING = "Dancing"
const val EVENT_TYPE_WATCH_EXERCISE = "Watch Exercise"
const val DEFAULT_DANCING_EVENT_NAME = "Dancing Event"
const val DEFAULT_WATCH_EXERCISE_NAME = "Watch Exercise"
const val DEFAULT_FESTIVAL_NAME = "Life Festival"

@Entity(
    tableName = "candies",
    indices = [
        Index(value = ["festivalId"]),
        Index(value = ["startTime"])
    ]
)
data class CandyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val festivalId: Long? = null,
    val name: String = "Candy",
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = startTime,
    val size: Int = 0,
    val createdAt: Long = startTime
)

@Entity(tableName = "festivals")
data class FestivalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = DEFAULT_FESTIVAL_NAME,
    val imageUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
)

@Entity(
    tableName = "events",
    indices = [
        Index(value = ["endTime"]),
        Index(value = ["type", "startTime"])
    ]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val festivalId: Long? = null,
    val type: String = EVENT_TYPE_DANCING,
    val name: String = DEFAULT_DANCING_EVENT_NAME,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val startSteps: Int? = null,
    val startActivityCount: Int? = null,
    val startDistance: Int? = null,
    val startCalories: Int? = null,
    val durationSeconds: Int = 0,
    val stepDelta: Int = 0,
    val activityDelta: Int = 0,
    val distanceDelta: Int = 0,
    val calorieDelta: Int = 0,
    val heartRateSamples: Int = 0,
    val averageBpm: Int? = null,
    val minBpm: Int? = null,
    val maxBpm: Int? = null,
    val estimatedWorkoutCalories: Int = 0,
    val lastUpdatedTime: Long = startTime
) {
    @get:Ignore
    val isActive: Boolean get() = endTime == null
    @get:Ignore
    val activeCalories: Int get() = max(calorieDelta, estimatedWorkoutCalories)
}

@Dao
interface EventDao {
    @Insert
    suspend fun insertFestival(festival: FestivalEntity): Long

    @Update
    suspend fun updateFestival(festival: FestivalEntity)

    @Query("SELECT * FROM festivals ORDER BY createdAt ASC")
    fun observeFestivals(): Flow<List<FestivalEntity>>

    @Query("SELECT * FROM festivals ORDER BY createdAt DESC LIMIT 1")
    suspend fun getNewestFestival(): FestivalEntity?

    @Query("SELECT * FROM festivals WHERE id = :id LIMIT 1")
    suspend fun getFestival(id: Long): FestivalEntity?

    @Insert
    suspend fun insert(event: EventEntity): Long

    @Update
    suspend fun update(event: EventEntity)

    @Query("SELECT * FROM events WHERE id = :id LIMIT 1")
    suspend fun getEvent(id: Long): EventEntity?

    @Query("SELECT * FROM events WHERE type = :type AND endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveEventOnce(type: String = EVENT_TYPE_DANCING): EventEntity?

    @Query("SELECT * FROM events WHERE type = :type AND endTime IS NULL ORDER BY startTime DESC")
    suspend fun getActiveEventsOnce(type: String = EVENT_TYPE_DANCING): List<EventEntity>

    @Query("SELECT * FROM events WHERE type = :type AND endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    fun observeActiveEvent(type: String = EVENT_TYPE_DANCING): Flow<EventEntity?>

    @Query("SELECT * FROM events ORDER BY startTime DESC LIMIT 100")
    fun observeRecentEvents(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE festivalId = :festivalId ORDER BY startTime DESC LIMIT 100")
    fun observeEventsForFestival(festivalId: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE type = :type AND startTime BETWEEN :startTime - :toleranceMs AND :startTime + :toleranceMs LIMIT 1")
    suspend fun findEventNearStart(type: String, startTime: Long, toleranceMs: Long): EventEntity?

    @Query("SELECT * FROM events WHERE type = :type AND endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun findOpenEventByType(type: String): EventEntity?

    @Query(
        """
        SELECT * FROM events
        WHERE type = :type
        AND startTime <= :endTime + :toleranceMs
        AND COALESCE(endTime, lastUpdatedTime, startTime) >= :startTime - :toleranceMs
        ORDER BY startTime DESC
        LIMIT 1
        """
    )
    suspend fun findEventOverlapping(type: String, startTime: Long, endTime: Long, toleranceMs: Long): EventEntity?

    @Query("UPDATE events SET festivalId = :festivalId, lastUpdatedTime = :updatedAt WHERE id = :eventId")
    suspend fun attachEventToFestival(eventId: Long, festivalId: Long, updatedAt: Long)

    @Query("DELETE FROM events WHERE id = :eventId")
    suspend fun deleteEvent(eventId: Long)

    @Insert
    suspend fun insertCandy(candy: CandyEntity): Long

    @Query("SELECT * FROM candies ORDER BY startTime DESC LIMIT 200")
    fun observeCandies(): Flow<List<CandyEntity>>

    @Query("DELETE FROM candies WHERE id = :id")
    suspend fun deleteCandy(id: Long)
}
