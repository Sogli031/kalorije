package com.kalorije.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kalorije.app.data.model.FoodEntry
import com.kalorije.app.ui.theme.AppColors
import com.kalorije.app.ui.theme.AppTypography
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun FoodEntryCard(
    entry: FoodEntry,
    onDelete: () -> Unit,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember(entry.id) { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Obrisati obrok?", style = AppTypography.titleMedium) },
            text = { Text(entry.product.name, style = AppTypography.bodyMedium) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text("Obriši", color = AppColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Odustani")
                }
            },
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary
        )
    }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentPadding = 12.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Vreme obroka na vrhu (koristi uneto vreme ako postoji, inače createdAt)
            Text(
                text = entry.time ?: entry.createdAt.format(timeFormatter),
                style = AppTypography.labelSmall.copy(color = AppColors.TextMuted),
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!entry.product.imageUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppColors.GlassBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🍽", style = AppTypography.titleMedium)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.product.name,
                        style = AppTypography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${entry.grams.roundToInt()}g",
                        style = AppTypography.labelSmall.copy(color = AppColors.TextSecondary)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${entry.calories.roundToInt()} kcal",
                        style = AppTypography.labelLarge.copy(color = AppColors.Calories)
                    )
                }

                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Obriši",
                        tint = AppColors.TextMuted
                    )
                }
            }

        }
    }
}

/**
 * Kartica za grupu jela unetih zajedno
 */
@Composable
fun GroupedFoodEntryCard(
    entries: List<FoodEntry>,
    onDeleteGroup: () -> Unit,
    onEditEntry: ((String) -> Unit)? = null,
    onDeleteEntry: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val groupIdentity = remember(entries) { entries.joinToString("_") { it.id } }
    var isExpanded by remember(groupIdentity) { mutableStateOf(false) }
    var showGroupDeleteDialog by remember(groupIdentity) { mutableStateOf(false) }

    val totalCalories = entries.sumOf { it.calories }.roundToInt()

    // Vreme grupe (uzmi od prvog unosa - preferira time polje ako postoji)
    val groupTime = entries.firstOrNull()?.let { it.time ?: it.createdAt.format(timeFormatter) } ?: ""

    // Grupiši ista jela i prebroj ih - prikaži gramažu za svaku stavku
    val groupedItems = entries.groupBy { it.product.name }
        .map { (name, items) ->
            val count = items.size
            val totalGrams = items.sumOf { it.grams }.roundToInt()
            "$name (${totalGrams}g)" + if (count > 1) " x$count" else ""
        }

    if (showGroupDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showGroupDeleteDialog = false },
            title = { Text("Obrisati grupu obroka?", style = AppTypography.titleMedium) },
            text = { Text("${entries.size} stavki", style = AppTypography.bodyMedium) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showGroupDeleteDialog = false
                        onDeleteGroup()
                    }
                ) {
                    Text("Obriši", color = AppColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGroupDeleteDialog = false }) {
                    Text("Odustani")
                }
            },
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary
        )
    }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        contentPadding = 12.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Vreme grupe na vrhu
            if (groupTime.isNotEmpty()) {
                Text(
                    text = groupTime,
                    style = AppTypography.labelSmall.copy(color = AppColors.TextMuted),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Lista jela u grupi
                    groupedItems.forEach { itemText ->
                        Text(
                            text = itemText,
                            style = AppTypography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "$totalCalories kcal",
                        style = AppTypography.labelLarge.copy(color = AppColors.Calories)
                    )
                }

                Row {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Skupi" else "Proširi za izmenu",
                        tint = AppColors.TextSecondary,
                        modifier = Modifier
                            .padding(12.dp)
                            .size(24.dp)
                    )
                    IconButton(onClick = { showGroupDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Obriši grupu",
                            tint = AppColors.TextMuted
                        )
                    }
                }
            }


            // Expanded view showing individual entries for editing
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (onEditEntry != null) "Klikni na jelo za izmenu:" else "Stavke u grupi:",
                        style = AppTypography.labelSmall.copy(color = AppColors.TextMuted)
                    )
                    entries.forEach { entry ->
                        GroupedEntryItem(
                            entry = entry,
                            onEdit = onEditEntry?.let { edit -> { edit(entry.id) } },
                            onDelete = onDeleteEntry?.let { delete -> { delete(entry.id) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupedEntryItem(
    entry: FoodEntry,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    val clickModifier = if (onEdit != null) Modifier.clickable { onEdit() } else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.GlassBackground.copy(alpha = 0.5f))
            .then(clickModifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.product.name,
                style = AppTypography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${entry.grams.roundToInt()}g",
                style = AppTypography.labelSmall
            )
        }
        Text(
            text = "${entry.calories.roundToInt()} kcal",
            style = AppTypography.labelMedium.copy(color = AppColors.Calories)
        )
        if (onEdit != null) {
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Izmeni",
                    tint = AppColors.Primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (onDelete != null) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Obriši",
                    tint = AppColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
