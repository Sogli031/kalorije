package com.kalorije.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "saved_foods")
data class SavedFoodEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val caloriesPer100g: Double,
    val defaultGrams: Double = 100.0,
    val createdAt: LocalDateTime
)
