package com.kalorije.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kalorije.app.data.model.LiveCycleStats
import com.kalorije.app.data.model.PostCycleState
import com.kalorije.app.data.model.RecommendationType
import com.kalorije.app.data.model.UserProfile
import com.kalorije.app.ui.components.GlassCard
import com.kalorije.app.ui.components.appTextFieldColors
import com.kalorije.app.ui.theme.AppColors
import com.kalorije.app.ui.theme.AppDimens
import com.kalorije.app.ui.theme.AppTypography
import com.kalorije.app.util.WeightNormalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrtSection(
    useTrt: Boolean, onUseTrtChange: (Boolean) -> Unit,
    trtStartDate: LocalDate?, onTrtStartDateChange: (LocalDate?) -> Unit,
    trtCycleEndDate: LocalDate?, onTrtEndDateChange: (LocalDate?) -> Unit,
    trtDoseText: String, onTrtDoseChange: (String) -> Unit,
    usesAi: Boolean, onUsesAiChange: (Boolean) -> Unit,
    previewProfile: UserProfile?, liveCycleStats: LiveCycleStats?,
    onEndCycle: () -> Unit,
    showTrtDatePicker: Boolean, onShowTrtDatePickerChange: (Boolean) -> Unit,
    showEndDatePicker: Boolean, onShowEndDatePickerChange: (Boolean) -> Unit,
    postCycleState: PostCycleState? = null
) {
    Text(text = "TRT / AAS", style = AppTypography.titleMedium)
    Spacer(modifier = Modifier.height(AppDimens.paddingSmall))
    GlassCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Koristim testosteron", style = AppTypography.labelLarge)
                    Text("Prilagodi BMR za TRT/ciklus", style = AppTypography.labelSmall.copy(color = AppColors.TextMuted))
                }
                Switch(useTrt, onUseTrtChange, colors = SwitchDefaults.colors(checkedThumbColor = AppColors.Primary, checkedTrackColor = AppColors.Primary.copy(alpha = 0.5f)))
            }
            // Post-cycle stabilizacija
            if (!useTrt && postCycleState?.isActive == true) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(AppColors.Warning.copy(alpha = 0.1f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text("Post-cycle stabilizacija", style = AppTypography.labelLarge.copy(color = AppColors.Warning))
                    Spacer(Modifier.height(4.dp))
                    Text(postCycleState.statusMessage, style = AppTypography.bodySmall.copy(color = AppColors.TextSecondary))
                    Spacer(Modifier.height(4.dp))
                    Text("Preostalo: ${postCycleState.daysRemaining} dana", style = AppTypography.bodySmall.copy(color = AppColors.Warning))
                }
            }
            if (useTrt) {
                OutlinedTextField(trtStartDate?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) ?: "", {}, readOnly = true,
                    label = { Text("Početak ciklusa") }, modifier = Modifier.fillMaxWidth(), colors = appTextFieldColors(),
                    trailingIcon = { IconButton(onClick = { onShowTrtDatePickerChange(true) }) { Text("📅", style = AppTypography.titleMedium) } })
                OutlinedTextField(trtCycleEndDate?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) ?: "", {}, readOnly = true,
                    label = { Text("Planirani kraj ciklusa (opciono)") }, modifier = Modifier.fillMaxWidth(), colors = appTextFieldColors(),
                    trailingIcon = {
                        Row {
                            if (trtCycleEndDate != null) IconButton({ onTrtEndDateChange(null) }) { Icon(Icons.Default.Delete, "Obriši datum", tint = AppColors.Warning) }
                            IconButton({ onShowEndDatePickerChange(true) }) { Text("📅", style = AppTypography.titleMedium) }
                        }
                    })
                OutlinedTextField(trtDoseText, onTrtDoseChange, Modifier.fillMaxWidth(), label = { Text("Doza (mg/nedeljno)") },
                    placeholder = { Text("npr. 500") }, supportingText = { Text("Utiče na procenu vodene retencije") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = appTextFieldColors(), singleLine = true)
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Koristim AI", style = AppTypography.labelLarge)
                        Text("Aromatase inhibitor (smanjuje vodu ~30%)", style = AppTypography.labelSmall.copy(color = AppColors.TextMuted))
                    }
                    Switch(usesAi, onUsesAiChange, colors = SwitchDefaults.colors(checkedThumbColor = AppColors.Primary, checkedTrackColor = AppColors.Primary.copy(alpha = 0.5f)))
                }
                previewProfile?.weeksOnTrt?.let { weeks ->
                    Column(Modifier.fillMaxWidth().background(AppColors.Primary.copy(alpha = 0.1f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(12.dp)) {
                        val weeksText = when { weeks == 1 -> "nedelja"; weeks in 2..4 -> "nedelje"; else -> "nedelja" }
                        Text("⏱️ Na ciklusu: $weeks $weeksText", style = AppTypography.bodyMedium)
                        val weeksExact = (previewProfile.daysOnTrt ?: 0) / 7.0
                        val estimatedWater = WeightNormalizer.estimateWaterRetention(weeksExact, previewProfile.trtDoseMgWeek, previewProfile.usesAromataseInhibitor)
                        val (waterLow, _, waterHigh) = WeightNormalizer.estimateWaterRetentionRange(weeksExact, previewProfile.trtDoseMgWeek, previewProfile.usesAromataseInhibitor)
                        Spacer(Modifier.height(4.dp))
                        Text("💧 Procenjena voda: ~${String.format("%.1f", estimatedWater)} kg", style = AppTypography.bodySmall.copy(color = AppColors.Primary))
                        val confidencePct = (WeightNormalizer.confidenceMarginForWeek(weeks) * 100).toInt()
                        Text("Opseg: ${String.format("%.1f", waterLow)} - ${String.format("%.1f", waterHigh)} kg (±${confidencePct}%)", style = AppTypography.labelSmall.copy(color = if (weeks <= 3) AppColors.Warning else AppColors.TextMuted))
                        if (previewProfile.isInWaterRetentionPhase) {
                            Text("⚠️ Faza akumulacije (prve 4-6 nedelja)", style = AppTypography.labelSmall.copy(color = AppColors.Warning))
                        } else {
                            Text("✓ Stabilna faza - voda dostigla plato", style = AppTypography.labelSmall.copy(color = AppColors.Success))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("📈 BMR faktor: +${((previewProfile.trtMultiplier - 1) * 100).toInt()}%", style = AppTypography.bodySmall.copy(color = AppColors.Success))
                        previewProfile.weightGainedOnCycle?.let { gained ->
                            Spacer(Modifier.height(4.dp))
                            Text("⚖️ Promena: ${if (gained >= 0) "+" else ""}${String.format("%.1f", gained)} kg",
                                style = AppTypography.bodySmall.copy(color = if (gained >= 0) AppColors.Success else AppColors.Error))
                        }
                    }
                    liveCycleStats?.let { stats ->
                        Spacer(Modifier.height(12.dp))
                        Text("💡 Live preporuke", style = AppTypography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        stats.getLiveRecommendations().forEach { recommendation ->
                            Row(Modifier.fillMaxWidth().background(when (recommendation.type) {
                                    RecommendationType.SUCCESS -> AppColors.Success.copy(alpha = 0.1f)
                                    RecommendationType.INFO -> AppColors.Primary.copy(alpha = 0.1f)
                                    RecommendationType.WARNING -> AppColors.Warning.copy(alpha = 0.1f)
                                    RecommendationType.CRITICAL -> AppColors.Error.copy(alpha = 0.1f)
                                }, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(10.dp), verticalAlignment = Alignment.Top) {
                                Text(recommendation.emoji, style = AppTypography.titleMedium)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(recommendation.title, style = AppTypography.labelMedium.copy(color = when (recommendation.type) {
                                        RecommendationType.SUCCESS -> AppColors.Success; RecommendationType.INFO -> AppColors.Primary
                                        RecommendationType.WARNING -> AppColors.Warning; RecommendationType.CRITICAL -> AppColors.Error
                                    }))
                                    Text(recommendation.message, style = AppTypography.bodySmall, color = AppColors.TextSecondary)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${stats.avgDailyCalories}", style = AppTypography.titleMedium.copy(color = AppColors.Calories))
                                Text("kcal/dan", style = AppTypography.labelSmall)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onEndCycle, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AppColors.Warning)) {
                        Text("🏁 Završi ciklus i vidi analizu")
                    }
                }
            }
        }
    }

    @Composable
    fun TrtDatePickerDialog(show: Boolean, date: LocalDate?, onDateChange: (LocalDate?) -> Unit, onDismiss: () -> Unit) {
        if (show) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
            DatePickerDialog({ onDismiss() },
                confirmButton = { TextButton({ datePickerState.selectedDateMillis?.let { millis ->
                    onDateChange(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()) }; onDismiss() }) { Text("Potvrdi") } },
                dismissButton = { TextButton({ onDismiss() }) { Text("Otkaži") } }
            ) { DatePicker(state = datePickerState) }
        }
    }

    TrtDatePickerDialog(showTrtDatePicker, trtStartDate, onTrtStartDateChange) { onShowTrtDatePickerChange(false) }
    TrtDatePickerDialog(showEndDatePicker, trtCycleEndDate, onTrtEndDateChange) { onShowEndDatePickerChange(false) }
}
