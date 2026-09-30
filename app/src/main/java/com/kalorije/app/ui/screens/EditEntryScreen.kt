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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kalorije.app.data.model.FoodEntry
import com.kalorije.app.data.model.FoodProduct
import com.kalorije.app.data.repository.FoodRepository
import com.kalorije.app.ui.components.GlassCard
import com.kalorije.app.ui.components.MacroDisplay
import com.kalorije.app.ui.components.appTextFieldColors
import com.kalorije.app.ui.theme.AppColors
import com.kalorije.app.ui.theme.AppDimens
import com.kalorije.app.ui.theme.AppTypography
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun EditEntryScreen(
    entryId: String,
    repository: FoodRepository,
    onNavigateBack: () -> Unit
) {
    val existingEntry = remember { repository.getEntryById(entryId) }

    if (existingEntry == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .statusBarsPadding()
                .padding(AppDimens.paddingMedium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Unos nije pronađen",
                style = AppTypography.titleMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onNavigateBack,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
            ) {
                Text("Nazad")
            }
        }
        return
    }

    var name by rememberSaveable { mutableStateOf(existingEntry.product.name) }
    var caloriesText by rememberSaveable { mutableStateOf(existingEntry.product.caloriesPer100g.toString()) }
    var gramsText by rememberSaveable { mutableStateOf(existingEntry.grams.toString()) }
    var timeText by rememberSaveable {
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        mutableStateOf(existingEntry.time ?: existingEntry.createdAt.format(timeFormatter))
    }

    val grams = gramsText.toDoubleOrNull() ?: 0.0
    val caloriesPer100 = caloriesText.toDoubleOrNull() ?: 0.0

    val totalCalories = (caloriesPer100 * grams / 100).roundToInt()

    val gramsError = gramsText.isNotBlank() && grams <= 0
    val caloriesError = caloriesText.isNotBlank() && caloriesPer100 < 0

    val canSave = name.isNotBlank() && grams > 0 && caloriesPer100 >= 0 && !gramsError && !caloriesError

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .statusBarsPadding()
            .padding(AppDimens.paddingMedium)
            .verticalScroll(rememberScrollState())
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
                text = "Izmeni unos",
                style = AppTypography.titleLarge
            )
        }

        Spacer(modifier = Modifier.height(AppDimens.paddingMedium))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Naziv hrane") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appTextFieldColors(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Kalorije na 100g:",
                    style = AppTypography.labelMedium.copy(color = AppColors.TextMuted)
                )

                OutlinedTextField(
                    value = caloriesText,
                    onValueChange = { caloriesText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Kalorije / 100g") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = appTextFieldColors(),
                    singleLine = true,
                    isError = caloriesError,
                    supportingText = if (caloriesError) {
                        { Text("Kalorije ne mogu biti negativne", color = AppColors.Error) }
                    } else null
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Količina:",
                    style = AppTypography.labelMedium.copy(color = AppColors.TextMuted)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = gramsText,
                        onValueChange = { gramsText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Grama") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        colors = appTextFieldColors(),
                        singleLine = true,
                        isError = gramsError,
                        supportingText = if (gramsError) {
                            { Text("Gramaza mora biti veća od 0", color = AppColors.Error) }
                        } else null
                    )

                    OutlinedTextField(
                        value = timeText,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() || it == ':' }.take(5)
                            timeText = filtered
                        },
                        label = { Text("Vreme") },
                        modifier = Modifier.weight(0.6f),
                        colors = appTextFieldColors(),
                        singleLine = true,
                        placeholder = { Text("HH:mm") }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(AppDimens.paddingMedium))

        if (canSave) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "Ukupno za $gramsText g:",
                        style = AppTypography.labelLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MacroDisplay("Kalorije", "$totalCalories", "kcal", AppColors.Calories)
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppDimens.paddingMedium))
        }

        Button(
            onClick = {
                val product = FoodProduct(
                    id = existingEntry.product.id,
                    name = name,
                    brand = existingEntry.product.brand,
                    imageUrl = existingEntry.product.imageUrl,
                    caloriesPer100g = caloriesPer100,
                    servingSize = existingEntry.product.servingSize
                )
                // Validiraj vreme
                val validTime = if (timeText.matches(Regex("\\d{1,2}:\\d{2}"))) {
                    timeText
                } else {
                    existingEntry.time
                }
                val updatedEntry = existingEntry.copy(
                    product = product,
                    grams = grams,
                    time = validTime
                )
                repository.updateFull(entryId, updatedEntry)
                onNavigateBack()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
            enabled = canSave
        ) {
            Text("Sačuvaj izmene")
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

