package com.kalorije.app.util

import com.kalorije.app.data.model.UserProfile
import kotlin.math.abs

/**
 * Projekcija težine kroz vreme pri konstantnom dnevnom unosu.
 *
 * Za razliku od linearne projekcije (tempo × broj nedelja), ovde se TDEE
 * preračunava svake nedelje na osnovu nove težine. Bez toga se prognoza
 * postepeno kvari: na +6 kg TDEE poraste ~85 kcal/dan, pa linearna kriva
 * posle 12 nedelja precenjuje dobitak za oko pola kilograma.
 */
object WeightProjection {

    /**
     * @param startWeight Trenutna težina (kg)
     * @param dailyCalories Pretpostavljeni dnevni unos (kcal)
     * @param tdeeAtStart TDEE pri trenutnoj težini (kcal)
     * @param tdeeKcalPerKg Promena TDEE po kilogramu telesne mase
     * @param weeks Broj nedelja za projekciju
     * @return Lista težina po nedeljama (indeks 0 = kraj 1. nedelje)
     */
    fun project(
        startWeight: Double,
        dailyCalories: Int,
        tdeeAtStart: Double,
        tdeeKcalPerKg: Double,
        weeks: Int
    ): List<Double> {
        val result = mutableListOf<Double>()
        var weight = startWeight
        repeat(weeks) {
            val tdeeNow = tdeeAtStart + (weight - startWeight) * tdeeKcalPerKg
            val weeklyChange = ((dailyCalories - tdeeNow) * 7) / UserProfile.KCAL_PER_KG
            weight += weeklyChange
            result.add(weight)
        }
        return result
    }

    /**
     * Broj nedelja do ciljne težine uz preračunavanje TDEE.
     * Vraća null ako se cilj ne dostiže u okviru [maxWeeks] (npr. težina se
     * stabilizuje pre cilja jer TDEE "sustigne" unos).
     */
    fun weeksToGoal(
        startWeight: Double,
        goalWeight: Double,
        dailyCalories: Int,
        tdeeAtStart: Double,
        tdeeKcalPerKg: Double,
        maxWeeks: Int = 104
    ): Int? {
        val gaining = goalWeight > startWeight
        var weight = startWeight
        for (week in 1..maxWeeks) {
            val tdeeNow = tdeeAtStart + (weight - startWeight) * tdeeKcalPerKg
            val weeklyChange = ((dailyCalories - tdeeNow) * 7) / UserProfile.KCAL_PER_KG
            if (abs(weeklyChange) < 0.005) return null // stabilizovalo se pre cilja
            weight += weeklyChange
            if (gaining && weight >= goalWeight) return week
            if (!gaining && weight <= goalWeight) return week
        }
        return null
    }
}
