package com.kalorije.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kalorije.app.data.model.SavedFood
import com.kalorije.app.ui.theme.AppColors
import com.kalorije.app.ui.theme.AppTypography
import kotlin.math.roundToInt

data class SelectedFoodItem(
    val food: SavedFood,
    val grams: Double // količina u gramima koju korisnik unese
)

@Composable
fun SavedFoodsDialog(
    savedFoods: List<SavedFood>,
    onSelectMultiple: (List<SelectedFoodItem>) -> Unit,
    onEdit: (SavedFood) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    // Mapa: foodId -> grams (0 = nije selektovano)
    val selections = remember { mutableStateMapOf<String, Double>() }

    // Kalkulator grama - koliko kalorija korisnik želi da unese
    var targetCaloriesText by remember { mutableStateOf("") }
    val targetCalories = targetCaloriesText.toIntOrNull() ?: 0

    val filteredFoods = remember(searchQuery, savedFoods) {
        if (searchQuery.isBlank()) {
            savedFoods
        } else {
            savedFoods.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    val hasSelections = selections.any { it.value > 0 }

    // Izračunaj ukupne makroe za selektovana jela
    val totalCalories = remember(selections.toMap(), savedFoods) {
        selections.entries.sumOf { (foodId, grams) ->
            val food = savedFoods.find { it.id == foodId }
            if (food != null && grams > 0) {
                (food.caloriesPer100g * grams / 100.0).toInt()
            } else 0
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Sačuvana jela",
                style = AppTypography.titleMedium
            )
        },
        text = {
            Column {
                // Kalkulator grama - unesi željene kalorije
                OutlinedTextField(
                    value = targetCaloriesText,
                    onValueChange = { targetCaloriesText = it.filter { c -> c.isDigit() } },
                    placeholder = { Text("Unesi željene kcal...") },
                    label = { Text("Koliko kcal ti treba?") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.Calories,
                        unfocusedBorderColor = AppColors.GlassBorder,
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary,
                        focusedLabelColor = AppColors.Calories,
                        unfocusedLabelColor = AppColors.TextMuted
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    suffix = { Text("kcal", style = AppTypography.labelSmall) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Pretraga
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Pretraži...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = AppColors.TextMuted
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.Primary,
                        unfocusedBorderColor = AppColors.GlassBorder,
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Prikaz ukupno za selektovana jela
                if (hasSelections) {
                    Text(
                        text = "Selektovano: $totalCalories kcal",
                        style = AppTypography.labelMedium.copy(color = AppColors.Primary),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (filteredFoods.isEmpty()) {
                    Text(
                        text = if (searchQuery.isBlank()) "Nema sačuvanih jela" else "Nema rezultata pretrage",
                        style = AppTypography.bodyMedium.copy(color = AppColors.TextMuted),
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredFoods, key = { it.id }) { food ->
                            val isSelected = selections.containsKey(food.id)
                            SavedFoodItemMultiSelect(
                                food = food,
                                isSelected = isSelected,
                                targetCalories = targetCalories,
                                onGramsChange = { newGrams ->
                                    if (newGrams < 0) {
                                        selections.remove(food.id)
                                    } else {
                                        selections[food.id] = newGrams
                                    }
                                },
                                onEdit = { onEdit(food) },
                                onDelete = { onDelete(food.id) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = selections.mapNotNull { (foodId, grams) ->
                        if (grams > 0) {
                            savedFoods.find { it.id == foodId }?.let { food ->
                                SelectedFoodItem(food, grams)
                            }
                        } else null
                    }
                    if (selected.isNotEmpty()) {
                        onSelectMultiple(selected)
                    }
                },
                enabled = hasSelections
            ) {
                Text(
                    text = "Dodaj",
                    color = if (hasSelections) AppColors.Primary else AppColors.TextMuted
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Zatvori")
            }
        },
        containerColor = AppColors.Surface
    )
}

@Composable
private fun SavedFoodItemMultiSelect(
    food: SavedFood,
    isSelected: Boolean,
    targetCalories: Int,
    onGramsChange: (Double) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var gramsText by remember(food.id, isSelected) { mutableStateOf("") }

    // Izračunaj kalorije za trenutno unete grame
    val displayGrams = gramsText.toDoubleOrNull() ?: 0.0
    val calculatedCalories = (food.caloriesPer100g * displayGrams / 100.0).roundToInt()

    // Izračunaj koliko grama treba za ciljane kalorije
    val gramsForTargetCalories = if (targetCalories > 0 && food.caloriesPer100g > 0) {
        (targetCalories.toDouble() / food.caloriesPer100g * 100.0).roundToInt()
    } else 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isSelected) {
                    gramsText = ""
                    onGramsChange(-1.0)
                } else {
                    gramsText = ""
                    onGramsChange(0.0)
                }
            }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { checked ->
                if (checked) {
                    gramsText = ""
                    onGramsChange(0.0)
                } else {
                    gramsText = ""
                    onGramsChange(-1.0)
                }
            },
            colors = CheckboxDefaults.colors(
                checkedColor = AppColors.Primary,
                uncheckedColor = AppColors.TextMuted
            )
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = food.name,
                style = AppTypography.labelLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "${food.caloriesPer100g.roundToInt()} kcal/100g",
                    style = AppTypography.labelSmall.copy(color = AppColors.TextMuted)
                )
                if (isSelected && displayGrams > 0) {
                    Text(
                        text = "= $calculatedCalories kcal",
                        style = AppTypography.labelSmall.copy(color = AppColors.Calories)
                    )
                }
            }
            // Prikaz koliko grama treba za ciljane kalorije
            if (targetCalories > 0 && gramsForTargetCalories > 0) {
                Text(
                    text = "→ za $targetCalories kcal treba ${gramsForTargetCalories}g",
                    style = AppTypography.labelSmall.copy(color = AppColors.Calories)
                )
            }
        }

        // Grams input - prikazuje se samo kad je selektovano
        if (isSelected) {
            Column(horizontalAlignment = Alignment.End) {
                OutlinedTextField(
                    value = gramsText,
                    onValueChange = { newValue ->
                        val filtered = newValue.filter { it.isDigit() || it == '.' }
                        gramsText = filtered
                        // Only update selection when there's a valid positive number
                        // Empty field = user is editing, don't deselect
                        val grams = filtered.toDoubleOrNull()
                        if (grams != null && grams > 0) {
                            onGramsChange(grams)
                        }
                    },
                    modifier = Modifier.width(70.dp),
                    placeholder = { Text("g", style = AppTypography.labelSmall) },
                    suffix = { Text("g", style = AppTypography.labelSmall) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.Primary,
                        unfocusedBorderColor = AppColors.GlassBorder,
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary
                    ),
                    textStyle = AppTypography.labelMedium
                )
                // Dugme za auto-popunjavanje ako je uneto ciljanih kalorija
                if (targetCalories > 0 && gramsForTargetCalories > 0) {
                    TextButton(
                        onClick = {
                            gramsText = gramsForTargetCalories.toString()
                            onGramsChange(gramsForTargetCalories.toDouble())
                        },
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Text(
                            text = "Popuni ${gramsForTargetCalories}g",
                            style = AppTypography.labelSmall.copy(color = AppColors.Calories)
                        )
                    }
                }
            }
        } else {
            // Edit/Delete buttons kad nije selektovano
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Izmeni",
                    tint = AppColors.TextSecondary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Obriši",
                    tint = AppColors.TextMuted
                )
            }
        }
    }
}
