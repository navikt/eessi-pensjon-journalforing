package no.nav.eessi.pensjon.klienter.journalpost

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonRawValue
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.TreeNode
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.json.mapAnyToJson
import no.nav.eessi.pensjon.models.Behandlingstema
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.Tema
import java.io.IOException

/**
 * /rest/journalpostapi/v1/journalpost
 *
 * Oppretter en journalpost i fagarkivet, med eller uten dokumenter
 */
class OpprettJournalpostRequest(
        val avsenderMottaker: AvsenderMottaker,
        val behandlingstema: Behandlingstema? = null,
        val bruker: Bruker? = null,
        @JsonDeserialize(using = JsonAsStringDeserializer::class)
        @JsonRawValue
        val dokumenter: String,
        val journalfoerendeEnhet: Enhet? = null,
        val journalpostType: JournalpostType,
        val sak: Sak? = null,
        val tema: Tema = Tema.PENSJON,
        val tilleggsopplysninger: List<Tilleggsopplysning>? = null,
        val tittel: String
){
    val kanal: String = "EESSI"
    val eksternReferanseId: String? = null

    override fun toString(): String {
        return mapAnyToJson(this,true)
    }
}

enum class JournalpostType: Code {
    UTGAAENDE {
        override fun decode() = "Utgående"
    },
    INNGAAENDE {
        override fun decode() = "Inngående"
    }
}

data class Sak(
    val arkivsaksnummer: String,
    val arkivsaksystem: String
)

/**
 * Avsender eller mottaker informasjon for personen eller organisasjonen som enten sender eller mottar dokumentet
 *
 * https://confluence.adeo.no/display/BOA/Type%3A+AvsenderMottaker
 */
data class AvsenderMottaker(
    val id: String? = null,
    val idType: IdType? = null,
    val navn: String? = null,
    val land: String? = null
)

enum class IdType {
    FNR,
    ORGNR
}

data class Bruker(
    val id: String,
    val idType: String = "FNR"
)

data class Tilleggsopplysning(
        val nokkel: String,
        val verdi: String
)

interface Code {
    fun decode(): String
}

private class JsonAsStringDeserializer : JsonDeserializer<String>() {
    @Throws(IOException::class, JsonProcessingException::class)
    override fun deserialize(jsonParser: JsonParser, deserializationContext: DeserializationContext): String {
        val tree = jsonParser.codec.readTree<TreeNode>(jsonParser)
        return tree.toString()
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class OpprettJournalPostResponse(
        val journalpostId: String,
        val journalstatus: String,
        val melding: String? = null,
        val journalpostferdigstilt: Boolean
){
    override fun toString(): String {
        val mapper = jacksonObjectMapper()
        return mapper.writeValueAsString(this)
    }
}
