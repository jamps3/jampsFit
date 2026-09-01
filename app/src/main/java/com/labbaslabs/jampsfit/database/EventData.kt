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
const val DEFAULT_DANCING_EVENT_NAME = "Event"
const val DEFAULT_WATCH_EXERCISE_NAME = "Watch Exercise"
const val DEFAULT_FESTIVAL_NAME = "Event"

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

@Entity(
    tableName = "meals",
    indices = [
        Index(value = ["festivalId"]),
        Index(value = ["createdAt"])
    ]
)
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val festivalId: Long? = null,
    val name: String = "Meal",
    val type: String = "Meal",
    val calories: Int = 0,
    val details: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "supplements",
    indices = [
        Index(value = ["name"])
    ]
)
data class SupplementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "Supplement",
    val dailyTargetMg: Int = 100,
    val singleDoseMg: Int = 100,
    val selectedAmountMg: Int = singleDoseMg,
    val stepMg: Int = singleDoseMg,
    val maxDailyMg: Int = dailyTargetMg,
    val sortOrder: Int = 0,
    val rampEnabled: Boolean = false,
    val rampStartMg: Int = singleDoseMg,
    val rampTargetMg: Int = dailyTargetMg,
    val rampDays: Int = 0,
    val rampStartedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "supplement_entries",
    indices = [
        Index(value = ["festivalId"]),
        Index(value = ["supplementId"]),
        Index(value = ["takenAt"])
    ]
)
data class SupplementEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val festivalId: Long? = null,
    val supplementId: Long = 0,
    val name: String = "Supplement",
    val amountMg: Int = 0,
    val takenAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "festivals")
data class FestivalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = DEFAULT_FESTIVAL_NAME,
    val imageUri: String? = null,
    val isActive: Boolean = false,
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
    suspend fun insertFestivals(festivals: List<FestivalEntity>)

    @Insert
    suspend fun insertEvents(events: List<EventEntity>)

    @Insert
    suspend fun insertCandies(candies: List<CandyEntity>)

    @Insert
    suspend fun insertMeals(meals: List<MealEntity>)

    @Insert
    suspend fun insertSupplements(supplements: List<SupplementEntity>)
    @Insert
    suspend fun insertFestival(festival: FestivalEntity): Long

    @Update
    suspend fun updateFestival(festival: FestivalEntity)

    @Query("SELECT * FROM festivals ORDER BY createdAt ASC")
    fun observeFestivals(): Flow<List<FestivalEntity>>

    @Query("SELECT * FROM festivals ORDER BY createdAt DESC LIMIT 1")
    suspend fun getNewestFestival(): FestivalEntity?

    @Query("SELECT * FROM festivals WHERE isActive = 1 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getActiveFestival(): FestivalEntity?

    @Query("SELECT * FROM festivals WHERE id = :id LIMIT 1")
    suspend fun getFestival(id: Long): FestivalEntity?

    @Query("UPDATE festivals SET isActive = CASE WHEN id = :id THEN 1 ELSE 0 END, updatedAt = :updatedAt")
    suspend fun setActiveFestival(id: Long, updatedAt: Long)

    @Query("DELETE FROM festivals WHERE id = :id")
    suspend fun deleteFestival(id: Long)

    @Query("UPDATE events SET festivalId = NULL WHERE festivalId = :festivalId")
    suspend fun detachEventsFromFestival(festivalId: Long)

    @Query("UPDATE candies SET festivalId = NULL WHERE festivalId = :festivalId")
    suspend fun detachCandiesFromFestival(festivalId: Long)

    @Query("UPDATE meals SET festivalId = NULL WHERE festivalId = :festivalId")
    suspend fun detachMealsFromFestival(festivalId: Long)

    @Query("UPDATE supplement_entries SET festivalId = NULL WHERE festivalId = :festivalId")
    suspend fun detachSupplementEntriesFromFestival(festivalId: Long)

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

    @Insert
    suspend fun insertMeal(meal: MealEntity): Long

    @Query("SELECT * FROM meals ORDER BY createdAt DESC LIMIT 200")
    fun observeMeals(): Flow<List<MealEntity>>

    @Query("DELETE FROM meals WHERE id = :id")
    suspend fun deleteMeal(id: Long)

    @Insert
    suspend fun insertSupplement(supplement: SupplementEntity): Long

    @Update
    suspend fun updateSupplement(supplement: SupplementEntity)

    @Query("SELECT * FROM supplements ORDER BY sortOrder ASC, name ASC")
    fun observeSupplements(): Flow<List<SupplementEntity>>

    @Query("SELECT COUNT(*) FROM supplements")
    suspend fun countSupplements(): Int

    @Query("SELECT * FROM supplements WHERE name = :name LIMIT 1")
    suspend fun getSupplementByName(name: String): SupplementEntity?

    @Query("SELECT * FROM supplements WHERE id = :id LIMIT 1")
    suspend fun getSupplement(id: Long): SupplementEntity?

    @Query("UPDATE supplements SET sortOrder = :sortOrder, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSupplementOrder(id: Long, sortOrder: Int, updatedAt: Long)

    @Insert
    suspend fun insertSupplementEntry(entry: SupplementEntryEntity): Long

    @Query("SELECT * FROM supplement_entries ORDER BY takenAt DESC LIMIT 300")
    fun observeSupplementEntries(): Flow<List<SupplementEntryEntity>>

    @Query("DELETE FROM supplement_entries WHERE id = :id")
    suspend fun deleteSupplementEntry(id: Long)
}

fun defaultSupplements(): List<SupplementEntity> = listOf(
    SupplementEntity(name = "Magnesium", dailyTargetMg = 400, singleDoseMg = 100, selectedAmountMg = 100, stepMg = 100, maxDailyMg = 400, sortOrder = 0),
    SupplementEntity(name = "B", dailyTargetMg = 50, singleDoseMg = 50, selectedAmountMg = 50, stepMg = 50, maxDailyMg = 100, sortOrder = 1),
    SupplementEntity(name = "NAC", dailyTargetMg = 600, singleDoseMg = 600, selectedAmountMg = 600, stepMg = 100, maxDailyMg = 1_200, sortOrder = 2),
    SupplementEntity(name = "MSM", dailyTargetMg = 15_000, singleDoseMg = 1_000, selectedAmountMg = 1_000, stepMg = 500, maxDailyMg = 15_000, sortOrder = 3, rampEnabled = true, rampStartMg = 1_000, rampTargetMg = 15_000, rampDays = 90),
    SupplementEntity(name = "C", dailyTargetMg = 500, singleDoseMg = 500, selectedAmountMg = 500, stepMg = 100, maxDailyMg = 2_000, sortOrder = 4),
    SupplementEntity(name = "Zinc", dailyTargetMg = 15, singleDoseMg = 15, selectedAmountMg = 15, stepMg = 5, maxDailyMg = 40, sortOrder = 5),
    SupplementEntity(name = "Kreatine", dailyTargetMg = 5_000, singleDoseMg = 5_000, selectedAmountMg = 5_000, stepMg = 1_000, maxDailyMg = 5_000, sortOrder = 6),
    SupplementEntity(name = "Kalium", dailyTargetMg = 500, singleDoseMg = 500, selectedAmountMg = 500, stepMg = 100, maxDailyMg = 1_000, sortOrder = 7)
)
