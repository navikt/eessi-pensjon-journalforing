package no.nav.eessi.pensjon.models.sed

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonValue
import no.nav.eessi.pensjon.models.SedType

@JsonIgnoreProperties(ignoreUnknown = true)
data class Document(
        val id: String,
        val type: SedType?,
        val status: DocStatus?
) {
    fun validStatus(): Boolean = (status == DocStatus.SENT || status == DocStatus.RECEIVED)
    fun cancelledStatus(): Boolean = (status == DocStatus.CANCELLED)
}

@Suppress("unused")
enum class DocStatus(@JsonValue private val value: String) {
    EMPTY("empty"),
    RECEIVED("received"),
    SENT("sent"),
    CANCELLED("cancelled")
}
