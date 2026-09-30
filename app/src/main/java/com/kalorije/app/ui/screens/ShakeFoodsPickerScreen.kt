package com.kalorije.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kalorije.app.data.model.SavedFood
import com.kalorije.app.data.repository.FoodRepository
import com.kalorije.app.ui.components.GlassCard
import com.kalorije.app.ui.theme.AppColors
import com.kalorije.app.ui.theme.AppDimens
import com.kalorije.app.ui.theme.AppTypography
import kotlin.math.roundToInt

@Composable
fun ShakeFoodsPickerScreen(
    repository: FoodRepository,
    onNavigateBack: () -> Unit
) {
    val savedFoods by repository.savedFoods.collectAsState()
    val shakeOrder by repository.shakeOrder.collectAsState()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedIds by remember(shakeOrder, savedFoods) {
        mutableStateOf(
            shakeOrder.filter { shakeFoodId -> savedFoods.any { it.id == shakeFoodId } }.toSet()
        )
    }

    val filteredFoods = remember(searchQuery, savedFoods) {
        if (searchQuery.isBlank()) {
            savedFoods
        } else {
            savedFoods.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    fun toggleSelection(foodId: String) {
        selectedIds = if (foodId in selectedIds) {
            selectedIds - foodId
        } else {
            selectedIds + foodId
        }
    }

    fun saveSelection() {
        val validIds = savedFoods.map { it.id }.toSet()
        val orderedSelection = buildList {
            shakeOrder.forEach { foodId ->
                if (foodId in selectedIds && foodId in validIds) {
                    add(foodId)
                }
            }
            savedFoods.forEach { food ->
                if (food.id in selectedIds && food.id !in this) {
                    add(food.id)
                }
            }
        }
        repository.setShakeFoodIds(orderedSelection)
        onNavigateBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .statusBarsPadding()
            .padding(AppDimens.paddingMedium)
    ) {
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
                text = "Sačuvana jela za šejk",
                style = AppTypography.titleLarge
            )
        }

        Spacer(modifier = Modifier.height(AppDimens.paddingSmall))

        Text(
            text = "Izaberi stavke za šejk. Gramažu unosiš tek u ekranu \"Unesi šejk\".",
            style = AppTypography.bodyMedium.copy(color = AppColors.TextSecondary)
        )

        Spacer(modifier = Modifier.height(AppDimens.paddingMedium))

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
                items(filteredFoods, key = { it.id }, contentType = { "shake_select_food" }) { food ->
                    ShakeSelectableFoodItem(
                        food = food,
                        isSelected = food.id in selectedIds,
                        onToggle = { toggleSelection(food.id) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(AppDimens.paddingMedium))

        Button(
            onClick = ::saveSelection,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
        ) {
            Text("Sačuvaj izbor (${selectedIds.size})")
        }
    }
}

@Composable
private fun ShakeSelectableFoodItem(
    food: SavedFood,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
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
                Text(
                    text = "${food.caloriesPer100g.roundToInt()} kcal/100g",
                    style = AppTypography.labelSmall.copy(color = AppColors.TextMuted)
                )
            }
        }
    }
}
