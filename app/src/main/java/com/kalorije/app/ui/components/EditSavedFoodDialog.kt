package com.kalorije.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
fun EditSavedFoodDialog(
    food: SavedFood,
    onSave: (SavedFood) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(food.name) }
    var caloriesText by remember { mutableStateOf(food.caloriesPer100g.toString()) }

    var nameError by remember { mutableStateOf(false) }
    var caloriesError by remember { mutableStateOf(false) }

    fun validateAndSave() {
        val calories = caloriesText.toDoubleOrNull()

        nameError = name.isBlank()
        caloriesError = calories == null || calories < 0

        if (!nameError && !caloriesError) {
            onSave(
                food.copy(
                    name = name,
                    caloriesPer100g = calories ?: 0.0
                )
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Izmeni sačuvano jelo",
                style = AppTypography.titleMedium
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = false
                    },
                    label = { Text("Naziv") },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text("Naziv ne može biti prazan", color = AppColors.Error) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = dialogTextFieldColors(),
                    singleLine = true
                )



                OutlinedTextField(
                    value = caloriesText,
                    onValueChange = {
                        caloriesText = it.filter { c -> c.isDigit() || c == '.' }
                        caloriesError = false
                    },
                    label = { Text("Kalorije (na 100g)") },
                    isError = caloriesError,
                    supportingText = if (caloriesError) {
                        { Text("Kalorije ne mogu biti negativne", color = AppColors.Error) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = dialogTextFieldColors(),
                    singleLine = true
                )

            }
        },
        confirmButton = {
            Button(
                onClick = { validateAndSave() },
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
            ) {
                Text("Sačuvaj")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Otkaži")
            }
        },
        containerColor = AppColors.Surface
    )
}

@Composable
private fun dialogTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AppColors.Primary,
    unfocusedBorderColor = AppColors.GlassBorder,
    focusedTextColor = AppColors.TextPrimary,
    unfocusedTextColor = AppColors.TextPrimary,
    focusedLabelColor = AppColors.Primary,
    errorBorderColor = AppColors.Error,
    errorLabelColor = AppColors.Error
)
