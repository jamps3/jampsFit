package com.labbaslabs.jampsfit.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.labbaslabs.jampsfit.LocalMainViewModel
import com.labbaslabs.jampsfit.WatchState
import com.labbaslabs.jampsfit.database.CandyEntity
import com.labbaslabs.jampsfit.database.FoodEntity
import com.labbaslabs.jampsfit.database.FoodRoles
import com.labbaslabs.jampsfit.database.FoodSources
import com.labbaslabs.jampsfit.food.CalorieTargetMode
import com.labbaslabs.jampsfit.food.MealIngredient
import com.labbaslabs.jampsfit.food.MealSuggestion
import com.labbaslabs.jampsfit.food.availableFoods
import com.labbaslabs.jampsfit.food.calculateEatCalorieTarget
import com.labbaslabs.jampsfit.food.generateMealSuggestions
import com.labbaslabs.jampsfit.food.recalculateMealWithLockedIngredient
import com.labbaslabs.jampsfit.ui.components.SleekCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
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
    var confirmCalorieReset by remember { mutableStateOf(false) }
    var addingFood by remember { mutableStateOf(false) }
    var burgerCenterBuns by rememberSaveable { mutableStateOf(0) }
    var candyName by rememberSaveable { mutableStateOf("") }
    var candySize by rememberSaveable { mutableStateOf("") }
    var candyHours by rememberSaveable { mutableStateOf("") }

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
    val selectedFestivalId = state.selectedFestivalId ?: state.festivals.maxByOrNull { it.createdAt }?.id
    val festivalCandies = remember(state.candies, selectedFestivalId) {
        state.candies.filter { candy -> selectedFestivalId == null || candy.festivalId == selectedFestivalId }
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
                AssistChip(
                    onClick = { confirmCalorieReset = true },
                    label = { Text("Reset current") },
                    enabled = (state.calories ?: 0) > state.calorieBaseline
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

        CandiesCard(
            name = candyName,
            size = candySize,
            hours = candyHours,
            candies = festivalCandies,
            onNameChange = { candyName = it.take(32) },
            onSizeChange = { candySize = it.filter(Char::isDigit).take(4) },
            onHoursChange = { candyHours = it.filter(Char::isDigit).take(2) },
            onAdd = {
                val trimmedName = candyName.trim()
                val sizeValue = candySize.toIntOrNull()
                val hourValue = candyHours.toIntOrNull()
                if (trimmedName.isNotEmpty() && sizeValue != null && hourValue != null) {
                    viewModel.addCandy(trimmedName, sizeValue, hourValue)
                    candyName = ""
                    candySize = ""
                    candyHours = ""
                }
            },
            onDelete = { viewModel.deleteCandy(it.id) }
        )

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
            onAddFood = { addingFood = true },
            onAddToChosenMeal = { food ->
                chosenMeal = chosenMeal + (food.id to food.defaultAmount)
            },
            onKcalChange = { food, kcal -> viewModel.saveFood(food.copy(kcalPerUnit = kcal)) },
            onAmountChange = { food, amount -> viewModel.saveFood(food.copy(defaultAmount = amount)) },
            onShoppingListChange = { food, checked -> viewModel.setFoodOnShoppingList(food.id, checked) }
        )

        ShoppingListCard(
            foods = shoppingFoods,
            checkedIds = state.shoppingListCheckedIds,
            onCheckedChange = { food, checked -> viewModel.setShoppingListChecked(food.id, checked) },
            onQuantityChange = { food, amount -> viewModel.saveFood(food.copy(defaultAmount = amount)) },
            onRemove = { viewModel.setFoodOnShoppingList(it.id, false) }
        )

        CurrentBurgerCard(
            targetCalories = targetCalories,
            centerBuns = burgerCenterBuns,
            onCenterBunsChange = { burgerCenterBuns = it.coerceIn(0, 12) }
        )
        CurrentNuggetsCard(targetCalories = targetCalories)
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

    if (confirmCalorieReset) {
        AlertDialog(
            onDismissRequest = { confirmCalorieReset = false },
            title = { Text("Reset current calories?") },
            text = { Text("This keeps watch history intact and starts app calorie counting from the current watch value.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetCalorieBaseline()
                        confirmCalorieReset = false
                    }
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCalorieReset = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (addingFood) {
        QuickAddFoodDialog(
            onDismiss = { addingFood = false },
            onSave = { food ->
                viewModel.saveFood(food)
                addingFood = false
            }
        )
    }
}

@Composable
private fun CandiesCard(
    name: String,
    size: String,
    hours: String,
    candies: List<CandyEntity>,
    onNameChange: (String) -> Unit,
    onSizeChange: (String) -> Unit,
    onHoursChange: (String) -> Unit,
    onAdd: () -> Unit,
    onDelete: (CandyEntity) -> Unit
) {
    val canAdd = name.trim().isNotEmpty() && size.length in 1..4 && hours.length in 1..2
    SleekCard(borderColor = Color(0xFFE91E63)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBadge(Icons.Default.Restaurant, Color(0xFFE91E63))
                Column {
                    Text("Candies", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Attached to the current festival", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            IconButton(onClick = onAdd, enabled = canAdd, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Add candy", tint = Color(0xFFE91E63))
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Candy name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = size,
                onValueChange = onSizeChange,
                label = { Text("Size") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = hours,
                onValueChange = onHoursChange,
                label = { Text("Hours") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (candies.isEmpty()) {
            Text("No candies recorded yet", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                candies.forEach { candy ->
                    CandyEntryRow(candy = candy, onDelete = { onDelete(candy) })
                }
            }
        }
    }
}

@Composable
private fun CandyEntryRow(candy: CandyEntity, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(candy.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${formatCandyTime(candy.startTime)} - ${formatCandyTime(candy.endTime)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
        Text(
            "Size ${candy.size.toString().padStart(4, '0')}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE91E63)
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.Remove, contentDescription = "Delete candy", tint = Color.Gray)
        }
    }
}

private fun formatCandyTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
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
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                modifier = Modifier.height(210.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (ingredients.isEmpty()) {
                    Text("Add items from Can Eat Now", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                } else {
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
                }
            }
            Button(
                onClick = { onApply(totalCalories) },
                modifier = Modifier.fillMaxWidth(),
                enabled = ingredients.isNotEmpty()
            ) {
                Text("Apply Chosen Meal")
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
private fun CurrentBurgerCard(
    targetCalories: Int,
    centerBuns: Int,
    onCenterBunsChange: (Int) -> Unit
) {
    val burger = remember(targetCalories, centerBuns) { BurgerPlan.fromCalories(targetCalories, centerBuns) }
    SleekCard(borderColor = Color(0xFFFF7043)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBadge(Icons.Default.Fastfood, Color(0xFFFF7043))
                Column {
                    Text("Current Burger", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${burger.displayCalories} kcal burger", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = { onCenterBunsChange(centerBuns - 1) },
                    enabled = centerBuns > 0,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Remove center bun", tint = Color.Gray)
                }
                AssistChip(
                    onClick = {},
                    label = { Text("${burger.centerBuns} center buns", fontSize = 11.sp) }
                )
                IconButton(
                    onClick = { onCenterBunsChange(centerBuns + 1) },
                    enabled = centerBuns < 12,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add center bun", tint = Color(0xFFFF7043))
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        ) {
            drawBurger(burger)
        }
        Spacer(modifier = Modifier.height(8.dp))
        BurgerIngredientList(burger)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Top and bottom buns stay constant; patties, cheese, sauce, and salad scale toward 15,000 kcal.",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
    }
}

@Composable
private fun BurgerIngredientList(burger: BurgerPlan) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        burger.ingredients.forEach { ingredient ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    ingredient.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.LightGray
                )
                Text(
                    "${ingredient.calories} kcal",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFB74D)
                )
            }
        }
    }
}

private data class BurgerIngredient(
    val name: String,
    val calories: Int
)

private data class BurgerPlan(
    val displayCalories: Int,
    val patties: Int,
    val cheeseSlices: Int,
    val sauceLayers: Int,
    val saladLayers: Int,
    val centerBuns: Int
) {
    val ingredients: List<BurgerIngredient>
        get() = listOf(
            BurgerIngredient("Top + bottom buns", 220),
            BurgerIngredient("$centerBuns center buns", centerBuns * 110),
            BurgerIngredient("$patties meat patties", patties * 250),
            BurgerIngredient("$cheeseSlices cheese slices", cheeseSlices * 70),
            BurgerIngredient("$sauceLayers sauce layers", sauceLayers * 40),
            BurgerIngredient("$saladLayers salad/tomato layers", saladLayers * 15)
        )

    companion object {
        private const val MAX_BURGER_KCAL = 15_000

        fun fromCalories(calories: Int, centerBuns: Int): BurgerPlan {
            val displayCalories = calories.coerceIn(0, MAX_BURGER_KCAL)
            val ratio = displayCalories / MAX_BURGER_KCAL.toFloat()
            val patties = max(1, (1 + ratio * 11f).roundToInt())
            return BurgerPlan(
                displayCalories = displayCalories,
                patties = patties,
                cheeseSlices = max(1, (patties * 0.75f).roundToInt()),
                sauceLayers = max(1, (patties * 0.45f).roundToInt()),
                saladLayers = max(1, (patties * 0.35f).roundToInt()),
                centerBuns = centerBuns.coerceIn(0, 12)
            )
        }
    }
}

private fun DrawScope.drawBurger(plan: BurgerPlan) {
    val centerX = size.width / 2f
    val maxWidth = size.width * 0.82f
    val minWidth = size.width * 0.48f
    val scale = (plan.displayCalories / 15_000f).coerceIn(0f, 1f)
    val burgerWidth = minWidth + (maxWidth - minWidth) * scale
    val left = centerX - burgerWidth / 2f
    val layerGap = 2.dp.toPx()
    val topBunHeight = 34.dp.toPx()
    val bottomBunHeight = 24.dp.toPx()
    val pattyHeight = 11.dp.toPx()
    val cheeseHeight = 5.dp.toPx()
    val sauceHeight = 4.dp.toPx()
    val saladHeight = 5.dp.toPx()
    val centerBunHeight = 12.dp.toPx()
    val ingredientHeight = plan.patties * pattyHeight +
        plan.cheeseSlices * cheeseHeight +
        plan.sauceLayers * sauceHeight +
        plan.saladLayers * saladHeight +
        plan.centerBuns * centerBunHeight +
        (plan.patties + plan.cheeseSlices + plan.sauceLayers + plan.saladLayers + plan.centerBuns) * layerGap
    val burgerHeight = topBunHeight + ingredientHeight + bottomBunHeight
    var y = ((size.height - burgerHeight) / 2f).coerceAtLeast(8.dp.toPx())

    drawRoundRect(
        color = Color(0xFFE7A84F),
        topLeft = Offset(left, y),
        size = Size(burgerWidth, topBunHeight),
        cornerRadius = CornerRadius(26.dp.toPx(), 26.dp.toPx())
    )
    drawRoundRect(
        color = Color(0xFFF8D37B),
        topLeft = Offset(left + burgerWidth * 0.08f, y + topBunHeight * 0.46f),
        size = Size(burgerWidth * 0.84f, topBunHeight * 0.18f),
        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
    )
    y += topBunHeight + layerGap

    repeat(plan.patties) { index ->
        if (index < plan.saladLayers) {
            drawIngredientLayer(left, y, burgerWidth, saladHeight, Color(0xFF6CBF3F), 0.92f, 10.dp.toPx())
            y += saladHeight + layerGap
        }
        if (index < plan.sauceLayers) {
            drawIngredientLayer(left, y, burgerWidth, sauceHeight, Color(0xFFD84315), 0.86f, 8.dp.toPx())
            y += sauceHeight + layerGap
        }
        if (index < plan.cheeseSlices) {
            drawIngredientLayer(left, y, burgerWidth, cheeseHeight, Color(0xFFFFD54F), 0.96f, 4.dp.toPx())
            y += cheeseHeight + layerGap
        }
        drawIngredientLayer(left, y, burgerWidth, pattyHeight, Color(0xFF6D3B22), 0.9f, 12.dp.toPx())
        y += pattyHeight + layerGap
        if (index < plan.centerBuns) {
            drawIngredientLayer(left, y, burgerWidth, centerBunHeight, Color(0xFFE0A24B), 0.88f, 10.dp.toPx())
            y += centerBunHeight + layerGap
        }
    }

    drawRoundRect(
        color = Color(0xFFD9943D),
        topLeft = Offset(left + burgerWidth * 0.04f, y),
        size = Size(burgerWidth * 0.92f, bottomBunHeight),
        cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
    )
}

private fun DrawScope.drawIngredientLayer(
    burgerLeft: Float,
    y: Float,
    burgerWidth: Float,
    height: Float,
    color: Color,
    widthFactor: Float,
    radius: Float
) {
    val width = burgerWidth * widthFactor
    val left = burgerLeft + (burgerWidth - width) / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(left, y),
        size = Size(width, height),
        cornerRadius = CornerRadius(radius, radius)
    )
}

@Composable
private fun CurrentNuggetsCard(targetCalories: Int) {
    val nuggets = remember(targetCalories) { NuggetsPlan.fromCalories(targetCalories) }
    SleekCard(borderColor = Color(0xFFFFB300)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBadge(Icons.Default.Fastfood, Color(0xFFFFB300))
                Column {
                    Text("Current Nuggets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${nuggets.displayCalories} kcal nuggets", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            AssistChip(
                onClick = {},
                label = { Text("${nuggets.totalCount} nuggets", fontSize = 11.sp) }
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
        ) {
            drawNuggets(nuggets)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Uses regular chicken nuggets at about ${NuggetsPlan.KCAL_PER_NUGGET} kcal each; the tray scales toward 15,000 kcal.",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
    }
}

private data class NuggetsPlan(
    val displayCalories: Int,
    val totalCount: Int,
    val visibleCount: Int
) {
    companion object {
        const val KCAL_PER_NUGGET = 45
        private const val MAX_NUGGET_KCAL = 15_000
        private const val MAX_VISIBLE_NUGGETS = 72

        fun fromCalories(calories: Int): NuggetsPlan {
            val displayCalories = calories.coerceIn(0, MAX_NUGGET_KCAL)
            val totalCount = max(1, ceil(displayCalories / KCAL_PER_NUGGET.toFloat()).roundToInt())
            val visibleCount = totalCount.coerceAtMost(MAX_VISIBLE_NUGGETS)
            return NuggetsPlan(
                displayCalories = displayCalories,
                totalCount = totalCount,
                visibleCount = visibleCount
            )
        }
    }
}

private fun DrawScope.drawNuggets(plan: NuggetsPlan) {
    val trayPadding = 16.dp.toPx()
    val trayLeft = trayPadding
    val trayTop = 10.dp.toPx()
    val trayWidth = size.width - trayPadding * 2f
    val trayHeight = size.height - 20.dp.toPx()
    drawRoundRect(
        color = Color(0xFF3F2A1C).copy(alpha = 0.18f),
        topLeft = Offset(trayLeft, trayTop),
        size = Size(trayWidth, trayHeight),
        cornerRadius = CornerRadius(22.dp.toPx(), 22.dp.toPx())
    )

    val columns = 8
    val rows = ceil(plan.visibleCount / columns.toFloat()).roundToInt().coerceAtLeast(1)
    val cellWidth = trayWidth / columns
    val cellHeight = trayHeight / rows
    val nuggetWidth = (cellWidth * 0.68f).coerceIn(18.dp.toPx(), 36.dp.toPx())
    val nuggetHeight = (cellHeight * 0.54f).coerceIn(13.dp.toPx(), 28.dp.toPx())

    repeat(plan.visibleCount) { index ->
        val row = index / columns
        val column = index % columns
        val centerX = trayLeft + column * cellWidth + cellWidth / 2f
        val centerY = trayTop + row * cellHeight + cellHeight / 2f
        val wobbleX = ((index % 3) - 1) * 2.dp.toPx()
        val wobbleY = if (index % 2 == 0) 1.5.dp.toPx() else -1.5.dp.toPx()
        drawNugget(
            center = Offset(centerX + wobbleX, centerY + wobbleY),
            width = nuggetWidth,
            height = nuggetHeight,
            seed = index
        )
    }
}

private fun DrawScope.drawNugget(center: Offset, width: Float, height: Float, seed: Int) {
    val topLeft = Offset(center.x - width / 2f, center.y - height / 2f)
    drawRoundRect(
        color = Color(0xFFD99A32),
        topLeft = topLeft,
        size = Size(width, height),
        cornerRadius = CornerRadius(width * 0.42f, height * 0.5f)
    )
    drawRoundRect(
        color = Color(0xFFF3C35A),
        topLeft = Offset(topLeft.x + width * 0.15f, topLeft.y + height * 0.18f),
        size = Size(width * 0.38f, height * 0.2f),
        cornerRadius = CornerRadius(width * 0.16f, height * 0.1f)
    )
    if (seed % 4 == 0) {
        drawCircle(
            color = Color(0xFFB8741E).copy(alpha = 0.55f),
            radius = height * 0.1f,
            center = Offset(center.x + width * 0.2f, center.y + height * 0.12f)
        )
    }
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
    onAddFood: () -> Unit,
    onAddToChosenMeal: (FoodEntity) -> Unit,
    onKcalChange: (FoodEntity, Int) -> Unit,
    onAmountChange: (FoodEntity, Float) -> Unit,
    onShoppingListChange: (FoodEntity, Boolean) -> Unit
) {
    SleekCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBadge(Icons.Default.Restaurant, Color(0xFF03A9F4))
                Text("Can Eat Now", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onAddFood, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Add food", tint = Color(0xFF03A9F4))
            }
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
                        onAmountChange = { onAmountChange(food, it) },
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
    onAmountChange: (Float) -> Unit,
    onShoppingListChange: (FoodEntity, Boolean) -> Unit
) {
    var kcalText by remember(food.id, food.kcalPerUnit) { mutableStateOf(food.kcalPerUnit.toString()) }
    var gramsText by remember(food.id, food.defaultAmount, food.unitLabel) {
        mutableStateOf(food.defaultAmount.toDisplayGrams(food.unitLabel).toString())
    }
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
        if (food.role == FoodRoles.VEGETABLE) {
            GramField(
                value = gramsText,
                onValueChange = { next ->
                    gramsText = next
                    next.toIntOrNull()?.takeIf { it > 0 }?.let { grams ->
                        onAmountChange(grams.toDefaultAmount(food.unitLabel))
                    }
                },
                modifier = Modifier.widthIn(min = 74.dp, max = 88.dp)
            )
        }
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
private fun GramField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { char -> char.isDigit() }.take(4)) },
        label = { Text("g") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

@Composable
private fun QuickAddFoodDialog(
    onDismiss: () -> Unit,
    onSave: (FoodEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var source by remember { mutableStateOf(FoodSources.STORE) }
    var role by remember { mutableStateOf(FoodRoles.VEGETABLE) }
    var unitLabel by remember { mutableStateOf("100 g") }
    var kcalText by remember { mutableStateOf("30") }
    var amountText by remember { mutableStateOf("200") }
    var stepText by remember { mutableStateOf("50") }
    var onShoppingList by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Food") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SourceChip(FoodSources.HOME, Icons.Default.Home, source == FoodSources.HOME) { source = FoodSources.HOME }
                    SourceChip(FoodSources.STORE, Icons.Default.Store, source == FoodSources.STORE) { source = FoodSources.STORE }
                    SourceChip(FoodSources.FAST_FOOD, Icons.Default.Fastfood, source == FoodSources.FAST_FOOD) { source = FoodSources.FAST_FOOD }
                }
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FoodRoles.all.forEach { option ->
                        FilterChip(selected = role == option, onClick = { role = option }, label = { Text(option, fontSize = 12.sp) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = unitLabel,
                        onValueChange = { unitLabel = it },
                        label = { Text("Unit") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    KcalField(
                        value = kcalText,
                        onValueChange = { kcalText = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DecimalField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = if (unitLabel.isGramUnit()) "Default g" else "Default",
                        modifier = Modifier.weight(1f)
                    )
                    DecimalField(
                        value = stepText,
                        onValueChange = { stepText = it },
                        label = if (unitLabel.isGramUnit()) "Step g" else "Step",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Shopping list", style = MaterialTheme.typography.bodyMedium)
                    Checkbox(checked = onShoppingList, onCheckedChange = { onShoppingList = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val unit = unitLabel.ifBlank { if (role == FoodRoles.VEGETABLE) "100 g" else "portion" }
                    val defaultAmount = (amountText.toFloatOrNull() ?: 1f).fromDisplayAmount(unit)
                    onSave(
                        FoodEntity(
                            name = name,
                            source = source,
                            role = role,
                            unitLabel = unit,
                            kcalPerUnit = kcalText.toIntOrNull() ?: 100,
                            defaultAmount = defaultAmount,
                            stepSize = (stepText.toFloatOrNull() ?: 1f).fromDisplayAmount(unit),
                            availableAmount = if (source == FoodSources.HOME) defaultAmount else null,
                            isCustom = true,
                            onShoppingList = onShoppingList
                        )
                    )
                },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DecimalField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> onValueChange(next.filter { it.isDigit() || it == '.' }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
    val amount = if (food.unitLabel.isGramUnit()) {
        "${food.defaultAmount.toDisplayGrams(food.unitLabel)} g"
    } else if (food.source == FoodSources.HOME) {
        food.availableAmount?.let { "${it.formatAmount()} ${food.unitLabel}" } ?: "Available"
    } else {
        food.source
    }
    return "$amount • ${food.kcalPerUnit} kcal/${food.unitLabel}"
}

private fun String.isGramUnit(): Boolean = trim().equals("100 g", ignoreCase = true)

private fun Float.fromDisplayAmount(unitLabel: String): Float {
    return if (unitLabel.isGramUnit()) this / 100f else this
}

private fun Int.toDefaultAmount(unitLabel: String): Float {
    return if (unitLabel.isGramUnit()) this / 100f else toFloat()
}

private fun Float.toDisplayGrams(unitLabel: String): Int {
    return if (unitLabel.isGramUnit()) (this * 100f).roundToInt() else roundToInt()
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
