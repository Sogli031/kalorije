package com.kalorije.app.data.model

enum class MealType(val displayName: String) {
    BREAKFAST("Doručak"),
    LUNCH("Ručak"),
    DINNER("Večera"),
    SNACK("Užina");

    companion object {
        fun fromString(value: String): MealType {
            return entries.firstOrNull { it.name == value } ?: SNACK
        }
    }
}
