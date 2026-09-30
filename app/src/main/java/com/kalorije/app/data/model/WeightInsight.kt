package com.kalorije.app.data.model

/**
 * Insight about weight progress compared to calorie intake
 */
data class WeightInsight(
    val type: InsightType,
    val message: String,
    val avgCaloriesConsumed: Int,
    val targetCalories: Int,
    val caloriesDiff: Int,           // positive = ate more, negative = ate less
    val actualWeeklyChange: Double,  // kg/week
    val targetWeeklyChange: Double,  // kg/week
    val weightDiff: Double           // positive = gained more, negative = gained less than target
)

enum class InsightType {
    ON_TRACK,           // Everything matches
    OVEREATING_EFFECT,  // Ate more → gained more (expected behavior)
    UNDEREATING_EFFECT, // Ate less → gained less/lost more (expected behavior)
    TDEE_HIGHER,        // Ate more but on target → real TDEE is higher
    TDEE_LOWER,         // Ate on plan but gained more → real TDEE is lower
    INSUFFICIENT_DATA   // Not enough data to analyze
}
