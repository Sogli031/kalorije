package com.kalorije.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kalorije.app.data.model.SavedFood
import com.kalorije.app.ui.theme.AppColors
import com.kalorije.app.ui.theme.AppTypography

@Composable
fun AddSavedFoodDialog(
    onSave: (SavedFood) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var caloriesText by remember { mutableStateOf("") }

    val canSave = name.isNotBlank() &&
            (caloriesText.toDoubleOrNull() ?: 0.0) > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Dodaj novo jelo",
                style = AppTypography.titleMedium
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Naziv jela") },
                    placeholder = { Text("npr. Jogurt") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appTextFieldColors(),
                    singleLine = true
                )

                Text(
                    text = "Kalorije po 100g:",
                    style = AppTypography.labelMedium.copy(color = AppColors.TextMuted)
                )

                OutlinedTextField(
                    value = caloriesText,
                    onValueChange = { caloriesText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Kalorije / 100g") },
                    placeholder = { Text("npr. 60") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = appTextFieldColors(),
                    singleLine = true
                )

            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val food = SavedFood(
                        name = name,
                        caloriesPer100g = caloriesText.toDoubleOrNull() ?: 0.0,
                        defaultGrams = 100.0
                    )
                    onSave(food)
                },
                enabled = canSave
            ) {
                Text(
                    text = "Sačuvaj",
                    color = if (canSave) AppColors.Primary else AppColors.TextMuted
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Odustani")
            }
        },
        containerColor = AppColors.Surface
    )
}
