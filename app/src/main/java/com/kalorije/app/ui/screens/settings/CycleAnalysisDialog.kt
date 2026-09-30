package com.kalorije.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kalorije.app.data.model.CycleHistory
import com.kalorije.app.ui.theme.AppColors
import com.kalorije.app.ui.theme.AppTypography
import java.time.format.DateTimeFormatter

@Composable
fun CycleAnalysisDialog(
    cycle: CycleHistory,
    onDismiss: () -> Unit
) {
    val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = AppColors.Surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header with rating
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Analiza ciklusa",
                        style = AppTypography.titleLarge
                    )
                    Text(
                        text = "${cycle.cycleRating.emoji} ${cycle.cycleRating.displayName}",
                        style = AppTypography.titleMedium.copy(
                            color = when (cycle.cycleRating) {
                                com.kalorije.app.data.model.CycleRating.EXCELLENT -> AppColors.Success
                                com.kalorije.app.data.model.CycleRating.GOOD -> AppColors.Primary
                                com.kalorije.app.data.model.CycleRating.OKAY -> AppColors.Warning
                                com.kalorije.app.data.model.CycleRating.POOR -> AppColors.Error
                            }
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Duration
                Text(
                    text = "📅 Trajanje",
                    style = AppTypography.labelLarge
                )
                Text(
                    text = "${cycle.startDate.format(dateFormatter)} - ${cycle.endDate.format(dateFormatter)}",
                    style = AppTypography.bodyMedium
                )
                Text(
                    text = "${cycle.durationWeeks} nedelja (${cycle.durationDays} dana)",
                    style = AppTypography.bodySmall.copy(color = AppColors.TextSecondary)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Weight change
                Text(
                    text = "⚖️ Promena težine",
                    style = AppTypography.labelLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Start: ${String.format("%.1f", cycle.startWeight)} kg",
                            style = AppTypography.bodyMedium
                        )
                        Text(
                            text = "Kraj: ${String.format("%.1f", cycle.endWeight)} kg",
                            style = AppTypography.bodyMedium
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${if (cycle.weightChange >= 0) "+" else ""}${String.format("%.1f", cycle.weightChange)} kg",
                            style = AppTypography.titleMedium.copy(
                                color = if (cycle.isGain) AppColors.Success else AppColors.Error
                            )
                        )
                        Text(
                            text = "${String.format("%.2f", cycle.weeklyWeightChange)} kg/ned",
                            style = AppTypography.bodySmall.copy(color = AppColors.TextSecondary)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Water retention analysis
                Text(
                    text = "💧 Vodena retencija",
                    style = AppTypography.labelLarge
                )
                Text(
                    text = "Procenjena voda: ~${String.format("%.1f", cycle.estimatedWaterRetention)} kg",
                    style = AppTypography.bodyMedium.copy(color = AppColors.Primary)
                )
                Text(
                    text = "Procenjena suva težina: ${String.format("%.1f", cycle.estimatedDryEndWeight)} kg",
                    style = AppTypography.bodyMedium
                )
                Text(
                    text = "Stvarni dobitak (bez vode): ${if (cycle.estimatedLeanGain >= 0) "+" else ""}${String.format("%.1f", cycle.estimatedLeanGain)} kg",
                    style = AppTypography.bodyMedium.copy(
                        color = if (cycle.estimatedLeanGain >= 0) AppColors.Success else AppColors.Error
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Water loss timeline
                Text(
                    text = "📉 Očekivani gubitak vode:",
                    style = AppTypography.labelMedium
                )
                cycle.getWaterLossTimeline().forEach { (period, water) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = period,
                            style = AppTypography.bodySmall
                        )
                        Text(
                            text = if (water > 0) "~${String.format("%.1f", water)} kg vode" else "Normalizovano",
                            style = AppTypography.bodySmall.copy(
                                color = if (water > 0) AppColors.Primary else AppColors.Success
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "⚠️ Očekuj pad od ~${String.format("%.1f", cycle.estimatedWaterRetention)} kg u naredne 2-4 nedelje",
                    style = AppTypography.labelSmall.copy(color = AppColors.Warning)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Nutrition
                Text(
                    text = "🍽️ Ishrana (prosek)",
                    style = AppTypography.labelLarge
                )
                Text(
                    text = "Kalorije: ${cycle.avgDailyCalories} kcal/dan",
                    style = AppTypography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Suggestions
                Text(
                    text = "💡 Preporuke za sledeći ciklus",
                    style = AppTypography.labelLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                cycle.getImprovementSuggestions().forEach { suggestion ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "• ",
                            style = AppTypography.bodySmall
                        )
                        Text(
                            text = suggestion,
                            style = AppTypography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Close button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                ) {
                    Text("Zatvori")
                }
            }
        }
    }
}
