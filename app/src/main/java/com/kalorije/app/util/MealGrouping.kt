package com.kalorije.app.util

import com.kalorije.app.data.model.FoodEntry
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

data class MealGroup(
    val time: LocalDateTime,
    val entries: List<FoodEntry>
)

/**
 * Minimalni podaci potrebni za grupisanje unosa u obroke.
 * Koristi se kad nam treba samo broj obroka (bez učitavanja celih unosa).
 */
data class MealTiming(
    val date: LocalDate,
    val createdAt: LocalDateTime,
    val groupId: String?,
    val time: String?
)

private const val DEFAULT_MEAL_GROUP_WINDOW_MINUTES = 20L

private data class TimedItem<T>(
    val time: LocalDateTime,
    val groupId: String?,
    val value: T
)

fun groupEntriesIntoMeals(
    entries: List<FoodEntry>,
    windowMinutes: Long = DEFAULT_MEAL_GROUP_WINDOW_MINUTES
): List<MealGroup> {
    val items = entries.map { TimedItem(entryMealDateTime(it), it.groupId, it) }
    return buildGroups(items, windowMinutes).map { (time, values) ->
        MealGroup(time = time, entries = values)
    }
}

/**
 * Broj obroka (kartica) za dati skup unosa - ista logika kao [groupEntriesIntoMeals].
 */
fun countMealGroups(
    timings: List<MealTiming>,
    windowMinutes: Long = DEFAULT_MEAL_GROUP_WINDOW_MINUTES
): Int {
    val items = timings.map { TimedItem(timingMealDateTime(it), it.groupId, it) }
    return buildGroups(items, windowMinutes).size
}

/**
 * Zajednička logika: unosi sa istim groupId su jedan obrok, a zatim se
 * susedni obroci unutar [windowMinutes] spajaju u jedan.
 */
private fun <T> buildGroups(
    items: List<TimedItem<T>>,
    windowMinutes: Long
): List<Pair<LocalDateTime, List<T>>> {
    if (items.isEmpty()) return emptyList()

    val groupedItems = items.filter { it.groupId != null }
        .groupBy { it.groupId }
        .values
        .map { group ->
            val groupTime = group.maxOf { it.time }
            groupTime to group.sortedByDescending { it.time }
        }

    val standaloneItems = items.filter { it.groupId == null }
        .map { item -> item.time to listOf(item) }

    val sortedGroups = (groupedItems + standaloneItems)
        .sortedByDescending { it.first }

    val mergedGroups = mutableListOf<Pair<LocalDateTime, List<TimedItem<T>>>>()

    for (group in sortedGroups) {
        if (mergedGroups.isEmpty()) {
            mergedGroups.add(group)
            continue
        }

        val lastIndex = mergedGroups.lastIndex
        val lastGroup = mergedGroups[lastIndex]
        val diffMinutes = ChronoUnit.MINUTES.between(group.first, lastGroup.first)

        if (diffMinutes <= windowMinutes) {
            val mergedItems = (lastGroup.second + group.second)
                .sortedByDescending { it.time }
            mergedGroups[lastIndex] = lastGroup.first to mergedItems
        } else {
            mergedGroups.add(group)
        }
    }

    return mergedGroups.map { (time, groupItems) -> time to groupItems.map { it.value } }
}

private fun entryMealDateTime(entry: FoodEntry): LocalDateTime =
    mealDateTime(entry.date, entry.time, entry.createdAt)

private fun timingMealDateTime(timing: MealTiming): LocalDateTime =
    mealDateTime(timing.date, timing.time, timing.createdAt)

private fun mealDateTime(date: LocalDate, time: String?, createdAt: LocalDateTime): LocalDateTime {
    val parsedTime = time?.let { WeightNormalizer.parseTime(it) }
    return if (parsedTime != null) {
        toLogicalMealDateTime(date, parsedTime)
    } else {
        createdAt
    }
}
