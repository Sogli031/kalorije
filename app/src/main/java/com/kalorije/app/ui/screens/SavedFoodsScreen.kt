package com.kalorije.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.kalorije.app.data.repository.FoodRepository
import com.kalorije.app.ui.components.AddSavedFoodDialog
import com.kalorije.app.ui.components.EditSavedFoodDialog
import com.kalorije.app.ui.components.GlassCard
import com.kalorije.app.ui.components.SelectedFoodItem
import com.kalorije.app.ui.theme.AppColors
import com.kalorije.app.ui.theme.AppDimens
import com.kalorije.app.ui.theme.AppTypography
import kotlin.math.roundToInt

@Composable
fun SavedFoodsScreen(
    repository: FoodRepository,
    onSelectItems: ((List<SelectedFoodItem>) -> Unit)? = null,
    onNavigateBack: () -> Unit
) {
    val savedFoods by repository.savedFoods.collectAsState()
    val isSelectionMode = onSelectItems != null

    var searchQuery by remember { mutableStateOf("") }
    val selections = remember { mutableStateMapOf<String, Double>() }

    var showEditDialog by remember { mutableStateOf(false) }
    var foodToEdit by remember { mutableStateOf<SavedFood?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val filteredFoods = remember(searchQuery, savedFoods) {
        if (searchQuery.isBlank()) {
            savedFoods
        } else {
            savedFoods.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    val hasSelections = selections.any { it.value > 0 }

    val totalCalories = remember(selections.toMap(), savedFoods) {
        selections.entries.sumOf { (foodId, grams) ->
            val food = savedFoods.find { it.id == foodId }
            if (food != null && grams > 0) {
                (food.caloriesPer100g * grams / 100.0).toInt()
            } else 0
        }
    }

    if (showEditDialog && foodToEdit != null) {
        EditSavedFoodDialog(
            food = foodToEdit!!,
            onSave = { updatedFood ->
                repository.updateSavedFood(updatedFood)
                showEditDialog = false
                foodToEdit = null
            },
            onDismiss = {
                showEditDialog = false
                foodToEdit = null
            }
        )
    }

    if (showAddDialog) {
        AddSavedFoodDialog(
            onSave = { food ->
                repository.addSavedFood(food)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .statusBarsPadding()
            .padding(AppDimens.paddingMedium)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Nazad",
                    tint = AppColors.TextPrimary
                )
            }
            Text(
                text = "Sačuvana jela",
                style = AppTypography.titleLarge
            )
        }

        Spacer(modifier = Modifier.height(AppDimens.paddingSmall))

        // Search
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

        Spacer(modifier = Modifier.height(AppDimens.paddingSmall))

        // Selection summary - only in selection mode
        if (isSelectionMode && hasSelections) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$totalCalories",
                            style = AppTypography.titleMedium.copy(color = AppColors.Calories)
                        )
                        Text(
                            text = "kcal",
                            style = AppTypography.labelSmall.copy(color = AppColors.TextMuted)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${selections.count { it.value > 0 }}",
                            style = AppTypography.titleMedium.copy(color = AppColors.Primary)
                        )
                        Text(
                            text = "jela",
                            style = AppTypography.labelSmall.copy(color = AppColors.TextMuted)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(AppDimens.paddingSmall))
        }

        // Foods list
        if (filteredFoods.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (searchQuery.isBlank()) "Nema sačuvanih jela" else "Nema rezultata pretrage",
                    style = AppTypography.bodyMedium.copy(color = AppColors.TextMuted)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredFoods, key = { it.id }, contentType = { "saved_food" }) { food ->
                    val isSelected = selections.containsKey(food.id)
                    SavedFoodItem(
                        food = food,
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                        onGramsChange = { newGrams ->
                            if (newGrams < 0) {
                                selections.remove(food.id)
                            } else {
                                selections[food.id] = newGrams
                            }
                        },
                        onEdit = {
                            foodToEdit = food
                            showEditDialog = true
                        },
                        onDelete = { repository.deleteSavedFood(food.id) }
                    )
                }
            }
        }

        // Add to meal button - only in selection mode
        if (isSelectionMode) {
            Spacer(modifier = Modifier.height(AppDimens.paddingMedium))

            Button(
                onClick = {
                    val selected = selections.mapNotNull { (foodId, grams) ->
                        if (grams > 0) {
                            savedFoods.find { it.id == foodId }?.let { food ->
                                SelectedFoodItem(food, grams)
                            }
                        } else null
                    }
                    if (selected.isNotEmpty()) {
                        onSelectItems?.invoke(selected)
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
        }
    }

        // FAB for adding new food - only in management mode
        if (!isSelectionMode) {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(AppDimens.paddingMedium),
                containerColor = AppColors.Primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Dodaj novo jelo",
                    tint = AppColors.TextPrimary
                )
            }
        }
    }
}

@Composable
private fun SavedFoodItem(
    food: SavedFood,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onGramsChange: (Double) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var gramsText by remember(food.id, isSelected) { mutableStateOf("") }

    val displayGrams = gramsText.toDoubleOrNull() ?: 0.0
    val calculatedCalories = (food.caloriesPer100g * displayGrams / 100.0).roundToInt()

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelectionMode) {
                    Modifier.clickable {
                        if (isSelected) {
                            gramsText = ""
                            onGramsChange(-1.0)
                        } else {
                            gramsText = ""
                            onGramsChange(0.0)
                        }
                    }
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox only in selection mode
            if (isSelectionMode) {
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
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = food.name,
                    style = AppTypography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${food.caloriesPer100g.roundToInt()} kcal/100g",
                        style = AppTypography.labelSmall.copy(color = AppColors.TextMuted)
                    )
                }
                // Show calculated values only in selection mode when selected
                if (isSelectionMode && isSelected && displayGrams > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "= $calculatedCalories kcal",
                            style = AppTypography.labelMedium.copy(color = AppColors.Calories)
                        )
                    }
                }
            }

            // In selection mode: show grams input when selected, otherwise nothing
            // In management mode: always show edit/delete buttons
            if (isSelectionMode && isSelected) {
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
                        modifier = Modifier.width(80.dp),
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
            } else if (!isSelectionMode || !isSelected) {
                // Show edit/delete in management mode or when not selected in selection mode
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
}
