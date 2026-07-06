package com.labbaslabs.jampsfit.food

import com.labbaslabs.jampsfit.database.FoodEntity
import com.labbaslabs.jampsfit.database.FoodRoles
import com.labbaslabs.jampsfit.database.FoodSources
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

data class MealIngredient(
    val food: FoodEntity,
    val amount: Float,
    val locked: Boolean = false
) {
    val calories: Int get() = (amount * food.kcalPerUnit).roundToInt()
}

data class MealSuggestion(
    val id: String,
    val title: String,
    val sourceLabel: String,
    val targetCalories: Int,
    val ingredients: List<MealIngredient>
) {
    val totalCalories: Int get() = ingredients.sumOf { it.calories }
    val calorieDelta: Int get() = totalCalories - targetCalories
    val isCloseMatch: Boolean get() = abs(calorieDelta) <= 25
}

fun availableFoods(foods: List<FoodEntity>, selectedSources: Set<String>): List<FoodEntity> {
    return foods
        .filter { food ->
            food.enabled &&
                food.kcalPerUnit > 0 &&
                food.defaultAmount > 0f &&
                food.stepSize > 0f &&
                food.source in selectedSources &&
                (food.source != FoodSources.HOME || (food.availableAmount ?: 1f) > 0f)
        }
        .sortedWith(compareBy<FoodEntity> { FoodSources.all.indexOf(it.source).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
            .thenBy { FoodRoles.all.indexOf(it.role).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
            .thenBy { it.name.lowercase() })
}

fun generateMealSuggestions(
    foods: List<FoodEntity>,
    targetCalories: Int,
    selectedSources: Set<String>,
    limit: Int = 8
): List<MealSuggestion> {
    if (targetCalories <= 0) return emptyList()

    val available = availableFoods(foods, selectedSources)
    val suggestions = mutableListOf<MealSuggestion>()

    if (FoodSources.HOME in selectedSources) {
        suggestions += balancedMeals(
            foods = available.filter { it.source == FoodSources.HOME },
            targetCalories = targetCalories,
            sourceLabel = FoodSources.HOME
        )
    }
    if (FoodSources.STORE in selectedSources) {
        suggestions += balancedMeals(
            foods = available.filter { it.source == FoodSources.STORE },
            targetCalories = targetCalories,
            sourceLabel = FoodSources.STORE
        )
    }
    if (FoodSources.HOME in selectedSources && FoodSources.STORE in selectedSources) {
        suggestions += balancedMeals(
            foods = available.filter { it.source == FoodSources.HOME || it.source == FoodSources.STORE },
            targetCalories = targetCalories,
            sourceLabel = "Home + Store"
        )
    }
    if (FoodSources.FAST_FOOD in selectedSources) {
        suggestions += readyMealOptions(
            foods = available.filter { it.source == FoodSources.FAST_FOOD },
            targetCalories = targetCalories
        )
    }

    return suggestions
        .distinctBy { suggestion -> suggestion.ingredients.map { it.food.id to it.food.name } }
        .sortedWith(compareBy<MealSuggestion> { abs(it.calorieDelta) }.thenBy { it.title })
        .take(limit)
}

fun recalculateMealWithLockedIngredient(
    suggestion: MealSuggestion,
    lockedFoodId: Long,
    lockedAmount: Float
): MealSuggestion {
    val foods = suggestion.ingredients.map { it.food }
    return scaleMeal(
        id = "${suggestion.id}-locked-$lockedFoodId",
        title = suggestion.title,
        sourceLabel = suggestion.sourceLabel,
        foods = foods,
        targetCalories = suggestion.targetCalories,
        lockedFoodId = lockedFoodId,
        lockedAmount = lockedAmount
    )
}

private fun balancedMeals(
    foods: List<FoodEntity>,
    targetCalories: Int,
    sourceLabel: String
): List<MealSuggestion> {
    val carbs = foods.byRole(FoodRoles.CARB)
    val proteins = foods.byRole(FoodRoles.PROTEIN)
    val vegetables = foods.byRole(FoodRoles.VEGETABLE)
    val fats = foods.byRole(FoodRoles.FAT)

    if (carbs.isEmpty() || proteins.isEmpty()) return emptyList()

    val vegetableOptions = vegetables.take(2).ifEmpty { listOf(null) }
    val fatOptions = fats.take(2).ifEmpty { listOf(null) }
    val suggestions = mutableListOf<MealSuggestion>()

    carbs.take(3).forEach { carb ->
        proteins.take(3).forEach { protein ->
            vegetableOptions.forEach { vegetable ->
                fatOptions.forEach { fat ->
                    val mealFoods = listOfNotNull(carb, protein, vegetable, fat)
                    suggestions += scaleMeal(
                        id = "${sourceLabel}-${mealFoods.joinToString("-") { it.name }}",
                        title = listOf(carb.name, protein.name).joinToString(" + "),
                        sourceLabel = sourceLabel,
                        foods = mealFoods,
                        targetCalories = targetCalories
                    )
                }
            }
        }
    }

    return suggestions
}

private fun readyMealOptions(foods: List<FoodEntity>, targetCalories: Int): List<MealSuggestion> {
    val readyMeals = foods.filter { it.role == FoodRoles.READY_MEAL }.ifEmpty { foods }
    val suggestions = mutableListOf<MealSuggestion>()

    readyMeals.take(6).forEach { food ->
        suggestions += scaleMeal(
            id = "fast-${food.name}",
            title = food.name,
            sourceLabel = FoodSources.FAST_FOOD,
            foods = listOf(food),
            targetCalories = targetCalories
        )
    }

    readyMeals.take(4).windowed(size = 2, step = 1, partialWindows = false).forEach { pair ->
        suggestions += scaleMeal(
            id = "fast-${pair.joinToString("-") { it.name }}",
            title = pair.joinToString(" + ") { it.name },
            sourceLabel = FoodSources.FAST_FOOD,
            foods = pair,
            targetCalories = targetCalories
        )
    }

    return suggestions
}

private fun scaleMeal(
    id: String,
    title: String,
    sourceLabel: String,
    foods: List<FoodEntity>,
    targetCalories: Int,
    lockedFoodId: Long? = null,
    lockedAmount: Float? = null
): MealSuggestion {
    val sanitizedTarget = targetCalories.coerceAtLeast(0)
    val amounts = foods.associateWith { 0f }.toMutableMap()
    val cappedFoods = mutableSetOf<FoodEntity>()
    val lockedFood = foods.firstOrNull { it.id == lockedFoodId }

    if (lockedFood != null && lockedAmount != null) {
        val safeAmount = lockedAmount
            .coerceAtLeast(0f)
            .coerceToAvailableAmount(lockedFood)
            .roundToStep(lockedFood.stepSize)
            .coerceToAvailableAmount(lockedFood)
        amounts[lockedFood] = safeAmount
        cappedFoods += lockedFood
    }

    for (pass in 0 until 6) {
        val flexibleFoods = foods.filter { it !in cappedFoods }
        val flexibleBaseCalories = flexibleFoods.sumOf { (it.defaultAmount * it.kcalPerUnit).roundToInt() }
        if (flexibleFoods.isEmpty() || flexibleBaseCalories <= 0) break

        val fixedCalories = cappedFoods.sumOf { food ->
            ((amounts[food] ?: 0f) * food.kcalPerUnit).roundToInt()
        }
        val remainingCalories = max(0, sanitizedTarget - fixedCalories)
        val scale = remainingCalories.toFloat() / flexibleBaseCalories
        var cappedThisPass = false

        flexibleFoods.forEach { food ->
            val proposed = (food.defaultAmount * scale)
                .coerceAtLeast(0f)
                .roundToStep(food.stepSize)
                .coerceToAvailableAmount(food)
            amounts[food] = proposed
            if (food.source == FoodSources.HOME && food.availableAmount != null && proposed >= food.availableAmount) {
                cappedFoods += food
                cappedThisPass = true
            }
        }

        if (!cappedThisPass) break
    }

    val ingredients = foods.map { food ->
        MealIngredient(
            food = food,
            amount = amounts[food]?.coerceAtLeast(0f) ?: 0f,
            locked = lockedFood?.id == food.id
        )
    }.filter { it.amount > 0f || it.locked }

    return MealSuggestion(
        id = id,
        title = title,
        sourceLabel = sourceLabel,
        targetCalories = sanitizedTarget,
        ingredients = ingredients
    )
}

private fun List<FoodEntity>.byRole(role: String): List<FoodEntity> = filter { it.role == role }

private fun Float.roundToStep(stepSize: Float): Float {
    if (stepSize <= 0f) return this
    return ((this / stepSize).roundToInt() * stepSize).coerceAtLeast(0f)
}

private fun Float.coerceToAvailableAmount(food: FoodEntity): Float {
    val available = food.availableAmount
    return if (food.source == FoodSources.HOME && available != null) coerceAtMost(available) else this
}
