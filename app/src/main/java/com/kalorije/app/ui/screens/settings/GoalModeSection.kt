package com.kalorije.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kalorije.app.data.model.GoalMode
import com.kalorije.app.ui.components.GlassCard
import com.kalorije.app.ui.components.appTextFieldColors
import com.kalorije.app.ui.theme.AppColors
import com.kalorije.app.ui.theme.AppDimens
import com.kalorije.app.ui.theme.AppTypography
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalModeSection(
    goalMode: GoalMode,
    onGoalModeChange: (GoalMode) -> Unit,
    goalPaceText: String,
    onGoalPaceChange: (String) -> Unit,
    paceWarning: String?,
    goalCalorieOffset: Int
) {
    Text(text = "Cilj težine", style = AppTypography.titleMedium)
    Spacer(modifier = Modifier.height(AppDimens.paddingSmall))

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 3 selectable kartice
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GoalModeCard(
                    label = "Održavanje",
                    emoji = "\u2696\uFE0F",
                    isSelected = goalMode == GoalMode.MAINTENANCE,
                    onClick = { onGoalModeChange(GoalMode.MAINTENANCE) },
                    modifier = Modifier.weight(1f)
                )
                GoalModeCard(
                    label = "Dobijanje",
                    emoji = "\uD83D\uDCAA",
                    isSelected = goalMode == GoalMode.BULK,
                    onClick = { onGoalModeChange(GoalMode.BULK) },
                    modifier = Modifier.weight(1f)
                )
                GoalModeCard(
                    label = "Mršavljenje",
                    emoji = "\uD83D\uDD25",
                    isSelected = goalMode == GoalMode.CUT,
                    onClick = { onGoalModeChange(GoalMode.CUT) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Za BULK/CUT: prikaz tempa
            if (goalMode != GoalMode.MAINTENANCE) {
                Text(
                    text = "Tempo (kg/nedeljno)",
                    style = AppTypography.labelLarge
                )

                // Predefinisani chip-ovi
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Poređenje je numeričko: "0.5" iz profila mora da označi chip "0.50"
                    val currentPace = goalPaceText.replace(",", ".").toDoubleOrNull()
                    val presets = listOf(0.25, 0.35, 0.5, 0.75)
                    presets.forEach { pace ->
                        PaceChip(
                            label = "${String.format("%.2f", pace)} kg",
                            isSelected = currentPace != null && abs(currentPace - pace) < 0.001,
                            onClick = { onGoalPaceChange(pace.toString()) }
                        )
                    }
                }

                // Ručni unos
                OutlinedTextField(
                    value = goalPaceText,
                    onValueChange = onGoalPaceChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ručni unos tempa") },
                    placeholder = { Text("npr. 0.35") },
                    suffix = { Text("kg/ned") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = appTextFieldColors(),
                    singleLine = true
                )

                // Prikaz kalorijskog offseta
                if (goalCalorieOffset != 0) {
                    val sign = if (goalCalorieOffset > 0) "+" else ""
                    val modeText = if (goalMode == GoalMode.BULK) "iznad" else "ispod"
                    Text(
                        text = "$sign$goalCalorieOffset kcal/dan $modeText TDEE",
                        style = AppTypography.bodySmall.copy(
                            color = if (goalMode == GoalMode.BULK) AppColors.Success else AppColors.Error
                        )
                    )
                }

                // Upozorenje za agresivan tempo
                paceWarning?.let { warning ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                AppColors.Warning.copy(alpha = 0.1f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("\u26A0\uFE0F", style = AppTypography.bodyMedium)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = warning,
                            style = AppTypography.bodySmall.copy(color = AppColors.Warning)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalModeCard(
    label: String,
    emoji: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isSelected) AppColors.Primary.copy(alpha = 0.15f) else AppColors.SurfaceLight.copy(alpha = 0.5f)
    val borderColor = if (isSelected) AppColors.Primary else AppColors.SurfaceLight

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, style = AppTypography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = AppTypography.labelMedium.copy(
                color = if (isSelected) AppColors.Primary else AppColors.TextSecondary
            )
        )
    }
}

@Composable
private fun PaceChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) AppColors.Primary.copy(alpha = 0.2f) else AppColors.SurfaceLight.copy(alpha = 0.5f)
    val borderColor = if (isSelected) AppColors.Primary else AppColors.SurfaceLight
    val textColor = if (isSelected) AppColors.Primary else AppColors.TextSecondary

    Text(
        text = label,
        style = AppTypography.labelMedium.copy(color = textColor),
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
