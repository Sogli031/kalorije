package com.kalorije.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kalorije.app.data.backup.SignInState
import com.kalorije.app.data.backup.SyncStatus
import com.kalorije.app.ui.components.GlassCard
import com.kalorije.app.ui.theme.AppColors
import com.kalorije.app.ui.theme.AppDimens
import com.kalorije.app.ui.theme.AppTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CloudBackupSection(
    signInState: SignInState,
    syncStatus: SyncStatus,
    lastSyncTime: Long?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    // Cloud Backup Section Title
    Text(
        text = "Cloud Backup",
        style = AppTypography.titleMedium
    )

    Spacer(modifier = Modifier.height(AppDimens.paddingSmall))

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (signInState) {
                            is SignInState.SignedIn -> Icons.Default.CloudDone
                            is SignInState.SignedOut -> Icons.Default.CloudOff
                        },
                        contentDescription = null,
                        tint = when (signInState) {
                            is SignInState.SignedIn -> AppColors.Success
                            is SignInState.SignedOut -> AppColors.TextMuted
                        },
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Column {
                        Text(
                            text = when (val state = signInState) {
                                is SignInState.SignedIn -> state.email
                                is SignInState.SignedOut -> "Nije povezano"
                            },
                            style = AppTypography.labelLarge
                        )

                        // Status indikator
                        when (val status = syncStatus) {
                            is SyncStatus.Idle -> {
                                // Prikaži poslednju sinhronizaciju
                                if (lastSyncTime != null) {
                                    Text(
                                        text = "Poslednja sinhronizacija: ${formatTime(lastSyncTime)}",
                                        style = AppTypography.labelSmall,
                                        color = AppColors.TextMuted
                                    )
                                }
                            }
                            is SyncStatus.Syncing -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = AppColors.Primary,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = "Sinhronizacija...",
                                        style = AppTypography.labelSmall,
                                        color = AppColors.Primary
                                    )
                                }
                            }
                            is SyncStatus.Retrying -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = AppColors.Primary,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = "Pokušaj ${status.attempt}/${status.maxAttempts}...",
                                        style = AppTypography.labelSmall,
                                        color = AppColors.Primary
                                    )
                                }
                            }
                            is SyncStatus.Success -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = AppColors.Success,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Uspešno",
                                        style = AppTypography.labelSmall,
                                        color = AppColors.Success
                                    )
                                }
                            }
                            is SyncStatus.Error -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = AppColors.Error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = status.message,
                                        style = AppTypography.labelSmall,
                                        color = AppColors.Error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppDimens.paddingMedium))

            when (signInState) {
                is SignInState.SignedOut -> {
                    Button(
                        onClick = onSignIn,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                    ) {
                        Icon(Icons.Default.Cloud, contentDescription = null)
                        Text(" Prijavi se sa Google")
                    }
                }
                is SignInState.SignedIn -> {
                    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.paddingSmall)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppDimens.paddingSmall)
                        ) {
                            val isSyncActive = syncStatus is SyncStatus.Syncing || syncStatus is SyncStatus.Retrying
                            Button(
                                onClick = onBackup,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                                enabled = !isSyncActive
                            ) {
                                Text("Backup")
                            }
                            Button(
                                onClick = onRestore,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                                enabled = !isSyncActive
                            ) {
                                Text("Vrati")
                            }
                        }
                        Button(
                            onClick = onSignOut,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.SurfaceLight)
                        ) {
                            Text("Odjavi se")
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
