package com.kalorije.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "food_products",
    indices = [
        Index(value = ["name", "brand", "caloriesPer100g"], unique = true)
    ]
)
data class FoodProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val brand: String? = null,
    val imageUrl: String? = null,
    val caloriesPer100g: Double,
    val servingSize: Double? = null
)
