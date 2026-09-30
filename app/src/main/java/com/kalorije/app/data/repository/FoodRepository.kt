package com.kalorije.app.data.repository

import android.app.Application
import android.util.Log
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import com.kalorije.app.data.model.DailyGoals
import com.kalorije.app.data.model.FoodEntry
import com.kalorije.app.data.model.FoodProduct
import com.kalorije.app.data.model.DailySummary
import com.kalorije.app.data.model.MealCartItem
import com.kalorije.app.data.model.SavedFood
import com.kalorije.app.data.model.UserProfile
import com.kalorije.app.data.db.KalorijeDatabase
import com.kalorije.app.data.db.JsonToRoomMigration
import com.kalorije.app.data.db.dao.DateSummary
import com.kalorije.app.data.db.dao.FoodEntryDao
import com.kalorije.app.data.db.dao.FoodProductDao
import com.kalorije.app.data.db.dao.SavedFoodDao
import com.kalorije.app.data.db.mapper.toEntity
import com.kalorije.app.data.db.mapper.toDomain
import com.kalorije.app.ui.components.SelectedFoodItem
import com.kalorije.app.util.GsonProvider
import com.kalorije.app.util.countMealGroups
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.room.InvalidationTracker
import androidx.room.withTransaction
import com.kalorije.app.data.backup.BackupScheduler
import com.kalorije.app.data.backup.BackupSnapshot
import com.kalorije.app.data.backup.KalorijeBackupCodec
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import com.kalorije.app.data.model.WeightEntry
import com.kalorije.app.data.model.WeightInsight
import com.kalorije.app.data.model.InsightType
import com.kalorije.app.data.model.CycleHistory
import com.kalorije.app.data.model.GoalMode
import com.kalorije.app.data.model.LiveCycleStats
import com.kalorije.app.data.model.PostCycleState
import com.kalorije.app.data.model.TdeeProfile
import com.kalorije.app.data.model.WeightGoal
import com.kalorije.app.util.WeightNormalizer
import com.kalorije.app.util.getLogicalDate
import java.time.LocalTime
import com.kalorije.app.BuildConfig

class FoodRepository(private val app: Application) {
    // Room baza i DAO-vi
    private val db = KalorijeDatabase.getInstance(app)
    private val productDao: FoodProductDao = db.foodProductDao()
    private val entryDao: FoodEntryDao = db.foodEntryDao()
    private val savedFoodDao: SavedFoodDao = db.savedFoodDao()

    // JSON fajlovi — profile/goals + eksplicitan izbor i redosled za šejk listu
    private val entriesFile = File(app.filesDir, "food_entries.json")
    private val goalsFile = File(app.filesDir, "goals.json")
    private val profileFile = File(app.filesDir, "profile.json")
    private val shakeOrderFile = File(app.filesDir, "shake_order.json")
    private val gson = GsonProvider.instance
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Mutex samo za profile/goals JSON operacije
    private val fileMutex = Mutex()
    private val shakeOrderMutex = Mutex()

    private val _entries = MutableStateFlow<List<FoodEntry>>(emptyList())
    val entries: StateFlow<List<FoodEntry>> = _entries

    private val _goals = MutableStateFlow(DailyGoals())
    val goals: StateFlow<DailyGoals> = _goals

    private val _profile = MutableStateFlow(UserProfile())
    val profile: StateFlow<UserProfile> = _profile

    private val _savedFoods = MutableStateFlow<List<SavedFood>>(emptyList())
    val savedFoods: StateFlow<List<SavedFood>> = _savedFoods

    private val _shakeOrder = MutableStateFlow<List<String>>(emptyList())
    val shakeOrder: StateFlow<List<String>> = _shakeOrder

    // Pending selections from SavedFoodsScreen to be consumed by ManualEntryScreen
    private val _pendingFoodSelections = MutableStateFlow<List<SelectedFoodItem>>(emptyList())
    val pendingFoodSelections: StateFlow<List<SelectedFoodItem>> = _pendingFoodSelections

    fun setPendingFoodSelections(items: List<SelectedFoodItem>) {
        _pendingFoodSelections.value = items
    }

    fun consumePendingFoodSelections(): List<SelectedFoodItem> {
        val items = _pendingFoodSelections.value
        _pendingFoodSelections.value = emptyList()
        return items
    }

    // Meal cart state - persists across navigation
    private val _mealCart = MutableStateFlow<List<MealCartItem>>(emptyList())
    val mealCart: StateFlow<List<MealCartItem>> = _mealCart

    fun addToMealCart(item: MealCartItem) {
        _mealCart.value = _mealCart.value + item
    }

    fun removeFromMealCart(itemId: String) {
        _mealCart.value = _mealCart.value.filter { it.id != itemId }
    }

    fun clearMealCart() {
        _mealCart.value = emptyList()
    }

    // Keš kalorija po datumu za brži TDEE proračun
    @Volatile
    private var caloriesByDateCache: Map<LocalDate, Double>? = null

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded

    // Sekcija koja pri startu nije mogla da se pročita. Dok postoji, backup se
    // odbija — inače bi tiho prazna sekcija pregazila dobar backup u oblaku.
    @Volatile
    private var loadProblem: String? = null

    // Uvoz iz oblaka upisuje i u Room i u JSON fajlove; bez ovoga bi svaki restore
    // odmah okinuo backup istog tog stanja nazad u oblak.
    @Volatile
    private var suppressAutoBackup = false

    /** Čeka da se podaci učitaju sa diska (profil, unosi, sačuvane namirnice). */
    suspend fun awaitLoaded() {
        _isLoaded.first { it }
    }

    init {
        ioScope.launch {
            try {
                // Migracija JSON -> Room (idempotentna, preskače ako je već završena)
                JsonToRoomMigration.migrateIfNeeded(app, db)

                listOf(
                    async { loadEntries() },
                    async { loadProfile() },
                    async { loadGoals() },
                    async {
                        loadSavedFoods()
                        loadShakeOrder()
                    }
                ).awaitAll()
                // Uvek resinhronizuj goals iz profila jer computed property-ji
                // (tdee, targetCalories) mogu dati drugačije vrednosti od sačuvanih
                _goals.value = _profile.value.toDailyGoals()
            } catch (e: Exception) {
                loadProblem = "Podaci nisu učitani"
                if (BuildConfig.DEBUG) Log.e(TAG, "Greška pri učitavanju podataka", e)
            } finally {
                // Uvek se postavlja — oni koji čekaju učitavanje ne smeju da vise zauvek
                _isLoaded.value = true
            }

            // Tek posle učitavanja: InvalidationTracker bi inače okinuo backup i na
            // migraciju/učitavanje. Jedan observer hvata SVE izmene u bazi, pa nijedan
            // novi DAO poziv ne može da se "zaboravi" da zakaže backup.
            db.invalidationTracker.addObserver(
                object : InvalidationTracker.Observer(
                    "food_products",
                    "food_entries",
                    "saved_foods"
                ) {
                    override fun onInvalidated(tables: Set<String>) = scheduleCloudBackup()
                }
            )
        }
    }

    /**
     * Zakazuje backup u oblak posle izmene. Zove se sa svake tačke upisa (Room
     * preko InvalidationTracker-a, JSON preko [saveToFile]); WorkManager spoji niz
     * brzih izmena u jedan posao.
     */
    private fun scheduleCloudBackup() {
        // Za vreme uvoza se ne zakazuje: upravo vraćeno stanje ne treba odmah nazad u oblak.
        if (suppressAutoBackup) return
        runCatching { BackupScheduler.scheduleAfterChange(app) }
    }

    private suspend fun loadEntries() {
        try {
            reloadEntriesFromDb()
        } catch (e: Exception) {
            loadProblem = "Unosi hrane nisu učitani"
            if (BuildConfig.DEBUG) Log.e(TAG, "Greška pri učitavanju entries iz Room", e)
        }
    }

    private suspend fun loadGoals() {
        try {
            if (!goalsFile.exists()) {
                // If no goals file but profile exists, generate from profile
                _goals.value = _profile.value.toDailyGoals()
                return
            }
            val raw = withContext(Dispatchers.IO) { goalsFile.readText() }
            val goals: DailyGoals = gson.fromJson(raw, DailyGoals::class.java) ?: return
            _goals.value = goals
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to load goals", e)
        }
    }

    private suspend fun loadProfile() {
        try {
            if (!profileFile.exists()) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Profile file does not exist")
                return
            }
            val raw = withContext(Dispatchers.IO) { profileFile.readText() }
            var profile: UserProfile = gson.fromJson(raw, UserProfile::class.java) ?: return
            if (BuildConfig.DEBUG) Log.d(TAG, "Parsed profile, weightHistory size: ${profile.weightHistory.size}")

            // Migracija: TDEE profili i GoalMode
            val beforeMigration = profile
            profile = profile.migrateIfNeeded()
            profile = migrateGoalIfNeeded(profile)

            _profile.value = profile

            // Odmah upiši rezultat migracije da se ne bi ponavljala pri svakom startu
            if (profile != beforeMigration) {
                saveProfileInternal()
            }
        } catch (e: Exception) {
            loadProblem = "Profil nije učitan"
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to load profile", e)
        }
    }

    /**
     * Migrira stari WeightGoal enum u GoalMode + goalPaceKgPerWeek.
     *
     * VAŽNO: migracija sme da se izvrši SAMO JEDNOM. Stari `goal` selektor više ne
     * postoji u UI-u, pa je bez `goalMigrated` flaga svaki restart app-a vraćao
     * korisnika koji je izabrao "Održavanje" nazad na BULK (default goal je GAIN_SLOW),
     * tiho dodajući ~+495 kcal/dan. Posle migracije `goal` se neutrališe na MAINTAIN.
     */
    private fun migrateGoalIfNeeded(profile: UserProfile): UserProfile {
        // Već migrirano — ne diraj korisnikov izbor
        if (profile.goalMigrated) return profile

        val migrated = when (profile.goal) {
            WeightGoal.LOSE_FAST -> profile.copy(goalMode = GoalMode.CUT, goalPaceKgPerWeek = 0.9)
            WeightGoal.LOSE_SLOW -> profile.copy(goalMode = GoalMode.CUT, goalPaceKgPerWeek = 0.45)
            WeightGoal.GAIN_SLOW -> profile.copy(goalMode = GoalMode.BULK, goalPaceKgPerWeek = 0.45)
            WeightGoal.GAIN_FAST -> profile.copy(goalMode = GoalMode.BULK, goalPaceKgPerWeek = 0.9)
            // MAINTAIN i CUSTOM: nema šta da se prenese, samo obeleži kao migrirano
            else -> profile
        }

        // Ako je korisnik već ručno postavio GoalMode (nije default MAINTENANCE+0),
        // njegov izbor ima prioritet nad starim enumom.
        val result = if (profile.goalMode != GoalMode.MAINTENANCE || profile.goalPaceKgPerWeek != 0.0) {
            profile
        } else {
            migrated
        }

        // CUSTOM se zadržava jer i dalje drži ručno unete kalorije; ostalo se neutrališe
        val neutralizedGoal = if (profile.goal == WeightGoal.CUSTOM) WeightGoal.CUSTOM else WeightGoal.MAINTAIN
        return result.copy(goal = neutralizedGoal, goalMigrated = true)
    }

    private suspend fun loadSavedFoods() {
        try {
            reloadSavedFoodsFromDb(syncShakeOrder = false)
        } catch (e: Exception) {
            loadProblem = "Sačuvane namirnice nisu učitane"
            if (BuildConfig.DEBUG) Log.e(TAG, "Greška pri učitavanju saved foods iz Room", e)
        }
    }

    private suspend fun loadShakeOrder() {
        try {
            shakeOrderMutex.withLock {
                var loadedOrder: List<String> = emptyList()
                var shouldPersist = false

                if (shakeOrderFile.exists()) {
                    try {
                        val raw = withContext(Dispatchers.IO) { shakeOrderFile.readText() }
                        loadedOrder = gson.fromJson(raw, STRING_LIST_TYPE) ?: emptyList()
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.e(TAG, "Greška pri učitavanju shake redosleda", e)
                        loadedOrder = emptyList()
                        shouldPersist = true
                    }
                }

                val distinctLoadedOrder = loadedOrder.distinct()
                if (distinctLoadedOrder.size != loadedOrder.size) {
                    shouldPersist = true
                }

                val savedFoodIdSet = _savedFoods.value.map { it.id }.toSet()
                val existingOrder = distinctLoadedOrder.filter { it in savedFoodIdSet }
                if (existingOrder.size != distinctLoadedOrder.size) {
                    shouldPersist = true
                }

                _shakeOrder.value = existingOrder

                if (shouldPersist || (shakeOrderFile.exists() && existingOrder != loadedOrder)) {
                    saveShakeOrderInternal()
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Greška pri inicijalizaciji shake redosleda", e)
            shakeOrderMutex.withLock {
                _shakeOrder.value = emptyList()
                if (shakeOrderFile.exists()) {
                    saveShakeOrderInternal()
                }
            }
        }
    }

    /**
     * Generic save helper - reduces duplication across save methods.
     * Must be called within fileMutex or from a context that already holds it.
     */
    private suspend fun <T> saveToFile(file: File, data: T, type: java.lang.reflect.Type) {
        repeat(MAX_RETRIES) { attempt ->
            try {
                val json = gson.toJson(data, type)
                withContext(Dispatchers.IO) { file.writeText(json) }
                scheduleCloudBackup()
                return
            } catch (e: Exception) {
                if (attempt < MAX_RETRIES - 1) delay(RETRY_DELAY_MS * (attempt + 1))
                else {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Failed to save ${file.name}", e)
                }
            }
        }
    }

    /**
     * Strogo snimanje za restore: temp fajl + rename, a greška se baca
     * (saveToFile je samo loguje, pa restore ne bi znao da stanje nije na disku).
     */
    private suspend fun <T> writeFileStrict(file: File, data: T, type: java.lang.reflect.Type) {
        withContext(Dispatchers.IO) {
            val json = gson.toJson(data, type)
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(json)
            if (!tmp.renameTo(file)) {
                file.writeText(json)
                tmp.delete()
            }
        }
    }

    // Internal save variants za JSON — samo profile/goals
    private suspend fun saveGoalsInternal() {
        saveToFile(goalsFile, _goals.value, DailyGoals::class.java)
    }

    private suspend fun saveProfileInternal() {
        if (BuildConfig.DEBUG) Log.d(TAG, "Saving profile, weightHistory size: ${_profile.value.weightHistory.size}")
        saveToFile(profileFile, _profile.value, UserProfile::class.java)
        if (BuildConfig.DEBUG) Log.d(TAG, "Profile saved successfully")
    }

    private suspend fun saveShakeOrderInternal() {
        saveToFile(shakeOrderFile, _shakeOrder.value, STRING_LIST_TYPE)
    }

    // Room pomoćne metode — pronađi ili umetni product
    private suspend fun findOrInsertProduct(product: FoodProduct): Long {
        val existingId = productDao.findExistingId(product.name, product.brand, product.caloriesPer100g)
        if (existingId != null) return existingId
        return productDao.insert(product.toEntity())
    }

    // Ponovo učitaj entries iz Room baze u StateFlow keš
    private suspend fun reloadEntriesFromDb() {
        val all = entryDao.getAllWithProducts().map { it.toDomain() }
        _entries.value = all.sortedByDescending { it.createdAt }
        caloriesByDateCache = null
    }

    // Ponovo učitaj saved foods iz Room baze u StateFlow keš
    private suspend fun reloadSavedFoodsFromDb(syncShakeOrder: Boolean = true) {
        val foods = savedFoodDao.getAll().map { it.toDomain() }.sortedByDescending { it.createdAt }
        _savedFoods.value = foods
        if (syncShakeOrder) {
            syncShakeOrderWithSavedFoods(foods)
        }
    }

    private suspend fun syncShakeOrderWithSavedFoods(savedFoods: List<SavedFood>) {
        shakeOrderMutex.withLock {
            val savedFoodIds = savedFoods.map { it.id }.toSet()
            val currentOrder = _shakeOrder.value
            val syncedOrder = currentOrder.filter { it in savedFoodIds }
            if (syncedOrder != currentOrder) {
                _shakeOrder.value = syncedOrder
                saveShakeOrderInternal()
            }
        }
    }

    fun add(entry: FoodEntry) {
        ioScope.launch {
            val productId = findOrInsertProduct(entry.product)
            entryDao.insert(entry.toEntity(productId))
            reloadEntriesFromDb()
        }
    }

    fun addAll(newEntries: List<FoodEntry>) {
        ioScope.launch {
            val entryEntities = newEntries.map { entry ->
                val productId = findOrInsertProduct(entry.product)
                entry.toEntity(productId)
            }
            entryDao.insertAll(entryEntities)
            reloadEntriesFromDb()
        }
    }

    fun updateFull(id: String, updatedEntry: FoodEntry) {
        ioScope.launch {
            val productId = findOrInsertProduct(updatedEntry.product)
            entryDao.update(updatedEntry.copy(id = id).toEntity(productId))
            reloadEntriesFromDb()
        }
    }

    fun getEntryById(id: String): FoodEntry? {
        return _entries.value.find { it.id == id }
    }

    fun delete(id: String) {
        ioScope.launch {
            entryDao.deleteById(id)
            reloadEntriesFromDb()
        }
    }

    /**
     * Briše sve unose za dati datum (ceo dan).
     */
    suspend fun deleteEntriesForDate(date: LocalDate) {
        entryDao.deleteByDate(date)
        reloadEntriesFromDb()
    }

    fun addSavedFood(food: SavedFood) {
        ioScope.launch {
            savedFoodDao.insert(food.toEntity())
            reloadSavedFoodsFromDb()
        }
    }

    fun updateSavedFood(updatedFood: SavedFood) {
        ioScope.launch {
            savedFoodDao.update(updatedFood.toEntity())
            reloadSavedFoodsFromDb()
        }
    }

    fun deleteSavedFood(id: String) {
        ioScope.launch {
            savedFoodDao.deleteById(id)
            reloadSavedFoodsFromDb()
        }
    }

    fun setShakeFoodIds(foodIds: List<String>) {
        ioScope.launch {
            shakeOrderMutex.withLock {
                val savedFoodIds = _savedFoods.value.map { it.id }.toSet()
                val sanitizedOrder = foodIds.distinct().filter { it in savedFoodIds }
                if (sanitizedOrder == _shakeOrder.value) return@withLock

                _shakeOrder.value = sanitizedOrder
                saveShakeOrderInternal()
            }
        }
    }

    fun moveShakeFoodUp(foodId: String) {
        ioScope.launch {
            shakeOrderMutex.withLock {
                val currentOrder = _shakeOrder.value
                val index = currentOrder.indexOf(foodId)
                if (index <= 0) return@withLock

                val reordered = currentOrder.toMutableList()
                val previousFoodId = reordered[index - 1]
                reordered[index - 1] = foodId
                reordered[index] = previousFoodId

                _shakeOrder.value = reordered
                saveShakeOrderInternal()
            }
        }
    }

    fun moveShakeFoodDown(foodId: String) {
        ioScope.launch {
            shakeOrderMutex.withLock {
                val currentOrder = _shakeOrder.value
                val index = currentOrder.indexOf(foodId)
                if (index == -1 || index >= currentOrder.lastIndex) return@withLock

                val reordered = currentOrder.toMutableList()
                val nextFoodId = reordered[index + 1]
                reordered[index + 1] = foodId
                reordered[index] = nextFoodId

                _shakeOrder.value = reordered
                saveShakeOrderInternal()
            }
        }
    }

    fun getShakeOrderedFoods(savedFoods: List<SavedFood>): List<SavedFood> {
        if (savedFoods.isEmpty()) return emptyList()

        val foodById = savedFoods.associateBy { it.id }
        return _shakeOrder.value.mapNotNull { foodId -> foodById[foodId] }
    }

    fun getEntriesForDate(date: LocalDate): List<FoodEntry> {
        return _entries.value.filter { it.date == date }
    }

    fun getSummaryForDate(date: LocalDate): DailySummary {
        return DailySummary.fromEntries(getEntriesForDate(date))
    }

    fun getDistinctDates(): List<LocalDate> {
        return _entries.value.map { it.date }.distinct().sortedDescending()
    }

    /**
     * Paginovani sumarni podaci po datumu za HistoryScreen.
     */
    suspend fun getDateSummariesPaginated(limit: Int, offset: Int): List<DateSummary> {
        return entryDao.getDateSummaries(limit, offset)
    }

    /**
     * Broj obroka (grupisanih kartica, ne pojedinačnih stavki) po datumu.
     */
    suspend fun getMealCountsForDates(dates: List<LocalDate>): Map<LocalDate, Int> {
        if (dates.isEmpty()) return emptyMap()
        return entryDao.getMealTimingsForDates(dates)
            .groupBy { it.date }
            .mapValues { (_, timings) -> countMealGroups(timings) }
    }

    /**
     * Učitaj entries za određeni datum direktno iz Room baze.
     */
    suspend fun getEntriesForDateFromDb(date: LocalDate): List<FoodEntry> {
        return entryDao.getEntriesForDate(date).map { it.toDomain() }
    }

    /**
     * Ukupan broj različitih datuma sa unosima.
     */
    suspend fun getDistinctDateCount(): Int {
        return entryDao.getDistinctDateCount()
    }

    fun updateGoals(goals: DailyGoals) {
        ioScope.launch {
            fileMutex.withLock {
                _goals.value = goals
                saveGoalsInternal()
            }
        }
    }

    fun updateProfile(profile: UserProfile, recalculateGoals: Boolean = true) {
        ioScope.launch {
            fileMutex.withLock {
                _profile.value = profile
                if (recalculateGoals) {
                    _goals.value = profile.toDailyGoals()
                }
                saveProfileInternal()
                if (recalculateGoals) {
                    saveGoalsInternal()
                }
            }

            // Rekalkulacija TDEE je namerno van fileMutex bloka —
            // recalculateTdeeFromHistoryInternal() interno uzima mutex sama
            if (profile.useTrt && profile.weightHistory.size >= 2) {
                recalculateTdeeFromHistoryInternal()
            }
        }
    }

    /**
     * Add a weight entry and update profile.
     * Stores the actual measured weight without normalization.
     *
     * @param weightKg Measured weight in kg
     * @param time Optional time of measurement in "HH:mm" format
     */
    /**
     * Proveri da li je post-cycle stabilizacija istekla i resetuj je.
     * Mora se pozvati unutar fileMutex.
     */
    private fun checkPostCycleExpiry() {
        val profile = _profile.value
        val postCycle = profile.postCycleState ?: return
        if (!postCycle.isActive) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Post-cycle stabilizacija istekla, resetujem")
            _profile.value = profile.copy(postCycleState = null)
        }
    }

    fun addWeightEntry(weightKg: Double, time: String? = null) {
        require(weightKg in 20.0..500.0) { "Težina mora biti između 20 i 500 kg" }

        // Logički datum (dan počinje u 5h) — isto kao unosi hrane, da bi merenje
        // u 04:30 pripadalo istom danu kao i hrana te noći.
        val measurementDate = getLogicalDate()
        val measurementTime = time?.let { WeightNormalizer.parseTime(it) } ?: LocalTime.now()

        // Težina se čuva tačno kako je izmerena — bez korekcija. Zaokruživanje na 2 decimale
        // (rezolucija vage) samo skida floating-point smeće tipa 81.349999.
        val entry = WeightEntry(
            date = measurementDate,
            weightKg = Math.round(weightKg * 100.0) / 100.0,
            time = time ?: WeightNormalizer.formatTime(measurementTime)
        )

        if (BuildConfig.DEBUG) Log.d(TAG, "Adding weight entry: $weightKg kg at $time")

        ioScope.launch {
            fileMutex.withLock {
                // Proveri da li je post-cycle istekao pre rekalkulacije
                checkPostCycleExpiry()

                val current = _profile.value
                val updatedProfile = current.addWeightEntry(entry).let { updated ->
                    // Prvo merenje = dan 1 nedeljnog perioda. Fiksira se jednom i ne pomera se.
                    // Ako već postoji starija istorija (profil pre uvođenja polja), uzima se
                    // njen najstariji datum — isto što i fallback u weeklyPeriodAnchor, bez skoka.
                    if (current.weightPeriodStartDate == null) {
                        val earliest = current.weightHistory.minOfOrNull { it.date } ?: entry.date
                        updated.copy(weightPeriodStartDate = minOf(earliest, entry.date))
                    } else updated
                }
                if (BuildConfig.DEBUG) Log.d(TAG, "Updated profile weightHistory size: ${updatedProfile.weightHistory.size}")
                _profile.value = updatedProfile
                _goals.value = updatedProfile.toDailyGoals()
                saveProfileInternal()
                saveGoalsInternal()
            }

            // Rekalkulacija TDEE je van fileMutex bloka jer sama interno uzima mutex
            recalculateTdeeFromHistoryInternal()
        }
    }

    /**
     * Delete a weight entry by date
     */
    fun deleteWeightEntry(date: LocalDate) {
        ioScope.launch {
            fileMutex.withLock {
                val updatedProfile = _profile.value.copy(
                    weightHistory = _profile.value.weightHistory.filter { it.date != date }
                )
                _profile.value = updatedProfile
                saveProfileInternal()
            }
        }
    }

    /**
     * Set goal weight for progress tracking
     */
    fun setGoalWeight(weight: Double?) {
        ioScope.launch {
            fileMutex.withLock {
                val updatedProfile = _profile.value.copy(goalWeight = weight)
                _profile.value = updatedProfile
                saveProfileInternal()
            }
        }
    }

    /**
     * Set fixed starting weight for progress tracking (does not affect TDEE calculations)
     */
    fun setFixedStartingWeight(weight: Double?) {
        ioScope.launch {
            fileMutex.withLock {
                val updatedProfile = _profile.value.copy(fixedStartingWeight = weight)
                _profile.value = updatedProfile
                saveProfileInternal()
            }
        }
    }

    /**
     * Set fixed starting weight.
     * Stores the actual measured weight without normalization.
     */
    fun setFixedStartingWeightNormalized(weightKg: Double, @Suppress("UNUSED_PARAMETER") time: String?) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Setting starting weight: $weightKg kg")

        ioScope.launch {
            fileMutex.withLock {
                val updatedProfile = _profile.value.copy(fixedStartingWeight = weightKg)
                _profile.value = updatedProfile
                saveProfileInternal()
            }
        }
    }

    /**
     * Reset all weight progress data.
     * Clears: weightHistory, goalWeight, fixedStartingWeight, tdeeAdjustment
     */
    fun resetWeightProgress() {
        ioScope.launch {
            fileMutex.withLock {
                val updatedProfile = _profile.value.copy(
                    weightHistory = emptyList(),
                    weightPeriodStartDate = null, // sledeće merenje ponovo postaje dan 1
                    goalWeight = null,
                    fixedStartingWeight = null,
                    tdeeAdjustment = 0,
                    naturalTdeeProfile = TdeeProfile(),
                    trtTdeeProfile = TdeeProfile(),
                    postCycleState = null
                )
                _profile.value = updatedProfile
                _goals.value = updatedProfile.toDailyGoals()
                saveProfileInternal()
                saveGoalsInternal()
            }
        }
    }

    /**
     * Recalculate TDEE adjustment based on actual weight changes vs calories consumed
     * Should be called after at least 1-2 weeks of data.
     * Public version - safe to call from any thread.
     */
    fun recalculateTdeeFromHistory() {
        ioScope.launch {
            recalculateTdeeFromHistoryInternal()
        }
    }

    private fun getCaloriesByDate(): Map<LocalDate, Double> {
        return caloriesByDateCache ?: _entries.value
            .groupBy { it.date }
            .mapValues { (_, entries) -> entries.sumOf { it.calories } }
            .also { caloriesByDateCache = it }
    }

    /**
     * Internal version for use within coroutine scope.
     * Must be called from ioScope.
     */
    private suspend fun recalculateTdeeFromHistoryInternal() {
        val profile = _profile.value
        // Preskoči ako je TDEE ručno zaključan
        if (profile.manualTdee != null) return
        if (profile.weightHistory.size < 2) return

        // Post-cycle stabilizacija: ne uči TDEE
        if (profile.postCycleState?.isActive == true) return

        // Za TRT: faze 1 i 2 ne uče TDEE
        if (profile.useTrt && profile.trtStartDate != null && profile.trtTdeePhase < 3) return

        // Calculate average daily calories over the tracking period
        val sortedHistory = profile.weightHistory.sortedBy { it.date }

        // Za TRT fazu 3: koristi samo podatke od dana 28+ (stabilni period)
        val startDate = if (profile.useTrt && profile.trtStartDate != null) {
            val stableDate = profile.trtStartDate.plusDays(28)
            sortedHistory.firstOrNull { !it.date.isBefore(stableDate) }?.date ?: return
        } else {
            sortedHistory.first().date
        }
        val endDate = sortedHistory.last().date

        val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate).toInt()
        if (daysBetween < UserProfile.MIN_TDEE_LEARNING_DAYS) return // Need at least 3 weeks

        val caloriesByDate = getCaloriesByDate()

        var totalCalories = 0.0
        var daysWithEntries = 0
        var currentDate = startDate
        while (!currentDate.isAfter(endDate)) {
            caloriesByDate[currentDate]?.let { cals ->
                totalCalories += cals
                daysWithEntries++
            }
            currentDate = currentDate.plusDays(1)
        }

        if (daysWithEntries == 0) return

        // Dani bez unosa se NE smeju tiho izostaviti iz proseka — neupisani dan
        // sistematski podiže prosek i time naduvava naučeni TDEE. Ako pokrivenost
        // nije dovoljna, radije ne uči ništa nego da uči na iskrivljenim podacima.
        val totalDaysInPeriod = daysBetween + 1
        val coverage = daysWithEntries.toDouble() / totalDaysInPeriod
        if (coverage < MIN_LOGGING_COVERAGE) {
            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "Preskačem TDEE učenje: pokrivenost ${(coverage * 100).toInt()}% (< ${(MIN_LOGGING_COVERAGE * 100).toInt()}%)"
            )
            return
        }

        val averageDailyCalories = totalCalories / daysWithEntries

        fileMutex.withLock {
            val updatedProfile = _profile.value.calculateLearnedTdeeAdjustment(averageDailyCalories)
            _profile.value = updatedProfile
            _goals.value = updatedProfile.toDailyGoals()
            saveProfileInternal()
            saveGoalsInternal()
        }
    }

    /**
     * Calculate insight about weight progress vs calorie intake
     * Returns null if not enough data
     *
     * Na TRT-u oduzima PRIRAST vodene retencije između dva merenja — inače bi voda
     * iz ranih nedelja ciklusa bila protumačena kao dobitak tkiva i zaključak bi
     * bio "TDEE je manji".
     */
    fun getWeightInsight(): WeightInsight? {
        // Snapshot oba StateFlow-a na početku za konzistentno stanje tokom cele funkcije
        // (čitanje bez mutexa je prihvatljivo jer su StateFlow write-ovi atomski)
        val currentProfile = _profile.value
        val currentEntries = _entries.value
        if (currentProfile.weightHistory.size < 2) return null

        val sortedHistory = currentProfile.weightHistory.sortedBy { it.date }
        val oldestEntry = sortedHistory.first()
        val newestEntry = sortedHistory.last()

        val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(oldestEntry.date, newestEntry.date).toInt()
        if (daysBetween < 7) return null

        // Get food entries in this period
        val entriesInPeriod = currentEntries.filter { entry ->
            !entry.date.isBefore(oldestEntry.date) && !entry.date.isAfter(newestEntry.date)
        }
        if (entriesInPeriod.isEmpty()) return null

        // Calculate averages - koristi dane sa unosima (konzistentno sa recalculateTdeeFromHistoryInternal)
        val totalCalories = entriesInPeriod.sumOf { it.calories }
        val daysWithEntries = entriesInPeriod.map { it.date }.distinct().size.coerceAtLeast(1)
        val avgCaloriesConsumed = (totalCalories / daysWithEntries.toDouble()).toInt()
        val targetCalories = currentProfile.targetCalories

        // Weight changes (converted to weekly)
        // Korekcija za prirast vode na TRT ciklusu
        val waterAtStart = trtWaterRetentionOn(currentProfile, oldestEntry.date)
        val waterAtEnd = trtWaterRetentionOn(currentProfile, newestEntry.date)
        val waterGain = waterAtEnd - waterAtStart

        val actualWeightChange =
            (newestEntry.weightKg - oldestEntry.weightKg) - waterGain
        val actualWeeklyChange = (actualWeightChange / daysBetween) * 7
        val targetWeeklyChange = currentProfile.predictedWeeklyChange

        // Differences
        val caloriesDiff = avgCaloriesConsumed - targetCalories
        val weightDiff = actualWeeklyChange - targetWeeklyChange

        // Tolerance thresholds
        val calorieTolerance = 100 // kcal
        val weightTolerance = 0.15 // kg/week

        val ateMore = caloriesDiff > calorieTolerance
        val ateLess = caloriesDiff < -calorieTolerance
        val ateOnPlan = !ateMore && !ateLess

        val gainedMore = weightDiff > weightTolerance
        val gainedLess = weightDiff < -weightTolerance
        val onTrackWeight = !gainedMore && !gainedLess

        // Determine insight type and message
        val (type, baseMessage) = when {
            ateOnPlan && onTrackWeight -> InsightType.ON_TRACK to "Na dobrom si putu!"

            ateMore && gainedMore -> InsightType.OVEREATING_EFFECT to
                "Prekoračenje: ${String.format("%+d", caloriesDiff)} kcal/dan → ${String.format("%+.2f", weightDiff)} kg/ned extra"

            ateLess && gainedLess -> InsightType.UNDEREATING_EFFECT to
                "Manji unos: ${String.format("%+d", caloriesDiff)} kcal/dan → ${String.format("%+.2f", weightDiff)} kg/ned razlika"

            ateMore && onTrackWeight -> InsightType.TDEE_HIGHER to
                "Jedeš ${String.format("%+d", caloriesDiff)} kcal više ali pratiš cilj → TDEE veći od formule"

            ateMore && gainedLess -> InsightType.TDEE_HIGHER to
                "Jedeš više ali dobijaš manje → tvoj TDEE je znatno veći"

            ateLess && onTrackWeight -> InsightType.TDEE_LOWER to
                "Jedeš ${String.format("%d", -caloriesDiff)} kcal manje ali pratiš cilj → TDEE manji od formule"

            ateLess && gainedMore -> InsightType.TDEE_LOWER to
                "Jedeš manje ali dobijaš više → tvoj TDEE je manji"

            ateOnPlan && gainedMore -> InsightType.TDEE_LOWER to
                "Pratiš plan ali dobijaš ${String.format("%+.2f", weightDiff)} kg/ned više (bez vode) → TDEE manji"

            ateOnPlan && gainedLess -> InsightType.TDEE_HIGHER to
                "Pratiš plan ali dobijaš ${String.format("%.2f", -weightDiff)} kg/ned manje → TDEE veći od formule"

            else -> InsightType.ON_TRACK to "Na dobrom si putu!"
        }

        return WeightInsight(
            type = type,
            message = baseMessage,
            avgCaloriesConsumed = avgCaloriesConsumed,
            targetCalories = targetCalories,
            caloriesDiff = caloriesDiff,
            actualWeeklyChange = actualWeeklyChange,
            targetWeeklyChange = targetWeeklyChange,
            weightDiff = weightDiff
        )
    }

    /**
     * Procenjena vodena retencija (kg) na dati datum za aktivni TRT ciklus.
     * Vraća 0 ako korisnik nije na ciklusu ili je datum pre početka ciklusa.
     */
    private fun trtWaterRetentionOn(profile: UserProfile, date: LocalDate): Double {
        val start = profile.trtStartDate
        if (!profile.useTrt || start == null) return 0.0
        val days = java.time.temporal.ChronoUnit.DAYS.between(start, date)
        if (days <= 0) return 0.0
        return WeightNormalizer.estimateWaterRetention(
            weeksOnCycle = days / 7.0,
            doseMgPerWeek = profile.trtDoseMgWeek,
            usingAi = profile.usesAromataseInhibitor
        )
    }

    /**
     * End the current TRT cycle and create a history entry with analysis
     * Returns the created CycleHistory or null if no active cycle.
     * Note: This runs synchronously to return the result immediately.
     */
    fun endTrtCycle(): CycleHistory? {
        // Take consistent snapshots
        val profile = _profile.value
        val currentEntries = _entries.value
        if (!profile.useTrt || profile.trtStartDate == null) return null

        val startDate = profile.trtStartDate
        val endDate = LocalDate.now()

        // Get weight at cycle start
        val startWeight = profile.weightAtCycleStart ?: profile.weightKg
        val endWeight = profile.currentWeight

        // Calculate average daily calories during cycle
        val entriesInCycle = currentEntries.filter { entry ->
            !entry.date.isBefore(startDate) && !entry.date.isAfter(endDate)
        }

        // Koristi dane sa unosima za prosek (konzistentno sa getLiveCycleStats)
        val daysWithEntries = entriesInCycle.map { it.date }.distinct().size.coerceAtLeast(1)

        val avgDailyCalories = if (entriesInCycle.isNotEmpty()) {
            (entriesInCycle.sumOf { it.calories } / daysWithEntries).toInt()
        } else 0

        val cycleEntry = CycleHistory(
            startDate = startDate,
            endDate = endDate,
            startWeight = startWeight,
            endWeight = endWeight,
            avgDailyCalories = avgDailyCalories,
            doseMgPerWeek = profile.trtDoseMgWeek,
            usedAi = profile.usesAromataseInhibitor,
            tdee = profile.tdee.toInt()
        )

        // Sačuvaj TDEE u trtTdeeProfile pre resetovanja
        val savedTrtProfile = if (profile.effectiveTdeeAdjustment != 0) {
            profile.trtTdeeProfile.copy(
                learnedAdjustment = profile.effectiveTdeeAdjustment,
                lastUpdated = endDate,
                trackingDays = profile.trackingDays,
                isConfident = profile.hasConfidentTdee
            )
        } else {
            profile.trtTdeeProfile
        }

        // Kreiraj post-cycle stabilizaciju
        val postCycle = PostCycleState(cycleEndDate = endDate)

        // Update profile atomically: turn off TRT and add to history
        val updatedProfile = profile.copy(
            useTrt = false,
            trtStartDate = null,
            trtCycleEndDate = null,
            cycleHistory = profile.cycleHistory + cycleEntry,
            trtTdeeProfile = savedTrtProfile,
            postCycleState = postCycle
        )

        // StateFlow write je atomski, pa postavljamo sinhrono za trenutnu UI reaktivnost.
        // Čuvanje na disk ide asinhrono unutar mutexa radi konzistentnosti sa drugim operacijama.
        _profile.value = updatedProfile
        _goals.value = updatedProfile.toDailyGoals()

        ioScope.launch {
            fileMutex.withLock {
                saveProfileInternal()
                saveGoalsInternal()
            }
        }

        return cycleEntry
    }

    /**
     * Get all completed cycles
     */
    fun getCycleHistory(): List<CycleHistory> = _profile.value.cycleHistory

    /**
     * Get live statistics for the current active TRT cycle
     * Returns null if no active cycle
     */
    fun getLiveCycleStats(): LiveCycleStats? {
        val currentProfile = _profile.value
        val currentEntries = _entries.value
        val startDate = currentProfile.trtStartDate
        if (!currentProfile.useTrt || startDate == null) return null

        val today = LocalDate.now()

        val daysOnCycle = java.time.temporal.ChronoUnit.DAYS.between(startDate, today).toInt().coerceAtLeast(0)
        val weeksOnCycle = (daysOnCycle / 7) + 1 // 1-based: nedelja 1 = dani 0-6, nedelja 2 = dani 7-13, itd.

        val startWeight = currentProfile.weightAtCycleStart ?: currentProfile.weightKg
        val currentWeight = currentProfile.currentWeight

        // Calculate average daily intake during cycle
        val entriesInCycle = currentEntries.filter { entry ->
            !entry.date.isBefore(startDate) && !entry.date.isAfter(today)
        }

        // Broj dana sa unosima (ne ukupan broj dana na ciklusu)
        val daysWithEntries = entriesInCycle.map { it.date }.distinct().size.coerceAtLeast(1)

        val avgDailyCalories = if (entriesInCycle.isNotEmpty()) {
            (entriesInCycle.sumOf { it.calories } / daysWithEntries).toInt()
        } else 0

        return LiveCycleStats(
            weeksOnCycle = weeksOnCycle,
            daysOnCycle = daysOnCycle,
            startWeight = startWeight,
            currentWeight = currentWeight,
            avgDailyCalories = avgDailyCalories,
            isWaterRetentionPhase = currentProfile.isInWaterRetentionPhase,
            doseMgPerWeek = currentProfile.trtDoseMgWeek,
            usingAi = currentProfile.usesAromataseInhibitor,
            tdee = currentProfile.tdee.toInt()
        )
    }

    data class ImportResult(
        val totalItems: Int,
        val warnings: List<String> = emptyList()
    )

    fun exportJson(): String {
        return KalorijeBackupCodec.encode(
            BackupSnapshot(
                entries = _entries.value,
                goals = _goals.value,
                profile = _profile.value,
                savedFoods = _savedFoods.value,
                shakeOrder = _shakeOrder.value
            )
        )
    }

    /**
     * JSON za backup u oblak. Odbija dok podaci nisu učitani ili ako neka
     * sekcija pri startu nije mogla da se pročita — prazno ne sme da pregazi
     * popunjen backup.
     */
    fun exportJsonForBackup(): String {
        check(_isLoaded.value) { "Podaci se još učitavaju — pokušaj ponovo za trenutak" }
        loadProblem?.let { problem -> error("$problem — backup je preskočen da ne bi pregazio dobar backup") }
        return exportJson()
    }

    fun hasLocalData(): Boolean {
        // Proveri Room keš (brz) ili JSON fajl (za stare podatke)
        return _entries.value.isNotEmpty() || (entriesFile.exists() && entriesFile.length() > 0)
    }

    /**
     * Vraćanje iz backup-a — sve ili ništa:
     *  1. ceo JSON se dekodira i proverava PRE prve izmene (neispravna sekcija = greška)
     *  2. profile/goals/šejk fajlovi se strogo snimaju, Room ide u JEDNOJ transakciji
     *  3. ako bilo šta pukne, fajlovi se vraćaju na staro stanje, a Room transakcija se poništava
     *  4. tek na kraju se osvežava stanje u memoriji
     */
    suspend fun importJson(raw: String): Result<ImportResult> {
        // Vraćanje iz oblaka ne sme odmah da okine backup tog istog stanja nazad.
        suppressAutoBackup = true
        return try {
            if (BuildConfig.DEBUG) Log.d(TAG, "importJson: starting, raw length: ${raw.length}")
            withTimeoutOrNull(15_000) { awaitLoaded() }
                ?: error("Lokalni podaci se još učitavaju — pokušaj ponovo")

            val snapshot = KalorijeBackupCodec.decode(raw)

            // Isto što radi loadProfile(): stari backup može da nosi nemigriran profil
            val newProfile = snapshot.profile?.let { migrateGoalIfNeeded(it.migrateIfNeeded()) }
            val newGoals = snapshot.goals ?: newProfile?.toDailyGoals()
            val newSavedFoods = snapshot.savedFoods
            // Šejk lista: samo ID-jevi namirnica koje posle vraćanja stvarno postoje.
            // Stari backup bez ovog ključa -> zadržava se lokalna lista.
            val savedFoodIdsAfterRestore = (newSavedFoods ?: _savedFoods.value).map { it.id }.toSet()
            val newShakeOrder = (snapshot.shakeOrder ?: _shakeOrder.value).filter { it in savedFoodIdsAfterRestore }

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "importJson: parsed ${snapshot.entries.size} entries, ${newSavedFoods?.size} saved foods")
            }

            withContext(ioScope.coroutineContext) {
                fileMutex.withLock {
                    shakeOrderMutex.withLock {
                        val oldProfile = _profile.value
                        val oldGoals = _goals.value
                        val oldShakeOrder = _shakeOrder.value
                        try {
                            if (newProfile != null) writeFileStrict(profileFile, newProfile, UserProfile::class.java)
                            if (newGoals != null) writeFileStrict(goalsFile, newGoals, DailyGoals::class.java)
                            writeFileStrict(shakeOrderFile, newShakeOrder, STRING_LIST_TYPE)

                            db.withTransaction {
                                entryDao.deleteAll()
                                val entryEntities = snapshot.entries.map { entry ->
                                    val productId = findOrInsertProduct(entry.product)
                                    check(productId > 0) { "Proizvod nije mogao biti upisan: ${entry.product.name}" }
                                    entry.toEntity(productId)
                                }
                                entryDao.insertAll(entryEntities)

                                if (newSavedFoods != null) {
                                    savedFoodDao.deleteAll()
                                    savedFoodDao.insertAll(newSavedFoods.map { it.toEntity() })
                                }
                            }
                        } catch (e: Exception) {
                            // Room transakcija je već poništena; vrati i fajlove na staro.
                            runCatching { writeFileStrict(profileFile, oldProfile, UserProfile::class.java) }
                            runCatching { writeFileStrict(goalsFile, oldGoals, DailyGoals::class.java) }
                            runCatching { writeFileStrict(shakeOrderFile, oldShakeOrder, STRING_LIST_TYPE) }
                            throw e
                        }

                        // Sve je na disku — osveži stanje u memoriji
                        if (newProfile != null) _profile.value = newProfile
                        if (newGoals != null) _goals.value = newGoals
                        _shakeOrder.value = newShakeOrder
                    }
                }

                // Učitaj iz Room u StateFlow keš
                reloadEntriesFromDb()
                reloadSavedFoodsFromDb(syncShakeOrder = false)
                loadProblem = null
                if (BuildConfig.DEBUG) Log.d(TAG, "importJson: svi podaci sačuvani")
            }

            val totalItems = snapshot.entries.size + (newSavedFoods?.size ?: 0)
            Result.success(ImportResult(totalItems = totalItems))
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to import JSON", e)
            Result.failure(e)
        } finally {
            suppressAutoBackup = false
        }
    }

    companion object {
        private const val TAG = "FoodRepository"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 100L

        /** Minimalni udeo dana sa upisanom hranom da bi prosek bio upotrebljiv za TDEE učenje. */
        private const val MIN_LOGGING_COVERAGE = 0.8

        // TypeToken za importJson Gson parsiranje (entries)
        private val FOOD_ENTRY_LIST_TYPE: Type = object : TypeToken<List<FoodEntry>>() {}.type
        // TypeToken za importJson Gson parsiranje (saved foods)
        private val SAVED_FOOD_LIST_TYPE: Type = object : TypeToken<List<SavedFood>>() {}.type
        // TypeToken za čuvanje redosleda šejk liste
        private val STRING_LIST_TYPE: Type = object : TypeToken<List<String>>() {}.type
    }
}
