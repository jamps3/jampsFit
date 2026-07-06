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
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
    var showHome by rememberSaveable { mutableStateOf(true) }
    var showStore by rememberSaveable { mutableStateOf(true) }
    var showFastFood by rememberSaveable { mutableStateOf(false) }
    var lockedSuggestionId by rememberSaveable { mutableStateOf<String?>(null) }
    var lockedFoodId by rememberSaveable { mutableLongStateOf(0L) }
    var lockedAmount by rememberSaveable { mutableFloatStateOf(0f) }

    val targetMode = CalorieTargetMode.valueOf(targetModeName)
    val targetCalories = calculateEatCalorieTarget(state, targetMode)
    val selectedSources = buildSet {
        if (showHome) add(FoodSources.HOME)
        if (showStore) add(FoodSources.STORE)
        if (showFastFood) add(FoodSources.FAST_FOOD)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Eat", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SleekCard(borderColor = Color(0xFFFF9800)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge(Icons.Default.LocalFireDepartment, Color(0xFFFF9800))
                    Column {
                        Text("$targetCalories kcal", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                        Text(if (targetMode == CalorieTargetMode.TotalSoFar) "Total so far" else "Active burned", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Total", style = MaterialTheme.typography.labelMedium, color = if (targetMode == CalorieTargetMode.TotalSoFar) Color.White else Color.Gray)
                    Switch(
                        checked = targetMode == CalorieTargetMode.ActiveBurned,
                        onCheckedChange = { active ->
                            targetModeName = if (active) CalorieTargetMode.ActiveBurned.name else CalorieTargetMode.TotalSoFar.name
                            lockedSuggestionId = null
                        }
                    )
                    Text("Active", style = MaterialTheme.typography.labelMedium, color = if (targetMode == CalorieTargetMode.ActiveBurned) Color.White else Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SourceChip(FoodSources.HOME, Icons.Default.Home, showHome) { showHome = it; lockedSuggestionId = null }
                SourceChip(FoodSources.STORE, Icons.Default.Store, showStore) { showStore = it; lockedSuggestionId = null }
                SourceChip(FoodSources.FAST_FOOD, Icons.Default.Fastfood, showFastFood) { showFastFood = it; lockedSuggestionId = null }
            }
        }

        ShoppingListCard(
            foods = shoppingFoods,
            onBought = { viewModel.markFoodBought(it.id) },
            onRemove = { viewModel.setFoodOnShoppingList(it.id, false) }
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
            onKcalChange = { food, kcal -> viewModel.saveFood(food.copy(kcalPerUnit = kcal)) },
            onShoppingListChange = { food, checked -> viewModel.setFoodOnShoppingList(food.id, checked) }
        )
    }
}

@Composable
private fun ShoppingListCard(
    foods: List<FoodEntity>,
    onBought: (FoodEntity) -> Unit,
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Checkbox(checked = false, onCheckedChange = { if (it) onBought(food) })
                            Column(modifier = Modifier.weight(1f)) {
                                Text(food.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${food.defaultAmount.formatAmount()} ${food.unitLabel} • ${food.source}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
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
    onAddToShoppingList: () -> Unit
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
            if (food.source != FoodSources.HOME) {
                IconButton(onClick = onAddToShoppingList, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.AddShoppingCart, contentDescription = "Add", tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
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
        Checkbox(
            checked = food.onShoppingList,
            onCheckedChange = { onShoppingListChange(food, it) }
        )
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
