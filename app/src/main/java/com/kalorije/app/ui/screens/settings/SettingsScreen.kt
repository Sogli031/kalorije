package com.kalorije.app.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.kalorije.app.data.backup.BackupOutcome
import com.kalorije.app.data.backup.CloudCheck
import com.kalorije.app.data.model.CycleHistory
import com.kalorije.app.notification.NotificationHelper
import com.kalorije.app.ui.components.GlassCard
import com.kalorije.app.ui.components.ScreenHeader
import com.kalorije.app.ui.components.SectionHeader
import com.kalorije.app.ui.theme.AppColors
import com.kalorije.app.ui.theme.AppDimens
import com.kalorije.app.ui.theme.AppTypography
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val profile by viewModel.profile.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val signInState by viewModel.signInState.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()

    // UI-only state
    var activityExpanded by remember { mutableStateOf(false) }
    var trainingDaysExpanded by remember { mutableStateOf(false) }
    var showTrtDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showEndCycleDialog by remember { mutableStateOf(false) }
    var completedCycleAnalysis by remember { mutableStateOf<CycleHistory?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }

    val showSnackbar: (String, SnackbarDuration) -> Unit = { message, duration ->
        scope.launch { snackbarHostState.showSnackbar(message = message, duration = duration) }
    }
    val hasNotificationPermission: () -> Boolean = {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            viewModel.updateReminderEnabled(true)
            viewModel.saveProfile()
            NotificationHelper.scheduleDailyReminder(context, uiState.reminderHour, uiState.reminderMinute)
            showSnackbar("Podsetnik uključen", SnackbarDuration.Short)
        } else {
            showSnackbar("Dozvola za notifikacije je potrebna", SnackbarDuration.Short)
        }
    }

    // U oblaku postoji backup koji ova instalacija još nije preuzela
    var showCloudAdoptDialog by remember { mutableStateOf(false) }

    // Posle vraćanja iz oblaka podsetnik mora da se uskladi sa vraćenim profilom —
    // alarm živi u AlarmManager-u, ne u backup-u.
    val syncReminderWithRestoredProfile: () -> Unit = {
        val restored = viewModel.profile.value
        when {
            !restored.weightReminderEnabled -> NotificationHelper.cancelReminder(context)
            hasNotificationPermission() ->
                NotificationHelper.scheduleDailyReminder(context, restored.weightReminderHour, restored.weightReminderMinute)
            // Dozvola se traži; launcher po odobrenju sam zakazuje podsetnik
            else -> notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val restoreFromCloud: () -> Unit = {
        scope.launch {
            try {
                when (val result = viewModel.restoreFromCloud()) {
                    RestoreResult.NotSignedIn -> showSnackbar("Niste prijavljeni na Google nalog", SnackbarDuration.Long)
                    RestoreResult.NoBackup -> showSnackbar("U oblaku nema backup-a", SnackbarDuration.Long)
                    is RestoreResult.Failed -> showSnackbar(result.message, SnackbarDuration.Long)
                    is RestoreResult.Success -> {
                        syncReminderWithRestoredProfile()
                        showSnackbar("Vraćeno ${result.imported.totalItems} stavki iz oblaka", SnackbarDuration.Short)
                    }
                }
            } catch (e: Exception) {
                showSnackbar("Vraćanje nije uspelo: ${e.message ?: "nepoznata greška"}", SnackbarDuration.Long)
            }
        }
    }

    val backupToCloud: (Boolean) -> Unit = { confirmedOverwrite ->
        scope.launch {
            try {
                when (val outcome = viewModel.backupNow(confirmedOverwrite)) {
                    BackupOutcome.Success -> showSnackbar("Backup uspešan", SnackbarDuration.Short)
                    BackupOutcome.EmptyData -> showSnackbar("Nema podataka za backup", SnackbarDuration.Long)
                    BackupOutcome.NotSignedIn -> showSnackbar("Niste prijavljeni na Google nalog", SnackbarDuration.Long)
                    BackupOutcome.CloudNotAdopted -> showCloudAdoptDialog = true
                    is BackupOutcome.Error -> showSnackbar("Backup nije uspeo: ${outcome.message}", SnackbarDuration.Long)
                }
            } catch (e: Exception) {
                showSnackbar("Backup nije uspeo: ${e.message ?: "nepoznata greška"}", SnackbarDuration.Long)
            }
        }
    }

    // Odmah posle prijave: ako oblak nije preuzet a backup postoji, ponudi vraćanje.
    // Neuspela provera se NE tumači kao "oblak je prazan".
    val offerRestoreIfCloudHasBackup: () -> Unit = {
        scope.launch {
            when (val check = viewModel.checkCloudForAdoption()) {
                CloudCheck.Exists -> showCloudAdoptDialog = true
                is CloudCheck.Failed -> showSnackbar("Provera oblaka nije uspela: ${check.message}", SnackbarDuration.Long)
                CloudCheck.Missing, null -> Unit
            }
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // Google vraća RESULT_CANCELED i za prave greške (npr. DEVELOPER_ERROR kad SHA-1
        // nije registrovan), pa se rezultat uvek parsira da bi se greška prikazala
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        val account = try {
            task.getResult(ApiException::class.java)
        } catch (e: ApiException) {
            val opis = when (e.statusCode) {
                GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> "Prijava otkazana"
                GoogleSignInStatusCodes.DEVELOPER_ERROR -> "Greška 10: SHA-1 potpis aplikacije nije registrovan u Google Cloud konzoli"
                GoogleSignInStatusCodes.NETWORK_ERROR -> "Greška mreže, proveri internet"
                else -> "Greška prijave (kod ${e.statusCode}): ${GoogleSignInStatusCodes.getStatusCodeString(e.statusCode)}"
            }
            showSnackbar(opis, SnackbarDuration.Long)
            null
        } catch (e: Exception) {
            showSnackbar("Greška prijave: ${e.message ?: e.javaClass.simpleName}", SnackbarDuration.Long)
            null
        }
        if (account != null) {
            viewModel.handleSignInResult(account)
            showSnackbar("Prijavljen na Google Drive", SnackbarDuration.Short)
            offerRestoreIfCloudHasBackup()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> Snackbar(data, containerColor = AppColors.Surface, contentColor = AppColors.TextPrimary) } },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp)
    ) { scaffoldPadding ->
        Column(Modifier.fillMaxSize().background(AppColors.Background).statusBarsPadding().padding(scaffoldPadding).padding(AppDimens.paddingMedium).verticalScroll(rememberScrollState())) {
            ScreenHeader(title = "Podešavanja", onBack = onNavigateBack)
            Spacer(Modifier.height(AppDimens.paddingMedium))

            SectionHeader(title = "Profil")
            Spacer(Modifier.height(AppDimens.paddingSmall))
            ProfileSection(uiState.heightText, viewModel::updateHeight, uiState.heightError, uiState.weightText, viewModel::updateWeight, uiState.weightError,
                uiState.ageText, viewModel::updateAge, uiState.ageError, uiState.isMale, viewModel::updateGender,
                uiState.activityLevel, viewModel::updateActivityLevel, activityExpanded, { activityExpanded = it },
                uiState.resistanceTrainingDays, viewModel::updateResistanceTrainingDays, trainingDaysExpanded, { trainingDaysExpanded = it },
                uiState.manualTdeeText, viewModel::updateManualTdee)

            Spacer(Modifier.height(AppDimens.paddingMedium))

            SectionHeader(title = "Cilj")
            Spacer(Modifier.height(AppDimens.paddingSmall))
            GoalModeSection(
                goalMode = uiState.goalMode,
                onGoalModeChange = viewModel::updateGoalMode,
                goalPaceText = uiState.goalPaceText,
                onGoalPaceChange = viewModel::updateGoalPace,
                paceWarning = uiState.paceWarning,
                goalCalorieOffset = uiState.previewProfile?.goalCalorieOffset ?: 0
            )

            Spacer(Modifier.height(AppDimens.paddingMedium))

            SectionHeader(title = "TRT i ciklus")
            Spacer(Modifier.height(AppDimens.paddingSmall))
            TrtSection(uiState.useTrt, viewModel::updateTrtEnabled, uiState.trtStartDate, viewModel::updateTrtStartDate,
                uiState.trtCycleEndDate, viewModel::updateTrtEndDate, uiState.trtDoseText, viewModel::updateTrtDose,
                uiState.usesAi, viewModel::updateUsesAi, uiState.previewProfile, uiState.liveCycleStats,
                { showEndCycleDialog = true }, showTrtDatePicker, { showTrtDatePicker = it }, showEndDatePicker, { showEndDatePicker = it },
                postCycleState = uiState.postCycleState)

            if (showEndCycleDialog) {
                Dialog(onDismissRequest = { showEndCycleDialog = false }) {
                    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = AppColors.Surface) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Završi ciklus?", style = AppTypography.titleMedium)
                            Spacer(Modifier.height(12.dp))
                            Text("Ovo će sačuvati ciklus u istoriju i prikazati analizu tvojih rezultata.", style = AppTypography.bodyMedium, color = AppColors.TextSecondary)
                            Spacer(Modifier.height(16.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                                Button({ showEndCycleDialog = false }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AppColors.SurfaceLight)) { Text("Odustani") }
                                Button({
                                    val analysis = viewModel.endTrtCycle()
                                    showEndCycleDialog = false
                                    if (analysis != null) completedCycleAnalysis = analysis
                                }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) { Text("Završi") }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(AppDimens.paddingMedium))

            uiState.previewProfile?.let { preview ->
                SectionHeader(title = "Dnevni cilj")
                Spacer(Modifier.height(AppDimens.paddingSmall))
                GoalsPreviewSection(preview, viewModel.hasProfileError(), viewModel::saveProfile)
            }

            Spacer(Modifier.height(AppDimens.paddingLarge))

            SectionHeader(title = "Backup")
            Spacer(Modifier.height(AppDimens.paddingSmall))
            CloudBackupSection(signInState, syncStatus, lastSyncTime,
                onSignIn = { signInLauncher.launch(viewModel.getSignInIntent()) },
                onSignOut = { scope.launch { viewModel.signOut(); showSnackbar("Odjavljen sa Google Drive-a", SnackbarDuration.Short) } },
                onBackup = { backupToCloud(false) },
                onRestore = restoreFromCloud)

            Spacer(Modifier.height(AppDimens.paddingLarge))

            SectionHeader(title = "Podsetnik")
            Spacer(Modifier.height(AppDimens.paddingSmall))
            ReminderSection(uiState.reminderEnabled,
                onReminderEnabledChange = { enabled ->
                    if (enabled) {
                        if (!hasNotificationPermission()) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            return@ReminderSection
                        }
                        viewModel.updateReminderEnabled(true)
                        viewModel.saveProfile()
                        NotificationHelper.scheduleDailyReminder(context, uiState.reminderHour, uiState.reminderMinute)
                        showSnackbar("Podsetnik uključen", SnackbarDuration.Short)
                    } else {
                        viewModel.updateReminderEnabled(false)
                        viewModel.saveProfile()
                        NotificationHelper.cancelReminder(context)
                        showSnackbar("Podsetnik isključen", SnackbarDuration.Short)
                    }
                },
                uiState.reminderHour, uiState.reminderMinute,
                onTimeChange = { h, m ->
                    viewModel.updateReminderHour(h)
                    viewModel.updateReminderMinute(m)
                    viewModel.saveProfile()
                    if (uiState.reminderEnabled && hasNotificationPermission()) {
                        NotificationHelper.scheduleDailyReminder(context, h, m)
                        showSnackbar("Podsetnik postavljen na ${String.format("%02d:%02d", h, m)}", SnackbarDuration.Short)
                    } else {
                        showSnackbar("Vreme podsetnika sačuvano", SnackbarDuration.Short)
                    }
                },
                showTimePicker, { showTimePicker = it }, context)

            Spacer(Modifier.height(AppDimens.paddingLarge))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(AppDimens.paddingMedium)) {
                    Text("O aplikaciji", style = AppTypography.titleMedium)
                    Spacer(Modifier.height(AppDimens.paddingSmall))
                    Text("Kalorije 1.0", style = AppTypography.labelLarge)
                    Text("Praćenje kalorija i ciljeva ishrane.", style = AppTypography.bodyMedium)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // Prazno ne gazi popunjeno: dok oblak nije preuzet, backup ne ide bez pitanja
    if (showCloudAdoptDialog) {
        Dialog(onDismissRequest = { showCloudAdoptDialog = false }) {
            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = AppColors.Surface) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("U oblaku već postoji backup", style = AppTypography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Na ovom uređaju podaci još nisu vraćeni iz oblaka. Novi backup bi zamenio postojeći.",
                        style = AppTypography.bodyMedium,
                        color = AppColors.TextSecondary
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        { showCloudAdoptDialog = false; restoreFromCloud() },
                        Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                    ) { Text("Vrati iz oblaka") }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        { showCloudAdoptDialog = false; backupToCloud(true) },
                        Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Error)
                    ) { Text("Ipak napravi backup") }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        { showCloudAdoptDialog = false },
                        Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.SurfaceLight)
                    ) { Text("Otkaži") }
                }
            }
        }
    }

    completedCycleAnalysis?.let { cycle ->
        CycleAnalysisDialog(cycle = cycle, onDismiss = { completedCycleAnalysis = null })
    }
}
