package com.labbaslabs.jampsfit.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labbaslabs.jampsfit.LocalMainViewModel
import com.labbaslabs.jampsfit.WatchState
import com.labbaslabs.jampsfit.database.FoodEntity
import com.labbaslabs.jampsfit.database.FoodRoles
import com.labbaslabs.jampsfit.database.FoodSources
import com.labbaslabs.jampsfit.ui.components.ConfirmActionDialog
import com.labbaslabs.jampsfit.ui.components.SleekCard
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun EatSettingsLauncher(state: WatchState) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    SleekCard(borderColor = Color(0xFFFF9800)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Restaurant, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(24.dp))
                Column {
                    Text("Eat Settings", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("${state.foods.size} foods", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            Button(onClick = { expanded = !expanded }, shape = RoundedCornerShape(8.dp)) {
                Text(if (expanded) "Close" else "Open", fontSize = 12.sp)
            }
        }
    }

    if (expanded) {
        EatSettingsPanel(state)
    }
}

@Composable
private fun EatSettingsPanel(state: WatchState) {
    val viewModel = LocalMainViewModel.current
    var sourceFilter by rememberSaveable { mutableStateOf(FoodSources.HOME) }
    var editingFood by remember { mutableStateOf<FoodEntity?>(null) }
    val visibleFoods = state.foods.filter { it.source == sourceFilter }

    SleekCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Foods", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedButton(
                onClick = { editingFood = newFood(sourceFilter) },
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(6.dp))
                Text("Add", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FoodSourceFilterChip(FoodSources.HOME, Icons.Default.Home, sourceFilter) { sourceFilter = it }
            FoodSourceFilterChip(FoodSources.STORE, Icons.Default.Store, sourceFilter) { sourceFilter = it }
            FoodSourceFilterChip(FoodSources.FAST_FOOD, Icons.Default.Fastfood, sourceFilter) { sourceFilter = it }
        }

        Spacer(modifier = Modifier.height(12.dp))
        if (visibleFoods.isEmpty()) {
            Text("No foods", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                visibleFoods.forEach { food ->
                    FoodSettingsRow(
                        food = food,
                        onEnabledChange = { viewModel.setFoodEnabled(food.id, it) },
                        onShoppingListChange = { viewModel.setFoodOnShoppingList(food.id, it) },
                        onAmountChange = { viewModel.setFoodAvailableAmount(food.id, it) },
                        onEdit = { editingFood = food },
                        doubleConfirm = state.doubleConfirmationsEnabled,
                        onDelete = { viewModel.deleteFood(food.id) }
                    )
                }
            }
        }
    }

    editingFood?.let { food ->
        FoodEditorDialog(
            food = food,
            onDismiss = { editingFood = null },
            onSave = {
                viewModel.saveFood(it)
                editingFood = null
            }
        )
    }
}

@Composable
private fun FoodSettingsRow(
    food: FoodEntity,
    onEnabledChange: (Boolean) -> Unit,
    onShoppingListChange: (Boolean) -> Unit,
    onAmountChange: (Float?) -> Unit,
    onEdit: () -> Unit,
    doubleConfirm: Boolean,
    onDelete: () -> Unit
) {
    var confirmDelete by remember(food.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(food.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(food.detailsText(), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            Switch(checked = food.enabled, onCheckedChange = onEnabledChange)
            Checkbox(checked = food.onShoppingList, onCheckedChange = onShoppingListChange)
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            if (food.isCustom) {
                IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
        }

        if (food.source == FoodSources.HOME) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Pantry ${food.availableAmount?.formatAmount() ?: "?"} ${food.unitLabel}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val current = food.availableAmount ?: 0f
                            onAmountChange((current - food.stepSize).coerceAtLeast(0f))
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("-", fontSize = 16.sp) }
                    OutlinedButton(
                        onClick = {
                            val current = food.availableAmount ?: 0f
                            onAmountChange(current + food.stepSize)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("+", fontSize = 16.sp) }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                Text(if (food.onShoppingList) "On shopping list" else "Not listed", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
    if (confirmDelete) {
        ConfirmActionDialog(
            title = "Delete food?",
            text = "This removes ${food.name} from food options.",
            confirmLabel = "Delete",
            doubleConfirm = doubleConfirm,
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun FoodEditorDialog(
    food: FoodEntity,
    onDismiss: () -> Unit,
    onSave: (FoodEntity) -> Unit
) {
    var name by remember(food.id) { mutableStateOf(food.name) }
    var source by remember(food.id) { mutableStateOf(food.source) }
    var role by remember(food.id) { mutableStateOf(food.role) }
    var unitLabel by remember(food.id) { mutableStateOf(food.unitLabel) }
    var kcalText by remember(food.id) { mutableStateOf(food.kcalPerUnit.toString()) }
    var defaultAmountText by remember(food.id) { mutableStateOf(food.defaultAmount.formatAmount()) }
    var stepSizeText by remember(food.id) { mutableStateOf(food.stepSize.formatAmount()) }
    var availableText by remember(food.id) { mutableStateOf(food.availableAmount?.formatAmount() ?: "") }
    var enabled by remember(food.id) { mutableStateOf(food.enabled) }
    var onShoppingList by remember(food.id) { mutableStateOf(food.onShoppingList) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (food.id == 0L) "Add Food" else "Edit Food") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)

                Text("Source", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FoodSourceFilterChip(FoodSources.HOME, Icons.Default.Home, source) { source = it }
                    FoodSourceFilterChip(FoodSources.STORE, Icons.Default.Store, source) { source = it }
                    FoodSourceFilterChip(FoodSources.FAST_FOOD, Icons.Default.Fastfood, source) { source = it }
                }

                Text("Role", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FoodRoles.all.forEach { roleOption ->
                        FilterChip(
                            selected = role == roleOption,
                            onClick = { role = roleOption },
                            label = { Text(roleOption, fontSize = 12.sp) }
                        )
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
                    OutlinedTextField(
                        value = kcalText,
                        onValueChange = { kcalText = it.filter { char -> char.isDigit() } },
                        label = { Text("kcal") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DecimalField(defaultAmountText, { defaultAmountText = it }, "Default", Modifier.weight(1f))
                    DecimalField(stepSizeText, { stepSizeText = it }, "Step", Modifier.weight(1f))
                }

                if (source == FoodSources.HOME) {
                    DecimalField(availableText, { availableText = it }, "Pantry amount", Modifier.fillMaxWidth())
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Enabled", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
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
                    onSave(
                        food.copy(
                            name = name,
                            source = source,
                            role = role,
                            unitLabel = unitLabel,
                            kcalPerUnit = kcalText.toIntOrNull() ?: food.kcalPerUnit,
                            defaultAmount = defaultAmountText.toFloatOrNull() ?: food.defaultAmount,
                            stepSize = stepSizeText.toFloatOrNull() ?: food.stepSize,
                            enabled = enabled,
                            availableAmount = if (source == FoodSources.HOME) availableText.toFloatOrNull() else null,
                            isCustom = food.isCustom || food.id == 0L,
                            onShoppingList = onShoppingList
                        )
                    )
                }
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
private fun FoodSourceFilterChip(
    source: String,
    icon: ImageVector,
    selectedSource: String,
    onSelected: (String) -> Unit
) {
    FilterChip(
        selected = selectedSource == source,
        onClick = { onSelected(source) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        label = { Text(source, fontSize = 12.sp) }
    )
}

private fun newFood(source: String): FoodEntity {
    return FoodEntity(
        name = "",
        source = source,
        role = if (source == FoodSources.FAST_FOOD) FoodRoles.READY_MEAL else FoodRoles.CARB,
        unitLabel = "portion",
        kcalPerUnit = 100,
        defaultAmount = 1f,
        stepSize = 0.5f,
        enabled = true,
        availableAmount = if (source == FoodSources.HOME) 1f else null,
        isCustom = true
    )
}

private fun FoodEntity.detailsText(): String {
    val amount = if (source == FoodSources.HOME) {
        " • pantry ${availableAmount?.formatAmount() ?: "?"} $unitLabel"
    } else {
        ""
    }
    return "$source • $role • $kcalPerUnit kcal/$unitLabel$amount"
}

private fun Float.formatAmount(): String {
    return if (abs(this - roundToInt()) < 0.05f) {
        roundToInt().toString()
    } else {
        String.format(Locale.US, "%.1f", this)
    }
}
