package com.kalorije.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.kalorije.app.data.db.entity.FoodEntryEntity
import com.kalorije.app.data.db.relation.FoodEntryWithProduct
import com.kalorije.app.util.MealTiming
import java.time.LocalDate

@Dao
interface FoodEntryDao {

    @Transaction
    @Query("SELECT * FROM food_entries ORDER BY createdAt DESC")
    suspend fun getAllWithProducts(): List<FoodEntryWithProduct>

    @Transaction
    @Query("SELECT * FROM food_entries WHERE date = :date ORDER BY createdAt DESC")
    suspend fun getEntriesForDate(date: LocalDate): List<FoodEntryWithProduct>

    @Query("SELECT * FROM food_entries WHERE id = :id")
    suspend fun getById(id: String): FoodEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: FoodEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<FoodEntryEntity>)

    @Update
    suspend fun update(entry: FoodEntryEntity)

    @Query("DELETE FROM food_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM food_entries")
    suspend fun deleteAll()

    @Query("DELETE FROM food_entries WHERE date = :date")
    suspend fun deleteByDate(date: LocalDate)

    @Query("""
        SELECT
            date,
            SUM(p.caloriesPer100g * e.grams / 100.0) as totalCalories,
            COUNT(*) as entryCount
        FROM food_entries e
        INNER JOIN food_products p ON e.productId = p.id
        GROUP BY date
        ORDER BY date DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getDateSummaries(limit: Int, offset: Int): List<DateSummary>

    @Query("""
        SELECT
            date,
            SUM(p.caloriesPer100g * e.grams / 100.0) as totalCalories
        FROM food_entries e
        INNER JOIN food_products p ON e.productId = p.id
        GROUP BY date
    """)
    suspend fun getCaloriesByDate(): List<DateCalories>

    @Query("SELECT COUNT(DISTINCT date) FROM food_entries")
    suspend fun getDistinctDateCount(): Int

    /**
     * Lagani podaci za grupisanje unosa u obroke (bez proizvoda i količina).
     */
    @Query("""
        SELECT date, createdAt, groupId, time
        FROM food_entries
        WHERE date IN (:dates)
    """)
    suspend fun getMealTimingsForDates(dates: List<LocalDate>): List<MealTiming>
}
