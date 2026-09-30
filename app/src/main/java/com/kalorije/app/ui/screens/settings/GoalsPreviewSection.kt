package com.kalorije.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kalorije.app.data.model.GoalMode
import com.kalorije.app.data.model.UserProfile
import com.kalorije.app.ui.components.GlassCard
import com.kalorije.app.ui.theme.AppColors
import com.kalorije.app.ui.theme.AppDimens
import com.kalorije.app.ui.theme.AppTypography
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun GoalsPreviewSection(
    previewProfile: UserProfile,
    hasProfileError: Boolean,
    onSaveProfile: () -> Unit
) {
    // Calculated Goals Preview Title
    Text(
        text = "Izračunati ciljevi",
        style = AppTypography.titleMedium
    )

    Spacer(modifier = Modifier.height(AppDimens.paddingSmall))

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Calculate,
                    contentDescription = null,
                    tint = AppColors.Primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Na osnovu tvojih podataka:",
                    style = AppTypography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // BMR and TDEE info
            Text(
                text = "BMR (bazalni metabolizam): ${previewProfile.bmr.roundToInt()} kcal" +
                        if (previewProfile.useTrt) " (TRT +${((previewProfile.trtMultiplier - 1) * 100).toInt()}%)" else "",
                style = AppTypography.bodyMedium
            )
            Text(
                text = "TDEE (dnevna potrošnja): ${previewProfile.tdee.roundToInt()} kcal",
                style = AppTypography.bodyMedium
            )

            // Show TDEE source info
            if (previewProfile.manualTdee != null) {
                Text(
                    text = "TDEE: ručno postavljen (${previewProfile.manualTdee} kcal)",
                    style = AppTypography.bodySmall.copy(color = AppColors.Primary)
                )
            } else if (previewProfile.effectiveTdeeAdjustment != 0) {
                val profileLabel = when {
                    previewProfile.postCycleState?.isActive == true -> "Post-cycle (zamrznuto)"
                    previewProfile.useTrt -> "TRT profil"
                    else -> "Natural profil"
                }
                Text(
                    text = "Naučena korekcija ($profileLabel): ${if (previewProfile.effectiveTdeeAdjustment > 0) "+" else ""}${previewProfile.effectiveTdeeAdjustment} kcal",
                    style = AppTypography.bodySmall.copy(color = AppColors.Primary)
                )
            }

            // GoalMode info
            val goalModeText = when (previewProfile.goalMode) {
                GoalMode.MAINTENANCE -> "Održavanje"
                GoalMode.BULK -> "Dobijanje (${previewProfile.goalPaceKgPerWeek} kg/ned)"
                GoalMode.CUT -> "Mršavljenje (${previewProfile.goalPaceKgPerWeek} kg/ned)"
            }
            Text(
                text = "Cilj: $goalModeText",
                style = AppTypography.bodySmall.copy(color = AppColors.TextSecondary)
            )
            if (previewProfile.goalCalorieOffset != 0) {
                val sign = if (previewProfile.goalCalorieOffset > 0) "+" else ""
                Text(
                    text = "Offset: $sign${previewProfile.goalCalorieOffset} kcal/dan",
                    style = AppTypography.bodySmall.copy(
                        color = if (previewProfile.goalCalorieOffset > 0) AppColors.Success else AppColors.Error
                    )
                )
            }

            // Predicted weight change
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                val change = previewProfile.predictedWeeklyChange
                Icon(
                    imageVector = when {
                        abs(change) < 0.05 -> Icons.AutoMirrored.Filled.TrendingFlat
                        change > 0 -> Icons.AutoMirrored.Filled.TrendingUp
                        else -> Icons.AutoMirrored.Filled.TrendingDown
                    },
                    contentDescription = null,
                    tint = when {
                        abs(change) < 0.05 -> AppColors.TextMuted
                        change > 0 -> AppColors.Success
                        else -> AppColors.Error
                    },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Prognoza: ${previewProfile.predictedChangeText}",
                    style = AppTypography.bodyMedium.copy(
                        color = when {
                            abs(change) < 0.05 -> AppColors.TextMuted
                            change > 0 -> AppColors.Success
                            else -> AppColors.Error
                        }
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Ciljne kalorije
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MacroTargetDisplay("Kalorije", "${previewProfile.targetCalories}", "kcal", AppColors.Calories)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Nedeljni cilj (pon–ned): ${previewProfile.targetCalories * 7} kcal",
                style = AppTypography.bodySmall.copy(color = AppColors.TextSecondary)
            )

            // Upozorenje ako je cilj podignut na donju granicu
            if (previewProfile.isCalorieFloorApplied) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⚠️ Izabrani tempo daje cilj ispod bezbednog minimuma " +
                            "(${previewProfile.minimumCalories} kcal = BMR / apsolutni minimum). " +
                            "Cilj je podignut na tu granicu — smanji tempo za realnu prognozu.",
                    style = AppTypography.bodySmall.copy(color = AppColors.Warning)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSaveProfile,
                enabled = !hasProfileError,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Primary,
                    disabledContainerColor = AppColors.SurfaceLight
                )
            ) {
                Text("Sačuvaj i primeni ciljeve")
            }
        }
    }
}

@Composable
private fun MacroTargetDisplay(
    label: String,
    value: String,
    unit: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = AppTypography.titleMedium.copy(color = color)
        )
        Text(
            text = unit,
            style = AppTypography.labelSmall
        )
        Text(
            text = label,
            style = AppTypography.labelSmall
        )
    }
}
