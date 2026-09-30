package com.kalorije.app.data.db.mapper

import com.kalorije.app.data.db.entity.FoodEntryEntity
import com.kalorije.app.data.db.entity.FoodProductEntity
import com.kalorije.app.data.db.entity.SavedFoodEntity
import com.kalorije.app.data.db.relation.FoodEntryWithProduct
import com.kalorije.app.data.model.FoodEntry
import com.kalorije.app.data.model.FoodProduct
import com.kalorije.app.data.model.MealType
import com.kalorije.app.data.model.SavedFood

// --- FoodProduct maperi ---

fun FoodProduct.toEntity(): FoodProductEntity = FoodProductEntity(
    name = name,
    brand = brand,
    imageUrl = imageUrl,
    caloriesPer100g = caloriesPer100g,
    servingSize = servingSize
)

fun FoodProductEntity.toDomain(): FoodProduct = FoodProduct(
    id = id.toString(),
    name = name,
    brand = brand,
    imageUrl = imageUrl,
    caloriesPer100g = caloriesPer100g,
    servingSize = servingSize
)

// --- FoodEntry maperi ---

fun FoodEntry.toEntity(productId: Long): FoodEntryEntity = FoodEntryEntity(
    id = id,
    date = date,
    createdAt = createdAt,
    productId = productId,
    grams = grams,
    mealType = mealType.name,
    groupId = groupId,
    time = time
)

fun FoodEntryWithProduct.toDomain(): FoodEntry = FoodEntry(
    id = entry.id,
    date = entry.date,
    createdAt = entry.createdAt,
    product = product.toDomain(),
    grams = entry.grams,
    mealType = MealType.fromString(entry.mealType),
    groupId = entry.groupId,
    time = entry.time
)

// --- SavedFood maperi ---

fun SavedFood.toEntity(): SavedFoodEntity = SavedFoodEntity(
    id = id,
    name = name,
    caloriesPer100g = caloriesPer100g,
    defaultGrams = defaultGrams,
    createdAt = createdAt
)

fun SavedFoodEntity.toDomain(): SavedFood = SavedFood(
    id = id,
    name = name,
    caloriesPer100g = caloriesPer100g,
    defaultGrams = defaultGrams,
    createdAt = createdAt
)
