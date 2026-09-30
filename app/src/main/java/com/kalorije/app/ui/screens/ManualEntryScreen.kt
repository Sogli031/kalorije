package com.kalorije.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kalorije.app.data.model.FoodEntry
import com.kalorije.app.data.model.FoodProduct
import com.kalorije.app.data.model.MealCartItem
import com.kalorije.app.data.model.SavedFood
import com.kalorije.app.data.repository.FoodRepository
import com.kalorije.app.ui.components.GlassCard
import com.kalorije.app.ui.components.MacroDisplay
import com.kalorije.app.ui.components.SavedFoodsPicker
import com.kalorije.app.ui.components.ScreenHeader
import com.kalorije.app.ui.components.SectionHeader
import com.kalorije.app.ui.components.SegmentedControl
import com.kalorije.app.ui.components.appTextFieldColors
import com.kalorije.app.ui.theme.AppColors
import com.kalorije.app.ui.theme.AppDimens
import com.kalorije.app.ui.theme.AppTypography
import com.kalorije.app.util.getLogicalDate
import com.kalorije.app.util.WeightNormalizer
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToInt

private const val MANUAL_MODE_INDEX = 0
private const val SAVED_MODE_INDEX = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryScreen(
    repository: FoodRepository,
    targetDate: LocalDate? = null,
    onNavigateBack: () -> Unit,
    onNavigateToSavedFoods: () -> Unit
) {
    val logicalDate = getLogicalDate()
    val entryDate = targetDate ?: logicalDate
    val pendingSelections by repository.pendingFoodSelections.collectAsState()
    val savedFoods by repository.savedFoods.collectAsState()

    // Korpa - lista jela za unos (sačuvana u repository da preživi navigaciju)
    val mealCart by repository.mealCart.collectAsState()

    // Vreme obroka - default je trenutno vreme
    var mealTime by rememberSaveable {
        val now = LocalTime.now()
        mutableStateOf(String.format("%02d:%02d", now.hour, now.minute))
    }

    // Consume pending selections when returning from SavedFoodsScreen
    LaunchedEffect(pendingSelections) {
        if (pendingSelections.isNotEmpty()) {
            val items = repository.consumePendingFoodSelections()
            items.forEach { item ->
                repository.addToMealCart(
                    MealCartItem(
                        name = item.food.name,
                        grams = item.grams,
                        caloriesPer100g = item.food.caloriesPer100g
                    )
                )
            }
        }
    }

    // Ručni unos forme
    var name by rememberSaveable { mutableStateOf("") }
    var caloriesText by rememberSaveable { mutableStateOf("") }
    var gramsText by rememberSaveable { mutableStateOf("100") }
    var saveForLater by rememberSaveable { mutableStateOf(false) }
    var isPerHundredGrams by rememberSaveable { mutableStateOf(true) }
    var selectedEntryMode by rememberSaveable { mutableStateOf(MANUAL_MODE_INDEX) }
    var isSubmitting by rememberSaveable { mutableStateOf(false) }
    val entryModes = listOf("Ručno", "Sačuvano")

    // Funkcija za resetovanje forme
    fun resetForm() {
        name = ""
        caloriesText = ""
        gramsText = "100"
        saveForLater = false
        isPerHundredGrams = true
    }

    // Calculate values based on mode with validation
    val grams = gramsText.toDoubleOrNull() ?: 0.0
    val gramsError = gramsText.isNotBlank() && grams <= 0
    val inputCalories = caloriesText.toDoubleOrNull() ?: 0.0
    val caloriesError = caloriesText.isNotBlank() && inputCalories < 0

    // Per 100g vrednost
    val caloriesPer100 =
        if (isPerHundredGrams) inputCalories else if (grams > 0) inputCalories * 100 / grams else 0.0

    val canAddToCart = name.isNotBlank() && grams > 0 && inputCalories >= 0 && !gramsError && !caloriesError

    // Ukupno iz korpe
    val cartTotalCalories = mealCart.sumOf { it.totalCalories }
    var showExitCartDialog by rememberSaveable { mutableStateOf(false) }

    fun submitMealAndExit() {
        if (isSubmitting || mealCart.isEmpty()) return
        isSubmitting = true
        val cartSnapshot = mealCart.toList()

        // Ako ima vise od jednog jela, grupisi ih zajedno
        val groupId = if (cartSnapshot.size > 1) {
            UUID.randomUUID().toString()
        } else null

        // Validiraj vreme koristeci standardnu validaciju
        val time = WeightNormalizer.parseTime(mealTime) ?: LocalTime.now()
        val validTime = WeightNormalizer.formatTime(time)

        val entries = cartSnapshot.map { item ->
            val product = FoodProduct(
                name = item.name,
                caloriesPer100g = item.caloriesPer100g
            )
            FoodEntry(
                id = UUID.randomUUID().toString(),
                date = entryDate,
                product = product,
                grams = item.grams,
                groupId = groupId,
                time = validTime
            )
        }
        repository.addAll(entries)
        repository.clearMealCart()
        showExitCartDialog = false
        onNavigateBack()
    }

    fun exitWithoutSaving() {
        repository.clearMealCart()
        showExitCartDialog = false
        onNavigateBack()
    }

    fun handleNavigateBack() {
        if (mealCart.isNotEmpty()) {
            showExitCartDialog = true
        } else {
            onNavigateBack()
        }
    }

    BackHandler {
        if (showExitCartDialog) {
            showExitCartDialog = false
        } else {
            handleNavigateBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .statusBarsPadding()
            .padding(AppDimens.paddingMedium)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader(
            title = "Unesi obrok",
            subtitle = if (targetDate != null && targetDate != logicalDate) {
                "za ${targetDate.format(DateTimeFormatter.ofPattern("d. MMMM"))}"
            } else null,
            onBack = ::handleNavigateBack
        )

        Spacer(modifier = Modifier.height(AppDimens.paddingMedium))

        // KORPA - prikaži ako ima stavki
        if (mealCart.isNotEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = AppColors.Primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Korpa (${mealCart.size} stavki)",
                            style = AppTypography.labelLarge
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    mealCart.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = AppTypography.labelMedium
                                )
                                Text(
                                    text = "${item.grams.roundToInt()}g - ${item.totalCalories} kcal",
                                    style = AppTypography.labelSmall.copy(color = AppColors.TextMuted)
                                )
                            }
                            IconButton(
                                onClick = { repository.removeFromMealCart(item.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Ukloni",
                                    tint = AppColors.TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Vreme obroka
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Vreme obroka:",
                            style = AppTypography.labelMedium
                        )
                        OutlinedTextField(
                            value = mealTime,
                            onValueChange = { input ->
                                // Dozvoli samo brojeve i dvotačku, max 5 karaktera (HH:mm)
                                val filtered = input.filter { it.isDigit() || it == ':' }.take(5)
                                mealTime = filtered
                            },
                            modifier = Modifier.width(100.dp),
                            singleLine = true,
                            placeholder = { Text("HH:mm") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppColors.Primary,
                                unfocusedBorderColor = AppColors.GlassBorder,
                                focusedTextColor = AppColors.TextPrimary,
                                unfocusedTextColor = AppColors.TextPrimary
                            ),
                            textStyle = AppTypography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppDimens.paddingMedium))
        }

        SegmentedControl(
            options = entryModes,
            selectedIndex = selectedEntryMode,
            onSelected = { selectedEntryMode = it },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(AppDimens.paddingMedium))

        if (selectedEntryMode == SAVED_MODE_INDEX) {
            // Lista sačuvanih jela se otvara odmah, u istom ekranu
            SavedFoodsPicker(
                savedFoods = savedFoods,
                onAddItems = { items ->
                    items.forEach { item ->
                        repository.addToMealCart(
                            MealCartItem(
                                name = item.food.name,
                                grams = item.grams,
                                caloriesPer100g = item.food.caloriesPer100g
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                onManageSavedFoods = onNavigateToSavedFoods
            )
        }

        if (selectedEntryMode == MANUAL_MODE_INDEX) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Naziv hrane") },
                        placeholder = { Text("npr. Hleb") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = appTextFieldColors(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = gramsText,
                        onValueChange = { gramsText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Grama") },
                        placeholder = { Text("npr. 50") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        colors = appTextFieldColors(),
                        singleLine = true,
                        isError = gramsError,
                        supportingText = if (gramsError) {
                            { Text("Gramaza mora biti veća od 0", color = AppColors.Error) }
                        } else null
                    )

                    SegmentedControl(
                        options = listOf("Po 100g", "Ukupno"),
                        selectedIndex = if (isPerHundredGrams) 0 else 1,
                        onSelected = { isPerHundredGrams = it == 0 },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isPerHundredGrams) "Unos po 100g" else "Unos ukupno",
                            style = AppTypography.labelSmall.copy(color = AppColors.TextMuted)
                        )
                    }

                    OutlinedTextField(
                        value = caloriesText,
                        onValueChange = { caloriesText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text(if (isPerHundredGrams) "Kalorije / 100g" else "Kalorije ukupno") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        colors = appTextFieldColors(),
                        singleLine = true,
                        isError = caloriesError,
                        supportingText = if (caloriesError) {
                            { Text("Kalorije ne mogu biti negativne", color = AppColors.Error) }
                        } else null
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = saveForLater,
                            onCheckedChange = { saveForLater = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AppColors.Primary,
                                uncheckedColor = AppColors.TextMuted
                            )
                        )
                        Text(
                            text = "Sačuvaj za kasnije",
                            style = AppTypography.labelSmall
                        )
                    }

                    // Dugme za dodavanje u korpu
                    OutlinedButton(
                        onClick = {
                            repository.addToMealCart(
                                MealCartItem(
                                    name = name,
                                    grams = grams,
                                    caloriesPer100g = caloriesPer100
                                )
                            )

                            if (saveForLater) {
                                repository.addSavedFood(
                                    SavedFood(
                                        name = name,
                                        caloriesPer100g = caloriesPer100,
                                        defaultGrams = grams
                                    )
                                )
                            }

                            resetForm()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Primary),
                        enabled = canAddToCart
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Dodaj u korpu")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(AppDimens.paddingMedium))

        if (mealCart.isNotEmpty()) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                bgColor = AppColors.SurfaceRaised,
                accentColor = AppColors.Primary
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        title = "Sažetak obroka",
                        trailing = "${mealCart.size} stavki"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MacroDisplay("Ukupno", "$cartTotalCalories", "kcal", AppColors.Calories)
                    }
                    Button(
                        onClick = ::submitMealAndExit,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                        enabled = !isSubmitting
                    ) {
                        Text("Sačuvaj obrok ($cartTotalCalories kcal)")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showExitCartDialog && mealCart.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showExitCartDialog = false },
            title = {
                Text("U korpi imaš nesačuvane stavke")
            },
            text = {
                Text("Želiš li da ih dodaš u obrok pre izlaska?")
            },
            confirmButton = {
                TextButton(
                    onClick = ::submitMealAndExit,
                    enabled = !isSubmitting
                ) {
                    Text("Dodaj i izađi")
                }
            },
            dismissButton = {
                TextButton(onClick = ::exitWithoutSaving) {
                    Text("Izađi bez čuvanja")
                }
            }
        )
    }
}

