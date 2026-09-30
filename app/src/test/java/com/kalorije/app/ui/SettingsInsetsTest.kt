package com.kalorije.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsInsetsTest {
    @Test
    fun `main scaffold leaves status bar padding to screens`() {
        val navGraph = sourceFile("src/main/java/com/kalorije/app/ui/navigation/NavGraph.kt")

        assertTrue(
            "Root scaffold should not add top system insets because each screen already handles statusBarsPadding.",
            navGraph.contains("contentWindowInsets = WindowInsets(0.dp)")
        )
    }

    @Test
    fun `settings snackbar scaffold does not add another system inset layer`() {
        val settingsScreen = sourceFile("src/main/java/com/kalorije/app/ui/screens/settings/SettingsScreen.kt")

        assertTrue(
            "Settings screen should either avoid a nested Scaffold or disable its contentWindowInsets.",
            settingsScreen.contains("contentWindowInsets = WindowInsets(0.dp)") ||
                !settingsScreen.contains("Scaffold(")
        )
    }

    private fun sourceFile(path: String): String =
        listOf(File(path), File("app/$path"))
            .first { it.exists() }
            .readText()
}
