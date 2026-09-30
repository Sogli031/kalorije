package com.kalorije.app.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object GsonProvider {
    val instance: Gson by lazy {
        GsonBuilder()
            .registerTypeAdapter(LocalDate::class.java, LocalDateAdapter())
            .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeAdapter())
            .create()
    }
}

private class LocalDateAdapter : TypeAdapter<LocalDate>() {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    override fun write(out: JsonWriter, value: LocalDate?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.value(value.format(formatter))
        }
    }

    override fun read(input: JsonReader): LocalDate? {
        return if (input.peek() == JsonToken.NULL) {
            input.nextNull()
            null
        } else {
            LocalDate.parse(input.nextString(), formatter)
        }
    }
}

private class LocalDateTimeAdapter : TypeAdapter<LocalDateTime>() {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    override fun write(out: JsonWriter, value: LocalDateTime?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.value(value.format(formatter))
        }
    }

    override fun read(input: JsonReader): LocalDateTime? {
        return if (input.peek() == JsonToken.NULL) {
            input.nextNull()
            null
        } else {
            LocalDateTime.parse(input.nextString(), formatter)
        }
    }
}
