package com.kalorije.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.progressSemantics
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kalorije.app.data.db.dao.DateSummary
import com.kalorije.app.data.model.DailyGoals
import com.kalorije.app.ui.components.FoodEntryCard
import com.kalorije.app.ui.components.GlassCard
import com.kalorije.app.ui.components.GroupedFoodEntryCard
import com.kalorije.app.ui.components.ScreenHeader
import com.kalorije.app.ui.screens.history.HistoryViewModel
import com.kalorije.app.ui.theme.AppColors
import com.kalorije.app.ui.theme.AppDimens
import com.kalorije.app.ui.theme.AppTypography
import com.kalorije.app.util.getLogicalDate
import com.kalorije.app.util.groupEntriesIntoMeals
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    goals: DailyGoals,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit = {},
    onNavigateToAddMeal: (LocalDate) -> Unit = {},
    onDeleteEntry: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val logicalDate = getLogicalDate()
    var dayToDelete by remember { mutableStateOf<LocalDate?>(null) }

    dayToDelete?.let { date ->
        val daySummary = uiState.dateSummaries.find { it.date == date }
        val dayLabel = if (date == logicalDate) {
            "današnji dan"
        } else {
            date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM"))
        }
        val mealCount = uiState.mealCounts[date] ?: daySummary?.entryCount ?: 0

        AlertDialog(
            onDismissRequest = { dayToDelete = null },
            title = { Text("Obrisati ceo dan?", style = AppTypography.titleMedium) },
            text = {
                Text(
                    text = "Briše se $dayLabel - ${mealCountLabel(mealCount)}" +
                        (daySummary?.let { ", ${it.totalCalories.roundToInt()} kcal" } ?: "") +
                        ". Ovo se ne može poništiti.",
                    style = AppTypography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDay(date)
                        dayToDelete = null
                    }
                ) {
                    Text("Obriši", color = AppColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { dayToDelete = null }) {
                    Text("Odustani")
                }
            },
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .statusBarsPadding()
            .padding(AppDimens.paddingMedium)
    ) {
        ScreenHeader(title = "Istorija", onBack = onNavigateBack)

        Spacer(modifier = Modifier.height(AppDimens.paddingMedium))

        if (uiState.dateSummaries.isEmpty() && !uiState.isLoading) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(AppDimens.paddingMedium)
                ) {
                    Text(
                        text = "Nema sačuvanih obroka",
                        style = AppTypography.titleMedium
                    )
                    Text(
                        text = "Počni sa unosom hrane da bi istorija bila prikazana ovde.",
                        style = AppTypography.bodyMedium,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(AppDimens.paddingSmall)
            ) {
                itemsIndexed(
                    items = uiState.dateSummaries,
                    key = { _, summary -> summary.date.toString() },
                    contentType = { _, _ -> "day_summary" }
                ) { index, summary ->
                    val isExpanded = uiState.expandedDate == summary.date
                    val isToday = summary.date == logicalDate

                    SwipeToDeleteDay(onDeleteRequest = { dayToDelete = summary.date }) {
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.expandDate(summary.date) }
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = if (isToday) "Danas" else summary.date.format(
                                                DateTimeFormatter.ofPattern("EEEE, d. MMMM")
                                            ),
                                            style = AppTypography.titleMedium
                                        )
                                        Text(
                                            text = mealCountLabel(
                                                uiState.mealCounts[summary.date] ?: summary.entryCount
                                            ),
                                            style = AppTypography.labelSmall
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "${summary.totalCalories.roundToInt()} kcal",
                                                style = AppTypography.labelLarge.copy(color = AppColors.Calories)
                                            )
                                            Text(
                                                text = "od ${goals.calories}",
                                                style = AppTypography.labelSmall
                                            )
                                        }
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = if (isExpanded) "Skupi" else "Proširi",
                                            tint = AppColors.TextSecondary
                                        )
                                    }
                                }

                                // Macro summary red
                                DayCalorieProgress(
                                    current = summary.totalCalories,
                                    goal = goals.calories,
                                    modifier = Modifier.padding(top = 10.dp)
                                )

                                AnimatedVisibility(visible = isExpanded) {
                                    Column(
                                        modifier = Modifier.padding(top = AppDimens.paddingMedium),
                                        verticalArrangement = Arrangement.spacedBy(AppDimens.paddingSmall)
                                    ) {
                                        if (uiState.isLoadingEntries) {
                                            Box(
                                                modifier = Modifier.fillMaxWidth(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    color = AppColors.Primary,
                                                    strokeWidth = 2.dp
                                                )
                                            }
                                        } else {
                                            // Dugme za dodavanje obroka za ovaj dan
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { onNavigateToAddMeal(summary.date) }
                                                    .padding(vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Dodaj obrok",
                                                    tint = AppColors.Primary
                                                )
                                                Text(
                                                    text = "Dodaj obrok",
                                                    style = AppTypography.labelLarge.copy(color = AppColors.Primary),
                                                    modifier = Modifier.padding(start = 4.dp)
                                                )
                                            }
                                            val mealGroups = remember(uiState.expandedEntries) {
                                                groupEntriesIntoMeals(uiState.expandedEntries)
                                            }
                                            for (group in mealGroups) {
                                                if (group.entries.size > 1) {
                                                    GroupedFoodEntryCard(
                                                        entries = group.entries,
                                                        onDeleteGroup = {
                                                            group.entries.forEach { onDeleteEntry(it.id) }
                                                        },
                                                        onEditEntry = { entryId -> onNavigateToEdit(entryId) },
                                                        onDeleteEntry = { entryId -> onDeleteEntry(entryId) }
                                                    )
                                                } else {
                                                    val entry = group.entries.first()
                                                    FoodEntryCard(
                                                        entry = entry,
                                                        onDelete = { onDeleteEntry(entry.id) },
                                                        onClick = { onNavigateToEdit(entry.id) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Paginacija: kad se prikaže poslednji item, učitaj sledeću stranicu
                    if (index == uiState.dateSummaries.size - 1 && uiState.hasMore && !uiState.isLoading) {
                        LaunchedEffect(uiState.dateSummaries.size) {
                            viewModel.loadNextPage()
                        }
                    }
                }

                // Loading indikator na dnu liste
                if (uiState.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AppDimens.paddingMedium),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = AppColors.Primary,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCalorieProgress(
    current: Double,
    goal: Int,
    modifier: Modifier = Modifier
) {
    val progress = if (goal > 0) (current / goal).coerceIn(0.0, 1.0) else 0.0
    val animatedProgress by animateFloatAsState(
        targetValue = progress.toFloat(),
        animationSpec = tween(durationMillis = 500),
        label = "history_day_calorie_progress"
    )
    val currentCalories = current.roundToInt()
    val statusText = when {
        goal <= 0 -> "Bez cilja"
        current > goal -> "Preko cilja za ${(current - goal).roundToInt()} kcal"
        current >= goal * 0.9 -> "Blizu cilja"
        else -> "Ispod cilja za ${(goal - current).roundToInt()} kcal"
    }
    val statusColor = when {
        goal <= 0 -> AppColors.TextSecondary
        current > goal -> AppColors.Warning
        current >= goal * 0.9 -> AppColors.Calories
        else -> AppColors.Primary
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = statusText,
                style = AppTypography.labelMedium.copy(color = statusColor),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
            Text(
                text = if (goal > 0) "$currentCalories / $goal kcal" else "$currentCalories kcal",
                style = AppTypography.labelSmall
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .progressSemantics(progress.toFloat())
                .semantics {
                    stateDescription = if (goal > 0) {
                        "$statusText, $currentCalories od $goal kcal"
                    } else {
                        "$statusText, $currentCalories kcal"
                    }
                }
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(AppColors.SurfaceLight)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusColor)
            )
        }
    }
}

/**
 * Prevlačenjem ulevo otkriva se crvena pozadina; kad se pređe prag, traži se potvrda brisanja dana.
 */
@Composable
private fun SwipeToDeleteDay(
    onDeleteRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val maxDragPx = with(density) { 140.dp.toPx() }
    val thresholdPx = with(density) { 90.dp.toPx() }

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(AppDimens.cornerRadius))
                .background(AppColors.Error.copy(alpha = 0.15f)),
            contentAlignment = Alignment.CenterEnd
        ) {
            Row(
                modifier = Modifier.padding(end = AppDimens.paddingMedium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = AppColors.Error
                )
                Text(
                    text = "Obriši dan",
                    style = AppTypography.labelMedium.copy(color = AppColors.Error)
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offsetX.snapTo((offsetX.value + delta).coerceIn(-maxDragPx, 0f))
                        }
                    },
                    onDragStopped = {
                        if (offsetX.value <= -thresholdPx) onDeleteRequest()
                        offsetX.animateTo(0f)
                    }
                )
        ) {
            content()
        }
    }
}

/**
 * "1 obrok" / "2 obroka" / "15 obroka" - broji grupisane obroke, ne pojedinačne stavke.
 */
private fun mealCountLabel(count: Int): String {
    val suffix = if (count % 10 == 1 && count % 100 != 11) "obrok" else "obroka"
    return "$count $suffix"
}
