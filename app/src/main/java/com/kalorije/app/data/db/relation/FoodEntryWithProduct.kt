package com.kalorije.app.data.db.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.kalorije.app.data.db.entity.FoodEntryEntity
import com.kalorije.app.data.db.entity.FoodProductEntity

data class FoodEntryWithProduct(
    @Embedded val entry: FoodEntryEntity,
    @Relation(
        parentColumn = "productId",
        entityColumn = "id"
    )
    val product: FoodProductEntity
)
