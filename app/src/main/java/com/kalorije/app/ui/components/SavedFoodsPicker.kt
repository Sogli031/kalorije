package com.kalorije.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import com.kalorije.app.ui.theme.AppDimens
import com.kalorije.app.ui.theme.AppTypography
import kotlin.math.roundToInt

/**
 * Inline lista sačuvanih jela - prikazuje se direktno u ekranu za unos obroka,
 * bez otvaranja posebnog ekrana.
 */
@Composable
fun SavedFoodsPicker(
    savedFoods: List<SavedFood>,
    onAddItems: (List<SelectedFoodItem>) -> Unit,
    modifier: Modifier = Modifier,
    onManageSavedFoods: (() -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    val selections = remember { mutableStateMapOf<String, Double>() }

    val filteredFoods = remember(searchQuery, savedFoods) {
        if (searchQuery.isBlank()) {
            savedFoods
        } else {
            savedFoods.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    val hasSelections = selections.any { it.value > 0 }

    val totalCalories = selections.entries.sumOf { (foodId, grams) ->
        val food = savedFoods.find { it.id == foodId }
        if (food != null && grams > 0) (food.caloriesPer100g * grams / 100.0).toInt() else 0
    }

    GlassCard(
        modifier = modifier,
        bgColor = AppColors.SurfaceRaised,
        accentColor = AppColors.Primary
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader(
                title = "Sačuvana jela",
                trailing = if (hasSelections) "$totalCalories kcal" else null
            )

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
                colors = appTextFieldColors(),
                singleLine = true
            )

            if (filteredFoods.isEmpty()) {
                Text(
                    text = if (searchQuery.isBlank()) {
                        "Nema sačuvanih jela"
                    } else {
                        "Nema rezultata pretrage"
                    },
                    style = AppTypography.bodyMedium.copy(color = AppColors.TextMuted),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    filteredFoods.forEach { food ->
                        SavedFoodPickerRow(
                            food = food,
                            isSelected = selections.containsKey(food.id),
                            onToggle = { selected ->
                                if (selected) {
                                    selections[food.id] = 0.0
                                } else {
                                    selections.remove(food.id)
                                }
                            },
                            onGramsChange = { grams -> selections[food.id] = grams }
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val selected = selections.mapNotNull { (foodId, grams) ->
                        if (grams > 0) {
                            savedFoods.find { it.id == foodId }?.let { SelectedFoodItem(it, grams) }
                        } else null
                    }
                    if (selected.isNotEmpty()) {
                        onAddItems(selected)
                        selections.clear()
                        searchQuery = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                enabled = hasSelections
            ) {
                Text(
                    text = if (hasSelections) "Dodaj u obrok ($totalCalories kcal)" else "Izaberi jela"
                )
            }

            if (onManageSavedFoods != null) {
                Text(
                    text = "Uredi sačuvana jela",
                    style = AppTypography.labelSmall.copy(color = AppColors.Primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onManageSavedFoods() }
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SavedFoodPickerRow(
    food: SavedFood,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit,
    onGramsChange: (Double) -> Unit
) {
    var gramsText by remember(food.id, isSelected) { mutableStateOf("") }

    val displayGrams = gramsText.toDoubleOrNull() ?: 0.0
    val calculatedCalories = (food.caloriesPer100g * displayGrams / 100.0).roundToInt()

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isSelected) },
        contentPadding = AppDimens.paddingSmall,
        selected = isSelected,
        accentColor = AppColors.Primary
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle(it) },
                colors = CheckboxDefaults.colors(
                    checkedColor = AppColors.Primary,
                    uncheckedColor = AppColors.TextMuted
                )
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(text = food.name, style = AppTypography.labelLarge)
                Text(
                    text = "${food.caloriesPer100g.roundToInt()} kcal/100g",
                    style = AppTypography.labelSmall.copy(color = AppColors.TextMuted)
                )
                if (isSelected && displayGrams > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "= $calculatedCalories kcal",
                        style = AppTypography.labelMedium.copy(color = AppColors.Calories)
                    )
                }
            }

            if (isSelected) {
                OutlinedTextField(
                    value = gramsText,
                    onValueChange = { newValue ->
                        val filtered = newValue.filter { it.isDigit() || it == '.' }
                        gramsText = filtered
                        onGramsChange(filtered.toDoubleOrNull() ?: 0.0)
                    },
                    modifier = Modifier.width(90.dp),
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
            }
        }
    }
}
