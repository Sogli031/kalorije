package com.kalorije.app.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kalorije.app.ui.components.GlassCard
import com.kalorije.app.ui.components.appTextFieldColors
import com.kalorije.app.ui.theme.AppColors
import com.kalorije.app.ui.theme.AppDimens
import com.kalorije.app.ui.theme.AppTypography

@Composable
fun ReminderSection(
    reminderEnabled: Boolean,
    onReminderEnabledChange: (Boolean) -> Unit,
    reminderHour: Int,
    reminderMinute: Int,
    onTimeChange: (Int, Int) -> Unit,
    showTimePicker: Boolean,
    onShowTimePickerChange: (Boolean) -> Unit,
    context: Context
) {
    // Podsetnik za merenje Section Title
    Text(
        text = "Podsetnik za merenje",
        style = AppTypography.titleMedium
    )

    Spacer(modifier = Modifier.height(AppDimens.paddingSmall))

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dnevni podsetnik",
                        style = AppTypography.labelLarge
                    )
                    Text(
                        text = "Podsetiće te da se izmeriš ujutru",
                        style = AppTypography.labelSmall.copy(color = AppColors.TextMuted)
                    )
                }
                Switch(
                    checked = reminderEnabled,
                    onCheckedChange = onReminderEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AppColors.Primary,
                        checkedTrackColor = AppColors.Primary.copy(alpha = 0.5f)
                    )
                )
            }

            if (reminderEnabled) {
                // Prikaz i izbor vremena
                OutlinedTextField(
                    value = String.format("%02d:%02d", reminderHour, reminderMinute),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Vreme podsetnika") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appTextFieldColors(),
                    trailingIcon = {
                        IconButton(onClick = { onShowTimePickerChange(true) }) {
                            Text("⏰", style = AppTypography.titleMedium)
                        }
                    }
                )
            }
        }
    }

    // Time picker dialog
    if (showTimePicker) {
        LaunchedEffect(Unit) {
            val dialog = android.app.TimePickerDialog(
                context,
                { _, selectedHour, selectedMinute ->
                    onTimeChange(selectedHour, selectedMinute)
                    onShowTimePickerChange(false)
                },
                reminderHour,
                reminderMinute,
                true // 24-satni format
            )
            dialog.setOnCancelListener { onShowTimePickerChange(false) }
            dialog.setOnDismissListener { onShowTimePickerChange(false) }
            dialog.show()
        }
    }
}
