package com.kalorije.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kalorije.app.data.db.entity.FoodProductEntity

@Dao
interface FoodProductDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(product: FoodProductEntity): Long

    @Query("""
        SELECT id FROM food_products
        WHERE name = :name AND brand IS :brand AND caloriesPer100g = :caloriesPer100g
        LIMIT 1
    """)
    suspend fun findExistingId(name: String, brand: String?, caloriesPer100g: Double): Long?

    @Query("SELECT * FROM food_products WHERE id = :id")
    suspend fun getById(id: Long): FoodProductEntity?

    @Query("SELECT * FROM food_products")
    suspend fun getAll(): List<FoodProductEntity>
}
