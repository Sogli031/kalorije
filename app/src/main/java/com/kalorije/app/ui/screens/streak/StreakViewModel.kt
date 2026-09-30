package com.kalorije.app.ui.screens.streak

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.kalorije.app.KalorijeApplication
import com.kalorije.app.data.model.UserProfile
import com.kalorije.app.data.repository.FoodRepository
import com.kalorije.app.util.StreakAnalysis
import com.kalorije.app.util.StreakAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class StreakUiState(
    val analysis: StreakAnalysis = StreakAnalysis(),
    val isLoading: Boolean = true
)

class StreakViewModel(private val repository: FoodRepository) : ViewModel() {

    val profile: StateFlow<UserProfile> = repository.profile

    private val _uiState = MutableStateFlow(StreakUiState())
    val uiState: StateFlow<StreakUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.entries, repository.profile, repository.goals) { entries, profile, goals ->
                Triple(entries, profile, goals.calories)
            }.collect { (entries, profile, targetCalories) ->
                val analysis = withContext(Dispatchers.Default) {
                    val caloriesByDate: Map<LocalDate, Double> = entries
                        .groupBy { it.date }
                        .mapValues { (_, dayEntries) -> dayEntries.sumOf { it.calories } }
                    StreakAnalyzer.analyze(
                        caloriesByDate = caloriesByDate,
                        profile = profile,
                        target = targetCalories
                    )
                }
                _uiState.value = StreakUiState(analysis = analysis, isLoading = false)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KalorijeApplication
                return StreakViewModel(app.foodRepository) as T
            }
        }
    }
}
