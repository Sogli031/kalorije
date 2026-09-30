package com.kalorije.app.util

/**
 * Procena kompozicije dobitka težine (P-ratio) pri kalorijskom višku.
 *
 * @property muscleGainMin Minimalna procena dobitka mišića (kg)
 * @property muscleGainMax Maksimalna procena dobitka mišića (kg)
 * @property fatGainMin Minimalna procena dobitka masti (kg)
 * @property fatGainMax Maksimalna procena dobitka masti (kg)
 * @property musclePercentMin Minimalni procenat dobitka koji je mišić
 * @property musclePercentMax Maksimalni procenat dobitka koji je mišić
 */
data class PartitionEstimate(
    val muscleGainMin: Double,
    val muscleGainMax: Double,
    val fatGainMin: Double,
    val fatGainMax: Double,
    val musclePercentMin: Double,
    val musclePercentMax: Double
) {
    /**
     * Srednja vrednost procenjenog dobitka mišića
     */
    val muscleGainMid: Double
        get() = (muscleGainMin + muscleGainMax) / 2

    /**
     * Srednja vrednost procenjenog dobitka masti
     */
    val fatGainMid: Double
        get() = (fatGainMin + fatGainMax) / 2

    /**
     * Srednji procenat mišića
     */
    val musclePercentMid: Double
        get() = (musclePercentMin + musclePercentMax) / 2
}

/**
 * Podaci za prikaz particije sa uključenom vodom (za TRT korisnike).
 */
data class PartitionData(
    val estimate: PartitionEstimate,
    val totalGain: Double,
    val estimatedWater: Double, // 0 ako nije na TRT-u
    val dryGain: Double, // totalGain - estimatedWater
    // Ulaz korišćen za procenu — UI mora da prikaže istu vrednost koja je ušla u račun
    val surplusKcalPerDay: Int = 0
)

/**
 * Kalkulator za procenu kalorijske particije (P-ratio).
 *
 * Koristi Forbes formulu kao bazu sa modifikatorima za:
 * - Trening s otporom
 * - TRT/AAS status
 * - Veličina kalorijskog viška
 *
 * VAŽNA NAPOMENA: Ove procene imaju veliku nesigurnost (±25-40%).
 * Forbes formula nije validirana za trenirane osobe i potcenjuje
 * dobitak mišića kod onih koji treniraju.
 */
object PartitionCalculator {

    /**
     * Izračunava procenu particije dobitka težine.
     *
     * @param totalWeightGain Ukupan dobitak težine u kg
     * @param fatMassKg Masa masti u kg (koristi profile.estimatedFatMassKg)
     * @param resistanceTrainingDays Broj dana treninga s otporom nedeljno (0-7)
     * @param isOnTrt Da li korisnik koristi TRT/AAS
     * @param surplusKcalPerDay Dnevni kalorijski višak iznad TDEE
     * @return [PartitionEstimate] sa procenama ili null ako nema dobitka
     */
    fun calculatePartition(
        totalWeightGain: Double,
        fatMassKg: Double,
        resistanceTrainingDays: Int,
        isOnTrt: Boolean,
        surplusKcalPerDay: Int
    ): PartitionEstimate? {
        // Samo za dobitak težine
        if (totalWeightGain <= 0) return null

        val (muscleRatioMin, muscleRatioMax) = leanFractionRange(
            fatMassKg = fatMassKg,
            resistanceTrainingDays = resistanceTrainingDays,
            isOnTrt = isOnTrt,
            surplusKcalPerDay = surplusKcalPerDay
        )

        // Izračunaj kg dobitka
        val muscleMin = totalWeightGain * muscleRatioMin
        val muscleMax = totalWeightGain * muscleRatioMax
        val fatMin = totalWeightGain - muscleMax // Minimum masti = ukupno - max mišića
        val fatMax = totalWeightGain - muscleMin // Maximum masti = ukupno - min mišića

        return PartitionEstimate(
            muscleGainMin = muscleMin,
            muscleGainMax = muscleMax,
            fatGainMin = fatMin.coerceAtLeast(0.0),
            fatGainMax = fatMax.coerceAtLeast(0.0),
            musclePercentMin = muscleRatioMin * 100,
            musclePercentMax = muscleRatioMax * 100
        )
    }

    /**
     * Udeo nemasne mase (FFM) u promeni težine — Forbes baza sa istim modifikatorima
     * koje koristi [calculatePartition]. Vraća (min, max) kao frakcije 0..1.
     *
     * Izdvojeno da bi i procena energetske gustine tkiva (v. [StreakAnalyzer])
     * koristila identičnu particiju, bez dupliranja pragova.
     */
    fun leanFractionRange(
        fatMassKg: Double,
        resistanceTrainingDays: Int,
        isOnTrt: Boolean,
        surplusKcalPerDay: Int
    ): Pair<Double, Double> {
        // 1. Bazna Forbes procena
        // Formula: dFFM/dBW = 10.4 / (10.4 + FM)
        // gde je FM = masa masti u kg
        val baseMuscleRatio = 10.4 / (10.4 + fatMassKg)

        // 2. Modifikatori (aditivni, u procentnim poenima)
        var modifierMin = 0.0
        var modifierMax = 0.0

        // Trening s otporom
        // Napomena: 2x nedeljno HIT (do otkaza) je jednako efikasan kao 4x nedeljno standardni
        when {
            resistanceTrainingDays >= 4 -> {
                // Visoka frekvencija (4+ dana): +15-25% mišića
                modifierMin += 0.15
                modifierMax += 0.25
            }
            resistanceTrainingDays == 3 -> {
                // Srednja frekvencija (3 dana): +10-20% mišića
                modifierMin += 0.10
                modifierMax += 0.20
            }
            resistanceTrainingDays == 2 -> {
                // HIT / Full body 2x (do otkaza): +15-25% mišića
                // Mentzer/Yates stil - manja frekvencija, maksimalni intenzitet
                modifierMin += 0.15
                modifierMax += 0.25
            }
            resistanceTrainingDays == 1 -> {
                // Jednom nedeljno: +5-10% mišića
                modifierMin += 0.05
                modifierMax += 0.10
            }
        }

        // TRT/AAS: +15-25% mišića (bazirano na istraživanjima)
        // Bhasin et al. pokazao je značajno poboljšanje particije uz testosteron
        if (isOnTrt) {
            modifierMin += 0.15
            modifierMax += 0.25
        }

        // Previsok kalorijski višak: -5-10% mišića (više ide u mast)
        // Višak iznad 500 kcal generalno dovodi do više masti
        when {
            surplusKcalPerDay > 750 -> {
                // Ogroman višak: -10-15% mišića
                modifierMin -= 0.15
                modifierMax -= 0.10
            }
            surplusKcalPerDay > 500 -> {
                // Veliki višak: -5-10% mišića
                modifierMin -= 0.10
                modifierMax -= 0.05
            }
            surplusKcalPerDay in 0..199 -> {
                // Mali višak (lean bulk): +5% mišića (manje ukupno, ali bolji ratio)
                // Napomena: samo za stvarni (nenegativan) višak — kod deficita uz
                // dobitak težine ovaj bonus nema smisla i ranije se pogrešno primenjivao.
                modifierMin += 0.03
                modifierMax += 0.05
            }
        }

        // 3. Izračunaj finalne ratije
        // Ograniči na realne vrednosti (5-85% mišića)
        val muscleRatioMin = (baseMuscleRatio + modifierMin).coerceIn(0.05, 0.85)
        val muscleRatioMax = (baseMuscleRatio + modifierMax).coerceIn(0.05, 0.85)

        return muscleRatioMin to muscleRatioMax
    }

    /**
     * Vraća tekstualni opis nesigurnosti procene.
     */
    fun getUncertaintyNote(): String {
        return "Ove procene imaju veliku nesigurnost (±25-40%). " +
                "Forbes formula nije validirana za trenirane osobe i procenjuje nemasnu " +
                "masu (FFM) — koja uz mišić uključuje glikogen, vodu i vezivno tkivo, pa " +
                "nije 100% čist mišić. " +
                "Stvarni rezultati zavise od genetike, tipa treninga, sna i stresa."
    }

    /**
     * Vraća preporuku za poboljšanje P-ratio.
     */
    fun getImprovementTips(
        resistanceTrainingDays: Int,
        surplusKcalPerDay: Int
    ): List<String> {
        val tips = mutableListOf<String>()

        if (resistanceTrainingDays < 2) {
            tips.add("Treniraj s otporom minimum 2x nedeljno (HIT) ili 3x standardno")
        }

        if (surplusKcalPerDay > 500) {
            tips.add("Smanji kalorijski višak na 300-500 kcal za manje masti")
        }

        if (tips.isEmpty()) {
            tips.add("Odlični parametri! Nastavi ovako za optimalnu particiju.")
        }

        return tips
    }
}
