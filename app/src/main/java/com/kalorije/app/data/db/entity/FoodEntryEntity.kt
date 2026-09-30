package com.kalorije.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(
    tableName = "food_entries",
    foreignKeys = [
        ForeignKey(
            entity = FoodProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["date"]),
        Index(value = ["productId"])
    ]
)
data class FoodEntryEntity(
    @PrimaryKey
    val id: String,
    val date: LocalDate,
    val createdAt: LocalDateTime,
    val productId: Long,
    val grams: Double,
    val mealType: String,
    val groupId: String? = null,
    val time: String? = null
)
