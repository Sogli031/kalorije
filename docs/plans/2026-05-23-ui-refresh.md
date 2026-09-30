# Kalorije UI Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-optimized:subagent-driven-development (recommended) or superpowers-optimized:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refresh the Android Compose UI across the main calorie, meal-entry, history, weight-progress, and settings flows while preserving the existing data model, calculations, backup format, and logical-date behavior.

**Architecture:** Keep the current Compose structure and Material3 stack. Introduce a small set of shared visual primitives in `ui/components` and extend the existing `Theme.kt` tokens, then apply them screen by screen. Navigation destinations remain the same; only their visible presentation changes. Data access continues through the existing repositories and ViewModels.

**Tech Stack:** Kotlin, Android Compose, Material3, Navigation Compose, existing Space Grotesk fonts, existing Gradle Android plugin.

**Assumptions:**
- Assumes the proposed UI direction is approved: dark compact nutrition dashboard with stronger hierarchy — will NOT work if the current visual layout must remain unchanged.
- Assumes no business rules change — will NOT work if calorie, macro, TDEE, TRT, backup, or logical-date behavior is expected to change.
- Assumes existing Android unit/build verification is enough for this UI plan — will NOT prove device-specific visual layout without a manual emulator/device pass.
- Assumes project-map constraint still applies: no Git commands in this repository — will NOT include commit steps.

---

## Scope Check

This plan touches multiple UI surfaces, but they share one design system and one navigation shell. The tasks are ordered so each screen remains buildable after its task, and each task can be reviewed independently.

## File Structure

- `app/src/main/java/com/kalorije/app/ui/theme/Theme.kt`  
  Extend existing colors, typography, radii, spacing, and icon dimensions. Keep Space Grotesk.

- `app/src/main/java/com/kalorije/app/ui/components/GlassCard.kt`  
  Make the existing card primitive more flexible: radius, content padding, accent border, and selected state.

- `app/src/main/java/com/kalorije/app/ui/components/SharedComponents.kt`  
  Add reusable screen headers, section headers, metric pills, compact action buttons, segmented controls, and confirmation helpers.

- `app/src/main/java/com/kalorije/app/ui/components/MacroProgressBar.kt`  
  Add compact macro summary rows and over-goal visual states without changing macro calculations.

- `app/src/main/java/com/kalorije/app/ui/components/FoodEntryCard.kt`  
  Redesign meal cards for density and safer deletion.

- `app/src/main/java/com/kalorije/app/ui/navigation/NavGraph.kt`  
  Add bottom navigation shell for the main destinations while preserving all existing routes.

- `app/src/main/java/com/kalorije/app/ui/screens/HomeScreen.kt`  
  Rework today dashboard, primary actions, and meal list hierarchy.

- `app/src/main/java/com/kalorije/app/ui/screens/ManualEntryScreen.kt`  
  Rework entry workflow into saved/manual modes and add persistent cart summary.

- `app/src/main/java/com/kalorije/app/ui/screens/HistoryScreen.kt`  
  Add daily calorie progress, status text, and denser expanded content.

- `app/src/main/java/com/kalorije/app/ui/screens/weight/WeightProgressScreen.kt`  
  Reorder the weight screen around current status, chart, input, and collapsible advanced sections.

- `app/src/main/java/com/kalorije/app/ui/screens/settings/SettingsScreen.kt`  
  Group settings sections visually without changing setting semantics.

## Verification Commands

Run these after each task that edits Kotlin:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Run this after the final task:

```powershell
.\gradlew.bat testDebugUnitTest lint assembleDebug
```

Expected final outcome: all commands complete successfully. `lint` is configured with `abortOnError = false`; review output for new UI-related warnings even when the command exits successfully.

---

### Task 1: Extend Design Tokens And Shared Primitives

**Files:**
- Modify: `app/src/main/java/com/kalorije/app/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/kalorije/app/ui/components/GlassCard.kt`
- Modify: `app/src/main/java/com/kalorije/app/ui/components/SharedComponents.kt`
- Modify: `app/src/main/java/com/kalorije/app/ui/components/MacroProgressBar.kt`

**Security flag:** `none`

**Does NOT cover:** screen-specific layout changes, navigation changes, food deletion behavior, or any repository/ViewModel logic.

- [x] **Step 1: Lock existing non-UI behavior**

Run:

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: PASS. Existing `MealGroupingTest` remains green before UI edits.

- [x] **Step 2: Extend theme tokens**

In `Theme.kt`, keep the existing color names and add the new tokens below so existing call sites do not break:

```kotlin
object AppColors {
    val Background = Color(0xFF0D1117)
    val Surface = Color(0xFF161B22)
    val SurfaceLight = Color(0xFF21262D)
    val SurfaceRaised = Color(0xFF1C222B)
    val SurfacePressed = Color(0xFF262E38)
    val SurfaceOverlay = Color(0xE61A2028)

    val Primary = Color(0xFF4CAF50)
    val PrimaryDark = Color(0xFF388E3C)
    val PrimarySoft = Color(0x263FB950)

    val Calories = Color(0xFFFF6B6B)
    val Protein = Color(0xFF4ECDC4)
    val Fat = Color(0xFFFFD166)
    val Carbs = Color(0xFF95E1D3)

    val TextPrimary = Color(0xFFF0F6FC)
    val TextSecondary = Color(0xFFB7C0CC)
    val TextMuted = Color(0xFF7D8590)

    val GlassBorder = Color(0xFF30363D)
    val GlassBorderActive = Color(0xFF4A5564)
    val GlassBackground = Color(0xFF161B22)

    val Success = Color(0xFF3FB950)
    val Warning = Color(0xFFD29922)
    val Error = Color(0xFFF85149)
}

object AppDimens {
    val paddingXSmall = 4.dp
    val paddingSmall = 8.dp
    val paddingMedium = 16.dp
    val paddingLarge = 24.dp
    val radiusSmall = 8.dp
    val radiusMedium = 12.dp
    val radiusLarge = 16.dp
    val cornerRadius = radiusMedium
    val iconButtonSize = 44.dp
    val cardElevation = 0.dp
}
```

- [x] **Step 3: Upgrade `GlassCard` API while keeping existing calls valid**

Change `GlassCard` signature to:

```kotlin
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    bgColor: Color = AppColors.GlassBackground,
    borderColor: Color = AppColors.GlassBorder,
    cornerRadius: Dp = AppDimens.cornerRadius,
    contentPadding: Dp = AppDimens.paddingMedium,
    selected: Boolean = false,
    accentColor: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val resolvedBorder = when {
        selected -> accentColor ?: AppColors.Primary
        accentColor != null -> accentColor.copy(alpha = 0.6f)
        else -> borderColor
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(bgColor)
            .border(1.dp, resolvedBorder, shape)
            .padding(contentPadding),
        content = content
    )
}
```

- [x] **Step 4: Add shared UI primitives**

Append these composables to `SharedComponents.kt`:

```kotlin
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            onBack?.let {
                IconButton(onClick = it, modifier = Modifier.size(AppDimens.iconButtonSize)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Nazad", tint = AppColors.TextPrimary)
                }
            }
            Column {
                Text(title, style = AppTypography.titleLarge)
                subtitle?.let { Text(it, style = AppTypography.bodyMedium) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), content = actions)
    }
}

@Composable
fun SectionHeader(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = AppTypography.titleMedium)
        trailing?.let { Text(it, style = AppTypography.labelSmall) }
    }
}

@Composable
fun MetricPill(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(AppDimens.radiusSmall))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.36f), RoundedCornerShape(AppDimens.radiusSmall))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, style = AppTypography.labelSmall.copy(color = color))
        Text(value, style = AppTypography.labelMedium.copy(color = AppColors.TextPrimary))
    }
}

@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(AppDimens.radiusMedium))
            .background(AppColors.Surface)
            .border(1.dp, AppColors.GlassBorder, RoundedCornerShape(AppDimens.radiusMedium))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(AppDimens.radiusSmall))
                    .background(if (selected) AppColors.PrimarySoft else Color.Transparent)
                    .clickable { onSelected(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = AppTypography.labelLarge.copy(
                        color = if (selected) AppColors.Primary else AppColors.TextSecondary
                    )
                )
            }
        }
    }
}
```

Add missing imports in `SharedComponents.kt`:

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
```

- [x] **Step 5: Add compact macro row**

In `MacroProgressBar.kt`, add:

```kotlin
@Composable
fun CompactMacroProgressRow(
    label: String,
    current: Double,
    goal: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val overGoal = goal > 0 && current > goal
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = AppTypography.labelMedium.copy(color = color))
            Text(
                text = "${current.roundToInt()} / ${goal}g",
                style = AppTypography.labelSmall.copy(
                    color = if (overGoal) AppColors.Warning else AppColors.TextSecondary
                )
            )
        }
        MacroProgressBar(label = "", current = current, goal = goal, color = color)
    }
}
```

- [x] **Step 6: Verify**

Run:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Expected: PASS. No changes to tests are required because this task adds reusable UI primitives only.

---

### Task 2: Add Bottom Navigation Shell

**Files:**
- Modify: `app/src/main/java/com/kalorije/app/ui/navigation/NavGraph.kt`

**Security flag:** `none`

**Does NOT cover:** saved-food management redesign, per-screen content redesign, or route renaming. Bottom navigation is shown only for `home`, `history`, `weight_progress`, and `settings`; all edit/entry/detail screens stay full-screen.

- [x] **Step 1: Confirm routes compile before changes**

Run:

```powershell
.\gradlew.bat assembleDebug
```

Expected: PASS.

- [x] **Step 2: Wrap `NavHost` with Material3 `Scaffold` and main bottom bar**

In `NavGraph.kt`, add imports:

```kotlin
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import com.kalorije.app.ui.theme.AppColors
```

Create the destination model and bottom bar in the same file:

```kotlin
private data class MainNavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val mainNavItems = listOf(
    MainNavItem(Routes.HOME, "Danas", Icons.Default.Home),
    MainNavItem(Routes.HISTORY, "Istorija", Icons.Default.History),
    MainNavItem(Routes.WEIGHT_PROGRESS, "Težina", Icons.AutoMirrored.Filled.ShowChart),
    MainNavItem(Routes.SETTINGS, "Podešavanja", Icons.Default.Settings)
)
```

Inside `NavGraph`, before `NavHost`, derive current route:

```kotlin
val backStackEntry by navController.currentBackStackEntryAsState()
val currentRoute = backStackEntry?.destination?.route
val showBottomBar = currentRoute in mainNavItems.map { it.route }
```

Wrap the existing `NavHost`:

```kotlin
Scaffold(
    containerColor = AppColors.Background,
    bottomBar = {
        if (showBottomBar) {
            NavigationBar(containerColor = AppColors.Surface) {
                mainNavItems.forEach { item ->
                    val selected = currentRoute == item.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                navController.navigate(item.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    }
) { innerPadding ->
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = Modifier.padding(innerPadding)
    ) {
        // keep existing composable blocks
    }
}
```

- [x] **Step 3: Remove duplicate main-nav icon pressure from Home later**

Do not remove Home header icons in this task. Task 3 handles it after bottom navigation compiles.

- [x] **Step 4: Verify**

Run:

```powershell
.\gradlew.bat assembleDebug
```

Expected: PASS. Bottom navigation compiles and existing routes still resolve.

---

### Task 3: Rework Home Into Daily Dashboard

**Files:**
- Modify: `app/src/main/java/com/kalorije/app/ui/screens/HomeScreen.kt`
- Modify: `app/src/main/java/com/kalorije/app/ui/components/MacroProgressBar.kt`

**Security flag:** `none`

**Does NOT cover:** changing how `summary`, `todayEntries`, `goals`, `profile`, or meal grouping are computed. It only changes presentation.

- [x] **Step 1: Preserve current calculations**

In `HomeScreen.kt`, keep these existing state blocks unchanged:

```kotlin
val entries by repository.entries.collectAsState()
val goals by repository.goals.collectAsState()
val profile by repository.profile.collectAsState()

val today = getLogicalDate()
val todayEntries = remember(entries, today) {
    entries.filter { it.date == today }
}
val summary = remember(todayEntries) {
    MacroSummary.fromEntries(todayEntries)
}
```

- [x] **Step 2: Replace header with shared `ScreenHeader`**

Use:

```kotlin
ScreenHeader(
    title = "Kalorije",
    subtitle = today.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM")),
    actions = {
        IconButton(onClick = onNavigateToSavedFoods) {
            Icon(
                imageVector = Icons.Default.Bookmark,
                contentDescription = "Sačuvana jela",
                tint = AppColors.TextSecondary
            )
        }
    }
)
```

Remove Home header actions for history, weight, and settings because Task 2 provides bottom navigation.

- [x] **Step 3: Build a stronger summary card**

Replace the separate calorie and macro cards with one `GlassCard` using `bgColor = AppColors.SurfaceRaised`:

```kotlin
GlassCard(
    modifier = Modifier.fillMaxWidth(),
    bgColor = AppColors.SurfaceRaised,
    accentColor = AppColors.Calories,
    contentPadding = AppDimens.paddingLarge
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Danas", style = AppTypography.labelMedium)
                Text(
                    text = "${summary.calories.roundToInt()} kcal",
                    style = AppTypography.numberLarge.copy(color = AppColors.Calories)
                )
                Text(
                    text = "Cilj: ${goals.calories} kcal",
                    style = AppTypography.bodyMedium
                )
            }
            val remaining = (goals.calories - summary.calories).roundToInt()
            MetricPill(
                label = if (remaining >= 0) "Preostalo" else "Preko",
                value = "${kotlin.math.abs(remaining)} kcal",
                color = if (remaining >= 0) AppColors.Success else AppColors.Warning
            )
        }

        MacroProgressBar("Proteini", summary.protein, goals.protein, AppColors.Protein)
        MacroProgressBar("Masti", summary.fat, goals.fat, AppColors.Fat)
        MacroProgressBar("Ugljeni hidrati", summary.carbs, goals.carbs, AppColors.Carbs)

        if (profile.useTrt && profile.trtTdeePhase < 3) {
            Text(
                text = "Cilj prema formuli — TDEE se još utvrđuje",
                style = AppTypography.labelSmall.copy(color = AppColors.Warning)
            )
        }
    }
}
```

- [x] **Step 4: Convert meal actions to icon-led primary actions**

Replace the two full-width buttons with a row:

```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(AppDimens.paddingSmall)
) {
    Button(
        onClick = onNavigateToManualEntry,
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
    ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Obrok")
    }
    Button(
        onClick = onNavigateToShakeEntry,
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.buttonColors(containerColor = AppColors.SurfaceLight)
    ) {
        Icon(Icons.Default.LocalDrink, contentDescription = null, tint = AppColors.Primary)
        Spacer(Modifier.width(8.dp))
        Text("Šejk")
    }
}
```

Add import:

```kotlin
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalDrink
import kotlin.math.roundToInt
```

- [x] **Step 5: Improve empty state**

Replace empty text with:

```kotlin
GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = AppDimens.paddingLarge) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Još nema obroka za danas", style = AppTypography.labelLarge)
        Text("Dodaj obrok ili šejk da vidiš dnevni napredak.", style = AppTypography.bodyMedium)
        Button(
            onClick = onNavigateToManualEntry,
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
        ) {
            Text("Unesi prvi obrok")
        }
    }
}
```

- [x] **Step 6: Verify**

Run:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Expected: PASS. Home uses the same data and routes with updated layout.

---

### Task 4: Redesign Food Entry Cards And Safer Delete Flow

**Files:**
- Modify: `app/src/main/java/com/kalorije/app/ui/components/FoodEntryCard.kt`

**Security flag:** `none`

**Does NOT cover:** changing delete semantics in repositories or adding undo. Delete still calls the existing `onDelete`; this task only changes when the user triggers it.

- [x] **Step 1: Keep card inputs unchanged**

Do not change the signatures of:

```kotlin
fun FoodEntryCard(
    entry: FoodEntry,
    onDelete: () -> Unit,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
)

fun GroupedFoodEntryCard(
    entries: List<FoodEntry>,
    onDeleteGroup: () -> Unit,
    onEditEntry: ((String) -> Unit)? = null,
    onDeleteEntry: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
)
```

- [x] **Step 2: Add delete confirmation state**

Inside `FoodEntryCard`, add:

```kotlin
var showDeleteDialog by remember { mutableStateOf(false) }

if (showDeleteDialog) {
    AlertDialog(
        onDismissRequest = { showDeleteDialog = false },
        title = { Text("Obrisati obrok?", style = AppTypography.titleMedium) },
        text = { Text(entry.product.name, style = AppTypography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = {
                showDeleteDialog = false
                onDelete()
            }) {
                Text("Obriši", color = AppColors.Error)
            }
        },
        dismissButton = {
            TextButton(onClick = { showDeleteDialog = false }) {
                Text("Odustani")
            }
        },
        containerColor = AppColors.Surface,
        titleContentColor = AppColors.TextPrimary,
        textContentColor = AppColors.TextSecondary
    )
}
```

Add imports:

```kotlin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
```

- [x] **Step 3: Make the card denser and clearer**

Use `MetricPill` for macros:

```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    MetricPill("P", "${entry.protein.roundToInt()}g", AppColors.Protein)
    MetricPill("M", "${entry.fat.roundToInt()}g", AppColors.Fat)
    MetricPill("UH", "${entry.carbs.roundToInt()}g", AppColors.Carbs)
}
```

Change the delete icon click to:

```kotlin
IconButton(onClick = { showDeleteDialog = true }) {
    Icon(
        imageVector = Icons.Default.Delete,
        contentDescription = "Obriši",
        tint = AppColors.TextMuted
    )
}
```

- [x] **Step 4: Apply the same confirmation to group delete**

Inside `GroupedFoodEntryCard`, add:

```kotlin
var showGroupDeleteDialog by remember { mutableStateOf(false) }
```

Show:

```kotlin
if (showGroupDeleteDialog) {
    AlertDialog(
        onDismissRequest = { showGroupDeleteDialog = false },
        title = { Text("Obrisati grupu obroka?", style = AppTypography.titleMedium) },
        text = { Text("${entries.size} stavki", style = AppTypography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = {
                showGroupDeleteDialog = false
                onDeleteGroup()
            }) {
                Text("Obriši", color = AppColors.Error)
            }
        },
        dismissButton = {
            TextButton(onClick = { showGroupDeleteDialog = false }) {
                Text("Odustani")
            }
        },
        containerColor = AppColors.Surface
    )
}
```

Change group delete button to:

```kotlin
IconButton(onClick = { showGroupDeleteDialog = true }) {
    Icon(Icons.Default.Delete, contentDescription = "Obriši grupu", tint = AppColors.TextMuted)
}
```

- [x] **Step 5: Verify**

Run:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Expected: PASS. Delete callbacks still compile at all call sites.

---

### Task 5: Rework Manual Entry Flow

**Files:**
- Modify: `app/src/main/java/com/kalorije/app/ui/screens/ManualEntryScreen.kt`

**Security flag:** `none`

**Does NOT cover:** changing cart persistence, saved food selection, nutrient math, time parsing, or `submitMealAndExit` behavior.

- [x] **Step 1: Preserve all existing state and submit functions**

Keep `mealCart`, `pendingSelections`, `mealTime`, input state, validation variables, `submitMealAndExit`, `exitWithoutSaving`, and `handleNavigateBack` unchanged.

- [x] **Step 2: Add mode selector state**

Near UI-only state:

```kotlin
var selectedEntryMode by rememberSaveable { mutableStateOf(0) }
val entryModes = listOf("Sačuvano", "Ručno")
```

- [x] **Step 3: Replace header with shared header**

Use:

```kotlin
ScreenHeader(
    title = "Unesi obrok",
    subtitle = if (targetDate != null && targetDate != logicalDate) {
        "za ${targetDate.format(java.time.format.DateTimeFormatter.ofPattern("d. MMMM"))}"
    } else null,
    onBack = ::handleNavigateBack
)
```

- [x] **Step 4: Add segmented workflow switch**

Above the saved/manual content:

```kotlin
SegmentedControl(
    options = entryModes,
    selectedIndex = selectedEntryMode,
    onSelected = { selectedEntryMode = it },
    modifier = Modifier.fillMaxWidth()
)
```

When `selectedEntryMode == 0`, show the saved-food button as a primary panel:

```kotlin
GlassCard(modifier = Modifier.fillMaxWidth(), selected = true, accentColor = AppColors.Primary) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Dodaj iz sačuvanih jela", style = AppTypography.labelLarge)
        Text("Brz izbor namirnica koje već koristiš.", style = AppTypography.bodyMedium)
        Button(
            onClick = onNavigateToSavedFoods,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
        ) {
            Icon(Icons.Default.Bookmark, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Otvori sačuvana jela")
        }
    }
}
```

When `selectedEntryMode == 1`, show the existing manual form card.

- [x] **Step 5: Improve the input mode switch**

Replace the `Switch` row for `isPerHundredGrams` with:

```kotlin
SegmentedControl(
    options = listOf("Po 100g", "Ukupno"),
    selectedIndex = if (isPerHundredGrams) 0 else 1,
    onSelected = { isPerHundredGrams = it == 0 },
    modifier = Modifier.fillMaxWidth()
)
```

This preserves the existing nutrient conversion formulas.

- [x] **Step 6: Add persistent cart summary at bottom of content**

At the end of the main `Column`, when `mealCart.isNotEmpty()`, add:

```kotlin
GlassCard(
    modifier = Modifier.fillMaxWidth(),
    bgColor = AppColors.SurfaceRaised,
    accentColor = AppColors.Primary
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Korpa", "${mealCart.size} stavki")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MacroDisplay("Kal", "$cartTotalCalories", "kcal", AppColors.Calories)
            MacroDisplay("P", "$cartTotalProtein", "g", AppColors.Protein)
            MacroDisplay("M", "$cartTotalFat", "g", AppColors.Fat)
            MacroDisplay("UH", "$cartTotalCarbs", "g", AppColors.Carbs)
        }
        Button(
            onClick = ::submitMealAndExit,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
        ) {
            Text("Sačuvaj obrok")
        }
    }
}
```

Keep the existing detailed cart list near the top so individual item removal remains available.

- [x] **Step 7: Verify**

Run:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Expected: PASS. Existing cart add/remove/save flows compile with unchanged data paths.

---

### Task 6: Improve History Day Cards

**Files:**
- Modify: `app/src/main/java/com/kalorije/app/ui/screens/HistoryScreen.kt`

**Security flag:** `none`

**Does NOT cover:** changing pagination, history loading, date expansion state, deletion logic, or meal grouping.

- [x] **Step 1: Preserve ViewModel interactions**

Keep:

```kotlin
val uiState by viewModel.uiState.collectAsState()
viewModel.expandDate(summary.date)
viewModel.loadNextPage()
groupEntriesIntoMeals(uiState.expandedEntries)
```

- [x] **Step 2: Replace header with shared header**

Use:

```kotlin
ScreenHeader(title = "Istorija", onBack = onNavigateBack)
```

- [x] **Step 3: Add calorie progress helper**

In `HistoryScreen.kt`, add:

```kotlin
@Composable
private fun DayCalorieProgress(current: Double, goal: Int) {
    val progress = if (goal > 0) (current / goal).coerceIn(0.0, 1.0) else 0.0
    val status = when {
        goal <= 0 -> "bez cilja"
        current > goal -> "preko cilja"
        current >= goal * 0.9 -> "blizu cilja"
        else -> "ispod cilja"
    }
    val statusColor = when (status) {
        "preko cilja" -> AppColors.Warning
        "blizu cilja" -> AppColors.Success
        else -> AppColors.TextSecondary
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(AppColors.SurfaceLight)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.toFloat())
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusColor)
            )
        }
        Text(status, style = AppTypography.labelSmall.copy(color = statusColor))
    }
}
```

Add imports:

```kotlin
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
```

- [x] **Step 4: Use progress helper in each day card**

After the day header row and before macro summary row:

```kotlin
Spacer(modifier = Modifier.height(10.dp))
DayCalorieProgress(summary.totalCalories, goals.calories)
Spacer(modifier = Modifier.height(8.dp))
```

- [x] **Step 5: Improve empty state action**

Replace the current empty text with:

```kotlin
GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = AppDimens.paddingLarge) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Istorija je prazna", style = AppTypography.labelLarge)
        Text("Kada uneseš prvi obrok, dnevni pregled će se prikazati ovde.", style = AppTypography.bodyMedium)
    }
}
```

- [x] **Step 6: Verify**

Run:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Expected: PASS. History pagination and expansion still compile.

---

### Task 7: Reorder Weight Progress Screen

**Files:**
- Modify: `app/src/main/java/com/kalorije/app/ui/screens/weight/WeightProgressScreen.kt`
- Modify: `app/src/main/java/com/kalorije/app/ui/components/SharedComponents.kt`

**Security flag:** `none`

**Does NOT cover:** changing `WeightProgressViewModel`, `WeightNormalizer`, `PartitionCalculator`, TRT/TDEE formulas, or stored weight entries.

- [x] **Step 1: Preserve ViewModel state and mutations**

Do not change:

```kotlin
val profile by viewModel.profile.collectAsState()
val uiState by viewModel.uiState.collectAsState()
viewModel.addWeightEntry(weight, validTime)
viewModel.setGoalWeight(goal)
viewModel.setFixedStartingWeight(weight)
viewModel.resetWeightProgress()
viewModel.deleteWeightEntry(date)
viewModel.recalculateTdee()
```

- [x] **Step 2: Add collapsible section helper**

Append to `SharedComponents.kt`:

```kotlin
@Composable
fun CollapsiblePanel(
    title: String,
    subtitle: String? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = AppTypography.labelLarge)
                    subtitle?.let { Text(it, style = AppTypography.labelSmall) }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Skupi" else "Proširi",
                    tint = AppColors.TextSecondary
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            }
        }
    }
}
```

Add imports:

```kotlin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
```

- [x] **Step 3: Replace header with shared header**

Use:

```kotlin
ScreenHeader(title = "Napredak težine", onBack = onNavigateBack)
```

- [x] **Step 4: Reorder `LazyColumn` items**

Use this order:

```kotlin
item(contentType = "statistics") { WeightStatisticsCard(...) }
item(contentType = "chart") { WeightChartSection(...) }
item(contentType = "weight_input") { WeightInputSection(...) }
item(contentType = "goal_weight") { GoalWeightSection(...) }
item(contentType = "starting_weight") { StartingWeightSection(...) }
```

Then wrap advanced cards:

```kotlin
uiState.insight?.let { ins ->
    item(contentType = "insight") {
        CollapsiblePanel("Uvidi", "Trend kalorija i težine") {
            InsightCard(insight = ins)
        }
    }
}

if (profile.trackingDays >= 7 || profile.manualTdee != null) {
    item(contentType = "learned_tdee") {
        CollapsiblePanel("TDEE", profile.tdeeConfidenceText) {
            LearnedTdeeCard(...)
        }
    }
}
```

For TRT and partition sections:

```kotlin
CollapsiblePanel("TRT voda", "Procena zadržane vode") {
    TrtWaterRetentionCard(...)
}

CollapsiblePanel("Particija dobitka", "Mast, voda i suva masa") {
    PartitionEstimateCard(...)
}
```

- [x] **Step 5: Keep simulator near projection**

When `profile.goalWeight != null`, keep `WeightProjectionCard` and `CalorieSimulatorCard` adjacent.

- [x] **Step 6: Verify**

Run:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Expected: PASS. No util or ViewModel files are modified.

---

### Task 8: Group Settings Into Clear Sections

**Files:**
- Modify: `app/src/main/java/com/kalorije/app/ui/screens/settings/SettingsScreen.kt`

**Security flag:** `security`

**Does NOT cover:** changing notification permission behavior, Google Drive auth, backup/restore semantics, TRT profile fields, or goal calculations.

- [x] **Step 1: Preserve all ViewModel and permission handlers**

Keep existing launchers and handler blocks unchanged:

```kotlin
notificationPermissionLauncher
signInLauncher
viewModel.handleSignInResult(account)
viewModel.startBackup()
viewModel.restoreData()
viewModel.importData(data)
NotificationHelper.scheduleDailyReminder(...)
NotificationHelper.cancelReminder(context)
```

- [x] **Step 2: Replace header with shared header**

Use:

```kotlin
ScreenHeader(title = "Podešavanja", onBack = onNavigateBack)
```

- [x] **Step 3: Wrap major sections with titles and spacing**

Use this structure inside the existing scroll column:

```kotlin
SectionHeader("Profil")
Spacer(Modifier.height(AppDimens.paddingSmall))
ProfileSection(...)

Spacer(Modifier.height(AppDimens.paddingMedium))
SectionHeader("Cilj")
Spacer(Modifier.height(AppDimens.paddingSmall))
GoalModeSection(...)

Spacer(Modifier.height(AppDimens.paddingMedium))
SectionHeader("TRT i ciklus")
Spacer(Modifier.height(AppDimens.paddingSmall))
TrtSection(...)

Spacer(Modifier.height(AppDimens.paddingMedium))
SectionHeader("Dnevni cilj")
Spacer(Modifier.height(AppDimens.paddingSmall))
GoalsPreviewSection(...)

Spacer(Modifier.height(AppDimens.paddingLarge))
SectionHeader("Backup")
Spacer(Modifier.height(AppDimens.paddingSmall))
CloudBackupSection(...)

Spacer(Modifier.height(AppDimens.paddingLarge))
SectionHeader("Podsetnik")
Spacer(Modifier.height(AppDimens.paddingSmall))
ReminderSection(...)
```

- [x] **Step 4: Restyle About section**

Replace the inline `Surface` with `GlassCard`:

```kotlin
SectionHeader("O aplikaciji")
Spacer(Modifier.height(AppDimens.paddingSmall))
GlassCard(modifier = Modifier.fillMaxWidth()) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Kalorije 1.0", style = AppTypography.labelLarge)
        Text(
            "Praćenje kalorija, makro nutrijenata, težine, ciljeva i backup-a.",
            style = AppTypography.bodyMedium
        )
    }
}
```

- [x] **Step 5: Verify**

Run:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Expected: PASS. Permission and backup code paths still compile unchanged.

---

### Task 9: Final Visual Polish And Regression Verification

**Files:**
- Modify only files changed by Tasks 1-8 if compile or visual issues are found.

**Security flag:** `none`

**Does NOT cover:** new features, data migrations, dependency upgrades, new backup fields, or algorithm changes.

- [x] **Step 1: Compile all UI changes together**

Run:

```powershell
.\gradlew.bat testDebugUnitTest lint assembleDebug
```

Expected: PASS. Review lint output for new Compose/accessibility warnings.

- [ ] **Step 2: Manual device or emulator smoke test**

Skipped in this run: `adb devices` returned no connected device/emulator. Run this checklist on a device or emulator before treating visual QA as complete.

Install debug APK or run from Android Studio and verify:

```text
Home:
- Bottom navigation is visible on main screens.
- Home summary card shows the same calorie/macro values as before.
- "Obrok" opens manual entry.
- "Šejk" opens shake entry.
- Empty state button opens manual entry.

Manual entry:
- Back with non-empty cart still shows exit dialog.
- Saved food selection still returns items to cart.
- Manual mode still calculates per-100g and total inputs correctly.
- Save creates the same number of FoodEntry records as before.

History:
- Expanding a day still loads entries.
- Add meal from a historical day still passes that date.
- Delete confirmation appears before delete.

Weight:
- Adding a weight entry still saves with formatted time.
- Chart and history still render.
- Reset and delete dialogs still call the existing ViewModel methods.

Settings:
- Notification permission flow still prompts on Android 13+.
- Google Drive sign-in, backup, and restore actions still invoke existing handlers.
- Goal preview still saves profile with existing validation.
```

- [x] **Step 3: Accessibility pass**

Check these items in code:

```text
- Icon-only buttons have contentDescription.
- Text inside buttons remains short enough for Serbian labels.
- Status is not conveyed by color alone: history progress includes text.
- Dialog confirm actions name the destructive action.
- Main navigation labels are visible.
```

- [x] **Step 4: Scope guard**

Confirm no files under these paths were modified:

```text
app/src/main/java/com/kalorije/app/data/
app/src/main/java/com/kalorije/app/util/
app/src/main/java/com/kalorije/app/notification/
app/src/main/AndroidManifest.xml
```

Expected: only UI files listed in this plan changed.

## Self-Review

- Spec coverage: Home, bottom navigation, meal cards, manual entry, history, weight progress, settings grouping, tokens, and verification are covered.
- Placeholder scan: no deferred implementation markers are present.
- Type consistency: new shared functions use existing `AppColors`, `AppTypography`, `AppDimens`, `GlassCard`, and Compose Material3 APIs.
- Scope-reduction scan: the plan preserves all proposed UI surfaces and explicitly excludes business logic, data model, and backup changes.

## Execution Handoff

Recommended execution approach: **Subagent-Driven** because there are 9 tasks and screen tasks can be reviewed independently after shared components land.
