package com.kalorije.app.data.db

import androidx.room.TypeConverter
import com.kalorije.app.data.model.MealType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Converters {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? = date?.format(dateFormatter)

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it, dateFormatter) }

    @TypeConverter
    fun fromLocalDateTime(dateTime: LocalDateTime?): String? = dateTime?.format(dateTimeFormatter)

    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? = value?.let { LocalDateTime.parse(it, dateTimeFormatter) }

    @TypeConverter
    fun fromMealType(mealType: MealType): String = mealType.name

    @TypeConverter
    fun toMealType(value: String): MealType = MealType.fromString(value)
}
