package com.kalorije.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kalorije.app.KalorijeApplication
import com.kalorije.app.data.backup.BackupOutcome
import com.kalorije.app.data.backup.CloudCheck
import com.kalorije.app.data.backup.RestoreDownload
import com.kalorije.app.data.backup.SyncManager
import com.kalorije.app.data.backup.SyncStatus
import com.kalorije.app.data.model.ActivityLevel
import com.kalorije.app.data.model.CycleHistory
import com.kalorije.app.data.model.GoalMode
import com.kalorije.app.data.repository.FoodRepository.ImportResult
import com.kalorije.app.data.model.LiveCycleStats
import com.kalorije.app.data.model.PostCycleState
import com.kalorije.app.data.model.UserProfile
import com.kalorije.app.data.model.WeightGoal
import com.kalorije.app.data.repository.FoodRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed class RestoreResult {
    data class Success(val imported: ImportResult) : RestoreResult()
    data object NoBackup : RestoreResult()
    data object NotSignedIn : RestoreResult()
    data class Failed(val message: String) : RestoreResult()
}

data class SettingsUiState(
    // Profile edit state - inicijalizuje se iz profile
    val heightText: String = "",
    val weightText: String = "",
    val ageText: String = "",
    val isMale: Boolean = true,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val weightGoal: WeightGoal = WeightGoal.MAINTAIN,
    val customCaloriesText: String = "",
    val manualTdeeText: String = "",  // prazan = automatsko učenje
    val resistanceTrainingDays: Int = 3,

    // TRT edit state
    val useTrt: Boolean = false,
    val trtStartDate: LocalDate? = null,
    val trtCycleEndDate: LocalDate? = null,
    val trtDoseText: String = "150",
    val usesAi: Boolean = false,

    // Reminder state
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 7,
    val reminderMinute: Int = 0,

    // Validation (computed)
    val heightError: Boolean = false,
    val weightError: Boolean = false,
    val ageError: Boolean = false,

    // Preview profile (computed od edit state-a)
    val previewProfile: UserProfile? = null,

    // Live cycle stats
    val liveCycleStats: LiveCycleStats? = null,

    // GoalMode state
    val goalMode: GoalMode = GoalMode.MAINTENANCE,
    val goalPaceText: String = "",
    val paceWarning: String? = null,
    val postCycleState: PostCycleState? = null
)

class SettingsViewModel(
    private val repository: FoodRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    val profile = repository.profile

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // Izloži SyncManager stanja za UI
    val syncStatus: StateFlow<SyncStatus> = syncManager.syncStatus
    val lastSyncTime: StateFlow<Long?> = syncManager.lastSyncTime
    val signInState = syncManager.signInState

    init {
        viewModelScope.launch {
            repository.profile.collect { prof ->
                // Samo ako je prvi put ili ako profil došao sa servera
                val current = _uiState.value
                if (current.heightText.isEmpty()) {
                    // Inicijalizuj iz profila
                    _uiState.value = createStateFromProfile(prof)
                }
            }
        }
    }

    private fun createStateFromProfile(profile: UserProfile): SettingsUiState {
        val state = SettingsUiState(
            heightText = profile.heightCm.toString(),
            weightText = profile.weightKg.toString(),
            ageText = profile.age.toString(),
            isMale = profile.isMale,
            activityLevel = profile.activityLevel,
            weightGoal = profile.goal,
            customCaloriesText = profile.customCalories?.toString() ?: "",
            manualTdeeText = profile.manualTdee?.toString() ?: "",
            resistanceTrainingDays = profile.resistanceTrainingDays,
            useTrt = profile.useTrt,
            trtStartDate = profile.trtStartDate,
            trtCycleEndDate = profile.trtCycleEndDate,
            trtDoseText = profile.trtDoseMgWeek.toInt().toString(),
            usesAi = profile.usesAromataseInhibitor,
            reminderEnabled = profile.weightReminderEnabled,
            reminderHour = profile.weightReminderHour,
            reminderMinute = profile.weightReminderMinute,
            goalMode = profile.goalMode,
            goalPaceText = if (profile.goalPaceKgPerWeek > 0) profile.goalPaceKgPerWeek.toString() else "",
            postCycleState = profile.postCycleState
        )
        return recomputeValidationAndPreview(state)
    }

    private fun updateState(transform: SettingsUiState.() -> SettingsUiState) {
        val newState = _uiState.value.transform()
        _uiState.value = recomputeValidationAndPreview(newState)
    }

    private fun recomputeValidationAndPreview(state: SettingsUiState): SettingsUiState {
        val hVal = state.heightText.toIntOrNull()
        val wVal = state.weightText.toDoubleOrNull()
        val aVal = state.ageText.toIntOrNull()

        val heightErr = state.heightText.isNotBlank() && (hVal == null || hVal !in 50..250)
        val weightErr = state.weightText.isNotBlank() && (wVal == null || wVal !in 20.0..500.0)
        val ageErr = state.ageText.isNotBlank() && (aVal == null || aVal !in 1..120)

        val parsedPace = state.goalPaceText.replace(",", ".").toDoubleOrNull()?.coerceIn(0.0, 2.0) ?: 0.0

        // Kreiraj previewProfile od trenutnog edit state-a
        val currentProfile = profile.value
        // copy(), ne konstruktor: polja koja se ovde ne edituju (weightPeriodStartDate,
        // goalMigrated, tdeeAdjustment, istorije...) moraju da prežive čuvanje profila.
        val preview = currentProfile.copy(
            heightCm = hVal ?: currentProfile.heightCm,
            weightKg = wVal ?: currentProfile.weightKg,
            age = aVal ?: currentProfile.age,
            isMale = state.isMale,
            activityLevel = state.activityLevel,
            goal = state.weightGoal,
            customCalories = if (state.weightGoal == WeightGoal.CUSTOM) {
                state.customCaloriesText.toIntOrNull()?.takeIf { it in 500..10000 }
            } else null,
            manualTdee = state.manualTdeeText.toIntOrNull()?.takeIf { it in 500..10000 },
            useTrt = state.useTrt,
            trtStartDate = state.trtStartDate,
            trtCycleEndDate = state.trtCycleEndDate,
            trtDoseMgWeek = state.trtDoseText.toDoubleOrNull() ?: currentProfile.trtDoseMgWeek,
            usesAromataseInhibitor = state.usesAi,
            resistanceTrainingDays = state.resistanceTrainingDays,
            // Podsetnik se edituje u Settings-u pa mora ići iz state-a, ne iz currentProfile
            weightReminderEnabled = state.reminderEnabled,
            weightReminderHour = state.reminderHour,
            weightReminderMinute = state.reminderMinute,
            postCycleState = state.postCycleState,
            goalMode = state.goalMode,
            goalPaceKgPerWeek = parsedPace
        )

        return state.copy(
            heightError = heightErr,
            weightError = weightErr,
            ageError = ageErr,
            previewProfile = preview,
            paceWarning = preview.paceWarning
        )
    }

    // Update metode
    fun updateHeight(text: String) {
        updateState { copy(heightText = text.filter { it.isDigit() }) }
    }

    fun updateWeight(text: String) {
        updateState { copy(weightText = text.filter { it.isDigit() || it == '.' }) }
    }

    fun updateAge(text: String) {
        updateState { copy(ageText = text.filter { it.isDigit() }) }
    }

    fun updateGender(isMale: Boolean) {
        updateState { copy(isMale = isMale) }
    }

    fun updateActivityLevel(level: ActivityLevel) {
        updateState { copy(activityLevel = level) }
    }

    fun updateWeightGoal(goal: WeightGoal) {
        updateState {
            val newCustomCalories = if (goal == WeightGoal.CUSTOM && customCaloriesText.isEmpty()) {
                profile.value.targetCalories.toString()
            } else {
                customCaloriesText
            }
            copy(weightGoal = goal, customCaloriesText = newCustomCalories)
        }
    }

    fun updateCustomCalories(text: String) {
        updateState { copy(customCaloriesText = text.filter { it.isDigit() }) }
    }

    fun updateManualTdee(text: String) {
        updateState { copy(manualTdeeText = text.filter { it.isDigit() }) }
    }

    fun updateResistanceTrainingDays(days: Int) {
        updateState { copy(resistanceTrainingDays = days) }
    }

    fun updateTrtEnabled(enabled: Boolean) {
        updateState {
            val newStartDate = if (enabled && trtStartDate == null) {
                LocalDate.now()
            } else {
                trtStartDate
            }
            copy(useTrt = enabled, trtStartDate = newStartDate)
        }
    }

    fun updateTrtStartDate(date: LocalDate?) {
        updateState { copy(trtStartDate = date) }
    }

    fun updateTrtEndDate(date: LocalDate?) {
        updateState { copy(trtCycleEndDate = date) }
    }

    fun updateTrtDose(text: String) {
        updateState { copy(trtDoseText = text.filter { it.isDigit() }) }
    }

    fun updateUsesAi(usesAi: Boolean) {
        updateState { copy(usesAi = usesAi) }
    }

    fun updateGoalMode(mode: GoalMode) {
        updateState {
            val defaultPace = when (mode) {
                GoalMode.MAINTENANCE -> ""
                GoalMode.BULK -> "0.35"
                GoalMode.CUT -> "0.5"
            }
            // Ako već ima tempo za taj mode, zadrži ga
            val pace = if (mode == goalMode && goalPaceText.isNotBlank()) goalPaceText else defaultPace
            copy(goalMode = mode, goalPaceText = pace)
        }
    }

    fun updateGoalPace(text: String) {
        updateState { copy(goalPaceText = text.filter { it.isDigit() || it == '.' || it == ',' }) }
    }

    // Idu kroz updateState da bi previewProfile (koji saveProfile čuva) dobio nove vrednosti
    fun updateReminderEnabled(enabled: Boolean) {
        updateState { copy(reminderEnabled = enabled) }
    }

    fun updateReminderHour(hour: Int) {
        updateState { copy(reminderHour = hour) }
    }

    fun updateReminderMinute(minute: Int) {
        updateState { copy(reminderMinute = minute) }
    }

    // Action metode
    fun saveProfile() {
        val preview = _uiState.value.previewProfile ?: return
        repository.updateProfile(preview, recalculateGoals = true)
    }

    fun endTrtCycle(): CycleHistory? {
        val analysis = repository.endTrtCycle()
        if (analysis != null) {
            updateState {
                copy(
                    useTrt = false,
                    trtStartDate = null,
                    postCycleState = PostCycleState(cycleEndDate = LocalDate.now())
                )
            }
        }
        return analysis
    }

    fun getLiveCycleStats(): LiveCycleStats? {
        val stats = repository.getLiveCycleStats()
        _uiState.value = _uiState.value.copy(liveCycleStats = stats)
        return stats
    }

    /**
     * Backup u oblak. Nema više force zaobilaženja: prazno stanje se ne šalje,
     * a dok oblak nije preuzet na ovoj instalaciji vraća se CloudNotAdopted i
     * ekran pita korisnika. [confirmedOverwrite] = korisnik je izričito potvrdio.
     */
    suspend fun backupNow(confirmedOverwrite: Boolean = false): BackupOutcome {
        if (confirmedOverwrite) syncManager.markCloudAdopted()
        return syncManager.backup { repository.exportJsonForBackup() }
    }

    /**
     * Vraćanje iz oblaka: preuzimanje + uvoz (sve-ili-ništa). Radi u application
     * scope-u da izlazak sa ekrana ne prekine restore na pola.
     */
    suspend fun restoreFromCloud(): RestoreResult = syncManager.runInAppScope {
        when (val download = syncManager.restore()) {
            RestoreDownload.NotSignedIn -> RestoreResult.NotSignedIn
            RestoreDownload.NoBackup -> RestoreResult.NoBackup
            is RestoreDownload.Error -> RestoreResult.Failed("Preuzimanje iz oblaka nije uspelo: ${download.message}")
            is RestoreDownload.Data -> {
                val result = repository.importJson(download.json)
                val imported = result.getOrNull()
                if (imported != null) {
                    syncManager.markCloudAdopted()
                    _uiState.value = createStateFromProfile(profile.value)
                    RestoreResult.Success(imported)
                } else {
                    val message = "Vraćanje nije uspelo, ništa nije izmenjeno: " +
                        (result.exceptionOrNull()?.message ?: "nepoznata greška")
                    syncManager.reportRestoreFailure(message)
                    RestoreResult.Failed(message)
                }
            }
        }
    }

    /** Posle prijave: da li treba ponuditi vraćanje (oblak nije preuzet, a backup postoji). */
    suspend fun checkCloudForAdoption(): CloudCheck? {
        if (syncManager.isCloudAdopted()) return null
        return syncManager.checkCloudBackup()
    }

    fun hasProfileError(): Boolean {
        return _uiState.value.heightError || _uiState.value.weightError || _uiState.value.ageError
    }

    // Google Sign-In passthrough metode
    fun getSignInIntent() = syncManager.getSignInIntent()

    fun handleSignInResult(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount?) = syncManager.handleSignInResult(account)

    suspend fun signOut() = syncManager.signOut()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KalorijeApplication
                SettingsViewModel(app.foodRepository, app.syncManager)
            }
        }
    }
}
