package com.kalorije.app.data.model

import java.time.LocalDate

data class WeightEntry(
    val date: LocalDate,
    val weightKg: Double,
    val time: String? = null // HH:mm format - vreme merenja
)
