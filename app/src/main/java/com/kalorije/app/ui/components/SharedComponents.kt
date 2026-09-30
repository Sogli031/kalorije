package com.kalorije.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kalorije.app.ui.theme.AppColors
import com.kalorije.app.ui.theme.AppDimens
import com.kalorije.app.ui.theme.AppTypography

@Composable
fun appTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AppColors.Primary,
    unfocusedBorderColor = AppColors.GlassBorder,
    focusedTextColor = AppColors.TextPrimary,
    unfocusedTextColor = AppColors.TextPrimary,
    focusedLabelColor = AppColors.Primary
)

@Composable
fun MacroDisplay(
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

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            onBack?.let {
                IconButton(
                    onClick = it,
                    modifier = Modifier.size(AppDimens.iconButtonSize)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Nazad",
                        tint = AppColors.TextPrimary
                    )
                }
            }
            Column {
                Text(title, style = AppTypography.titleLarge)
                subtitle?.let { Text(it, style = AppTypography.bodyMedium) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), content = actions)
    }
}

@Composable
fun SectionHeader(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = AppTypography.titleMedium)
        trailing?.let { Text(it, style = AppTypography.labelSmall) }
    }
}

@Composable
fun CollapsiblePanel(
    title: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppDimens.paddingSmall)
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = AppTypography.titleMedium)
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(AppDimens.iconButtonSize)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "Sakrij $title" else "Prikaži $title",
                        tint = AppColors.TextSecondary
                    )
                }
            }
        }

        if (expanded) {
            content()
        }
    }
}

@Composable
fun MetricPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(AppDimens.radiusSmall)
    Row(
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.36f), shape)
            .padding(horizontal = AppDimens.paddingSmall, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, style = AppTypography.labelSmall.copy(color = color))
        Text(value, style = AppTypography.labelMedium.copy(color = AppColors.TextPrimary))
    }
}

@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val outerShape = RoundedCornerShape(AppDimens.radiusMedium)
    Row(
        modifier = modifier
            .clip(outerShape)
            .background(AppColors.Surface)
            .border(1.dp, AppColors.GlassBorder, outerShape)
            .selectableGroup()
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(AppDimens.radiusSmall))
                    .background(if (selected) AppColors.PrimarySoft else Color.Transparent)
                    .selectable(
                        selected = selected,
                        onClick = { onSelected(index) },
                        role = Role.Tab
                    )
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = AppTypography.labelLarge.copy(
                        color = if (selected) AppColors.Primary else AppColors.TextSecondary
                    )
                )
            }
        }
    }
}
