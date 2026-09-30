package com.kalorije.app.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

val LOGICAL_DAY_START_TIME: LocalTime = LocalTime.of(5, 0)

/**
 * Vraća "logički datum" za unos hrane.
 * Ako je trenutno vreme pre 5 ujutru, smatra se da je još uvek prethodni dan.
 * Npr. ako jedeš u 2 ujutru, to se računa kao sinoćnji obrok.
 */
fun getLogicalDate(): LocalDate {
    val now = LocalTime.now()

    return if (now.isBefore(LOGICAL_DAY_START_TIME)) {
        LocalDate.now().minusDays(1)
    } else {
        LocalDate.now()
    }
}

/**
 * Pretvara datum i vreme obroka u LocalDateTime koji poštuje logički početak dana.
 *
 * Vremena pre 05:00 pripadaju "kraju" tog logičkog dana, pa se za sortiranje
 * pomeraju na sledeći kalendarski dan.
 */
fun toLogicalMealDateTime(date: LocalDate, time: LocalTime): LocalDateTime {
    val effectiveDate = if (time.isBefore(LOGICAL_DAY_START_TIME)) {
        date.plusDays(1)
    } else {
        date
    }
    return LocalDateTime.of(effectiveDate, time)
}
