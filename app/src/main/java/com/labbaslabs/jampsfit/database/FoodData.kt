package com.labbaslabs.jampsfit.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

object FoodSources {
    const val HOME = "Home"
    const val STORE = "Store"
    const val FAST_FOOD = "Fast food"

    val all = listOf(HOME, STORE, FAST_FOOD)
}

object FoodRoles {
    const val CARB = "Carb"
    const val PROTEIN = "Protein"
    const val VEGETABLE = "Vegetable"
    const val FAT = "Fat"
    const val READY_MEAL = "Ready meal"

    val all = listOf(CARB, PROTEIN, VEGETABLE, FAT, READY_MEAL)
}

@Entity(
    tableName = "foods",
    indices = [
        Index(value = ["source"]),
        Index(value = ["role"])
    ]
)
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val source: String,
    val role: String,
    val unitLabel: String,
    val kcalPerUnit: Int,
    val defaultAmount: Float,
    val stepSize: Float,
    val enabled: Boolean = true,
    val availableAmount: Float? = null,
    val isCustom: Boolean = false,
    val onShoppingList: Boolean = false
)

@Dao
interface FoodDao {
    @Query("SELECT * FROM foods ORDER BY source, role, name")
    fun observeFoods(): Flow<List<FoodEntity>>

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun countFoods(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(food: FoodEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(foods: List<FoodEntity>)

    @Update
    suspend fun update(food: FoodEntity)

    @Query("DELETE FROM foods WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE foods SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE foods SET availableAmount = :availableAmount WHERE id = :id")
    suspend fun setAvailableAmount(id: Long, availableAmount: Float?)

    @Query("UPDATE foods SET onShoppingList = :onShoppingList WHERE id = :id")
    suspend fun setOnShoppingList(id: Long, onShoppingList: Boolean)

    @Query("SELECT * FROM foods WHERE id = :id LIMIT 1")
    suspend fun getFood(id: Long): FoodEntity?

    @Query("SELECT * FROM foods WHERE source = :source AND role = :role AND name = :name LIMIT 1")
    suspend fun getFood(source: String, role: String, name: String): FoodEntity?
}

fun defaultFoods(): List<FoodEntity> = listOf(
    FoodEntity(
        name = "Cooked rice",
        source = FoodSources.HOME,
        role = FoodRoles.CARB,
        unitLabel = "dl",
        kcalPerUnit = 130,
        defaultAmount = 2f,
        stepSize = 0.5f,
        availableAmount = 6f
    ),
    FoodEntity(
        name = "Cooked pasta",
        source = FoodSources.HOME,
        role = FoodRoles.CARB,
        unitLabel = "dl",
        kcalPerUnit = 150,
        defaultAmount = 2f,
        stepSize = 0.5f,
        availableAmount = 5f
    ),
    FoodEntity(
        name = "Potatoes",
        source = FoodSources.HOME,
        role = FoodRoles.CARB,
        unitLabel = "piece",
        kcalPerUnit = 80,
        defaultAmount = 3f,
        stepSize = 1f,
        availableAmount = 8f
    ),
    FoodEntity(
        name = "Chicken breast",
        source = FoodSources.HOME,
        role = FoodRoles.PROTEIN,
        unitLabel = "100 g",
        kcalPerUnit = 165,
        defaultAmount = 1.5f,
        stepSize = 0.5f,
        availableAmount = 4f
    ),
    FoodEntity(
        name = "Egg",
        source = FoodSources.HOME,
        role = FoodRoles.PROTEIN,
        unitLabel = "piece",
        kcalPerUnit = 75,
        defaultAmount = 2f,
        stepSize = 1f,
        availableAmount = 6f
    ),
    FoodEntity(
        name = "Tuna",
        source = FoodSources.HOME,
        role = FoodRoles.PROTEIN,
        unitLabel = "can",
        kcalPerUnit = 160,
        defaultAmount = 1f,
        stepSize = 0.5f,
        availableAmount = 3f
    ),
    FoodEntity(
        name = "Mixed vegetables",
        source = FoodSources.HOME,
        role = FoodRoles.VEGETABLE,
        unitLabel = "dl",
        kcalPerUnit = 45,
        defaultAmount = 2f,
        stepSize = 0.5f,
        availableAmount = 8f
    ),
    FoodEntity(
        name = "Olive oil",
        source = FoodSources.HOME,
        role = FoodRoles.FAT,
        unitLabel = "tbsp",
        kcalPerUnit = 120,
        defaultAmount = 1f,
        stepSize = 0.5f,
        availableAmount = 6f
    ),
    FoodEntity(
        name = "Bread",
        source = FoodSources.STORE,
        role = FoodRoles.CARB,
        unitLabel = "slice",
        kcalPerUnit = 85,
        defaultAmount = 3f,
        stepSize = 1f
    ),
    FoodEntity(
        name = "Skyr",
        source = FoodSources.STORE,
        role = FoodRoles.PROTEIN,
        unitLabel = "cup",
        kcalPerUnit = 150,
        defaultAmount = 1f,
        stepSize = 1f
    ),
    FoodEntity(
        name = "Salad bag",
        source = FoodSources.STORE,
        role = FoodRoles.VEGETABLE,
        unitLabel = "bag",
        kcalPerUnit = 55,
        defaultAmount = 1f,
        stepSize = 0.5f
    ),
    FoodEntity(
        name = "Nuts",
        source = FoodSources.STORE,
        role = FoodRoles.FAT,
        unitLabel = "30 g",
        kcalPerUnit = 180,
        defaultAmount = 1f,
        stepSize = 0.5f
    ),
    FoodEntity(
        name = "Pizza slice",
        source = FoodSources.FAST_FOOD,
        role = FoodRoles.READY_MEAL,
        unitLabel = "slice",
        kcalPerUnit = 285,
        defaultAmount = 2f,
        stepSize = 1f
    ),
    FoodEntity(
        name = "Burger",
        source = FoodSources.FAST_FOOD,
        role = FoodRoles.READY_MEAL,
        unitLabel = "burger",
        kcalPerUnit = 550,
        defaultAmount = 1f,
        stepSize = 1f
    ),
    FoodEntity(
        name = "Fries",
        source = FoodSources.FAST_FOOD,
        role = FoodRoles.READY_MEAL,
        unitLabel = "portion",
        kcalPerUnit = 365,
        defaultAmount = 1f,
        stepSize = 0.5f
    ),
    FoodEntity(
        name = "Chicken wrap",
        source = FoodSources.FAST_FOOD,
        role = FoodRoles.READY_MEAL,
        unitLabel = "wrap",
        kcalPerUnit = 430,
        defaultAmount = 1f,
        stepSize = 1f
    )
)
