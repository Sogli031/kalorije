package com.kalorije.app.data.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class FoodEntry(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val product: FoodProduct,
    val grams: Double,
    val mealType: MealType = MealType.SNACK,
    val groupId: String? = null, // Za grupisanje vise jela unetih zajedno
    val time: String? = null // HH:mm format - vreme kada je obrok pojedjen
) {
    val calories: Double get() = product.caloriesPer100g * grams / 100.0
}
