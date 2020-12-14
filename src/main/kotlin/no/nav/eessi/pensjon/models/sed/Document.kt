package no.nav.eessi.pensjon.models.sed

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.eessi.pensjon.models.SedType

@JsonIgnoreProperties(ignoreUnknown = true)
data class Document(
        val id: String,
        val type: SedType,
        val status: String,
        val creationDate: Long? = null,
        val lastUpdate: Long? = null,
        val displayName: String? = null
)