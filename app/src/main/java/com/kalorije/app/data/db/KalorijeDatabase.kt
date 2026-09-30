package com.kalorije.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kalorije.app.data.db.dao.FoodEntryDao
import com.kalorije.app.data.db.dao.FoodProductDao
import com.kalorije.app.data.db.dao.SavedFoodDao
import com.kalorije.app.data.db.entity.FoodEntryEntity
import com.kalorije.app.data.db.entity.FoodProductEntity
import com.kalorije.app.data.db.entity.SavedFoodEntity

@Database(
    entities = [
        FoodProductEntity::class,
        FoodEntryEntity::class,
        SavedFoodEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class KalorijeDatabase : RoomDatabase() {

    abstract fun foodProductDao(): FoodProductDao
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun savedFoodDao(): SavedFoodDao

    companion object {
        @Volatile
        private var INSTANCE: KalorijeDatabase? = null

        /**
         * v1 → v2: uklonjeno praćenje makronutrijenata.
         * Kolone proteinPer100g / fatPer100g / carbsPer100g se brišu iz food_products
         * i saved_foods. Kalorije, gramaže i svi unosi hrane ostaju netaknuti.
         *
         * Tabele se rekreiraju ručno (ne DROP COLUMN) jer SQLite na starijim
         * Androidima to ne podržava (DROP COLUMN je tek od SQLite 3.35, minSdk je 26).
         *
         * CREATE naredbe su doslovno one koje Room generiše za v2 (app/schemas/.../2.json) —
         * ako se razlikuju, Room pri otvaranju baze puca sa "Migration didn't properly
         * handle" greškom.
         *
         * defer_foreign_keys odlaže proveru stranih ključeva do kraja transakcije:
         * food_entries ima FK na food_products sa ON DELETE RESTRICT, pa bi DROP
         * roditeljske tabele inače pao na constraint grešci. Pošto se id-jevi
         * prenose nepromenjeni, na kraju transakcije su svi FK-ovi ponovo validni.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA defer_foreign_keys = TRUE")

                // --- food_products ---
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `food_products_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`brand` TEXT, " +
                        "`imageUrl` TEXT, " +
                        "`caloriesPer100g` REAL NOT NULL, " +
                        "`servingSize` REAL)"
                )
                db.execSQL(
                    "INSERT INTO `food_products_new` (`id`, `name`, `brand`, `imageUrl`, `caloriesPer100g`, `servingSize`) " +
                        "SELECT `id`, `name`, `brand`, `imageUrl`, `caloriesPer100g`, `servingSize` FROM `food_products`"
                )
                db.execSQL("DROP TABLE `food_products`")
                db.execSQL("ALTER TABLE `food_products_new` RENAME TO `food_products`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_food_products_name_brand_caloriesPer100g` " +
                        "ON `food_products` (`name`, `brand`, `caloriesPer100g`)"
                )

                // --- saved_foods ---
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_foods_new` (" +
                        "`id` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`caloriesPer100g` REAL NOT NULL, " +
                        "`defaultGrams` REAL NOT NULL, " +
                        "`createdAt` TEXT NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "INSERT INTO `saved_foods_new` (`id`, `name`, `caloriesPer100g`, `defaultGrams`, `createdAt`) " +
                        "SELECT `id`, `name`, `caloriesPer100g`, `defaultGrams`, `createdAt` FROM `saved_foods`"
                )
                db.execSQL("DROP TABLE `saved_foods`")
                db.execSQL("ALTER TABLE `saved_foods_new` RENAME TO `saved_foods`")

                // food_entries se ne dira — tabela i njeni indeksi ostaju nepromenjeni
            }
        }

        fun getInstance(context: Context): KalorijeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KalorijeDatabase::class.java,
                    "kalorije.db"
                ).addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
