package com.kalorije.app

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.compose.rememberNavController
import com.kalorije.app.notification.NotificationHelper
import com.kalorije.app.notification.scheduleReminderFromSavedProfile
import kotlinx.coroutines.launch
import com.kalorije.app.ui.navigation.NavGraph
import com.kalorije.app.ui.screens.CrashLogScreen
import com.kalorije.app.ui.theme.AppColors
import com.kalorije.app.ui.theme.AppDimens
import com.kalorije.app.ui.theme.AppTypography
import com.kalorije.app.ui.theme.KalorijeTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private var appInitialized = false
    private var exactAlarmWarningShownThisForeground = false
    private var showExactAlarmPermissionDialog by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Proveri crash log pre bilo čega drugog
        val crashFile = File(filesDir, "crash_log.txt")
        if (crashFile.exists()) {
            val crashLog = crashFile.readText()
            crashFile.delete()
            setContent {
                KalorijeTheme {
                    CrashLogScreen(crashLog = crashLog)
                }
            }
            return
        }

        // Inicijalizacija servisa (wrappovana u try-catch)
        try {
            val app = application as KalorijeApplication
            val foodRepository = app.foodRepository

            // Kreiraj notification channel za podsetnike
            NotificationHelper.createNotificationChannel(this)

            // Zakaži podsetnik ako je uključen — tek kad se profil učita sa diska
            // (odmah posle kreiranja repozitorijuma profile.value je još default)
            app.applicationScope.launch {
                scheduleReminderFromSavedProfile(applicationContext)
            }

            setContent {
                KalorijeTheme {
                    val navController = rememberNavController()
                    NavGraph(
                        navController = navController,
                        repository = foodRepository
                    )
                    if (showExactAlarmPermissionDialog) {
                        ExactAlarmPermissionDialog(
                            onOpenSettings = ::openExactAlarmSettings,
                            onDismiss = { showExactAlarmPermissionDialog = false }
                        )
                    }
                }
            }
            appInitialized = true
        } catch (e: Exception) {
            val crashLog = Log.getStackTraceString(e)
            setContent {
                KalorijeTheme {
                    CrashLogScreen(crashLog = crashLog)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        exactAlarmWarningShownThisForeground = false
    }

    override fun onResume() {
        super.onResume()
        if (appInitialized) {
            requestExactAlarmPermissionIfNeeded()
        }
    }

    private fun requestExactAlarmPermissionIfNeeded() {
        if (exactAlarmWarningShownThisForeground) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val alarmManager = getSystemService(AlarmManager::class.java)
        if (alarmManager.canScheduleExactAlarms()) return

        exactAlarmWarningShownThisForeground = true
        showExactAlarmPermissionDialog = true
    }

    private fun openExactAlarmSettings() {
        showExactAlarmPermissionDialog = false
        runCatching { startActivity(exactAlarmSettingsIntent()) }
    }

    private fun exactAlarmSettingsIntent(): Intent {
        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:$packageName")
        }
    }
}

@Composable
private fun ExactAlarmPermissionDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(AppDimens.radiusLarge),
            color = AppColors.SurfaceRaised,
            border = BorderStroke(1.dp, AppColors.Warning.copy(alpha = 0.45f))
        ) {
            Column(
                modifier = Modifier
                    .padding(AppDimens.paddingLarge)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppDimens.paddingMedium),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Tačni alarmi nisu uključeni",
                    style = AppTypography.titleMedium.copy(
                        color = AppColors.Warning,
                        fontWeight = FontWeight.W700
                    ),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Da bi podsetnici stizali u tačno vreme, uključi dozvolu Alarmi i podsetnici za ovu aplikaciju.",
                    style = AppTypography.bodyMedium.copy(color = AppColors.TextSecondary),
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Warning,
                        contentColor = AppColors.Background
                    ),
                    shape = RoundedCornerShape(AppDimens.radiusMedium)
                ) {
                    Text("Otvori podešavanje", style = AppTypography.labelLarge)
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.TextSecondary),
                    border = BorderStroke(1.dp, AppColors.GlassBorder),
                    shape = RoundedCornerShape(AppDimens.radiusMedium)
                ) {
                    Text("Kasnije", style = AppTypography.labelLarge)
                }
            }
        }
    }
}
