package com.kalorije.app.ui.screens.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kalorije.app.KalorijeApplication
import com.kalorije.app.data.model.FoodEntry
import com.kalorije.app.data.model.UserProfile
import com.kalorije.app.data.model.WeightEntry
import com.kalorije.app.data.model.WeeklyWeight
import com.kalorije.app.data.model.WeightGoal
import com.kalorije.app.data.model.WeightInsight
import com.kalorije.app.data.repository.FoodRepository
import com.kalorije.app.util.PartitionCalculator
import com.kalorije.app.util.PartitionData
import com.kalorije.app.util.PartitionEstimate
import com.kalorije.app.util.WeeklyWeightCalculator
import com.kalorije.app.util.WeightNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * TRT kartica data - prekalkulisane vrednosti za TRT Water Retention Card
 */
data class TrtCardData(
    val weeksOnCycle: Int,
    val estimatedWater: Double,
    val waterLow: Double,
    val waterHigh: Double,
    val dryWeight: Double,
    val leanGain: Double,
    val waterPercent: Double,
    val weeklyTissueGain: Double,
    val actualAvgCalories: Int,
    val formulaWater: Double,
    val totalChange: Double
)

/**
 * UI State za WeightProgressScreen
 */
data class WeightProgressUiState(
    val sortedHistory: List<WeightEntry> = emptyList(),
    /** Nedeljni proseci (7-dnevni periodi od prvog merenja), najnovija nedelja prva. Sadrži i nevažeće nedelje (isValid = false). */
    val weeklyWeights: List<WeeklyWeight> = emptyList(),
    /**
     * "Trenutna" težina za prikaz: prosek poslednje VAŽEĆE nedelje (≥4 merenja),
     * a dok takva ne postoji — poslednje dnevno merenje.
     */
    val currentWeight: Double = 0.0,
    /** true ako je [currentWeight] nedeljni prosek, false ako je pojedinačno merenje */
    val currentWeightIsWeekly: Boolean = false,
    val insight: WeightInsight? = null,
    val partitionData: PartitionData? = null,
    val avgDailyCalories: Int? = null,
    val trtData: TrtCardData? = null
)

/**
 * ViewModel za WeightProgressScreen — upravlja svim kalkulacijama i business logikom
 */
class WeightProgressViewModel(private val repository: FoodRepository) : ViewModel() {

    // StateFlow-ovi iz repository-ja
    val profile: StateFlow<UserProfile> = repository.profile

    private val foodEntries: StateFlow<List<FoodEntry>> = repository.entries

    // UI State
    private val _uiState = MutableStateFlow(WeightProgressUiState())
    val uiState: StateFlow<WeightProgressUiState> = _uiState.asStateFlow()

    // Keširane vrednosti za izbegavanje nepotrebnih rekalkulacija
    private var lastWeightHistory: List<WeightEntry>? = null
    private var lastProfile: UserProfile? = null
    private var cachedInsight: WeightInsight? = null
    private var cachedPartitionData: PartitionData? = null
    private var cachedTrtData: TrtCardData? = null
    private var cachedSortedHistory: List<WeightEntry> = emptyList()
    private var cachedWeeklyWeights: List<WeeklyWeight> = emptyList()
    private var cachedCurrentWeight: Double = 0.0
    private var cachedCurrentWeightIsWeekly: Boolean = false

    init {
        // Osluškuj promene u profilu i entries, pa rekalkuliši UI state
        viewModelScope.launch {
            combine(repository.profile, repository.entries) { prof, entries ->
                prof to entries
            }.distinctUntilChanged().collect { (prof, entries) ->
                recalculate(prof, entries)
            }
        }
    }

    /**
     * Rekalkuliši sve vrednosti u UI state na osnovu profile i entries.
     * Koristi memoizaciju — skupe kalkulacije (partition, TRT, insight) se ponovo
     * računaju samo kad se weightHistory ili profil promeni, ne kad se doda food entry.
     */
    private suspend fun recalculate(profile: UserProfile, entries: List<FoodEntry>) {
        withContext(Dispatchers.Default) {
            val weightHistoryChanged = lastWeightHistory !== profile.weightHistory
                || lastWeightHistory?.size != profile.weightHistory.size
            val profileChanged = lastProfile !== profile

            val needsFullRecalc = weightHistoryChanged || profileChanged

            val sortedHistory: List<WeightEntry>
            val weeklyWeights: List<WeeklyWeight>
            val currentWeight: Double
            val currentWeightIsWeekly: Boolean
            val insight: WeightInsight?
            val partitionData: PartitionData?
            val trtData: TrtCardData?

            if (needsFullRecalc) {
                // Profil ili istorija težina se promenila — rekalkuliši sve
                sortedHistory = profile.weightHistory.sortedByDescending { it.date }
                weeklyWeights = WeeklyWeightCalculator.compute(
                    history = profile.weightHistory,
                    anchor = profile.weeklyPeriodAnchor
                ).asReversed()
                val latestValidWeek = weeklyWeights.firstOrNull { it.isValid }
                currentWeight = latestValidWeek?.averageKg ?: profile.currentWeight
                currentWeightIsWeekly = latestValidWeek != null
                insight = repository.getWeightInsight()
                partitionData = calculatePartitionData(profile, entries, insight)
                trtData = calculateTrtData(profile, insight)

                // Ažuriraj keš
                lastWeightHistory = profile.weightHistory
                lastProfile = profile
                cachedInsight = insight
                cachedPartitionData = partitionData
                cachedTrtData = trtData
                cachedSortedHistory = sortedHistory
                cachedWeeklyWeights = weeklyWeights
                cachedCurrentWeight = currentWeight
                cachedCurrentWeightIsWeekly = currentWeightIsWeekly
            } else {
                // Samo entries su se promenile — koristi keširane vrednosti
                sortedHistory = cachedSortedHistory
                weeklyWeights = cachedWeeklyWeights
                currentWeight = cachedCurrentWeight
                currentWeightIsWeekly = cachedCurrentWeightIsWeekly
                insight = cachedInsight
                partitionData = cachedPartitionData
                trtData = cachedTrtData
            }

            // Prosečan dnevni kalorijski unos se uvek rekalkuliše jer zavisi od entries
            val avgDailyCalories = calculateAvgDailyCalories(entries)

            // Ažuriraj UI state
            _uiState.value = WeightProgressUiState(
                sortedHistory = sortedHistory,
                weeklyWeights = weeklyWeights,
                currentWeight = currentWeight,
                currentWeightIsWeekly = currentWeightIsWeekly,
                insight = insight,
                partitionData = partitionData,
                avgDailyCalories = avgDailyCalories,
                trtData = trtData
            )
        }
    }

    /**
     * Izračunaj partition data — P-ratio kartica
     */
    private fun calculatePartitionData(
        profile: UserProfile,
        entries: List<FoodEntry>,
        insight: WeightInsight?
    ): PartitionData? {
        val totalGain = profile.totalWeightChange ?: 0.0
        val trackingWeeks = profile.trackingDays / 7

        // Potrebno: dobitak ≥0.5kg i bar 1 nedelja praćenja
        if (totalGain < 0.5 || trackingWeeks < 1) return null

        // Izračunaj procenjenu vodu ako je na TRT-u
        val estimatedWater = if (profile.useTrt && profile.trtStartDate != null) {
            val weeksExact = (profile.daysOnTrt ?: 0) / 7.0
            val formulaWater = WeightNormalizer.estimateWaterRetention(
                weeksOnCycle = weeksExact,
                doseMgPerWeek = profile.trtDoseMgWeek,
                usingAi = profile.usesAromataseInhibitor
            )

            // Izračunaj procenjeno tkivo iz kalorijskog viška IZNAD TDEE
            val estimatedTissueFromCalories = insight?.let {
                val caloriesSurplusFromTdee = it.avgCaloriesConsumed - profile.tdee.toInt()
                val weeksTracked = (profile.trackingDays / 7.0).coerceAtLeast(1.0)
                (caloriesSurplusFromTdee * weeksTracked * 7) / UserProfile.KCAL_PER_KG
            } ?: 0.0

            // Voda ne može biti veća od (ukupna promena - procenjeno tkivo iz kalorija)
            val maxPossibleWater = if (totalGain > 0 && estimatedTissueFromCalories > 0) {
                (totalGain - estimatedTissueFromCalories * 0.5).coerceAtLeast(0.0)
            } else {
                totalGain.coerceAtLeast(0.0)
            }

            // Koristi manju vrednost: formula ili maksimalno moguće
            if (totalGain > 0) {
                formulaWater.coerceAtMost(maxPossibleWater).coerceAtMost(totalGain)
            } else {
                formulaWater
            }
        } else 0.0

        // Suvi dobitak = ukupno - voda
        val dryGain = (totalGain - estimatedWater).coerceAtLeast(0.0)

        // Dnevni kalorijski višak
        val dailySurplus = insight?.let {
            it.avgCaloriesConsumed - profile.tdee.toInt()
        } ?: profile.dailySurplus

        // Particija se računa samo za SUVI dobitak (bez vode)
        val estimate = PartitionCalculator.calculatePartition(
            totalWeightGain = if (profile.useTrt) dryGain else totalGain,
            fatMassKg = profile.estimatedFatMassKg,
            resistanceTrainingDays = profile.resistanceTrainingDays,
            isOnTrt = profile.useTrt,
            surplusKcalPerDay = dailySurplus
        )

        return estimate?.let {
            PartitionData(
                estimate = it,
                totalGain = totalGain,
                estimatedWater = estimatedWater,
                dryGain = dryGain,
                surplusKcalPerDay = dailySurplus
            )
        }
    }

    /**
     * Izračunaj prosečan dnevni unos kalorija iz poslednjih 14 dana (excluding today)
     */
    private fun calculateAvgDailyCalories(entries: List<FoodEntry>): Int? {
        val today = LocalDate.now()
        val daysToLookBack = 14
        val recentEntries = entries.filter { entry ->
            val daysDiff = java.time.temporal.ChronoUnit.DAYS.between(entry.date, today)
            daysDiff in 1..daysToLookBack
        }
        if (recentEntries.isEmpty()) return null

        val distinctDays = recentEntries.map { it.date }.distinct().size
        return if (distinctDays > 0) {
            (recentEntries.sumOf { it.calories.toDouble() } / distinctDays).toInt()
        } else null
    }

    /**
     * Izračunaj TRT data — prekalkulisane vrednosti za TRT karticu
     */
    private fun calculateTrtData(profile: UserProfile, insight: WeightInsight?): TrtCardData? {
        if (!profile.useTrt || profile.trtStartDate == null) return null

        val weeksOnCycle = profile.weeksOnTrt ?: 1
        val weeksExact = (profile.daysOnTrt ?: 0) / 7.0
        val formulaWater = WeightNormalizer.estimateWaterRetention(
            weeksOnCycle = weeksExact,
            doseMgPerWeek = profile.trtDoseMgWeek,
            usingAi = profile.usesAromataseInhibitor
        )
        val (waterLow, _, waterHigh) = WeightNormalizer.estimateWaterRetentionRange(
            weeksOnCycle = weeksExact,
            doseMgPerWeek = profile.trtDoseMgWeek,
            usingAi = profile.usesAromataseInhibitor
        )

        val startWeight = profile.startingWeight ?: profile.currentWeight
        val totalChange = profile.totalWeightChange ?: 0.0

        // Izračunaj procenjeno tkivo iz kalorijskog viška IZNAD TDEE
        val estimatedTissueFromCalories = if (insight != null && insight.actualWeeklyChange != 0.0) {
            val caloriesSurplusFromTdee = insight.avgCaloriesConsumed - profile.tdee.toInt()
            val weeksTracked = (profile.trackingDays / 7.0).coerceAtLeast(1.0)
            (caloriesSurplusFromTdee * weeksTracked * 7) / UserProfile.KCAL_PER_KG
        } else {
            0.0
        }

        // Voda ne može biti veća od (ukupna promena - procenjeno tkivo iz kalorija)
        val maxPossibleWater = if (totalChange > 0 && estimatedTissueFromCalories > 0) {
            (totalChange - estimatedTissueFromCalories * 0.5).coerceAtLeast(0.0)
        } else {
            totalChange.coerceAtLeast(0.0)
        }

        // Koristi manju vrednost: formula ili maksimalno moguće
        val estimatedWater = if (totalChange > 0) {
            formulaWater.coerceAtMost(maxPossibleWater).coerceAtMost(totalChange)
        } else {
            formulaWater
        }

        val dryWeight = profile.currentWeight - estimatedWater
        val leanGain = dryWeight - startWeight

        // Procena koliko % promene je voda
        val waterPercent = if (totalChange > 0) {
            ((estimatedWater / totalChange) * 100).coerceIn(0.0, 99.0)
        } else 0.0

        // Izračunaj nedeljni tempo dobijanja tkiva
        val weeklyTissueGain = if (weeksOnCycle > 0 && leanGain != 0.0) {
            leanGain / weeksOnCycle
        } else 0.0

        // Koristi isti prosek kao Uvid kartica za konzistentnost
        val actualAvgCalories = insight?.avgCaloriesConsumed ?: profile.targetCalories

        return TrtCardData(
            weeksOnCycle = weeksOnCycle,
            estimatedWater = estimatedWater,
            waterLow = waterLow.coerceAtMost(estimatedWater),
            waterHigh = waterHigh.coerceAtMost(totalChange.coerceAtLeast(formulaWater)),
            dryWeight = dryWeight,
            leanGain = leanGain,
            waterPercent = waterPercent,
            weeklyTissueGain = weeklyTissueGain,
            actualAvgCalories = actualAvgCalories,
            formulaWater = formulaWater,
            totalChange = totalChange
        )
    }

    // ====== AKCIJE (public metode) ======

    fun addWeightEntry(weight: Double, time: String) {
        viewModelScope.launch {
            repository.addWeightEntry(weight, time)
        }
    }

    fun deleteWeightEntry(date: LocalDate) {
        viewModelScope.launch {
            repository.deleteWeightEntry(date)
        }
    }

    fun resetWeightProgress() {
        viewModelScope.launch {
            repository.resetWeightProgress()
        }
    }

    fun setGoalWeight(weight: Double?) {
        viewModelScope.launch {
            repository.setGoalWeight(weight)
        }
    }

    fun setFixedStartingWeight(weight: Double) {
        viewModelScope.launch {
            repository.setFixedStartingWeightNormalized(weight, null)
        }
    }

    fun recalculateTdee() {
        viewModelScope.launch {
            repository.recalculateTdeeFromHistory()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KalorijeApplication
                WeightProgressViewModel(app.foodRepository)
            }
        }
    }
}
