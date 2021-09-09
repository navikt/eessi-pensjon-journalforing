package no.nav.eessi.pensjon.json

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper


fun mapAnyToJson(data: Any): String {
    return jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(data)
}

fun mapAnyToJson(data: Any, nonempty: Boolean = false): String {
    return if (nonempty) {
        jacksonObjectMapper()
                .registerModule(JavaTimeModule())
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_EMPTY)
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(data)
    } else {
        mapAnyToJson(data)
    }
}

inline fun <reified T : Any> typeRefs(): TypeReference<T> = object : TypeReference<T>() {}

inline fun <reified T : Any> mapJsonToAny(json: String, objekt: TypeReference<T>, failonunknown: Boolean = false): T {
    return jacksonObjectMapper()
                .registerModule(JavaTimeModule())
                .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, failonunknown)
                .readValue(json, objekt)
}

inline fun <reified T: Any> String.toKotlinObject(): T {
    val mapper = jacksonObjectMapper()
    return mapper.readValue(this, T::class.java)
}


fun Any.toJson() =  mapAnyToJson(this)
