package com.labbaslabs.jampsfit.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.labbaslabs.jampsfit.LocalMainViewModel
import com.labbaslabs.jampsfit.WatchState
import com.labbaslabs.jampsfit.database.FoodEntity
import com.labbaslabs.jampsfit.database.FoodSources
import com.labbaslabs.jampsfit.food.CalorieTargetMode
import com.labbaslabs.jampsfit.food.MealIngredient
import com.labbaslabs.jampsfit.food.MealSuggestion
import com.labbaslabs.jampsfit.food.availableFoods
import com.labbaslabs.jampsfit.food.calculateEatCalorieTarget
import com.labbaslabs.jampsfit.food.generateMealSuggestions
import com.labbaslabs.jampsfit.food.recalculateMealWithLockedIngredient
import com.labbaslabs.jampsfit.ui.components.SleekCard
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
fun EatScreen(state: WatchState, scrollState: ScrollState = rememberScrollState()) {
    val viewModel = LocalMainViewModel.current
    var targetModeName by rememberSaveable { mutableStateOf(CalorieTargetMode.TotalSoFar.name) }
    var lockedSuggestionId by rememberSaveable { mutableStateOf<String?>(null) }
    var lockedFoodId by rememberSaveable { mutableLongStateOf(0L) }
    var lockedAmount by rememberSaveable { mutableFloatStateOf(0f) }
    var chosenMeal by remember { mutableStateOf<Map<Long, Float>>(emptyMap()) }
    var confirmMealReset by remember { mutableStateOf(false) }

    val targetMode = CalorieTargetMode.valueOf(targetModeName)
    val rawTargetCalories = calculateEatCalorieTarget(state, targetMode)
    val targetCalories = (rawTargetCalories - state.appliedMealCalories).coerceAtLeast(0)
    val selectedSources = buildSet {
        if (state.eatShowHome) add(FoodSources.HOME)
        if (state.eatShowStore) add(FoodSources.STORE)
        if (state.eatShowFastFood) add(FoodSources.FAST_FOOD)
    }
    val visibleFoods = remember(state.foods, selectedSources) { availableFoods(state.foods, selectedSources) }
    val suggestions = remember(state.foods, targetCalories, selectedSources) {
        generateMealSuggestions(
            foods = state.foods,
            targetCalories = targetCalories,
            selectedSources = selectedSources
        )
    }
    val shoppingFoods = remember(state.foods) {
        state.foods.filter { it.onShoppingList }.sortedWith(compareBy<FoodEntity> { it.source }.thenBy { it.name })
    }
    val chosenIngredients = remember(state.foods, chosenMeal) {
        chosenMeal.mapNotNull { (foodId, amount) ->
            state.foods.firstOrNull { it.id == foodId }?.let { food ->
                MealIngredient(food = food, amount = amount)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Eat", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SleekCard(borderColor = Color(0xFFFF9800)) {
            Text("Calories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge(Icons.Default.LocalFireDepartment, Color(0xFFFF9800))
                    Column {
                        Text("$targetCalories kcal", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                        Text(
                            if (state.appliedMealCalories > 0) {
                                "${state.appliedMealCalories} kcal applied"
                            } else if (targetMode == CalorieTargetMode.TotalSoFar) {
                                "Total so far"
                            } else {
                                "Active burned"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = targetMode == CalorieTargetMode.TotalSoFar,
                    onClick = {
                        targetModeName = CalorieTargetMode.TotalSoFar.name
                        lockedSuggestionId = null
                    },
                    label = { Text("Total") }
                )
                FilterChip(
                    selected = targetMode == CalorieTargetMode.ActiveBurned,
                    onClick = {
                        targetModeName = CalorieTargetMode.ActiveBurned.name
                        lockedSuggestionId = null
                    },
                    label = { Text("Active") }
                )
                AssistChip(
                    onClick = {
                        if (state.eatCaloriesIncremental) {
                            confirmMealReset = true
                        } else {
                            viewModel.resetAppliedMealCalories()
                        }
                    },
                    label = { Text("Reset meals") },
                    enabled = state.appliedMealCalories > 0
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !state.eatCaloriesIncremental,
                    onClick = { viewModel.updateEatCaloriesIncremental(false) },
                    label = { Text("Clear at midnight") }
                )
                FilterChip(
                    selected = state.eatCaloriesIncremental,
                    onClick = { viewModel.updateEatCaloriesIncremental(true) },
                    label = { Text("Count forever") }
                )
            }
        }

        ChosenMealCard(
            ingredients = chosenIngredients,
            targetCalories = targetCalories,
            onAmountChange = { ingredient, amount ->
                chosenMeal = chosenMeal + (ingredient.food.id to amount)
            },
            onKcalChange = { food, kcal -> viewModel.saveFood(food.copy(kcalPerUnit = kcal)) },
            onApply = { calories ->
                viewModel.applyMealCalories(calories)
                chosenMeal = emptyMap()
                lockedSuggestionId = null
                lockedFoodId = 0L
                lockedAmount = 0f
            },
            onRemove = { food ->
                chosenMeal = chosenMeal - food.id
            }
        )

        CategoryFilterCard(
            showHome = state.eatShowHome,
            showStore = state.eatShowStore,
            showFastFood = state.eatShowFastFood,
            onShowHomeChange = {
                viewModel.updateEatSourceFilters(it, state.eatShowStore, state.eatShowFastFood)
                lockedSuggestionId = null
            },
            onShowStoreChange = {
                viewModel.updateEatSourceFilters(state.eatShowHome, it, state.eatShowFastFood)
                lockedSuggestionId = null
            },
            onShowFastFoodChange = {
                viewModel.updateEatSourceFilters(state.eatShowHome, state.eatShowStore, it)
                lockedSuggestionId = null
            }
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Meal Options", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (suggestions.isEmpty()) {
                SleekCard {
                    Text("No matching foods available", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(suggestions, key = { it.id }) { suggestion ->
                        val displaySuggestion = if (lockedSuggestionId == suggestion.id && lockedFoodId != 0L) {
                            recalculateMealWithLockedIngredient(suggestion, lockedFoodId, lockedAmount)
                        } else {
                            suggestion
                        }
                        MealSuggestionCard(
                            suggestion = displaySuggestion,
                            modifier = Modifier.widthIn(min = 300.dp, max = 340.dp),
                            onAmountChange = { ingredient, amount ->
                                lockedSuggestionId = suggestion.id
                                lockedFoodId = ingredient.food.id
                                lockedAmount = amount
                            },
                            onKcalChange = { food, kcal -> viewModel.saveFood(food.copy(kcalPerUnit = kcal)) },
                            onAddToShoppingList = { food -> viewModel.setFoodOnShoppingList(food.id, true) }
                        )
                    }
                }
            }
        }

        CanEatNowCard(
            foods = visibleFoods,
            onAddToChosenMeal = { food ->
                chosenMeal = chosenMeal + (food.id to food.defaultAmount)
            },
            onKcalChange = { food, kcal -> viewModel.saveFood(food.copy(kcalPerUnit = kcal)) },
            onShoppingListChange = { food, checked -> viewModel.setFoodOnShoppingList(food.id, checked) }
        )

        ShoppingListCard(
            foods = shoppingFoods,
            checkedIds = state.shoppingListCheckedIds,
            onCheckedChange = { food, checked -> viewModel.setShoppingListChecked(food.id, checked) },
            onQuantityChange = { food, amount -> viewModel.saveFood(food.copy(defaultAmount = amount)) },
            onRemove = { viewModel.setFoodOnShoppingList(it.id, false) }
        )
    }

    if (confirmMealReset) {
        AlertDialog(
            onDismissRequest = { confirmMealReset = false },
            title = { Text("Reset counted calories?") },
            text = { Text("This clears all applied Chosen Meal calories from the forever counter.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAppliedMealCalories()
                        confirmMealReset = false
                    }
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmMealReset = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ChosenMealCard(
    ingredients: List<MealIngredient>,
    targetCalories: Int,
    onAmountChange: (MealIngredient, Float) -> Unit,
    onKcalChange: (FoodEntity, Int) -> Unit,
    onApply: (Int) -> Unit,
    onRemove: (FoodEntity) -> Unit
) {
    val totalCalories = ingredients.sumOf { it.calories }
    SleekCard(borderColor = Color(0xFFFFC107)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBadge(Icons.Default.Restaurant, Color(0xFFFFC107))
                Column {
                    Text("Chosen Meal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("$totalCalories / $targetCalories kcal", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF9800))
                }
            }
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        calorieDeltaText(totalCalories - targetCalories),
                        color = if (totalCalories <= targetCalories) Color(0xFF8BC34A) else Color(0xFFFFC107),
                        fontSize = 11.sp
                    )
                }
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        if (ingredients.isEmpty()) {
            Text("Add items from Can Eat Now", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ingredients.forEach { ingredient ->
                    IngredientAmountRow(
                        ingredient = ingredient,
                        onAmountChange = { onAmountChange(ingredient, it) },
                        onKcalChange = { onKcalChange(ingredient.food, it) },
                        onAddToShoppingList = { onRemove(ingredient.food) },
                        actionIcon = Icons.Default.RemoveShoppingCart,
                        actionTint = Color.Gray,
                        actionDescription = "Remove",
                        alwaysShowAction = true
                    )
                }
                Button(
                    onClick = { onApply(totalCalories) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply Chosen Meal")
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterCard(
    showHome: Boolean,
    showStore: Boolean,
    showFastFood: Boolean,
    onShowHomeChange: (Boolean) -> Unit,
    onShowStoreChange: (Boolean) -> Unit,
    onShowFastFoodChange: (Boolean) -> Unit
) {
    SleekCard {
        Text("Food Categories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SourceChip(FoodSources.HOME, Icons.Default.Home, showHome, onShowHomeChange)
            SourceChip(FoodSources.STORE, Icons.Default.Store, showStore, onShowStoreChange)
            SourceChip(FoodSources.FAST_FOOD, Icons.Default.Fastfood, showFastFood, onShowFastFoodChange)
        }
    }
}

@Composable
private fun ShoppingListCard(
    foods: List<FoodEntity>,
    checkedIds: Set<Long>,
    onCheckedChange: (FoodEntity, Boolean) -> Unit,
    onQuantityChange: (FoodEntity, Float) -> Unit,
    onRemove: (FoodEntity) -> Unit
) {
    SleekCard(borderColor = Color(0xFF4CAF50)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBadge(Icons.Default.ShoppingCart, Color(0xFF4CAF50))
            Text("Shopping List", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(10.dp))
        if (foods.isEmpty()) {
            Text("Empty", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                foods.forEach { food ->
                    var quantityText by remember(food.id, food.defaultAmount) { mutableStateOf(food.defaultAmount.formatAmount()) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Checkbox(
                                checked = food.id in checkedIds,
                                onCheckedChange = { onCheckedChange(food, it) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(food.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${food.source} • ${food.kcalPerUnit} kcal/${food.unitLabel}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    val next = ((quantityText.toFloatOrNull() ?: food.defaultAmount) - food.stepSize)
                                        .coerceAtLeast(food.stepSize)
                                    quantityText = next.formatAmount()
                                    onQuantityChange(food, next)
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease quantity", tint = Color.Gray)
                            }
                            QuantityField(
                                value = quantityText,
                                unitLabel = food.unitLabel,
                                onValueChange = { next ->
                                    quantityText = next
                                    next.toFloatOrNull()?.takeIf { it > 0f }?.let { onQuantityChange(food, it) }
                                },
                                modifier = Modifier.widthIn(min = 76.dp, max = 92.dp)
                            )
                            IconButton(
                                onClick = {
                                    val next = ((quantityText.toFloatOrNull() ?: food.defaultAmount) + food.stepSize)
                                        .coerceAtMost(100f)
                                    quantityText = next.formatAmount()
                                    onQuantityChange(food, next)
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase quantity", tint = Color(0xFF4CAF50))
                            }
                        }
                        IconButton(onClick = { onRemove(food) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.RemoveShoppingCart, contentDescription = "Remove", tint = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuantityField(
    value: String,
    unitLabel: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { char -> char.isDigit() || char == '.' }.take(5)) },
        label = { Text(unitLabel) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}

@Composable
private fun MealSuggestionCard(
    suggestion: MealSuggestion,
    modifier: Modifier,
    onAmountChange: (MealIngredient, Float) -> Unit,
    onKcalChange: (FoodEntity, Int) -> Unit,
    onAddToShoppingList: (FoodEntity) -> Unit
) {
    SleekCard(modifier = modifier, borderColor = if (suggestion.isCloseMatch) Color(0xFFFF9800) else Color(0xFFFFC107)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(suggestion.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(suggestion.sourceLabel, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        calorieDeltaText(suggestion.calorieDelta),
                        color = if (suggestion.calorieDelta <= 0) Color(0xFF8BC34A) else Color(0xFFFFC107),
                        fontSize = 11.sp
                    )
                }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("${suggestion.totalCalories} / ${suggestion.targetCalories} kcal", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFF9800), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            suggestion.ingredients.forEach { ingredient ->
                IngredientAmountRow(
                    ingredient = ingredient,
                    onAmountChange = { onAmountChange(ingredient, it) },
                    onKcalChange = { onKcalChange(ingredient.food, it) },
                    onAddToShoppingList = { onAddToShoppingList(ingredient.food) }
                )
            }
        }
    }
}

@Composable
private fun IngredientAmountRow(
    ingredient: MealIngredient,
    onAmountChange: (Float) -> Unit,
    onKcalChange: (Int) -> Unit,
    onAddToShoppingList: () -> Unit,
    actionIcon: ImageVector = Icons.Default.AddShoppingCart,
    actionTint: Color = Color(0xFF4CAF50),
    actionDescription: String = "Add",
    alwaysShowAction: Boolean = false
) {
    val food = ingredient.food
    val maxAmount = maxSliderAmount(food, ingredient.amount)
    var kcalText by remember(food.id, food.kcalPerUnit) { mutableStateOf(food.kcalPerUnit.toString()) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    food.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (ingredient.locked) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${ingredient.amount.formatAmount()} ${food.unitLabel} • ${ingredient.calories} kcal",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            KcalField(
                value = kcalText,
                onValueChange = { next ->
                    kcalText = next
                    next.toIntOrNull()?.takeIf { it > 0 }?.let(onKcalChange)
                },
                modifier = Modifier.widthIn(min = 72.dp, max = 86.dp)
            )
            if (alwaysShowAction || food.source != FoodSources.HOME) {
                IconButton(onClick = onAddToShoppingList, modifier = Modifier.size(34.dp)) {
                    Icon(actionIcon, contentDescription = actionDescription, tint = actionTint, modifier = Modifier.size(20.dp))
                }
            }
        }
        Slider(
            value = ingredient.amount.coerceIn(0f, maxAmount),
            onValueChange = onAmountChange,
            valueRange = 0f..maxAmount,
            steps = sliderSteps(food.stepSize, maxAmount)
        )
    }
}

@Composable
private fun CanEatNowCard(
    foods: List<FoodEntity>,
    onAddToChosenMeal: (FoodEntity) -> Unit,
    onKcalChange: (FoodEntity, Int) -> Unit,
    onShoppingListChange: (FoodEntity, Boolean) -> Unit
) {
    SleekCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBadge(Icons.Default.Restaurant, Color(0xFF03A9F4))
            Text("Can Eat Now", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(10.dp))
        if (foods.isEmpty()) {
            Text("No enabled foods", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                foods.take(12).forEach { food ->
                    FoodAvailabilityRow(
                        food = food,
                        onAddToChosenMeal = { onAddToChosenMeal(food) },
                        onKcalChange = { onKcalChange(food, it) },
                        onShoppingListChange = onShoppingListChange
                    )
                }
            }
        }
    }
}

@Composable
private fun FoodAvailabilityRow(
    food: FoodEntity,
    onAddToChosenMeal: () -> Unit,
    onKcalChange: (Int) -> Unit,
    onShoppingListChange: (FoodEntity, Boolean) -> Unit
) {
    var kcalText by remember(food.id, food.kcalPerUnit) { mutableStateOf(food.kcalPerUnit.toString()) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceIcon(food.source)
            Column(modifier = Modifier.weight(1f)) {
                Text(food.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(foodAvailabilityText(food), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
        KcalField(
            value = kcalText,
            onValueChange = { next ->
                kcalText = next
                next.toIntOrNull()?.takeIf { it > 0 }?.let(onKcalChange)
            },
            modifier = Modifier.widthIn(min = 72.dp, max = 86.dp)
        )
        IconButton(onClick = onAddToChosenMeal, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Add, contentDescription = "Add to chosen meal", tint = Color(0xFFFFC107), modifier = Modifier.size(20.dp))
        }
        IconButton(
            onClick = { onShoppingListChange(food, !food.onShoppingList) },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (food.onShoppingList) Icons.Default.RemoveShoppingCart else Icons.Default.AddShoppingCart,
                contentDescription = if (food.onShoppingList) "Remove from shopping list" else "Add to shopping list",
                tint = if (food.onShoppingList) Color(0xFF4CAF50) else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun KcalField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { char -> char.isDigit() }.take(4)) },
        label = { Text("kcal") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

@Composable
private fun SourceChip(label: String, icon: ImageVector, selected: Boolean, onSelectedChange: (Boolean) -> Unit) {
    FilterChip(
        selected = selected,
        onClick = { onSelectedChange(!selected) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        label = { Text(label) }
    )
}

@Composable
private fun IconBadge(icon: ImageVector, color: Color) {
    androidx.compose.material3.Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.12f)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(8.dp).size(24.dp))
    }
}

@Composable
private fun SourceIcon(source: String) {
    val icon = when (source) {
        FoodSources.HOME -> Icons.Default.Home
        FoodSources.STORE -> Icons.Default.Store
        else -> Icons.Default.Fastfood
    }
    Icon(icon, contentDescription = null, tint = sourceColor(source), modifier = Modifier.size(20.dp))
}

private fun sourceColor(source: String): Color = when (source) {
    FoodSources.HOME -> Color(0xFF4CAF50)
    FoodSources.STORE -> Color(0xFF03A9F4)
    else -> Color(0xFFFF9800)
}

private fun foodAvailabilityText(food: FoodEntity): String {
    val amount = if (food.source == FoodSources.HOME) {
        food.availableAmount?.let { "${it.formatAmount()} ${food.unitLabel}" } ?: "Available"
    } else {
        food.source
    }
    return "$amount • ${food.kcalPerUnit} kcal/${food.unitLabel}"
}

private fun calorieDeltaText(delta: Int): String = when {
    abs(delta) <= 25 -> "match"
    delta < 0 -> "${abs(delta)} kcal left"
    else -> "$delta kcal over"
}

private fun maxSliderAmount(food: FoodEntity, currentAmount: Float): Float {
    val pantryMax = if (food.source == FoodSources.HOME) food.availableAmount else null
    return (pantryMax ?: maxOf(food.defaultAmount * 5f, currentAmount * 2f, 1f)).coerceAtLeast(food.stepSize)
}

private fun sliderSteps(stepSize: Float, maxAmount: Float): Int {
    if (stepSize <= 0f) return 0
    return (ceil(maxAmount / stepSize).roundToInt() - 1).coerceIn(0, 50)
}

private fun Float.formatAmount(): String {
    return if (abs(this - roundToInt()) < 0.05f) {
        roundToInt().toString()
    } else {
        String.format(Locale.US, "%.1f", this)
    }
}
