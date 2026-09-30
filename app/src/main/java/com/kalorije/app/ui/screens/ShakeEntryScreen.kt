package com.kalorije.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kalorije.app.data.model.FoodEntry
import com.kalorije.app.data.model.FoodProduct
import com.kalorije.app.data.model.SavedFood
import com.kalorije.app.data.repository.FoodRepository
import com.kalorije.app.ui.components.GlassCard
import com.kalorije.app.ui.components.MacroDisplay
import com.kalorije.app.ui.components.appTextFieldColors
import com.kalorije.app.ui.theme.AppColors
import com.kalorije.app.ui.theme.AppDimens
import com.kalorije.app.ui.theme.AppTypography
import com.kalorije.app.util.WeightNormalizer
import com.kalorije.app.util.getLogicalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun ShakeEntryScreen(
    repository: FoodRepository,
    onNavigateToSavedFoods: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val savedFoods by repository.savedFoods.collectAsState()
    val shakeOrder by repository.shakeOrder.collectAsState()
    val orderedFoods = remember(savedFoods, shakeOrder) {
        repository.getShakeOrderedFoods(savedFoods)
    }
    val focusManager = LocalFocusManager.current

    val gramsInputs = remember { mutableStateMapOf<String, String>() }

    val selectedFoods = remember(orderedFoods, gramsInputs.toMap()) {
        selectedShakeFoods(orderedFoods, gramsInputs)
    }

    val totalCalories = remember(selectedFoods) {
        selectedFoods.sumOf { (food, grams) -> food.caloriesPer100g * grams / 100.0 }
    }

    fun submitShake() {
        focusManager.clearFocus(force = true)
        val foodsToSubmit = selectedShakeFoods(orderedFoods, gramsInputs)
        if (foodsToSubmit.isEmpty()) return

        val groupId = UUID.randomUUID().toString()
        val logicalDate = getLogicalDate()
        val time = WeightNormalizer.formatTime(LocalTime.now())

        val entries = foodsToSubmit.map { (food, grams) ->
            FoodEntry(
                id = UUID.randomUUID().toString(),
                date = logicalDate,
                product = FoodProduct(
                    name = food.name,
                    caloriesPer100g = food.caloriesPer100g
                ),
                grams = grams,
                groupId = groupId,
                time = time
            )
        }

        repository.addAll(entries)
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
                text = "Unesi šejk",
                style = AppTypography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onNavigateToSavedFoods) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Dodaj stavke iz sačuvanih jela",
                    tint = AppColors.Primary
                )
            }
        }

        Spacer(modifier = Modifier.height(AppDimens.paddingMedium))

        if (orderedFoods.isEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Nema izabranih stavki za šejk.",
                        style = AppTypography.bodyMedium
                    )
                    Text(
                        text = "Pritisni + da izabereš stavke iz sačuvanih jela.",
                        style = AppTypography.labelSmall.copy(color = AppColors.TextMuted)
                    )
                }
            }
        } else {
            Text(
                text = "Stavke šejka",
                style = AppTypography.titleMedium
            )

            Spacer(modifier = Modifier.height(AppDimens.paddingSmall))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(orderedFoods, key = { _, food -> food.id }, contentType = { _, _ -> "shake_food" }) { index, food ->
                    ShakeFoodItem(
                        food = food,
                        gramsInput = gramsInputs[food.id].orEmpty(),
                        currentGrams = parsePositiveGrams(gramsInputs[food.id].orEmpty()),
                        isFirst = index == 0,
                        isLast = index == orderedFoods.lastIndex,
                        onGramsInputChange = { input ->
                            gramsInputs[food.id] = sanitizeDecimalInput(input)
                        },
                        onMoveUp = { repository.moveShakeFoodUp(food.id) },
                        onMoveDown = { repository.moveShakeFoodDown(food.id) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(AppDimens.paddingMedium))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Unos za ovaj šejk",
                    style = AppTypography.titleMedium
                )

                if (selectedFoods.isEmpty()) {
                    Text(
                        text = "Unesi gramažu za stavke koje si pojeo.",
                        style = AppTypography.bodyMedium
                    )
                } else {
                    selectedFoods.forEach { (food, grams) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = food.name,
                                    style = AppTypography.labelLarge
                                )
                                Text(
                                    text = "${grams.roundToInt()} g • ${(food.caloriesPer100g * grams / 100.0).roundToInt()} kcal",
                                    style = AppTypography.labelSmall.copy(color = AppColors.TextMuted)
                                )
                            }
                            IconButton(
                                onClick = { gramsInputs.remove(food.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Ukloni",
                                    tint = AppColors.TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MacroDisplay(
                            label = "Kal",
                            value = totalCalories.roundToInt().toString(),
                            unit = "kcal",
                            color = AppColors.Calories
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(AppDimens.paddingMedium))

        Button(
            onClick = ::submitShake,
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedFoods.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
        ) {
            Text("Unesi šejk (${totalCalories.roundToInt()} kcal)")
        }
    }
}

@Composable
private fun ShakeFoodItem(
    food: SavedFood,
    gramsInput: String,
    currentGrams: Double?,
    isFirst: Boolean,
    isLast: Boolean,
    onGramsInputChange: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val parsedGrams = gramsInput.replace(',', '.').toDoubleOrNull()
    val isInputError = gramsInput.isNotBlank() && (parsedGrams == null || parsedGrams <= 0.0)

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = food.name,
                    style = AppTypography.labelLarge
                )
                Text(
                    text = if (currentGrams != null && currentGrams > 0.0) {
                        "${currentGrams.roundToInt()} g • ${(food.caloriesPer100g * currentGrams / 100.0).roundToInt()} kcal"
                    } else {
                        "${food.caloriesPer100g.roundToInt()} kcal/100g"
                    },
                    style = AppTypography.labelSmall.copy(color = AppColors.TextMuted)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = gramsInput,
                    onValueChange = onGramsInputChange,
                    modifier = Modifier.width(96.dp),
                    suffix = { Text("g", style = AppTypography.labelSmall) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = isInputError,
                    colors = appTextFieldColors(),
                    textStyle = AppTypography.labelMedium
                )
                IconButton(onClick = onMoveUp, enabled = !isFirst) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Pomeri gore",
                        tint = if (!isFirst) AppColors.TextSecondary else AppColors.TextMuted
                    )
                }
                IconButton(onClick = onMoveDown, enabled = !isLast) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Pomeri dole",
                        tint = if (!isLast) AppColors.TextSecondary else AppColors.TextMuted
                    )
                }
            }
        }
    }
}

private fun sanitizeDecimalInput(input: String): String {
    val normalized = input.replace(',', '.')
    val filtered = buildString {
        normalized.forEach { ch ->
            if (ch.isDigit() || ch == '.') {
                append(ch)
            }
        }
    }
    val dotIndex = filtered.indexOf('.')
    return if (dotIndex == -1) {
        filtered
    } else {
        val beforeDot = filtered.substring(0, dotIndex + 1)
        val afterDot = filtered.substring(dotIndex + 1).replace(".", "")
        beforeDot + afterDot
    }
}

private fun parsePositiveGrams(input: String): Double? {
    val grams = input.replace(',', '.').toDoubleOrNull() ?: return null
    return grams.takeIf { it > 0.0 }
}

private fun selectedShakeFoods(
    orderedFoods: List<SavedFood>,
    gramsInputs: Map<String, String>
): List<Pair<SavedFood, Double>> {
    return orderedFoods.mapNotNull { food ->
        parsePositiveGrams(gramsInputs[food.id].orEmpty())?.let { grams ->
            food to grams
        }
    }
}
