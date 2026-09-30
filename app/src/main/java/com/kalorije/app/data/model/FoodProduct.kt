package com.kalorije.app.data.model

data class FoodProduct(
    val id: String? = null,
    val name: String,
    val brand: String? = null,
    val imageUrl: String? = null,
    val caloriesPer100g: Double,
    val servingSize: Double? = null
) {
    val displayName: String
        get() = if (brand.isNullOrBlank()) name else "$name ($brand)"
}
