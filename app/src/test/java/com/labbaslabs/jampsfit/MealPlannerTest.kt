package com.labbaslabs.jampsfit

import com.labbaslabs.jampsfit.database.FoodEntity
import com.labbaslabs.jampsfit.database.FoodRoles
import com.labbaslabs.jampsfit.database.FoodSources
import com.labbaslabs.jampsfit.food.availableFoods
import com.labbaslabs.jampsfit.food.generateMealSuggestions
import com.labbaslabs.jampsfit.food.recalculateMealWithLockedIngredient
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MealPlannerTest {
    @Test
    fun balancedMealScalesNearTargetCalories() {
        val suggestion = generateMealSuggestions(
            foods = balancedFoods(),
            targetCalories = 700,
            selectedSources = setOf(FoodSources.HOME)
        ).first()

        assertTrue(abs(suggestion.calorieDelta) <= 25)
        assertTrue(suggestion.ingredients.any { it.food.role == FoodRoles.CARB })
        assertTrue(suggestion.ingredients.any { it.food.role == FoodRoles.PROTEIN })
    }

    @Test
    fun lockedIngredientRecalculatesOtherIngredients() {
        val suggestion = generateMealSuggestions(
            foods = balancedFoods(),
            targetCalories = 700,
            selectedSources = setOf(FoodSources.HOME)
        ).first()
        val rice = suggestion.ingredients.first { it.food.name == "Rice" }

        val recalculated = recalculateMealWithLockedIngredient(
            suggestion = suggestion,
            lockedFoodId = rice.food.id,
            lockedAmount = 1f
        )

        assertEquals(1f, recalculated.ingredients.first { it.food.id == rice.food.id }.amount, 0.001f)
        assertTrue(abs(recalculated.calorieDelta) <= 25)
    }

    @Test
    fun pantryCapsLimitHomeFoodsAndShowClosestMeal() {
        val suggestion = generateMealSuggestions(
            foods = balancedFoods().map { it.copy(availableAmount = 1f) },
            targetCalories = 1_000,
            selectedSources = setOf(FoodSources.HOME)
        ).first()

        assertTrue(suggestion.totalCalories < 1_000)
        assertTrue(suggestion.ingredients.all { it.amount <= 1f })
    }

    @Test
    fun sourceFilterCanShowOnlyFastFoodOptions() {
        val suggestions = generateMealSuggestions(
            foods = balancedFoods() + food(20, "Burger", FoodSources.FAST_FOOD, FoodRoles.READY_MEAL, 550),
            targetCalories = 600,
            selectedSources = setOf(FoodSources.FAST_FOOD)
        )

        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.all { it.sourceLabel == FoodSources.FAST_FOOD })
    }

    @Test
    fun unavailableFoodsAreExcluded() {
        val foods = listOf(
            food(1, "Rice", FoodSources.HOME, FoodRoles.CARB, 100, enabled = false),
            food(2, "Chicken", FoodSources.HOME, FoodRoles.PROTEIN, 200, availableAmount = 0f)
        )

        assertTrue(availableFoods(foods, setOf(FoodSources.HOME)).isEmpty())
        assertTrue(generateMealSuggestions(foods, 500, setOf(FoodSources.HOME)).isEmpty())
    }

    private fun balancedFoods(): List<FoodEntity> = listOf(
        food(1, "Rice", FoodSources.HOME, FoodRoles.CARB, 100, defaultAmount = 2f, stepSize = 0.1f),
        food(2, "Chicken", FoodSources.HOME, FoodRoles.PROTEIN, 200, defaultAmount = 1f, stepSize = 0.1f),
        food(3, "Vegetables", FoodSources.HOME, FoodRoles.VEGETABLE, 50, defaultAmount = 1f, stepSize = 0.1f)
    )

    private fun food(
        id: Long,
        name: String,
        source: String,
        role: String,
        kcalPerUnit: Int,
        defaultAmount: Float = 1f,
        stepSize: Float = 1f,
        enabled: Boolean = true,
        availableAmount: Float? = null
    ): FoodEntity = FoodEntity(
        id = id,
        name = name,
        source = source,
        role = role,
        unitLabel = "unit",
        kcalPerUnit = kcalPerUnit,
        defaultAmount = defaultAmount,
        stepSize = stepSize,
        enabled = enabled,
        availableAmount = availableAmount
    )
}
