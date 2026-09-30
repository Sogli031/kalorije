package com.kalorije.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kalorije.app.data.db.entity.SavedFoodEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedFoodDao {

    @Query("SELECT * FROM saved_foods ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedFoodEntity>>

    @Query("SELECT * FROM saved_foods ORDER BY createdAt DESC")
    suspend fun getAll(): List<SavedFoodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(food: SavedFoodEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(foods: List<SavedFoodEntity>)

    @Update
    suspend fun update(food: SavedFoodEntity)

    @Query("DELETE FROM saved_foods WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM saved_foods")
    suspend fun deleteAll()
}
