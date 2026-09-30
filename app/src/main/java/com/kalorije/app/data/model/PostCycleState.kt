package com.kalorije.app.data.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Stanje post-cycle stabilizacije nakon završetka TRT ciklusa.
 * Tokom stabilizacije TDEE učenje je zamrznuto jer vodena retencija opada
 * i signal težine nije pouzdan.
 */
data class PostCycleState(
    val cycleEndDate: LocalDate,
    val stabilizationDays: Int = 21
) {
    /** Broj dana od završetka ciklusa */
    val daysSinceEnd: Int
        get() = ChronoUnit.DAYS.between(cycleEndDate, LocalDate.now()).toInt().coerceAtLeast(0)

    /** Da li je stabilizacija još uvek aktivna */
    val isActive: Boolean
        get() = daysSinceEnd < stabilizationDays

    /** Preostali dani stabilizacije */
    val daysRemaining: Int
        get() = (stabilizationDays - daysSinceEnd).coerceAtLeast(0)

    /** Poruka o statusu stabilizacije po fazama */
    val statusMessage: String
        get() = when {
            daysSinceEnd < 7 -> "Rana stabilizacija — vodena retencija opada. TDEE učenje je pauzirano."
            daysSinceEnd < 14 -> "Srednja faza — težina se normalizuje. Još ${daysRemaining} dana do kraja pauze."
            daysSinceEnd < stabilizationDays -> "Završna faza — skoro gotovo. Još ${daysRemaining} dana."
            else -> "Stabilizacija završena. TDEE učenje je ponovo aktivno."
        }
}
