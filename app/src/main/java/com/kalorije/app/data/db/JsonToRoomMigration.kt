package com.kalorije.app.data.db

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kalorije.app.BuildConfig
import com.kalorije.app.data.db.dao.FoodProductDao
import com.kalorije.app.data.db.entity.FoodEntryEntity
import com.kalorije.app.data.db.entity.FoodProductEntity
import com.kalorije.app.data.db.entity.SavedFoodEntity
import com.kalorije.app.data.model.FoodEntry
import com.kalorije.app.data.model.FoodProduct
import com.kalorije.app.data.model.SavedFood
import com.kalorije.app.util.GsonProvider
import java.io.File
import java.lang.reflect.Type

// Migracija podataka iz JSON fajlova u Room bazu
object JsonToRoomMigration {

    private const val TAG = "JsonToRoomMigration"
    private const val PREFS_NAME = "kalorije_prefs"
    private const val KEY_MIGRATED = "json_to_room_migrated"

    private val FOOD_ENTRY_LIST_TYPE: Type = object : TypeToken<List<FoodEntry>>() {}.type
    private val SAVED_FOOD_LIST_TYPE: Type = object : TypeToken<List<SavedFood>>() {}.type

    // Glavna metoda — poziva se jednom pri pokretanju aplikacije.
    // Ako je migracija već izvršena, odmah se vraća.
    suspend fun migrateIfNeeded(context: Context, db: KalorijeDatabase) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MIGRATED, false)) {
            log("Migracija već izvršena, preskačem.")
            return
        }

        log("Započinjem migraciju iz JSON-a u Room...")

        val gson = GsonProvider.instance
        val filesDir = context.filesDir

        // Učitavanje JSON fajlova
        val entries: List<FoodEntry> = readJsonFile(
            File(filesDir, "food_entries.json"),
            FOOD_ENTRY_LIST_TYPE,
            gson
        )
        val savedFoods: List<SavedFood> = readJsonFile(
            File(filesDir, "saved_foods.json"),
            SAVED_FOOD_LIST_TYPE,
            gson
        )

        log("Učitano ${entries.size} unosa i ${savedFoods.size} sačuvanih namirnica.")

        if (entries.isEmpty() && savedFoods.isEmpty()) {
            log("Nema podataka za migraciju, markiram kao završenu.")
            prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
            return
        }

        // Keš za mapiranje FoodProduct -> productId u bazi
        // Ključ: "name|brand|caloriesPer100g"
        val productIdCache = mutableMapOf<String, Long>()

        db.withTransaction {
            val productDao = db.foodProductDao()
            val entryDao = db.foodEntryDao()
            val savedFoodDao = db.savedFoodDao()

            // Ekstrahovanje unikatnih proizvoda iz entry-ja i umetanje u bazu
            val entryEntities = entries.map { entry ->
                val product = entry.product
                val cacheKey = buildProductKey(product)

                val productId = productIdCache.getOrPut(cacheKey) {
                    resolveProductId(productDao, product)
                }

                FoodEntryEntity(
                    id = entry.id,
                    date = entry.date,
                    createdAt = entry.createdAt,
                    productId = productId,
                    grams = entry.grams,
                    mealType = entry.mealType.name,
                    groupId = entry.groupId,
                    time = entry.time
                )
            }

            // Umetanje svih entry-ja
            if (entryEntities.isNotEmpty()) {
                entryDao.insertAll(entryEntities)
                log("Umetnuto ${entryEntities.size} unosa hrane.")
            }

            // Umetanje sačuvanih namirnica
            if (savedFoods.isNotEmpty()) {
                val savedFoodEntities = savedFoods.map { food ->
                    SavedFoodEntity(
                        id = food.id,
                        name = food.name,
                        caloriesPer100g = food.caloriesPer100g,
                        defaultGrams = food.defaultGrams,
                        createdAt = food.createdAt
                    )
                }
                savedFoodDao.insertAll(savedFoodEntities)
                log("Umetnuto ${savedFoodEntities.size} sačuvanih namirnica.")
            }
        }

        // Čuvanje flaga tek POSLE uspešne transakcije
        prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
        log("Migracija uspešno završena.")
    }

    // Pronalazi ili kreira FoodProductEntity i vraća njen ID
    private suspend fun resolveProductId(
        dao: FoodProductDao,
        product: FoodProduct
    ): Long {
        // Pokušaj da nađeš postojeći proizvod
        val existingId = dao.findExistingId(
            name = product.name,
            brand = product.brand,
            caloriesPer100g = product.caloriesPer100g
        )
        if (existingId != null) {
            return existingId
        }

        // Umetni novi proizvod
        val entity = FoodProductEntity(
            name = product.name,
            brand = product.brand,
            imageUrl = product.imageUrl,
            caloriesPer100g = product.caloriesPer100g,
            servingSize = product.servingSize
        )
        val insertedId = dao.insert(entity)

        // IGNORE strategija vraća -1 ako postoji duplikat (race condition)
        if (insertedId != -1L) {
            return insertedId
        }

        // Fallback — duplikat umetnut između findExistingId i insert poziva
        return dao.findExistingId(
            name = product.name,
            brand = product.brand,
            caloriesPer100g = product.caloriesPer100g
        ) ?: throw IllegalStateException(
            "Ne mogu da pronađem niti umetnem proizvod: ${product.name}"
        )
    }

    private fun buildProductKey(product: FoodProduct): String {
        return "${product.name}|${product.brand}|${product.caloriesPer100g}"
    }

    private fun <T> readJsonFile(file: File, type: Type, gson: Gson): List<T> {
        if (!file.exists()) {
            log("Fajl ne postoji: ${file.name}")
            return emptyList()
        }
        return try {
            val json = file.readText()
            if (json.isBlank()) {
                log("Fajl je prazan: ${file.name}")
                emptyList()
            } else {
                gson.fromJson<List<T>>(json, type) ?: emptyList()
            }
        } catch (e: Exception) {
            log("Greška pri čitanju ${file.name}: ${e.message}")
            emptyList()
        }
    }

    private fun log(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }
}
