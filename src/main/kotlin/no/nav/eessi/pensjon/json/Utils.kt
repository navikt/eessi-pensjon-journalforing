package no.nav.eessi.pensjon.json

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

fun mapAnyToJson(data: Any): String {
    return jacksonObjectMapper()
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(data)
}

fun mapAnyToJson(data: Any, nonempty: Boolean = false): String {
    return if (nonempty) {
        jacksonObjectMapper()
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_EMPTY)
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(data)
    } else {
        mapAnyToJson(data)
    }
}

inline fun <reified T : Any> typeRefs(): TypeReference<T> = object : TypeReference<T>() {}

inline fun <reified T : Any> mapJsonToAny(json: String, objekt: TypeReference<T>, failonunknown: Boolean = false): T {
    return try {
        jacksonObjectMapper()
                .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, failonunknown)
                .readValue(json, objekt)
    } catch (jpe: JsonParseException) {
        jpe.printStackTrace()
        throw JsonException("Feilet ved konvertering av jsonformat, ${jpe.message}")
    } catch (jme: JsonMappingException) {
        jme.printStackTrace()
        throw JsonIllegalArgumentException("Feilet ved mapping av jsonformat, ${jme.message}")
    } catch (ex: Exception) {
        ex.printStackTrace()
        throw JsonException("Feilet med en ukjent feil ved jsonformat, ${ex.message}")
    }
}


fun Any.toJson() =  mapAnyToJson(this)
fun Any.toEmptyJson() =  mapAnyToJson(this, true)

@ResponseStatus(value = HttpStatus.UNPROCESSABLE_ENTITY)
class JsonException(message: String?) : RuntimeException(message)

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
class JsonIllegalArgumentException(message: String?) : IllegalArgumentException(message)
