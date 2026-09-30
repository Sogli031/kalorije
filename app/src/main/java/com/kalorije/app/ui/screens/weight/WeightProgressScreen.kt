package com.kalorije.app.ui.screens.weight

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kalorije.app.data.model.InsightType
import com.kalorije.app.data.model.WeeklyWeight
import com.kalorije.app.data.model.WeightEntry
import com.kalorije.app.data.model.WeightInsight
import com.kalorije.app.ui.components.CollapsiblePanel
import com.kalorije.app.ui.components.GlassCard
import com.kalorije.app.ui.components.ScreenHeader
import com.kalorije.app.ui.components.WeightLineChart
import com.kalorije.app.ui.theme.AppColors
import com.kalorije.app.ui.theme.AppDimens
import com.kalorije.app.ui.theme.AppTypography
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt
import com.kalorije.app.data.model.UserProfile
import com.kalorije.app.util.PartitionCalculator
import com.kalorije.app.util.PartitionEstimate
import com.kalorije.app.util.WeightNormalizer
import com.kalorije.app.util.WeightProjection

@Composable
fun WeightProgressScreen(
    onNavigateBack: () -> Unit,
    viewModel: WeightProgressViewModel = viewModel(factory = WeightProgressViewModel.Factory)
) {
    // State iz ViewModel-a
    val profile by viewModel.profile.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var newWeight by rememberSaveable { mutableStateOf("") }
    var goalWeightInput by remember(profile) { mutableStateOf(profile.goalWeight?.toString() ?: "") }
    var startingWeightInput by remember(profile) { mutableStateOf(profile.fixedStartingWeight?.toString() ?: "") }

    // Validation for weight entry
    val parsedWeight = newWeight.replace(",", ".").toDoubleOrNull()
    val weightRangeError = parsedWeight != null && parsedWeight !in 20.0..500.0

    // Vreme merenja - default je trenutno vreme
    var measurementTime by rememberSaveable {
        val now = LocalTime.now()
        mutableStateOf(String.format("%02d:%02d", now.hour, now.minute))
    }

    // Simulator state
    var simulatedCalories by rememberSaveable { mutableStateOf(profile.targetCalories.toString()) }

    // Dialog state
    var showResetDialog by remember { mutableStateOf(false) }
    var weightEntryToDelete by remember { mutableStateOf<LocalDate?>(null) }

    // Dialogs
    ResetProgressDialog(
        show = showResetDialog,
        onDismiss = { showResetDialog = false },
        onConfirm = {
            viewModel.resetWeightProgress()
            showResetDialog = false
            goalWeightInput = ""
            startingWeightInput = ""
        }
    )

    DeleteWeightEntryDialog(
        dateToDelete = weightEntryToDelete,
        onDismiss = { weightEntryToDelete = null },
        onConfirm = { date ->
            viewModel.deleteWeightEntry(date)
            weightEntryToDelete = null
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(AppDimens.paddingMedium)
        ) {
            ScreenHeader(title = "Napredak težine", onBack = onNavigateBack)

            Spacer(modifier = Modifier.height(AppDimens.paddingMedium))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(AppDimens.paddingMedium)
            ) {
                // Statistics Card
                item(contentType = "statistics") {
                    // Trenutna = nedeljni prosek (kad postoji važeća nedelja); promena i
                    // napredak ka cilju se računaju iz iste vrednosti da bi kartica bila dosledna.
                    val currentWeight = uiState.currentWeight
                    val startingWeight = profile.startingWeight
                    WeightStatisticsCard(
                        currentWeight = currentWeight,
                        currentWeightIsWeekly = uiState.currentWeightIsWeekly,
                        startingWeight = startingWeight,
                        totalChange = startingWeight?.let { currentWeight - it },
                        goalWeight = profile.goalWeight,
                        progressToGoal = profile.goalWeight?.let { goal ->
                            startingWeight?.let { start ->
                                val totalNeeded = goal - start
                                if (totalNeeded == 0.0) 1.0
                                else ((currentWeight - start) / totalNeeded).coerceIn(0.0, 1.0)
                            }
                        }
                    )
                }

                // Chart Card
                item(contentType = "chart") {
                    WeightChartSection(
                        sortedHistory = uiState.sortedHistory,
                        weeklyWeights = uiState.weeklyWeights,
                        goalWeight = profile.goalWeight
                    )
                }

                // Nedeljni prosek — stabilna "težina nedelje" umesto dnevnih oscilacija
                item(contentType = "weekly_average") {
                    WeeklyAverageCard(weeks = uiState.weeklyWeights)
                }

                // Weight Input Card
                item(contentType = "weight_input") {
                    WeightInputSection(
                        newWeight = newWeight,
                        onNewWeightChange = { newWeight = it },
                        measurementTime = measurementTime,
                        onMeasurementTimeChange = { input ->
                            val filtered = input.filter { it.isDigit() || it == ':' }.take(5)
                            measurementTime = filtered
                        },
                        parsedWeight = parsedWeight,
                        weightRangeError = weightRangeError,
                        onAddWeight = { weight, time ->
                            val validTime = WeightNormalizer.formatTime(
                                WeightNormalizer.parseTime(time) ?: LocalTime.now()
                            )
                            viewModel.addWeightEntry(weight, validTime)
                            newWeight = ""
                            val now = LocalTime.now()
                            measurementTime = String.format("%02d:%02d", now.hour, now.minute)
                        }
                    )
                }

                // Goal Weight Card
                item(contentType = "goal_weight") {
                    GoalWeightSection(
                        goalWeightInput = goalWeightInput,
                        onGoalWeightInputChange = { goalWeightInput = it },
                        onSaveGoalWeight = {
                            val goal = goalWeightInput.replace(",", ".").toDoubleOrNull()
                            viewModel.setGoalWeight(goal)
                        }
                    )
                }

                // Starting Weight Card
                item(contentType = "starting_weight") {
                    StartingWeightSection(
                        startingWeightInput = startingWeightInput,
                        onStartingWeightInputChange = { startingWeightInput = it },
                        onSaveStartingWeight = {
                            val weight = startingWeightInput.replace(",", ".").toDoubleOrNull()
                            if (weight != null) {
                                viewModel.setFixedStartingWeight(weight)
                            }
                        },
                        firstMeasuredWeight = profile.firstMeasuredWeight,
                        fixedStartingWeight = profile.fixedStartingWeight
                    )
                }

                // Weight Projection Card (pokazuje se samo ako je goal weight postavljen)
                profile.goalWeight?.let { goal ->
                    item {
                        WeightProjectionCard(
                            currentWeight = uiState.currentWeight,
                            goalWeight = goal,
                            tdee = profile.tdee,
                            tdeeKcalPerKg = profile.tdeeKcalPerKg,
                            avgDailyCalories = uiState.avgDailyCalories,
                            targetCalories = profile.targetCalories
                        )
                    }

                    // Calorie Simulator Card
                    item {
                        CalorieSimulatorCard(
                            currentWeight = uiState.currentWeight,
                            goalWeight = goal,
                            tdee = profile.tdee,
                            tdeeKcalPerKg = profile.tdeeKcalPerKg,
                            currentTargetCalories = profile.targetCalories,
                            simulatedCalories = simulatedCalories,
                            onSimulatedCaloriesChange = { simulatedCalories = it }
                        )
                    }
                }

                // Insights Card
                uiState.insight?.let { ins ->
                    item(key = "weight_insight", contentType = "insight") {
                        CollapsiblePanel(title = "Uvid") {
                            InsightCard(insight = ins)
                        }
                    }
                }

                // Learned TDEE Card (shown when we have enough data or manual TDEE)
                if (profile.trackingDays >= 7 || profile.manualTdee != null) {
                    item(key = "learned_tdee", contentType = "learned_tdee") {
                        CollapsiblePanel(title = "Naučeni TDEE") {
                            LearnedTdeeCard(
                                tdee = profile.tdee.toInt(),
                                baseTdee = profile.baseTdee.toInt(),
                                adjustment = profile.effectiveTdeeAdjustment,
                                confidenceText = profile.tdeeConfidenceText,
                                isConfident = profile.hasConfidentTdee,
                                tdeeSource = profile.tdeeSource,
                                isManualTdee = profile.manualTdee != null,
                                trtPhaseMessage = profile.trtTdeePhaseMessage
                            )
                        }
                    }
                }

                // TRT Water Retention Card - prikazuje se samo ako je na ciklusu
                if (profile.useTrt && profile.trtStartDate != null) {
                    uiState.trtData?.let { trt ->
                        item(key = "trt_card", contentType = "trt_card") {
                            CollapsiblePanel(title = "TRT voda") {
                                TrtWaterRetentionCard(
                                    weeksOnCycle = trt.weeksOnCycle,
                                    estimatedWater = trt.estimatedWater,
                                    waterLow = trt.waterLow,
                                    waterHigh = trt.waterHigh,
                                    dryWeight = trt.dryWeight,
                                    leanGain = trt.leanGain,
                                    waterPercent = trt.waterPercent,
                                    doseMgPerWeek = profile.trtDoseMgWeek,
                                    usesAi = profile.usesAromataseInhibitor,
                                    formulaWater = trt.formulaWater,
                                    totalChange = trt.totalChange,
                                    daysUntilCycleEnd = profile.daysUntilCycleEnd
                                )
                            }
                        }
                    }
                }

                // Partition Estimate Card - prikazuje se samo ako ima dobitka težine ≥0.5kg
                // i ako ima bar 1 nedelja praćenja
                uiState.partitionData?.let { pd ->
                    item(key = "partition_estimate", contentType = "partition_estimate") {
                        CollapsiblePanel(title = "Procena particije") {
                            PartitionEstimateCard(
                                estimate = pd.estimate,
                                totalWeightGain = pd.totalGain,
                                estimatedWater = pd.estimatedWater,
                                dryGain = pd.dryGain,
                                isOnTrt = profile.useTrt,
                                trackingWeeks = profile.trackingDays / 7,
                                // Iste vrednosti koje su ušle u kalkulaciju particije
                                resistanceTrainingDays = profile.resistanceTrainingDays,
                                surplusKcalPerDay = pd.surplusKcalPerDay
                            )
                        }
                    }
                }

                // Weight History
                weightHistoryItems(
                    sortedHistory = uiState.sortedHistory,
                    onDeleteEntry = { date -> weightEntryToDelete = date },
                    onRecalculateTdee = { viewModel.recalculateTdee() },
                    onShowResetDialog = { showResetDialog = true },
                    hasGoalWeight = profile.goalWeight != null,
                    hasFixedStartingWeight = profile.fixedStartingWeight != null,
                    isManualTdee = profile.manualTdee != null
                )

                // Bottom spacing
                item {
                    Spacer(modifier = Modifier.height(AppDimens.paddingLarge))
                }
            }
        }
    }
}

@Composable
private fun WeightStatisticsCard(
    currentWeight: Double,
    currentWeightIsWeekly: Boolean,
    startingWeight: Double?,
    totalChange: Double?,
    goalWeight: Double?,
    progressToGoal: Double?
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Starting Weight
                StatItem(
                    label = "Početna",
                    value = startingWeight?.let { String.format("%.2f kg", it) } ?: "-"
                )
                // Current Weight — nedeljni prosek kad postoji važeća nedelja
                StatItem(
                    label = if (currentWeightIsWeekly) "Trenutna (ned. prosek)" else "Trenutna",
                    value = String.format("%.2f kg", currentWeight),
                    valueColor = AppColors.Primary
                )
                // Change
                totalChange?.let { change ->
                    StatItem(
                        label = "Promena",
                        value = String.format("%+.2f kg", change),
                        valueColor = if (change < 0) AppColors.Error else if (change > 0) AppColors.Success else AppColors.TextPrimary
                    )
                }
            }

            // Goal progress
            goalWeight?.let { goal ->
                Spacer(modifier = Modifier.height(AppDimens.paddingMedium))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cilj: ${String.format("%.2f", goal)} kg",
                        style = AppTypography.bodyMedium
                    )
                    val remaining = goal - currentWeight
                    Text(
                        text = if (abs(remaining) < 0.1) "Cilj postignut!" else "Do cilja: ${String.format("%+.2f", remaining)} kg",
                        style = AppTypography.labelMedium.copy(
                            color = if (abs(remaining) < 0.1) AppColors.Success else AppColors.TextSecondary
                        )
                    )
                }

                progressToGoal?.let { progress ->
                    Spacer(modifier = Modifier.height(AppDimens.paddingSmall))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(AppColors.SurfaceLight, shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.toFloat())
                                .height(8.dp)
                                .background(AppColors.Success, shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    valueColor: Color = AppColors.TextPrimary
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = AppTypography.labelSmall
        )
        Text(
            text = value,
            style = AppTypography.titleMedium.copy(color = valueColor)
        )
    }
}

@Composable
private fun WeightHistoryItem(
    entry: WeightEntry,
    previousEntry: WeightEntry?,
    onDelete: () -> Unit
) {
    val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val change = previousEntry?.let { entry.weightKg - it.weightKg }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Date and time row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.date.format(dateFormatter),
                        style = AppTypography.bodyMedium
                    )
                    entry.time?.let { time ->
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = time,
                            style = AppTypography.labelSmall,
                            color = AppColors.TextMuted
                        )
                    }
                }

                // Weight row with change indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = String.format("%.2f kg", entry.weightKg),
                        style = AppTypography.titleMedium
                    )
                    change?.let { delta ->
                        if (abs(delta) >= 0.005) {
                            Spacer(modifier = Modifier.width(8.dp))
                            val isLoss = delta < 0
                            Icon(
                                imageVector = if (delta > 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                contentDescription = if (isLoss) "Smanjenje težine" else "Povećanje težine",
                                tint = if (isLoss) AppColors.Error else AppColors.Success,
                                modifier = Modifier.height(16.dp)
                            )
                            Text(
                                text = String.format("%+.2f", delta),
                                style = AppTypography.labelMedium.copy(
                                    color = if (isLoss) AppColors.Error else AppColors.Success
                                )
                            )
                        }
                    }
                }

            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Obriši",
                    tint = AppColors.TextMuted
                )
            }
        }
    }
}

@Composable
private fun LearnedTdeeCard(
    tdee: Int,
    baseTdee: Int,
    adjustment: Int,
    confidenceText: String,
    isConfident: Boolean,
    tdeeSource: String = "Formula",
    isManualTdee: Boolean = false,
    trtPhaseMessage: String? = null
) {
    val borderColor = when {
        isManualTdee -> AppColors.Primary
        isConfident -> AppColors.Success
        else -> AppColors.Primary
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tvoj TDEE",
                    style = AppTypography.titleMedium
                )
                Box(
                    modifier = Modifier
                        .background(
                            borderColor.copy(alpha = 0.15f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = confidenceText,
                        style = AppTypography.labelSmall,
                        color = borderColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppDimens.paddingSmall))

            // Main TDEE value
            Text(
                text = "$tdee kcal/dan",
                style = AppTypography.titleLarge.copy(
                    color = if (isConfident || isManualTdee) AppColors.Success else AppColors.Primary
                )
            )

            // Izvor TDEE-a
            Text(
                text = tdeeSource,
                style = AppTypography.labelSmall,
                color = AppColors.TextMuted
            )

            Spacer(modifier = Modifier.height(AppDimens.paddingSmall))

            // Breakdown (sakrij korekciju kad je ručno postavljen)
            if (!isManualTdee) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Formula",
                            style = AppTypography.labelSmall
                        )
                        Text(
                            text = "$baseTdee kcal",
                            style = AppTypography.bodySmall
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Korekcija",
                            style = AppTypography.labelSmall
                        )
                        Text(
                            text = if (adjustment >= 0) "+$adjustment kcal" else "$adjustment kcal",
                            style = AppTypography.bodySmall,
                            color = if (adjustment > 0) AppColors.Success else if (adjustment < 0) AppColors.Error else AppColors.TextSecondary
                        )
                    }
                }
            }

            if (isConfident && !isManualTdee) {
                Spacer(modifier = Modifier.height(AppDimens.paddingSmall))
                Text(
                    text = "Ovo je tvoj stvarni TDEE baziran na merenjima",
                    style = AppTypography.labelSmall,
                    color = AppColors.TextMuted
                )
            }

            // TRT faza poruka
            trtPhaseMessage?.let { message ->
                Spacer(modifier = Modifier.height(AppDimens.paddingSmall))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            AppColors.Primary.copy(alpha = 0.1f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                ) {
                    Text(
                        text = message,
                        style = AppTypography.bodySmall,
                        color = AppColors.Primary
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightCard(insight: WeightInsight) {
    val (backgroundColor, iconColor) = when (insight.type) {
        InsightType.ON_TRACK -> AppColors.Success.copy(alpha = 0.15f) to AppColors.Success
        InsightType.OVEREATING_EFFECT -> AppColors.Error.copy(alpha = 0.15f) to AppColors.Error
        InsightType.UNDEREATING_EFFECT -> AppColors.Warning.copy(alpha = 0.15f) to AppColors.Warning
        InsightType.TDEE_HIGHER -> AppColors.Primary.copy(alpha = 0.15f) to AppColors.Primary
        InsightType.TDEE_LOWER -> AppColors.Primary.copy(alpha = 0.15f) to AppColors.Primary
        InsightType.INSUFFICIENT_DATA -> AppColors.TextMuted.copy(alpha = 0.15f) to AppColors.TextMuted
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(backgroundColor, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = when (insight.type) {
                            InsightType.ON_TRACK -> Icons.AutoMirrored.Filled.TrendingUp
                            InsightType.OVEREATING_EFFECT -> Icons.AutoMirrored.Filled.TrendingUp
                            InsightType.UNDEREATING_EFFECT -> Icons.AutoMirrored.Filled.TrendingDown
                            InsightType.TDEE_HIGHER -> Icons.AutoMirrored.Filled.TrendingUp
                            InsightType.TDEE_LOWER -> Icons.AutoMirrored.Filled.TrendingDown
                            InsightType.INSUFFICIENT_DATA -> Icons.AutoMirrored.Filled.TrendingUp
                        },
                        contentDescription = null,
                        tint = iconColor
                    )
                }
                Text(
                    text = "Uvid",
                    style = AppTypography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.paddingSmall))

            Text(
                text = insight.message,
                style = AppTypography.bodyMedium,
                color = iconColor
            )

            Spacer(modifier = Modifier.height(AppDimens.paddingSmall))

            // Details row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Prosek unosa",
                        style = AppTypography.labelSmall
                    )
                    Text(
                        text = "${insight.avgCaloriesConsumed} kcal/dan",
                        style = AppTypography.bodySmall
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Preporuka",
                        style = AppTypography.labelSmall
                    )
                    Text(
                        text = "${insight.targetCalories} kcal/dan",
                        style = AppTypography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Stvarna promena",
                        style = AppTypography.labelSmall
                    )
                    Text(
                        text = String.format("%+.2f kg/ned", insight.actualWeeklyChange),
                        style = AppTypography.bodySmall
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Cilj",
                        style = AppTypography.labelSmall
                    )
                    Text(
                        text = String.format("%+.2f kg/ned", insight.targetWeeklyChange),
                        style = AppTypography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun TrtWaterRetentionCard(
    weeksOnCycle: Int,
    estimatedWater: Double,
    waterLow: Double,
    waterHigh: Double,
    dryWeight: Double,
    leanGain: Double,
    waterPercent: Double,
    doseMgPerWeek: Double,
    usesAi: Boolean,
    formulaWater: Double = estimatedWater,
    totalChange: Double = 0.0,
    daysUntilCycleEnd: Int? = null
) {
    val phaseInfo = when {
        weeksOnCycle <= 2 -> "Rana faza" to "Brza akumulacija vode (40-50% od max)"
        weeksOnCycle <= 4 -> "Srednja faza" to "Nastavak akumulacije (75-85% od max)"
        weeksOnCycle <= 6 -> "Kasna faza" to "Blizu stabilizacije (95% od max)"
        else -> "Plato" to "Voda dostigla maksimum"
    }

    val phaseColor = when {
        weeksOnCycle <= 2 -> AppColors.Warning
        weeksOnCycle <= 4 -> AppColors.Primary
        weeksOnCycle <= 6 -> AppColors.Primary
        else -> AppColors.Success
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💧 TRT Vodena Retencija",
                    style = AppTypography.titleMedium
                )
                Box(
                    modifier = Modifier
                        .background(
                            phaseColor.copy(alpha = 0.15f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Nedelja $weeksOnCycle",
                        style = AppTypography.labelMedium.copy(color = phaseColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Faza info
            Text(
                text = "${phaseInfo.first}: ${phaseInfo.second}",
                style = AppTypography.labelSmall.copy(color = phaseColor)
            )

            // Upozorenje za rane nedelje - neizvesnost je visoka
            if (weeksOnCycle <= 3) {
                Spacer(modifier = Modifier.height(8.dp))
                val confidencePct = (WeightNormalizer.confidenceMarginForWeek(weeksOnCycle) * 100).toInt()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            AppColors.Warning.copy(alpha = 0.15f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = "⚠️ Procena je neprecizna u ranim nedeljama (±${confidencePct}%). " +
                            "Fokusiraj se na trend posle 4+ nedelje za pouzdanije podatke.",
                        style = AppTypography.labelSmall.copy(color = AppColors.Warning)
                    )
                }
            }

            // Prikaz preostalih dana do kraja ciklusa
            daysUntilCycleEnd?.let { daysLeft ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (daysLeft > 0) "Do kraja ciklusa: $daysLeft dana"
                           else if (daysLeft == 0) "Danas je planirani kraj ciklusa!"
                           else "Ciklus je prošao planirani kraj za ${-daysLeft} dana",
                    style = AppTypography.bodyMedium,
                    color = if (daysLeft <= 7) AppColors.Warning else AppColors.TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.paddingMedium))

            // Main stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Estimated water
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Procenjena voda",
                        style = AppTypography.labelSmall
                    )
                    Text(
                        text = "~${String.format("%.1f", estimatedWater)} kg",
                        style = AppTypography.titleMedium.copy(color = AppColors.Primary)
                    )
                    val confidencePctDisplay = (WeightNormalizer.confidenceMarginForWeek(weeksOnCycle) * 100).toInt()
                    Text(
                        text = "${String.format("%.1f", waterLow)}-${String.format("%.1f", waterHigh)} kg (±${confidencePctDisplay}%)",
                        style = AppTypography.labelSmall.copy(
                            color = if (weeksOnCycle <= 3) AppColors.Warning else AppColors.TextMuted
                        )
                    )
                }

                // Dry weight
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Suva težina",
                        style = AppTypography.labelSmall
                    )
                    Text(
                        text = "${String.format("%.1f", dryWeight)} kg",
                        style = AppTypography.titleMedium.copy(color = AppColors.Success)
                    )
                    Text(
                        text = "bez vode",
                        style = AppTypography.labelSmall.copy(color = AppColors.TextMuted)
                    )
                }

                // Lean gain
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Stvarni dobitak",
                        style = AppTypography.labelSmall
                    )
                    Text(
                        text = "${if (leanGain >= 0) "+" else ""}${String.format("%.1f", leanGain)} kg",
                        style = AppTypography.titleMedium.copy(
                            color = if (leanGain >= 0) AppColors.Success else AppColors.Error
                        )
                    )
                    Text(
                        text = "mišići",
                        style = AppTypography.labelSmall.copy(color = AppColors.TextMuted)
                    )
                }
            }

            // Water percentage of total change
            if (waterPercent > 0) {
                Spacer(modifier = Modifier.height(AppDimens.paddingMedium))

                Text(
                    text = "Procenat vode u ukupnoj promeni",
                    style = AppTypography.labelSmall
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .background(
                            AppColors.SurfaceLight,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                        )
                ) {
                    // Water portion (blue)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((waterPercent / 100f).toFloat().coerceIn(0f, 1f))
                            .height(12.dp)
                            .background(
                                AppColors.Primary,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                            )
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "💧 Voda: ${String.format("%.0f", waterPercent)}%",
                        style = AppTypography.labelSmall.copy(color = AppColors.Primary)
                    )
                    Text(
                        text = "💪 Tkivo: ${String.format("%.0f", 100 - waterPercent)}%",
                        style = AppTypography.labelSmall.copy(color = AppColors.Success)
                    )
                }
            }

            // Napomena ako je formula korigovana
            if (formulaWater > estimatedWater && totalChange > 0) {
                Spacer(modifier = Modifier.height(AppDimens.paddingSmall))
                Text(
                    text = "ℹ️ Formula predviđa ${String.format("%.1f", formulaWater)} kg vode, ali si dobio samo ${String.format("%.1f", totalChange)} kg ukupno. Korigovano na osnovu kalorijskog viška.",
                    style = AppTypography.labelSmall.copy(color = AppColors.Warning)
                )
            }

            // Dose info
            Spacer(modifier = Modifier.height(AppDimens.paddingSmall))
            Text(
                text = "Doza: ${doseMgPerWeek.toInt()} mg/ned" + if (usesAi) " • AI: Da (-30% vode)" else "",
                style = AppTypography.labelSmall.copy(color = AppColors.TextMuted)
            )
        }
    }
}

@Composable
private fun WeightProjectionCard(
    currentWeight: Double,
    goalWeight: Double,
    tdee: Double,
    tdeeKcalPerKg: Double,
    avgDailyCalories: Int?,
    targetCalories: Int
) {
    // Use actual average calories if available, otherwise fall back to target
    val caloriesForProjection = avgDailyCalories ?: targetCalories
    val usingActualCalories = avgDailyCalories != null

    // Tempo prve nedelje (dalje se TDEE preračunava sa novom težinom)
    val dailySurplus = caloriesForProjection - tdee.roundToInt()
    val predictedWeeklyChange = (dailySurplus * 7) / UserProfile.KCAL_PER_KG

    val weightDifference = goalWeight - currentWeight
    val isGaining = weightDifference > 0

    // Check if goal is already reached
    val goalReached = abs(weightDifference) < 0.1

    val dateFormatter = DateTimeFormatter.ofPattern("dd.MM")

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = "Napredak do cilja",
                style = AppTypography.titleMedium
            )

            Spacer(modifier = Modifier.height(AppDimens.paddingSmall))

            // Current position - always shown in green
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        AppColors.Success.copy(alpha = 0.15f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📍 Sada",
                    style = AppTypography.bodyMedium,
                    color = AppColors.Success
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = String.format("%.2f kg", currentWeight),
                        style = AppTypography.titleMedium,
                        color = AppColors.Success
                    )
                    Text(
                        text = String.format("(do cilja: %+.2f)", goalWeight - currentWeight),
                        style = AppTypography.labelSmall,
                        color = AppColors.Success
                    )
                }
            }

            // If goal reached, show celebration
            if (goalReached) {
                Spacer(modifier = Modifier.height(AppDimens.paddingMedium))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            AppColors.Success.copy(alpha = 0.2f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎯 Cilj dostignut!",
                        style = AppTypography.titleMedium,
                        color = AppColors.Success
                    )
                }
                return@GlassCard
            }

            Spacer(modifier = Modifier.height(AppDimens.paddingSmall))

            // Info about projection calculation
            if (abs(predictedWeeklyChange) < 0.01) {
                Text(
                    text = "Unos ($caloriesForProjection kcal) ≈ TDEE (${tdee.toInt()} kcal) - težina stabilna",
                    style = AppTypography.labelSmall,
                    color = AppColors.TextMuted
                )
                return@GlassCard
            }

            val isCorrectDirection = (isGaining && predictedWeeklyChange > 0) || (!isGaining && predictedWeeklyChange < 0)

            // Info text
            val directionText = if (predictedWeeklyChange > 0) "dobijanja" else "mršavljenja"
            val changeText = String.format("%.2f", abs(predictedWeeklyChange))
            val calorieSource = if (usingActualCalories) "stvarnog unosa" else "cilja"
            Text(
                text = "Tempo: $changeText kg/ned $directionText",
                style = AppTypography.labelSmall,
                color = if (isCorrectDirection) AppColors.TextSecondary else AppColors.Warning
            )
            Text(
                text = "($caloriesForProjection kcal/dan iz $calorieSource)",
                style = AppTypography.labelSmall,
                color = AppColors.TextMuted
            )

            if (!isCorrectDirection) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "⚠️ Ideš u suprotnom smeru od cilja!",
                    style = AppTypography.labelSmall,
                    color = AppColors.Warning
                )
                return@GlassCard
            }

            Spacer(modifier = Modifier.height(AppDimens.paddingSmall))

            // Nedelje do cilja uz preračunavanje TDEE sa promenom težine
            val weeksToGoalRaw = remember(currentWeight, goalWeight, caloriesForProjection, tdee, tdeeKcalPerKg) {
                WeightProjection.weeksToGoal(
                    startWeight = currentWeight,
                    goalWeight = goalWeight,
                    dailyCalories = caloriesForProjection,
                    tdeeAtStart = tdee,
                    tdeeKcalPerKg = tdeeKcalPerKg
                )
            }
            val weeksToGoal = (weeksToGoalRaw ?: 52).coerceIn(1, 52)

            // Projection table - future weeks (memoized to avoid recomposition)
            val projectionData = remember(currentWeight, caloriesForProjection, tdee, tdeeKcalPerKg, weeksToGoal) {
                val weights = WeightProjection.project(
                    startWeight = currentWeight,
                    dailyCalories = caloriesForProjection,
                    tdeeAtStart = tdee,
                    tdeeKcalPerKg = tdeeKcalPerKg,
                    weeks = weeksToGoal.coerceAtMost(12)
                )
                weights.mapIndexed { index, projectedWeight ->
                    val week = index + 1
                    Triple(week, projectedWeight, LocalDate.now().plusWeeks(week.toLong()))
                }
            }

            // Find which week the user has reached based on current weight
            val reachedWeekIndex = projectionData.indexOfLast { (_, weight, _) ->
                if (isGaining) currentWeight >= weight else currentWeight <= weight
            }

            projectionData.forEachIndexed { index, (week, weight, date) ->
                val reachedGoal = if (isGaining) weight >= goalWeight else weight <= goalWeight
                val displayWeight = if (reachedGoal) goalWeight else weight

                // This week is green if user's current weight has reached or passed it
                val isCurrentWeek = index == reachedWeekIndex

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isCurrentWeek) Modifier.background(
                                AppColors.Success.copy(alpha = 0.15f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                            ) else Modifier
                        )
                        .padding(vertical = 4.dp, horizontal = if (isCurrentWeek) 8.dp else 0.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isCurrentWeek) "📍 Sed. $week (${date.format(dateFormatter)})" else "Sed. $week (${date.format(dateFormatter)})",
                        style = AppTypography.bodySmall,
                        color = if (isCurrentWeek) AppColors.Success else AppColors.TextSecondary
                    )

                    Text(
                        text = String.format("%.1f kg", displayWeight),
                        style = AppTypography.bodyMedium,
                        color = if (isCurrentWeek) AppColors.Success else AppColors.TextPrimary
                    )
                }

                if (reachedGoal) {
                    return@forEachIndexed
                }
            }

            // If goal is more than 12 weeks away
            if (weeksToGoalRaw == null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "📅 Pri ovom unosu težina se stabilizuje pre cilja — TDEE raste sa težinom. Koriguj unos.",
                    style = AppTypography.labelSmall,
                    color = AppColors.Warning
                )
            } else if (weeksToGoal > 12) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "📅 Procena: ~$weeksToGoal sedmica do cilja",
                    style = AppTypography.labelSmall,
                    color = AppColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun CalorieSimulatorCard(
    currentWeight: Double,
    goalWeight: Double,
    tdee: Double,
    tdeeKcalPerKg: Double,
    currentTargetCalories: Int,
    simulatedCalories: String,
    onSimulatedCaloriesChange: (String) -> Unit
) {
    val simCaloriesValue = simulatedCalories.replace(",", ".").toIntOrNull() ?: currentTargetCalories

    // Izračunaj simuliranu promjenu težine (tempo prve nedelje)
    val dailySurplus = simCaloriesValue - tdee.roundToInt()
    val simulatedWeeklyChange = (dailySurplus * 7) / UserProfile.KCAL_PER_KG

    val weightDifference = goalWeight - currentWeight
    val isGaining = weightDifference > 0
    val isCorrectDirection = (isGaining && simulatedWeeklyChange > 0) || (!isGaining && simulatedWeeklyChange < 0)

    // Sedmice do cilja — uz preračunavanje TDEE sa promenom težine
    val weeksToGoal = if (isCorrectDirection && abs(simulatedWeeklyChange) > 0.01) {
        WeightProjection.weeksToGoal(
            startWeight = currentWeight,
            goalWeight = goalWeight,
            dailyCalories = simCaloriesValue,
            tdeeAtStart = tdee,
            tdeeKcalPerKg = tdeeKcalPerKg
        )
    } else {
        null
    }
    
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = "🧪 Simulator kalorija",
                style = AppTypography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(AppDimens.paddingSmall))
            
            Text(
                text = "Pogledaj kako različit unos utiče na prognozu:",
                style = AppTypography.labelSmall,
                color = AppColors.TextSecondary
            )
            
            Spacer(modifier = Modifier.height(AppDimens.paddingMedium))
            
            // Input za kalorije
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.paddingSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = simulatedCalories,
                    onValueChange = onSimulatedCaloriesChange,
                    label = { Text("Dnevni unos (kcal)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.Primary,
                        unfocusedBorderColor = AppColors.GlassBorder,
                        focusedLabelColor = AppColors.Primary,
                        unfocusedLabelColor = AppColors.TextSecondary,
                        cursorColor = AppColors.Primary,
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary
                    )
                )
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "TDEE",
                        style = AppTypography.labelSmall,
                        color = AppColors.TextMuted
                    )
                    Text(
                        text = "${tdee.toInt()}",
                        style = AppTypography.bodySmall
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(AppDimens.paddingMedium))
            
            // Rezultati simulacije
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Dnevni suficit/deficit
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (dailySurplus >= 0) "Suficit" else "Deficit",
                        style = AppTypography.labelSmall,
                        color = AppColors.TextSecondary
                    )
                    Text(
                        text = "${if (dailySurplus >= 0) "+" else ""}$dailySurplus kcal",
                        style = AppTypography.bodyMedium,
                        color = if (dailySurplus > 0) AppColors.Warning else if (dailySurplus < 0) AppColors.Success else AppColors.TextPrimary
                    )
                }
                
                // Sedmična promena
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Promena/ned",
                        style = AppTypography.labelSmall,
                        color = AppColors.TextSecondary
                    )
                    Text(
                        text = String.format("%+.2f kg", simulatedWeeklyChange),
                        style = AppTypography.bodyMedium,
                        color = if (isCorrectDirection) AppColors.Success else AppColors.Error
                    )
                }
                
                // Vreme do cilja
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Do cilja",
                        style = AppTypography.labelSmall,
                        color = AppColors.TextSecondary
                    )
                    Text(
                        text = weeksToGoal?.let { "$it sed." } ?: "∞",
                        style = AppTypography.bodyMedium,
                        color = if (weeksToGoal != null) AppColors.Primary else AppColors.Error
                    )
                }
            }
            
            // Upozorenje ako ide u pogrešnom smeru
            if (!isCorrectDirection && abs(simulatedWeeklyChange) > 0.01) {
                Spacer(modifier = Modifier.height(AppDimens.paddingSmall))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            AppColors.Error.copy(alpha = 0.15f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                ) {
                    Text(
                        text = if (isGaining) "⚠️ Premali unos - dobićeš težinu sporije ili gubiš" 
                               else "⚠️ Preveliki unos - dobićeš težinu umesto da gubiš",
                        style = AppTypography.labelSmall,
                        color = AppColors.Error
                    )
                }
            }
            
            // Quick buttons za uobičajene deficite/suficite
            Spacer(modifier = Modifier.height(AppDimens.paddingMedium))
            
            Text(
                text = "Brzi izbor:",
                style = AppTypography.labelSmall,
                color = AppColors.TextMuted
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(-500, -250, 0, 250, 500).forEach { adjustment ->
                    val calories = tdee.toInt() + adjustment
                    val label = when {
                        adjustment < 0 -> "$adjustment"
                        adjustment > 0 -> "+$adjustment"
                        else -> "TDEE"
                    }
                    Button(
                        onClick = { onSimulatedCaloriesChange(calories.toString()) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (simCaloriesValue == calories) AppColors.Primary else AppColors.SurfaceLight
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = label,
                            style = AppTypography.labelSmall,
                            color = if (simCaloriesValue == calories) Color.White else AppColors.TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PartitionEstimateCard(
    estimate: PartitionEstimate,
    totalWeightGain: Double,
    estimatedWater: Double,
    dryGain: Double,
    isOnTrt: Boolean,
    trackingWeeks: Int,
    resistanceTrainingDays: Int,
    surplusKcalPerDay: Int
) {
    val isEarlyWarning = trackingWeeks < 4

    // Računaj procente za prikaz (uključujući vodu kad je na TRT-u)
    val waterPercent = if (totalWeightGain > 0) (estimatedWater / totalWeightGain * 100) else 0.0
    val musclePercent = if (totalWeightGain > 0) (estimate.muscleGainMid / totalWeightGain * 100) else 0.0
    val fatPercent = if (totalWeightGain > 0) (estimate.fatGainMid / totalWeightGain * 100) else 0.0

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📊 Procena kompozicije promene",
                    style = AppTypography.titleMedium
                )
                if (isEarlyWarning) {
                    Box(
                        modifier = Modifier
                            .background(
                                AppColors.Warning.copy(alpha = 0.15f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "⏳",
                            style = AppTypography.labelSmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppDimens.paddingSmall))

            // Ukupan dobitak
            Text(
                text = "Dobio si: +${String.format("%.1f", totalWeightGain)} kg",
                style = AppTypography.bodyMedium
            )

            Spacer(modifier = Modifier.height(AppDimens.paddingMedium))

            // Procena - različit prikaz za TRT i prirodne
            if (isOnTrt && estimatedWater > 0.1) {
                // TRT: Prikaži vodu, mišiće i mast
                PartitionTrtBreakdown(
                    estimatedWater = estimatedWater,
                    waterPercent = waterPercent,
                    musclePercent = musclePercent,
                    fatPercent = fatPercent,
                    estimate = estimate,
                    dryGain = dryGain
                )
            } else {
                // Prirodan: Samo mišići i mast
                PartitionNaturalBreakdown(estimate = estimate)
            }

            // Tips sekcija
            PartitionTipsSection(
                isEarlyWarning = isEarlyWarning,
                resistanceTrainingDays = resistanceTrainingDays,
                surplusKcalPerDay = surplusKcalPerDay
            )
        }
    }
}

@Composable
private fun WeightInputSection(
    newWeight: String,
    onNewWeightChange: (String) -> Unit,
    measurementTime: String,
    onMeasurementTimeChange: (String) -> Unit,
    parsedWeight: Double?,
    weightRangeError: Boolean,
    onAddWeight: (Double, String) -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = "Zabeleži težinu",
                style = AppTypography.titleMedium
            )
            Spacer(modifier = Modifier.height(AppDimens.paddingSmall))

            // Weight input row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.paddingSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newWeight,
                    onValueChange = onNewWeightChange,
                    label = { Text("Težina (kg)") },
                    placeholder = { Text("npr. 81.35", color = AppColors.TextMuted) },
                    isError = weightRangeError,
                    supportingText = if (weightRangeError) {
                        { Text("Težina mora biti između 20 i 500 kg", color = AppColors.Error) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.Primary,
                        unfocusedBorderColor = AppColors.GlassBorder,
                        focusedLabelColor = AppColors.Primary,
                        unfocusedLabelColor = AppColors.TextSecondary,
                        cursorColor = AppColors.Primary,
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary,
                        errorBorderColor = AppColors.Error,
                        errorLabelColor = AppColors.Error
                    )
                )
                OutlinedTextField(
                    value = measurementTime,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() || it == ':' }.take(5)
                        onMeasurementTimeChange(filtered)
                    },
                    label = { Text("Vreme") },
                    singleLine = true,
                    modifier = Modifier.width(90.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.Primary,
                        unfocusedBorderColor = AppColors.GlassBorder,
                        focusedLabelColor = AppColors.Primary,
                        unfocusedLabelColor = AppColors.TextSecondary,
                        cursorColor = AppColors.Primary,
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.paddingSmall))

            Button(
                onClick = {
                    parsedWeight?.let { weight ->
                        // Safety net: validate range before saving
                        if (weight in 20.0..500.0) {
                            // Validiraj vreme
                            val time = WeightNormalizer.parseTime(measurementTime) ?: LocalTime.now()
                            val validTime = WeightNormalizer.formatTime(time)
                            onAddWeight(weight, validTime)
                        }
                    }
                },
                enabled = parsedWeight != null && !weightRangeError,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Primary,
                    disabledContainerColor = AppColors.SurfaceLight
                )
            ) {
                Text("Zabeleži")
            }
        }
    }
}

@Composable
private fun GoalWeightSection(
    goalWeightInput: String,
    onGoalWeightInputChange: (String) -> Unit,
    onSaveGoalWeight: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = "Ciljna težina",
                style = AppTypography.titleMedium
            )
            Spacer(modifier = Modifier.height(AppDimens.paddingSmall))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.paddingSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = goalWeightInput,
                    onValueChange = onGoalWeightInputChange,
                    label = { Text("Cilj (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.Primary,
                        unfocusedBorderColor = AppColors.GlassBorder,
                        focusedLabelColor = AppColors.Primary,
                        unfocusedLabelColor = AppColors.TextSecondary,
                        cursorColor = AppColors.Primary,
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary
                    )
                )
                Button(
                    onClick = onSaveGoalWeight,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Primary
                    )
                ) {
                    Text("Sačuvaj")
                }
            }
        }
    }
}

@Composable
private fun StartingWeightSection(
    startingWeightInput: String,
    onStartingWeightInputChange: (String) -> Unit,
    onSaveStartingWeight: () -> Unit,
    firstMeasuredWeight: Double?,
    fixedStartingWeight: Double?
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = "Početna težina",
                style = AppTypography.titleMedium
            )
            Spacer(modifier = Modifier.height(AppDimens.paddingSmall))
            Text(
                text = "Postavi početnu težinu za praćenje napretka.",
                style = AppTypography.labelSmall,
                color = AppColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(AppDimens.paddingSmall))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.paddingSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = startingWeightInput,
                    onValueChange = onStartingWeightInputChange,
                    label = { Text("Težina (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.Primary,
                        unfocusedBorderColor = AppColors.GlassBorder,
                        focusedLabelColor = AppColors.Primary,
                        unfocusedLabelColor = AppColors.TextSecondary,
                        cursorColor = AppColors.Primary,
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary
                    )
                )
                Button(
                    onClick = onSaveStartingWeight,
                    enabled = startingWeightInput.replace(",", ".").toDoubleOrNull() != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Primary,
                        disabledContainerColor = AppColors.SurfaceLight
                    )
                ) {
                    Text("Sačuvaj")
                }
            }
            // Show info if fixed starting weight is different from first measurement
            firstMeasuredWeight?.let { firstMeasured ->
                fixedStartingWeight?.let { fixed ->
                    if (abs(firstMeasured - fixed) > 0.01) {
                        Spacer(modifier = Modifier.height(AppDimens.paddingSmall))
                        Text(
                            text = "Prvo merenje: ${String.format("%.2f", firstMeasured)} kg",
                            style = AppTypography.labelSmall,
                            color = AppColors.TextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeightChartSection(
    sortedHistory: List<WeightEntry>,
    weeklyWeights: List<WeeklyWeight>,
    goalWeight: Double?
) {
    // Važeće nedelje kao tačke na grafikonu — datum tačke je ponedeljak te nedelje
    val weeklyEntries = remember(weeklyWeights) {
        weeklyWeights
            .filter { it.isValid }
            .map { WeightEntry(date = it.weekStart, weightKg = it.averageKg) }
    }
    // Čim postoje bar 2 važeće nedelje, nedeljni prikaz je podrazumevani (to je "prava" težina)
    val hasEnoughWeekly = weeklyEntries.size >= 2
    var showWeekly by rememberSaveable(hasEnoughWeekly) { mutableStateOf(hasEnoughWeekly) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Grafikon težine",
                    style = AppTypography.titleMedium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChartModeChip(label = "Dnevno", selected = !showWeekly) { showWeekly = false }
                    Spacer(modifier = Modifier.width(6.dp))
                    ChartModeChip(label = "Nedeljno", selected = showWeekly) { showWeekly = true }
                }
            }
            Spacer(modifier = Modifier.height(AppDimens.paddingSmall))
            when {
                sortedHistory.isEmpty() -> Text(
                    text = "Nema merenja",
                    style = AppTypography.bodyMedium
                )
                showWeekly && weeklyEntries.isEmpty() -> Text(
                    text = "Još nema nedelje sa bar ${WeeklyWeight.MIN_MEASURED_DAYS} merenja",
                    style = AppTypography.bodyMedium,
                    color = AppColors.TextSecondary
                )
                showWeekly -> WeightLineChart(
                    entries = weeklyEntries,
                    goalWeight = goalWeight
                )
                else -> WeightLineChart(
                    entries = sortedHistory,
                    goalWeight = goalWeight
                )
            }
        }
    }
}

@Composable
private fun ChartModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label, style = AppTypography.labelMedium) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = AppColors.TextSecondary,
            selectedContainerColor = AppColors.Primary.copy(alpha = 0.2f),
            selectedLabelColor = AppColors.Primary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = AppColors.GlassBorder,
            selectedBorderColor = AppColors.Primary
        )
    )
}

/**
 * Kartica sa nedeljnim prosecima (7-dnevni periodi od prvog merenja).
 * Najnovija važeća nedelja se ističe, sve nedelje su u sklopivoj listi.
 */
@Composable
private fun WeeklyAverageCard(weeks: List<WeeklyWeight>) {
    val rangeFormatter = remember { DateTimeFormatter.ofPattern("dd.MM") }
    val validWeeks = remember(weeks) { weeks.filter { it.isValid } } // najnovija prva
    val latestValid = validWeeks.firstOrNull()
    val previousValid = validWeeks.getOrNull(1)

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = "Nedeljni prosek",
                style = AppTypography.titleMedium
            )
            Text(
                text = "Nedelja = 7 dana od prvog merenja (dan 1–7, 8–14...). Važi ako ima bar ${WeeklyWeight.MIN_MEASURED_DAYS} merenja.",
                style = AppTypography.labelSmall,
                color = AppColors.TextMuted
            )
            Spacer(modifier = Modifier.height(AppDimens.paddingSmall))

            if (weeks.isEmpty()) {
                Text(
                    text = "Nema merenja — meri se svakog jutra, prosek se računa sam.",
                    style = AppTypography.bodyMedium,
                    color = AppColors.TextSecondary
                )
                return@Column
            }

            if (latestValid != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = formatWeekRange(latestValid, rangeFormatter) +
                                if (latestValid.isComplete) "" else "  ·  u toku",
                            style = AppTypography.labelSmall,
                            color = AppColors.TextSecondary
                        )
                        Text(
                            text = String.format("%.2f kg", latestValid.averageKg),
                            style = AppTypography.titleLarge.copy(color = AppColors.Primary)
                        )
                        Text(
                            text = "${latestValid.daysMeasured}/7 merenja",
                            style = AppTypography.labelSmall,
                            color = AppColors.TextMuted
                        )
                    }
                    previousValid?.let { prev ->
                        val delta = latestValid.averageKg - prev.averageKg
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "vs prošla važeća",
                                style = AppTypography.labelSmall,
                                color = AppColors.TextSecondary
                            )
                            Text(
                                text = String.format("%+.2f kg", delta),
                                style = AppTypography.titleMedium.copy(color = deltaColor(delta))
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Još nema nedelje sa bar ${WeeklyWeight.MIN_MEASURED_DAYS} merenja.",
                    style = AppTypography.bodyMedium,
                    color = AppColors.TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.paddingSmall))
            CollapsiblePanel(title = "Sve nedelje (${weeks.size})") {
                Column(verticalArrangement = Arrangement.spacedBy(AppDimens.paddingSmall)) {
                    weeks.forEachIndexed { index, week ->
                        // Promena se računa prema prethodnoj VAŽEĆOJ nedelji, nevažeće se preskaču
                        val prevValid = weeks.drop(index + 1).firstOrNull { it.isValid }
                        WeeklyWeightRow(
                            week = week,
                            previousValid = prevValid,
                            rangeFormatter = rangeFormatter
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyWeightRow(
    week: WeeklyWeight,
    previousValid: WeeklyWeight?,
    rangeFormatter: DateTimeFormatter
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = formatWeekRange(week, rangeFormatter),
                style = AppTypography.bodyMedium
            )
            Text(
                text = "${week.daysMeasured}/7 merenja" + if (week.isComplete) "" else "  ·  u toku",
                style = AppTypography.labelSmall,
                color = AppColors.TextMuted
            )
        }
        if (week.isValid) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                previousValid?.let { prev ->
                    val delta = week.averageKg - prev.averageKg
                    if (abs(delta) >= 0.005) {
                        Text(
                            text = String.format("%+.2f", delta),
                            style = AppTypography.labelMedium.copy(color = deltaColor(delta))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
                Text(
                    text = String.format("%.2f kg", week.averageKg),
                    style = AppTypography.titleMedium
                )
            }
        } else {
            Text(
                text = "ne važi",
                style = AppTypography.labelMedium.copy(color = AppColors.Warning)
            )
        }
    }
}

private fun formatWeekRange(week: WeeklyWeight, formatter: DateTimeFormatter): String =
    "${week.weekStart.format(formatter)} – ${week.weekEnd.format(formatter)}"

private fun deltaColor(delta: Double): Color = when {
    delta < -0.05 -> AppColors.Error
    delta > 0.05 -> AppColors.Success
    else -> AppColors.TextPrimary
}

private fun androidx.compose.foundation.lazy.LazyListScope.weightHistoryItems(
    sortedHistory: List<WeightEntry>,
    onDeleteEntry: (LocalDate) -> Unit,
    onRecalculateTdee: () -> Unit,
    onShowResetDialog: () -> Unit,
    hasGoalWeight: Boolean,
    hasFixedStartingWeight: Boolean,
    isManualTdee: Boolean = false
) {
    // History Header
    if (sortedHistory.isNotEmpty()) {
        item {
            Text(
                text = "Istorija merenja (${sortedHistory.size})",
                style = AppTypography.titleMedium
            )
        }
    }

    // History Items
    itemsIndexed(
        items = sortedHistory,
        key = { index, entry -> entry.date.toString() + "_" + (entry.time ?: "no_time") },
        contentType = { _, _ -> "weight_history_item" }
    ) { index, entry ->
        val previousEntry = if (index < sortedHistory.size - 1) sortedHistory[index + 1] else null
        WeightHistoryItem(
            entry = entry,
            previousEntry = previousEntry,
            onDelete = { onDeleteEntry(entry.date) }
        )
    }

    // TDEE Recalculation Button
    if (sortedHistory.size >= 2) {
        item {
            Spacer(modifier = Modifier.height(AppDimens.paddingSmall))
            Button(
                onClick = onRecalculateTdee,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isManualTdee,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.SurfaceLight,
                    disabledContainerColor = AppColors.SurfaceLight.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = if (isManualTdee) "TDEE zaključan (ručno postavljen)" else "Ponovo izračunaj TDEE iz istorije",
                    color = if (isManualTdee) AppColors.TextMuted else AppColors.TextPrimary
                )
            }
        }
    }

    // Reset Progress Button
    if (sortedHistory.isNotEmpty() || hasGoalWeight || hasFixedStartingWeight) {
        item {
            Spacer(modifier = Modifier.height(AppDimens.paddingMedium))
            Button(
                onClick = onShowResetDialog,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Error.copy(alpha = 0.15f)
                )
            ) {
                Text(
                    text = "Resetuj napredak težine",
                    color = AppColors.Error
                )
            }
        }
    }
}

@Composable
private fun ResetProgressDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = "Reset napretka", style = AppTypography.titleMedium) },
            text = {
                Text(
                    text = "Da li si siguran da želiš da obrišeš sve podatke o napretku težine?\n\nOvo uključuje:\n• Istoriju merenja\n• Ciljnu težinu\n• Početnu težinu\n• TDEE korekciju\n\nOva akcija se ne može poništiti.",
                    style = AppTypography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Error)
                ) {
                    Text("Obriši sve")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Odustani", color = AppColors.TextSecondary)
                }
            },
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary
        )
    }
}

@Composable
private fun DeleteWeightEntryDialog(
    dateToDelete: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    dateToDelete?.let { date ->
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = "Obriši merenje", style = AppTypography.titleMedium) },
            text = {
                Text(
                    text = "Da li si siguran da želiš da obrišeš merenje za ${date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}?",
                    style = AppTypography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { onConfirm(date) },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Error)
                ) {
                    Text("Obriši")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Odustani", color = AppColors.TextSecondary)
                }
            },
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary
        )
    }
}

@Composable
private fun PartitionTrtBreakdown(
    estimatedWater: Double,
    waterPercent: Double,
    musclePercent: Double,
    fatPercent: Double,
    estimate: PartitionEstimate,
    dryGain: Double
) {
    // Row sa 3 kolone (Voda / Mišići / Mast)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Voda
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "💧 Voda",
                style = AppTypography.labelMedium
            )
            Text(
                text = "~${String.format("%.1f", estimatedWater)} kg",
                style = AppTypography.titleMedium.copy(color = AppColors.Primary)
            )
            Text(
                text = "${String.format("%.0f", waterPercent)}%",
                style = AppTypography.labelSmall.copy(color = AppColors.TextMuted)
            )
        }

        // Mišići
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "💪 Mišići",
                style = AppTypography.labelMedium
            )
            Text(
                text = "${String.format("%.1f", estimate.muscleGainMin)} - ${String.format("%.1f", estimate.muscleGainMax)} kg",
                style = AppTypography.titleMedium.copy(color = AppColors.Success)
            )
            Text(
                text = "${String.format("%.0f", musclePercent)}%",
                style = AppTypography.labelSmall.copy(color = AppColors.TextMuted)
            )
        }

        // Mast
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "🔥 Mast",
                style = AppTypography.labelMedium
            )
            Text(
                text = "${String.format("%.1f", estimate.fatGainMin)} - ${String.format("%.1f", estimate.fatGainMax)} kg",
                style = AppTypography.titleMedium.copy(color = AppColors.Warning)
            )
            Text(
                text = "${String.format("%.0f", fatPercent)}%",
                style = AppTypography.labelSmall.copy(color = AppColors.TextMuted)
            )
        }
    }

    Spacer(modifier = Modifier.height(AppDimens.paddingSmall))

    // Napomena o suvoj težini
    Text(
        text = "Mišići i mast su iz suve težine (+${String.format("%.1f", dryGain)} kg)",
        style = AppTypography.labelSmall.copy(color = AppColors.TextMuted)
    )

    Spacer(modifier = Modifier.height(AppDimens.paddingMedium))

    // Vizuelna particija sa 3 dela (voda, mišići, mast)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp)
            .background(
                AppColors.SurfaceLight,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Voda (plavo)
            if (waterPercent > 0) {
                Box(
                    modifier = Modifier
                        .weight((waterPercent / 100f).toFloat().coerceIn(0.01f, 1f))
                        .height(16.dp)
                        .background(
                            AppColors.Primary,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                topStart = 8.dp, bottomStart = 8.dp,
                                topEnd = 0.dp, bottomEnd = 0.dp
                            )
                        )
                )
            }
            // Mišići (zeleno)
            if (musclePercent > 0) {
                Box(
                    modifier = Modifier
                        .weight((musclePercent / 100f).toFloat().coerceIn(0.01f, 1f))
                        .height(16.dp)
                        .background(AppColors.Success)
                )
            }
            // Mast (narandžasto) - ostatak
            if (fatPercent > 0) {
                Box(
                    modifier = Modifier
                        .weight((fatPercent / 100f).toFloat().coerceIn(0.01f, 1f))
                        .height(16.dp)
                        .background(
                            AppColors.Warning,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                topStart = 0.dp, bottomStart = 0.dp,
                                topEnd = 8.dp, bottomEnd = 8.dp
                            )
                        )
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    // Procenat Row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "💧${String.format("%.0f", waterPercent)}%",
            style = AppTypography.labelSmall.copy(color = AppColors.Primary)
        )
        Text(
            text = "💪${String.format("%.0f", musclePercent)}%",
            style = AppTypography.labelSmall.copy(color = AppColors.Success)
        )
        Text(
            text = "🔥${String.format("%.0f", fatPercent)}%",
            style = AppTypography.labelSmall.copy(color = AppColors.Warning)
        )
    }
}

@Composable
private fun PartitionNaturalBreakdown(
    estimate: PartitionEstimate
) {
    // Row sa 2 kolone (Mišići / Mast)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Mišići
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "💪 Mišići",
                style = AppTypography.labelMedium
            )
            Text(
                text = "${String.format("%.1f", estimate.muscleGainMin)} - ${String.format("%.1f", estimate.muscleGainMax)} kg",
                style = AppTypography.titleMedium.copy(color = AppColors.Success)
            )
            Text(
                text = "${String.format("%.0f", estimate.musclePercentMin)}-${String.format("%.0f", estimate.musclePercentMax)}%",
                style = AppTypography.labelSmall.copy(color = AppColors.TextMuted)
            )
        }

        // Mast
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "🔥 Mast",
                style = AppTypography.labelMedium
            )
            Text(
                text = "${String.format("%.1f", estimate.fatGainMin)} - ${String.format("%.1f", estimate.fatGainMax)} kg",
                style = AppTypography.titleMedium.copy(color = AppColors.Warning)
            )
            Text(
                text = "${String.format("%.0f", 100 - estimate.musclePercentMax)}-${String.format("%.0f", 100 - estimate.musclePercentMin)}%",
                style = AppTypography.labelSmall.copy(color = AppColors.TextMuted)
            )
        }
    }

    Spacer(modifier = Modifier.height(AppDimens.paddingMedium))

    // Vizuelna particija (progress bar) - samo mišići i mast
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp)
            .background(
                AppColors.SurfaceLight,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth((estimate.musclePercentMid / 100f).toFloat().coerceIn(0f, 1f))
                .height(16.dp)
                .background(
                    AppColors.Success,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(
                        topStart = 8.dp, bottomStart = 8.dp,
                        topEnd = 0.dp, bottomEnd = 0.dp
                    )
                )
        )
    }

    Spacer(modifier = Modifier.height(4.dp))

    // Procenat Row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "💪 ${String.format("%.0f", estimate.musclePercentMid)}%",
            style = AppTypography.labelSmall.copy(color = AppColors.Success)
        )
        Text(
            text = "🔥 ${String.format("%.0f", 100 - estimate.musclePercentMid)}%",
            style = AppTypography.labelSmall.copy(color = AppColors.Warning)
        )
    }
}

@Composable
private fun PartitionTipsSection(
    isEarlyWarning: Boolean,
    resistanceTrainingDays: Int,
    surplusKcalPerDay: Int
) {
    // Early warning
    if (isEarlyWarning) {
        Spacer(modifier = Modifier.height(AppDimens.paddingSmall))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    AppColors.Warning.copy(alpha = 0.1f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                )
                .padding(8.dp)
        ) {
            Text(
                text = "⚠️ Manje od 4 nedelje praćenja. Procena će biti tačnija sa više podataka.",
                style = AppTypography.labelSmall.copy(color = AppColors.Warning)
            )
        }
    }

    Spacer(modifier = Modifier.height(AppDimens.paddingSmall))

    // Napomena o nesigurnosti
    Text(
        text = "⚠️ Procena - individualne varijacije mogu biti ±25-40%",
        style = AppTypography.labelSmall.copy(color = AppColors.TextMuted)
    )

    // Saveti za poboljšanje
    val tips = PartitionCalculator.getImprovementTips(
        resistanceTrainingDays = resistanceTrainingDays,
        surplusKcalPerDay = surplusKcalPerDay
    )

    if (tips.isNotEmpty() && tips.first() != "Odlični parametri! Nastavi ovako za optimalnu particiju.") {
        Spacer(modifier = Modifier.height(AppDimens.paddingSmall))
        Text(
            text = "💡 Saveti za bolji odnos:",
            style = AppTypography.labelMedium
        )
        tips.forEach { tip ->
            Text(
                text = "• $tip",
                style = AppTypography.labelSmall.copy(color = AppColors.TextSecondary)
            )
        }
    } else {
        Spacer(modifier = Modifier.height(AppDimens.paddingSmall))
        Text(
            text = "✅ ${tips.first()}",
            style = AppTypography.labelSmall.copy(color = AppColors.Success)
        )
    }
}
