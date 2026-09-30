package com.kalorije.app.data.model

import java.time.LocalDate

/**
 * Profil za TDEE učenje — razdvojen za TRT i Natural stanje.
 * Čuva naučenu korekciju, datum poslednjeg ažuriranja, broj dana praćenja,
 * i indikator pouzdanosti.
 */
data class TdeeProfile(
    val learnedAdjustment: Int = 0,
    val lastUpdated: LocalDate? = null,
    val trackingDays: Int = 0,
    val isConfident: Boolean = false
)
