package com.kalorije.app.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kalorije.app.data.model.DailySummary
import com.kalorije.app.data.repository.FoodRepository
import com.kalorije.app.ui.components.CalorieRingCard
import com.kalorije.app.ui.components.FoodEntryCard
import com.kalorije.app.ui.components.GlassCard
import com.kalorije.app.ui.components.GroupedFoodEntryCard
import com.kalorije.app.ui.components.ScreenHeader
import com.kalorije.app.ui.theme.AppColors
import com.kalorije.app.ui.theme.AppDimens
import com.kalorije.app.ui.theme.AppTypography
import com.kalorije.app.util.getLogicalDate
import com.kalorije.app.util.groupEntriesIntoMeals
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@Composable
fun HomeScreen(
    repository: FoodRepository,
    onNavigateToManualEntry: () -> Unit,
    onNavigateToShakeEntry: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    onNavigateToSavedFoods: () -> Unit
) {
    val entries by repository.entries.collectAsState()
    val goals by repository.goals.collectAsState()
    val profile by repository.profile.collectAsState()

    val today = getLogicalDate()
    val todayEntries = remember(entries, today) {
        entries.filter { it.date == today }
    }
    val summary = remember(todayEntries) {
        DailySummary.fromEntries(todayEntries)
    }

    // Tekuca nedelja: ponedeljak — nedelja
    val weekStart = remember(today) {
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
    val weeklyCalories = remember(entries, weekStart) {
        val weekEnd = weekStart.plusDays(6)
        entries.filter { it.date >= weekStart && it.date <= weekEnd }
            .sumOf { it.calories }
    }



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
            ScreenHeader(
                title = "Kalorije",
                subtitle = today.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM"))
            ) {
                IconButton(onClick = onNavigateToSavedFoods) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = "Sačuvana jela",
                        tint = AppColors.TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppDimens.paddingMedium))

            CalorieRingCard(
                current = summary.calories,
                goal = goals.calories,
                goalIsEstimate = profile.useTrt && profile.trtTdeePhase < 3,
                weeklyCurrent = weeklyCalories,
                weekStart = weekStart,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(AppDimens.paddingMedium))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.paddingSmall)
            ) {
                Button(
                    onClick = onNavigateToManualEntry,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                    Text(
                        text = "Obrok",
                        modifier = Modifier.padding(start = AppDimens.paddingSmall)
                    )
                }

                Button(
                    onClick = onNavigateToShakeEntry,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.SurfaceRaised,
                        contentColor = AppColors.TextPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalDrink,
                        contentDescription = null
                    )
                    Text(
                        text = "Šejk",
                        modifier = Modifier.padding(start = AppDimens.paddingSmall)
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppDimens.paddingMedium))

            // Today's entries
            Text(
                text = "Današnji obroci",
                style = AppTypography.titleMedium
            )

            Spacer(modifier = Modifier.height(AppDimens.paddingSmall))

            if (todayEntries.isEmpty()) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(AppDimens.paddingSmall),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Još nema obroka",
                            style = AppTypography.titleMedium
                        )
                        Text(
                            text = "Unesi prvi obrok da vidiš kalorije za današnji dan.",
                            style = AppTypography.bodyMedium
                        )
                        Button(
                            onClick = onNavigateToManualEntry,
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null
                            )
                            Text(
                                text = "Unesi prvi obrok",
                                modifier = Modifier.padding(start = AppDimens.paddingSmall)
                            )
                        }
                    }
                }
            } else {
                val mealGroups = remember(todayEntries) {
                    groupEntriesIntoMeals(todayEntries)
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(AppDimens.paddingSmall),
                    modifier = Modifier.weight(1f)
                ) {
                    mealGroups.forEach { group ->
                        if (group.entries.size > 1) {
                            val groupKey = "meal_${group.entries.joinToString("_") { it.id }}"
                            item(key = groupKey, contentType = "meal_group") {
                                GroupedFoodEntryCard(
                                    entries = group.entries,
                                    onDeleteGroup = {
                                        group.entries.forEach { repository.delete(it.id) }
                                    },
                                    onEditEntry = { entryId -> onNavigateToEdit(entryId) },
                                    onDeleteEntry = { entryId -> repository.delete(entryId) }
                                )
                            }
                        } else {
                            val entry = group.entries.first()
                            item(key = entry.id, contentType = "single_entry") {
                                FoodEntryCard(
                                    entry = entry,
                                    onDelete = { repository.delete(entry.id) },
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
