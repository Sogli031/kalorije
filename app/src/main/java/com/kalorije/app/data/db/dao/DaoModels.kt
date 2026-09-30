package com.kalorije.app.data.db.dao

import java.time.LocalDate

data class DateSummary(
    val date: LocalDate,
    val totalCalories: Double,
    val entryCount: Int
)

data class DateCalories(
    val date: LocalDate,
    val totalCalories: Double
)
