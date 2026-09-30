package com.kalorije.app.data.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

enum class GoalMode {
    MAINTENANCE,
    BULK,
    CUT
}

data class UserProfile(
    val heightCm: Int = 188,
    val weightKg: Double = 75.0,
    val age: Int = 29,
    val isMale: Boolean = true,
    val activityLevel: ActivityLevel = ActivityLevel.LIGHT,
    val goal: WeightGoal = WeightGoal.GAIN_SLOW,
    // Custom calories - if set, overrides calculated target
    val customCalories: Int? = null,
    // Ručno postavljen TDEE (zaobilazi auto-učenje)
    val manualTdee: Int? = null,
    // Learned TDEE adjustment based on actual weight changes
    // Positive = body burns more than calculated, Negative = burns less
    val tdeeAdjustment: Int = 0,
    // Weight history for learning
    val weightHistory: List<WeightEntry> = emptyList(),
    // Dan prvog merenja — od njega se broje 7-dnevni periodi za nedeljni prosek
    // (dan 1–7 = prva "nedelja", 8–14 druga...). Postavlja se automatski pri prvom
    // unosu i čuva se odvojeno da brisanje prvog merenja ne pomeri sve periode.
    val weightPeriodStartDate: LocalDate? = null,
    // Goal weight for progress tracking
    val goalWeight: Double? = null,
    // Fixed starting weight - used for progress display only, NOT for TDEE calculations
    val fixedStartingWeight: Double? = null,
    // TRT/AAS support
    val useTrt: Boolean = false,
    val trtStartDate: LocalDate? = null,
    val trtCycleEndDate: LocalDate? = null, // Planirani datum završetka ciklusa
    val trtDoseMgWeek: Double = 400.0, // Nedeljina doza u mg (default 400mg)
    val usesAromataseInhibitor: Boolean = false, // Da li koristi AI (smanjuje water retention ~30%)
    // Completed cycle history
    val cycleHistory: List<CycleHistory> = emptyList(),
    // Podsetnik za merenje težine
    val weightReminderEnabled: Boolean = false,
    val weightReminderHour: Int = 5,
    val weightReminderMinute: Int = 0,
    // Broj dana treninga s otporom nedeljno (0-7) - koristi se za P-ratio kalkulaciju
    val resistanceTrainingDays: Int = 0,
    // Dualni TDEE profili — razdvojeno učenje za TRT i Natural
    val naturalTdeeProfile: TdeeProfile = TdeeProfile(),
    val trtTdeeProfile: TdeeProfile = TdeeProfile(),
    // Post-cycle stabilizacija (null = nema aktivne stabilizacije)
    val postCycleState: PostCycleState? = null,
    // Korisnički izbor cilja težine
    val goalMode: GoalMode = GoalMode.MAINTENANCE,
    val goalPaceKgPerWeek: Double = 0.0,
    // Da li je stari WeightGoal enum već prebačen u GoalMode (migracija sme samo jednom)
    val goalMigrated: Boolean = false
) {
    /**
     * BMI izračunat iz težine i visine
     */
    val bmi: Double
        get() = weightKg / ((heightCm / 100.0) * (heightCm / 100.0))

    /**
     * Procenjen procenat telesne masti na osnovu BMI formule.
     *
     * Formula (Deurenberg et al., 1991):
     * - Muškarci: BF% = 1.20 × BMI + 0.23 × Age - 16.2
     * - Žene: BF% = 1.20 × BMI + 0.23 × Age - 5.4
     *
     * Napomena: pošto se bazira na BMI, formula PRECENJUJE BF% kod mišićavih/sportista
     * (visok BMI od mišića se čita kao mast), a POTCENJUJE kod mršavo-debelih sedentarnih.
     * Za preciznije merenje koristi DEXA, potapanje u vodu, ili kaliperometriju.
     */
    val estimatedBodyFatPercent: Double
        get() {
            val baseBf = 1.20 * bmi + 0.23 * age
            return if (isMale) {
                (baseBf - 16.2).coerceIn(5.0, 50.0)
            } else {
                (baseBf - 5.4).coerceIn(10.0, 55.0)
            }
        }

    /**
     * Procenjena masa masti u kilogramima
     */
    val estimatedFatMassKg: Double
        get() = weightKg * (estimatedBodyFatPercent / 100.0)

    /**
     * Weeks on TRT cycle (null if not using)
     * Returns 1-based week number (week 1 = first 7 days, week 2 = days 8-14, etc.)
     */
    val weeksOnTrt: Int?
        get() {
            val startDate = trtStartDate
            return if (useTrt && startDate != null) {
                val days = ChronoUnit.DAYS.between(startDate, LocalDate.now()).toInt().coerceAtLeast(0)
                (days / 7) + 1 // Week 1 starts on day 0
            } else null
        }

    /**
     * Broj dana od početka TRT ciklusa (0-based).
     * Vraća null ako nije na TRT-u.
     */
    val daysOnTrt: Int?
        get() {
            val startDate = trtStartDate
            return if (useTrt && startDate != null) {
                ChronoUnit.DAYS.between(startDate, LocalDate.now()).toInt().coerceAtLeast(0)
            } else null
        }

    /**
     * Frakcione nedelje na TRT ciklusu (dani / 7.0, 0-based).
     * Koristi se za SVE kalkulacije tempa i modela — `weeksOnTrt` je 1-based prikazni
     * broj nedelje i deljenje njime unosi grešku do cele nedelje.
     */
    val weeksOnTrtExact: Double?
        get() = daysOnTrt?.let { it / 7.0 }

    /**
     * TRT TDEE faza:
     * 0 = nije na TRT-u
     * 1 = rana faza (<28 dana) - bez učenja
     * 2 = stabilizacija (28-41 dan) - prikupljanje podataka
     * 3 = aktivno učenje (42+ dana) - stabilan signal
     */
    val trtTdeePhase: Int
        get() {
            val days = daysOnTrt ?: return 0
            return when {
                days < 28 -> 1
                days < 42 -> 2
                else -> 3
            }
        }

    /**
     * Poruka o statusu TDEE učenja za TRT korisnike.
     * Vraća null ako je manualTdee postavljen ili korisnik nije na TRT-u.
     */
    val trtTdeePhaseMessage: String?
        get() {
            if (manualTdee != null || !useTrt) return null
            val days = daysOnTrt ?: return null
            return when (trtTdeePhase) {
                1 -> "TDEE se trenutno ne uči zbog rane faze ciklusa i vodene retencije."
                2 -> {
                    val daysUntilLearning = 42 - days
                    "Podaci se prikupljaju za stabilizaciju signala. Učenje počinje za $daysUntilLearning dana."
                }
                3 -> "TDEE se sada uči na osnovu stabilnih podataka."
                else -> null
            }
        }

    /**
     * Weight at the start of current TRT cycle (closest measurement to start date)
     */
    val weightAtCycleStart: Double?
        get() {
            val startDate = trtStartDate
            return if (useTrt && startDate != null && weightHistory.isNotEmpty()) {
                // Jedan prolaz: simultano traži najbliži entry (u prvih 7 dana) i najstariji entry
                val cutoff = startDate.plusDays(7)
                var closestEntry: WeightEntry? = null
                var closestDays = Long.MAX_VALUE
                var oldestEntry: WeightEntry = weightHistory[0]

                for (entry in weightHistory) {
                    // Najstariji entry (fallback)
                    if (entry.date < oldestEntry.date) {
                        oldestEntry = entry
                    }
                    // Najbliži start date-u, u prvih 7 dana
                    if (!entry.date.isAfter(cutoff)) {
                        val days = abs(ChronoUnit.DAYS.between(entry.date, startDate))
                        if (days < closestDays) {
                            closestDays = days
                            closestEntry = entry
                        }
                    }
                }

                (closestEntry ?: oldestEntry).weightKg
            } else null
        }

    /**
     * Weight gained during current TRT cycle
     */
    val weightGainedOnCycle: Double?
        get() = weightAtCycleStart?.let { startWeight ->
            currentWeight - startWeight
        }

    /**
     * Days remaining until planned cycle end date (null if not set or not on cycle)
     */
    val daysUntilCycleEnd: Int?
        get() {
            val endDate = trtCycleEndDate
            return if (useTrt && endDate != null) {
                ChronoUnit.DAYS.between(LocalDate.now(), endDate).toInt()
            } else null
        }

    /**
     * Whether currently in water retention phase (first ~6 weeks of TRT).
     *
     * Research basis (PubMed):
     * Sa dugim estrima (enantat/cipionat) nivoi hormona rastu do steady-state za ~4-6
     * nedelja, pa i vodena retencija raste do platoa kroz taj period (v. WeightNormalizer
     * sigmoidni model). Tipičan rani dobitak je 1-2+ kg vode; čist dobitak tkiva postaje
     * razdvojiv tek posle ~6 nedelja kada se voda stabilizuje.
     */
    val isInWaterRetentionPhase: Boolean
        get() = weeksOnTrt?.let { it <= 6 } ?: false

    /**
     * TRT metabolic multiplier.
     *
     * Research basis (PubMed):
     * - BMR increase of 7-13% documented (mean 10%) after 3-12 months of TRT
     * - Elevation persists (9% at 12 months in clinical studies)
     * - Effect is primarily mediated through lean body mass gains (r = 0.91)
     * - First 4 weeks: gradual increase as water retention stabilizes
     * - After 4 weeks: full metabolic effect ~1.10x BMR
     *
     * Koristi frakcione nedelje pa je kriva neprekidna (nema skoka 1.075 -> 1.10
     * između nedelje 4 i 5 kao u ranijoj verziji).
     */
    val trtMultiplier: Double
        get() = if (!useTrt) 1.0 else {
            val weeks = weeksOnTrtExact ?: 0.0
            1.0 + 0.10 * (weeks / TRT_METABOLIC_RAMP_WEEKS).coerceIn(0.0, 1.0)
        }

    /**
     * BMR using Mifflin-St Jeor formula, adjusted for TRT if applicable
     */
    val bmr: Double
        get() {
            val baseBmr = if (isMale) {
                10 * weightKg + 6.25 * heightCm - 5 * age + 5
            } else {
                10 * weightKg + 6.25 * heightCm - 5 * age - 161
            }
            return baseBmr * trtMultiplier
        }

    /**
     * Base TDEE = BMR * activity multiplier
     */
    val baseTdee: Double
        get() = bmr * activityLevel.multiplier

    /**
     * Koliko se TDEE menja po kilogramu telesne mase.
     * Mifflin-St Jeor daje 10 kcal/kg na nivou BMR, pa se skalira TRT i aktivnošću.
     * Koristi se da projekcije težine ne drže TDEE fiksnim kroz cele mesece.
     */
    val tdeeKcalPerKg: Double
        get() = 10.0 * trtMultiplier * activityLevel.multiplier

    /**
     * Efektivna TDEE korekcija uzimajući u obzir dualne profile i post-cycle stabilizaciju.
     * - Tokom post-cycle stabilizacije → 0 (zamrznut TDEE)
     * - Na TRT-u faza 1-2 (<42 dana) → 0 (samo formula, vodena retencija maskira signal)
     * - Na TRT-u faza 3 (42+ dana) → trtTdeeProfile.learnedAdjustment
     * - Natural → naturalTdeeProfile.learnedAdjustment
     * Fallback na tdeeAdjustment za backward compat ako profili nisu migrirani.
     */
    val effectiveTdeeAdjustment: Int
        get() = when {
            postCycleState?.isActive == true -> 0
            useTrt && trtTdeePhase < 3 -> 0
            useTrt -> trtTdeeProfile.learnedAdjustment.takeIf { it != 0 } ?: tdeeAdjustment
            else -> naturalTdeeProfile.learnedAdjustment.takeIf { it != 0 } ?: tdeeAdjustment
        }

    /**
     * Adjusted TDEE — ručni TDEE ima prioritet, inače formula + naučena korekcija
     */
    val tdee: Double
        get() = manualTdee?.toDouble() ?: (baseTdee + effectiveTdeeAdjustment)

    /**
     * Izvor TDEE vrednosti za prikaz u UI
     */
    val tdeeSource: String
        get() = when {
            manualTdee != null -> "Ručno postavljen"
            postCycleState?.isActive == true -> "Post-cycle stabilizacija"
            effectiveTdeeAdjustment != 0 -> "Naučen iz podataka"
            else -> "Formula"
        }

    /**
     * Kalorijski offset na osnovu GoalMode i tempa (goalPaceKgPerWeek).
     * BULK → pozitivan offset, CUT → negativan offset, MAINTENANCE → 0.
     * Formula: pace_kg * KCAL_PER_KG / 7_dana
     *
     * `goalPaceKgPerWeek` se uvek čuva kao POZITIVNA vrednost — smer određuje goalMode.
     */
    val goalCalorieOffset: Int
        get() {
            val pace = abs(goalPaceKgPerWeek)
            return when (goalMode) {
                GoalMode.MAINTENANCE -> 0
                GoalMode.BULK -> (pace * KCAL_PER_KG / 7).roundToInt()
                GoalMode.CUT -> -(pace * KCAL_PER_KG / 7).roundToInt()
            }
        }

    /**
     * Najniže kalorije koje app sme da preporuči.
     * Nikad ispod BMR-a i nikad ispod apsolutnog minimuma (1500 M / 1200 Ž),
     * jer agresivan tempo na maloj telesnoj masi inače daje cilj ispod bazalnog
     * metabolizma (npr. 60 kg, -1 kg/ned → 715 kcal).
     */
    val minimumCalories: Int
        get() = maxOf(bmr.roundToInt(), if (isMale) ABSOLUTE_MIN_CALORIES_MALE else ABSOLUTE_MIN_CALORIES_FEMALE)

    /**
     * Cilj pre primene donje granice (interno, za detekciju clamp-a)
     */
    private val rawTargetCalories: Int
        get() = if (useTrt && trtTdeePhase < 3) {
            // Faza 1-2: samo formula + cilj, customCalories se ignoriše jer TDEE nije ustanovljen
            (baseTdee + goalCalorieOffset).roundToInt()
        } else {
            customCalories ?: (tdee + goalCalorieOffset).roundToInt()
        }

    /**
     * Target calories - either custom or calculated, ograničeno na minimumCalories
     */
    val targetCalories: Int
        get() = rawTargetCalories.coerceAtLeast(minimumCalories)

    /**
     * Da li je cilj podignut na donju granicu (za upozorenje u UI-u)
     */
    val isCalorieFloorApplied: Boolean
        get() = rawTargetCalories < minimumCalories

    /**
     * Upozorenje za agresivan tempo promene težine
     */
    val paceWarning: String?
        get() = when (goalMode) {
            GoalMode.BULK -> when {
                goalPaceKgPerWeek > 0.75 -> "Tempo >0.75 kg/ned dovodi do značajnog nakupljanja masti."
                goalPaceKgPerWeek > 0.5 -> "Tempo >0.5 kg/ned — veći deo dobitka može biti mast."
                else -> null
            }
            GoalMode.CUT -> when {
                goalPaceKgPerWeek > 1.0 -> "Tempo >1.0 kg/ned — visok rizik od gubitka mišića!"
                goalPaceKgPerWeek > 0.75 -> "Tempo >0.75 kg/ned — umeren rizik od gubitka mišića."
                else -> null
            }
            GoalMode.MAINTENANCE -> null
        }

    /**
     * Daily calorie surplus/deficit based on target vs TDEE
     */
    val dailySurplus: Int
        get() = targetCalories - tdee.roundToInt()

    /**
     * Predicted weekly weight change in kg.
     *
     * Koristi 7700 kcal/kg — energetska gustina MASNOG tkiva (~7000-8000 kcal/kg).
     * To je standardna konvencija za planiranje tempa i konzistentna je sa
     * goalCalorieOffset, ali NIJE energetska cena čistog mišića: nemasno tkivo je
     * ~1800-2500 kcal/kg (velikim delom voda), pa je pri dobijanju stvarni prirast
     * težine obično nešto brži od predviđenog, a pri gubitku nešto sporiji.
     */
    val predictedWeeklyChange: Double
        get() = (dailySurplus * 7) / KCAL_PER_KG

    /**
     * Formatted prediction string
     */
    val predictedChangeText: String
        get() {
            val change = predictedWeeklyChange
            return when {
                abs(change) < 0.05 -> "Održavanje težine"
                change > 0 -> "+${String.format("%.2f", change)} kg/ned"
                else -> "${String.format("%.2f", change)} kg/ned"
            }
        }

    /**
     * Current weight from latest entry or default.
     * Falls back to fixedStartingWeight if no entries exist.
     */
    val currentWeight: Double
        get() = weightHistory.maxByOrNull { it.date }?.weightKg
            ?: fixedStartingWeight
            ?: weightKg

    /**
     * Starting weight for progress display
     * Uses fixedStartingWeight if set, otherwise falls back to first measurement
     */
    val startingWeight: Double?
        get() = fixedStartingWeight ?: weightHistory.minByOrNull { it.date }?.weightKg

    /**
     * Početak 7-dnevnih perioda za nedeljni prosek. Sačuvani datum ima prednost;
     * za stare profile (pre uvođenja polja) pada na datum najstarijeg merenja.
     */
    val weeklyPeriodAnchor: LocalDate?
        get() = weightPeriodStartDate ?: weightHistory.minByOrNull { it.date }?.date

    /**
     * First measured weight from history (used for TDEE calculations, ignores fixedStartingWeight)
     */
    val firstMeasuredWeight: Double?
        get() = weightHistory.minByOrNull { it.date }?.weightKg

    /**
     * Total weight change from start to now
     */
    val totalWeightChange: Double?
        get() = startingWeight?.let { currentWeight - it }

    /**
     * Progress towards goal weight (0.0 to 1.0)
     */
    val progressToGoal: Double?
        get() = goalWeight?.let { goal ->
            startingWeight?.let { start ->
                val totalNeeded = goal - start
                if (totalNeeded == 0.0) 1.0
                else ((currentWeight - start) / totalNeeded).coerceIn(0.0, 1.0)
            }
        }

    /**
     * Number of days we've been tracking weight
     */
    val trackingDays: Int
        get() {
            if (weightHistory.size < 2) return 0
            val sorted = weightHistory.sortedBy { it.date }
            return java.time.temporal.ChronoUnit.DAYS.between(sorted.first().date, sorted.last().date).toInt()
        }

    /**
     * Whether we have enough data for confident TDEE (14+ days and adjustment applied)
     */
    val hasConfidentTdee: Boolean
        get() = trackingDays >= MIN_TDEE_LEARNING_DAYS && effectiveTdeeAdjustment != 0

    /**
     * TDEE confidence level description
     */
    val tdeeConfidenceText: String
        get() = when {
            manualTdee != null -> "Zaključan"
            postCycleState?.isActive == true -> "Post-cycle pauza (${postCycleState!!.daysRemaining} dana)"
            useTrt && trtStartDate != null -> {
                val days = daysOnTrt ?: 0
                when (trtTdeePhase) {
                    1 -> "Rana faza ciklusa (dan ${days}/28)"
                    2 -> "Stabilizacija (dan ${days}/42)"
                    else -> {
                        // Faza 3: pouzdanost bazirana na danima stabilnih podataka (od dana 28+)
                        val stableDate = trtStartDate!!.plusDays(28)
                        val stableDays = weightHistory.sortedBy { it.date }
                            .filter { !it.date.isBefore(stableDate) }
                            .let { entries ->
                                if (entries.size < 2) 0
                                else ChronoUnit.DAYS.between(entries.first().date, entries.last().date).toInt()
                            }
                        confidenceTextForDays(stableDays)
                    }
                }
            }
            else -> confidenceTextForDays(trackingDays)
        }

    /**
     * Tekst pouzdanosti — pragovi prate MIN_TDEE_LEARNING_DAYS (21 dan),
     * jer pre toga učenje uopšte ne startuje.
     */
    private fun confidenceTextForDays(days: Int): String = when {
        days < MIN_TDEE_LEARNING_DAYS -> "Prikupljanje podataka ($days/$MIN_TDEE_LEARNING_DAYS dana)"
        days < 28 -> "Rano učenje ($days dana)"
        days < 42 -> "Pouzdano ($days dana)"
        else -> "Veoma pouzdano ($days dana)"
    }

    /**
     * Add a weight entry and optionally recalculate TDEE adjustment
     */
    fun addWeightEntry(entry: WeightEntry): UserProfile {
        // Jedno merenje po danu — novo merenje zamenjuje staro za isti datum.
        // Inače bi rolling proseci davali veću težinu danima sa više merenja,
        // a `currentWeight` (maxByOrNull { date }) bio nedeterministički.
        val withoutSameDay = weightHistory.filterNot { it.date == entry.date }
        // Limit 730 unosa (~2 godine dnevnog merenja). Ranije je bio 90, ali sa svakodnevnim
        // merenjem to je samo ~3 meseca — nedeljni proseci bi počeli da nestaju već posle jedne sezone.
        val newHistory = (withoutSameDay + entry).sortedByDescending { it.date }.take(MAX_WEIGHT_HISTORY_ENTRIES)
        return copy(
            weightKg = entry.weightKg,
            weightHistory = newHistory
        )
    }

    /**
     * Calculate learned TDEE adjustment based on actual vs predicted weight change.
     *
     * Za TRT korisnike: preskače prvih 28 dana ciklusa (period stabilizacije vode)
     * umesto pokušaja korekcije za water retention. Koristi 7-dnevni rolling average
     * umesto pojedinačnih merenja za početak/kraj perioda.
     */
    fun calculateLearnedTdeeAdjustment(averageCaloriesConsumed: Double): UserProfile {
        if (weightHistory.size < 2) return this

        // Post-cycle stabilizacija: ne uči TDEE
        if (postCycleState?.isActive == true) return this

        // TRT trofazna logika — NE resetovati na 0, samo preskočiti učenje
        if (useTrt && trtStartDate != null) {
            when (trtTdeePhase) {
                1 -> return this // Rana faza: bez učenja
                2 -> return this // Stabilizacija: prikupljanje, ali bez učenja
                // Faza 3: nastavlja sa učenjem koristeći podatke od dana 28+
            }
        }

        val sortedHistory = weightHistory.sortedBy { it.date }
        val newestEntry = sortedHistory.last()

        // Za TRT fazu 3: koristi samo podatke posle stabilizacije vode (28 dana)
        val effectiveOldest = if (useTrt && trtStartDate != null) {
            val stableDate = trtStartDate.plusDays(28)
            sortedHistory.firstOrNull { !it.date.isBefore(stableDate) }
                ?: return this // Nema podataka posle stabilizacije
        } else {
            sortedHistory.first()
        }

        val daysBetween = ChronoUnit.DAYS.between(effectiveOldest.date, newestEntry.date)
        // Minimum 21 dan: sa 7-dnevnim prozorima na oba kraja, kraći period znači
        // da se prozori preklapaju i promena težine ispada lažno ~0.
        if (daysBetween < MIN_TDEE_LEARNING_DAYS) {
            return this // Nema dovoljno podataka
        }

        // 7-dnevni rolling average za početak perioda
        val startEntries = sortedHistory.filter {
            !it.date.isBefore(effectiveOldest.date) &&
            ChronoUnit.DAYS.between(effectiveOldest.date, it.date) < TDEE_WINDOW_DAYS
        }
        val startWeight = startEntries.map { it.weightKg }.average()

        // 7-dnevni rolling average za kraj perioda
        val endEntries = sortedHistory.filter {
            ChronoUnit.DAYS.between(it.date, newestEntry.date) < TDEE_WINDOW_DAYS
        }
        val endWeight = endEntries.map { it.weightKg }.average()

        // Efektivni razmak = razmak između TEŽIŠTA (srednjih datuma) dva prozora,
        // a ne između krajnjih merenja. Deljenje sa daysBetween je ranije
        // sistematski potcenjivalo stvarni tempo promene težine.
        val startMidDay = startEntries
            .map { ChronoUnit.DAYS.between(effectiveOldest.date, it.date).toDouble() }
            .average()
        val endMidDay = endEntries
            .map { ChronoUnit.DAYS.between(effectiveOldest.date, it.date).toDouble() }
            .average()
        val effectiveDays = endMidDay - startMidDay
        if (effectiveDays < MIN_EFFECTIVE_TDEE_DAYS) {
            return this // Prozori se previše preklapaju da bi signal bio upotrebljiv
        }

        // Nema water correction — za TRT, prvih 28 dana je preskočeno
        val totalWeightChange = endWeight - startWeight
        val actualCaloriesForChange = totalWeightChange * KCAL_PER_KG // kcal equivalent

        // How many calories per day did the weight change represent?
        val actualDailySurplus = actualCaloriesForChange / effectiveDays

        // What was our predicted daily surplus based on average intake?
        val predictedDailySurplus = averageCaloriesConsumed - baseTdee

        // The difference tells us how our TDEE is off
        // If we gained less than predicted, our TDEE is higher than calculated
        val adjustment = (predictedDailySurplus - actualDailySurplus).toInt()

        // Smooth the adjustment (don't change too drastically)
        val currentAdj = effectiveTdeeAdjustment
        val smoothedAdjustment = (currentAdj * 0.7 + adjustment * 0.3).toInt()

        // Clamp to reasonable range (-750 to +750) - allows for extreme metabolisms
        val clampedAdjustment = smoothedAdjustment.coerceIn(-750, 750)

        // Upiši u odgovarajući profil i u tdeeAdjustment za backward compat
        val today = LocalDate.now()
        return if (useTrt) {
            copy(
                tdeeAdjustment = clampedAdjustment,
                trtTdeeProfile = trtTdeeProfile.copy(
                    learnedAdjustment = clampedAdjustment,
                    lastUpdated = today,
                    trackingDays = daysBetween.toInt(),
                    isConfident = effectiveDays >= CONFIDENT_TDEE_DAYS
                )
            )
        } else {
            copy(
                tdeeAdjustment = clampedAdjustment,
                naturalTdeeProfile = naturalTdeeProfile.copy(
                    learnedAdjustment = clampedAdjustment,
                    lastUpdated = today,
                    trackingDays = daysBetween.toInt(),
                    isConfident = effectiveDays >= CONFIDENT_TDEE_DAYS
                )
            )
        }
    }

    /**
     * Migracija starih podataka: ako su oba TDEE profila prazna ali tdeeAdjustment postoji,
     * prenesi ga u odgovarajući profil na osnovu useTrt flaga.
     */
    fun migrateIfNeeded(): UserProfile {
        if (naturalTdeeProfile.learnedAdjustment == 0 &&
            trtTdeeProfile.learnedAdjustment == 0 &&
            tdeeAdjustment != 0
        ) {
            return if (useTrt) {
                copy(trtTdeeProfile = trtTdeeProfile.copy(learnedAdjustment = tdeeAdjustment))
            } else {
                copy(naturalTdeeProfile = naturalTdeeProfile.copy(learnedAdjustment = tdeeAdjustment))
            }
        }
        return this
    }

    fun toDailyGoals(): DailyGoals = DailyGoals(calories = targetCalories)

    companion object {
        /** Energetska gustina masnog tkiva — konvencija za planiranje tempa (kcal/kg). */
        const val KCAL_PER_KG = 7700.0

        /** Maksimalan broj sačuvanih merenja težine (jedno po danu → ~2 godine). */
        const val MAX_WEIGHT_HISTORY_ENTRIES = 730

        /** Nedelje za pun TRT metabolički efekat (+10% BMR), linearni ramp. */
        const val TRT_METABOLIC_RAMP_WEEKS = 4.0

        /** Apsolutni kalorijski minimumi. */
        const val ABSOLUTE_MIN_CALORIES_MALE = 1500
        const val ABSOLUTE_MIN_CALORIES_FEMALE = 1200

        /** Dužina rolling prozora za usrednjavanje težine (dani). */
        const val TDEE_WINDOW_DAYS = 7

        /** Minimalni raspon podataka za učenje TDEE (dani). */
        const val MIN_TDEE_LEARNING_DAYS = 21

        /** Minimalni razmak između težišta prozora da signal bude upotrebljiv (dani). */
        const val MIN_EFFECTIVE_TDEE_DAYS = 10.0

        /** Efektivni razmak od kog se naučeni TDEE smatra pouzdanim (dani). */
        const val CONFIDENT_TDEE_DAYS = 14.0
    }
}

enum class ActivityLevel(
    val displayName: String,
    val description: String,
    val multiplier: Double
) {
    SEDENTARY("Sedentaran", "Bez treninga", 1.2),
    LIGHT("Lako aktivan", "1-2 treninga nedeljno", 1.375),
    MODERATE("Umereno aktivan", "3-5 treninga nedeljno", 1.55),
    ACTIVE("Aktivan", "6-7 treninga nedeljno", 1.725),
    VERY_ACTIVE("Veoma aktivan", "2x dnevno trening", 1.9);

    companion object {
        fun fromString(value: String): ActivityLevel {
            return entries.firstOrNull { it.name == value } ?: LIGHT
        }
    }
}

enum class WeightGoal(
    val displayName: String,
    val description: String,
    val calorieAdjustment: Int
) {
    LOSE_FAST("Brzo mršavljenje", "~-0.9kg nedeljno", -1000),
    LOSE_SLOW("Sporo mršavljenje", "~-0.45kg nedeljno", -500),
    MAINTAIN("Održavanje", "Ista težina", 0),
    GAIN_SLOW("Sporo dobijanje", "~+0.45kg nedeljno", 500),
    GAIN_FAST("Brzo dobijanje", "~+0.9kg nedeljno", 1000),
    CUSTOM("Ručno podešeno", "Unesi kalorije", 0);

    companion object {
        fun fromString(value: String): WeightGoal {
            return entries.firstOrNull { it.name == value } ?: MAINTAIN
        }
    }
}
