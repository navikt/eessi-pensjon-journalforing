package no.nav.eessi.eessifagmodul.utils

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.io.ByteArrayResource
import org.springframework.web.client.RestClientException
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import javax.xml.datatype.DatatypeFactory
import javax.xml.datatype.XMLGregorianCalendar

inline fun <reified T : Any> typeRef(): ParameterizedTypeReference<T> = object : ParameterizedTypeReference<T>() {}
inline fun <reified T : Any> typeRefs(): TypeReference<T> = object : TypeReference<T>() {}
inline fun <reified T : Any> mapJsonToAny(json: String, objec: TypeReference<T>, failonunknown: Boolean = false): T {
    if (validateJson(json)) {
        return jacksonObjectMapper()
//                .registerModule(JavaTimeModule())
//                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, failonunknown)
                .readValue<T>(json, objec)
    } else {
        throw IllegalArgumentException("Not valid json format")
    }
}

fun <K, V> Map<K, V>.reversed() = HashMap<V, K>().also { newMap ->
    entries.forEach { newMap.put(it.value, it.key) }
}


fun createErrorMessage(responseBody: String): RestClientException {
    return mapJsonToAny(responseBody, typeRefs())
}

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

fun validateJson(json: String): Boolean {
    return try {
        jacksonObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                .readTree(json)
        true
    } catch (ex: Exception) {
        false
    }
}

fun XMLGregorianCalendar.simpleFormat(): String {
    //rinaformat dd-MM-YYYY
    return SimpleDateFormat("yyyy-MM-dd").format(this.toGregorianCalendar().time)
}

fun createXMLCalendarFromString(dateStr: String): XMLGregorianCalendar {
    val time = LocalDate.parse(dateStr)
    val gcal = GregorianCalendar()
    gcal.timeInMillis = time.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return DatatypeFactory.newInstance().newXMLGregorianCalendar(gcal)
}


fun Date.simpleFormat(): String {
    return SimpleDateFormat("yyyy-MM-dd").format(this)
}

inline fun <T : Any, R> whenNotNull(input: T?, callback: (T) -> R): R? {
    return input?.let(callback)
}

fun getCounter(key: String): Counter {
    val countermap = mapOf("AKSJONOK" to counter("eessipensjon_fagmodul.euxmuligeaksjoner", "vellykkede"),
            "AKSJONFEIL" to counter("eessipensjon_fagmodul.euxmuligeaksjoner", "feilede"),
            "SENDSEDOK" to counter("eessipensjon_fagmodul.sendsed", "vellykkede"),
            "SENDSEDFEIL" to counter("eessipensjon_fagmodul.sendsed", "feilede"),
            "HENTSEDOK" to counter("eessipensjon_fagmodul.hentsed", "vellykkede"),
            "HENTSEDFEIL" to counter("eessipensjon_fagmodul.hentsed", "feilede"),
            "SLETTSEDOK" to counter("eessipensjon_fagmodul.slettsed", "vellykkede"),
            "SLETTSEDFEIL" to counter("eessipensjon_fagmodul.slettsed", "feilede"),
            "OPPRETTEDOK" to counter("eessipensjon_fagmodul.opprettsed", "vellykkede"),
            "OPPRETTSEDFEIL" to counter("eessipensjon_fagmodul.opprettsed", "feilede"),
            "OPPRETTBUCOGSEDOK" to counter("eessipensjon_fagmodul.opprettbucogsed", "vellykkede"),
            "OPPRETTBUCOGSEDFEIL" to counter("eessipensjon_fagmodul.opprettbucogsed", "feilede"),
            "HENTBUCOK" to counter("eessipensjon_fagmodul.hentbuc", "vellykkede"),
            "HENTBUCFEIL" to counter("eessipensjon_fagmodul.hentbuc", "feilede"),
            "PERSONINFORMASJONOK" to counter("eessipensjon_fagmodul.hentperson", "vellykkede"),
            "PERSONINFORMASJONFEIL" to counter("eessipensjon_fagmodul.hentperson", "feilede"),
            "HENTKRAVUTLANDOK" to counter("eessipensjon_fagmodul.hentKravUtland", "vellykkede"),
            "HENTKRAVUTLANDFEIL" to counter("eessipensjon_fagmodul.hentKravUtland", "feilede")

    )
    return countermap.getValue(key)
}

fun counter(name: String, type: String): Counter {
    return Metrics.counter(name, "type", type)
}

fun getFileAsResource(bytearr: ByteArray, filename: String): ByteArrayResource {
    class FileAsResource : ByteArrayResource(bytearr) {
        override fun getFilename(): String? {
            return filename
        }
    }
    return FileAsResource()
}

fun errorBody(error: String, uuid: String = "no-uuid"): String {
    return "{\"success\": false, \n \"error\": \"$error\", \"uuid\": \"$uuid\"}"
}

fun successBody(): String {
    return "{\"success\": true}"
}
