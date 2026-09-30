package com.kalorije.app.data.model

/**
 * Dnevni zbir unosa. App prati samo kalorije.
 */
data class DailySummary(
    val calories: Double = 0.0
) {
    companion object {
        fun fromEntries(entries: List<FoodEntry>): DailySummary {
            return DailySummary(calories = entries.sumOf { it.calories })
        }
    }
}
