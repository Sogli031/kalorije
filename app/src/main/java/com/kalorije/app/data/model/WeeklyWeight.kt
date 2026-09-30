package com.kalorije.app.data.model

import java.time.LocalDate

/**
 * Prosečna težina za jedan 7-dnevni period ("nedelju").
 *
 * Periodi kreću od dana prvog merenja (UserProfile.weightPeriodStartDate), ne od
 * ponedeljka: dan 1–7 je prva nedelja, 8–14 druga, itd.
 *
 * Dnevna merenja variraju zbog vode, hrane u crevima, soli itd. — prosek
 * cele nedelje je mnogo stabilniji pokazatelj od bilo kog pojedinačnog dana,
 * pa se on tretira kao "težina te nedelje".
 */
data class WeeklyWeight(
    val weekStart: LocalDate,     // prvi dan perioda
    val averageKg: Double,
    val daysMeasured: Int,        // koliko različitih dana ima merenje (0..7)
    val isComplete: Boolean       // da li je nedelja već prošla (danas > nedelja uveče)
) {
    val weekEnd: LocalDate get() = weekStart.plusDays(6)

    /** Nedelja važi samo ako ima dovoljno merenja — sa 3 ili manje prosek je previše nepouzdan. */
    val isValid: Boolean get() = daysMeasured >= MIN_MEASURED_DAYS

    companion object {
        /**
         * Minimalan broj dana sa merenjem da bi nedeljni prosek važio.
         * Propušten dan-dva ne smeta; ako fale 4+ dana (tj. ima ≤3 merenja), nedelja se ne računa.
         */
        const val MIN_MEASURED_DAYS = 4
    }
}
