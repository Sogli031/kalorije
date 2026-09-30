package com.kalorije.app.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.kalorije.app.KalorijeApplication
import com.kalorije.app.data.db.dao.DateSummary
import com.kalorije.app.data.model.FoodEntry
import com.kalorije.app.data.repository.FoodRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HistoryUiState(
    val dateSummaries: List<DateSummary> = emptyList(),
    val mealCounts: Map<LocalDate, Int> = emptyMap(),
    val isLoading: Boolean = true,
    val hasMore: Boolean = true,
    val expandedDate: LocalDate? = null,
    val expandedEntries: List<FoodEntry> = emptyList(),
    val isLoadingEntries: Boolean = false
)

class HistoryViewModel(private val repository: FoodRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private var currentOffset = 0
    private val pageSize = 30

    init {
        // Prati sve promene unosa (dodavanje/izmena/brisanje, i sa drugih ekrana)
        // i osvezi vec ucitane strane istorije.
        viewModelScope.launch {
            // Sacekaj da repozitorijum ucita podatke da lista ne bi zatreptala kao prazna
            repository.isLoaded.first { it }
            repository.entries.collect { reloadLoadedPages() }
        }
    }

    fun loadNextPage() {
        if (_uiState.value.isLoading || !_uiState.value.hasMore) return

        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val summaries = repository.getDateSummariesPaginated(pageSize, currentOffset)
            currentOffset += summaries.size
            val mealCounts = repository.getMealCountsForDates(summaries.map { it.date })
            _uiState.update { it.copy(
                dateSummaries = it.dateSummaries + summaries,
                mealCounts = it.mealCounts + mealCounts,
                isLoading = false,
                hasMore = summaries.size == pageSize
            )}
        }
    }

    /**
     * Ponovo ucitava sve do sada prikazane strane, cuvajuci prosireni dan.
     */
    private suspend fun reloadLoadedPages() {
        val limit = maxOf(pageSize, currentOffset)
        val summaries = repository.getDateSummariesPaginated(limit, 0)
        val mealCounts = repository.getMealCountsForDates(summaries.map { it.date })
        val expandedDate = _uiState.value.expandedDate
            ?.takeIf { date -> summaries.any { it.date == date } }
        val expandedEntries = expandedDate?.let { repository.getEntriesForDateFromDb(it) }.orEmpty()

        currentOffset = summaries.size
        _uiState.update { it.copy(
            dateSummaries = summaries,
            mealCounts = mealCounts,
            isLoading = false,
            hasMore = summaries.size == limit,
            expandedDate = expandedDate,
            expandedEntries = expandedEntries,
            isLoadingEntries = false
        )}
    }

    fun expandDate(date: LocalDate) {
        // Ako je vec expandovan, skupi
        if (_uiState.value.expandedDate == date) {
            collapseDate()
            return
        }

        _uiState.update { it.copy(expandedDate = date, isLoadingEntries = true, expandedEntries = emptyList()) }
        viewModelScope.launch {
            val entries = repository.getEntriesForDateFromDb(date)
            _uiState.update {
                if (it.expandedDate == date) {
                    it.copy(expandedEntries = entries, isLoadingEntries = false)
                } else it
            }
        }
    }

    fun collapseDate() {
        _uiState.update { it.copy(expandedDate = null, expandedEntries = emptyList()) }
    }

    fun deleteEntry(id: String) {
        // repository.delete osvezava entries flow -> reloadLoadedPages se poziva sam
        repository.delete(id)
    }

    /**
     * Brise sve unose za dati dan. Lista se osvezava preko entries flow-a.
     */
    fun deleteDay(date: LocalDate) {
        viewModelScope.launch {
            repository.deleteEntriesForDate(date)
        }
    }

    fun refresh() {
        viewModelScope.launch { reloadLoadedPages() }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KalorijeApplication
                return HistoryViewModel(app.foodRepository) as T
            }
        }
    }
}
