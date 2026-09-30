package com.kalorije.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kalorije.app.data.model.ActivityLevel
import com.kalorije.app.ui.components.GlassCard
import com.kalorije.app.ui.components.appTextFieldColors
import com.kalorije.app.ui.theme.AppColors
import com.kalorije.app.ui.theme.AppDimens
import com.kalorije.app.ui.theme.AppTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSection(
    heightText: String, onHeightChange: (String) -> Unit, heightError: Boolean,
    weightText: String, onWeightChange: (String) -> Unit, weightError: Boolean,
    ageText: String, onAgeChange: (String) -> Unit, ageError: Boolean,
    isMale: Boolean, onGenderChange: (Boolean) -> Unit,
    activityLevel: ActivityLevel, onActivityLevelChange: (ActivityLevel) -> Unit,
    activityExpanded: Boolean, onActivityExpandedChange: (Boolean) -> Unit,
    resistanceTrainingDays: Int, onResistanceTrainingDaysChange: (Int) -> Unit,
    trainingDaysExpanded: Boolean, onTrainingDaysExpandedChange: (Boolean) -> Unit,
    manualTdeeText: String, onManualTdeeChange: (String) -> Unit
) {
    Text(text = "Tvoj profil", style = AppTypography.titleMedium)
    Spacer(modifier = Modifier.height(AppDimens.paddingSmall))
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(heightText, onHeightChange, Modifier.fillMaxWidth(), label = { Text("Visina (cm)") },
                isError = heightError, supportingText = if (heightError) { { Text("Visina mora biti između 50 i 250 cm", color = AppColors.Error) } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = appTextFieldColors(), singleLine = true)
            OutlinedTextField(weightText, onWeightChange, Modifier.fillMaxWidth(), label = { Text("Težina (kg)") },
                isError = weightError, supportingText = if (weightError) { { Text("Težina mora biti između 20 i 500 kg", color = AppColors.Error) } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = appTextFieldColors(), singleLine = true)
            OutlinedTextField(ageText, onAgeChange, Modifier.fillMaxWidth(), label = { Text("Godine") },
                isError = ageError, supportingText = if (ageError) { { Text("Starost mora biti između 1 i 120 godina", color = AppColors.Error) } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = appTextFieldColors(), singleLine = true)
            Text(text = "Pol", style = AppTypography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.weight(1f).selectable(isMale) { onGenderChange(true) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(isMale, { onGenderChange(true) }, colors = RadioButtonDefaults.colors(selectedColor = AppColors.Primary))
                    Icon(Icons.Default.Male, null, tint = if (isMale) AppColors.Primary else AppColors.TextMuted)
                    Spacer(Modifier.width(4.dp))
                    Text("Muško", style = AppTypography.labelLarge.copy(color = if (isMale) AppColors.TextPrimary else AppColors.TextMuted))
                }
                Row(Modifier.weight(1f).selectable(!isMale) { onGenderChange(false) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(!isMale, { onGenderChange(false) }, colors = RadioButtonDefaults.colors(selectedColor = AppColors.Primary))
                    Icon(Icons.Default.Female, null, tint = if (!isMale) AppColors.Primary else AppColors.TextMuted)
                    Spacer(Modifier.width(4.dp))
                    Text("Žensko", style = AppTypography.labelLarge.copy(color = if (!isMale) AppColors.TextPrimary else AppColors.TextMuted))
                }
            }
            ExposedDropdownMenuBox(activityExpanded, onActivityExpandedChange) {
                OutlinedTextField(activityLevel.displayName, {}, Modifier.fillMaxWidth().menuAnchor(), readOnly = true,
                    label = { Text("Nivo aktivnosti") }, supportingText = { Text(activityLevel.description) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(activityExpanded) }, colors = appTextFieldColors())
                ExposedDropdownMenu(activityExpanded, { onActivityExpandedChange(false) }) {
                    ActivityLevel.entries.forEach { level ->
                        DropdownMenuItem({ Column { Text(level.displayName); Text(level.description, style = AppTypography.labelSmall) } },
                            { onActivityLevelChange(level); onActivityExpandedChange(false) })
                    }
                }
            }
            ExposedDropdownMenuBox(trainingDaysExpanded, onTrainingDaysExpandedChange) {
                OutlinedTextField(if (resistanceTrainingDays == 0) "Ne treniram" else "$resistanceTrainingDays dana nedeljno", {},
                    Modifier.fillMaxWidth().menuAnchor(), readOnly = true, label = { Text("Trening s otporom") },
                    supportingText = { Text("Za procenu kompozicije dobitka") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(trainingDaysExpanded) }, colors = appTextFieldColors())
                ExposedDropdownMenu(trainingDaysExpanded, { onTrainingDaysExpandedChange(false) }) {
                    listOf(Triple(0, "Ne treniram", "Bez treninga s tegovima"), Triple(1, "1 dan", "Jednom nedeljno"),
                        Triple(2, "2 dana", "HIT / Full body"), Triple(3, "3 dana", "Push-Pull-Legs / Full body"),
                        Triple(4, "4 dana", "Upper-Lower split"), Triple(5, "5 dana", "PPL + Upper-Lower"),
                        Triple(6, "6 dana", "PPL x2"), Triple(7, "7 dana", "Svaki dan")).forEach { (days, label, description) ->
                        DropdownMenuItem({ Column { Text(label); Text(description, style = AppTypography.labelSmall) } },
                            { onResistanceTrainingDaysChange(days); onTrainingDaysExpandedChange(false) })
                    }
                }
            }
            OutlinedTextField(manualTdeeText, onManualTdeeChange, Modifier.fillMaxWidth(),
                label = { Text("Ručni TDEE (opciono)") }, placeholder = { Text("npr. 3000") },
                supportingText = { Text("Ostavi prazno za automatsko učenje") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = appTextFieldColors(), singleLine = true)
        }
    }
}
