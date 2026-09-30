package com.kalorije.app.data.model

import java.util.UUID
import kotlin.math.roundToInt

// Stavka u korpi za unos
data class MealCartItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val grams: Double,
    val caloriesPer100g: Double
) {
    val totalCalories: Int get() = (caloriesPer100g * grams / 100.0).roundToInt()
}
