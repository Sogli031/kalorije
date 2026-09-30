package com.kalorije.app.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.kalorije.app.data.repository.FoodRepository
import com.kalorije.app.ui.screens.EditEntryScreen
import com.kalorije.app.ui.screens.HistoryScreen
import com.kalorije.app.ui.screens.HomeScreen
import com.kalorije.app.ui.screens.ManualEntryScreen
import com.kalorije.app.ui.screens.SavedFoodsScreen
import com.kalorije.app.ui.screens.ShakeEntryScreen
import com.kalorije.app.ui.screens.ShakeFoodsPickerScreen
import com.kalorije.app.ui.screens.history.HistoryViewModel
import com.kalorije.app.ui.screens.settings.SettingsScreen
import com.kalorije.app.ui.screens.streak.StreakScreen
import com.kalorije.app.ui.screens.weight.WeightProgressScreen
import com.kalorije.app.ui.theme.AppColors

object Routes {
    const val HOME = "home"
    const val MANUAL_ENTRY = "manual_entry?date={date}"
    const val MANUAL_ENTRY_BASE = "manual_entry"
    const val EDIT_ENTRY = "edit_entry/{entryId}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val STREAK = "streak"
    const val WEIGHT_PROGRESS = "weight_progress"
    const val SAVED_FOODS = "saved_foods"
    const val SAVED_FOODS_MANAGE = "saved_foods_manage"
    const val SAVED_FOODS_SHAKE = "saved_foods_shake"
    const val SHAKE_ENTRY = "shake_entry"

    fun editEntry(entryId: String) = "edit_entry/$entryId"
    fun manualEntry(date: java.time.LocalDate? = null) =
        if (date != null) "manual_entry?date=$date" else "manual_entry"
}

@Composable
fun NavGraph(
    navController: NavHostController,
    repository: FoodRepository
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val bottomNavItems = remember {
        listOf(
            BottomNavItem(Routes.HOME, "Danas", Icons.Default.Home),
            BottomNavItem(Routes.HISTORY, "Istorija", Icons.Default.History),
            BottomNavItem(Routes.WEIGHT_PROGRESS, "Težina", Icons.AutoMirrored.Filled.ShowChart),
            BottomNavItem(Routes.STREAK, "Niz", Icons.Default.LocalFireDepartment),
            BottomNavItem(Routes.SETTINGS, "Podešavanja", Icons.Default.Settings)
        )
    }

    Scaffold(
        containerColor = AppColors.Background,
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            if (currentRoute in bottomNavItems.map { it.route }) {
                NavigationBar(containerColor = AppColors.Surface) {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(Routes.HOME) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label
                                )
                            },
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
        composable(Routes.HOME) {
            HomeScreen(
                repository = repository,
                onNavigateToManualEntry = { navController.navigate(Routes.manualEntry()) },
                onNavigateToShakeEntry = { navController.navigate(Routes.SHAKE_ENTRY) },
                onNavigateToEdit = { entryId -> navController.navigate(Routes.editEntry(entryId)) },
                onNavigateToSavedFoods = { navController.navigate(Routes.SAVED_FOODS_MANAGE) }
            )
        }

        composable(Routes.SHAKE_ENTRY) {
            ShakeEntryScreen(
                repository = repository,
                onNavigateToSavedFoods = { navController.navigate(Routes.SAVED_FOODS_SHAKE) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SAVED_FOODS_SHAKE) {
            ShakeFoodsPickerScreen(
                repository = repository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.MANUAL_ENTRY,
            arguments = listOf(
                navArgument("date") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val dateString = backStackEntry.arguments?.getString("date")
            val targetDate = dateString?.let { java.time.LocalDate.parse(it) }
            ManualEntryScreen(
                repository = repository,
                targetDate = targetDate,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSavedFoods = { navController.navigate(Routes.SAVED_FOODS) }
            )
        }

        composable(Routes.SAVED_FOODS) {
            SavedFoodsScreen(
                repository = repository,
                onSelectItems = { items ->
                    repository.setPendingFoodSelections(items)
                    navController.popBackStack()
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SAVED_FOODS_MANAGE) {
            SavedFoodsScreen(
                repository = repository,
                onSelectItems = null, // Management mode - no selection
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.EDIT_ENTRY,
            arguments = listOf(navArgument("entryId") { type = NavType.StringType })
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getString("entryId") ?: return@composable
            EditEntryScreen(
                entryId = entryId,
                repository = repository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.HISTORY) {
            val historyViewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)
            val goals by repository.goals.collectAsState()
            HistoryScreen(
                viewModel = historyViewModel,
                goals = goals,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { entryId -> navController.navigate(Routes.editEntry(entryId)) },
                onNavigateToAddMeal = { date -> navController.navigate(Routes.manualEntry(date)) },
                onDeleteEntry = { id -> historyViewModel.deleteEntry(id) }
            )
        }

        composable(Routes.STREAK) {
            StreakScreen()
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.WEIGHT_PROGRESS) {
            WeightProgressScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        }
    }
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)
