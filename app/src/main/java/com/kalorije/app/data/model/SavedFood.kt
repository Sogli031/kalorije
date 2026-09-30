package com.kalorije.app.data.model

import java.time.LocalDateTime
import java.util.UUID

data class SavedFood(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val caloriesPer100g: Double,
    val defaultGrams: Double = 100.0,
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    fun toFoodProduct(): FoodProduct {
        return FoodProduct(
            id = id,
            name = name,
            caloriesPer100g = caloriesPer100g
        )
    }

    companion object {
        fun fromFoodProduct(product: FoodProduct, grams: Double = 100.0): SavedFood {
            return SavedFood(
                name = product.name,
                caloriesPer100g = product.caloriesPer100g,
                defaultGrams = grams
            )
        }
    }
}
