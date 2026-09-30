package com.kalorije.app.util

import com.kalorije.app.data.model.FoodEntry
import com.kalorije.app.data.model.FoodProduct
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class MealGroupingTest {

    private val product = FoodProduct(
        name = "Test hrana",
        caloriesPer100g = 100.0
    )

    @Test
    fun entriesAfterMidnightStayAtTopWithinLogicalDay() {
        val logicalDate = LocalDate.of(2026, 1, 10)
        val entries = listOf(
            testEntry(id = "late_evening", date = logicalDate, time = "23:10"),
            testEntry(id = "after_midnight", date = logicalDate, time = "00:40")
        )

        val groups = groupEntriesIntoMeals(entries, windowMinutes = 0)

        assertEquals(2, groups.size)
        assertEquals("after_midnight", groups.first().entries.first().id)
    }

    @Test
    fun entriesAcrossMidnightWithinWindowAreMerged() {
        val logicalDate = LocalDate.of(2026, 1, 10)
        val entries = listOf(
            testEntry(id = "before_midnight", date = logicalDate, time = "23:58"),
            testEntry(id = "after_midnight", date = logicalDate, time = "00:06")
        )

        val groups = groupEntriesIntoMeals(entries, windowMinutes = 20)

        assertEquals(1, groups.size)
        assertEquals(
            setOf("before_midnight", "after_midnight"),
            groups.first().entries.map { it.id }.toSet()
        )
    }

    private fun testEntry(id: String, date: LocalDate, time: String): FoodEntry {
        return FoodEntry(
            id = id,
            date = date,
            createdAt = LocalDateTime.of(date, LOGICAL_DAY_START_TIME),
            product = product,
            grams = 100.0,
            time = time
        )
    }
}
